// Package communication implements the internal Communication Engine
// (doc 11, doc 24 §7.4): threaded, append-only messages between workers and
// departments with typed kinds. There is no direct worker-to-worker RPC for
// content exchange — all inter-department talk flows through here.
package communication

import (
	"context"
	"crypto/rand"
	"encoding/hex"
	"errors"
	"time"

	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgxpool"

	"veliora-backend/internal/shared/events"
)

// ErrNotFound is returned for missing messages.
var ErrNotFound = errors.New("not found")

// MessageKinds (doc 24 §7.4).
var MessageKinds = map[string]bool{
	"statement": true, "question": true, "challenge": true,
	"evidence": true, "vote": true, "summary": true,
	"evidence_request": true, "critique": true, "clarification": true,
	"directive": true,
}

// Service is the Communication Engine.
type Service struct {
	pool     *pgxpool.Pool
	appender *events.Appender
}

// NewService builds the Communication Engine.
func NewService(pool *pgxpool.Pool) *Service {
	return &Service{pool: pool, appender: events.NewAppender(pool)}
}

// Message is an internal organizational message.
type Message struct {
	MessageID        string `json:"message_id"`
	OrganizationID   string `json:"organization_id"`
	MeetingID        string `json:"meeting_id,omitempty"`
	InvestigationID  string `json:"investigation_id,omitempty"`
	SenderWorkerID   string `json:"sender_worker_id"`
	RecipientWorkerID string `json:"recipient_worker_id,omitempty"`
	Content          string `json:"content"`
	MessageType      string `json:"message_type"`
	ParentMessageID  string `json:"parent_message_id,omitempty"`
	CreatedAt        string `json:"created_at"`
}

// SendRequest is rpc.comm.send (doc 24 §7.4 POST /messages).
type SendRequest struct {
	OrganizationID   string `json:"organization_id"`
	MeetingID        string `json:"meeting_id,omitempty"`
	InvestigationID  string `json:"investigation_id,omitempty"`
	SenderWorkerID   string `json:"sender_worker_id"`
	RecipientWorkerID string `json:"recipient_worker_id,omitempty"` // empty = broadcast
	Kind             string `json:"kind"`
	Body             string `json:"body"`
	ParentMessageID  string `json:"parent_message_id,omitempty"`
}

// Send persists a message and emits message.sent.
func (s *Service) Send(ctx context.Context, req SendRequest) (*Message, error) {
	if !MessageKinds[req.Kind] {
		req.Kind = "statement"
	}
	if req.Body == "" {
		return nil, errors.New("empty body")
	}

	// Validate sender exists.
	var senderExists bool
	if err := s.pool.QueryRow(ctx, `
		SELECT EXISTS(SELECT 1 FROM workers WHERE id = $1 AND organization_id = $2)`,
		req.SenderWorkerID, req.OrganizationID).Scan(&senderExists); err != nil {
		return nil, err
	}
	if !senderExists {
		return nil, errors.New("sender not found")
	}

	id := newUUID()
	if _, err := s.pool.Exec(ctx, `
		INSERT INTO messages (id, organization_id, meeting_id, investigation_id,
			sender_worker_id, recipient_worker_id, content, message_type, parent_message_id)
		VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9)`,
		id, req.OrganizationID, nullIfEmpty(req.MeetingID), nullIfEmpty(req.InvestigationID),
		req.SenderWorkerID, nullIfEmpty(req.RecipientWorkerID), req.Body, req.Kind,
		nullIfEmpty(req.ParentMessageID)); err != nil {
		return nil, err
	}

	ev := events.Root(req.OrganizationID, events.EvMessageSent, events.CatCommunication,
		"communication-engine", events.Source{Component: "communication-engine", WorkerID: req.SenderWorkerID},
		map[string]interface{}{
			"message_id": id, "from": req.SenderWorkerID, "to": req.RecipientWorkerID,
			"kind": req.Kind, "body": req.Body, "meeting_id": req.MeetingID,
			"investigation_id": req.InvestigationID,
		})
	if _, err := s.appender.Append(ctx, ev); err != nil {
		return nil, err
	}

	return s.Get(ctx, req.OrganizationID, id)
}

