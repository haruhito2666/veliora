// Package memory implements the institutional Memory System (doc 06):
// scoped memories (org/department/worker), decay, and pgvector semantic recall.
// Embeddings are produced via an embedder interface so tests can stub them;
// production uses OpenAI text-embedding-3-small (1536 dims) from the Python
// worker runtime via an RPC, or a deterministic local hash for dev.
package memory

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

// ErrNotFound is returned when a memory is missing.
var ErrNotFound = errors.New("not found")

// Scopes (doc 05 §2.4).
const (
	ScopeOrg        = "org"
	ScopeDepartment = "department"
	ScopeWorker     = "worker"
)

// Types (doc 06).
const (
	TypeEpisodic   = "episodic"
	TypeProcedural = "procedural"
	TypeSemantic   = "semantic"
	TypePolicy     = "policy"
)

// Embedder produces a 1536-dim embedding for text.
type Embedder interface {
	Embed(ctx context.Context, text string) ([]float64, error)
}

// Service is the Memory System.
type Service struct {
	pool     *pgxpool.Pool
	appender *events.Appender
	embedder Embedder
}

// NewService builds the Memory System. If embedder is nil, recall falls back
// to keyword matching (no vector).
func NewService(pool *pgxpool.Pool, embedder Embedder) *Service {
	return &Service{pool: pool, appender: events.NewAppender(pool), embedder: embedder}
}

// Memory is a stored institutional memory record.
type Memory struct {
	ID              string                 `json:"memory_id"`
	OrganizationID  string                 `json:"organization_id"`
	DepartmentID    string                 `json:"department_id,omitempty"`
	WorkerID        string                 `json:"worker_id,omitempty"`
	Scope           string                 `json:"scope"`
	Type            string                 `json:"memory_type"`
	Content         map[string]interface{} `json:"content"`
	Importance      float64                `json:"importance"`
	DecayScore      float64                `json:"decay_score"`
	SourceInvestigationID string           `json:"source_investigation_id,omitempty"`
	CreatedAt       string                 `json:"created_at"`
}

// CreateRequest is rpc.memory.create.
type CreateRequest struct {
	OrganizationID  string                 `json:"organization_id"`
	DepartmentID    string                 `json:"department_id,omitempty"`
	WorkerID        string                 `json:"worker_id,omitempty"`
	Scope           string                 `json:"scope"`
	Type            string                 `json:"memory_type"`
	Content         map[string]interface{} `json:"content"`
	Importance      float64                `json:"importance"`
	SourceInvestigationID string           `json:"source_investigation_id,omitempty"`
}

// Create stores a memory and emits memory.created.
func (s *Service) Create(ctx context.Context, req CreateRequest) (*Memory, error) {
	if req.Scope == "" {
		req.Scope = ScopeOrg
	}
	if req.Type == "" {
		req.Type = TypeSemantic
	}
	if req.Importance <= 0 {
		req.Importance = 0.5
	}

	contentJSON, _ := json.Marshal(req.Content)

	// Build embedding if available.
	var embedding []float64
	text := memoryText(req.Content)
	if s.embedder != nil {
		emb, err := s.embedder.Embed(ctx, text)
		if err == nil {
			embedding = emb
		}
	}

	id := newUUID()
	if _, err := s.pool.Exec(ctx, `
		INSERT INTO memories (id, organization_id, department_id, worker_id, scope,
			memory_type, content, embedding, importance, decay_score, source_investigation_id)
		VALUES ($1, $2, $3, $4, $5, $6, $7::jsonb, $8, $9, 1.0, $10)`,
		id, req.OrganizationID, nullable(req.DepartmentID), nullable(req.WorkerID),
		req.Scope, req.Type, string(contentJSON), vector(embedding), req.Importance,
		nullable(req.SourceInvestigationID)); err != nil {
		return nil, err
	}

	// Emit memory.created.
	ev := events.Root(req.OrganizationID, events.EvMemoryCreated, events.CatMemory,
		"memory-engine", events.Source{Component: "memory-engine"},
		map[string]interface{}{"memory_id": id, "scope": req.Scope, "type": req.Type})
	if _, err := s.appender.Append(ctx, ev); err != nil {
		return nil, err
	}

	return s.Get(ctx, req.OrganizationID, id)
}

