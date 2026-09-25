// Package contracts defines the cross-service DTOs (doc 15 §3.3:
// shared/contracts). These are the single source of truth for the shapes
// exchanged over NATS RPC and the REST gateway.
package contracts

// ── Organizations ──────────────────────────────────────────────────────

// CreateOrgRequest is POST /v1/organizations (doc 24 §5.1).
type CreateOrgRequest struct {
	Name            string             `json:"name"`
	Template        string             `json:"template"`
	Constitution    *ConstitutionDTO   `json:"constitution,omitempty"`
	DNA             map[string]float64 `json:"dna,omitempty"`
	CaspianChannels []string           `json:"caspian_channels,omitempty"`
}

// ConstitutionDTO mirrors doc 13 §13.4.2 content shape.
type ConstitutionDTO struct {
	Mission       string                 `json:"mission"`
	Principles    []string               `json:"principles"`
	Values        []string               `json:"values"`
	Ethics        []string               `json:"ethics"`
	DecisionRules map[string]interface{} `json:"decision_rules"`
}

// CreateOrgResponse is 201 from POST /v1/organizations (doc 24 §5.1).
type CreateOrgResponse struct {
	OrganizationID string          `json:"organization_id"`
	Name           string          `json:"name"`
	State          string          `json:"state"`
	Template       string          `json:"template"`
	ConstitutionID string          `json:"constitution_id"`
	DNAID          string          `json:"dna_id"`
	Departments    []DepartmentDTO `json:"departments"`
	CreatedAt      string          `json:"created_at"`
}

// DepartmentDTO is the department resource (doc 24 §7.1).
type DepartmentDTO struct {
	DepartmentID   string   `json:"department_id"`
	OrganizationID string   `json:"organization_id"`
	Name           string   `json:"name"`
	Role           string   `json:"role"`
	Charter        string   `json:"charter"`
	Workers        []string `json:"workers"`
	Influence      float64  `json:"influence"`
	Load           float64  `json:"load"`
	CreatedAt      string   `json:"created_at"`
}

// WorkerDTO is the worker resource (doc 24 §7.2).
type WorkerDTO struct {
	WorkerID       string  `json:"worker_id"`
	OrganizationID string  `json:"organization_id"`
	DepartmentID   string  `json:"department_id"`
	Name           string  `json:"name"`
	Role           string  `json:"role"`
	State          string  `json:"state"`
	TrustScore     float64 `json:"trust_score"`
	CurrentTaskID  string  `json:"current_task_id"`
	Promotions     int     `json:"promotions"`
	JoinedAt       string  `json:"joined_at"`
}

// OrgStatus is GET /v1/organizations/{id}/status (doc 24 §5.2).
type OrgStatus struct {
	OrganizationID string          `json:"organization_id"`
	State          string          `json:"state"`
	Health         float64         `json:"health"`
	Departments    []DeptStatusDTO `json:"departments"`
	Counts         OrgCounts       `json:"counts"`
}

// DeptStatusDTO is the live per-department status (doc 19 §6.1).
type DeptStatusDTO struct {
	Name           string  `json:"name"`
	WorkerState    string  `json:"worker_state"`
	Activity       string  `json:"activity"`
	CurrentTask    string  `json:"current_task,omitempty"`
	Artifacts      int     `json:"artifacts"`
	OpenChallenges int     `json:"open_challenges"`
	CaspianOpen    int     `json:"caspian_open"`
	Trust          float64 `json:"trust"`
	LastEventAt    string  `json:"last_event_at"`
}

// OrgCounts is the counts block of the status payload.
type OrgCounts struct {
	InvestigationsActive int `json:"investigations_active"`
	Meetings             int `json:"meetings"`
	Messages             int `json:"messages"`
}

// GetOrgRequest is a generic org-scoped lookup.
type GetOrgRequest struct {
	OrganizationID string `json:"organization_id"`
}

// OrgSnapshot is the full org projection (doc 24 §5.2).
type OrgSnapshot struct {
	OrganizationID string             `json:"organization_id"`
	Name           string             `json:"name"`
	State          string             `json:"state"`
	Template       string             `json:"template"`
	Health         float64            `json:"health"`
	DNA            map[string]float64 `json:"dna"`
	Constitution   *ConstitutionDTO   `json:"constitution,omitempty"`
	Departments    []DepartmentDTO    `json:"departments"`
	Workers        []WorkerDTO        `json:"workers"`
	CreatedAt      string             `json:"created_at"`
}

// OrgTemplatesDTO is the catalog of org templates.
type OrgTemplatesDTO struct {
	Templates []TemplateSummary `json:"templates"`
}

// TemplateSummary is a lightweight template description.
type TemplateSummary struct {
	ID          string             `json:"id"`
	Name        string             `json:"name"`
	Description string             `json:"description"`
	Departments []string           `json:"departments"`
	DefaultDNA  map[string]float64 `json:"default_dna"`
}

// RPCError is the error envelope returned over NATS RPC.
type RPCError struct {
	Error string `json:"error"`
}
