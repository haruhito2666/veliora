# 16. Event System

> Event-driven architecture for Veliora. The event store is the
> **immutable source of truth**; every domain table in
> `13-database-design.md` is a materialized projection rebuilt from events.
> This document specifies the event envelope, the full event taxonomy, causal
> chains, event sourcing, replay, and real-time streaming to the mobile app.

---

## 16.1 Design Principles

### The "One Agent" Test Applied to Events

A single-agent system emits lifecycle events too — `tool_called`,
`message_appended`, `token_generated`. Those exist for observability. In
Veliora, events exist for a structurally different reason: **the
organization's state is the emergent product of many workers acting in
sequence, and only the event log can reconstruct why the org is the way it
is.**

| Event category | Why it only exists because this is an organization |
|-----------------|----------------------------------------------------|
| `meeting.*` | A single agent does not convene. |
| `debate.*` | A single agent has no opponent. |
| `vote.*`, `decision.*` | A single agent does not ratify. |
| `trust.*` | Reputation is a social construct. |
| `constitution.*` | A single agent amends no charter. |
| `worker.*` (hire/fire/promote) | A single agent does not staff itself. |
| `evolution.*` | A single agent does not restructure itself. |
| `communication.*` (external) | Division of labor reaches outward. |

If an event type would fire identically in a one-agent system, it is an
observability log line, not an organizational event — it does not belong in
the event store.

### Foundational Rules

1. **Events are immutable.** Once appended, an event is never edited or
   deleted. Corrections are new compensating events.
2. **Events are the source of truth.** Domain tables are projections; they
   can always be rebuilt by replaying the event store.
3. **Every event carries a causal chain.** No organizational decision is
   spontaneous — each event links to the event that caused it, back to a root.
4. **Events are typed and versioned.** `schema_version` in the envelope
   allows evolution without breaking replay.
5. **Events are the mobile app's lifeblood.** The dashboard is a live view of
   the event stream, not a polling API. See `22-mobile-ux.md`.

---

## 16.2 Why Event Sourcing for an Organization

A traditional CRUD database answers "what is the current state?" An
organization must also answer:

- **"Why did we decide this?"** — causal chain to the originating prompt.
- **"What was our reasoning three years ago?"** — replay to a point in time.
- **"Who disagreed, and how was it resolved?"** — debate and vote events.
- **"How did our trust in Research change after the Q3 miss?"** — trust
  time-series.
- **"What was the org like before the constitution amendment?"** — replay to
  a snapshot before the amendment event.

Only an append-only event log with causal links can answer these. The
projection tables in `13-database-design.md` give fast queries; the event
store gives the truth.

```
   Event Store (immutable, partitioned)        Projections (queryable)
   ┌─────────────────────────────┐            ┌────────────────────┐
   │ events                      │  replay →  │ organizations       │
   │  (investigation.started)    │ ────────►  │ investigations      │
   │  (evidence.created)         │ ────────►  │ evidence            │
   │  (debate.challenged)        │ ────────►  │ debates             │
   │  (vote.cast)                │ ────────►  │ votes               │
   │  (decision.ratified)        │ ────────►  │ decisions           │
   │  (trust.updated)            │ ────────►  │ trust_records        │
   │  (constitution.amended)     │ ────────►  │ constitutions        │
   │  ...                        │            │ ...                 │
   └─────────────────────────────┘            └────────────────────┘
        ▲ append-only                  ▲ rebuilt by projection consumers
        │                                  │
   workers + engines                 projection workers (subscribers)
```

---

## 16.3 Event Envelope Schema

Every event in the store conforms to one envelope. The envelope is stored
as columns on the `events` table (see `13-database-design.md` §13.3.11) and
serialized as JSON for the wire.

```json
{
  "event_id":         "01921a4f-7c3b-7d2e-9a01-4f3b2c1d0e5a",
  "event_type":       "investigation.phase_changed",
  "event_category":   "investigation",
  "organization_id":  "01921a3e-1b2c-7d1e-8a00-aa11bb22cc33",
  "timestamp":        "2026-08-05T16:53:10.777Z",
  "source": {
    "component":      "investigation_engine",
    "worker_id":       "01921a40-...-...",
    "department_id":   "01921a41-...-..."
  },
  "payload": {
    "investigation_id": "01921a42-...-...",
    "from_phase":       "planning",
    "to_phase":         "investigating"
  },
  "causal_chain": {
    "parent_event_id":  "01921a3f-...-...",
    "correlation_id":   "01921a3d-...-...",
    "root_event_id":    "01921a3c-...-...",
    "depth":            3
  },
  "metadata": {
    "schema_version":   "1.0",
    "recorded_at":      "2026-08-05T16:53:10.780Z",
    "sequence_number":  48217
  }
}
```

