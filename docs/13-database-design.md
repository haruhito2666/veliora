# 13. Database Design

> Complete data model and persistence layer for Veliora.
> Primary database: **PostgreSQL 16+**. The schema is designed around the
> organizational paradigm — every table exists because this is an
> **organization**, not a single AI. See `16-event-system.md` for the event
> store that is the immutable source of truth; the tables here are
> **materialized projections** rebuilt from that store.

---

## 13.1 Design Principles

### The "One Agent" Test Applied to Persistence

A single-agent system needs: a conversation log, a memory store, maybe a
tool-call history. None of the following tables would exist in that world:

| Table | Why it only exists because this is an organization |
|-------|----------------------------------------------------|
| `departments` | A single agent has no peers to partition work among. |
| `meetings`, `messages` (multi-party) | A single agent does not convene. |
| `votes`, `decisions` | A single agent does not ratify. |
| `evidence_challenges` | A single agent has no peer reviewer. |
| `trust_records` | Reputation is meaningless without peers. |
| `constitutions` | A single agent needs no governing charter. |
| `evolution_events` | A single agent does not hire/fire/restructure itself. |
| `external_contacts`, `communication_logs` | A single agent has no division of labor reaching outward. |

If a proposed table passes none of these tests, it belongs in a per-worker
store, not the organizational database.

### Foundational Conventions

1. **UUID v7 primary keys** everywhere — time-ordered, sortable, collision-free
   across distributed workers, and natural for event sourcing.
2. **`TIMESTAMPTZ`** for all timestamps; never `TIMESTAMP`. The org operates
   across Caspian channels that span timezones.
3. **Soft state, never hard deletes.** Rows carry `status` / `deleted_at`.
   The organization must audit its own history — see `06-memory-system.md`.
4. **JSONB for semi-structured, schema-versioned content** (DNA parameters,
   constitution, memory content, event payloads). Strict columns for
   queryable, indexed fields; JSONB for evolving structure.
5. **Event sourcing as the source of truth.** Every state change is an event
   in the `events` table; the domain tables are projections. This makes
   organizational replay, audit, and debugging possible — see
   `16-event-system.md`.
6. **Partitioning for time-series tables** (`events`, `trust_records`,
   `communication_logs`, `worker_activity_logs`, `memory_reinforcements`).
7. **`pgvector` for semantic memory recall** — institutional memory is
   retrieved by meaning, not just keyword.

### PostgreSQL Feature Usage

| Feature | Purpose |
|---------|---------|
| Declarative range partitioning | Time-series tables |
| `JSONB` + `GIN` indexes | Flexible content + fast containment queries |
| `gen_random_uuid()` | Default UUID generation |
| `GENERATED ALWAYS` columns | Derived/computed fields |
| `LISTEN`/`NOTIFY` | MVP real-time fan-out to projections |
| Materialized views | Aggregated dashboards (org health, trust summaries) |
| `pgvector` | Embedding-based memory search |
| Logical replication | Scale-out read replicas for mobile API |
| Row-level security (RLS) | Multi-tenant isolation between organizations |

---

## 13.2 Entity-Relationship Diagram

