// Package evolution implements the Evolution Engine (doc 07, doc 24 §8.5,
// doc 25 §7): structural self-modification proposals (hire/fire/promote/
// merge/split/policy/dna_shift/amend_constitution) that traverse
// proposed → debating → voting → approved|rejected → executing → applied.
package evolution

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

// ErrNotFound is returned for missing proposals.
var ErrNotFound = errors.New("not found")

// ErrInvalidTransition is returned when a proposal state guard rejects an act.
var ErrInvalidTransition = errors.New("invalid_transition")

// Event types (doc 13 §13.3.9).
const (
	TypeHire             = "hire"
	TypeFire             = "fire"
	TypePromote          = "promote"
	TypeCreateDepartment = "create_department"
	TypeSplitDepartment  = "split_department"
	TypeMergeDepartment  = "merge_department"
	TypePolicyRewrite    = "policy_rewrite"
	TypeDNAShift         = "dna_shift"
	TypeAmendConstitution = "amend_constitution"
)

// States (doc 24 §8.5).
var validStates = map[string]bool{
	"proposed": true, "debating": true, "voting": true,
	"approved": true, "rejected": true, "executing": true,
	"applied": true, "rolled_back": true,
}

// Service is the Evolution Engine.
type Service struct {
	pool     *pgxpool.Pool
	appender *events.Appender
	// ApplyFn executes the structural change (set by wiring; e.g. spawn a
	// worker via org-manager). Receives the proposal and before/after JSON.
	ApplyFn func(ctx context.Context, orgID string, ev EvolutionEvent) (afterState map[string]interface{}, err error)
}

// NewService builds the Evolution Engine.
func NewService(pool *pgxpool.Pool) *Service {
	return &Service{pool: pool, appender: events.NewAppender(pool)}
}

// EvolutionEvent is the proposal resource (doc 24 §8.5).
type EvolutionEvent struct {
	EventID         string                 `json:"event_id"`
	OrganizationID  string                 `json:"organization_id"`
	EventType       string                 `json:"event_type"`
	TargetType      string                 `json:"target_type"`
	TargetID        string                 `json:"target_id"`
	Description     string                 `json:"description"`
	Rationale       string                 `json:"rationale"`
	Status          string                 `json:"status"`
	TriggeredByRule string                 `json:"triggered_by_rule,omitempty"`
	BeforeState     map[string]interface{} `json:"before_state,omitempty"`
	AfterState      map[string]interface{} `json:"after_state,omitempty"`
	ProposedAt      string                 `json:"proposed_at"`
	ExecutedAt      string                 `json:"executed_at,omitempty"`
}

// ProposeRequest is rpc.evolution.propose (doc 24 §8.5).
type ProposeRequest struct {
	OrganizationID string `json:"organization_id"`
	EventType      string `json:"event_type"`
	TargetType     string `json:"target_type"`
	TargetID       string `json:"target_id"`
	Description    string `json:"description"`
	Rationale      string `json:"rationale"`
	TriggeredByRule string `json:"triggered_by_rule,omitempty"`
	InvestigationID string `json:"triggered_by_investigation_id,omitempty"`
}

// Propose creates an evolution event in 'proposed' and emits evolution.proposed.
func (s *Service) Propose(ctx context.Context, req ProposeRequest) (*EvolutionEvent, error) {
	if !validType(req.EventType) {
		return nil, fmt.Errorf("unknown evolution type: %s", req.EventType)
	}
	if req.TargetType == "" {
		req.TargetType = req.EventType
	}

	var id string
	if err := s.pool.QueryRow(ctx, `
		INSERT INTO evolution_events (organization_id, event_type, target_type, target_id,
			description, rationale, status, triggered_by_rule, triggered_by_investigation_id)
		VALUES ($1, $2, $3, $4, $5, $6, 'proposed', $7, $8) RETURNING id`,
		req.OrganizationID, req.EventType, req.TargetType, req.TargetID,
		req.Description, req.Rationale, nullIfEmpty(req.TriggeredByRule),
		nullIfEmpty(req.InvestigationID)).Scan(&id); err != nil {
		return nil, err
	}

	ev := events.Root(req.OrganizationID, events.EvEvolutionProposed, events.CatEvolution,
		"evolution-engine", events.Source{Component: "evolution-engine"},
		map[string]interface{}{
			"evolution_event_id": id, "event_type": req.EventType,
			"target": req.TargetID, "rationale": req.Rationale,
		})
	if _, err := s.appender.Append(ctx, ev); err != nil {
		return nil, err
	}

	return s.Get(ctx, req.OrganizationID, id)
}

// StartDebate moves proposed → debating.
func (s *Service) StartDebate(ctx context.Context, orgID, evoID string) error {
	return s.transition(ctx, orgID, evoID, "proposed", "debating")
}

// StartVote moves debating → voting.
func (s *Service) StartVote(ctx context.Context, orgID, evoID string) error {
	return s.transition(ctx, orgID, evoID, "debating", "voting")
}