| Field | Type | Description |
|-------|------|-------------|
| `event_id` | UUID v7 | Globally unique, time-ordered. Idempotency key. |
| `event_type` | string | `<category>.<verb>` from the taxonomy in §16.4. |
| `event_category` | string | One of the 10 categories. |
| `organization_id` | UUID | Tenant scope; partitions all access. |
| `timestamp` | ISO8601 | When the event logically occurred. |
| `source.component` | string | Engine/worker that emitted it. |
| `source.worker_id` | UUID\|null | The worker, if a worker acted. |
| `source.department_id` | UUID\|null | The department, if department-scoped. |
| `payload` | object | Type-specific; see §16.4. |
| `causal_chain.parent_event_id` | UUID\|null | The immediate cause. |
| `causal_chain.correlation_id` | UUID | Groups all events in one cascade. |
| `causal_chain.root_event_id` | UUID | The originating event of the cascade. |
| `causal_chain.depth` | int | Distance from root; 0 = root. |
| `metadata.schema_version` | string | Envelope + payload schema version. |
| `metadata.recorded_at` | ISO8601 | When the store durably appended it. |
| `metadata.sequence_number` | bigint | Per-org monotonic; enables ordering + resume. |

### Causal Chain Semantics

- **Root event** (`depth = 0`): the user prompt or an autonomous trigger.
  `root_event_id == event_id`, `parent_event_id == null`.
- **Child event**: `parent_event_id` points to the event that directly caused
  it; `root_event_id` is propagated unchanged; `depth = parent.depth + 1`.
- **Correlation ID**: shared by every event in a single cascade (e.g., one
  investigation from prompt to final decision). Enables "show me everything
  that happened in this investigation" with one index lookup.

This is what makes organizational reasoning **reconstructable**: the chain
answers "why?" at every step. A single-agent system has no causal chain
because there is no second party to cause or be caused.

---

## 16.4 Event Taxonomy

Ten categories. Each type lists its payload shape. Types marked **MVP** are in
the hackathon scope (see `28-mvp-hackathon.md`).

### 16.4.1 Organization Events

| Event type | Payload | Notes |
|------------|--------|------|
| `organization.created` | `{name, template, initial_dna, initial_constitution}` | Root of the org's event log. |
| `organization.evolved` | `{evolution_event_id, summary}` | Fired after an `evolution.*` executes. |
| `organization.dissolved` | `{reason, final_state_ref}` | Terminal; no further events. |

### 16.4.2 Worker Events

| Event type | Payload | Notes |
|------------|--------|------|
| `worker.hired` | `{worker_id, role, department_id, configuration}` | MVP |
| `worker.fired` | `{worker_id, reason, triggered_by_vote_id}` | |
| `worker.promoted` | `{worker_id, from_role, to_role, new_department_id}` | |
| `worker.status_changed` | `{worker_id, from_status, to_status, activity}` | MVP; drives the live dashboard "CEO: Working / Reading papers". |

### 16.4.3 Investigation Events

| Event type | Payload | Notes |
|------------|--------|------|
| `investigation.started` | `{investigation_id, title, prompt, plan}` | MVP |
| `investigation.phase_changed` | `{investigation_id, from_phase, to_phase}` | MVP |
| `investigation.completed` | `{investigation_id, confidence_score, recommendation}` | MVP |

### 16.4.4 Meeting Events

| Event type | Payload | Notes |
|------------|--------|------|
| `meeting.called` | `{meeting_id, investigation_id, agenda, participants[]}` | MVP |
| `meeting.started` | `{meeting_id, quorum_met}` | MVP |
| `meeting.ended` | `{meeting_id, summary, decision_refs[]}` | MVP |

### 16.4.5 Debate Events

| Event type | Payload | Notes |
|------------|--------|------|
| `debate.opened` | `{debate_id, investigation_id, topic}` | MVP |
| `debate.challenged` | `{debate_id, evidence_id, challenger_worker_id, challenge_text}` | MVP; the Evidence Challenge mechanic. |
| `debate.concluded` | `{debate_id, outcome, retained_evidence_ids[]}` | MVP |

### 16.4.6 Communication Events

| Event type | Payload | Notes |
|------------|--------|------|
| `communication.sent` | `{log_id, channel, contact_id, subject, caspian_message_id}` | MVP; outbound via Caspian. |
| `communication.received` | `{log_id, channel, contact_id, caspian_message_id, content_ref}` | MVP; inbound from Caspian. |
| `communication.replied` | `{log_id, in_reply_to, caspian_message_id}` | MVP |

