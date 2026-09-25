// Package kgraph implements the Knowledge Graph (doc 14) on Postgres:
// claims as nodes, evidence links and challenge edges as edges, and
// worker relationship edges (doc 05 §2.6). Neo4j is a later scale-out.
package kgraph

import (
	"context"
	"crypto/rand"
	"encoding/hex"
	"errors"
	"time"

	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgxpool"

	"veliora-backend/internal/shared/events"
)

// ErrNotFound is returned for missing nodes.
var ErrNotFound = errors.New("not found")

// Service is the Knowledge Graph.
type Service struct {
	pool     *pgxpool.Pool
	appender *events.Appender
}

// NewService builds the Knowledge Graph engine.
func NewService(pool *pgxpool.Pool) *Service {
	return &Service{pool: pool, appender: events.NewAppender(pool)}
}

// Claim is a knowledge graph node (a belief with provenance).
type Claim struct {
	ID               string `json:"claim_id"`
	OrganizationID   string `json:"organization_id"`
	InvestigationID  string `json:"investigation_id,omitempty"`
	DepartmentID     string `json:"department_id,omitempty"`
	ClaimText        string `json:"claim_text"`
	Confidence       float64 `json:"confidence"`
	Status           string `json:"status"` // proposed|accepted|rejected|amended
	CreatedAt        string `json:"created_at"`
}

// AddClaimRequest is rpc.kg.add_claim.
type AddClaimRequest struct {
	OrganizationID  string  `json:"organization_id"`
	InvestigationID string  `json:"investigation_id,omitempty"`
	DepartmentID    string  `json:"department_id,omitempty"`
	ClaimText       string  `json:"claim_text"`
	Confidence      float64 `json:"confidence"`
}

// AddClaim creates a claim node.
func (s *Service) AddClaim(ctx context.Context, req AddClaimRequest) (*Claim, error) {
	id := newUUID()
	if _, err := s.pool.Exec(ctx, `
		INSERT INTO kg_claims (id, organization_id, investigation_id, department_id, claim_text, confidence, status)
		VALUES ($1, $2, $3, $4, $5, $6, 'proposed')`,
		id, req.OrganizationID, nullable(req.InvestigationID), nullable(req.DepartmentID),
		req.ClaimText, req.Confidence); err != nil {
		return nil, err
	}
	return s.GetClaim(ctx, req.OrganizationID, id)
}

// GetClaim returns one claim.
func (s *Service) GetClaim(ctx context.Context, orgID, claimID string) (*Claim, error) {
	var c Claim
	var createdAt time.Time
	var invID, deptID *string
	err := s.pool.QueryRow(ctx, `
		SELECT id, organization_id, investigation_id, department_id, claim_text, confidence, status, created_at
		FROM kg_claims WHERE id = $1 AND organization_id = $2`, claimID, orgID).
		Scan(&c.ID, &c.OrganizationID, &invID, &deptID, &c.ClaimText, &c.Confidence, &c.Status, &createdAt)
	if err != nil {
		if err == pgx.ErrNoRows {
			return nil, ErrNotFound
		}
		return nil, err
	}
	c.InvestigationID = deref(invID)
	c.DepartmentID = deref(deptID)
	c.CreatedAt = createdAt.Format(time.RFC3339)
	return &c, nil
}

// Edge links two claims (cites/challenges/supports/refutes).
type Edge struct {
	ID             string `json:"edge_id"`
	OrganizationID string `json:"organization_id"`
	SourceID       string `json:"source_id"`
	TargetID       string `json:"target_id"`
	Relation       string `json:"relation"` // cites|challenges|supports|refutes
	Weight         float64 `json:"weight"`
	Note           string `json:"note,omitempty"`
	CreatedAt      string `json:"created_at"`
}

// AddEdgeRequest is rpc.kg.add_edge.
type AddEdgeRequest struct {
	OrganizationID string  `json:"organization_id"`
	SourceID       string  `json:"source_id"`
	TargetID       string  `json:"target_id"`
	Relation       string  `json:"relation"`
	Weight         float64 `json:"weight"`
	Note           string  `json:"note,omitempty"`
}

// AddEdge creates an edge between two claims (or claim↔evidence).
func (s *Service) AddEdge(ctx context.Context, req AddEdgeRequest) (*Edge, error) {
	id := newUUID()
	if _, err := s.pool.Exec(ctx, `
		INSERT INTO kg_edges (id, organization_id, source_id, target_id, relation, weight, note)
		VALUES ($1, $2, $3, $4, $5, $6, $7)`,
		id, req.OrganizationID, req.SourceID, req.TargetID, req.Relation, req.Weight, req.Note); err != nil {
		return nil, err
	}
	return s.GetEdge(ctx, req.OrganizationID, id)
}

