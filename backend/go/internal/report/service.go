// Package report implements the board report assembly (doc 19 §10):
// the 9-section deliverable derived from the investigation's evidence,
// debate records, and vote.
package report

import (
	"context"
	"encoding/json"
	"errors"
	"time"

	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgxpool"
)

// ErrNotFound is returned when the investigation or report is missing.
var ErrNotFound = errors.New("not found")

// ErrNotComplete is returned when the investigation is not completed
// (doc 24 §8.2: report requires state == completed).
var ErrNotComplete = errors.New("investigation_not_complete")

// Report is the board report (doc 19 §10).
type Report struct {
	ReportID           string   `json:"report_id"`
	InvestigationID    string   `json:"investigation_id"`
	OrganizationID     string   `json:"organization_id"`
	ExecutiveSummary   string   `json:"executive_summary"`
	Recommendation     string   `json:"recommendation"`
	Confidence         float64  `json:"confidence"`
	DissentingOpinions []string `json:"dissenting_opinions"`
	SupportingEvidence []string `json:"supporting_evidence"`
	Risks              []string `json:"risks"`
	Opportunities      []string `json:"opportunities"`
	NextSteps          []string `json:"next_steps"`
	FullDiscussion     string   `json:"full_discussion"`
	CreatedAt          string   `json:"created_at"`
}

// Service assembles reports from persisted organizational data.
type Service struct {
	pool *pgxpool.Pool
}

// NewService builds the report service.
func NewService(pool *pgxpool.Pool) *Service {
	return &Service{pool: pool}
}

// Get returns the report for a completed investigation. If none was ever
// assembled, it assembles one from the evidence + decisions on record.
func (s *Service) Get(ctx context.Context, orgID, invID string) (*Report, error) {
	var state string
	var confidence *float64
	var recommendation []byte
	if err := s.pool.QueryRow(ctx, `
		SELECT status, confidence_score, final_recommendation
		FROM investigations WHERE id = $1 AND organization_id = $2`, invID, orgID).
		Scan(&state, &confidence, &recommendation); err != nil {
		if err == pgx.ErrNoRows {
			return nil, ErrNotFound
		}
		return nil, err
	}
	if state != "completed" && state != "archived" {
		return nil, ErrNotComplete
	}

	rep := &Report{
		ReportID: "rep-" + invID[:8], InvestigationID: invID, OrganizationID: orgID,
	}
	if confidence != nil {
		rep.Confidence = *confidence
	}
	if len(recommendation) > 0 {
		var rec struct {
			Summary        string   `json:"summary"`
			Recommendation string   `json:"recommendation"`
			Risks          []string `json:"risks"`
			Opportunities  []string `json:"opportunities"`
			NextSteps      []string `json:"next_steps"`
		}
		if json.Unmarshal(recommendation, &rec) == nil {
			rep.ExecutiveSummary = rec.Summary
			rep.Recommendation = rec.Recommendation
			rep.Risks = rec.Risks
			rep.Opportunities = rec.Opportunities
			rep.NextSteps = rec.NextSteps
		}
	}

	// Supporting evidence (accepted claims from the KG).
	rows, err := s.pool.Query(ctx, `
		SELECT claim_text FROM kg_claims
		WHERE investigation_id = $1 AND status = 'accepted'
		ORDER BY created_at LIMIT 20`, invID)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	for rows.Next() {
		var claim string
		if err := rows.Scan(&claim); err != nil {
			return nil, err
		}
		rep.SupportingEvidence = append(rep.SupportingEvidence, claim)
	}

	// Dissenting opinions from the last debate transcript.
	dRows, err := s.pool.Query(ctx, `
		SELECT argument_text FROM debate_arguments a
		JOIN debates d ON d.id = a.debate_id
		WHERE d.investigation_id = $1 AND d.status = 'concluded'
		  AND a.stance NOT IN ('opening','challenge','synthesis')
		ORDER BY a.created_at DESC LIMIT 5`, invID)
	if err != nil {
		return nil, err
	}
	defer dRows.Close()
	for dRows.Next() {
		var text string
		if err := dRows.Scan(&text); err != nil {
			return nil, err
		}
		if text != "" {
			rep.DissentingOpinions = append(rep.DissentingOpinions, text)
		}
	}

	rep.FullDiscussion = s.transcriptSummary(ctx, invID)
	rep.CreatedAt = time.Now().UTC().Format(time.RFC3339)
	return rep, nil
}

// transcriptSummary builds a compact transcript summary for section 9.
func (s *Service) transcriptSummary(ctx context.Context, invID string) string {
	rows, err := s.pool.Query(ctx, `
		SELECT w.role, a.argument_text FROM debate_arguments a
		JOIN debates d ON d.id = a.debate_id
		JOIN workers w ON w.id = a.worker_id
		WHERE d.investigation_id = $1
		ORDER BY a.created_at LIMIT 30`, invID)
	if err != nil {
		return ""
	}
	defer rows.Close()
	out := ""
	for rows.Next() {
		var role, text string
		if err := rows.Scan(&role, &text); err != nil {
			continue
		}
		if text == "" {
			continue
		}
		out += role + ": " + text + "\n"
	}
	return out
}