### 16.4.7 Trust Events

| Event type | Payload | Notes |
|------------|--------|------|
| `trust.updated` | `{worker_id, evaluator_worker_id, old_score, new_score, context, delta}` | |
| `trust.threshold_crossed` | `{worker_id, threshold, direction, new_role_eligibility}` | Triggers promotion/vote-weight changes. |

### 16.4.8 Constitution Events

| Event type | Payload | Notes |
|------------|--------|------|
| `constitution.amended` | `{old_version, new_version, change_diff, ratified_by_vote_id}` | Produces a new `constitutions` row; see `09-constitution-engine.md`. |

### 16.4.9 Memory Events

| Event type | Payload | Notes |
|------------|--------|------|
| `memory.created` | `{memory_id, scope, memory_type, summary, importance}` | |
| `memory.reinforced` | `{memory_id, reinforced_by_event_id, new_decay_score}` | |
| `memory.decayed` | `{memory_id, new_decay_score, pruned_from_recall}` | Logical decay; row retained for audit. |

### 16.4.10 Evolution Events

| Event type | Payload | Notes |
|------------|--------|------|
| `evolution.proposed` | `{evolution_event_id, event_type, target, rationale}` | |
| `evolution.approved` | `{evolution_event_id, vote_id}` | |
| `evolution.executed` | `{evolution_event_id, before_state, after_state}` | |
| `evolution.rolled_back` | `{evolution_event_id, reason}` | |

---

## 16.5 Event Flow Diagrams

### 16.5.1 Investigation Cascade (flagship flow)

```
User prompt (external trigger — not a stored event)
    │
    ▼
investigation.started                 (root, depth=0, correlation_id=C1)
    │                                   payload carries the user's prompt
    ├──► worker.status_changed         (depth=1)  "Research: assigned"
    ├──► worker.status_changed         (depth=1)  "Marketing: assigned"
    │
    ▼
investigation.phase_changed           (depth=1)  planning → investigating
    │
    ├──► communication.sent           (depth=2)  Research → Email → Professor
    │       │
    │       ▼
    │   communication.received        (depth=3)  Professor replies
    │       │
    │       ▼
    │   communication.replied         (depth=3)  Research follows up
    │       │
    │       ▼
    │   evidence.created              (depth=3)  Research submits evidence
    │       │
    │       ▼
    │   debate.opened                 (depth=4)  Engineering challenges sample size
    │       │
    │       ▼
    │   debate.challenged              (depth=4)
    │       │
    │       ▼
    │   debate.concluded              (depth=5)  evidence retained/rejected
    │       │
    │       ▼
    │   trust.updated                 (depth=5)  Research reputation adjusts
    │
    ├──► meeting.called               (depth=2)  CEO convenes board
    │       │
    │       ▼
    │   meeting.started              (depth=3)
    │       │
    │       ▼
    │   meeting.ended                (depth=3)  summary produced
    │
    ▼
investigation.phase_changed          (depth=1)  investigating → voting
    │
    ▼
vote.cast (×N)                        (depth=2)  weighted by trust
    │
    ▼
decision.ratified                     (depth=2)
    │
    ▼
investigation.completed               (depth=1)  recommendation + confidence
    │
    ▼
memory.created (×N)                   (depth=2)  institutional memory written
```

All events in this cascade share `correlation_id = C1` and
`root_event_id = investigation.started`. The mobile app subscribes to `C1`
and renders the entire investigation live.

### 16.5.2 Evolution Cascade (self-modification)

```
investigation.completed               (root of this cascade)
    │  conclusion: "Engineering overloaded; recommend hiring"
    ▼
evolution.proposed                     (depth=1)  hire new engineer
    │
    ▼
meeting.called → meeting.ended         (depth=2)  debate the proposal
    │
    ▼
vote.cast (×N)                         (depth=2)
    │
    ▼
evolution.approved                     (depth=2)
    │
    ▼
evolution.executed                     (depth=2)  worker.hired fires
    │
    ▼
worker.hired                           (depth=3)
    │
    ▼
organization.evolved                   (depth=3)  org state changed
```

### 16.5.3 Constitution Amendment Cascade

```
investigation.completed                (root)
    │  conclusion: "decision_rules.voting_quorum too low; bad decisions slip"
    ▼
constitution.amendment_proposed        (depth=1)  JSON Patch diff
    │
    ▼
vote.cast (×N)                         (depth=2)  threshold 0.75
    │
    ▼
constitution.amended                   (depth=2)  new version row
    │
    ▼
organization.evolved                   (depth=2)  governance changed
```