// Approve moves voting → approved and emits evolution.approved.
func (s *Service) Approve(ctx context.Context, orgID, evoID string) error {
	if err := s.transition(ctx, orgID, evoID, "voting", "approved"); err != nil {
		return err
	}
	ev := events.Root(orgID, events.EvEvolutionApproved, events.CatEvolution,
		"evolution-engine", events.Source{Component: "evolution-engine"},
		map[string]interface{}{"evolution_event_id": evoID})
	_, err := s.appender.Append(ctx, ev)
	return err
}

// Reject moves voting → rejected and emits evolution.rejected.
func (s *Service) Reject(ctx context.Context, orgID, evoID string) error {
	if err := s.transition(ctx, orgID, evoID, "voting", "rejected"); err != nil {
		return err
	}
	ev := events.Root(orgID, events.EvEvolutionRejected, events.CatEvolution,
		"evolution-engine", events.Source{Component: "evolution-engine"},
		map[string]interface{}{"evolution_event_id": evoID})
	_, err := s.appender.Append(ctx, ev)
	return err
}

// Execute runs the structural change (approved → executing → applied),
// capturing before/after state and emitting evolution.executed.
func (s *Service) Execute(ctx context.Context, orgID, evoID string) (*EvolutionEvent, error) {
	e, err := s.Get(ctx, orgID, evoID)
	if err != nil {
		return nil, err
	}
	if e.Status != "approved" {
		return nil, ErrInvalidTransition
	}
	if err := s.transition(ctx, orgID, evoID, "approved", "executing"); err != nil {
		return nil, err
	}

	// Capture before state (snapshot of the org's worker/dept counts).
	before := s.snapshot(ctx, orgID)

	after := before
	if s.ApplyFn != nil {
		afterState, err := s.ApplyFn(ctx, orgID, *e)
		if err != nil {
			// Roll back on failure.
			_, _ = s.Rollback(ctx, orgID, evoID, err.Error())
			return nil, err
		}
		after = afterState
	}
	afterJSON, _ := json.Marshal(after)
	beforeJSON, _ := json.Marshal(before)

	if _, err := s.pool.Exec(ctx, `
		UPDATE evolution_events SET status = 'applied', before_state = $3::jsonb,
			after_state = $4::jsonb, executed_at = now()
		WHERE id = $1 AND organization_id = $2`, evoID, orgID, string(beforeJSON), string(afterJSON)); err != nil {
		return nil, err
	}

	ev := events.Root(orgID, events.EvEvolutionExecuted, events.CatEvolution,
		"evolution-engine", events.Source{Component: "evolution-engine"},
		map[string]interface{}{
			"evolution_event_id": evoID, "before_state": before, "after_state": after,
		})
	if _, err := s.appender.Append(ctx, ev); err != nil {
		return nil, err
	}

	return s.Get(ctx, orgID, evoID)
}

// Rollback marks a failed/rolled-back execution (doc 25 §7).
func (s *Service) Rollback(ctx context.Context, orgID, evoID, reason string) (*EvolutionEvent, error) {
	if _, err := s.pool.Exec(ctx, `
		UPDATE evolution_events SET status = 'rolled_back' WHERE id = $1 AND organization_id = $2`,
		evoID, orgID); err != nil {
		return nil, err
	}
	ev := events.Root(orgID, events.EvEvolutionRolledBack, events.CatEvolution,
		"evolution-engine", events.Source{Component: "evolution-engine"},
		map[string]interface{}{"evolution_event_id": evoID, "reason": reason})
	if _, err := s.appender.Append(ctx, ev); err != nil {
		return nil, err
	}
	return s.Get(ctx, orgID, evoID)
}

// Get returns one evolution event.
func (s *Service) Get(ctx context.Context, orgID, evoID string) (*EvolutionEvent, error) {
	var e EvolutionEvent
	var before, after []byte
	var proposedAt, executedAt *time.Time
	var targetID, rule, invID *string
	err := s.pool.QueryRow(ctx, `
		SELECT id, organization_id, event_type, target_type, target_id, description,
		       rationale, status, triggered_by_rule, before_state, after_state, proposed_at, executed_at
		FROM evolution_events WHERE id = $1 AND organization_id = $2`, evoID, orgID).
		Scan(&e.EventID, &e.OrganizationID, &e.EventType, &e.TargetType, &targetID,
			&e.Description, &e.Rationale, &e.Status, &rule, &before, &after, &proposedAt, &executedAt)
	if err != nil {
		if err == pgx.ErrNoRows {
			return nil, ErrNotFound
		}
		return nil, err
	}
	e.TargetID = deref(targetID)
	e.TriggeredByRule = deref(rule)
	e.ProposedAt = proposedAt.Format(time.RFC3339)
	if executedAt != nil {
		e.ExecutedAt = executedAt.Format(time.RFC3339)
	}
	_ = json.Unmarshal(before, &e.BeforeState)
	_ = json.Unmarshal(after, &e.AfterState)
	_ = invID
	return &e, nil
}

