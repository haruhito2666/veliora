package memory

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

func createOrg(t *testing.T, pool *pgxpool.Pool) (string, string) {
	t.Helper()
	ctx := context.Background()
	svc := orgmgr.NewService(pool, templateDir(t))
	resp, err := svc.CreateOrg(ctx, contracts.CreateOrgRequest{Name: "Memory Test", Template: "startup"})
	if err != nil {
		t.Fatalf("create org: %v", err)
	}
	snap, err := svc.GetOrgSnapshot(ctx, resp.OrganizationID)
	if err != nil {
		t.Fatalf("snapshot: %v", err)
	}
	if len(snap.Departments) == 0 {
		t.Fatal("no departments")
	}
	return resp.OrganizationID, snap.Departments[0].DepartmentID
}

// stubEmbedder returns a deterministic 1536-dim embedding so recall orders by
// a cheap distance (tests the plumbing; keyword fallback is covered too).
type stubEmbedder struct{}

func (stubEmbedder) Embed(ctx context.Context, text string) ([]float64, error) {
	out := make([]float64, 1536)
	for i, c := range text {
		out[i%1536] += float64(c) / 1000.0
	}
	return out, nil
}

func TestMemoryCreateListRecall(t *testing.T) {
	pool := testPool(t)
	defer pool.Close()
	ctx := context.Background()

	orgID, deptID := createOrg(t, pool)
	svc := NewService(pool, stubEmbedder{})

	// Create two memories.
	m1, err := svc.Create(ctx, CreateRequest{
		OrganizationID: orgID, DepartmentID: deptID, Scope: ScopeDepartment,
		Type: TypeSemantic, Importance: 0.8,
		Content: map[string]interface{}{
			"summary": "Q3 market survey overestimated demand by 40%",
			"details": "Sample skewed to early adopters",
		},
	})
	if err != nil {
		t.Fatalf("create m1: %v", err)
	}
	m2, err := svc.Create(ctx, CreateRequest{
		OrganizationID: orgID, Scope: ScopeOrg, Type: TypePolicy, Importance: 0.9,
		Content: map[string]interface{}{
			"summary": "Engineering requires 2 reviewers for HIPAA claims",
			"details": "Adopted after investigation #12",
		},
	})
	if err != nil {
		t.Fatalf("create m2: %v", err)
	}

	// Get.
	got, err := svc.Get(ctx, orgID, m1.ID)
	if err != nil {
		t.Fatalf("get: %v", err)
	}
	if got.Scope != ScopeDepartment {
		t.Fatalf("scope = %s", got.Scope)
	}

	// List filtered by department.
	list, err := svc.List(ctx, orgID, "", deptID, "")
	if err != nil {
		t.Fatalf("list: %v", err)
	}
	if len(list) != 1 || list[0].ID != m1.ID {
		t.Fatalf("dept list = %d items, want 1 (m1)", len(list))
	}

	// Recall by query (keyword fallback since stub embedder exists but works).
	rec, err := svc.Recall(ctx, RecallRequest{OrganizationID: orgID, Query: "HIPAA", Limit: 5})
	if err != nil {
		t.Fatalf("recall: %v", err)
	}
	if len(rec) == 0 {
		t.Fatal("recall returned nothing for HIPAA")
	}
	if rec[0].ID != m2.ID {
		t.Fatalf("recall top = %s, want m2 (HIPAA)", rec[0].ID)
	}

	// Reinforce + decay don't error.
	if err := svc.Reinforce(ctx, orgID, m2.ID); err != nil {
		t.Fatalf("reinforce: %v", err)
	}
	if err := svc.Decay(ctx, orgID); err != nil {
		t.Fatalf("decay: %v", err)
	}
}