---

## 16.6 Event Store & Projections

### 16.6.1 Storage

The event store is the `events` table in `13-database-design.md` §13.3.11,
range-partitioned by `occurred_at`. The per-org `sequence_number` (BIGINT,
monotonic) is the canonical ordering key; `occurred_at` is the partition key.

### 16.6.2 Append Path

```
Engine/Worker
    │  emit(event)
    ▼
Event Bus (MVP: Postgres LISTEN/NOTIFY + INSERT;
           Scale: NATS JetStream / Kafka)
    │
    ├─► INSERT INTO events ...  (durable, returns sequence_number)
    │
    ├─► NOTIFY event_stream     (fan-out to projection consumers)
    │
    └─► NOTIFY ws_fanout         (fan-out to WebSocket subscribers)
```

Append is transactional with the emitting engine's work: the engine writes
its domain rows and the event in the same transaction, so projections and the
store never diverge. (For high-throughput scale, the engine writes only the
event and projections own all domain rows — see `27-scaling-strategy.md`.)

### 16.6.3 Projection Consumers

Each projection is an idempotent subscriber that reads new events (by
`sequence_number` watermark) and upserts into a domain table.

```
┌──────────────┐   sequence watermark   ┌─────────────────────┐
│  events      │ ─────────────────────► │ InvestigationProjector│ → investigations
│  (partitioned)│                       │ TrustProjector       │ → trust_records
└──────────────┘                        │ MemoryProjector      │ → memories
                                        │ EvolutionProjector   │ → evolution_events
                                        │ CommunicationProjector → communication_logs
                                        └─────────────────────┘
```

Each projector maintains its own `projection_watermarks` row:

```sql
CREATE TABLE projection_watermarks (
    projector_name   TEXT PRIMARY KEY,
    organization_id   UUID,
    last_sequence      BIGINT NOT NULL,
    last_event_id      UUID NOT NULL,
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT now()
);
```

Idempotency: projectors key all upserts on `event_id` and replay-safe
`ON CONFLICT DO NOTHING`. Re-running a projector from sequence 0 rebuilds the
projection exactly.

---

## 16.7 Event Replay & Snapshots

### 16.7.1 Replay for Debugging

To answer "what was the org's state at time T?":

```sql
-- 1. Find the root investigation event
SELECT id, correlation_id FROM events
WHERE organization_id = $1 AND event_type = 'investigation.started'
  AND payload->>'title' = 'Should I build an AI healthcare startup?';

-- 2. Replay all events in that correlation up to T
SELECT event_type, payload, occurred_at, chain_depth
FROM events
WHERE correlation_id = $2 AND occurred_at <= $3
ORDER BY sequence_number;
```

A replay tool re-applies events to an in-memory projection, reproducing the
exact organizational state at T. This is how the org audits its own past.

### 16.7.2 Snapshots

Replaying from event 0 is expensive at scale. Snapshots are periodic
materialized projections frozen at a `sequence_number`:

```sql
CREATE TABLE projection_snapshots (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id   UUID NOT NULL,
    projector_name    TEXT NOT NULL,
    sequence_number   BIGINT NOT NULL,
    state             JSONB NOT NULL,        -- serialized projection
    taken_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX snap_org_proj_idx ON projection_snapshots(organization_id, projector_name, sequence_number);
```

Replay loads the latest snapshot below the target sequence, then applies
only events after it. Snapshots are taken every N events (configurable per
projector; default 10,000) or at investigation boundaries.

### 16.7.3 Replay for Audit

