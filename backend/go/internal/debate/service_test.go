package debate

import (
	"context"
	"crypto/rand"
	"encoding/hex"
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

func createOrg(t *testing.T, pool *pgxpool.Pool) (string, []string, string) {
	t.Helper()
	ctx := context.Background()
	svc := orgmgr.NewService(pool, templateDir(t))
	resp, err := svc.CreateOrg(ctx, contracts.CreateOrgRequest{Name: "Debate Test", Template: "startup"})
	if err != nil {
		t.Fatalf("create org: %v", err)
	}
	snap, err := svc.GetOrgSnapshot(ctx, resp.OrganizationID)
	if err != nil {
		t.Fatalf("snapshot: %v", err)
	}
	ids := make([]string, 0, len(snap.Workers))
	for _, w := range snap.Workers {
		ids = append(ids, w.WorkerID)
	}
	return resp.OrganizationID, ids, ids[0]
}

func TestFullDebateProtocol(t *testing.T) {
	pool := testPool(t)
	defer pool.Close()
	ctx := context.Background()

	orgID, workerIDs, _ := createOrg(t, pool)

	// Real investigation row.
	invID := newTestUUID()
	if _, err := pool.Exec(ctx, `
		INSERT INTO investigations (id, organization_id, title, prompt, status)
		VALUES ($1, $2, 'market', 'should we go?', 'debating')`, invID, orgID); err != nil {
		t.Fatalf("create inv: %v", err)
	}

	svc := NewService(pool)
	svc.WeightFn = func(ctx context.Context, orgID, workerID string) (float64, error) {
		return 0.9, nil
	}

	d, err := svc.Open(ctx, OpenRequest{
		OrganizationID: orgID, InvestigationID: invID,
		Topic: "Should we recommend GO?", Type: TypePosition,
		ParticipantIDs: workerIDs,
	})
	if err != nil {
		t.Fatalf("open: %v", err)
	}
	if d.Phase != PhaseOpening {
		t.Fatalf("phase = %s, want opening", d.Phase)
	}

	// Ordering rule: a vote before the vote phase is rejected.
	if _, err := svc.AddArgument(ctx, orgID, d.DebateID, workerIDs[0], "vote", "yes"); err == nil {
		t.Fatal("vote in opening phase should be rejected")
	}

	// Advance through phases, posting the right kinds.
	svc.Advance(ctx, orgID, d.DebateID) // → evidence
	if _, err := svc.AddArgument(ctx, orgID, d.DebateID, workerIDs[0], "evidence", "market is $45B"); err != nil {
		t.Fatalf("evidence: %v", err)
	}
	svc.Advance(ctx, orgID, d.DebateID) // → cross_exam
	if _, err := svc.AddArgument(ctx, orgID, d.DebateID, workerIDs[1], "challenge", "sample too small"); err != nil {
		t.Fatalf("challenge: %v", err)
	}
	if _, err := svc.AddArgument(ctx, orgID, d.DebateID, workerIDs[0], "answer", "we have n=40"); err != nil {
		t.Fatalf("answer: %v", err)
	}
	svc.Advance(ctx, orgID, d.DebateID) // → rebuttal
	if _, err := svc.AddArgument(ctx, orgID, d.DebateID, workerIDs[2], "rebuttal", "still underpowered"); err != nil {
		t.Fatalf("rebuttal: %v", err)
	}
	svc.Advance(ctx, orgID, d.DebateID) // → synthesis
	if _, err := svc.AddArgument(ctx, orgID, d.DebateID, workerIDs[0], "synthesis", "GO with conditions"); err != nil {
		t.Fatalf("synthesis: %v", err)
	}
	svc.Advance(ctx, orgID, d.DebateID) // → vote

	// All vote.
	for _, w := range workerIDs {
		if _, err := svc.AddArgument(ctx, orgID, d.DebateID, w, "vote", "GO"); err != nil {
			t.Fatalf("vote %s: %v", w, err)
		}
	}

	// Conclude explicitly (auto-conclude may already have run).
	done, err := svc.Conclude(ctx, orgID, d.DebateID)
	if err != nil {
		t.Fatalf("conclude: %v", err)
	}
	if done.State != "concluded" {
		t.Fatalf("state = %s", done.State)
	}

	// Transcript is complete.
	tr, err := svc.Transcript(ctx, d.DebateID)
	if err != nil {
		t.Fatalf("transcript: %v", err)
	}
	if len(tr) == 0 {
		t.Fatal("empty transcript")
	}
}

func TestOpenRequiresTwoParticipants(t *testing.T) {
	pool := testPool(t)
	defer pool.Close()
	ctx := context.Background()

	orgID, workerIDs, _ := createOrg(t, pool)
	invID := newTestUUID()
	if _, err := pool.Exec(ctx, `
		INSERT INTO investigations (id, organization_id, title, prompt, status)
		VALUES ($1, $2, 'x', 'y', 'debating')`, invID, orgID); err != nil {
		t.Fatal(err)
	}

	svc := NewService(pool)
	_, err := svc.Open(ctx, OpenRequest{
		OrganizationID: orgID, InvestigationID: invID,
		Topic: "solo?", Type: TypePosition, ParticipantIDs: workerIDs[:1],
	})
	if err == nil {
		t.Fatal("opening with one participant should fail")
	}
}

func newTestUUID() string {
	b := make([]byte, 16)
	_, _ = rand.Read(b)
	b[6] = (b[6] & 0x0f) | 0x40
	b[8] = (b[8] & 0x3f) | 0x80
	s := hex.EncodeToString(b)
	return s[0:8] + "-" + s[8:12] + "-" + s[12:16] + "-" + s[16:20] + "-" + s[20:32]
}
