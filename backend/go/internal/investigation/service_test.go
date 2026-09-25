package investigation

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

func createOrg(t *testing.T, pool *pgxpool.Pool) string {
	t.Helper()
	ctx := context.Background()
	svc := orgmgr.NewService(pool, templateDir(t))
	resp, err := svc.CreateOrg(ctx, contracts.CreateOrgRequest{Name: "Inv Test", Template: "startup"})
	if err != nil {
		t.Fatalf("create org: %v", err)
	}
	return resp.OrganizationID
}

func TestFullInvestigationLifecycle(t *testing.T) {
	pool := testPool(t)
	defer pool.Close()
	ctx := context.Background()

	orgID := createOrg(t, pool)
	svc := NewService(pool)

	inv, err := svc.Create(ctx, CreateRequest{
		OrganizationID: orgID, Prompt: "Should I build an AI healthcare startup?",
	})
	if err != nil {
		t.Fatalf("create: %v", err)
	}
	if inv.State != StatePlanning {
		t.Fatalf("state = %s, want planning", inv.State)
	}
	if inv.Charter == nil || inv.Charter.ConfidenceThreshold != 0.72 {
		t.Fatal("charter not signed")
	}

	// Plan and dispatch.
	if err := svc.StartPlanning(ctx, orgID, inv.InvestigationID, CreateRequest{
		OrganizationID: orgID, Prompt: inv.Prompt,
	}); err != nil {
		t.Fatalf("planning: %v", err)
	}
	got, _ := svc.Get(ctx, orgID, inv.InvestigationID)
	if got.State != StateResearching {
		t.Fatalf("state = %s after planning", got.State)
	}
	if len(got.Plan.Tasks) != 5 {
		t.Fatalf("plan tasks = %d, want 5", len(got.Plan.Tasks))
	}

	// Walk the rest of the happy path.
	for _, to := range []string{StateAnalyzing, StateDebating, StateSynthesizing, StateVote} {
		if err := svc.Transition(ctx, orgID, inv.InvestigationID, to); err != nil {
			t.Fatalf("transition %s: %v", to, err)
		}
	}

	// Confidence above threshold → reporting → completed.
	if err := svc.SetConfidence(ctx, orgID, inv.InvestigationID, 0.81); err != nil {
		t.Fatalf("set confidence: %v", err)
	}
	got, _ = svc.Get(ctx, orgID, inv.InvestigationID)
	if got.State != StateReporting {
		t.Fatalf("state = %s, want reporting", got.State)
	}
	if err := svc.Complete(ctx, orgID, inv.InvestigationID, "rep-1"); err != nil {
		t.Fatalf("complete: %v", err)
	}
	got, _ = svc.Get(ctx, orgID, inv.InvestigationID)
	if got.State != StateCompleted {
		t.Fatalf("state = %s, want completed", got.State)
	}
}

func TestConfidenceLoopBackToResearch(t *testing.T) {
	pool := testPool(t)
	defer pool.Close()
	ctx := context.Background()

	orgID := createOrg(t, pool)
	svc := NewService(pool)

	inv, err := svc.Create(ctx, CreateRequest{OrganizationID: orgID, Prompt: "low confidence test"})
	if err != nil {
		t.Fatal(err)
	}
	if err := svc.StartPlanning(ctx, orgID, inv.InvestigationID, CreateRequest{
		OrganizationID: orgID, Prompt: inv.Prompt}); err != nil {
		t.Fatal(err)
	}
	for _, to := range []string{StateAnalyzing, StateDebating, StateSynthesizing, StateVote} {
		if err := svc.Transition(ctx, orgID, inv.InvestigationID, to); err != nil {
			t.Fatalf("transition %s: %v", to, err)
		}
	}
	// Low confidence → loops back to researching (cycle 2).
	if err := svc.SetConfidence(ctx, orgID, inv.InvestigationID, 0.5); err != nil {
		t.Fatalf("set confidence: %v", err)
	}
	got, _ := svc.Get(ctx, orgID, inv.InvestigationID)
	if got.State != StateResearching {
		t.Fatalf("state = %s, want researching (loop)", got.State)
	}
	if got.CycleCount != 2 {
		t.Fatalf("cycle = %d, want 2", got.CycleCount)
	}
}

func TestInvalidTransitionRejected(t *testing.T) {
	pool := testPool(t)
	defer pool.Close()
	ctx := context.Background()

	orgID := createOrg(t, pool)
	svc := NewService(pool)

	inv, err := svc.Create(ctx, CreateRequest{OrganizationID: orgID, Prompt: "guards"})
	if err != nil {
		t.Fatal(err)
	}
	// Skip planning → jump to debating should fail.
	if err := svc.Transition(ctx, orgID, inv.InvestigationID, StateDebating); err == nil {
		t.Fatal("planning → debating should be rejected")
	}
	// Complete from planning should fail.
	if err := svc.Complete(ctx, orgID, inv.InvestigationID, "x"); err == nil {
		t.Fatal("complete from planning should be rejected")
	}
	// Cancel works from planning.
	if err := svc.Cancel(ctx, orgID, inv.InvestigationID); err != nil {
		t.Fatalf("cancel: %v", err)
	}
}
