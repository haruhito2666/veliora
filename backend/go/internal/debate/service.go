// Package debate implements the Debate Engine (doc 10): Society-of-Thought
// protocol with 6 phases (opening → evidence → cross_exam → rebuttal →
// synthesis → vote), nested evidence challenges, and trust-weighted voting.
package debate

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

// ErrNotFound is returned for missing debates.
var ErrNotFound = errors.New("not found")

// ErrInvalidTransition is returned when a protocol rule is violated
// (doc 10 §6.2 ordering rules).
var ErrInvalidTransition = errors.New("invalid_transition")

// Debate types (doc 10 §4).
const (
	TypeEvidenceChallenge = "evidence_challenge"
	TypePosition          = "position"
	TypePolicy            = "policy"
	TypeStrategic         = "strategic"
)

// Phases of the protocol (doc 10 §5).
const (
	PhaseOpening    = "opening"
	PhaseEvidence   = "evidence"
	PhaseCrossExam  = "cross_exam"
	PhaseRebuttal   = "rebuttal"
	PhaseSynthesis  = "synthesis"
	PhaseVote       = "vote"
	PhaseClosed     = "closed"
)

// valid phases in order.
var phaseOrder = []string{PhaseOpening, PhaseEvidence, PhaseCrossExam, PhaseRebuttal, PhaseSynthesis, PhaseVote, PhaseClosed}

// Service is the Debate Engine.
type Service struct {
	pool     *pgxpool.Pool
	appender *events.Appender
	// WeightFn supplies a worker's trust weight for voting (set by the
	// parent service wiring; defaults to neutral 0.5).
	WeightFn func(ctx context.Context, orgID, workerID string) (float64, error)
}

// NewService builds the Debate Engine.
func NewService(pool *pgxpool.Pool) *Service {
	return &Service{pool: pool, appender: events.NewAppender(pool)}
}

// Debate is the debate resource.
type Debate struct {
	DebateID        string   `json:"debate_id"`
	OrganizationID  string   `json:"organization_id"`
	InvestigationID string   `json:"investigation_id"`
	Topic           string   `json:"topic"`
	Type            string   `json:"debate_type"`
	State           string   `json:"state"`
	Phase           string   `json:"phase"`
	Participants    []string `json:"participants"`
	OpenedAt        string   `json:"opened_at"`
	ConcludedAt     string   `json:"concluded_at,omitempty"`
	Verdict         string   `json:"verdict,omitempty"`
	Tally           map[string]float64 `json:"tally,omitempty"`
}

// OpenRequest is rpc.debate.open (doc 10 §6.1 debate.open).
type OpenRequest struct {
	OrganizationID  string   `json:"organization_id"`
	InvestigationID string   `json:"investigation_id"`
	Topic           string   `json:"topic"`
	Type            string   `json:"debate_type"`
	ParticipantIDs  []string `json:"participant_ids"`
}

// Open creates a debate in PhaseOpening.
func (s *Service) Open(ctx context.Context, req OpenRequest) (*Debate, error) {
	if !validType(req.Type) {
		req.Type = TypeEvidenceChallenge
	}
	if req.Topic == "" {
		return nil, errors.New("topic required")
	}
	if len(req.ParticipantIDs) < 2 {
		return nil, errors.New("a debate needs at least two participants (doc 10 §2)")
	}

	var id string
	if err := s.pool.QueryRow(ctx, `
		INSERT INTO debates (investigation_id, topic, debate_type, status, phase)
		VALUES ($1, $2, $3, 'open', 'opening') RETURNING id`,
		req.InvestigationID, req.Topic, req.Type).Scan(&id); err != nil {
		return nil, err
	}

	// Register opening positions as debate_arguments with stance 'opening'.
	for _, w := range req.ParticipantIDs {
		if _, err := s.pool.Exec(ctx, `
			INSERT INTO debate_arguments (debate_id, worker_id, stance, argument_text)
			VALUES ($1, $2, 'opening', '')`, id, w); err != nil {
			return nil, err
		}
	}

	ev := events.Root(req.OrganizationID, events.EvDebateOpened, events.CatDebate,
		"debate-engine", events.Source{Component: "debate-engine"},
		map[string]interface{}{
			"debate_id": id, "investigation_id": req.InvestigationID,
			"topic": req.Topic, "type": req.Type, "participants": req.ParticipantIDs,
		})
	if _, err := s.appender.Append(ctx, ev); err != nil {
		return nil, err
	}

	return s.Get(ctx, req.OrganizationID, id)
}

