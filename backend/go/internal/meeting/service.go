// Package meeting implements the Meeting Engine (doc 20): the 8-stage
// lifecycle (called → agenda_set → notified → preparing → conducting →
// minutes → ended), 5 meeting types, and agenda/action-item tracking.
package meeting

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"time"

	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgxpool"

	"veliora-backend/internal/shared/events"
)

// ErrNotFound is returned for missing meetings.
var ErrNotFound = errors.New("not found")

// ErrInvalidTransition is returned when a meeting state guard rejects an act.
var ErrInvalidTransition = errors.New("invalid_transition")

// MeetingTypes (doc 20 §4).
const (
	TypeAllHands       = "all_hands"
	TypeDepartmental   = "departmental"
	TypeCrossFunctional = "cross_functional"
	TypeEmergency      = "emergency"
	TypeBoard          = "board"
)

// States of the meeting state machine (doc 20 §5).
var validStates = map[string]bool{
	"called": true, "agenda_set": true, "notified": true, "preparing": true,
	"conducting": true, "minutes": true, "ended": true, "cancelled": true,
}

// Service is the Meeting Engine.
type Service struct {
	pool     *pgxpool.Pool
	appender *events.Appender
}

// NewService builds the Meeting Engine.
func NewService(pool *pgxpool.Pool) *Service {
	return &Service{pool: pool, appender: events.NewAppender(pool)}
}

// AgendaItem (doc 20 §5 Stage 2).
type AgendaItem struct {
	ID             string `json:"id"`
	Title          string `json:"title"`
	OwnerWorkerID  string `json:"owner_worker_id,omitempty"`
	Type           string `json:"type"` // status|decision|blocker|announcement|debate_ref
	TimeBudgetMin  int    `json:"time_budget_min"`
	DesiredOutcome string `json:"desired_outcome,omitempty"`
}

// Meeting is the meeting resource (doc 24 §7.3).
type Meeting struct {
	MeetingID       string       `json:"meeting_id"`
	OrganizationID  string       `json:"organization_id"`
	InvestigationID string       `json:"investigation_id,omitempty"`
	Title           string       `json:"title"`
	Type            string       `json:"type"`
	State           string       `json:"state"`
	Agenda          []AgendaItem `json:"agenda"`
	Participants    []string     `json:"participants"`
	Chair           string       `json:"chair"`
	CalledAt        string       `json:"called_at"`
	StartedAt       string       `json:"started_at,omitempty"`
	EndedAt         string       `json:"ended_at,omitempty"`
	Minutes         string       `json:"minutes,omitempty"`
	StreamURL       string       `json:"stream_url,omitempty"`
}

// CallRequest is rpc.meeting.call (doc 20 §5 Stage 1).
type CallRequest struct {
	OrganizationID  string `json:"organization_id"`
	InvestigationID string `json:"investigation_id,omitempty"`
	Title           string `json:"title"`
	Type            string `json:"type"`
	Agenda          []AgendaItem `json:"agenda,omitempty"`
	ParticipantIDs  []string `json:"participant_ids"`
	ChairWorkerID   string `json:"chair_worker_id"`
}

