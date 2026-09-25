// Package investigation implements the Investigation Engine (doc 19) —
// the flagship orchestration spine. It drives the full state machine
// (doc 19 §5, doc 25 §4): created → planning → researching → analyzing →
// debating → synthesizing → vote → confidence_chk → (research loop |
// reporting → completed), plus cancelled/failed.
package investigation

import (
	"context"
	"crypto/rand"
	"encoding/hex"
	"encoding/json"
	"errors"
	"fmt"
	"time"

	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgxpool"

	"veliora-backend/internal/shared/events"
)

// ErrNotFound is returned for missing investigations.
var ErrNotFound = errors.New("not found")

// ErrInvalidTransition is returned when a state-machine guard rejects an act
// (doc 19 §5.2 transition table).
var ErrInvalidTransition = errors.New("invalid_transition")

// States (doc 25 §4.1).
const (
	StateCreated      = "created"
	StatePlanning     = "planning"
	StateResearching  = "researching"
	StateAnalyzing    = "analyzing"
	StateDebating     = "debating"
	StateSynthesizing = "synthesizing"
	StateVote         = "vote"
	StateConfidenceChk = "confidence_chk"
	StateReporting    = "reporting"
	StateCompleted    = "completed"
	StateCancelled    = "cancelled"
	StateFailed       = "failed"
	StateArchived     = "archived"
)

// Phases (doc 19 §4).
const (
	PhaseScoping = "scoping"
	PhaseResearch = "research"
	PhaseAnalysis = "analysis"
	PhaseDebate  = "debate"
	PhaseSynthesis = "synthesis"
	PhaseReport  = "report"
)

// valid transitions (doc 19 §5.2).
var transitions = map[string]map[string]bool{
	StateCreated:       {StatePlanning: true, StateCancelled: true},
	StatePlanning:      {StateResearching: true, StateCancelled: true, StateFailed: true},
	StateResearching:   {StateAnalyzing: true, StateCancelled: true, StateFailed: true},
	StateAnalyzing:     {StateDebating: true, StateCancelled: true, StateFailed: true},
	StateDebating:      {StateSynthesizing: true, StateCancelled: true, StateFailed: true},
	StateSynthesizing:  {StateVote: true, StateCancelled: true, StateFailed: true},
	StateVote:          {StateConfidenceChk: true, StateCancelled: true},
	StateConfidenceChk: {StateResearching: true, StateReporting: true, StateFailed: true},
	StateReporting:     {StateCompleted: true, StateFailed: true},
	StateCompleted:     {StateArchived: true},
}

// Service is the Investigation Engine.
type Service struct {
	pool     *pgxpool.Pool
	appender *events.Appender
	// TaskDispatchFn is called to push tasks to the worker runtime
	// (set by the service wiring; Phase 12).
	TaskDispatchFn func(ctx context.Context, orgID, invID string, plan Plan) error
}

// NewService builds the Investigation Engine.
func NewService(pool *pgxpool.Pool) *Service {
	return &Service{pool: pool, appender: events.NewAppender(pool)}
}

// Charter is the immutable investigation charter (doc 19 §4.1).
type Charter struct {
	Goal               string   `json:"goal"`
	Departments        []string `json:"departments"`
	SuccessCriteria    []string `json:"success_criteria"`
	ConfidenceThreshold float64 `json:"confidence_threshold"`
	Priority           string   `json:"priority"`
	Deadline           string   `json:"deadline,omitempty"`
}

// Task is a planner task (doc 18 Task).
type Task struct {
	ID           string `json:"task_id"`
	Department   string `json:"department"`
	Description  string `json:"description"`
	Deliverables string `json:"deliverables"`
	DependsOn    []string `json:"depends_on,omitempty"`
	Status       string `json:"status"`
}

// Plan is the task graph (doc 18).
type Plan struct {
	Tasks []Task `json:"tasks"`
}

// Investigation is the resource (doc 24 §6.2).
type Investigation struct {
	InvestigationID string  `json:"investigation_id"`
	OrganizationID  string  `json:"organization_id"`
	Title           string  `json:"title"`
	Prompt          string  `json:"prompt"`
	State           string  `json:"state"`
	Phase           string  `json:"current_phase"`
	Charter         *Charter `json:"charter,omitempty"`
	Plan            Plan    `json:"plan"`
	CycleCount      int     `json:"cycle_count"`
	Confidence      float64 `json:"confidence,omitempty"`
	CreatedAt       string  `json:"created_at"`
	CompletedAt     string  `json:"completed_at,omitempty"`
	ReportID        string  `json:"report_id,omitempty"`
}

