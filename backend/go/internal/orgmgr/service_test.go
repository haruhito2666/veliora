package orgmgr

import (
	"context"
	"os"
	"testing"

	"github.com/jackc/pgx/v5/pgxpool"

	"veliora-backend/internal/shared/contracts"
)

// testPool returns a pool to the local dev DB.
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

// templateDir finds the templates dir relative to repo root.
func templateDir(t *testing.T) string {
	t.Helper()
	// Tests run from backend/go/internal/orgmgr; templates live at repo root.
	for _, p := range []string{
		"../../../../templates/organizations",
		"../../../../../templates/organizations",
		"/Users/priyanshuboruah/societyos/templates/organizations",
	} {
		if _, err := os.Stat(p); err == nil {
			return p
		}
	}
	t.Skip("templates dir not found")
	return ""
}

func TestCreateStartupOrg(t *testing.T) {
	pool := testPool(t)
	defer pool.Close()
	ctx := context.Background()

	svc := NewService(pool, templateDir(t))
	resp, err := svc.CreateOrg(ctx, contracts.CreateOrgRequest{
		Name:     "Test Startup",
		Template: "startup",
	})
	if err != nil {
		t.Fatalf("create org: %v", err)
	}
	if resp.OrganizationID == "" {
		t.Fatal("empty org id")
	}
	if resp.State != "active" {
		t.Fatalf("state = %s, want active", resp.State)
	}
	// Startup template: 6 departments.
	if len(resp.Departments) != 6 {
		t.Fatalf("departments = %d, want 6", len(resp.Departments))
	}

	// Snapshot should reflect departments + workers.
	snap, err := svc.GetOrgSnapshot(ctx, resp.OrganizationID)
	if err != nil {
		t.Fatalf("snapshot: %v", err)
	}
	if len(snap.Departments) != 6 {
		t.Fatalf("snapshot depts = %d, want 6", len(snap.Departments))
	}
	if len(snap.Workers) != 8 {
		t.Fatalf("workers = %d, want 8 (CEO1+Research2+Eng2+Fin1+Mkt1+Legal1)", len(snap.Workers))
	}
	if snap.Constitution == nil || snap.Constitution.Mission == "" {
		t.Fatal("constitution missing")
	}
	if snap.DNA["accuracy"] == 0 {
		t.Fatal("dna not applied")
	}

	// Status should be healthy and active.
	st, err := svc.GetOrgStatus(ctx, resp.OrganizationID)
	if err != nil {
		t.Fatalf("status: %v", err)
	}
	if st.State != "active" {
		t.Fatalf("status state = %s", st.State)
	}
	if len(st.Departments) == 0 {
		t.Fatal("status has no departments")
	}
}

func TestCreateOrgReplayConsistency(t *testing.T) {
	pool := testPool(t)
	defer pool.Close()
	ctx := context.Background()

	svc := NewService(pool, templateDir(t))
	resp, err := svc.CreateOrg(ctx, contracts.CreateOrgRequest{
		Name:     "Replay Test",
		Template: "startup",
	})
	if err != nil {
		t.Fatalf("create: %v", err)
	}

	// The org's event stream must contain organization.created as seq 1.
	var count int
	if err := pool.QueryRow(ctx, `
		SELECT COUNT(*) FROM events WHERE organization_id = $1`, resp.OrganizationID).Scan(&count); err != nil {
		t.Fatalf("count events: %v", err)
	}
	if count != 1 {
		t.Fatalf("events = %d, want 1", count)
	}
	var seq int64
	if err := pool.QueryRow(ctx, `
		SELECT sequence_number FROM events WHERE organization_id = $1`, resp.OrganizationID).Scan(&seq); err != nil {
		t.Fatal(err)
	}
	if seq != 1 {
		t.Fatalf("sequence = %d, want 1", seq)
	}
}
