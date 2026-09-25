// Package orgmgr implements the Organization Manager engine (doc 04 §3.1,
// doc 25 §3): org lifecycle, template instantiation, and worker materialization.
package orgmgr

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"time"

	"github.com/google/uuid"
	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgxpool"

	"veliora-backend/internal/shared/contracts"
	"veliora-backend/internal/shared/events"
)

// Service is the Organization Manager.
type Service struct {
	pool        *pgxpool.Pool
	appender    *events.Appender
	templateDir string
}

// NewService builds the Organization Manager.
func NewService(pool *pgxpool.Pool, templateDir string) *Service {
	return &Service{pool: pool, appender: events.NewAppender(pool), templateDir: templateDir}
}

// ErrNotFound is returned for missing resources.
var ErrNotFound = errors.New("not found")

// ErrInvalidTransition is returned when a state-machine guard rejects an act.
var ErrInvalidTransition = errors.New("invalid_transition")

// CreateOrg materializes a template into a full organization (doc 24 §5.1,
// doc 05 §3.3). Everything is persisted in one transaction with the root
// organization.created event.
func (s *Service) CreateOrg(ctx context.Context, req contracts.CreateOrgRequest) (*contracts.CreateOrgResponse, error) {
	tmpl, err := LoadTemplate(req.Template, s.templateDir)
	if err != nil {
		return nil, fmt.Errorf("load template %q: %w", req.Template, err)
	}

	// Merge optional overrides (user-supplied DNA/constitution).
	dna := tmpl.DNA
	if len(req.DNA) > 0 {
		for k, v := range req.DNA {
			dna[k] = v
		}
	}
	constContent := tmpl.Constitution
	if req.Constitution != nil {
		c := map[string]interface{}{}
		if req.Constitution.Mission != "" {
			c["mission"] = req.Constitution.Mission
		} else {
			c["mission"] = constContent["mission"]
		}
		c["principles"] = firstNonNilList(req.Constitution.Principles, constContent["principles"])
		c["values"] = firstNonNilList(req.Constitution.Values, constContent["values"])
		c["ethics"] = firstNonNilList(req.Constitution.Ethics, constContent["ethics"])
		c["decision_rules"] = firstNonNilMap(req.Constitution.DecisionRules, constContent["decision_rules"])
		constContent = c
	}

	orgID := uuid.NewString()
	now := time.Now().UTC()
	source := events.Source{Component: "org-manager"}
	root := events.Root(orgID, events.EvOrgCreated, events.CatOrganization, "org-manager",
		source, map[string]interface{}{
			"name": req.Name, "template": req.Template, "dna": dna,
		})

	tx, err := s.pool.Begin(ctx)
	if err != nil {
		return nil, err
	}
	defer func() { _ = tx.Rollback(ctx) }()

	// Organization row (status forming; constitution/DNA linked below).
	if _, err := tx.Exec(ctx, `
		INSERT INTO organizations (id, name, template, status, metadata)
		VALUES ($1, $2, $3, 'forming', $4::jsonb)`,
		orgID, req.Name, req.Template,
		jsonMap(map[string]interface{}{"caspian_channels": req.CaspianChannels})); err != nil {
		return nil, err
	}

	// DNA profile (version 1, current).
	dnaJSON, _ := json.Marshal(dna)
	var dnaID string
	if err := tx.QueryRow(ctx, `
		INSERT INTO dna_profiles (organization_id, version, parameters, is_current)
		VALUES ($1, 1, $2::jsonb, true) RETURNING id`,
		orgID, string(dnaJSON)).Scan(&dnaID); err != nil {
		return nil, err
	}

	// Constitution (version 1, current; auto-ratified for templates).
	constJSON, _ := json.Marshal(constContent)
	var constID string
	if err := tx.QueryRow(ctx, `
		INSERT INTO constitutions (organization_id, version, content, is_current, ratified_at)
		VALUES ($1, 1, $2::jsonb, true, now()) RETURNING id`,
		orgID, string(constJSON)).Scan(&constID); err != nil {
		return nil, err
	}

	// Link org to current dna + constitution, transition to active.
	if _, err := tx.Exec(ctx, `
		UPDATE organizations SET current_dna_profile_id = $2, current_constitution_id = $3, status = 'active'
		WHERE id = $1`, orgID, dnaID, constID); err != nil {
		return nil, err
	}

	// Materialize departments and workers.
	var depts []contracts.DepartmentDTO
	for _, td := range tmpl.Departments {
		charterJSON, _ := json.Marshal(td.Charter)
		var deptID string
		if err := tx.QueryRow(ctx, `
			INSERT INTO departments (organization_id, name, charter, status)
			VALUES ($1, $2, $3::jsonb, 'active') RETURNING id`,
			orgID, td.Name, string(charterJSON)).Scan(&deptID); err != nil {
			return nil, err
		}
		for _, role := range td.Roles {
			for i := 0; i < role.Count; i++ {
				cfg := jsonMap(map[string]interface{}{
					"model":   "gpt-4o",
					"persona": fmt.Sprintf("%s %d", role.Role, i+1),
					"role":    role.Role,
				})
				var workerID string
				// Pass raw JSON bytes so pgx encodes it as JSONB (an object),
				// not as a JSON string wrapped in an array.
				if err := tx.QueryRow(ctx, `
					INSERT INTO workers (organization_id, department_id, role, status, configuration, current_trust_score)
					VALUES ($1, $2, $3, 'active', $4, 50.0) RETURNING id`,
					orgID, deptID, role.Role, []byte(cfg)).Scan(&workerID); err != nil {
					return nil, err
				}
				_ = workerID
			}
		}
		depts = append(depts, contracts.DepartmentDTO{
			DepartmentID: deptID, OrganizationID: orgID, Name: td.Name, Role: "department",
			CreatedAt: now.Format(time.RFC3339),
		})
	}

	// Append the root event in the same tx.
	if _, err := s.appender.AppendTx(ctx, tx, root); err != nil {
		return nil, err
	}

	if err := tx.Commit(ctx); err != nil {
		return nil, err
	}

	return &contracts.CreateOrgResponse{
		OrganizationID: orgID,
		Name:           req.Name,
		State:          "active",
		Template:       req.Template,
		ConstitutionID: constID,
		DNAID:          dnaID,
		Departments:    depts,
		CreatedAt:      now.Format(time.RFC3339),
	}, nil
}

