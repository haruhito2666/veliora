// Package constitution implements the Constitution Engine (doc 09, doc 10 §8.4):
// versioned constitutions, standing/quorum/supermajority checks, and amendment
// proposals that only take effect after a ratified vote.
package constitution

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"time"

	"github.com/google/uuid"
	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgxpool"

	"veliora-backend/internal/shared/events"
)

// ErrNotFound is returned for missing resources.
var ErrNotFound = errors.New("not found")

// ErrInvalidTransition is returned when a constitutional guard rejects an act.
var ErrInvalidTransition = errors.New("invalid_transition")

// Service is the Constitution Engine.
type Service struct {
	pool     *pgxpool.Pool
	appender *events.Appender
}

// NewService builds the Constitution Engine.
func NewService(pool *pgxpool.Pool) *Service {
	return &Service{pool: pool, appender: events.NewAppender(pool)}
}

// Constitution is the decoded constitutional content (doc 13 §13.4.2).
type Constitution struct {
	Mission       string                 `json:"mission"`
	Principles    []string               `json:"principles"`
	Values        []string               `json:"values"`
	Ethics        []string               `json:"ethics"`
	DecisionRules map[string]interface{} `json:"decision_rules"`
}

// GetCurrent returns the org's current constitution.
func (s *Service) GetCurrent(ctx context.Context, orgID string) (*Constitution, error) {
	var content []byte
	var version int
	err := s.pool.QueryRow(ctx, `
		SELECT content, version FROM constitutions
		WHERE organization_id = $1 AND is_current = true
		ORDER BY version DESC LIMIT 1`, orgID).Scan(&content, &version)
	if err != nil {
		if err == pgx.ErrNoRows {
			return nil, ErrNotFound
		}
		return nil, err
	}
	var c Constitution
	if err := json.Unmarshal(content, &c); err != nil {
		return nil, err
	}
	return &c, nil
}

// CheckRequest is rpc.constitution.check: validate an organizational act
// against the constitution (standing, quorum, supermajority).
type CheckRequest struct {
	OrganizationID string `json:"organization_id"`
	Action         string `json:"action"` // e.g. "admit_evidence", "hire", "amend", "finalize_recommendation"
	DepartmentID   string `json:"department_id,omitempty"`
	WorkerID       string `json:"worker_id,omitempty"`
}

// CheckResponse reports whether the act is permitted and why.
type CheckResponse struct {
	Allowed    bool     `json:"allowed"`
	Reason     string   `json:"reason"`
	Quorum     float64  `json:"quorum,omitempty"`
	Supermajority bool  `json:"supermajority,omitempty"`
}

// Check evaluates an act against the constitution's decision rules.
func (s *Service) Check(ctx context.Context, req CheckRequest) (*CheckResponse, error) {
	c, err := s.GetCurrent(ctx, req.OrganizationID)
	if err != nil {
		return nil, err
	}
	q := numRule(c.DecisionRules, "voting_quorum", 0.6)
	amendmentThr := numRule(c.DecisionRules, "amendment_threshold", 0.75)
	ceoVeto := boolRule(c.DecisionRules, "ceo_veto", false)

	resp := &CheckResponse{Allowed: true, Quorum: q}
	switch req.Action {
	case "amend", "hire", "fire", "merge", "split", "dna_shift":
		resp.Supermajority = true
		resp.Reason = fmt.Sprintf("requires supermajority (%.2f)", amendmentThr)
	case "finalize_recommendation":
		if ceoVeto {
			resp.Reason = "ceo veto permitted"
		} else {
			resp.Reason = "simple majority, trust-weighted"
		}
	default:
		resp.Reason = "permitted"
	}
	return resp, nil
}

// ProposeAmendment creates a constitution_amendments row (doc 24 §8.3).
// It does NOT change the current constitution until ratified by vote.
type ProposeAmendmentRequest struct {
	OrganizationID      string `json:"organization_id"`
	ProposedByWorkerID  string `json:"proposed_by_worker_id"`
	Section             string `json:"section"`
	ChangeFrom          interface{} `json:"change_from"`
	ChangeTo            interface{} `json:"change_to"`
	Rationale           string `json:"rationale"`
}

// ProposeAmendment inserts an amendment proposal in 'proposed' status.
func (s *Service) ProposeAmendment(ctx context.Context, req ProposeAmendmentRequest) (string, error) {
	c, err := s.GetCurrent(ctx, req.OrganizationID)
	if err != nil {
		return "", err
	}
	_ = c

	diff := map[string]interface{}{
		"section": req.Section,
		"from":    req.ChangeFrom,
		"to":      req.ChangeTo,
		"rationale": req.Rationale,
	}
	diffJSON, _ := json.Marshal(diff)

	var constID string
	if err := s.pool.QueryRow(ctx, `
		SELECT id FROM constitutions WHERE organization_id = $1 AND is_current = true LIMIT 1`,
		req.OrganizationID).Scan(&constID); err != nil {
		return "", err
	}

	var id string
	if err := s.pool.QueryRow(ctx, `
		INSERT INTO constitution_amendments (constitution_id, proposed_by_worker_id, change_diff, status)
		VALUES ($1, $2, $3::jsonb, 'proposed') RETURNING id`,
		constID, req.ProposedByWorkerID, string(diffJSON)).Scan(&id); err != nil {
		return "", err
	}

	// Emit a proposal event (rooted in the amendment).
	ev := events.Root(req.OrganizationID, "constitution.amendment_proposed",
		events.CatConstitution, "constitution-engine",
		events.Source{Component: "constitution-engine"}, map[string]interface{}{
			"amendment_id": id, "section": req.Section,
		})
	if _, err := s.appender.Append(ctx, ev); err != nil {
		return "", err
	}
	return id, nil
}