// CreateRequest is POST /v1/organizations/{id}/investigations (doc 24 §6.1).
type CreateRequest struct {
	OrganizationID string  `json:"organization_id"`
	Prompt         string  `json:"prompt"`
	Priority       string  `json:"priority"` // low|normal|high|critical
	Depth          string  `json:"depth"`    // quick|standard|deep
	Deadline       string  `json:"deadline,omitempty"`
	PlanFn         func(ctx context.Context, orgID, prompt string) (Plan, error) `json:"-"`
}

// Create starts an investigation: created → planning (charter signed),
// then planning → researching with a task graph. Returns the investigation.
func (s *Service) Create(ctx context.Context, req CreateRequest) (*Investigation, error) {
	if req.Prompt == "" {
		return nil, errors.New("prompt required")
	}
	if req.Priority == "" {
		req.Priority = "normal"
	}
	if req.Depth == "" {
		req.Depth = "standard"
	}

	id := newUUID()
	var title string
	if len(req.Prompt) > 60 {
		title = req.Prompt[:60] + "…"
	} else {
		title = req.Prompt
	}

	charter := Charter{
		Goal: req.Prompt, Priority: req.Priority,
		ConfidenceThreshold: 0.72,
		Departments: []string{"CEO Office", "Research Division", "Engineering", "Finance", "Marketing", "Legal"},
	}
	charterJSON, _ := json.Marshal(charter)

	if _, err := s.pool.Exec(ctx, `
		INSERT INTO investigations (id, organization_id, title, prompt, status, current_phase, charter, cycle_count)
		VALUES ($1, $2, $3, $4, 'planning', 'scoping', $5::jsonb, 1)`,
		id, req.OrganizationID, title, req.Prompt, string(charterJSON)); err != nil {
		return nil, err
	}

	root := events.Root(req.OrganizationID, events.EvInvStarted, events.CatInvestigation,
		"investigation-engine", events.Source{Component: "investigation-engine"},
		map[string]interface{}{
			"investigation_id": id, "title": title, "prompt": req.Prompt, "depth": req.Depth,
		})
	if _, err := s.appender.Append(ctx, root); err != nil {
		return nil, err
	}

	// Charter signed event.
	charterEv := events.Child(root, req.OrganizationID, events.EvInvCharter, events.CatInvestigation,
		"investigation-engine", events.Source{Component: "investigation-engine"},
		map[string]interface{}{"investigation_id": id, "charter": charter})
	if _, err := s.appender.Append(ctx, charterEv); err != nil {
		return nil, err
	}

	return s.Get(ctx, req.OrganizationID, id)
}

// StartPlanning runs the planner (Phase 12 LLM) and transitions to researching.
// When req.PlanFn is nil, a deterministic fallback plan is used.
func (s *Service) StartPlanning(ctx context.Context, orgID, invID string, req CreateRequest) error {
	if err := s.transition(ctx, orgID, invID, StatePlanning, StateResearching); err != nil {
		return err
	}
	// Set phase.
	plan := Plan{}
	var err error
	if req.PlanFn != nil {
		plan, err = req.PlanFn(ctx, orgID, req.Prompt)
		if err != nil {
			plan = fallbackPlan(req.Prompt)
		}
	} else {
		plan = fallbackPlan(req.Prompt)
	}
	planJSON, _ := json.Marshal(plan)
	if _, err := s.pool.Exec(ctx, `
		UPDATE investigations SET plan = $3::jsonb, current_phase = 'research'
		WHERE id = $1 AND organization_id = $2`, invID, orgID, string(planJSON)); err != nil {
		return err
	}

	// Emit phase_changed.
	ev := events.Root(orgID, events.EvInvPhase, events.CatInvestigation,
		"investigation-engine", events.Source{Component: "investigation-engine"},
		map[string]interface{}{"investigation_id": invID, "from_phase": "planning", "to_phase": "researching"})
	if _, err := s.appender.Append(ctx, ev); err != nil {
		return err
	}

	// Dispatch tasks to workers.
	if s.TaskDispatchFn != nil {
		if err := s.TaskDispatchFn(ctx, orgID, invID, plan); err != nil {
			return fmt.Errorf("dispatch: %w", err)
		}
	}
	return nil
}

// Transition applies a guarded state transition + emits investigation.phase_changed.
func (s *Service) Transition(ctx context.Context, orgID, invID, to string) error {
	return s.transition(ctx, orgID, invID, "", to)
}

