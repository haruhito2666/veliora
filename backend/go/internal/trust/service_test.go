package trust

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

// createOrg returns orgID + a Research worker id.
func createOrg(t *testing.T, pool *pgxpool.Pool) (string, string) {
	t.Helper()
	ctx := context.Background()
	svc := orgmgr.NewService(pool, templateDir(t))
	resp, err := svc.CreateOrg(ctx, contracts.CreateOrgRequest{Name: "Trust Test", Template: "startup"})
	if err != nil {
		t.Fatalf("create org: %v", err)
	}
	snap, err := svc.GetOrgSnapshot(ctx, resp.OrganizationID)
	if err != nil {
		t.Fatalf("snapshot: %v", err)
	}
	for _, w := range snap.Workers {
		if w.Role == "RESEARCHER" {
			return resp.OrganizationID, w.WorkerID
		}
	}
	t.Fatal("no researcher found")
	return "", ""
}

func TestNeutralStart(t *testing.T) {
	pool := testPool(t)
	defer pool.Close()
	ctx := context.Background()

	orgID, workerID := createOrg(t, pool)
	svc := NewService(pool)

	wt, err := svc.Get(ctx, orgID, workerID)
	if err != nil {
		t.Fatalf("get: %v", err)
	}
	// Neutral: α=β=1 → T=0.5, W = 0.5 − σ < 0.5.
	if wt.Score < 0.49 || wt.Score > 0.51 {
		t.Fatalf("neutral score = %v, want ~0.5", wt.Score)
	}
	if wt.Weight >= wt.Score {
		t.Fatalf("weight %v should be < score %v (shrinkage)", wt.Weight, wt.Score)
	}
}

func TestPositiveUpdateRaisesTrust(t *testing.T) {
	pool := testPool(t)
	defer pool.Close()
	ctx := context.Background()

	orgID, workerID := createOrg(t, pool)
	svc := NewService(pool)

	before, err := svc.Get(ctx, orgID, workerID)
	if err != nil {
		t.Fatal(err)
	}
	// 10 upheld evidence events.
	for i := 0; i < 10; i++ {
		if _, err := svc.Update(ctx, UpdateRequest{
			OrganizationID: orgID, WorkerID: workerID, Domain: "research",
			Positive: true, Context: "evidence upheld",
		}); err != nil {
			t.Fatalf("update %d: %v", i, err)
		}
	}
	after, err := svc.Get(ctx, orgID, workerID)
	if err != nil {
		t.Fatal(err)
	}
	if after.Score <= before.Score {
		t.Fatalf("score did not rise: %v -> %v", before.Score, after.Score)
	}
	if after.TaskCount != 10 {
		t.Fatalf("task count = %d, want 10", after.TaskCount)
	}
}

func TestNegativeUpdateLowersTrust(t *testing.T) {
	pool := testPool(t)
	defer pool.Close()
	ctx := context.Background()

	orgID, workerID := createOrg(t, pool)
	svc := NewService(pool)

	// Build up then tear down.
	for i := 0; i < 8; i++ {
		_, _ = svc.Update(ctx, UpdateRequest{
			OrganizationID: orgID, WorkerID: workerID, Domain: "forecast",
			Positive: true, Context: "good",
		})
	}
	before, _ := svc.Get(ctx, orgID, workerID)
	for i := 0; i < 8; i++ {
		_, _ = svc.Update(ctx, UpdateRequest{
			OrganizationID: orgID, WorkerID: workerID, Domain: "forecast",
			Positive: false, Context: "forecast missed",
		})
	}
	after, _ := svc.Get(ctx, orgID, workerID)
	if after.Score >= before.Score {
		t.Fatalf("score did not fall: %v -> %v", before.Score, after.Score)
	}
}

func TestWeightForVoting(t *testing.T) {
	pool := testPool(t)
	defer pool.Close()
	ctx := context.Background()

	orgID, workerID := createOrg(t, pool)
	svc := NewService(pool)

	w, err := svc.Weight(ctx, WeightRequest{OrganizationID: orgID, WorkerID: workerID, Domain: "research"})
	if err != nil {
		t.Fatalf("weight: %v", err)
	}
	if w.Weight <= 0 {
		t.Fatalf("weight = %v, want > 0", w.Weight)
	}

	// History should have records after updates.
	for i := 0; i < 3; i++ {
		_, _ = svc.Update(ctx, UpdateRequest{
			OrganizationID: orgID, WorkerID: workerID, Domain: "research",
			Positive: true, Context: "upheld",
		})
	}
	h, err := svc.History(ctx, orgID, workerID)
	if err != nil {
		t.Fatalf("history: %v", err)
	}
	if len(h) < 3 {
		t.Fatalf("history = %d records, want >= 3", len(h))
	}
}