The full event log is the audit trail. Compliance queries ("show every
decision and its causal chain for the last year") are recursive CTEs over
`events` — see Q5 in `13-database-design.md` §13.7. Events are **never
deleted**; cold partitions are archived to S3 Parquet, not destroyed.

---

## 16.8 Real-Time Streaming to Mobile

The mobile app is a live view of the event stream — see `22-mobile-ux.md`.
It does not poll; it subscribes.

### 16.8.1 WebSocket Connection

```
Mobile App                       API Gateway (WS)              Event Bus
    │                                 │                            │
    │── WS connect (org_id, token) ──►│                            │
    │                                 │── authz + register ────────►│
    │                                 │                            │
    │── subscribe(filters) ─────────►│                            │
    │                                 │── LISTEN ws_fanout ────────►│
    │                                 │                            │
    │                                 │◄── event appended ──────────│
    │◄── event (filtered) ────────────│                            │
    │                                 │                            │
    │── ack(last_sequence) ─────────►│                            │
```

### 16.8.2 Subscription Protocol

Client → server `subscribe`:

```json
{
  "type": "subscribe",
  "filters": {
    "organization_id": "01921a3e-...",
    "categories": ["investigation", "meeting", "debate", "worker"],
    "correlation_id": null,
    "worker_id": null
  },
  "since_sequence": 48200
}
```

Server → client `event`:

```json
{
  "type": "event",
  "event": { /* full envelope from §16.3 */ }
}
```

Client → server `ack` (flow control / resume):

```json
{ "type": "ack", "last_sequence": 48217 }
```

### 16.8.3 Resume & Backpressure

- On reconnect, the client sends `since_sequence` (its last acked
  `sequence_number`). The server replays all events with
  `sequence_number > since_sequence` for the org, then switches to live.
- If the gap exceeds the hot retention window, the server sends a
  `resync_required` message and the client fetches a snapshot via REST, then
  resumes from the snapshot's sequence.
- The client must `ack` every N events (default 50) so the server can
  release its send buffer. Without acks within a window, the server pauses
  (backpressure) rather than dropping events.

### 16.8.4 Fan-out Topology

```
                        ┌─────────────────────┐
   events (INSERT) ────►│  ws_fanout NOTIFY    │
                        └──────────┬──────────┘
                                   │
                ┌──────────────────┼──────────────────┐
                │                  │                  │
        ┌───────▼──────┐  ┌────────▼──────┐  ┌────────▼──────┐
        │ WS Gateway 1 │  │ WS Gateway 2   │  │ WS Gateway N   │
        │ (org A,B)    │  │ (org C,D)      │  │ (org E,F)      │
        └──────┬───────┘  └───────┬───────┘  └───────┬───────┘
               │                  │                  │
          mobile clients     mobile clients     mobile clients
```

Gateways are sharded by `organization_id` so a single org's events route to
one gateway, preserving per-org ordering. Cross-org ordering is not
guaranteed and not needed. See `27-scaling-strategy.md`.

---

## 16.9 Ordering, Idempotency & Schema Evolution

### Ordering

- **Per-org total order** via `sequence_number` (BIGINT, gap-less, assigned
  by the store on commit). This is the only ordering guarantee the
  organization needs.
- **No global order** across orgs. Organizations are independent — see
  contribution #10 in `SPEC.md`.

### Idempotency

- `event_id` (UUID v7) is the dedup key. Producers may retry appends with the
  same `event_id`; the store's `UNIQUE(organization_id, sequence_number)`
  and projector `ON CONFLICT DO NOTHING` make retries safe.
- Caspian inbound messages dedup on `caspian_message_id` before becoming
  `communication.received` events — see `12-caspian-integration.md`.

### Schema Evolution

- `metadata.schema_version` is per envelope. A bump lets projectors branch
  on version during replay.
- Payload fields are **additive only** within a version. Removed fields
  become `null`, never omitted, to keep replay deterministic.
- Breaking changes require a new `schema_version` and a projector that
  handles both old and new shapes. Old partitions keep their version
  forever; new partitions use the new version.

---

## 16.10 Event Bus: MVP vs Scale

| Stage | Bus | Rationale |
|-------|-----|-----------|
| MVP (hackathon) | PostgreSQL `LISTEN`/`NOTIFY` + `INSERT` | Zero new infra; one DB does everything. |
| Early scale | NATS JetStream | Per-org streams, durable, lightweight. |
| Large scale | Kafka, partitioned by `organization_id` | High throughput, long retention, replay. |

The envelope and projector contracts are identical across all three stages;
only the transport changes. This keeps `28-mvp-hackathon.md` buildable in a
weekend and `27-scaling-strategy.md` reachable without a rewrite.

---

## 16.11 Cross-References

- `13-database-design.md` — `events` table schema, partitioning, projection tables.
- `04-technical-architecture.md` — service boundaries for engines that emit events.
- `19-investigation-engine.md` — primary producer of the investigation cascade.
- `10-debate-engine.md` — `debate.*` event emission and Evidence Challenge.
- `20-meeting-engine.md` — `meeting.*` event emission.
- `08-trust-engine.md` — `trust.*` event computation.
- `09-constitution-engine.md` — `constitution.amended` and versioning.
- `07-evolution-engine.md` — `evolution.*` events and rollback.
- `06-memory-system.md` — `memory.*` events, decay, and reinforcement.
- `12-caspian-integration.md` — `communication.*` events and dedup.
- `22-mobile-ux.md` — WebSocket consumer of the event stream.
- `24-api-design.md` — REST + WebSocket surface for subscriptions.
- `25-state-machine.md` — state transitions expressed as event sequences.
- `27-scaling-strategy.md` — event bus transport evolution and sharding.