// Get returns one memory.
func (s *Service) Get(ctx context.Context, orgID, memoryID string) (*Memory, error) {
	var m Memory
	var content []byte
	var createdAt time.Time
	var deptID, workerID, sourceInv *string
	err := s.pool.QueryRow(ctx, `
		SELECT id, organization_id, department_id, worker_id, scope, memory_type,
		       content, importance, decay_score, source_investigation_id, created_at
		FROM memories WHERE id = $1 AND organization_id = $2`,
		memoryID, orgID).Scan(&m.ID, &m.OrganizationID, &deptID, &workerID, &m.Scope,
		&m.Type, &content, &m.Importance, &m.DecayScore, &sourceInv, &createdAt)
	if err != nil {
		if err == pgx.ErrNoRows {
			return nil, ErrNotFound
		}
		return nil, err
	}
	m.DepartmentID = deref(deptID)
	m.WorkerID = deref(workerID)
	m.SourceInvestigationID = deref(sourceInv)
	m.CreatedAt = createdAt.Format(time.RFC3339)
	_ = json.Unmarshal(content, &m.Content)
	return &m, nil
}

// List returns memories for an org, optionally filtered by scope/dept/worker.
func (s *Service) List(ctx context.Context, orgID, scope, departmentID, workerID string) ([]Memory, error) {
	rows, err := s.pool.Query(ctx, `
		SELECT id, organization_id, department_id, worker_id, scope, memory_type,
		       content, importance, decay_score, source_investigation_id, created_at
		FROM memories
		WHERE organization_id = $1
		  AND ($2 = '' OR scope = $2)
		  AND ($3::uuid IS NULL OR department_id = $3::uuid)
		  AND ($4::uuid IS NULL OR worker_id = $4::uuid)
		ORDER BY created_at DESC LIMIT 200`, orgID, scope, nullIfEmpty(departmentID), nullIfEmpty(workerID))
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	var out []Memory
	for rows.Next() {
		var m Memory
		var content []byte
		var createdAt time.Time
		var deptID, workerIDV, sourceInv *string
		if err := rows.Scan(&m.ID, &m.OrganizationID, &deptID, &workerIDV, &m.Scope,
			&m.Type, &content, &m.Importance, &m.DecayScore, &sourceInv, &createdAt); err != nil {
			return nil, err
		}
		m.DepartmentID = deref(deptID)
		m.WorkerID = deref(workerIDV)
		m.SourceInvestigationID = deref(sourceInv)
		m.CreatedAt = createdAt.Format(time.RFC3339)
		_ = json.Unmarshal(content, &m.Content)
		out = append(out, m)
	}
	return out, rows.Err()
}

// RecallRequest is rpc.memory.recall (doc 17 §5.3 retrieval policy).
type RecallRequest struct {
	OrganizationID string `json:"organization_id"`
	Query          string `json:"query"`
	DepartmentID   string `json:"department_id,omitempty"`
	Limit          int    `json:"limit"`
}