func (s *Service) transition(ctx context.Context, orgID, invID, from, to string) error {
	// Load current state.
	var cur string
	if err := s.pool.QueryRow(ctx, `
		SELECT status FROM investigations WHERE id = $1 AND organization_id = $2`,
		invID, orgID).Scan(&cur); err != nil {
		if err == pgx.ErrNoRows {
			return ErrNotFound
		}
		return err
	}
	if from != "" && cur != from {
		return ErrInvalidTransition
	}
	if !transitions[cur][to] {
		return fmt.Errorf("%w: %s → %s", ErrInvalidTransition, cur, to)
	}

	// Special guard: confidence loop capped at max_cycles (doc 19 §9).
	if to == StateResearching && cur == StateConfidenceChk {
		var cycles int
		if err := s.pool.QueryRow(ctx,
			`SELECT cycle_count FROM investigations WHERE id = $1`, invID).Scan(&cycles); err != nil {
			return err
		}
		if cycles >= 3 {
			return fmt.Errorf("%w: max cycles reached", ErrInvalidTransition)
		}
		if _, err := s.pool.Exec(ctx, `
			UPDATE investigations SET cycle_count = cycle_count + 1 WHERE id = $1`, invID); err != nil {
			return err
		}
	}

	if _, err := s.pool.Exec(ctx, `
		UPDATE investigations SET status = $3, current_phase = $4
		WHERE id = $1 AND organization_id = $2`, invID, orgID, to, phaseForState(to)); err != nil {
		return err
	}

	ev := events.Root(orgID, events.EvInvPhase, events.CatInvestigation,
		"investigation-engine", events.Source{Component: "investigation-engine"},
		map[string]interface{}{
			"investigation_id": invID, "from_phase": cur, "to_phase": to,
		})
	_, err := s.appender.Append(ctx, ev)
	return err
}

// SetConfidence records the org confidence (doc 19 §9) and either loops to
// research (below threshold, cycles < max) or moves to reporting.
func (s *Service) SetConfidence(ctx context.Context, orgID, invID string, confidence float64) error {
	if err := s.transition(ctx, orgID, invID, StateVote, StateConfidenceChk); err != nil {
		return err
	}
	if _, err := s.pool.Exec(ctx, `
		UPDATE investigations SET confidence_score = $2 WHERE id = $1`, invID, confidence); err != nil {
		return err
	}
	ev := events.Root(orgID, events.EvInvConfidence, events.CatInvestigation,
		"investigation-engine", events.Source{Component: "investigation-engine"},
		map[string]interface{}{
			"investigation_id": invID, "confidence": confidence, "threshold": 0.72,
		})
	if _, err := s.appender.Append(ctx, ev); err != nil {
		return err
	}

	// Decide: loop or report.
	if confidence < 0.72 {
		if err := s.transition(ctx, orgID, invID, StateConfidenceChk, StateResearching); err != nil {
			// Max cycles reached → deliver with caveat.
			if err := s.transition(ctx, orgID, invID, StateConfidenceChk, StateReporting); err != nil {
				return err
			}
		}
	} else {
		if err := s.transition(ctx, orgID, invID, StateConfidenceChk, StateReporting); err != nil {
			return err
		}
	}
	return nil
}

// Complete finishes the investigation: reporting → completed, emits
// investigation.completed + report_ready.
func (s *Service) Complete(ctx context.Context, orgID, invID string, reportID string) error {
	if err := s.transition(ctx, orgID, invID, StateReporting, StateCompleted); err != nil {
		return err
	}
	if _, err := s.pool.Exec(ctx, `
		UPDATE investigations SET completed_at = now() WHERE id = $1`, invID); err != nil {
		return err
	}
	ev := events.Root(orgID, events.EvInvCompleted, events.CatInvestigation,
		"investigation-engine", events.Source{Component: "investigation-engine"},
		map[string]interface{}{"investigation_id": invID, "report_id": reportID})
	if _, err := s.appender.Append(ctx, ev); err != nil {
		return err
	}
	ready := events.Root(orgID, events.EvInvReportReady, events.CatInvestigation,
		"investigation-engine", events.Source{Component: "investigation-engine"},
		map[string]interface{}{"investigation_id": invID, "report_id": reportID, "formats": []string{"json"}})
	_, err := s.appender.Append(ctx, ready)
	return err
}

// Cancel transitions any non-terminal state → cancelled.
func (s *Service) Cancel(ctx context.Context, orgID, invID string) error {
	var cur string
	if err := s.pool.QueryRow(ctx, `SELECT status FROM investigations WHERE id = $1`, invID).Scan(&cur); err != nil {
		return err
	}
	if cur == StateCompleted || cur == StateCancelled || cur == StateArchived {
		return ErrInvalidTransition
	}
	if _, err := s.pool.Exec(ctx, `UPDATE investigations SET status = 'cancelled' WHERE id = $1`, invID); err != nil {
		return err
	}
	ev := events.Root(orgID, events.EvInvCancelled, events.CatInvestigation,
		"investigation-engine", events.Source{Component: "investigation-engine"},
		map[string]interface{}{"investigation_id": invID})
	_, err := s.appender.Append(ctx, ev)
	return err
}