```
                           ┌────────────────────┐
                           │   organizations     │
                           │────────────────────│
                           │ id (PK)            │◄──┐
                           │ current_dna_id     │   │ 1
                           │ current_const_id   │   │
                           └─────────┬──────────┘   │
                                     │ 1            │
            ┌────────────────┬───────┴────────┬─────────────┐
            │ N              │ N              │ N           │ N
     ┌──────▼──────┐  ┌──────▼──────┐  ┌──────▼──────┐ ┌────▼──────────┐
     │ departments │  │  workers    │  │constitutions│ │ dna_profiles  │
     │─────────────│  │─────────────│  │─────────────│ │───────────────│
     │ id (PK)     │  │ id (PK)     │  │ id (PK)     │ │ id (PK)       │
     │ org_id (FK) │  │ org_id (FK) │  │ org_id (FK) │ │ org_id (FK)   │
     │ parent_dept │  │ dept_id(FK) │  │ version     │ │ version       │
     └──────┬──────┘  └──────┬──────┘  │ is_current  │ │ is_current    │
            │ 1              │ 1       └─────────────┘ └───────────────┘
            │                │
            │          ┌─────┴──────────┐
            │          │ trust_records   │ (partitioned, time-series)
            │          └────────────────┘
            │
     ┌──────▼──────────────┐
     │  investigations      │
     │─────────────────────│
     │ id (PK)             │◄──┐
     │ org_id (FK)         │   │ 1
     └──┬───────┬───────┬──┘   │
        │ N     │ N     │ N    │
   ┌────▼─┐┌───▼───┐┌───▼────┐│┌──────────────┐
   │tasks ││meetings││evidence│││  decisions    │
   └──────┘└───┬───┘└───┬───┘│└──────┬────────┘
                │ N       │ N         │ N
           ┌────▼────┐ ┌───▼──────────┐│
           │messages │ │evidence_     ││
           │(threaded)│ │challenges   ││
           └─────────┘ └──────────────┘│
                                       │
                              ┌────────▼────────┐
                              │     votes        │
                              └──────────────────┘

  Cross-cutting (org-scoped):
  ┌──────────────┐  ┌──────────────────┐  ┌────────────────────┐
  │  memories    │  │ evolution_events  │  │ external_contacts   │
  └──────────────┘  └──────────────────┘  └─────────┬──────────┘
                                                   │ N
                                            ┌──────▼───────────┐
                                            │communication_logs│ (partitioned)
                                            └──────────────────┘

  Source of truth (partitioned by occurred_at):
  ┌──────────────────────────────────────────┐
  │                 events                    │  →  all domain tables
  │───────────────────────────────────────────│     are projections
  │ id, org_id, event_type, source, payload,  │
  │ causal_parent_id, correlation_id,        │
  │ occurred_at, sequence_number              │
  └──────────────────────────────────────────┘
```

---

## 13.3 Core Schemas

### 13.3.1 Organizational Structure

```sql
CREATE TABLE organizations (
    id                       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name                     TEXT NOT NULL,
    template                 TEXT NOT NULL,   -- 'startup','research_lab',...
    status                   TEXT NOT NULL DEFAULT 'forming',
                             -- 'forming','active','evolving','dissolved'
    current_dna_profile_id   UUID,            -- FK added after dna_profiles
    current_constitution_id  UUID,            -- FK added after constitutions
    health_score             NUMERIC(5,2) DEFAULT 0,  -- 0-100, denormalized
    metadata                 JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at               TIMESTAMPTZ NOT NULL DEFAULT now(),
    dissolved_at             TIMESTAMPTZ,
    CHECK (status IN ('forming','active','evolving','dissolved'))
);
CREATE INDEX org_status_idx ON organizations(status) WHERE status = 'active';

CREATE TABLE departments (
    id                       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id          UUID NOT NULL REFERENCES organizations(id),
    name                     TEXT NOT NULL,
    charter                  JSONB NOT NULL DEFAULT '{}'::jsonb,
                             -- {purpose, responsibilities, authority}
    parent_department_id     UUID REFERENCES departments(id),
    status                   TEXT NOT NULL DEFAULT 'active',
                             -- 'active','split','merged','dissolved'
    created_at               TIMESTAMPTZ NOT NULL DEFAULT now(),
    dissolved_at            TIMESTAMPTZ,
    CHECK (status IN ('active','split','merged','dissolved'))
);
CREATE INDEX dept_org_idx        ON departments(organization_id);
CREATE INDEX dept_parent_idx     ON departments(parent_department_id);

CREATE TABLE workers (
    id                       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id          UUID NOT NULL REFERENCES organizations(id),
    department_id            UUID REFERENCES departments(id),
    role                     TEXT NOT NULL,   -- 'CEO','Research',...
    status                   TEXT NOT NULL DEFAULT 'active',
                             -- 'active','on_assignment','on_leave',
                             -- 'promoted','terminated'
    configuration            JSONB NOT NULL DEFAULT '{}'::jsonb,
                             -- {model, persona, specialties, tools}
    current_trust_score      NUMERIC(5,2) DEFAULT 50.0,  -- denormalized
    hired_at                 TIMESTAMPTZ NOT NULL DEFAULT now(),
    terminated_at            TIMESTAMPTZ
);
CREATE INDEX worker_org_dept_idx ON workers(organization_id, department_id);
CREATE INDEX worker_status_idx    ON workers(organization_id, status);
```

