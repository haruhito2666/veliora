package communication

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
	resp, err := svc.CreateOrg(ctx, contracts.CreateOrgRequest{Name: "Comm Test", Template: "startup"})
	if err != nil {
		t.Fatalf("create org: %v", err)
	}
	snap, err := svc.GetOrgSnapshot(ctx, resp.OrganizationID)
	if err != nil {
		t.Fatalf("snapshot: %v", err)
	}
	var ceoID, researchID string
	for _, w := range snap.Workers {
		if w.Role == "CEO" {
			ceoID = w.WorkerID
		}
		if w.Role == "RESEARCHER" && researchID == "" {
			researchID = w.WorkerID
		}
	}
	return resp.OrganizationID, ceoID, researchID
}

func TestSendGetList(t *testing.T) {
	pool := testPool(t)
	defer pool.Close()
	ctx := context.Background()

	orgID, ceoID, researchID := createOrgWorkers(t, pool)
	svc := NewService(pool)

	// CEO asks Research for evidence (evidence_request kind).
	msg, err := svc.Send(ctx, SendRequest{
		OrganizationID: orgID, SenderWorkerID: ceoID, RecipientWorkerID: researchID,
		Kind: "evidence_request", Body: "Please validate the sample size in evidence e-1.",
	})
	if err != nil {
		t.Fatalf("send: %v", err)
	}
	if msg.MessageType != "evidence_request" {
		t.Fatalf("type = %s", msg.MessageType)
	}

	// Research replies as a thread.
	reply, err := svc.Send(ctx, SendRequest{
		OrganizationID: orgID, SenderWorkerID: researchID, RecipientWorkerID: ceoID,
		Kind: "statement", Body: "n=12, underpowered; will amend.",
		ParentMessageID: msg.MessageID,
	})
	if err != nil {
		t.Fatalf("reply: %v", err)
	}
	if reply.ParentMessageID != msg.MessageID {
		t.Fatalf("parent = %s", reply.ParentMessageID)
	}

	// Get by id.
	got, err := svc.Get(ctx, orgID, reply.MessageID)
	if err != nil {
		t.Fatalf("get: %v", err)
	}
	if got.Content != "n=12, underpowered; will amend." {
		t.Fatalf("content = %q", got.Content)
	}

	// List filtered by recipient (Research should have 1 inbound).
	list, err := svc.List(ctx, orgID, "", "", "", researchID, 10)
	if err != nil {
		t.Fatalf("list: %v", err)
	}
	if len(list) != 1 || list[0].SenderWorkerID != ceoID {
		t.Fatalf("inbound list = %+v", list)
	}

	// Broadcast (recipient empty) works.
	if _, err := svc.Send(ctx, SendRequest{
		OrganizationID: orgID, SenderWorkerID: ceoID,
		Kind: "directive", Body: "All departments: submit by EOD.",
	}); err != nil {
		t.Fatalf("broadcast: %v", err)
	}
}

func TestSendInvalidSenderFails(t *testing.T) {
	pool := testPool(t)
	defer pool.Close()
	ctx := context.Background()

	orgID, _, _ := createOrgWorkers(t, pool)
	svc := NewService(pool)

	_, err := svc.Send(ctx, SendRequest{
		OrganizationID: orgID, SenderWorkerID: "00000000-0000-4000-8000-000000000000",
		Kind: "statement", Body: "hello",
	})
	if err == nil {
		t.Fatal("expected error for invalid sender")
	}
}
