package events

import (
	"context"
	"crypto/rand"
	"encoding/hex"
	"os"
	"testing"

	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgxpool"
	"github.com/google/uuid"
)

func randomHex(n int) string {
	b := make([]byte, n)
	_, _ = rand.Read(b)
	return hex.EncodeToString(b)
}

func randomOrgID(t *testing.T) string {
	t.Helper()
	u, err := uuid.NewRandom()
	if err != nil {
		t.Fatalf("uuid: %v", err)
	}
	return u.String()
}

// createTestOrg inserts a minimal organizations row for the given id,
// satisfying the org_sequences FK.
func createTestOrg(t *testing.T, ctx context.Context, pool *pgxpool.Pool, id string) {
	t.Helper()
	if _, err := pool.Exec(ctx,
		`INSERT INTO organizations (id, name, template, status)
		 VALUES ($1, 'test', 'startup', 'active')`, id); err != nil {
		t.Fatalf("create test org: %v", err)
	}
}

// testPool returns a pool to the local dev DB, skipping if unavailable.
func testPool(t *testing.T) *pgxpool.Pool {
	t.Helper()
	url := os.Getenv("DATABASE_URL")
	if url == "" {
		url = "postgres://veliora:veliora@localhost:5432/veliora?sslmode=disable"
	}
	pool, err := pgxpool.New(context.Background(), url)
	if err != nil {
		t.Skipf("no DB available: %v", err)
	}
	if err := pool.Ping(context.Background()); err != nil {
		t.Skipf("DB not reachable: %v", err)
	}
	return pool
}

func TestAppenderMonotonicSequences(t *testing.T) {
	pool := testPool(t)
	defer pool.Close()
	ctx := context.Background()

	// Isolate with a unique org id.
	orgID := randomOrgID(t)
	createTestOrg(t, ctx, pool, orgID)
	app := NewAppender(pool)

	root := Root(orgID, EvOrgCreated, CatOrganization, "test",
		Source{Component: "test"}, map[string]interface{}{"name": "x"})
	seq1, err := app.Append(ctx, root)
	if err != nil {
		t.Fatalf("append root: %v", err)
	}
	if seq1 != 1 {
		t.Fatalf("first sequence = %d, want 1", seq1)
	}

	child := Child(root, orgID, EvWorkerHired, CatWorker, "test",
		Source{Component: "test"}, map[string]interface{}{"worker_id": "w1"})
	seq2, err := app.Append(ctx, child)
	if err != nil {
		t.Fatalf("append child: %v", err)
	}
	if seq2 != 2 {
		t.Fatalf("second sequence = %d, want 2", seq2)
	}

	// Different org gets its own sequence starting at 1.
	org2 := randomOrgID(t)
	createTestOrg(t, ctx, pool, org2)
	root2 := Root(org2, EvOrgCreated, CatOrganization, "test",
		Source{Component: "test"}, map[string]interface{}{"name": "y"})
	seq3, err := app.Append(ctx, root2)
	if err != nil {
		t.Fatalf("append org2 root: %v", err)
	}
	if seq3 != 1 {
		t.Fatalf("org2 first sequence = %d, want 1", seq3)
	}
}

func TestAppenderIdempotentSameEventID(t *testing.T) {
	pool := testPool(t)
	defer pool.Close()
	ctx := context.Background()
	orgID := randomOrgID(t)
	createTestOrg(t, ctx, pool, orgID)
	app := NewAppender(pool)

	root := Root(orgID, EvOrgCreated, CatOrganization, "test",
		Source{Component: "test"}, map[string]interface{}{"name": "x"})
	seq1, err := app.Append(ctx, root)
	if err != nil {
		t.Fatalf("append: %v", err)
	}
	// Retry the same event id: sequence must NOT advance.
	seq2, err := app.Append(ctx, root)
	if err != nil {
		t.Fatalf("append retry: %v", err)
	}
	if seq2 != seq1 {
		t.Fatalf("retry advanced sequence: %d -> %d", seq1, seq2)
	}
	// The org sequence row should be 1, not 2.
	var last int64
	if err := pool.QueryRow(ctx,
		`SELECT last FROM org_sequences WHERE organization_id = $1`, orgID).Scan(&last); err != nil {
		t.Fatalf("read sequence: %v", err)
	}
	if last != 1 {
		t.Fatalf("org_sequences.last = %d, want 1", last)
	}
}

func TestProjectorIdempotentReplay(t *testing.T) {
	pool := testPool(t)
	defer pool.Close()
	ctx := context.Background()
	orgID := randomOrgID(t)
	createTestOrg(t, ctx, pool, orgID)
	app := NewAppender(pool)

	// Append 3 events.
	root := Root(orgID, EvOrgCreated, CatOrganization, "test",
		Source{Component: "test"}, map[string]interface{}{"name": "x"})
	if _, err := app.Append(ctx, root); err != nil {
		t.Fatal(err)
	}
	for i := 0; i < 2; i++ {
		child := Child(root, orgID, EvWorkerHired, CatWorker, "test",
			Source{Component: "test"}, map[string]interface{}{"n": i})
		if _, err := app.Append(ctx, child); err != nil {
			t.Fatal(err)
		}
	}

	// Projector counts worker.hired events.
	count := 0
	proj := NewProjector(pool, "test-projector-"+randomHex(6), orgID,
		func(ctx context.Context, tx pgx.Tx, ev Envelope) error {
			if ev.EventType == EvWorkerHired {
				count++
			}
			return nil
		})
	n, err := proj.Run(ctx)
	if err != nil {
		t.Fatalf("projector run: %v", err)
	}
	if n != 3 {
		t.Fatalf("projector processed %d events, want 3", n)
	}
	if count != 2 {
		t.Fatalf("projector counted %d hired, want 2", count)
	}

	// Re-run: watermark prevents reprocessing; count unchanged.
	count = 0
	n, err = proj.Run(ctx)
	if err != nil {
		t.Fatalf("projector re-run: %v", err)
	}
	if n != 0 {
		t.Fatalf("projector re-run processed %d, want 0", n)
	}
	if count != 0 {
		t.Fatalf("projector re-run counted %d, want 0", count)
	}
}
