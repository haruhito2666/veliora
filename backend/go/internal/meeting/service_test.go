package meeting

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

func createOrgWorkers(t *testing.T, pool *pgxpool.Pool) (string, []string, string) {
	t.Helper()
	ctx := context.Background()
	svc := orgmgr.NewService(pool, templateDir(t))
	resp, err := svc.CreateOrg(ctx, contracts.CreateOrgRequest{Name: "Meeting Test", Template: "startup"})
	if err != nil {
		t.Fatalf("create org: %v", err)
	}
	snap, err := svc.GetOrgSnapshot(ctx, resp.OrganizationID)
	if err != nil {
		t.Fatalf("snapshot: %v", err)
	}
	ids := make([]string, 0, len(snap.Workers))
	var ceoID string
	for _, w := range snap.Workers {
		ids = append(ids, w.WorkerID)
		if w.Role == "CEO" {
			ceoID = w.WorkerID
		}
	}
	return resp.OrganizationID, ids, ceoID
}

func TestFullMeetingLifecycle(t *testing.T) {
	pool := testPool(t)
	defer pool.Close()
	ctx := context.Background()

	orgID, workerIDs, ceoID := createOrgWorkers(t, pool)
	svc := NewService(pool)

	m, err := svc.Call(ctx, CallRequest{
		OrganizationID: orgID,
		Title:          "Evidence Challenge: market size",
		Type:           TypeCrossFunctional,
		ChairWorkerID:  ceoID,
		ParticipantIDs: workerIDs,
		Agenda: []AgendaItem{
			{Title: "Review Research evidence", Type: "status", TimeBudgetMin: 5},
			{Title: "Engineering critique", Type: "decision", TimeBudgetMin: 10},
			{Title: "Vote", Type: "decision", TimeBudgetMin: 5},
		},
	})
	if err != nil {
		t.Fatalf("call: %v", err)
	}
	if m.State != "called" {
		t.Fatalf("state = %s, want called", m.State)
	}
	if len(m.Participants) != len(workerIDs) {
		t.Fatalf("participants = %d, want %d", len(m.Participants), len(workerIDs))
	}
	if m.Chair != ceoID {
		t.Fatalf("chair = %s, want %s", m.Chair, ceoID)
	}

	// Set agenda.
	if err := svc.SetAgenda(ctx, orgID, m.MeetingID, []AgendaItem{
		{Title: "Vote", Type: "decision", TimeBudgetMin: 5},
	}); err != nil {
		t.Fatalf("set agenda: %v", err)
	}

	// Advance stages.
	if err := svc.Notify(ctx, orgID, m.MeetingID); err != nil {
		t.Fatalf("notify: %v", err)
	}
	if err := svc.Prepare(ctx, orgID, m.MeetingID); err != nil {
		t.Fatalf("prepare: %v", err)
	}
	if err := svc.Start(ctx, orgID, m.MeetingID); err != nil {
		t.Fatalf("start: %v", err)
	}
	if err := svc.RecordMinutes(ctx, orgID, m.MeetingID, "All agreed on the market sizing."); err != nil {
		t.Fatalf("minutes: %v", err)
	}
	if err := svc.End(ctx, orgID, m.MeetingID, "Vote passed 4-1"); err != nil {
		t.Fatalf("end: %v", err)
	}

	got, err := svc.Get(ctx, orgID, m.MeetingID)
	if err != nil {
		t.Fatalf("get: %v", err)
	}
	if got.State != "ended" {
		t.Fatalf("state = %s, want ended", got.State)
	}
	if got.EndedAt == "" {
		t.Fatal("ended_at not set")
	}
	if len(got.Agenda) != 1 {
		t.Fatalf("agenda = %d items", len(got.Agenda))
	}

	// List.
	list, err := svc.List(ctx, orgID, "")
	if err != nil {
		t.Fatalf("list: %v", err)
	}
	if len(list) != 1 {
		t.Fatalf("list = %d", len(list))
	}
}

func TestInvalidTransitionRejected(t *testing.T) {
	pool := testPool(t)
	defer pool.Close()
	ctx := context.Background()

	orgID, workerIDs, ceoID := createOrgWorkers(t, pool)
	svc := NewService(pool)

	m, err := svc.Call(ctx, CallRequest{
		OrganizationID: orgID, Title: "T", Type: TypeBoard,
		ChairWorkerID: ceoID, ParticipantIDs: workerIDs,
	})
	if err != nil {
		t.Fatalf("call: %v", err)
	}
	// Start before preparing must fail.
	if err := svc.Start(ctx, orgID, m.MeetingID); err == nil {
		t.Fatal("start from 'called' should fail")
	}
	// End from 'called' must fail.
	if err := svc.End(ctx, orgID, m.MeetingID, "x"); err == nil {
		t.Fatal("end from 'called' should fail")
	}
}
