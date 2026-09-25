package constitution

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

// createOrgWithConstitution creates a startup org and returns its id + CEO worker id.
func createOrgWithConstitution(t *testing.T, pool *pgxpool.Pool) (string, string) {
	t.Helper()
	ctx := context.Background()
	svc := orgmgr.NewService(pool, templateDir(t))
	resp, err := svc.CreateOrg(ctx, contracts.CreateOrgRequest{Name: "Const Test", Template: "startup"})
	if err != nil {
		t.Fatalf("create org: %v", err)
	}
	snap, err := svc.GetOrgSnapshot(ctx, resp.OrganizationID)
	if err != nil {
		t.Fatalf("snapshot: %v", err)
	}
	var ceoID string
	for _, w := range snap.Workers {
		if w.Role == "CEO" {
			ceoID = w.WorkerID
			break
		}
	}
	return resp.OrganizationID, ceoID
}

func TestGetCurrentAndCheck(t *testing.T) {
	pool := testPool(t)
	defer pool.Close()
	ctx := context.Background()

	orgID, _ := createOrgWithConstitution(t, pool)
	svc := NewService(pool)

	c, err := svc.GetCurrent(ctx, orgID)
	if err != nil {
		t.Fatalf("get current: %v", err)
	}
	if c.Mission == "" {
		t.Fatal("mission empty")
	}
	if len(c.Principles) == 0 {
		t.Fatal("principles empty")
	}

	resp, err := svc.Check(ctx, CheckRequest{OrganizationID: orgID, Action: "amend"})
	if err != nil {
		t.Fatalf("check: %v", err)
	}
	if !resp.Allowed {
		t.Fatalf("amend should be allowed, got %+v", resp)
	}
	if !resp.Supermajority {
		t.Fatal("amend should require supermajority")
	}
}

func TestAmendmentLifecycle(t *testing.T) {
	pool := testPool(t)
	defer pool.Close()
	ctx := context.Background()

	orgID, ceoID := createOrgWithConstitution(t, pool)
	svc := NewService(pool)

	// Propose raising the confidence threshold to 0.8.
	amID, err := svc.ProposeAmendment(ctx, ProposeAmendmentRequest{
		OrganizationID:     orgID,
		ProposedByWorkerID: ceoID,
		Section:            "confidence_threshold",
		ChangeFrom:         0.72,
		ChangeTo:           0.80,
		Rationale:          "Require stronger evidence",
	})
	if err != nil {
		t.Fatalf("propose: %v", err)
	}
	if amID == "" {
		t.Fatal("empty amendment id")
	}

	// Not applied yet — current threshold still 0.72.
	c, err := svc.GetCurrent(ctx, orgID)
	if err != nil {
		t.Fatal(err)
	}
	if c.DecisionRules["confidence_threshold"] != 0.72 {
		t.Fatalf("threshold = %v before apply", c.DecisionRules["confidence_threshold"])
	}

	// Apply (ratify).
	if err := svc.ApplyAmendment(ctx, orgID, amID); err != nil {
		t.Fatalf("apply: %v", err)
	}

	c2, err := svc.GetCurrent(ctx, orgID)
	if err != nil {
		t.Fatal(err)
	}
	if c2.DecisionRules["confidence_threshold"] != 0.80 {
		t.Fatalf("threshold = %v after apply, want 0.8", c2.DecisionRules["confidence_threshold"])
	}

	// History has 1 ratified amendment.
	h, err := svc.History(ctx, orgID)
	if err != nil {
		t.Fatal(err)
	}
	if len(h) != 1 || h[0].Status != "ratified" {
		t.Fatalf("history = %+v", h)
	}
}

func TestApplyAmendmentTwiceFails(t *testing.T) {
	pool := testPool(t)
	defer pool.Close()
	ctx := context.Background()

	orgID, ceoID := createOrgWithConstitution(t, pool)
	svc := NewService(pool)

	amID, err := svc.ProposeAmendment(ctx, ProposeAmendmentRequest{
		OrganizationID: orgID, ProposedByWorkerID: ceoID,
		Section: "ceo_veto", ChangeFrom: false, ChangeTo: true, Rationale: "test",
	})
	if err != nil {
		t.Fatal(err)
	}
	if err := svc.ApplyAmendment(ctx, orgID, amID); err != nil {
		t.Fatalf("first apply: %v", err)
	}
	if err := svc.ApplyAmendment(ctx, orgID, amID); err == nil {
		t.Fatal("second apply should fail (already ratified)")
	}
}