// Get returns an investigation.
func (s *Service) Get(ctx context.Context, orgID, invID string) (*Investigation, error) {
	var inv Investigation
	var charterJSON, planJSON []byte
	var confidence *float64
	var completedAt *time.Time
	var createdAt time.Time
	err := s.pool.QueryRow(ctx, `
		SELECT id, organization_id, title, prompt, status, current_phase, charter,
		       plan, cycle_count, confidence_score, created_at, completed_at
		FROM investigations WHERE id = $1 AND organization_id = $2`, invID, orgID).
		Scan(&inv.InvestigationID, &inv.OrganizationID, &inv.Title, &inv.Prompt, &inv.State,
			&inv.Phase, &charterJSON, &planJSON, &inv.CycleCount, &confidence, &createdAt, &completedAt)
	if err != nil {
		if err == pgx.ErrNoRows {
			return nil, ErrNotFound
		}
		return nil, err
	}
	if len(charterJSON) > 0 {
		var c Charter
		if json.Unmarshal(charterJSON, &c) == nil {
			inv.Charter = &c
		}
	}
	_ = json.Unmarshal(planJSON, &inv.Plan)
	if confidence != nil {
		inv.Confidence = *confidence
	}
	inv.CreatedAt = createdAt.Format(time.RFC3339)
	if completedAt != nil {
		inv.CompletedAt = completedAt.Format(time.RFC3339)
	}
	return &inv, nil
}

// List returns investigations for an org.
func (s *Service) List(ctx context.Context, orgID string) ([]Investigation, error) {
	rows, err := s.pool.Query(ctx, `
		SELECT id, organization_id, title, prompt, status, current_phase, plan, cycle_count, confidence_score, created_at
		FROM investigations WHERE organization_id = $1 ORDER BY created_at DESC LIMIT 100`, orgID)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	var out []Investigation
	for rows.Next() {
		var inv Investigation
		var planJSON []byte
		var confidence *float64
		var createdAt time.Time
		if err := rows.Scan(&inv.InvestigationID, &inv.OrganizationID, &inv.Title, &inv.Prompt,
			&inv.State, &inv.Phase, &planJSON, &inv.CycleCount, &confidence, &createdAt); err != nil {
			return nil, err
		}
		_ = json.Unmarshal(planJSON, &inv.Plan)
		if confidence != nil {
			inv.Confidence = *confidence
		}
		inv.CreatedAt = createdAt.Format(time.RFC3339)
		out = append(out, inv)
	}
	return out, rows.Err()
}

// fallbackPlan is the deterministic (no-LLM) plan used when the worker
// runtime isn't wired: one task per department (doc 18 worked example).
func fallbackPlan(prompt string) Plan {
	depts := []string{"Research Division", "Engineering", "Finance", "Marketing", "Legal"}
	tasks := make([]Task, 0, len(depts))
	for i, d := range depts {
		tasks = append(tasks, Task{
			ID:          fmt.Sprintf("tsk-%d", i+1),
			Department:  d,
			Description: fmt.Sprintf("Contribute %s analysis for: %s", deptVerb(d), prompt),
			Deliverables: deptDeliverable(d),
			Status:      "pending",
		})
	}
	return Plan{Tasks: tasks}
}

func deptVerb(d string) string {
	switch d {
	case "Research Division":
		return "market and competitor"
	case "Engineering":
		return "technical feasibility"
	case "Finance":
		return "financial viability"
	case "Marketing":
		return "demand"
	case "Legal":
		return "regulatory and compliance"
	}
	return "organizational"
}

func deptDeliverable(d string) string {
	switch d {
	case "Research Division":
		return "market size + competitor brief with sources"
	case "Engineering":
		return "feasibility verdict + effort estimate"
	case "Finance":
		return "24-month cost/revenue model"
	case "Marketing":
		return "demand signal + willingness to pay"
	case "Legal":
		return "compliance risk register"
	}
	return "brief"
}

func phaseForState(state string) string {
	switch state {
	case StatePlanning, StateCreated:
		return PhaseScoping
	case StateResearching:
		return PhaseResearch
	case StateAnalyzing:
		return PhaseAnalysis
	case StateDebating:
		return PhaseDebate
	case StateSynthesizing, StateVote, StateConfidenceChk:
		return PhaseSynthesis
	case StateReporting, StateCompleted, StateArchived:
		return PhaseReport
	}
	return PhaseScoping
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
