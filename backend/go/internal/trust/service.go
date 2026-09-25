// Package trust implements the Trust Engine (doc 08): per-worker, per-domain
// Beta(α,β) reputation with exponential decay and shrinkage toward distrust.
// Trust weight W = T − σ is the value all downstream engines consume.
package trust

import (
	"context"
	"encoding/json"
	"errors"
	"math"
	"time"

	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgxpool"

	"veliora-backend/internal/shared/events"
)

// ErrNotFound is returned when a worker has no trust state.
var ErrNotFound = errors.New("not found")

// Domains is the per-domain trust split (doc 08 §4.1).
var Domains = []string{"research", "forecast", "engineering", "marketing", "legal", "general"}

// Service is the Trust Engine.
type Service struct {
	pool     *pgxpool.Pool
	appender *events.Appender
}

// NewService builds the Trust Engine.
func NewService(pool *pgxpool.Pool) *Service {
	return &Service{pool: pool, appender: events.NewAppender(pool)}
}

// BetaParams are the α/β counters for one (worker, domain).
type BetaParams struct {
	Alpha float64 `json:"alpha"`
	Beta  float64 `json:"beta"`
}

// WorkerTrust is the aggregated trust state for one worker.
type WorkerTrust struct {
	WorkerID  string                `json:"worker_id"`
	Domains   map[string]BetaParams `json:"domains"`
	Score     float64               `json:"score"`   // T = α/(α+β), for display
	Weight    float64               `json:"weight"`  // W = T − σ, for voting
	TaskCount int                   `json:"task_count"`
}

// stateKey is the per-worker trust state row in the DB (workers.configuration
// holds trust counters; we keep the authoritative copy in a JSONB column via
// trust_records aggregation + a denormalized column on workers).
const trustStateKey = "trust_state"

// Get returns the worker's current trust (score for display, weight for voting).
func (s *Service) Get(ctx context.Context, orgID, workerID string) (*WorkerTrust, error) {
	var cfgJSON []byte
	err := s.pool.QueryRow(ctx, `
		SELECT configuration FROM workers WHERE id = $1 AND organization_id = $2`,
		workerID, orgID).Scan(&cfgJSON)
	if err != nil {
		if err == pgx.ErrNoRows {
			return nil, ErrNotFound
		}
		return nil, err
	}
	var cfg map[string]interface{}
	_ = json.Unmarshal(cfgJSON, &cfg)

	// Load persisted Beta counters (default α=β=1 → neutral 0.5).
	wt := &WorkerTrust{WorkerID: workerID, Domains: map[string]BetaParams{}}
	for _, d := range Domains {
		wt.Domains[d] = BetaParams{Alpha: 1, Beta: 1}
	}
	if raw, ok := cfg[trustStateKey]; ok {
		rawBytes, _ := json.Marshal(raw)
		var stored map[string]BetaParams
		if json.Unmarshal(rawBytes, &stored) == nil {
			for d, p := range stored {
				if p.Alpha < 0 || p.Beta < 0 {
					continue
				}
				wt.Domains[d] = p
			}
		}
	}
	if raw, ok := cfg["task_count"].(float64); ok {
		wt.TaskCount = int(raw)
	}
	wt.compute()
	return wt, nil
}

// Update applies a trust outcome event (doc 08 §3.4): positive/negative with
// an event weight, domain-scoped, with exponential decay toward the prior.
type UpdateRequest struct {
	OrganizationID string  `json:"organization_id"`
	WorkerID       string  `json:"worker_id"`
	Domain         string  `json:"domain"`
	Positive       bool    `json:"positive"`
	EventWeight    float64 `json:"event_weight"` // w_e, default 1
	Context        string  `json:"context"`      // e.g. "evidence upheld"
	EvaluatedBy    string  `json:"evaluated_by,omitempty"`
}