// Get returns one message.
func (s *Service) Get(ctx context.Context, orgID, messageID string) (*Message, error) {
	var m Message
	var createdAt time.Time
	var meetingID, invID, recipient, parent *string
	err := s.pool.QueryRow(ctx, `
		SELECT id, organization_id, meeting_id, investigation_id, sender_worker_id,
		       recipient_worker_id, content, message_type, parent_message_id, created_at
		FROM messages WHERE id = $1 AND organization_id = $2`, messageID, orgID).
		Scan(&m.MessageID, &m.OrganizationID, &meetingID, &invID, &m.SenderWorkerID,
			&recipient, &m.Content, &m.MessageType, &parent, &createdAt)
	if err != nil {
		if err == pgx.ErrNoRows {
			return nil, ErrNotFound
		}
		return nil, err
	}
	m.MeetingID = deref(meetingID)
	m.InvestigationID = deref(invID)
	m.RecipientWorkerID = deref(recipient)
	m.ParentMessageID = deref(parent)
	m.CreatedAt = createdAt.Format(time.RFC3339)
	return &m, nil
}

// List returns messages for an org, optionally filtered by meeting,
// investigation, sender, or recipient. Append-only, time-ordered.
func (s *Service) List(ctx context.Context, orgID, meetingID, investigationID, senderID, recipientID string, limit int) ([]Message, error) {
	if limit <= 0 || limit > 200 {
		limit = 100
	}
	rows, err := s.pool.Query(ctx, `
		SELECT id, organization_id, meeting_id, investigation_id, sender_worker_id,
		       recipient_worker_id, content, message_type, parent_message_id, created_at
		FROM messages
		WHERE organization_id = $1
		  AND ($2::uuid IS NULL OR meeting_id = $2::uuid)
		  AND ($3::uuid IS NULL OR investigation_id = $3::uuid)
		  AND ($4::uuid IS NULL OR sender_worker_id = $4::uuid)
		  AND ($5::uuid IS NULL OR recipient_worker_id = $5::uuid)
		ORDER BY created_at DESC LIMIT $6`,
		orgID, nullIfEmpty(meetingID), nullIfEmpty(investigationID),
		nullIfEmpty(senderID), nullIfEmpty(recipientID), limit)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	var out []Message
	for rows.Next() {
		var m Message
		var createdAt time.Time
		var meetingIDV, invID, recipient, parent *string
		if err := rows.Scan(&m.MessageID, &m.OrganizationID, &meetingIDV, &invID, &m.SenderWorkerID,
			&recipient, &m.Content, &m.MessageType, &parent, &createdAt); err != nil {
			return nil, err
		}
		m.MeetingID = deref(meetingIDV)
		m.InvestigationID = deref(invID)
		m.RecipientWorkerID = deref(recipient)
		m.ParentMessageID = deref(parent)
		m.CreatedAt = createdAt.Format(time.RFC3339)
		out = append(out, m)
	}
	return out, rows.Err()
}

// ── helpers ────────────────────────────────────────────────────────────

func nullIfEmpty(s string) interface{} {
	if s == "" {
		return nil
	}
	return s
}

func deref(s *string) string {
	if s == nil {
		return ""
	}
	return *s
}

func newUUID() string {
	b := make([]byte, 16)
	_, _ = rand.Read(b)
	b[6] = (b[6] & 0x0f) | 0x40
	b[8] = (b[8] & 0x3f) | 0x80
	s := hex.EncodeToString(b)
	return s[0:8] + "-" + s[8:12] + "-" + s[12:16] + "-" + s[16:20] + "-" + s[20:32]
}

// SetEventPublisher wires the NATS publisher for event fan-out.
func (s *Service) SetEventPublisher(p events.Publisher) {
	s.appender.SetPublisher(p)
}
