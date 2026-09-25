// Package caspian implements the Caspian Bridge (doc 12): the organization's
// external communication nervous system. Channels are real adapters, env-gated;
// without credentials, outbound is queued with status 'unsent' (no fake sends).
package caspian

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"os"
	"time"

	"github.com/jackc/pgx/v5/pgxpool"

	"veliora-backend/internal/shared/events"
)

// ErrNotFound is returned for missing contacts/threads.
var ErrNotFound = errors.New("not found")

// Channels (doc 12 §3).
const (
	ChannelEmail    = "email"
	ChannelTelegram = "telegram"
	ChannelSlack    = "slack"
	ChannelDiscord  = "discord"
	ChannelWhatsApp = "whatsapp"
)

// Config carries channel credentials (env-gated).
type Config struct {
	SMTPHost     string
	SMTPPort     int
	SMTPUser     string
	SMTPPassword string
	SMTPFrom     string
	ResendKey    string

	TelegramToken string
}

// FromEnv builds Config from environment variables.
func FromEnv() Config {
	return Config{
		SMTPHost:     env("CASPIAN_SMTP_HOST", ""),
		SMTPPort:     envInt("CASPIAN_SMTP_PORT", 587),
		SMTPUser:     env("CASPIAN_SMTP_USER", ""),
		SMTPPassword: env("CASPIAN_SMTP_PASSWORD", ""),
		SMTPFrom:     env("CASPIAN_SMTP_FROM", ""),
		ResendKey:    env("CASPIAN_RESEND_KEY", ""),
		TelegramToken: env("CASPIAN_TELEGRAM_TOKEN", ""),
	}
}

// Adapter is one external channel implementation.
type Adapter interface {
	Channel() string
	// Send delivers a message; returns the external message id or an error.
	Send(ctx context.Context, to, subject, body string) (string, error)
}

// Service is the Caspian Bridge.
type Service struct {
	pool     *pgxpool.Pool
	appender *events.Appender
	adapters map[string]Adapter
	config   Config
}

// NewService builds the bridge with the configured adapters.
func NewService(pool *pgxpool.Pool, cfg Config) *Service {
	s := &Service{
		pool: pool, appender: events.NewAppender(pool),
		adapters: map[string]Adapter{}, config: cfg,
	}
	// Wire adapters only when credentials exist.
	if cfg.ResendKey != "" || (cfg.SMTPHost != "" && cfg.SMTPUser != "") {
		s.adapters[ChannelEmail] = NewEmailAdapter(cfg)
	}
	if cfg.TelegramToken != "" {
		s.adapters[ChannelTelegram] = NewTelegramAdapter(cfg.TelegramToken)
	}
	return s
}

// Contact is an external contact (doc 13 §13.3.10).
type Contact struct {
	ID               string            `json:"contact_id"`
	OrganizationID   string            `json:"organization_id"`
	Name             string            `json:"name"`
	Role             string            `json:"role,omitempty"`
	Organization     string            `json:"organization,omitempty"`
	Channels         map[string]string `json:"channels"` // channel → address
	RelationshipType string            `json:"relationship_type"`
	TrustScore       float64           `json:"trust_score"`
}

// SendRequest is rpc.caspian.send (doc 12 §5 outbound lifecycle).
type SendRequest struct {
	OrganizationID string `json:"organization_id"`
	WorkerID       string `json:"worker_id"`
	InvestigationID string `json:"investigation_id,omitempty"`
	DepartmentID   string `json:"department_id,omitempty"`
	ContactID      string `json:"contact_id"`
	Channel        string `json:"channel"` // "" = auto-select
	Purpose        string `json:"purpose"` // formal|survey|dev_qa|vendor|consumer|recruiting|support
	Subject        string `json:"subject"`
	Body           string `json:"body"`
	Blocking       bool   `json:"blocking"`
	ExpectedBy     string `json:"expected_by,omitempty"`
	EvidenceType   string `json:"evidence_type"`
}

// Thread is a Caspian communication thread (app Comms screen).
type Thread struct {
	LogID          string `json:"log_id"`
	Channel        string `json:"channel"`
	ContactName    string `json:"contact_name"`
	Direction      string `json:"direction"`
	Subject        string `json:"subject"`
	Content        string `json:"content"`
	Status         string `json:"status"`
	ReceivedAt     string `json:"received_at,omitempty"`
	SentAt         string `json:"sent_at,omitempty"`
	Error          string `json:"error,omitempty"`
	OutreachStatus string `json:"outreach_status,omitempty"`
}