### 13.3.2 Investigation & Reasoning

```sql
CREATE TABLE investigations (
    id                       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id          UUID NOT NULL REFERENCES organizations(id),
    title                    TEXT NOT NULL,
    prompt                   TEXT NOT NULL,
    status                   TEXT NOT NULL DEFAULT 'planning',
                             -- 'planning','investigating','debating',
                             -- 'voting','completed','archived'
    current_phase            TEXT,
    plan                     JSONB NOT NULL DEFAULT '{}'::jsonb,
    confidence_score         NUMERIC(5,2),
    final_recommendation     JSONB,
    created_at               TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at             TIMESTAMPTZ,
    CHECK (status IN ('planning','investigating','debating',
                     'voting','completed','archived'))
);
CREATE INDEX inv_org_status_idx ON investigations(organization_id, status);

CREATE TABLE tasks (
    id                       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    investigation_id         UUID NOT NULL REFERENCES investigations(id),
    department_id            UUID REFERENCES departments(id),
    assigned_worker_id       UUID REFERENCES workers(id),
    description              TEXT NOT NULL,
    status                   TEXT NOT NULL DEFAULT 'pending',
                             -- 'pending','in_progress','blocked','done'
    result                   JSONB,
    created_at               TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at             TIMESTAMPTZ
);
CREATE INDEX task_inv_idx     ON tasks(investigation_id);
CREATE INDEX task_worker_idx  ON tasks(assigned_worker_id, status);
```

### 13.3.3 Meetings & Internal Communication

```sql
CREATE TABLE meetings (
    id                       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id          UUID NOT NULL REFERENCES organizations(id),
    investigation_id         UUID REFERENCES investigations(id),
    title                    TEXT NOT NULL,
    agenda                   JSONB NOT NULL DEFAULT '[]'::jsonb,
    status                   TEXT NOT NULL DEFAULT 'called',
                             -- 'called','started','ended','cancelled'
    quorum_rule              JSONB NOT NULL DEFAULT '{}'::jsonb,
    called_at                TIMESTAMPTZ NOT NULL DEFAULT now(),
    started_at               TIMESTAMPTZ,
    ended_at                 TIMESTAMPTZ
);
CREATE INDEX meet_org_idx      ON meetings(organization_id, status);
CREATE INDEX meet_inv_idx      ON meetings(investigation_id);

CREATE TABLE meeting_participants (
    meeting_id               UUID NOT NULL REFERENCES meetings(id),
    worker_id                UUID NOT NULL REFERENCES workers(id),
    role                     TEXT NOT NULL,  -- 'chair','voter','observer'
    joined_at                TIMESTAMPTZ,
    left_at                  TIMESTAMPTZ,
    PRIMARY KEY (meeting_id, worker_id)
);

CREATE TABLE messages (
    id                       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id          UUID NOT NULL REFERENCES organizations(id),
    meeting_id               UUID REFERENCES meetings(id),
    investigation_id         UUID REFERENCES investigations(id),
    sender_worker_id         UUID NOT NULL REFERENCES workers(id),
    recipient_worker_id      UUID REFERENCES workers(id),  -- NULL=broadcast
    content                   TEXT NOT NULL,
    message_type             TEXT NOT NULL DEFAULT 'statement',
                             -- 'statement','question','challenge',
                             -- 'evidence','vote','summary'
    parent_message_id        UUID REFERENCES messages(id),  -- threading
    created_at               TIMESTAMPTZ NOT NULL DEFAULT now(),
    metadata                 JSONB NOT NULL DEFAULT '{}'::jsonb
);
CREATE INDEX msg_meeting_idx    ON messages(meeting_id, created_at);
CREATE INDEX msg_thread_idx     ON messages(parent_message_id);
CREATE INDEX msg_sender_idx     ON messages(sender_worker_id, created_at);
```

