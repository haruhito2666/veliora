// Command evidence-engine serves evidence queries (doc 24 §8.1).
package main

import (
	"context"
	"encoding/json"
	"log"
	"time"

	"veliora-backend/internal/shared/bus"
	"veliora-backend/internal/shared/config"
	"veliora-backend/internal/shared/db"
)

// Evidence is the evidence resource (doc 24 §8.1).
type Evidence struct {
	EvidenceID   string     `json:"evidence_id"`
	InvestigationID string  `json:"investigation_id"`
	SubmittedBy  string     `json:"submitted_by"`
	SourceType   string     `json:"source_type"`
	Summary      string     `json:"summary"`
	Confidence   float64    `json:"confidence"`
	Status       string     `json:"status"`
	ChallengeCount int      `json:"challenge_count"`
	SubmittedAt  string     `json:"submitted_at"`
	Challenges   []Challenge `json:"challenges,omitempty"`
}

// Challenge is an evidence challenge record.
type Challenge struct {
	ChallengeID  string `json:"challenge_id"`
	By           string `json:"by"`
	Text         string `json:"critique"`
	Status       string `json:"resolution"`
}

func main() {
	ctx := context.Background()
	cfg := config.FromEnv()

	pool, err := db.Connect(ctx, cfg.PostgresURL)
	if err != nil {
		log.Fatalf("db: %v", err)
	}
	defer pool.Close()

	b, err := bus.Connect(cfg.NATSURL)
	if err != nil {
		log.Fatalf("nats: %v", err)
	}
	defer b.Close()

	// rpc.evidence.list
	if err := b.Respond("rpc.evidence.list", func(req []byte) (interface{}, error) {
		var r struct {
			OrganizationID  string `json:"organization_id"`
			InvestigationID string `json:"investigation_id"`
		}
		if err := json.Unmarshal(req, &r); err != nil {
			return nil, err
		}
		rows, err := pool.Query(ctx, `
			SELECT e.id, e.investigation_id, e.submitted_by_worker_id, e.source_type,
			       e.summary, e.content->>'confidence', e.status, e.challenge_count, e.submitted_at
			FROM evidence e
			JOIN investigations i ON i.id = e.investigation_id
			WHERE i.organization_id = $1 AND e.investigation_id = $2
			ORDER BY e.submitted_at DESC LIMIT 100`, r.OrganizationID, r.InvestigationID)
		if err != nil {
			return nil, err
		}
		defer rows.Close()
		var out []Evidence
		for rows.Next() {
			var e Evidence
			var confJSON *string
			var submittedAt time.Time
			if err := rows.Scan(&e.EvidenceID, &e.InvestigationID, &e.SubmittedBy,
				&e.SourceType, &e.Summary, &confJSON, &e.Status, &e.ChallengeCount, &submittedAt); err != nil {
				return nil, err
			}
			if confJSON != nil {
				_ = json.Unmarshal([]byte(*confJSON), &e.Confidence)
			}
			e.SubmittedAt = submittedAt.Format(time.RFC3339)

			// Challenges.
			cRows, err := pool.Query(ctx, `
				SELECT id, challenged_by_worker_id, challenge_text, status
				FROM evidence_challenges WHERE evidence_id = $1 ORDER BY created_at`, e.EvidenceID)
			if err == nil {
				for cRows.Next() {
					var c Challenge
					if err := cRows.Scan(&c.ChallengeID, &c.By, &c.Text, &c.Status); err == nil {
						e.Challenges = append(e.Challenges, c)
					}
				}
				cRows.Close()
			}
			out = append(out, e)
		}
		return out, rows.Err()
	}); err != nil {
		log.Fatalf("respond list: %v", err)
	}

	log.Printf("evidence-engine listening on NATS (%s)", cfg.NATSURL)
	select {}
}