// GetOrgSnapshot returns the full org projection.
func (s *Service) GetOrgSnapshot(ctx context.Context, orgID string) (*contracts.OrgSnapshot, error) {
	var snap contracts.OrgSnapshot
	var dnaJSON, constJSON []byte
	var createdAt time.Time
	err := s.pool.QueryRow(ctx, `
		SELECT o.id, o.name, o.status, o.template, o.health_score,
		       d.parameters, c.content, o.created_at
		FROM organizations o
		LEFT JOIN dna_profiles d ON d.id = o.current_dna_profile_id
		LEFT JOIN constitutions c ON c.id = o.current_constitution_id
		WHERE o.id = $1`, orgID).Scan(
		&snap.OrganizationID, &snap.Name, &snap.State, &snap.Template, &snap.Health,
		&dnaJSON, &constJSON, &createdAt)
	if err != nil {
		if err == pgx.ErrNoRows {
			return nil, ErrNotFound
		}
		return nil, err
	}
	_ = json.Unmarshal(dnaJSON, &snap.DNA)
	if constJSON != nil {
		var c contracts.ConstitutionDTO
		if json.Unmarshal(constJSON, &c) == nil {
			snap.Constitution = &c
		}
	}
	snap.CreatedAt = createdAt.Format(time.RFC3339)

	// Departments + workers.
	rows, err := s.pool.Query(ctx, `
		SELECT d.id, d.name, d.charter
		FROM departments d
		WHERE d.organization_id = $1 AND d.status = 'active'
		ORDER BY d.created_at`, orgID)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	for rows.Next() {
		var d contracts.DepartmentDTO
		var charter []byte
		if err := rows.Scan(&d.DepartmentID, &d.Name, &charter); err != nil {
			return nil, err
		}
		d.OrganizationID = orgID
		snap.Departments = append(snap.Departments, d)
	}
	for i := range snap.Departments {
		wRows, err := s.pool.Query(ctx, `
			SELECT id, role, current_trust_score
			FROM workers WHERE department_id = $1 AND status != 'terminated'`, snap.Departments[i].DepartmentID)
		if err != nil {
			return nil, err
		}
		defer wRows.Close()
		for wRows.Next() {
			var w contracts.WorkerDTO
			var trust float64
			if err := wRows.Scan(&w.WorkerID, &w.Role, &trust); err != nil {
				return nil, err
			}
			w.OrganizationID = orgID
			w.DepartmentID = snap.Departments[i].DepartmentID
			w.TrustScore = trust
			w.State = "active"
			snap.Departments[i].Workers = append(snap.Departments[i].Workers, w.WorkerID)
			snap.Workers = append(snap.Workers, w)
		}
	}
	return &snap, nil
}