// AddArgument records a protocol message (position/evidence/challenge/
// question/answer/rebuttal/synthesis/vote) with ordering validation
// (doc 10 §6.2). Returns the argument id.
func (s *Service) AddArgument(ctx context.Context, orgID, debateID, workerID, kind, text string) (string, error) {
	d, err := s.getRaw(ctx, orgID, debateID)
	if err != nil {
		return "", err
	}

	// Validate kind vs current phase (doc 10 §6.2).
	if err := validateKindPhase(kind, d.Phase); err != nil {
		return "", err
	}

	stance := kind
	switch kind {
	case "position":
		stance = "support"
	case "rebuttal":
		stance = "support"
	case "synthesis":
		stance = "synthesis"
	case "vote":
		stance = text // vote choice stored in stance
	case "challenge":
		stance = "challenge"
	}

	var argID string
	if err := s.pool.QueryRow(ctx, `
		INSERT INTO debate_arguments (debate_id, worker_id, stance, argument_text)
		VALUES ($1, $2, $3, $4) RETURNING id`,
		debateID, workerID, stance, text).Scan(&argID); err != nil {
		return "", err
	}

	// Emit the corresponding debate.* event.
	evType := map[string]string{
		"position": events.EvDebatePosition, "evidence": events.EvDebateEvidence,
		"challenge": events.EvDebateChallenged, "question": events.EvDebateQuestion,
		"answer": events.EvDebateAnswer, "rebuttal": events.EvDebateRebuttal,
		"synthesis": events.EvDebateSynthesis, "vote": events.EvDebateVote,
	}[kind]
	if evType == "" {
		evType = events.EvDebatePosition
	}
	ev := events.Root(orgID, evType, events.CatDebate, "debate-engine",
		events.Source{Component: "debate-engine", WorkerID: workerID},
		map[string]interface{}{"debate_id": debateID, "worker_id": workerID, "kind": kind, "text": text})
	if _, err := s.appender.Append(ctx, ev); err != nil {
		return "", err
	}

	// A vote may trigger automatic conclusion.
	if kind == "vote" && d.Phase == PhaseVote {
		if err := s.concludeIfAllVoted(ctx, orgID, debateID); err != nil {
			return "", err
		}
	}
	return argID, nil
}

// Advance moves the debate to the next phase (guarded).
func (s *Service) Advance(ctx context.Context, orgID, debateID string) error {
	d, err := s.getRaw(ctx, orgID, debateID)
	if err != nil {
		return err
	}
	next := nextPhase(d.Phase)
	if next == "" {
		return ErrInvalidTransition
	}
	if _, err := s.pool.Exec(ctx, `
		UPDATE debates SET phase = $2 WHERE id = $1`,
		debateID, next); err != nil {
		return err
	}
	return nil
}

// Conclude finalizes a debate: computes the trust-weighted tally from votes
// (doc 10 §8.4) and records the verdict + dissenting opinions.
func (s *Service) Conclude(ctx context.Context, orgID, debateID string) (*Debate, error) {
	d, err := s.getRaw(ctx, orgID, debateID)
	if err != nil {
		return nil, err
	}
	if d.Phase != PhaseVote && d.Phase != PhaseSynthesis {
		return nil, ErrInvalidTransition
	}

	// Gather votes (debate_arguments with stance = vote choice).
	rows, err := s.pool.Query(ctx, `
		SELECT worker_id, stance, argument_text FROM debate_arguments
		WHERE debate_id = $1 AND stance IN ('yes','no','abstain','adopt','reject','qualified','GO','HOLD')`,
		debateID)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	tally := map[string]float64{}
	votes := []map[string]interface{}{}
	for rows.Next() {
		var w, choice, reason string
		if err := rows.Scan(&w, &choice, &reason); err != nil {
			return nil, err
		}
		weight := 0.5
		if s.WeightFn != nil {
			if wgt, err := s.WeightFn(ctx, orgID, w); err == nil {
				weight = wgt
			}
		}
		tally[choice] += weight
		votes = append(votes, map[string]interface{}{
			"worker_id": w, "choice": choice, "weight": weight, "reasoning": reason,
		})
	}

	// Verdict = argmax.
	verdict := "no_consensus"
	var best float64
	for choice, w := range tally {
		if choice == "abstain" {
			continue
		}
		if w > best {
			best = w
			verdict = choice
		}
	}
	if best == 0 {
		verdict = "no_consensus"
	}

	if _, err := s.pool.Exec(ctx, `
		UPDATE debates SET status = 'concluded', phase = 'closed', concluded_at = now()
		WHERE id = $1`, debateID); err != nil {
		return nil, err
	}

	// Persist verdict as a decision row.
	contentJSON, _ := json.Marshal(map[string]interface{}{"tally": tally, "votes": votes})
	if _, err := s.pool.Exec(ctx, `
		INSERT INTO decisions (organization_id, investigation_id, decision_type, title, content, status, outcome)
		VALUES ($1, $2, 'recommendation', $3, $4::jsonb, 'ratified', $4::jsonb)`,
		orgID, d.InvestigationID, d.Topic, string(contentJSON)); err != nil {
		return nil, err
	}

	ev := events.Root(orgID, events.EvDebateConcluded, events.CatDebate,
		"debate-engine", events.Source{Component: "debate-engine"},
		map[string]interface{}{
			"debate_id": debateID, "verdict": verdict, "tally": tally, "votes": votes,
		})
	if _, err := s.appender.Append(ctx, ev); err != nil {
		return nil, err
	}

	return s.Get(ctx, orgID, debateID)
}