### 13.3.4 Evidence & Debate

```sql
CREATE TABLE evidence (
    id                       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    investigation_id         UUID NOT NULL REFERENCES investigations(id),
    submitted_by_worker_id   UUID NOT NULL REFERENCES workers(id),
    source_type              TEXT NOT NULL,
                             -- 'paper','survey','external_comm',
                             -- 'internal_analysis','web'
    source_ref               JSONB NOT NULL,  -- {url, citation, contact_id}
    summary                  TEXT NOT NULL,
    content                  JSONB NOT NULL,
    status                   TEXT NOT NULL DEFAULT 'submitted',
                             -- 'submitted','challenged','accepted',
                             -- 'rejected','superseded'
    challenge_count          INT NOT NULL DEFAULT 0,
    submitted_at             TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX evid_inv_idx       ON evidence(investigation_id, status);
CREATE INDEX evid_status_idx    ON evidence(status) WHERE status = 'challenged';

CREATE TABLE evidence_challenges (
    id                       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    evidence_id              UUID NOT NULL REFERENCES evidence(id),
    challenged_by_worker_id  UUID NOT NULL REFERENCES workers(id),
    challenge_text           TEXT NOT NULL,
    status                   TEXT NOT NULL DEFAULT 'open',
                             -- 'open','resolved_upheld','resolved_rejected'
    created_at               TIMESTAMPTZ NOT NULL DEFAULT now(),
    resolved_at              TIMESTAMPTZ
);
CREATE INDEX chal_evid_idx      ON evidence_challenges(evidence_id);

CREATE TABLE debates (
    id                       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    investigation_id         UUID NOT NULL REFERENCES investigations(id),
    topic                    TEXT NOT NULL,
    status                   TEXT NOT NULL DEFAULT 'open',
                             -- 'open','concluded'
    opened_at                TIMESTAMPTZ NOT NULL DEFAULT now(),
    concluded_at             TIMESTAMPTZ
);
CREATE TABLE debate_arguments (
    id                       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    debate_id                UUID NOT NULL REFERENCES debates(id),
    worker_id                UUID NOT NULL REFERENCES workers(id),
    stance                    TEXT NOT NULL,  -- 'support','oppose'
    argument_text             TEXT NOT NULL,
    parent_argument_id        UUID REFERENCES debate_arguments(id),
    created_at               TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX arg_debate_idx     ON debate_arguments(debate_id);
```

### 13.3.5 Voting & Decisions

```sql
CREATE TABLE votes (
    id                       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id          UUID NOT NULL REFERENCES organizations(id),
    investigation_id         UUID REFERENCES investigations(id),
    meeting_id               UUID REFERENCES meetings(id),
    voter_worker_id          UUID NOT NULL REFERENCES workers(id),
    topic_type               TEXT NOT NULL,
                             -- 'decision','amendment','hiring','firing',
                             -- 'promotion','restructure'
    topic_ref                UUID,   -- FK to decisions/amendments/etc.
    choice                   TEXT NOT NULL,  -- 'yes','no','abstain'
    weight                   NUMERIC(6,3) NOT NULL,  -- trust-derived
    reasoning                TEXT,
    created_at               TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX vote_topic_idx     ON votes(topic_type, topic_ref);

CREATE TABLE decisions (
    id                       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id          UUID NOT NULL REFERENCES organizations(id),
    investigation_id         UUID REFERENCES investigations(id),
    decision_type            TEXT NOT NULL,
                             -- 'recommendation','policy_change','hiring',
                             -- 'amendment','restructure'
    title                    TEXT NOT NULL,
    content                  JSONB NOT NULL,
    status                   TEXT NOT NULL DEFAULT 'proposed',
                             -- 'proposed','voting','ratified','vetoed',
                             -- 'executed','rolled_back'
    outcome                  JSONB,   -- {yes_weight, no_weight, ...}
    decided_at               TIMESTAMPTZ
);
CREATE INDEX dec_org_idx        ON decisions(organization_id, status);
```

### 13.3.6 Memory

