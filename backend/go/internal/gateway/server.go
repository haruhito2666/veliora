// Package gateway implements the public API surface (doc 24): authless local
// REST proxying to engine RPCs over NATS, and WebSocket event streaming.
package gateway

import (
	"context"
	"encoding/json"
	"net/http"
	"time"

	"github.com/go-chi/chi/v5"
	"github.com/go-chi/chi/v5/middleware"
	"github.com/jackc/pgx/v5/pgxpool"

	"veliora-backend/internal/shared/bus"
	"veliora-backend/internal/shared/contracts"
)

// Server is the API gateway.
type Server struct {
	pool *pgxpool.Pool
	bus  *bus.Bus
	// RPC timeout for engine calls.
	rpcTimeout time.Duration
	// WSHub handles live streams.
	ws *WSHub
}

// NewServer builds the gateway.
func NewServer(pool *pgxpool.Pool, b *bus.Bus) *Server {
	return &Server{pool: pool, bus: b, rpcTimeout: 10 * time.Second, ws: NewWSHub(b)}
}

// Router wires all routes.
func (s *Server) Router() http.Handler {
	r := chi.NewRouter()
	r.Use(middleware.Logger)
	r.Use(middleware.Recoverer)
	r.Use(middleware.Timeout(30 * time.Second))

	// Authless local mode is intended for a trusted single-user instance.
	// Do not expose this gateway to an untrusted network.
	r.Get("/health", s.handleHealth)

	r.Route("/v1", func(r chi.Router) {
		// Organizations.
		r.Get("/organizations/templates", s.handleTemplates)
		r.Get("/organizations", s.handleListOrgs)
		r.Post("/organizations", s.handleCreateOrg)
		r.Route("/organizations/{orgID}", func(r chi.Router) {
			r.Use(s.orgCtx)
			r.Get("/", s.handleGetOrg)
			r.Get("/status", s.handleOrgStatus)
			r.Get("/constitution", s.handleGetConstitution)
			r.Get("/evolution/events", s.handleEvolutionList)
			r.Get("/workers", s.handleWorkers)
			r.Get("/communications", s.handleCommunications)

			// Investigations.
			r.Get("/investigations", s.handleListInvestigations)
			r.Post("/investigations", s.handleCreateInvestigation)
			r.Get("/investigations/{invID}", s.handleGetInvestigation)
			r.Post("/investigations/{invID}/cancel", s.handleCancelInvestigation)
			r.Get("/investigations/{invID}/evidence", s.handleEvidenceList)
			r.Get("/investigations/{invID}/report", s.handleReport)

			// Meetings.
			r.Get("/meetings", s.handleListMeetings)
			r.Get("/meetings/{mtgID}", s.handleGetMeeting)
		})
	})

	// WebSocket streams (authless local mode).
	r.Get("/ws/org/{orgID}", s.handleOrgWS)
	r.Get("/ws/investigations/{orgID}/{invID}", s.handleInvWS)

	return r
}

type contextKey string

const ctxOrgID contextKey = "org_id"

// orgCtx resolves the organization from the path and verifies that it exists.
// Authless local mode intentionally performs no principal or ownership check.
func (s *Server) orgCtx(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		orgID := chi.URLParam(r, "orgID")
		var exists bool
		if err := s.pool.QueryRow(r.Context(), `
			SELECT EXISTS(SELECT 1 FROM organizations WHERE id = $1)`, orgID).Scan(&exists); err != nil {
			writeError(w, 500, "internal_db", err.Error())
			return
		}
		if !exists {
			writeError(w, 404, "organization_not_found", "organization not found")
			return
		}
		ctx := context.WithValue(r.Context(), ctxOrgID, orgID)
		next.ServeHTTP(w, r.WithContext(ctx))
	})
}

// ── handlers ───────────────────────────────────────────────────────────

func (s *Server) handleHealth(w http.ResponseWriter, r *http.Request) {
	writeJSON(w, 200, map[string]string{"status": "ok", "mode": "authless-local"})
}

func (s *Server) handleTemplates(w http.ResponseWriter, r *http.Request) {
	writeJSON(w, 200, contracts.OrgTemplatesDTO{Templates: []contracts.TemplateSummary{
		{ID: "startup", Name: "Startup Society", Description: "Decides whether to build a venture",
			Departments: []string{"CEO", "Research", "Engineering", "Finance", "Marketing", "Legal"}},
	}})
}