// Update applies the outcome and emits trust.updated.
func (s *Service) Update(ctx context.Context, req UpdateRequest) (*WorkerTrust, error) {
	if req.EventWeight <= 0 {
		req.EventWeight = 1
	}
	if req.Domain == "" {
		req.Domain = "general"
	}
	if _, ok := betaDomain(req.Domain); !ok {
		req.Domain = "general"
	}

	wt, err := s.Get(ctx, req.OrganizationID, req.WorkerID)
	if err != nil {
		return nil, err
	}

	p := wt.Domains[req.Domain]
	// Decay toward prior (α=β=1) before applying (doc 08 §3.5).
	lambda := 0.02 // per-update decay factor
	p.Alpha = 1 + (p.Alpha-1)*math.Exp(-lambda)
	p.Beta = 1 + (p.Beta-1)*math.Exp(-lambda)
	if req.Positive {
		p.Alpha += req.EventWeight
	} else {
		p.Beta += req.EventWeight
	}
	wt.Domains[req.Domain] = p
	wt.TaskCount++
	wt.compute()

	// Persist the new state atomically (raw JSONB object via []byte).
	newState, _ := json.Marshal(wt.Domains)
	mergeState := jsonMap(map[string]interface{}{
		trustStateKey: json.RawMessage(newState),
		"task_count":  wt.TaskCount,
	})
	if _, err := s.pool.Exec(ctx, `
		UPDATE workers SET configuration = configuration || $2,
			current_trust_score = $3
		WHERE id = $1`, req.WorkerID,
		[]byte(mergeState), wt.Score); err != nil {
		return nil, err
	}

	// trust_records projection (time-series).
	if _, err := s.pool.Exec(ctx, `
		INSERT INTO trust_records (organization_id, worker_id, evaluated_by_worker_id,
			trust_score, trust_weight, trust_context)
		VALUES ($1, $2, $3, $4, $5, $6::jsonb)`,
		req.OrganizationID, req.WorkerID, nullable(req.EvaluatedBy),
		wt.Score, wt.Weight,
		jsonString(map[string]interface{}{
			"domain": req.Domain, "positive": req.Positive,
			"event_weight": req.EventWeight, "context": req.Context,
		})); err != nil {
		return nil, err
	}

	// Emit trust.updated.
	ev := events.Root(req.OrganizationID, events.EvTrustUpdated, events.CatTrust,
		"trust-engine", events.Source{Component: "trust-engine"},
		map[string]interface{}{
			"worker_id": req.WorkerID, "old_score": 0, "new_score": wt.Score,
			"domain": req.Domain, "delta": req.EventWeight, "context": req.Context,
		})
	if _, err := s.appender.Append(ctx, ev); err != nil {
		return nil, err
	}

	return wt, nil
}

// WeightRequest asks for a worker's voting weight in a domain (doc 10 §8.4).
type WeightRequest struct {
	OrganizationID string `json:"organization_id"`
	WorkerID       string `json:"worker_id"`
	Domain         string `json:"domain"`
}

// WeightResponse is the voting weight and its trust components.
type WeightResponse struct {
	WorkerID string  `json:"worker_id"`
	Score    float64 `json:"score"`
	Weight   float64 `json:"weight"`
	Domain   string  `json:"domain"`
}

// Weight returns W = T − σ for voting (doc 08 §3.6).
func (s *Service) Weight(ctx context.Context, req WeightRequest) (*WeightResponse, error) {
	wt, err := s.Get(ctx, req.OrganizationID, req.WorkerID)
	if err != nil {
		return nil, err
	}
	if req.Domain == "" {
		req.Domain = "general"
	}
	if _, ok := wt.Domains[req.Domain]; !ok {
		req.Domain = "general"
	}
	return &WeightResponse{
		WorkerID: req.WorkerID,
		Score:    wt.Score,
		Weight:   wt.Weight,
		Domain:   req.Domain,
	}, nil
}

// History returns trust_records for a worker (doc 24 §8.6).
type TrustRecord struct {
	RecordedAt string  `json:"recorded_at"`
	Score      float64 `json:"score"`
	Weight     float64 `json:"weight"`
	Context    string  `json:"context"`
}

// History lists a worker's trust records, newest first.
func (s *Service) History(ctx context.Context, orgID, workerID string) ([]TrustRecord, error) {
	rows, err := s.pool.Query(ctx, `
		SELECT recorded_at, trust_score, trust_weight, trust_context->>'context'
		FROM trust_records
		WHERE organization_id = $1 AND worker_id = $2
		ORDER BY recorded_at DESC LIMIT 100`, orgID, workerID)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	var out []TrustRecord
	for rows.Next() {
		var r TrustRecord
		var t time.Time
		if err := rows.Scan(&t, &r.Score, &r.Weight, &r.Context); err != nil {
			return nil, err
		}
		r.RecordedAt = t.Format(time.RFC3339)
		out = append(out, r)
	}
	return out, rows.Err()
}

// compute sets Score (T) and Weight (W = T − σ) from the Beta counters
// (doc 08 §3.5–3.6: shrinkage toward distrust via the standard deviation).
func (wt *WorkerTrust) compute() {
	var totalAlpha, totalBeta float64
	for _, p := range wt.Domains {
		totalAlpha += p.Alpha
		totalBeta += p.Beta
	}
	// Overall T across all domains (display score).
	wt.Score = totalAlpha / (totalAlpha + totalBeta)
	// Weight uses the per-domain beta variance; approximate with the
	// pooled variance of the general domain.
	gen := wt.Domains["general"]
	n := gen.Alpha + gen.Beta
	var sigma float64
	if n > 0 {
		t := gen.Alpha / n
		sigma = math.Sqrt(t * (1 - t) / n)
	}
	wt.Weight = wt.Score - sigma
	if wt.Weight < 0 {
		wt.Weight = 0
	}
}

func betaDomain(d string) (string, bool) {
	for _, x := range Domains {
		if x == d {
			return d, true
		}
	}
	return "", false
}

func jsonString(m map[string]interface{}) string {
	b, _ := json.Marshal(m)
	return string(b)
}

func jsonMap(m map[string]interface{}) string {
	return jsonString(m)
}

func nullable(s string) interface{} {
	if s == "" {
		return nil
	}
	return s
}

// SetEventPublisher wires the NATS publisher for event fan-out.
func (s *Service) SetEventPublisher(p events.Publisher) {
	s.appender.SetPublisher(p)
}