```sql
CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE memories (
    id                       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id          UUID NOT NULL REFERENCES organizations(id),
    department_id            UUID REFERENCES departments(id),
    worker_id                UUID REFERENCES workers(id),
    scope                    TEXT NOT NULL,  -- 'org','department','worker'
    memory_type              TEXT NOT NULL,
                             -- 'episodic','procedural','semantic','policy'
    content                  JSONB NOT NULL,  -- see 13.4.3
    embedding                vector(1536),   -- pgvector, for semantic recall
    importance               NUMERIC(3,2) NOT NULL DEFAULT 0.5,
    decay_score              NUMERIC(3,2) NOT NULL DEFAULT 1.0,
    source_investigation_id  UUID REFERENCES investigations(id),
    source_event_id          UUID,           -- FK to events.id
    created_at               TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_reinforced_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX mem_org_scope_idx  ON memories(organization_id, scope);
CREATE INDEX mem_embedding_idx  ON memories USING ivfflat (embedding vector_cosine_ops)
    WITH (lists = 100);
```

### 13.3.7 Trust (Time-Series)

```sql
CREATE TABLE trust_records (
    id                       UUID NOT NULL,
    organization_id          UUID NOT NULL REFERENCES organizations(id),
    worker_id                UUID NOT NULL REFERENCES workers(id),
    evaluated_by_worker_id   UUID REFERENCES workers(id),  -- NULL=self/system
    trust_score              NUMERIC(5,2) NOT NULL,
    trust_context            JSONB NOT NULL,
                             -- {domain:'research_accuracy', evidence_ref, delta}
    recorded_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (id, recorded_at)
) PARTITION BY RANGE (recorded_at);
-- Monthly partitions created by a scheduled job; see 13.6.
```

### 13.3.8 Constitution & DNA

```sql
CREATE TABLE constitutions (
    id                       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id          UUID NOT NULL REFERENCES organizations(id),
    version                  INT NOT NULL,
    content                  JSONB NOT NULL,   -- see 13.4.2
    is_current               BOOLEAN NOT NULL DEFAULT false,
    ratified_by_vote_id      UUID REFERENCES votes(id),
    superseded_by            UUID REFERENCES constitutions(id),
    ratified_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (organization_id, version)
);
CREATE INDEX const_org_current_idx ON constitutions(organization_id)
    WHERE is_current;

CREATE TABLE constitution_amendments (
    id                       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    constitution_id          UUID NOT NULL REFERENCES constitutions(id),
    proposed_by_worker_id     UUID NOT NULL REFERENCES workers(id),
    change_diff              JSONB NOT NULL,  -- JSON Patch (RFC 6902)
    status                   TEXT NOT NULL DEFAULT 'proposed',
                             -- 'proposed','ratified','rejected'
    vote_id                   UUID REFERENCES votes(id),
    proposed_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    ratified_at              TIMESTAMPTZ
);

CREATE TABLE dna_profiles (
    id                       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id          UUID NOT NULL REFERENCES organizations(id),
    version                  INT NOT NULL,
    parameters               JSONB NOT NULL,  -- see 13.4.1
    is_current               BOOLEAN NOT NULL DEFAULT false,
    source_evolution_event_id UUID,          -- FK to evolution_events.id
    effective_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (organization_id, version)
);
-- Now add the deferred circular FKs:
ALTER TABLE organizations
    ADD CONSTRAINT org_dna_fk  FOREIGN KEY (current_dna_profile_id)
        REFERENCES dna_profiles(id) DEFERRABLE INITIALLY DEFERRED,
    ADD CONSTRAINT org_const_fk FOREIGN KEY (current_constitution_id)
        REFERENCES constitutions(id) DEFERRABLE INITIALLY DEFERRED;
```

### 13.3.9 Evolution