func (s *Server) handleListOrgs(w http.ResponseWriter, r *http.Request) {
	rows, err := s.pool.Query(r.Context(), `
		SELECT id, name, template, status, health_score
		FROM organizations ORDER BY created_at`)
	if err != nil {
		writeError(w, 500, "internal_db", err.Error())
		return
	}
	defer rows.Close()
	type orgLite struct {
		ID       string  `json:"organization_id"`
		Name     string  `json:"name"`
		Template string  `json:"template"`
		State    string  `json:"state"`
		Health   float64 `json:"health"`
	}
	var out []orgLite
	for rows.Next() {
		var o orgLite
		if err := rows.Scan(&o.ID, &o.Name, &o.Template, &o.State, &o.Health); err != nil {
			writeError(w, 500, "internal_db", err.Error())
			return
		}
		out = append(out, o)
	}
	writeJSON(w, 200, map[string]interface{}{"data": out})
}

func (s *Server) handleCreateOrg(w http.ResponseWriter, r *http.Request) {
	var req contracts.CreateOrgRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		writeError(w, 400, "invalid_request", err.Error())
		return
	}
	var resp contracts.CreateOrgResponse
	if err := s.bus.Request("rpc.orgmgr.create", req, &resp, s.rpcTimeout); err != nil {
		writeError(w, 502, "internal_orgmgr", err.Error())
		return
	}
	writeJSON(w, 201, resp)
}

func (s *Server) handleGetOrg(w http.ResponseWriter, r *http.Request) {
	orgID := r.Context().Value(ctxOrgID).(string)
	var snap contracts.OrgSnapshot
	if err := s.bus.Request("rpc.orgmgr.get", contracts.GetOrgRequest{OrganizationID: orgID}, &snap, s.rpcTimeout); err != nil {
		writeError(w, 502, "internal_orgmgr", err.Error())
		return
	}
	writeJSON(w, 200, snap)
}

func (s *Server) handleOrgStatus(w http.ResponseWriter, r *http.Request) {
	orgID := r.Context().Value(ctxOrgID).(string)
	var st contracts.OrgStatus
	if err := s.bus.Request("rpc.orgmgr.status", contracts.GetOrgRequest{OrganizationID: orgID}, &st, s.rpcTimeout); err != nil {
		writeError(w, 502, "internal_orgmgr", err.Error())
		return
	}
	writeJSON(w, 200, st)
}

func (s *Server) handleGetConstitution(w http.ResponseWriter, r *http.Request) {
	orgID := r.Context().Value(ctxOrgID).(string)
	var out interface{}
	if err := s.bus.Request("rpc.constitution.get", contracts.GetOrgRequest{OrganizationID: orgID}, &out, s.rpcTimeout); err != nil {
		writeError(w, 502, "internal_constitution", err.Error())
		return
	}
	writeJSON(w, 200, out)
}

func (s *Server) handleEvolutionList(w http.ResponseWriter, r *http.Request) {
	orgID := r.Context().Value(ctxOrgID).(string)
	var out interface{}
	if err := s.bus.Request("rpc.evolution.list", map[string]string{"organization_id": orgID}, &out, s.rpcTimeout); err != nil {
		writeError(w, 502, "internal_evolution", err.Error())
		return
	}
	writeJSON(w, 200, out)
}

func (s *Server) handleWorkers(w http.ResponseWriter, r *http.Request) {
	orgID := r.Context().Value(ctxOrgID).(string)
	var snap contracts.OrgSnapshot
	if err := s.bus.Request("rpc.orgmgr.get", contracts.GetOrgRequest{OrganizationID: orgID}, &snap, s.rpcTimeout); err != nil {
		writeError(w, 502, "internal_orgmgr", err.Error())
		return
	}
	writeJSON(w, 200, map[string]interface{}{"data": snap.Workers})
}

func (s *Server) handleCommunications(w http.ResponseWriter, r *http.Request) {
	orgID := r.Context().Value(ctxOrgID).(string)
	var out interface{}
	if err := s.bus.Request("rpc.caspian.threads", map[string]string{"organization_id": orgID}, &out, s.rpcTimeout); err != nil {
		writeError(w, 502, "internal_caspian", err.Error())
		return
	}
	writeJSON(w, 200, out)
}

func (s *Server) handleListInvestigations(w http.ResponseWriter, r *http.Request) {
	orgID := r.Context().Value(ctxOrgID).(string)
	var out interface{}
	if err := s.bus.Request("rpc.investigation.list", map[string]string{"organization_id": orgID}, &out, s.rpcTimeout); err != nil {
		writeError(w, 502, "internal_investigation", err.Error())
		return
	}
	writeJSON(w, 200, out)
}

