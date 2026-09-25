package events

import (
	"context"
	"encoding/json"
	"fmt"
	"time"

	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgxpool"
)

// Publisher publishes appended envelopes onto the NATS org stream. It is
// set by the service wiring (bus.EnsureOrgStream + PublishEnvelope); when
// nil, appends are DB-only (tests, engines without the bus).
type Publisher interface {
	EnsureOrgStream(orgID string) error
	PublishEnvelope(subject string, ev interface{}) error
}

// Appender persists envelopes to the event store with per-org monotonic
// sequence numbers, atomically with any domain-row writes the caller
// supplies (doc 16 §16.6.2: engine writes domain rows + event in one tx).
type Appender struct {
	pool      *pgxpool.Pool
	publisher Publisher
}

// NewAppender returns an Appender bound to the given pool.
func NewAppender(pool *pgxpool.Pool) *Appender {
	return &Appender{pool: pool}
}

// SetPublisher wires the NATS publisher for event fan-out.
func (a *Appender) SetPublisher(p Publisher) {
	a.publisher = p
}

// Append inserts the envelope and returns the assigned sequence number.
// payload is marshaled to JSONB; org sequences are bumped atomically.
func (a *Appender) Append(ctx context.Context, ev Envelope) (int64, error) {
	return a.AppendTx(ctx, nil, ev)
}

// AppendTx appends the event inside the given transaction (may be nil,
// in which case a fresh tx is opened). Returns the assigned sequence.
func (a *Appender) AppendTx(ctx context.Context, tx pgx.Tx, ev Envelope) (int64, error) {
	var err error
	ownsTx := tx == nil
	if ownsTx {
		tx, err = a.pool.Begin(ctx)
		if err != nil {
			return 0, fmt.Errorf("begin: %w", err)
		}
		defer func() { _ = tx.Rollback(ctx) }()
	}

	payloadJSON, err := json.Marshal(ev.Payload)
	if err != nil {
		return 0, fmt.Errorf("marshal payload: %w", err)
	}
	sourceJSON, err := json.Marshal(ev.Source)
	if err != nil {
		return 0, fmt.Errorf("marshal source: %w", err)
	}

	// Insert the event first; if this event_id already exists (idempotent
	// retry), return its existing sequence without touching org_sequences.
	var existingSeq int64
	err = tx.QueryRow(ctx, `
		SELECT sequence_number FROM events WHERE id = $1`, ev.EventID).Scan(&existingSeq)
	if err == nil {
		// Already appended; return the original sequence (no bump).
		return existingSeq, nil
	}
	if err != pgx.ErrNoRows {
		return 0, fmt.Errorf("check existing event: %w", err)
	}

	// Claim the next per-org sequence number.
	var seq int64
	err = tx.QueryRow(ctx, `
		INSERT INTO org_sequences (organization_id, last)
		VALUES ($1, 1)
		ON CONFLICT (organization_id)
		DO UPDATE SET last = org_sequences.last + 1
		RETURNING last`, ev.OrganizationID).Scan(&seq)
	if err != nil {
		return 0, fmt.Errorf("claim sequence: %w", err)
	}
	ev.Metadata.SequenceNumber = seq

	_, err = tx.Exec(ctx, `
		INSERT INTO events (id, organization_id, event_type, event_category,
			source, payload, causal_parent_id, correlation_id, root_event_id,
			chain_depth, sequence_number, occurred_at, recorded_at, schema_version)
		VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11,$12,$13,$14)`,
		ev.EventID, ev.OrganizationID, ev.EventType, ev.EventCategory,
		sourceJSON, payloadJSON,
		nullIfEmpty(ev.CausalChain.ParentEventID), ev.CausalChain.CorrelationID,
		ev.CausalChain.RootEventID, ev.CausalChain.Depth, seq,
		ev.Timestamp, ev.Metadata.RecordedAt, ev.Metadata.SchemaVersion)
	if err != nil {
		return 0, fmt.Errorf("insert event: %w", err)
	}

	if ownsTx {
		if err := tx.Commit(ctx); err != nil {
			return 0, fmt.Errorf("commit: %w", err)
		}
	}

	// Fan out to the org's JetStream stream (best-effort; DB is source of truth).
	if a.publisher != nil {
		subject := fmt.Sprintf("org.%s.%s.%s", ev.OrganizationID,
			ev.EventCategory, eventVerb(ev.EventType))
		_ = a.publisher.EnsureOrgStream(ev.OrganizationID)
		_ = a.publisher.PublishEnvelope(subject, ev)
	}
	return seq, nil
}