```sql
CREATE TABLE evolution_events (
    id                       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id          UUID NOT NULL REFERENCES organizations(id),
    event_type               TEXT NOT NULL,
                             -- 'hire','fire','promote','create_department',
                             -- 'split_department','merge_department',
                             -- 'policy_rewrite','dna_shift'
    target_type              TEXT NOT NULL,  -- 'worker','department','policy','dna'
    target_id                UUID NOT NULL,
    description              TEXT NOT NULL,
    rationale                TEXT NOT NULL,
    triggered_by_vote_id     UUID REFERENCES votes(id),
    triggered_by_investigation_id UUID REFERENCES investigations(id),
    triggered_by_rule        TEXT,           -- automated evolution rule id
    status                   TEXT NOT NULL DEFAULT 'proposed',
                             -- 'proposed','approved','executed','rolled_back'
    before_state             JSONB,
    after_state              JSONB,
    executed_at              TIMESTAMPTZ
);
CREATE INDEX evo_org_idx        ON evolution_events(organization_id, status);
CREATE INDEX evo_target_idx     ON evolution_events(target_type, target_id);
```

### 13.3.10 External Communication (Time-Series)

```sql
CREATE TABLE external_contacts (
    id                       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id          UUID NOT NULL REFERENCES organizations(id),
    name                     TEXT NOT NULL,
    role                     TEXT,
    organization             TEXT,            -- external org name
    channels                 JSONB NOT NULL DEFAULT '{}'::jsonb,
                             -- {email, telegram, slack, discord, whatsapp}
    relationship_type         TEXT NOT NULL,
                             -- 'expert','customer','candidate','vendor','professor'
    trust_score              NUMERIC(5,2) DEFAULT 50.0,
    created_at               TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_contacted_at        TIMESTAMPTZ
);
CREATE INDEX contact_org_idx    ON external_contacts(organization_id);

CREATE TABLE communication_logs (
    id                       UUID NOT NULL,
    organization_id          UUID NOT NULL REFERENCES organizations(id),
    worker_id                UUID REFERENCES workers(id),
    external_contact_id      UUID REFERENCES external_contacts(id),
    channel                  TEXT NOT NULL,  -- 'email','telegram',...
    direction                TEXT NOT NULL,  -- 'outbound','inbound'
    subject                  TEXT,
    content                  TEXT NOT NULL,
    caspian_message_id       TEXT,           -- idempotency key from Caspian
    status                   TEXT NOT NULL DEFAULT 'sent',
                             -- 'sent','delivered','replied','failed'
    related_investigation_id UUID REFERENCES investigations(id),
    related_evidence_id      UUID REFERENCES evidence(id),
    sent_at                  TIMESTAMPTZ NOT NULL DEFAULT now(),
    received_at              TIMESTAMPTZ,
    replied_at               TIMESTAMPTZ,
    PRIMARY KEY (id, sent_at)
) PARTITION BY RANGE (sent_at);
CREATE INDEX comm_contact_idx ON communication_logs(external_contact_id, sent_at);
```

### 13.3.11 Event Store (Source of Truth)

```sql
CREATE TABLE events (
    id                       UUID NOT NULL,
    organization_id          UUID NOT NULL,
    event_type               TEXT NOT NULL,   -- 'investigation.started', ...
    event_category           TEXT NOT NULL,   -- 'organization','worker',...
    source                   JSONB NOT NULL,   -- {component, worker_id, dept_id}
    payload                  JSONB NOT NULL,
    causal_parent_id         UUID,            -- FK self, deferred
    correlation_id           UUID NOT NULL,   -- groups a cascade
    root_event_id            UUID NOT NULL,   -- originator of the chain
    chain_depth              INT NOT NULL DEFAULT 0,
    sequence_number          BIGINT NOT NULL,  -- per-org monotonic
    occurred_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    recorded_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    schema_version           TEXT NOT NULL DEFAULT '1.0',
    PRIMARY KEY (id, occurred_at),
    UNIQUE (organization_id, sequence_number)
) PARTITION BY RANGE (occurred_at);
CREATE INDEX evt_correlation_idx ON events(correlation_id);
CREATE INDEX evt_org_seq_idx     ON events(organization_id, sequence_number);
CREATE INDEX evt_type_idx        ON events(event_type, occurred_at);
```

The full event taxonomy, envelope schema, and replay semantics are specified
in `16-event-system.md`. The domain tables above are **projections** written by
projection consumers that subscribe to the event stream.

---

## 13.4 JSONB Column Schemas

