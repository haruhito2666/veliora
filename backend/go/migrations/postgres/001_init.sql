-- Veliora — initial schema (doc 13 §13.3, adapted: no partitioning yet).
-- Event store is the source of truth; domain tables are projections.

CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- ── Organizational structure (doc 13 §13.3.1) ──────────────────────────
CREATE TABLE organizations (
    id                       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name                     TEXT NOT NULL,
    template                 TEXT NOT NULL,
    status                   TEXT NOT NULL DEFAULT 'forming'
                             CHECK (status IN ('forming','active','evolving','dissolved')),
    current_dna_profile_id   UUID,
    current_constitution_id  UUID,
    health_score             NUMERIC(5,2) NOT NULL DEFAULT 0,
    metadata                 JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at               TIMESTAMPTZ NOT NULL DEFAULT now(),
    dissolved_at             TIMESTAMPTZ
);
CREATE INDEX org_status_idx ON organizations(status) WHERE status = 'active';

CREATE TABLE departments (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id    UUID NOT NULL REFERENCES organizations(id),
    name               TEXT NOT NULL,
    charter            JSONB NOT NULL DEFAULT '{}'::jsonb,
    parent_department_id UUID REFERENCES departments(id),
    status             TEXT NOT NULL DEFAULT 'active'
                       CHECK (status IN ('active','split','merged','dissolved')),
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    dissolved_at       TIMESTAMPTZ
);
CREATE INDEX dept_org_idx    ON departments(organization_id);
CREATE INDEX dept_parent_idx ON departments(parent_department_id);

CREATE TABLE workers (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id      UUID NOT NULL REFERENCES organizations(id),
    department_id        UUID REFERENCES departments(id),
    role                 TEXT NOT NULL,
    status               TEXT NOT NULL DEFAULT 'active'
                         CHECK (status IN ('active','on_assignment','on_leave','promoted','terminated')),
    configuration        JSONB NOT NULL DEFAULT '{}'::jsonb,
    current_trust_score  NUMERIC(5,2) NOT NULL DEFAULT 50.0,
    hired_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    terminated_at        TIMESTAMPTZ
);
CREATE INDEX worker_org_dept_idx ON workers(organization_id, department_id);
CREATE INDEX worker_status_idx   ON workers(organization_id, status);

-- ── Per-org monotonic sequence (addition; doc 16 §16.9) ────────────────
CREATE TABLE org_sequences (
    organization_id UUID PRIMARY KEY REFERENCES organizations(id),
    last            BIGINT NOT NULL DEFAULT 0
);

-- ── Investigation & reasoning (doc 13 §13.3.2) ─────────────────────────
CREATE TABLE investigations (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id      UUID NOT NULL REFERENCES organizations(id),
    title                TEXT NOT NULL,
    prompt               TEXT NOT NULL,
    status               TEXT NOT NULL DEFAULT 'created'
                         CHECK (status IN ('created','planning','researching','analyzing',
                                           'debating','synthesizing','vote','confidence_chk',
                                           'reporting','completed','cancelled','failed','archived')),
    current_phase        TEXT,
    charter              JSONB,
    plan                 JSONB NOT NULL DEFAULT '{}'::jsonb,
    cycle_count          INT NOT NULL DEFAULT 1,
    confidence_score     NUMERIC(5,2),
    final_recommendation JSONB,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at         TIMESTAMPTZ
);
CREATE INDEX inv_org_status_idx ON investigations(organization_id, status);

CREATE TABLE tasks (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    investigation_id   UUID NOT NULL REFERENCES investigations(id),
    department_id      UUID REFERENCES departments(id),
    assigned_worker_id UUID REFERENCES workers(id),
    description        TEXT NOT NULL,
    status             TEXT NOT NULL DEFAULT 'pending'
                       CHECK (status IN ('pending','in_progress','blocked','done','failed')),
    result             JSONB,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at       TIMESTAMPTZ
);
CREATE INDEX task_inv_idx    ON tasks(investigation_id);
CREATE INDEX task_worker_idx ON tasks(assigned_worker_id, status);