// eventVerb derives the trailing verb token from an event type
// ("investigation.phase_changed" → "phase_changed") for the NATS subject.
func eventVerb(eventType string) string {
	for i := len(eventType) - 1; i >= 0; i-- {
		if eventType[i] == '.' {
			return eventType[i+1:]
		}
	}
	return eventType
}

// EventsAfter returns events for an org with sequence > after, ascending.
func (a *Appender) EventsAfter(ctx context.Context, orgID string, after int64, limit int) ([]Envelope, error) {
	rows, err := a.pool.Query(ctx, `
		SELECT id, event_type, event_category, organization_id,
		       source, payload, causal_parent_id, correlation_id, root_event_id,
		       chain_depth, sequence_number, occurred_at, recorded_at, schema_version
		FROM events
		WHERE organization_id = $1 AND sequence_number > $2
		ORDER BY sequence_number
		LIMIT $3`, orgID, after, limit)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	return scanRows(rows)
}

func scanRows(rows pgx.Rows) ([]Envelope, error) {
	var out []Envelope
	for rows.Next() {
		var e Envelope
		var sourceJSON, payloadJSON []byte
		var parentID, correlationID, rootID *string
		var seq int64
		var occurredAt, recordedAt interface{}
		var schemaVersion string
		var id, evType, category, orgID string
		var depth int
		if err := rows.Scan(&id, &evType, &category, &orgID,
			&sourceJSON, &payloadJSON, &parentID, &correlationID, &rootID,
			&depth, &seq, &occurredAt, &recordedAt, &schemaVersion); err != nil {
			return nil, err
		}
		// pgx returns time.Time for timestamptz; decode via JSON round-trip below.
		var source Source
		_ = json.Unmarshal(sourceJSON, &source)
		var payload interface{}
		_ = json.Unmarshal(payloadJSON, &payload)
		e = Envelope{
			EventID: id, EventType: evType, EventCategory: category, OrganizationID: orgID,
			Source: source, Payload: payload,
			CausalChain: CausalChain{
				ParentEventID: deref(parentID), CorrelationID: deref(correlationID),
				RootEventID: deref(rootID), Depth: depth,
			},
			Metadata: Metadata{SchemaVersion: schemaVersion, SequenceNumber: seq},
		}
		// Normalize timestamps through JSON for consistency.
		if b, err := json.Marshal(map[string]interface{}{"ts": occurredAt, "rec": recordedAt}); err == nil {
			var t struct {
				TS  timeVal `json:"ts"`
				Rec timeVal `json:"rec"`
			}
			if json.Unmarshal(b, &t) == nil {
				e.Timestamp = t.TS.Time
				e.Metadata.RecordedAt = t.Rec.Time
			}
		}
		out = append(out, e)
	}
	return out, rows.Err()
}

type timeVal struct{ time.Time }

func (t *timeVal) UnmarshalJSON(b []byte) error {
	var s string
	if err := json.Unmarshal(b, &s); err != nil {
		return err
	}
	tt, err := parseTime(s)
	if err != nil {
		return err
	}
	t.Time = tt
	return nil
}

func nullIfEmpty(s string) interface{} {
	if s == "" {
		return nil
	}
	return s
}

func deref(s *string) string {
	if s == nil {
		return ""
	}
	return *s
}