### 13.4.1 DNA Parameters (`dna_profiles.parameters`)

```json
{
  "innovation":          0.62,
  "risk":                0.45,
  "hierarchy":           0.70,
  "accuracy":            0.88,
  "speed":               0.55,
  "transparency":        0.80,
  "creativity":          0.60,
  "long_term_thinking":  0.72,
  "security":            0.50
}
```
Each value is `0.0–1.0`. Effects on planning, approvals, voting, and hiring are
defined in `21-organizational-dna.md`. A `GIN` index supports containment
queries ("find orgs with `accuracy >= 0.85`").

### 13.4.2 Constitution (`constitutions.content`)

```json
{
  "mission":     "Investigate and decide on startup viability.",
  "principles":  ["Evidence before opinion", "Dissent is recorded"],
  "values":      ["accuracy", "transparency", "pragmatism"],
  "ethics":      ["no fabrication", "cite sources", "flag uncertainty"],
  "decision_rules": {
    "voting_quorum": 0.6,
    "trust_weighted": true,
    "amendment_threshold": 0.75,
    "ceo_veto": false
  }
}
```
Versioned immutably; amendments produce a new `constitutions` row with
`superseded_by` set on the prior version. See `09-constitution-engine.md`.

### 13.4.3 Memory Content (`memories.content`)

```json
{
  "summary":   "Q3 market survey overestimated demand by 40%.",
  "details":   "Marketing surveyed 200 Telegram users; sample skewed early-adopters.",
  "lessons":   ["weight surveys by sample representativeness"],
  "tags":      ["marketing", "survey-bias", "q3"],
  "provenance": { "investigation_id": "...", "evidence_id": "..." }
}
```
The `embedding` column stores the vector of `summary + details` for semantic
recall. Decay and reinforcement mechanics are in `06-memory-system.md`.

---

## 13.5 Time-Series Tables & Partitioning Strategy

Four tables are partitioned by month using declarative range partitioning.
A scheduled `pg_cron` job creates the next month's partition 7 days ahead and
detaches partitions older than the retention window into cold storage.

| Table | Partition key | Retention (hot) | Cold storage |
|-------|---------------|----------------|--------------|
| `events` | `occurred_at` | 6 months | S3 Parquet (audit forever) |
| `trust_records` | `recorded_at` | 12 months | S3 Parquet |
| `communication_logs` | `sent_at` | 3 months | S3 Parquet |
| `worker_activity_logs` | `logged_at` | 3 months | S3 Parquet |

```sql
-- Example monthly partition for events
CREATE TABLE events_2026_08 PARTITION OF events
    FOR VALUES FROM ('2026-08-01') TO ('2026-09-01');

-- Detach + export (monthly maintenance)
ALTER TABLE events DETACH PARTITION events_2026_02;
-- Then pg_dump to Parquet and DROP the detached partition.
```

`worker_activity_logs` (referenced above) tracks per-worker state transitions
for the mobile dashboard's "what is each worker doing now" feed — see
`22-mobile-ux.md`.

---

## 13.6 Index Strategy Summary

| Access pattern | Index |
|----------------|-------|
| Active orgs by health | `org_status_idx` (partial) |
| Worker by org + dept | `worker_org_dept_idx` |
| Active investigations | `inv_org_status_idx` |
| Meeting message thread (chronological) | `msg_meeting_idx` |
| Open evidence challenges | `evid_status_idx` (partial) |
| Memory semantic recall | `mem_embedding_idx` (ivfflat) |
| DNA containment queries | `GIN` on `dna_profiles.parameters` |
| Constitution content queries | `GIN` on `constitutions.content` |
| Event replay by org sequence | `evt_org_seq_idx` |
| Causal chain traversal | `evt_correlation_idx` + recursive CTE |

---

## 13.7 Key Queries & Optimization

### Q1 — Current Organization State (mobile home screen)

```sql
SELECT o.*, d.parameters AS dna, c.content AS constitution
FROM organizations o
JOIN dna_profiles   d ON d.id = o.current_dna_profile_id
JOIN constitutions  c ON c.id = o.current_constitution_id
WHERE o.id = $1;
```
Single-row join; `current_*` columns are FKs to the `is_current` rows. O(1).

