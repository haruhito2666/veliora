package kgraph

import (
	"context"
	"os"
	"testing"

	"github.com/jackc/pgx/v5/pgxpool"

	"veliora-backend/internal/orgmgr"
	"veliora-backend/internal/shared/contracts"
)

func testPool(t *testing.T) *pgxpool.Pool {
	t.Helper()
	url := os.Getenv("DATABASE_URL")
	if url == "" {
		url = "postgres://veliora:veliora@localhost:5432/veliora?sslmode=disable"
	}
	pool, err := pgxpool.New(context.Background(), url)
	if err != nil {
		t.Skipf("no DB: %v", err)
	}
	if err := pool.Ping(context.Background()); err != nil {
		t.Skipf("DB not reachable: %v", err)
	}
	return pool
}

func templateDir(t *testing.T) string {
	t.Helper()
	for _, p := range []string{
		"../../../../templates/organizations",
		"/Users/priyanshuboruah/societyos/templates/organizations",
	} {
		if _, err := os.Stat(p); err == nil {
			return p
		}
	}
	t.Skip("templates dir not found")
	return ""
}

func createOrgWorkers(t *testing.T, pool *pgxpool.Pool) (string, string, string) {
	t.Helper()
	ctx := context.Background()
	svc := orgmgr.NewService(pool, templateDir(t))
	resp, err := svc.CreateOrg(ctx, contracts.CreateOrgRequest{Name: "KG Test", Template: "startup"})
	if err != nil {
		t.Fatalf("create org: %v", err)
	}
	snap, err := svc.GetOrgSnapshot(ctx, resp.OrganizationID)
	if err != nil {
		t.Fatalf("snapshot: %v", err)
	}
	var researchID, engineeringID string
	for _, w := range snap.Workers {
		if w.Role == "RESEARCHER" && researchID == "" {
			researchID = w.WorkerID
		}
		if w.Role == "ENGINEER" && engineeringID == "" {
			engineeringID = w.WorkerID
		}
	}
	return resp.OrganizationID, researchID, engineeringID
}

func TestClaimsEdgesSubgraph(t *testing.T) {
	pool := testPool(t)
	defer pool.Close()
	ctx := context.Background()

	orgID, _, _ := createOrgWorkers(t, pool)
	svc := NewService(pool)

	// kg_claims.investigation_id is FK-referenced; insert a real investigation row.
	invID := newUUID()
	if _, err := pool.Exec(ctx, `
		INSERT INTO investigations (id, organization_id, title, prompt, status)
		VALUES ($1, $2, 'test', 'test prompt', 'created')`, invID, orgID); err != nil {
		t.Fatalf("create investigation: %v", err)
	}

	// Two claims + a challenge edge.
	c1, err := svc.AddClaim(ctx, AddClaimRequest{
		OrganizationID: orgID, InvestigationID: invID, DepartmentID: "",
		ClaimText: "AI healthcare market is $45B by 2030", Confidence: 0.78,
	})
	if err != nil {
		t.Fatalf("add claim 1: %v", err)
	}
	c2, err := svc.AddClaim(ctx, AddClaimRequest{
		OrganizationID: orgID, InvestigationID: invID,
		ClaimText: "Sample size too small to support that", Confidence: 0.85,
	})
	if err != nil {
		t.Fatalf("add claim 2: %v", err)
	}

	e, err := svc.AddEdge(ctx, AddEdgeRequest{
		OrganizationID: orgID, SourceID: c2.ID, TargetID: c1.ID,
		Relation: "challenges", Weight: 0.9, Note: "n=12 underpowered",
	})
	if err != nil {
		t.Fatalf("add edge: %v", err)
	}
	if e.Relation != "challenges" {
		t.Fatalf("relation = %s", e.Relation)
	}

	sg, err := svc.SubgraphOf(ctx, orgID, invID)
	if err != nil {
		t.Fatalf("subgraph: %v", err)
	}
	if len(sg.Claims) != 2 {
		t.Fatalf("claims = %d, want 2", len(sg.Claims))
	}
	if len(sg.Edges) != 1 {
		t.Fatalf("edges = %d, want 1", len(sg.Edges))
	}
}

func TestRelationships(t *testing.T) {
	pool := testPool(t)
	defer pool.Close()
	ctx := context.Background()

	orgID, researchID, engineeringID := createOrgWorkers(t, pool)
	svc := NewService(pool)

	if err := svc.SetRelationship(ctx, orgID, Relationship{
		FromWorkerID: researchID, ToWorkerID: engineeringID,
		Type: "conflict", Strength: 0.7, Note: "disagreed on market sizing",
	}); err != nil {
		t.Fatalf("set relationship: %v", err)
	}

	rels, err := svc.RelationshipsOf(ctx, orgID, researchID)
	if err != nil {
		t.Fatalf("relationships: %v", err)
	}
	if len(rels) != 1 || rels[0].Type != "conflict" {
		t.Fatalf("relationships = %+v", rels)
	}
	if rels[0].ToWorkerID != engineeringID {
		t.Fatalf("to worker = %s", rels[0].ToWorkerID)
	}
}