// List returns evolution events for an org (doc 24 §8.5 GET).
func (s *Service) List(ctx context.Context, orgID string) ([]EvolutionEvent, error) {
	rows, err := s.pool.Query(ctx, `
		SELECT id, organization_id, event_type, target_type, target_id, description,
		       rationale, status, triggered_by_rule, proposed_at, executed_at
		FROM evolution_events WHERE organization_id = $1 ORDER BY proposed_at DESC LIMIT 100`, orgID)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	var out []EvolutionEvent
	for rows.Next() {
		var e EvolutionEvent
		var targetID, rule *string
		var proposedAt, executedAt *time.Time
		if err := rows.Scan(&e.EventID, &e.OrganizationID, &e.EventType, &e.TargetType, &targetID,
			&e.Description, &e.Rationale, &e.Status, &rule, &proposedAt, &executedAt); err != nil {
			return nil, err
		}
		e.TargetID = deref(targetID)
		e.TriggeredByRule = deref(rule)
		e.ProposedAt = proposedAt.Format(time.RFC3339)
		if executedAt != nil {
			e.ExecutedAt = executedAt.Format(time.RFC3339)
		}
		out = append(out, e)
	}
	return out, rows.Err()
}

// AutoProposals scans for structural signals (doc 05 §3.7): departments with
// many pending tasks → hire proposal; workers with trust < 0.3 → fire proposal;
// high-trust workers → promote proposal. Returns proposals created.
func (s *Service) AutoProposals(ctx context.Context, orgID string) ([]string, error) {
	var created []string

	// Overload signal: departments with > 3 open tasks → hire.
	rows, err := s.pool.Query(ctx, `
		SELECT d.id, d.name, COUNT(t.id) AS open_tasks
		FROM departments d
		JOIN tasks t ON t.department_id = d.id AND t.status IN ('pending','in_progress','blocked')
		WHERE d.organization_id = $1 AND d.status = 'active'
		GROUP BY d.id, d.name HAVING COUNT(t.id) > 3`, orgID)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	type deptLoad struct{ id, name string }
	var overloaded []deptLoad
	for rows.Next() {
		var d deptLoad
		var n int
		if err := rows.Scan(&d.id, &d.name, &n); err != nil {
			return nil, err
		}
		overloaded = append(overloaded, d)
	}
	rows.Close()

	for _, d := range overloaded {
		e, err := s.Propose(ctx, ProposeRequest{
			OrganizationID: orgID, EventType: TypeHire, TargetType: "department",
			TargetID: d.id, Description: "Hire a new specialist",
			Rationale: fmt.Sprintf("Department %s is overloaded (%d open tasks)", d.name, 4),
			TriggeredByRule: "overload",
		})
		if err == nil {
			created = append(created, e.EventID)
		}
	}

	// Low-trust signal: workers with current_trust_score < 30 → fire proposal.
	rows, err = s.pool.Query(ctx, `
		SELECT id, role FROM workers
		WHERE organization_id = $1 AND status = 'active' AND current_trust_score < 30`, orgID)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	for rows.Next() {
		var id, role string
		if err := rows.Scan(&id, &role); err != nil {
			return nil, err
		}
		e, err := s.Propose(ctx, ProposeRequest{
			OrganizationID: orgID, EventType: TypeFire, TargetType: "worker",
			TargetID: id, Description: "Fire underperforming " + role,
			Rationale: "Trust score below 30 (persistent underperformance)",
			TriggeredByRule: "low_trust",
		})
		if err == nil {
			created = append(created, e.EventID)
		}
	}
	return created, nil
}

func (s *Service) transition(ctx context.Context, orgID, evoID, from, to string) error {
	if !validStates[from] || !validStates[to] {
		return ErrInvalidTransition
	}
	tag, err := s.pool.Exec(ctx, `
		UPDATE evolution_events SET status = $3
		WHERE id = $1 AND organization_id = $2 AND status = $4`, evoID, orgID, to, from)
	if err != nil {
		return err
	}
	if tag.RowsAffected() == 0 {
		return ErrInvalidTransition
	}
	return nil
}

// snapshot captures org worker/department counts as the before/after state.
func (s *Service) snapshot(ctx context.Context, orgID string) map[string]interface{} {
	out := map[string]interface{}{}
	var workers, depts int
	_ = s.pool.QueryRow(ctx, `SELECT COUNT(*) FROM workers WHERE organization_id = $1 AND status != 'terminated'`, orgID).Scan(&workers)
	_ = s.pool.QueryRow(ctx, `SELECT COUNT(*) FROM departments WHERE organization_id = $1 AND status = 'active'`, orgID).Scan(&depts)
	out["workers"] = workers
	out["departments"] = depts
	return out
}

func validType(t string) bool {
	switch t {
	case TypeHire, TypeFire, TypePromote, TypeCreateDepartment, TypeSplitDepartment,
		TypeMergeDepartment, TypePolicyRewrite, TypeDNAShift, TypeAmendConstitution:
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