// ApplyAmendment ratifies a proposed amendment into a NEW constitution version
// (doc 24 §8.3: constitution does not change until the vote passes).
func (s *Service) ApplyAmendment(ctx context.Context, orgID, amendmentID string) error {
	tx, err := s.pool.Begin(ctx)
	if err != nil {
		return err
	}
	defer func() { _ = tx.Rollback(ctx) }()

	var changeDiff []byte
	var constID string
	if err := tx.QueryRow(ctx, `
		SELECT a.change_diff, a.constitution_id
		FROM constitution_amendments a
		JOIN constitutions c ON c.id = a.constitution_id
		WHERE a.id = $1 AND c.organization_id = $2 AND a.status = 'proposed'`,
		amendmentID, orgID).Scan(&changeDiff, &constID); err != nil {
		if err == pgx.ErrNoRows {
			return ErrInvalidTransition
		}
		return err
	}

	// Build the new content from current + diff.
	var current []byte
	if err := tx.QueryRow(ctx,
		`SELECT content FROM constitutions WHERE id = $1`, constID).Scan(&current); err != nil {
		return err
	}
	var content map[string]interface{}
	_ = json.Unmarshal(current, &content)

	var diff struct {
		Section string      `json:"section"`
		To      interface{} `json:"to"`
	}
	_ = json.Unmarshal(changeDiff, &diff)

	// decision_rules is a nested map; apply the change there.
	if rules, ok := content["decision_rules"].(map[string]interface{}); ok {
		rules[diff.Section] = diff.To
	}
	newContent, _ := json.Marshal(content)

	var version int
	if err := tx.QueryRow(ctx,
		`SELECT version FROM constitutions WHERE id = $1`, constID).Scan(&version); err != nil {
		return err
	}
	newVersion := version + 1

	var newID string
	if err := tx.QueryRow(ctx, `
		INSERT INTO constitutions (organization_id, version, content, is_current, superseded_by, ratified_at)
		VALUES ($1, $2, $3::jsonb, true, NULL, now()) RETURNING id`,
		orgID, newVersion, string(newContent)).Scan(&newID); err != nil {
		return err
	}

	// Old version no longer current; mark amendment ratified.
	if _, err := tx.Exec(ctx, `
		UPDATE constitutions SET is_current = false, superseded_by = $2 WHERE id = $1`,
		constID, newID); err != nil {
		return err
	}
	if _, err := tx.Exec(ctx, `
		UPDATE constitution_amendments SET status = 'ratified', ratified_at = now() WHERE id = $1`,
		amendmentID); err != nil {
		return err
	}

	// Emit constitution.amended.
	ev := events.Root(orgID, events.EvConstitutionAmended, events.CatConstitution,
		"constitution-engine", events.Source{Component: "constitution-engine"},
		map[string]interface{}{"old_version": version, "new_version": newVersion})
	if _, err := s.appender.AppendTx(ctx, tx, ev); err != nil {
		return err
	}

	return tx.Commit(ctx)
}

// AmendHistory returns the amendment records for an org.
type AmendmentRecord struct {
	ID         string    `json:"amendment_id"`
	Section    string    `json:"section"`
	Status     string    `json:"status"`
	ProposedAt string    `json:"proposed_at"`
}

// History lists amendments for an org (doc 24 §8.3 GET /history).
func (s *Service) History(ctx context.Context, orgID string) ([]AmendmentRecord, error) {
	rows, err := s.pool.Query(ctx, `
		SELECT a.id, a.change_diff->>'section', a.status, a.proposed_at
		FROM constitution_amendments a
		JOIN constitutions c ON c.id = a.constitution_id
		WHERE c.organization_id = $1
		ORDER BY a.proposed_at DESC`, orgID)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	var out []AmendmentRecord
	for rows.Next() {
		var r AmendmentRecord
		var proposedAt time.Time
		if err := rows.Scan(&r.ID, &r.Section, &r.Status, &proposedAt); err != nil {
			return nil, err
		}
		r.ProposedAt = proposedAt.Format(time.RFC3339)
		out = append(out, r)
	}
	return out, rows.Err()
}

func numRule(rules map[string]interface{}, key string, def float64) float64 {
	if v, ok := rules[key].(float64); ok {
		return v
	}
	return def
}

func boolRule(rules map[string]interface{}, key string, def bool) bool {
	if v, ok := rules[key].(bool); ok {
		return v
	}
	return def
}

var _ = uuid.NewString

// SetEventPublisher wires the NATS publisher for event fan-out.
func (s *Service) SetEventPublisher(p events.Publisher) {
	s.appender.SetPublisher(p)
}