// Send executes the outbound lifecycle: draft → channel select → send (or
// queue as unsent) → log row + outreach record → communication.sent event.
func (s *Service) Send(ctx context.Context, req SendRequest) (*Thread, error) {
	// Resolve contact + channel.
	var contact Contact
	var channelsJSON []byte
	var role, organization *string
	err := s.pool.QueryRow(ctx, `
		SELECT id, organization_id, name, role, organization, channels, relationship_type, trust_score
		FROM external_contacts WHERE id = $1 AND organization_id = $2`,
		req.ContactID, req.OrganizationID).Scan(&contact.ID, &contact.OrganizationID,
		&contact.Name, &role, &organization, &channelsJSON,
		&contact.RelationshipType, &contact.TrustScore)
	if err != nil {
		return nil, fmt.Errorf("contact: %w", err)
	}
	contact.Role = deref(role)
	contact.Organization = deref(organization)
	_ = jsonUnmarshal(channelsJSON, &contact.Channels)

	channel := req.Channel
	if channel == "" {
		channel = selectChannel(contact, req.Purpose)
	}
	if channel == "" {
		return nil, errors.New("no channel available for contact")
	}

	adapter, hasAdapter := s.adapters[channel]
	logStatus := "unsent"
	var logID string
	var caspianID, sendErr string

	if hasAdapter {
		caspianID, err = adapter.Send(ctx, contact.Channels[channel], req.Subject, req.Body)
		if err != nil {
			sendErr = err.Error()
		} else {
			logStatus = "sent"
		}
	} else {
		sendErr = "no credentials configured for channel " + channel
	}

	// Persist the communication log row.
	if err := s.pool.QueryRow(ctx, `
		INSERT INTO communication_logs (organization_id, worker_id, external_contact_id,
			investigation_id, channel, direction, subject, content, caspian_message_id,
			status, error, sent_at)
		VALUES ($1, $2, $3, $4, $5, 'outbound', $6, $7, $8, $9, $10,
			CASE WHEN $9 = 'sent' THEN now() ELSE NULL END)
		RETURNING id`,
		req.OrganizationID, nullIfEmpty(req.WorkerID), req.ContactID,
		nullIfEmpty(req.InvestigationID), channel, req.Subject, req.Body,
		nullIfEmpty(caspianID), logStatus, nullIfEmpty(sendErr)).Scan(&logID); err != nil {
		return nil, err
	}

	// Outreach record (doc 19 §7 CaspianOutreach).
	outreachStatus := "PENDING"
	if logStatus != "sent" {
		outreachStatus = "REFUSED"
	}
	if _, err := s.pool.Exec(ctx, `
		INSERT INTO caspian_outreach (investigation_id, department_id, worker_id, channel,
			recipient, message, evidence_type, blocking, expected_by, status, communication_log_id)
		VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10, $11)`,
		nullIfEmpty(req.InvestigationID), nullIfEmpty(req.DepartmentID), nullIfEmpty(req.WorkerID),
		channel, contact.Channels[channel], req.Body, firstNonEmpty(req.EvidenceType, "external_comm"),
		req.Blocking, nullIfEmpty(req.ExpectedBy), outreachStatus, logID); err != nil {
		return nil, err
	}

	// Emit communication.sent.
	ev := events.Root(req.OrganizationID, events.EvCommSent, events.CatCommunication,
		"caspian-bridge", events.Source{Component: "caspian-bridge", WorkerID: req.WorkerID},
		map[string]interface{}{
			"log_id": logID, "channel": channel, "contact_id": req.ContactID,
			"caspian_message_id": caspianID, "status": logStatus,
			"investigation_id": req.InvestigationID,
		})
	if _, err := s.appender.Append(ctx, ev); err != nil {
		return nil, err
	}

	return s.Thread(ctx, req.OrganizationID, logID)
}

// Thread returns one communication log row as a thread.
func (s *Service) Thread(ctx context.Context, orgID, logID string) (*Thread, error) {
	var t Thread
	var sentAt, receivedAt *time.Time
	var contactName string
	var outreachStatus *string
	err := s.pool.QueryRow(ctx, `
		SELECT l.id, l.channel, COALESCE(c.name, ''), l.direction, l.subject, l.content,
		       l.status, l.sent_at, l.received_at, l.error,
		       (SELECT o.status FROM caspian_outreach o WHERE o.communication_log_id = l.id LIMIT 1)
		FROM communication_logs l
		LEFT JOIN external_contacts c ON c.id = l.external_contact_id
		WHERE l.id = $1 AND l.organization_id = $2`, logID, orgID).
		Scan(&t.LogID, &t.Channel, &contactName, &t.Direction, &t.Subject, &t.Content,
			&t.Status, &sentAt, &receivedAt, &t.Error, &outreachStatus)
	if err != nil {
		return nil, err
	}
	t.ContactName = contactName
	t.OutreachStatus = deref(outreachStatus)
	if sentAt != nil {
		t.SentAt = sentAt.Format(time.RFC3339)
	}
	if receivedAt != nil {
		t.ReceivedAt = receivedAt.Format(time.RFC3339)
	}
	return &t, nil
}