// GetOrgStatus computes the lightweight live status (doc 24 §5.2, doc 13 Q6).
func (s *Service) GetOrgStatus(ctx context.Context, orgID string) (*contracts.OrgStatus, error) {
	var st contracts.OrgStatus
	var health float64
	err := s.pool.QueryRow(ctx, `
		SELECT o.id, o.status,
		       COALESCE(o.health_score, 0),
		       (SELECT COUNT(*) FROM investigations i
		        WHERE i.organization_id = o.id AND i.status IN ('planning','researching','analyzing','debating','synthesizing')),
		       (SELECT COUNT(*) FROM meetings m WHERE m.organization_id = o.id AND m.status IN ('called','agenda_set','notified','preparing','conducting')),
		       (SELECT COUNT(*) FROM messages m WHERE m.organization_id = o.id)
		FROM organizations o WHERE o.id = $1`, orgID).
		Scan(&st.OrganizationID, &st.State, &health, &st.Counts.InvestigationsActive, &st.Counts.Meetings, &st.Counts.Messages)
	if err != nil {
		if err == pgx.ErrNoRows {
			return nil, ErrNotFound
		}
		return nil, err
	}
	st.Health = health

	rows, err := s.pool.Query(ctx, `
		SELECT d.name, d.id, COALESCE(w.status, 'idle'), COALESCE(w.configuration->>'activity', ''),
		       AVG(w.current_trust_score)::float8
		FROM departments d
		LEFT JOIN workers w ON w.department_id = d.id AND w.status != 'terminated'
		WHERE d.organization_id = $1 AND d.status = 'active'
		GROUP BY d.id, d.name, w.status, w.configuration`, orgID)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	for rows.Next() {
		var d contracts.DeptStatusDTO
		var state, activity string
		var trust float64
		var name string
		if err := rows.Scan(&name, &d.Name, &state, &activity, &trust); err != nil {
			return nil, err
		}
		_ = name
		d.WorkerState = state
		d.Activity = activity
		d.Trust = trust
		st.Departments = append(st.Departments, d)
	}
	return &st, nil
}

// ── helpers ────────────────────────────────────────────────────────────

func jsonMap(m map[string]interface{}) string {
	b, _ := json.Marshal(m)
	return string(b)
}

func mustJSON(v interface{}) []byte {
	b, _ := json.Marshal(v)
	return b
}

func firstNonNilList(override []string, def interface{}) []string {
	if len(override) > 0 {
		return override
	}
	if l, ok := def.([]string); ok {
		return l
	}
	if l, ok := def.([]interface{}); ok {
		out := make([]string, 0, len(l))
		for _, v := range l {
			if s, ok := v.(string); ok {
				out = append(out, s)
			}
		}
		return out
	}
	return nil
}

func firstNonNilMap(override map[string]interface{}, def interface{}) interface{} {
	if len(override) > 0 {
		return override
	}
	return def
}

// SetEventPublisher wires the NATS publisher for event fan-out.
func (s *Service) SetEventPublisher(p events.Publisher) {
	s.appender.SetPublisher(p)
}