-- ── Meetings & internal communication (doc 13 §13.3.3) ─────────────────
CREATE TABLE meetings (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id   UUID NOT NULL REFERENCES organizations(id),
    investigation_id  UUID REFERENCES investigations(id),
    title             TEXT NOT NULL,
    agenda            JSONB NOT NULL DEFAULT '[]'::jsonb,
    status            TEXT NOT NULL DEFAULT 'called'
                      CHECK (status IN ('called','agenda_set','notified','preparing',
                                        'conducting','minutes','ended','cancelled')),
    quorum_rule       JSONB NOT NULL DEFAULT '{}'::jsonb,
    minutes           TEXT,
    called_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    started_at        TIMESTAMPTZ,
    ended_at          TIMESTAMPTZ
);
CREATE INDEX meet_org_idx ON meetings(organization_id, status);
CREATE INDEX meet_inv_idx ON meetings(investigation_id);

CREATE TABLE meeting_participants (
    meeting_id UUID NOT NULL REFERENCES meetings(id),
    worker_id  UUID NOT NULL REFERENCES workers(id),
    role       TEXT NOT NULL,             -- 'chair','voter','observer'
    joined_at  TIMESTAMPTZ,
    left_at    TIMESTAMPTZ,
    PRIMARY KEY (meeting_id, worker_id)
);

CREATE TABLE messages (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id     UUID NOT NULL REFERENCES organizations(id),
    meeting_id          UUID REFERENCES meetings(id),
    investigation_id    UUID REFERENCES investigations(id),
    sender_worker_id    UUID NOT NULL REFERENCES workers(id),
    recipient_worker_id UUID REFERENCES workers(id),  -- NULL=broadcast
    content             TEXT NOT NULL,
    message_type        TEXT NOT NULL DEFAULT 'statement'
                        CHECK (message_type IN ('statement','question','challenge',
                                                'evidence','vote','summary',
                                                'evidence_request','critique',
                                                'clarification','directive')),
    parent_message_id   UUID REFERENCES messages(id),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    metadata            JSONB NOT NULL DEFAULT '{}'::jsonb
);
CREATE INDEX msg_meeting_idx ON messages(meeting_id, created_at);
CREATE INDEX msg_thread_idx  ON messages(parent_message_id);
CREATE INDEX msg_sender_idx  ON messages(sender_worker_id, created_at);