### Q2 — Live Investigation Progress

```sql
SELECT i.id, i.title, i.status, i.current_phase,
       COUNT(DISTINCT t.id) FILTER (WHERE t.status='done')    AS tasks_done,
       COUNT(DISTINCT t.id)                                  AS tasks_total,
       COUNT(DISTINCT e.id)                                  AS evidence_count,
       COUNT(DISTINCT e.id) FILTER (WHERE e.status='challenged') AS challenges
FROM investigations i
LEFT JOIN tasks    t ON t.investigation_id = i.id
LEFT JOIN evidence e ON e.investigation_id = i.id
WHERE i.id = $1
GROUP BY i.id;
```
Backed by `inv_org_status_idx` and `evid_inv_idx`. For high-frequency polling
this is materialized into `investigation_progress_mv` refreshed every 5s.

### Q3 — Worker Trust History (time-series)

```sql
SELECT recorded_at, trust_score, trust_context->>'domain' AS domain
FROM trust_records
WHERE worker_id = $1 AND recorded_at >= now() - interval '90 days'
ORDER BY recorded_at;
```
Hits only the last 3 monthly partitions via partition pruning.

### Q4 — Semantic Memory Recall

```sql
SELECT id, content->>'summary' AS summary, importance, decay_score,
       embedding <=> $1 AS distance
FROM memories
WHERE organization_id = $2
  AND decay_score > 0.2
ORDER BY embedding <=> $1
LIMIT 10;
```
Uses ivfflat ANN index. The `decay_score > 0.2` filter prunes decayed memories
before ranking — see `06-memory-system.md`.

### Q5 — Causal Chain Reconstruction (audit)

```sql
WITH RECURSIVE chain AS (
  SELECT * FROM events WHERE id = $1
  UNION ALL
  SELECT e.* FROM events e JOIN chain c ON e.causal_parent_id = c.id
)
SELECT id, event_type, source, payload, occurred_at, chain_depth
FROM chain ORDER BY chain_depth;
```
Traverses the causal chain from any event back to its root. This is how the
organization answers "why did we decide this?" years later.

### Q6 — Organizational Health Rollup

```sql
SELECT AVG(current_trust_score) AS avg_trust,
       COUNT(*) FILTER (WHERE status='active') AS active_workers,
       (SELECT COUNT(*) FROM investigations
        WHERE organization_id=$1 AND status IN ('investigating','debating'))
         AS active_investigations
FROM workers WHERE organization_id = $1;
```
Materialized as `org_health_mv`, refreshed every 30s, feeds the mobile
dashboard's "Organization Health %" gauge — see `22-mobile-ux.md`.

---

## 13.8 Data Lifecycle & Retention

1. **Hot** (Postgres, partitioned): last 3–12 months per table.
2. **Warm** (Postgres read replica): last 12 months, queryable.
3. **Cold** (S3 Parquet via `pg_dump`/`COPY`): full audit history, queried
   via external tables or on-demand restore.
4. **Never deleted**: `events`, `decisions`, `constitutions`, `evolution_events`.
   The organization's institutional memory is its audit trail.

Decay is logical, not physical: `memories.decay_score` decreases over time and
is reinforced by `memory_reinforcements` rows; low-decay memories are excluded
from recall but retained for audit. See `06-memory-system.md`.

---

## 13.9 Cross-References

- `04-technical-architecture.md` — system topology and service boundaries.
- `16-event-system.md` — event store schema, event sourcing, and replay.
- `06-memory-system.md` — memory decay/reinforcement and semantic recall.
- `08-trust-engine.md` — trust score computation feeding `trust_records`.
- `09-constitution-engine.md` — amendment workflow and versioning.
- `21-organizational-dna.md` — DNA parameter effects on projections.
- `07-evolution-engine.md` — evolution event triggers and rollback.
- `12-caspian-integration.md` — `communication_logs` write path.
- `22-mobile-ux.md` — dashboard queries backed by materialized views.
- `27-scaling-strategy.md` — read replicas, partitioning, sharding by org.