// Threads lists all Caspian threads for an org (app Comms screen).
func (s *Service) Threads(ctx context.Context, orgID string) ([]Thread, error) {
	rows, err := s.pool.Query(ctx, `
		SELECT l.id, l.channel, COALESCE(c.name, ''), l.direction, l.subject, l.content,
		       l.status, l.sent_at, l.received_at, l.error,
		       (SELECT o.status FROM caspian_outreach o WHERE o.communication_log_id = l.id LIMIT 1)
		FROM communication_logs l
		LEFT JOIN external_contacts c ON c.id = l.external_contact_id
		WHERE l.organization_id = $1
		ORDER BY l.created_at DESC LIMIT 100`, orgID)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	var out []Thread
	for rows.Next() {
		var t Thread
		var sentAt, receivedAt *time.Time
		var contactName string
		var outreachStatus *string
		if err := rows.Scan(&t.LogID, &t.Channel, &contactName, &t.Direction, &t.Subject,
			&t.Content, &t.Status, &sentAt, &receivedAt, &t.Error, &outreachStatus); err != nil {
			return nil, err
		}
		t.ContactName = contactName
		t.OutreachStatus = deref(outreachStatus)
		if sentAt != nil {
			t.SentAt = sentAt.Format(time.RFC3339)
		}
		if receivedAt != nil {
			t.ReceivedAt = receivedAt.Format(time.RFC3339)
		}
		out = append(out, t)
	}
	return out, rows.Err()
}

// AddContact upserts an external contact (doc 13 §13.3.10).
func (s *Service) AddContact(ctx context.Context, c Contact) error {
	channelsJSON, _ := jsonMarshal(c.Channels)
	_, err := s.pool.Exec(ctx, `
		INSERT INTO external_contacts (organization_id, name, role, organization, channels, relationship_type, trust_score)
		VALUES ($1, $2, $3, $4, $5::jsonb, $6, $7)
		ON CONFLICT DO NOTHING`,
		c.OrganizationID, c.Name, nullIfEmpty(c.Role), nullIfEmpty(c.Organization),
		string(channelsJSON), c.RelationshipType, c.TrustScore)
	return err
}

// ListContacts returns contacts for an org.
func (s *Service) ListContacts(ctx context.Context, orgID string) ([]Contact, error) {
	rows, err := s.pool.Query(ctx, `
		SELECT id, organization_id, name, role, organization, channels, relationship_type, trust_score
		FROM external_contacts WHERE organization_id = $1 ORDER BY created_at`, orgID)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	var out []Contact
	for rows.Next() {
		var c Contact
		var channelsJSON []byte
		var role, organization *string
		if err := rows.Scan(&c.ID, &c.OrganizationID, &c.Name, &role, &organization,
			&channelsJSON, &c.RelationshipType, &c.TrustScore); err != nil {
			return nil, err
		}
		c.Role = deref(role)
		c.Organization = deref(organization)
		_ = jsonUnmarshal(channelsJSON, &c.Channels)
		out = append(out, c)
	}
	return out, rows.Err()
}

// selectChannel implements doc 12 §4.1: preferred channel, else by purpose.
func selectChannel(c Contact, purpose string) string {
	if preferred, ok := c.Channels["preferred"]; ok && preferred != "" {
		return preferred
	}
	switch purpose {
	case "formal", "recruiting":
		return ChannelEmail
	case "survey":
		return ChannelTelegram
	case "dev_qa":
		return ChannelDiscord
	case "vendor":
		return ChannelSlack
	case "consumer", "support":
		return ChannelWhatsApp
	}
	return ChannelEmail
}

func firstNonEmpty(a, b string) string {
	if a != "" {
		return a
	}
	return b
}

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

func jsonMarshal(v interface{}) ([]byte, error) {
	return json.Marshal(v)
}

func jsonUnmarshal(b []byte, v interface{}) error {
	if len(b) == 0 {
		return nil
	}
	return json.Unmarshal(b, v)
}

func env(key, def string) string {
	if v := os.Getenv(key); v != "" {
		return v
	}
	return def
}

func envInt(key string, def int) int {
	if v := os.Getenv(key); v != "" {
		var n int
		if _, err := fmt.Sscanf(v, "%d", &n); err == nil {
			return n
		}
	}
	return def
}

// SetEventPublisher wires the NATS publisher for event fan-out.
func (s *Service) SetEventPublisher(p events.Publisher) {
	s.appender.SetPublisher(p)
}