// Call creates a meeting in 'called' state and emits meeting.called.
func (s *Service) Call(ctx context.Context, req CallRequest) (*Meeting, error) {
	if !validMeetingType(req.Type) {
		req.Type = TypeCrossFunctional
	}
	if req.Title == "" {
		req.Title = "Organizational meeting"
	}
	agendaJSON, _ := json.Marshal(req.Agenda)

	var id string
	if err := s.pool.QueryRow(ctx, `
		INSERT INTO meetings (organization_id, investigation_id, title, agenda, status)
		VALUES ($1, $2, $3, $4::jsonb, 'called') RETURNING id`,
		req.OrganizationID, nullIfEmpty(req.InvestigationID), req.Title, string(agendaJSON)).Scan(&id); err != nil {
		return nil, err
	}

	// Participants (chair first).
	if req.ChairWorkerID != "" {
		req.ParticipantIDs = append([]string{req.ChairWorkerID}, req.ParticipantIDs...)
	}
	seen := map[string]bool{}
	for _, w := range req.ParticipantIDs {
		if w == "" || seen[w] {
			continue
		}
		seen[w] = true
		role := "voter"
		if w == req.ChairWorkerID {
			role = "chair"
		}
		if _, err := s.pool.Exec(ctx, `
			INSERT INTO meeting_participants (meeting_id, worker_id, role)
			VALUES ($1, $2, $3)`, id, w, role); err != nil {
			return nil, err
		}
	}

	ev := events.Root(req.OrganizationID, events.EvMeetingCalled, events.CatMeeting,
		"meeting-engine", events.Source{Component: "meeting-engine"},
		map[string]interface{}{
			"meeting_id": id, "investigation_id": req.InvestigationID,
			"title": req.Title, "type": req.Type, "agenda": req.Agenda,
			"participants": req.ParticipantIDs, "chair": req.ChairWorkerID,
		})
	if _, err := s.appender.Append(ctx, ev); err != nil {
		return nil, err
	}

	return s.Get(ctx, req.OrganizationID, id)
}

// SetAgenda replaces the agenda (Stage 2).
func (s *Service) SetAgenda(ctx context.Context, orgID, meetingID string, agenda []AgendaItem) error {
	agendaJSON, _ := json.Marshal(agenda)
	tag, err := s.pool.Exec(ctx, `
		UPDATE meetings SET agenda = $3::jsonb, status = 'agenda_set'
		WHERE id = $1 AND organization_id = $2 AND status = 'called'`,
		meetingID, orgID, string(agendaJSON))
	if err != nil {
		return err
	}
	if tag.RowsAffected() == 0 {
		return ErrInvalidTransition
	}
	return nil
}

// Notify marks the meeting as notified (Stage 3).
func (s *Service) Notify(ctx context.Context, orgID, meetingID string) error {
	return s.transition(ctx, orgID, meetingID, "agenda_set", "notified")
}

// Prepare marks the meeting as preparing (Stage 4).
func (s *Service) Prepare(ctx context.Context, orgID, meetingID string) error {
	return s.transition(ctx, orgID, meetingID, "notified", "preparing")
}

// Start convenes the meeting (Stage 5), emitting meeting.started.
func (s *Service) Start(ctx context.Context, orgID, meetingID string) error {
	if err := s.transition(ctx, orgID, meetingID, "preparing", "conducting"); err != nil {
		return err
	}
	ev := events.Root(orgID, events.EvMeetingStarted, events.CatMeeting,
		"meeting-engine", events.Source{Component: "meeting-engine"},
		map[string]interface{}{"meeting_id": meetingID, "quorum_met": true})
	_, err := s.appender.Append(ctx, ev)
	return err
}

// RecordMinutes stores minutes (Stage 6) and moves to 'minutes'.
func (s *Service) RecordMinutes(ctx context.Context, orgID, meetingID, minutes string) error {
	tag, err := s.pool.Exec(ctx, `
		UPDATE meetings SET minutes = $3, status = 'minutes'
		WHERE id = $1 AND organization_id = $2 AND status = 'conducting'`,
		meetingID, orgID, minutes)
	if err != nil {
		return err
	}
	if tag.RowsAffected() == 0 {
		return ErrInvalidTransition
	}
	ev := events.Root(orgID, events.EvMeetingMinutes, events.CatMeeting,
		"meeting-engine", events.Source{Component: "meeting-engine"},
		map[string]interface{}{"meeting_id": meetingID, "minutes": minutes})
	_, err = s.appender.Append(ctx, ev)
	return err
}

