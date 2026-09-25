package caspian

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

func newTestUUID() string {
	b := make([]byte, 16)
	_, _ = rand.Read(b)
	b[6] = (b[6] & 0x0f) | 0x40
	b[8] = (b[8] & 0x3f) | 0x80
	s := hex.EncodeToString(b)
	return s[0:8] + "-" + s[8:12] + "-" + s[12:16] + "-" + s[16:20] + "-" + s[20:32]
}

// createOrg returns orgID + first worker id.
func createOrg(t *testing.T, pool *pgxpool.Pool) (string, string) {
	t.Helper()
	ctx := context.Background()
	svc := orgmgr.NewService(pool, templateDir(t))
	resp, err := svc.CreateOrg(ctx, contracts.CreateOrgRequest{Name: "Caspian Test", Template: "startup"})
	if err != nil {
		t.Fatalf("create org: %v", err)
	}
	snap, err := svc.GetOrgSnapshot(ctx, resp.OrganizationID)
	if err != nil {
		t.Fatalf("snapshot: %v", err)
	}
	if len(snap.Workers) == 0 {
		t.Fatal("no workers")
	}
	return resp.OrganizationID, snap.Workers[0].WorkerID
}

func TestSendWithoutCredentialsQueuesUnsent(t *testing.T) {
	pool := testPool(t)
	defer pool.Close()
	ctx := context.Background()

	orgID, workerID := createOrg(t, pool)
	// Empty config → no adapters → everything queues as unsent.
	svc := NewService(pool, Config{})

	// Create a real investigation (outreach is investigation-scoped).
	invID := newTestUUID()
	if _, err := pool.Exec(ctx, `
		INSERT INTO investigations (id, organization_id, title, prompt, status)
		VALUES ($1, $2, 'market survey', 'survey demand', 'researching')`, invID, orgID); err != nil {
		t.Fatalf("create investigation: %v", err)
	}

	// Add a contact with a telegram address.
	if err := svc.AddContact(ctx, Contact{
		OrganizationID: orgID, Name: "Beta User Group", RelationshipType: "customer",
		Channels: map[string]string{"telegram": "@beta_users"},
	}); err != nil {
		t.Fatalf("add contact: %v", err)
	}
	contacts, err := svc.ListContacts(ctx, orgID)
	if err != nil {
		t.Fatalf("list contacts: %v", err)
	}
	if len(contacts) != 1 {
		t.Fatalf("contacts = %d", len(contacts))
	}

	th, err := svc.Send(ctx, SendRequest{
		OrganizationID: orgID, WorkerID: workerID, InvestigationID: invID,
		ContactID: contacts[0].ID,
		Purpose: "survey", Subject: "Survey", Body: "Quick 3-question survey?",
	})
	if err != nil {
		t.Fatalf("send: %v", err)
	}
	if th.Status != "unsent" {
		t.Fatalf("status = %s, want unsent (no credentials)", th.Status)
	}
	if th.Error == "" {
		t.Fatal("error should describe missing credentials")
	}
	if th.OutreachStatus != "REFUSED" {
		t.Fatalf("outreach status = %s, want REFUSED", th.OutreachStatus)
	}

	// Threads list includes it.
	threads, err := svc.Threads(ctx, orgID)
	if err != nil {
		t.Fatalf("threads: %v", err)
	}
	if len(threads) != 1 || threads[0].Status != "unsent" {
		t.Fatalf("threads = %+v", threads)
	}
}

func TestChannelSelection(t *testing.T) {
	cases := []struct {
		purpose string
		want    string
	}{
		{"formal", ChannelEmail},
		{"survey", ChannelTelegram},
		{"dev_qa", ChannelDiscord},
		{"vendor", ChannelSlack},
		{"consumer", ChannelWhatsApp},
	}
	for _, tc := range cases {
		got := selectChannel(Contact{}, tc.purpose)
		if got != tc.want {
			t.Fatalf("purpose %s → %s, want %s", tc.purpose, got, tc.want)
		}
	}
	// Preferred channel wins.
	got := selectChannel(Contact{Channels: map[string]string{"preferred": "email"}}, "survey")
	if got != ChannelEmail {
		t.Fatalf("preferred channel = %s, want email", got)
	}
}
