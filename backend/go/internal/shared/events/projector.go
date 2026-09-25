package events

import (
	"context"
	"fmt"

	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgxpool"
)

// Projector is the base for idempotent event-stream projections
// (doc 16 §16.6.3). It tracks a watermark per projector name and
// applies each event via Apply with an ON CONFLICT DO NOTHING upsert.
type Projector struct {
	pool    *pgxpool.Pool
	name    string
	orgID   string // empty = all orgs (single watermark)
	handler func(ctx context.Context, tx pgx.Tx, ev Envelope) error
}

// NewProjector builds a projector. handler is called per event inside a tx.
func NewProjector(pool *pgxpool.Pool, name, orgID string, handler func(ctx context.Context, tx pgx.Tx, ev Envelope) error) *Projector {
	return &Projector{pool: pool, name: name, orgID: orgID, handler: handler}
}

// Run advances the projector from its watermark until the event stream
// is exhausted, processing events in ascending sequence order.
func (p *Projector) Run(ctx context.Context) (int, error) {
	from := p.watermark(ctx)
	processed := 0
	for {
		evs, err := p.fetch(ctx, from, 100)
		if err != nil {
			return processed, err
		}
		if len(evs) == 0 {
			return processed, nil
		}
		for _, ev := range evs {
			tx, err := p.pool.Begin(ctx)
			if err != nil {
				return processed, err
			}
			if err := p.handler(ctx, tx, ev); err != nil {
				_ = tx.Rollback(ctx)
				return processed, fmt.Errorf("projector %s: %w", p.name, err)
			}
			if err := p.advanceWatermark(ctx, tx, ev); err != nil {
				_ = tx.Rollback(ctx)
				return processed, err
			}
			if err := tx.Commit(ctx); err != nil {
				return processed, err
			}
			from = ev.Metadata.SequenceNumber
			processed++
		}
	}
}

func (p *Projector) watermark(ctx context.Context) int64 {
	var last int64
	err := p.pool.QueryRow(ctx,
		`SELECT last_sequence FROM projection_watermarks WHERE projector_name = $1`,
		p.name).Scan(&last)
	if err != nil {
		return 0
	}
	return last
}

func (p *Projector) fetch(ctx context.Context, after int64, limit int) ([]Envelope, error) {
	var rows pgx.Rows
	var err error
	if p.orgID == "" {
		rows, err = p.pool.Query(ctx, `
			SELECT id, event_type, event_category, organization_id,
			       source, payload, causal_parent_id, correlation_id, root_event_id,
			       chain_depth, sequence_number, occurred_at, recorded_at, schema_version
			FROM events
			WHERE sequence_number > $1
			ORDER BY sequence_number LIMIT $2`, after, limit)
	} else {
		rows, err = p.pool.Query(ctx, `
			SELECT id, event_type, event_category, organization_id,
			       source, payload, causal_parent_id, correlation_id, root_event_id,
			       chain_depth, sequence_number, occurred_at, recorded_at, schema_version
			FROM events
			WHERE organization_id = $1 AND sequence_number > $2
			ORDER BY sequence_number LIMIT $3`, p.orgID, after, limit)
	}
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	return scanRows(rows)
}

func (p *Projector) advanceWatermark(ctx context.Context, tx pgx.Tx, ev Envelope) error {
	_, err := tx.Exec(ctx, `
		INSERT INTO projection_watermarks (projector_name, organization_id, last_sequence, last_event_id, updated_at)
		VALUES ($1, $2, $3, $4, now())
		ON CONFLICT (projector_name)
		DO UPDATE SET last_sequence = EXCLUDED.last_sequence,
		              last_event_id = EXCLUDED.last_event_id,
		              updated_at = now()`,
		p.name, p.orgID, ev.Metadata.SequenceNumber, ev.EventID)
	return err
}