// Recall returns top memories by semantic similarity with decay filtering
// (decay_score > 0.2, doc 13 Q4), falling back to keyword when no embedder.
func (s *Service) Recall(ctx context.Context, req RecallRequest) ([]Memory, error) {
	if req.Limit <= 0 || req.Limit > 50 {
		req.Limit = 10
	}

	query := req.Query
	if query == "" {
		return s.List(ctx, req.OrganizationID, "", req.DepartmentID, "")
	}

	// Try semantic recall when an embedder exists.
	if s.embedder != nil {
		emb, err := s.embedder.Embed(ctx, query)
		if err == nil && len(emb) > 0 {
			return s.semanticRecall(ctx, req, emb)
		}
	}

	// Keyword fallback.
	rows, err := s.pool.Query(ctx, `
		SELECT id, organization_id, department_id, worker_id, scope, memory_type,
		       content, importance, decay_score, source_investigation_id, created_at
		FROM memories
		WHERE organization_id = $1 AND decay_score > 0.2
		  AND content::text ILIKE '%' || $2 || '%'
		ORDER BY importance DESC LIMIT $3`, req.OrganizationID, query, req.Limit)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	return scanMemories(rows)
}

func (s *Service) semanticRecall(ctx context.Context, req RecallRequest, emb []float64) ([]Memory, error) {
	rows, err := s.pool.Query(ctx, `
		SELECT id, organization_id, department_id, worker_id, scope, memory_type,
		       content, importance, decay_score, source_investigation_id, created_at
		FROM memories
		WHERE organization_id = $1 AND decay_score > 0.2
		ORDER BY embedding <=> $2
		LIMIT $3`, req.OrganizationID, vector(emb), req.Limit)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	return scanMemories(rows)
}

// Decay applies exponential decay to all memories older than the half-life
// (doc 06): decay_score *= exp(-age_days / 90).
func (s *Service) Decay(ctx context.Context, orgID string) error {
	_, err := s.pool.Exec(ctx, `
		UPDATE memories
		SET decay_score = ROUND((decay_score * exp(-EXTRACT(EPOCH FROM (now() - last_reinforced_at)) / (86400.0 * 90)))::numeric, 2)
		WHERE organization_id = $1 AND decay_score > 0.2`, orgID)
	return err
}

// Reinforce bumps a memory's decay score back toward 1.0 (doc 16.4.9).
func (s *Service) Reinforce(ctx context.Context, orgID, memoryID string) error {
	_, err := s.pool.Exec(ctx, `
		UPDATE memories SET decay_score = LEAST(1.0, decay_score + 0.2), last_reinforced_at = now()
		WHERE id = $1 AND organization_id = $2`, memoryID, orgID)
	return err
}

func scanMemories(rows pgx.Rows) ([]Memory, error) {
	defer rows.Close()
	var out []Memory
	for rows.Next() {
		var m Memory
		var content []byte
		var createdAt time.Time
		var deptID, workerID, sourceInv *string
		if err := rows.Scan(&m.ID, &m.OrganizationID, &deptID, &workerID, &m.Scope,
			&m.Type, &content, &m.Importance, &m.DecayScore, &sourceInv, &createdAt); err != nil {
			return nil, err
		}
		m.DepartmentID = deref(deptID)
		m.WorkerID = deref(workerID)
		m.SourceInvestigationID = deref(sourceInv)
		m.CreatedAt = createdAt.Format(time.RFC3339)
		_ = json.Unmarshal(content, &m.Content)
		out = append(out, m)
	}
	return out, rows.Err()
}

// memoryText builds a searchable string from memory content.
func memoryText(content map[string]interface{}) string {
	var out string
	if s, ok := content["summary"].(string); ok {
		out += s + " "
	}
	if d, ok := content["details"].(string); ok {
		out += d
	}
	if out == "" {
		b, _ := json.Marshal(content)
		out = string(b)
	}
	return out
}

// vector converts a []float64 to a pgvector literal (or nil).
func vector(v []float64) interface{} {
	if len(v) == 0 {
		return nil
	}
	b, _ := json.Marshal(v)
	return "[" + string(b[1:len(b)-1]) + "]"
}

func nullable(s string) interface{} {
	if s == "" {
		return nil
	}
	return s
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

func newUUID() string {
	return uuidString()
}

var _ = math.Exp

// SetEventPublisher wires the NATS publisher for event fan-out.
func (s *Service) SetEventPublisher(p events.Publisher) {
	s.appender.SetPublisher(p)
}
