package gateway

import (
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"testing"
	"time"

	"veliora-backend/internal/shared/bus"
	"veliora-backend/internal/shared/contracts"
)

func TestHealthEndpointAuthless(t *testing.T) {
	router := NewServer(nil, nil).Router()
	req := httptest.NewRequest(http.MethodGet, "/health", nil)
	rec := httptest.NewRecorder()

	router.ServeHTTP(rec, req)

	if rec.Code != http.StatusOK {
		t.Fatalf("status = %d, want 200", rec.Code)
	}
	var body map[string]string
	if err := json.NewDecoder(rec.Body).Decode(&body); err != nil {
		t.Fatalf("decode health response: %v", err)
	}
	if body["mode"] != "authless-local" {
		t.Fatalf("mode = %q, want authless-local", body["mode"])
	}
}

// TestOrgManagerRPC verifies the org-manager service responds over NATS.
func TestOrgManagerRPC(t *testing.T) {
	b, err := bus.Connect("nats://localhost:4222")
	if err != nil {
		t.Skipf("NATS not available: %v", err)
	}
	defer b.Close()

	var resp contracts.CreateOrgResponse
	if err := b.Request("rpc.orgmgr.create",
		contracts.CreateOrgRequest{Name: "Smoke Org", Template: "startup"},
		&resp, 5*time.Second); err != nil {
		t.Fatalf("create rpc: %v", err)
	}
	if resp.OrganizationID == "" {
		t.Fatal("empty org id")
	}

	var snap contracts.OrgSnapshot
	if err := b.Request("rpc.orgmgr.get",
		contracts.GetOrgRequest{OrganizationID: resp.OrganizationID},
		&snap, 5*time.Second); err != nil {
		t.Fatalf("get rpc: %v", err)
	}
	if len(snap.Workers) != 8 {
		t.Fatalf("workers = %d, want 8", len(snap.Workers))
	}

	var st contracts.OrgStatus
	if err := b.Request("rpc.orgmgr.status",
		contracts.GetOrgRequest{OrganizationID: resp.OrganizationID},
		&st, 5*time.Second); err != nil {
		t.Fatalf("status rpc: %v", err)
	}
	if st.State != "active" {
		t.Fatalf("state = %s", st.State)
	}
}
