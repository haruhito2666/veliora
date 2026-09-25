package evolution

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
	resp, err := svc.CreateOrg(ctx, contracts.CreateOrgRequest{Name: "Evol Test", Template: "startup"})
	if err != nil {
		t.Fatalf("create org: %v", err)
	}
	snap, err := svc.GetOrgSnapshot(ctx, resp.OrganizationID)
	if err != nil {
		t.Fatalf("snapshot: %v", err)
	}
	return resp.OrganizationID, snap.Departments[0].DepartmentID
}

func TestProposalLifecycle(t *testing.T) {
	pool := testPool(t)
	defer pool.Close()
	ctx := context.Background()

	orgID, deptID := createOrg(t, pool)
	svc := NewService(pool)
	svc.ApplyFn = func(ctx context.Context, orgID string, ev EvolutionEvent) (map[string]interface{}, error) {
		return map[string]interface{}{"workers": 9, "departments": 6}, nil
	}

	e, err := svc.Propose(ctx, ProposeRequest{
		OrganizationID: orgID, EventType: TypeHire, TargetType: "department",
		TargetID: deptID, Description: "Hire a researcher",
		Rationale: "Research overloaded", TriggeredByRule: "overload",
	})
	if err != nil {
		t.Fatalf("propose: %v", err)
	}
	if e.Status != "proposed" {
		t.Fatalf("status = %s", e.Status)
	}

	if err := svc.StartDebate(ctx, orgID, e.EventID); err != nil {
		t.Fatalf("start debate: %v", err)
	}
	if err := svc.StartVote(ctx, orgID, e.EventID); err != nil {
		t.Fatalf("start vote: %v", err)
	}
	if err := svc.Approve(ctx, orgID, e.EventID); err != nil {
		t.Fatalf("approve: %v", err)
	}
	done, err := svc.Execute(ctx, orgID, e.EventID)
	if err != nil {
		t.Fatalf("execute: %v", err)
	}
	if done.Status != "applied" {
		t.Fatalf("status = %s, want applied", done.Status)
	}
	if done.BeforeState["workers"] == nil || done.AfterState["workers"] != float64(9) {
		t.Fatalf("before/after = %v / %v", done.BeforeState, done.AfterState)
	}
}

func TestInvalidTransitions(t *testing.T) {
	pool := testPool(t)
	defer pool.Close()
	ctx := context.Background()

	orgID, deptID := createOrg(t, pool)
	svc := NewService(pool)

	e, err := svc.Propose(ctx, ProposeRequest{
		OrganizationID: orgID, EventType: TypePromote, TargetType: "worker",
		TargetID: deptID, Description: "promote", Rationale: "high trust",
	})
	if err != nil {
		t.Fatal(err)
	}
	// Approve before debating/voting must fail.
	if err := svc.Approve(ctx, orgID, e.EventID); err == nil {
		t.Fatal("approve from proposed should fail")
	}
	// Execute before approved must fail.
	if _, err := svc.Execute(ctx, orgID, e.EventID); err == nil {
		t.Fatal("execute from proposed should fail")
	}
	// Reject from proposed must fail.
	if err := svc.Reject(ctx, orgID, e.EventID); err == nil {
		t.Fatal("reject from proposed should fail")
	}
}

func TestListAndAutoProposals(t *testing.T) {
	pool := testPool(t)
	defer pool.Close()
	ctx := context.Background()

	orgID, deptID := createOrg(t, pool)
	svc := NewService(pool)

	if _, err := svc.Propose(ctx, ProposeRequest{
		OrganizationID: orgID, EventType: TypePolicyRewrite, TargetType: "policy",
		TargetID: deptID, Description: "policy", Rationale: "update",
	}); err != nil {
		t.Fatal(err)
	}

	list, err := svc.List(ctx, orgID)
	if err != nil {
		t.Fatalf("list: %v", err)
	}
	if len(list) != 1 {
		t.Fatalf("list = %d", len(list))
	}

	// Auto-proposals: no overloaded dept / low-trust worker → none created.
	created, err := svc.AutoProposals(ctx, orgID)
	if err != nil {
		t.Fatalf("auto: %v", err)
	}
	if len(created) != 0 {
		t.Fatalf("auto created %d (should be 0): %v", len(created), created)
	}
}