func (s *Service) concludeIfAllVoted(ctx context.Context, orgID, debateID string) error {
	var total, voted int
	if err := s.pool.QueryRow(ctx, `
		SELECT COUNT(*), COUNT(*) FILTER (WHERE stance IN ('yes','no','abstain','adopt','reject','qualified','GO','HOLD'))
		FROM debate_arguments WHERE debate_id = $1 AND stance IN ('opening','support','challenge','yes','no','abstain','adopt','reject','qualified','GO','HOLD')`,
		debateID).Scan(&total, &voted); err != nil {
		return err
	}
	// Auto-conclude when every non-opening argument is a vote.
	if total > 0 && voted >= total {
		_, err := s.Conclude(ctx, orgID, debateID)
		return err
	}
	return nil
}

// Get returns a debate.
func (s *Service) Get(ctx context.Context, orgID, debateID string) (*Debate, error) {
	d, err := s.getRaw(ctx, orgID, debateID)
	if err != nil {
		return nil, err
	}
	rows, err := s.pool.Query(ctx, `
		SELECT worker_id FROM debate_arguments WHERE debate_id = $1 GROUP BY worker_id`, debateID)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	for rows.Next() {
		var w string
		if err := rows.Scan(&w); err != nil {
			return nil, err
		}
		d.Participants = append(d.Participants, w)
	}
	return d, rows.Err()
}

// Transcript returns the full ordered transcript of a debate (doc 24 report).
type TranscriptEntry struct {
	WorkerID string `json:"worker_id"`
	Stance   string `json:"stance"`
	Text     string `json:"text"`
	At       string `json:"at"`
}

// Transcript lists all arguments in order.
func (s *Service) Transcript(ctx context.Context, debateID string) ([]TranscriptEntry, error) {
	rows, err := s.pool.Query(ctx, `
		SELECT worker_id, stance, argument_text, created_at
		FROM debate_arguments WHERE debate_id = $1 ORDER BY created_at`, debateID)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	var out []TranscriptEntry
	for rows.Next() {
		var e TranscriptEntry
		var at time.Time
		if err := rows.Scan(&e.WorkerID, &e.Stance, &e.Text, &at); err != nil {
			return nil, err
		}
		e.At = at.Format(time.RFC3339)
		out = append(out, e)
	}
	return out, rows.Err()
}

func (s *Service) getRaw(ctx context.Context, orgID, debateID string) (*Debate, error) {
	var d Debate
	var invID *string
	var openedAt, concludedAt *time.Time
	var verdict *string
	err := s.pool.QueryRow(ctx, `
		SELECT id, investigation_id, topic, debate_type, status, phase, opened_at, concluded_at
		FROM debates WHERE id = $1`, debateID).
		Scan(&d.DebateID, &invID, &d.Topic, &d.Type, &d.State, &d.Phase, &openedAt, &concludedAt)
	if err != nil {
		if err == pgx.ErrNoRows {
			return nil, ErrNotFound
		}
		return nil, err
	}
	d.OrganizationID = orgID
	d.InvestigationID = deref(invID)
	d.OpenedAt = openedAt.Format(time.RFC3339)
	if concludedAt != nil {
		d.ConcludedAt = concludedAt.Format(time.RFC3339)
	}
	_ = verdict
	return &d, nil
}

func validateKindPhase(kind, phase string) error {
	allowed := map[string][]string{
		"position":   {PhaseOpening},
		"evidence":   {PhaseEvidence},
		"challenge":  {PhaseEvidence, PhaseCrossExam},
		"question":   {PhaseCrossExam},
		"answer":     {PhaseCrossExam},
		"rebuttal":   {PhaseRebuttal},
		"synthesis":  {PhaseSynthesis},
		"vote":       {PhaseVote},
	}[kind]
	for _, p := range allowed {
		if p == phase {
			return nil
		}
	}
	return fmt.Errorf("%w: %s not allowed in phase %s", ErrInvalidTransition, kind, phase)
}

func nextPhase(current string) string {
	for i, p := range phaseOrder {
		if p == current && i+1 < len(phaseOrder) {
			return phaseOrder[i+1]
		}
	}
	return ""
}

func validType(t string) bool {
	switch t {
	case TypeEvidenceChallenge, TypePosition, TypePolicy, TypeStrategic:
		return true
	}
	return false
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