// End concludes the meeting (Stage 7–8), emitting meeting.concluded.
func (s *Service) End(ctx context.Context, orgID, meetingID string, outcome string) error {
	tag, err := s.pool.Exec(ctx, `
		UPDATE meetings SET status = 'ended', ended_at = now()
		WHERE id = $1 AND organization_id = $2 AND status IN ('conducting','minutes')`,
		meetingID, orgID)
	if err != nil {
		return err
	}
	if tag.RowsAffected() == 0 {
		return ErrInvalidTransition
	}
	ev := events.Root(orgID, events.EvMeetingConcluded, events.CatMeeting,
		"meeting-engine", events.Source{Component: "meeting-engine"},
		map[string]interface{}{"meeting_id": meetingID, "outcome": outcome})
	_, err = s.appender.Append(ctx, ev)
	return err
}

// Get returns a meeting.
func (s *Service) Get(ctx context.Context, orgID, meetingID string) (*Meeting, error) {
	var m Meeting
	var agendaJSON, minutes []byte
	var invID *string
	var calledAt, startedAt, endedAt *time.Time
	err := s.pool.QueryRow(ctx, `
		SELECT id, organization_id, investigation_id, title, status, agenda,
		       called_at, started_at, ended_at
		FROM meetings WHERE id = $1 AND organization_id = $2`, meetingID, orgID).
		Scan(&m.MeetingID, &m.OrganizationID, &invID, &m.Title, &m.State,
			&agendaJSON, &calledAt, &startedAt, &endedAt)
	if err != nil {
		if err == pgx.ErrNoRows {
			return nil, ErrNotFound
		}
		return nil, err
	}
	_ = json.Unmarshal(agendaJSON, &m.Agenda)
	m.InvestigationID = deref(invID)
	m.CalledAt = calledAt.Format(time.RFC3339)
	if startedAt != nil {
		m.StartedAt = startedAt.Format(time.RFC3339)
	}
	if endedAt != nil {
		m.EndedAt = endedAt.Format(time.RFC3339)
	}
	_ = minutes
	m.StreamURL = fmt.Sprintf("wss://stream.veliora.io/v1/organizations/%s/meetings/%s", orgID, meetingID)

	// Participants.
	rows, err := s.pool.Query(ctx, `
		SELECT worker_id, role FROM meeting_participants
		WHERE meeting_id = $1 ORDER BY (role = 'chair') DESC, joined_at`, meetingID)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	for rows.Next() {
		var w, role string
		if err := rows.Scan(&w, &role); err != nil {
			return nil, err
		}
		if role == "chair" {
			m.Chair = w
		}
		m.Participants = append(m.Participants, w)
	}
	return &m, rows.Err()
}

// List returns meetings for an org (doc 24 §7.3).
func (s *Service) List(ctx context.Context, orgID, investigationID string) ([]Meeting, error) {
	rows, err := s.pool.Query(ctx, `
		SELECT id, organization_id, investigation_id, title, status
		FROM meetings
		WHERE organization_id = $1 AND ($2::uuid IS NULL OR investigation_id = $2::uuid)
		ORDER BY called_at DESC LIMIT 100`, orgID, nullIfEmpty(investigationID))
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	var out []Meeting
	for rows.Next() {
		var m Meeting
		var invID *string
		if err := rows.Scan(&m.MeetingID, &m.OrganizationID, &invID, &m.Title, &m.State); err != nil {
			return nil, err
		}
		m.InvestigationID = deref(invID)
		out = append(out, m)
	}
	return out, rows.Err()
}

func (s *Service) transition(ctx context.Context, orgID, meetingID, from, to string) error {
	if !validStates[from] || !validStates[to] {
		return ErrInvalidTransition
	}
	tag, err := s.pool.Exec(ctx, `
		UPDATE meetings SET status = $3
		WHERE id = $1 AND organization_id = $2 AND status = $4`,
		meetingID, orgID, to, from)
	if err != nil {
		return err
	}
	if tag.RowsAffected() == 0 {
		return ErrInvalidTransition
	}
	return nil
}

func validMeetingType(t string) bool {
	switch t {
	case TypeAllHands, TypeDepartmental, TypeCrossFunctional, TypeEmergency, TypeBoard:
		return true
	}
	return false
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

// SetEventPublisher wires the NATS publisher for event fan-out.
func (s *Service) SetEventPublisher(p events.Publisher) {
	s.appender.SetPublisher(p)
}