func (s *Server) handleCreateInvestigation(w http.ResponseWriter, r *http.Request) {
	orgID := r.Context().Value(ctxOrgID).(string)
	var req struct {
		Prompt string `json:"prompt"`
		Depth  string `json:"depth"`
	}
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		writeError(w, 400, "invalid_request", err.Error())
		return
	}
	var out interface{}
	if err := s.bus.Request("rpc.investigation.create", map[string]interface{}{
		"organization_id": orgID, "prompt": req.Prompt, "depth": req.Depth,
	}, &out, s.rpcTimeout); err != nil {
		writeError(w, 502, "internal_investigation", err.Error())
		return
	}
	writeJSON(w, 202, out)
}

func (s *Server) handleGetInvestigation(w http.ResponseWriter, r *http.Request) {
	orgID := r.Context().Value(ctxOrgID).(string)
	invID := chi.URLParam(r, "invID")
	var out interface{}
	if err := s.bus.Request("rpc.investigation.get", map[string]string{
		"organization_id": orgID, "investigation_id": invID,
	}, &out, s.rpcTimeout); err != nil {
		writeError(w, 502, "internal_investigation", err.Error())
		return
	}
	writeJSON(w, 200, out)
}

func (s *Server) handleCancelInvestigation(w http.ResponseWriter, r *http.Request) {
	orgID := r.Context().Value(ctxOrgID).(string)
	invID := chi.URLParam(r, "invID")
	var out interface{}
	if err := s.bus.Request("rpc.investigation.cancel", map[string]string{
		"organization_id": orgID, "investigation_id": invID,
	}, &out, s.rpcTimeout); err != nil {
		writeError(w, 502, "internal_investigation", err.Error())
		return
	}
	writeJSON(w, 200, out)
}

func (s *Server) handleEvidenceList(w http.ResponseWriter, r *http.Request) {
	orgID := r.Context().Value(ctxOrgID).(string)
	invID := chi.URLParam(r, "invID")
	var out interface{}
	if err := s.bus.Request("rpc.evidence.list", map[string]string{
		"organization_id": orgID, "investigation_id": invID,
	}, &out, s.rpcTimeout); err != nil {
		writeError(w, 502, "internal_evidence", err.Error())
		return
	}
	writeJSON(w, 200, out)
}

func (s *Server) handleReport(w http.ResponseWriter, r *http.Request) {
	orgID := r.Context().Value(ctxOrgID).(string)
	invID := chi.URLParam(r, "invID")
	var out interface{}
	if err := s.bus.Request("rpc.report.get", map[string]string{
		"organization_id": orgID, "investigation_id": invID,
	}, &out, s.rpcTimeout); err != nil {
		writeError(w, 502, "internal_report", err.Error())
		return
	}
	writeJSON(w, 200, out)
}

func (s *Server) handleListMeetings(w http.ResponseWriter, r *http.Request) {
	orgID := r.Context().Value(ctxOrgID).(string)
	var out interface{}
	if err := s.bus.Request("rpc.meeting.list", map[string]string{"organization_id": orgID}, &out, s.rpcTimeout); err != nil {
		writeError(w, 502, "internal_meeting", err.Error())
		return
	}
	writeJSON(w, 200, out)
}

func (s *Server) handleGetMeeting(w http.ResponseWriter, r *http.Request) {
	orgID := r.Context().Value(ctxOrgID).(string)
	mtgID := chi.URLParam(r, "mtgID")
	var out interface{}
	if err := s.bus.Request("rpc.meeting.get", map[string]string{
		"organization_id": orgID, "meeting_id": mtgID,
	}, &out, s.rpcTimeout); err != nil {
		writeError(w, 502, "internal_meeting", err.Error())
		return
	}
	writeJSON(w, 200, out)
}

// ── WebSocket ──────────────────────────────────────────────────────────

func (s *Server) handleOrgWS(w http.ResponseWriter, r *http.Request) {
	orgID := chi.URLParam(r, "orgID")
	s.ws.ServeOrg(w, r, orgID)
}

func (s *Server) handleInvWS(w http.ResponseWriter, r *http.Request) {
	orgID := chi.URLParam(r, "orgID")
	invID := chi.URLParam(r, "invID")
	s.ws.ServeInvestigation(w, r, orgID, invID)
}

// ── helpers ────────────────────────────────────────────────────────────

func writeJSON(w http.ResponseWriter, status int, v interface{}) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(status)
	_ = json.NewEncoder(w).Encode(v)
}

func writeError(w http.ResponseWriter, status int, code, message string) {
	writeJSON(w, status, map[string]interface{}{
		"error": map[string]string{"code": code, "message": message},
	})
}