// GetEdge returns one edge.
func (s *Service) GetEdge(ctx context.Context, orgID, edgeID string) (*Edge, error) {
	var e Edge
	var createdAt time.Time
	err := s.pool.QueryRow(ctx, `
		SELECT id, organization_id, source_id, target_id, relation, weight, note, created_at
		FROM kg_edges WHERE id = $1 AND organization_id = $2`, edgeID, orgID).
		Scan(&e.ID, &e.OrganizationID, &e.SourceID, &e.TargetID, &e.Relation, &e.Weight, &e.Note, &createdAt)
	if err != nil {
		if err == pgx.ErrNoRows {
			return nil, ErrNotFound
		}
		return nil, err
	}
	e.CreatedAt = createdAt.Format(time.RFC3339)
	return &e, nil
}

// Subgraph returns the claims + edges for an investigation (doc 24 evidence view).
type Subgraph struct {
	Claims []Claim `json:"claims"`
	Edges  []Edge  `json:"edges"`
}

// SubgraphOf returns all claims and edges for an investigation.
func (s *Service) SubgraphOf(ctx context.Context, orgID, investigationID string) (*Subgraph, error) {
	sg := &Subgraph{}

	rows, err := s.pool.Query(ctx, `
		SELECT id, organization_id, investigation_id, department_id, claim_text, confidence, status, created_at
		FROM kg_claims WHERE organization_id = $1 AND investigation_id = $2`, orgID, investigationID)
	if err != nil {
		return nil, err
	}
	for rows.Next() {
		var c Claim
		var createdAt time.Time
		var invID, deptID *string
		if err := rows.Scan(&c.ID, &c.OrganizationID, &invID, &deptID, &c.ClaimText, &c.Confidence, &c.Status, &createdAt); err != nil {
			rows.Close()
			return nil, err
		}
		c.InvestigationID = deref(invID)
		c.DepartmentID = deref(deptID)
		c.CreatedAt = createdAt.Format(time.RFC3339)
		sg.Claims = append(sg.Claims, c)
	}
	rows.Close()
	if err := rows.Err(); err != nil {
		return nil, err
	}

	// Edges where either endpoint is in the investigation's claims.
	eRows, err := s.pool.Query(ctx, `
		SELECT e.id, e.organization_id, e.source_id, e.target_id, e.relation, e.weight, e.note, e.created_at
		FROM kg_edges e
		WHERE e.organization_id = $1 AND (
			e.source_id IN (SELECT id FROM kg_claims WHERE investigation_id = $2) OR
			e.target_id IN (SELECT id FROM kg_claims WHERE investigation_id = $2)
		)`, orgID, investigationID)
	if err != nil {
		return nil, err
	}
	defer eRows.Close()
	for eRows.Next() {
		var e Edge
		var createdAt time.Time
		if err := eRows.Scan(&e.ID, &e.OrganizationID, &e.SourceID, &e.TargetID, &e.Relation, &e.Weight, &e.Note, &createdAt); err != nil {
			return nil, err
		}
		e.CreatedAt = createdAt.Format(time.RFC3339)
		sg.Edges = append(sg.Edges, e)
	}
	return sg, eRows.Err()
}

// Relationship is a worker↔worker edge (doc 05 §2.6).
type Relationship struct {
	FromWorkerID string  `json:"from_worker_id"`
	ToWorkerID   string  `json:"to_worker_id"`
	Type         string  `json:"type"` // trust|affinity|conflict
	Strength     float64 `json:"strength"`
	Note         string  `json:"note,omitempty"`
	UpdatedAt    string  `json:"updated_at"`
}

// SetRelationship upserts a directional worker relationship.
func (s *Service) SetRelationship(ctx context.Context, orgID string, r Relationship) error {
	_, err := s.pool.Exec(ctx, `
		INSERT INTO kg_relationships (organization_id, from_worker_id, to_worker_id, type, strength, note, updated_at)
		VALUES ($1, $2, $3, $4, $5, $6, now())
		ON CONFLICT (organization_id, from_worker_id, to_worker_id, type)
		DO UPDATE SET strength = EXCLUDED.strength, note = EXCLUDED.note, updated_at = now()`,
		orgID, r.FromWorkerID, r.ToWorkerID, r.Type, r.Strength, r.Note)
	return err
}

// RelationshipsOf returns all relationships involving a worker.
func (s *Service) RelationshipsOf(ctx context.Context, orgID, workerID string) ([]Relationship, error) {
	rows, err := s.pool.Query(ctx, `
		SELECT from_worker_id, to_worker_id, type, strength, note, updated_at
		FROM kg_relationships
		WHERE organization_id = $1 AND (from_worker_id = $2 OR to_worker_id = $2)
		ORDER BY updated_at DESC`, orgID, workerID)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	var out []Relationship
	for rows.Next() {
		var r Relationship
		var updated time.Time
		var note *string
		if err := rows.Scan(&r.FromWorkerID, &r.ToWorkerID, &r.Type, &r.Strength, &note, &updated); err != nil {
			return nil, err
		}
		r.Note = deref(note)
		r.UpdatedAt = updated.Format(time.RFC3339)
		out = append(out, r)
	}
	return out, rows.Err()
}

// ── helpers ────────────────────────────────────────────────────────────

func nullable(s string) interface{} {
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