-- ── Evidence & debate (doc 13 §13.3.4) ─────────────────────────────────
CREATE TABLE evidence (
    id                     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    investigation_id       UUID NOT NULL REFERENCES investigations(id),
    submitted_by_worker_id UUID NOT NULL REFERENCES workers(id),
    source_type            TEXT NOT NULL
                           CHECK (source_type IN ('paper','survey','external_comm',
                                                  'internal_analysis','web')),
    source_ref             JSONB NOT NULL,
    summary                TEXT NOT NULL,
    content                JSONB NOT NULL,
    status                 TEXT NOT NULL DEFAULT 'submitted'
                           CHECK (status IN ('submitted','challenged','accepted',
                                             'rejected','superseded')),
    challenge_count        INT NOT NULL DEFAULT 0,
    submitted_at           TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX evid_inv_idx   ON evidence(investigation_id, status);
CREATE INDEX evid_status_idx ON evidence(status) WHERE status = 'challenged';

CREATE TABLE evidence_challenges (
    id                     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    evidence_id            UUID NOT NULL REFERENCES evidence(id),
    challenged_by_worker_id UUID NOT NULL REFERENCES workers(id),
    challenge_text         TEXT NOT NULL,
    status                 TEXT NOT NULL DEFAULT 'open'
                           CHECK (status IN ('open','resolved_upheld','resolved_rejected',
                                             'resolved_qualified','resolved_rebutted')),
    created_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    resolved_at            TIMESTAMPTZ
);
CREATE INDEX chal_evid_idx ON evidence_challenges(evidence_id);

CREATE TABLE debates (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    investigation_id  UUID NOT NULL REFERENCES investigations(id),
    topic             TEXT NOT NULL,
    debate_type       TEXT NOT NULL DEFAULT 'evidence_challenge'
                      CHECK (debate_type IN ('evidence_challenge','position',
                                             'policy','strategic')),
    status            TEXT NOT NULL DEFAULT 'open'
                      CHECK (status IN ('open','concluded')),
    phase             TEXT NOT NULL DEFAULT 'opening'
                      CHECK (phase IN ('opening','evidence','cross_exam','rebuttal',
                                       'synthesis','vote','closed')),
    opened_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    concluded_at      TIMESTAMPTZ
);
CREATE INDEX debate_inv_idx ON debates(investigation_id);

CREATE TABLE debate_arguments (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    debate_id         UUID NOT NULL REFERENCES debates(id),
    worker_id         UUID NOT NULL REFERENCES workers(id),
    stance            TEXT NOT NULL,             -- 'support','oppose','pass','qualified'
    argument_text     TEXT NOT NULL,
    parent_argument_id UUID REFERENCES debate_arguments(id),
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX arg_debate_idx ON debate_arguments(debate_id);

-- ── Voting & decisions (doc 13 §13.3.5) ────────────────────────────────
CREATE TABLE votes (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id  UUID NOT NULL REFERENCES organizations(id),
    investigation_id UUID REFERENCES investigations(id),
    meeting_id       UUID REFERENCES meetings(id),
    voter_worker_id  UUID NOT NULL REFERENCES workers(id),
    topic_type       TEXT NOT NULL
                     CHECK (topic_type IN ('decision','amendment','hiring','firing',
                                           'promotion','restructure')),
    topic_ref        UUID,
    choice           TEXT NOT NULL CHECK (choice IN ('yes','no','abstain')),
    weight           NUMERIC(6,3) NOT NULL,
    reasoning        TEXT,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX vote_topic_idx ON votes(topic_type, topic_ref);

CREATE TABLE decisions (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id    UUID NOT NULL REFERENCES organizations(id),
    investigation_id   UUID REFERENCES investigations(id),
    decision_type      TEXT NOT NULL
                       CHECK (decision_type IN ('recommendation','policy_change','hiring',
                                                'amendment','restructure')),
    title              TEXT NOT NULL,
    content            JSONB NOT NULL,
    status             TEXT NOT NULL DEFAULT 'proposed'
                       CHECK (status IN ('proposed','voting','ratified','vetoed',
                                         'executed','rolled_back')),
    outcome            JSONB,
    decided_at         TIMESTAMPTZ
);
CREATE INDEX dec_org_idx ON decisions(organization_id, status);

-- ── Memory (doc 13 §13.3.6) ────────────────────────────────────────────
CREATE TABLE memories (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id         UUID NOT NULL REFERENCES organizations(id),
    department_id           UUID REFERENCES departments(id),
    worker_id               UUID REFERENCES workers(id),
    scope                   TEXT NOT NULL CHECK (scope IN ('org','department','worker')),
    memory_type             TEXT NOT NULL
                            CHECK (memory_type IN ('episodic','procedural','semantic','policy')),
    content                 JSONB NOT NULL,
    embedding               vector(1536),
    importance              NUMERIC(3,2) NOT NULL DEFAULT 0.5,
    decay_score             NUMERIC(3,2) NOT NULL DEFAULT 1.0,
    source_investigation_id UUID REFERENCES investigations(id),
    source_event_id         UUID,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_reinforced_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX mem_org_scope_idx ON memories(organization_id, scope);

-- ── Trust (doc 13 §13.3.7, non-partitioned) ────────────────────────────
CREATE TABLE trust_records (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id       UUID NOT NULL REFERENCES organizations(id),
    worker_id             UUID NOT NULL REFERENCES workers(id),
    evaluated_by_worker_id UUID REFERENCES workers(id),
    trust_score           NUMERIC(5,2) NOT NULL,
    trust_weight          NUMERIC(5,3) NOT NULL DEFAULT 0,
    trust_context         JSONB NOT NULL,
    recorded_at           TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX trust_worker_idx ON trust_records(worker_id, recorded_at);

-- ── Constitution & DNA (doc 13 §13.3.8) ────────────────────────────────
CREATE TABLE constitutions (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id      UUID NOT NULL REFERENCES organizations(id),
    version              INT NOT NULL,
    content              JSONB NOT NULL,
    is_current           BOOLEAN NOT NULL DEFAULT false,
    ratified_by_vote_id  UUID,
    superseded_by        UUID,
    ratified_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (organization_id, version)
);
CREATE INDEX const_org_current_idx ON constitutions(organization_id) WHERE is_current;

CREATE TABLE constitution_amendments (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    constitution_id       UUID NOT NULL REFERENCES constitutions(id),
    proposed_by_worker_id UUID NOT NULL REFERENCES workers(id),
    change_diff           JSONB NOT NULL,
    status                TEXT NOT NULL DEFAULT 'proposed'
                          CHECK (status IN ('proposed','ratified','rejected')),
    vote_id               UUID,
    proposed_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    ratified_at           TIMESTAMPTZ
);

CREATE TABLE dna_profiles (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id         UUID NOT NULL REFERENCES organizations(id),
    version                 INT NOT NULL,
    parameters              JSONB NOT NULL,
    is_current              BOOLEAN NOT NULL DEFAULT false,
    source_evolution_event_id UUID,
    effective_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (organization_id, version)
);

ALTER TABLE organizations
    ADD CONSTRAINT org_dna_fk FOREIGN KEY (current_dna_profile_id)
        REFERENCES dna_profiles(id) DEFERRABLE INITIALLY DEFERRED,
    ADD CONSTRAINT org_const_fk FOREIGN KEY (current_constitution_id)
        REFERENCES constitutions(id) DEFERRABLE INITIALLY DEFERRED;

-- ── Evolution (doc 13 §13.3.9) ─────────────────────────────────────────
CREATE TABLE evolution_events (
    id                            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id               UUID NOT NULL REFERENCES organizations(id),
    event_type                    TEXT NOT NULL
                                  CHECK (event_type IN ('hire','fire','promote',
                                                        'create_department','split_department',
                                                        'merge_department','policy_rewrite',
                                                        'dna_shift','amend_constitution')),
    target_type                   TEXT NOT NULL,
    target_id                     UUID NOT NULL,
    description                   TEXT NOT NULL,
    rationale                     TEXT NOT NULL,
    triggered_by_vote_id          UUID,
    triggered_by_investigation_id UUID REFERENCES investigations(id),
    triggered_by_rule             TEXT,
    status                        TEXT NOT NULL DEFAULT 'proposed'
                                  CHECK (status IN ('proposed','debating','voting',
                                                    'approved','rejected','executing',
                                                    'applied','rolled_back')),
    before_state                  JSONB,
    after_state                   JSONB,
    proposed_at                   TIMESTAMPTZ NOT NULL DEFAULT now(),
    executed_at                   TIMESTAMPTZ
);
CREATE INDEX evo_org_idx    ON evolution_events(organization_id, status);
CREATE INDEX evo_target_idx ON evolution_events(target_type, target_id);

-- ── External communication (doc 13 §13.3.10, non-partitioned) ──────────
CREATE TABLE external_contacts (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id   UUID NOT NULL REFERENCES organizations(id),
    name              TEXT NOT NULL,
    role              TEXT,
    organization      TEXT,
    channels          JSONB NOT NULL DEFAULT '{}'::jsonb,
    relationship_type TEXT NOT NULL
                      CHECK (relationship_type IN ('expert','customer','candidate',
                                                   'vendor','professor')),
    trust_score       NUMERIC(5,2) NOT NULL DEFAULT 50.0,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_contacted_at TIMESTAMPTZ
);
CREATE INDEX contact_org_idx ON external_contacts(organization_id);

CREATE TABLE communication_logs (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id         UUID NOT NULL REFERENCES organizations(id),
    worker_id               UUID REFERENCES workers(id),
    external_contact_id     UUID REFERENCES external_contacts(id),
    investigation_id        UUID REFERENCES investigations(id),
    channel                 TEXT NOT NULL CHECK (channel IN ('email','telegram','slack','discord','whatsapp')),
    direction               TEXT NOT NULL CHECK (direction IN ('outbound','inbound')),
    subject                 TEXT,
    content                 TEXT NOT NULL,
    caspian_message_id      TEXT,
    status                  TEXT NOT NULL DEFAULT 'unsent'
                            CHECK (status IN ('unsent','sent','delivered','replied','failed')),
    error                   TEXT,
    sent_at                 TIMESTAMPTZ,
    received_at             TIMESTAMPTZ,
    replied_at              TIMESTAMPTZ,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX comm_contact_idx ON communication_logs(external_contact_id, sent_at);
CREATE INDEX comm_org_idx     ON communication_logs(organization_id, sent_at);

-- ── Caspian outreach (doc 19 §7, addition) ─────────────────────────────
CREATE TABLE caspian_outreach (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    investigation_id  UUID NOT NULL REFERENCES investigations(id),
    department_id     UUID REFERENCES departments(id),
    worker_id         UUID REFERENCES workers(id),
    channel           TEXT NOT NULL,
    recipient         TEXT NOT NULL,
    message           TEXT NOT NULL,
    evidence_type     TEXT NOT NULL DEFAULT 'external_comm',
    blocking          BOOLEAN NOT NULL DEFAULT false,
    expected_by       TIMESTAMPTZ,
    status            TEXT NOT NULL DEFAULT 'PENDING'
                      CHECK (status IN ('PENDING','ANSWERED','EXPIRED','REFUSED')),
    communication_log_id UUID REFERENCES communication_logs(id),
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    answered_at       TIMESTAMPTZ
);
CREATE INDEX outreach_inv_idx ON caspian_outreach(investigation_id, status);

-- ── Event store (doc 13 §13.3.11, non-partitioned) ─────────────────────
CREATE TABLE events (
    id               UUID PRIMARY KEY,
    organization_id  UUID NOT NULL,
    event_type       TEXT NOT NULL,
    event_category   TEXT NOT NULL,
    source           JSONB NOT NULL,
    payload          JSONB NOT NULL,
    causal_parent_id UUID,
    correlation_id   UUID NOT NULL,
    root_event_id    UUID NOT NULL,
    chain_depth      INT NOT NULL DEFAULT 0,
    sequence_number  BIGINT NOT NULL,
    occurred_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    recorded_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    schema_version   TEXT NOT NULL DEFAULT '1.0',
    UNIQUE (organization_id, sequence_number)
);
CREATE INDEX evt_correlation_idx ON events(correlation_id);
CREATE INDEX evt_org_seq_idx     ON events(organization_id, sequence_number);
CREATE INDEX evt_type_idx        ON events(event_type, occurred_at);
CREATE INDEX evt_org_id_idx      ON events(organization_id, id);

-- ── Projection bookkeeping (doc 16 §16.6.3) ────────────────────────────
CREATE TABLE projection_watermarks (
    projector_name  TEXT PRIMARY KEY,
    organization_id UUID,
    last_sequence   BIGINT NOT NULL,
    last_event_id   UUID NOT NULL,
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE projection_snapshots (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL,
    projector_name  TEXT NOT NULL,
    sequence_number BIGINT NOT NULL,
    state           JSONB NOT NULL,
    taken_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX snap_org_proj_idx ON projection_snapshots(organization_id, projector_name, sequence_number);

-- ── Notifications (addition) ───────────────────────────────────────────
CREATE TABLE notifications (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organizations(id),
    event_id        UUID,
    kind            TEXT NOT NULL,
    title           TEXT NOT NULL,
    body            TEXT NOT NULL,
    read_at         TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX notif_org_idx ON notifications(organization_id, created_at);

-- ── Knowledge graph (doc 14, Postgres adjacency — Neo4j later) ─────────
CREATE TABLE kg_claims (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id   UUID NOT NULL REFERENCES organizations(id),
    investigation_id  UUID REFERENCES investigations(id),
    department_id     UUID REFERENCES departments(id),
    claim_text        TEXT NOT NULL,
    confidence        NUMERIC(4,3) NOT NULL DEFAULT 0.5,
    status            TEXT NOT NULL DEFAULT 'proposed'
                      CHECK (status IN ('proposed','accepted','rejected','amended')),
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX kg_claim_org_inv_idx ON kg_claims(organization_id, investigation_id);

CREATE TABLE kg_edges (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id   UUID NOT NULL REFERENCES organizations(id),
    source_id         UUID NOT NULL,
    target_id         UUID NOT NULL,
    relation          TEXT NOT NULL CHECK (relation IN ('cites','challenges','supports','refutes')),
    weight            NUMERIC(4,3) NOT NULL DEFAULT 0.5,
    note              TEXT,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX kg_edge_org_idx ON kg_edges(organization_id, source_id);

CREATE TABLE kg_relationships (
    organization_id UUID NOT NULL REFERENCES organizations(id),
    from_worker_id UUID NOT NULL REFERENCES workers(id),
    to_worker_id   UUID NOT NULL REFERENCES workers(id),
    type           TEXT NOT NULL CHECK (type IN ('trust','affinity','conflict')),
    strength       NUMERIC(4,3) NOT NULL DEFAULT 0.5,
    note           TEXT,
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (organization_id, from_worker_id, to_worker_id, type)
);
