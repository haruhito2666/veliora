# 04 — Technical Architecture

> **Veliora is not a multi-agent platform.** It is an autonomous organization
> that investigates, debates, evolves, and communicates with the real world.
> This document defines the system architecture that makes the organization —
> not any individual model — the unit of intelligence.

| Field         | Value                                                       |
|---------------|-------------------------------------------------------------|
| Document      | `04-technical-architecture.md`                              |
| Role          | Master architecture doc; referenced by all other docs       |
| Audience      | Backend engineers, platform engineers, SRE, security review |
| Status        | Design — v0.1                                               |
| Depends on    | `SPEC.md`, `01-core-philosophy.md`                           |
| Referenced by | `05`–`30` (all downstream design docs)                      |

---

## 1. Purpose and Scope

This document specifies the **system architecture** of Veliora: the
components, their responsibilities, their boundaries, the communication
patterns between them, the technology stack, and the deployment topology. It
is the single source of truth for "what runs where and talks to what."

It does **not** define internal algorithms of each engine — those live in their
own docs (`06-memory-system.md`, `07-evolution-engine.md`, etc.). When this
doc and a downstream doc disagree on an engine's internals, the downstream
doc wins; when they disagree on system topology, this doc wins.

### 1.1 The One Agent Test (Architectural Consequence)

Every component below is justified against a single question:

> *Would this component still exist if Veliora had exactly one AI agent?*

If yes, the component is wrong. The architecture is built so that
**intelligence emerges from the organization**, not from a model. Concrete
consequences:

- There is **no "orchestrator LLM"** that plans alone. Planning is a
  *meeting* (`20-meeting-engine.md`) where departments negotiate.
- There is **no shared scratchpad**. Departments communicate through
  *messages and meetings* (`11-communication-engine.md`), forcing
  observable, criticizable communication.
- There is **no global "best answer"**. Answers survive *debate and vote*
  (`10-debate-engine.md`), not a decoder loop.
- There is **no static agent config**. Departments *hire, fire, promote*
  themselves (`07-evolution-engine.md`).
- There is **no single trust score**. Trust is *per-department, per-domain*
  (`08-trust-engine.md`).
- There is **no monolithic prompt**. Prompts are *templated per role and
  per ritual* (`17-prompt-architecture.md`).

A single agent needs none of this. An organization needs all of it.

---

## 2. High-Level System Diagram

```
                              +-----------------------------------------+
                              |            USER (Mobile App)            |
                              +--------------------+--------------------+
                                                   | HTTPS / WSS
                                                   v
                              +-----------------------------------------+
                              |        Mobile API Gateway (24)          |
                              |  REST + GraphQL + WebSocket fan-out     |
                              +----+------------------+----------------+
                                   |                  |
                   control plane   |                  |  live event stream
                                   v                  v
        +--------------------------+  +---------------+----------------+
        |    Organization Manager   |  |       Event Bus (16)          |
        |  (lifecycle, DNA, state)  |  |   NATS JetStream / Kafka     |
        +----+---------------------+  +---+----------------+-----------+
             |                            |                |
             | spawn / scale               | publish        | subscribe
             v                            v                v
        +----+---------------------+  +----+----------------+-----------+
        |     Worker Runtime (05)   |  |   Knowledge Graph (14)        |
        |  (one process per agent)  |  |   (entities, claims, evidence)|
        +----+---------------------+  +----+----------------+-----------+
             |                            |                |
             |  LLM calls, tools, Caspian  | read/write      |
             v                            v                |
        +----+---------------------+  +----+----------------+-----------+
        |   External LLM Providers  |  |   Memory System (06)         |
        |   (OpenAI, Anthropic...)  |  |   (institutional memory)     |
        +--------------------------+  +------------------------------+
```

The vertical spine — **Organization Manager → Worker Runtime → LLMs** — is
the control plane: it decides *who exists and who runs*. The horizontal spine
— **Event Bus → Knowledge Graph / Memory** — is the data plane: it carries
*what the organization knows and says*. Every engine hangs off both spines.

---

## 3. Component Overview

Fourteen first-class components. Each is a separately deployable service with
its own data store where appropriate. Components communicate **only** through
the Event Bus or through well-typed synchronous RPC; there is no shared
in-memory state.

### 3.1 Organization Manager

**Responsibility.** Owns the lifecycle of an organization: creation from a
template, DNA application (`21-organizational-dna.md`), department
topology, state machine transitions (`25-state-machine.md`), and graceful
shutdown. It is the *only* component permitted to create or destroy workers.

**Why it passes the One Agent Test.** A single agent has no lifecycle, no
departments, no topology to manage. The Organization Manager exists solely
because the unit of compute is an *organization*.

**Key APIs.**
- `CreateOrg(template, dna) -> org_id`
- `ApplyDNA(org_id, dna_patch)`
- `Transition(org_id, event) -> new_state`  (see `25-state-machine.md`)
- `SpawnDepartment(org_id, role)` / `RetireDepartment(dept_id)`
- `GetOrgSnapshot(org_id) -> OrgState`

**Persistence.** `orgs`, `departments`, `org_state` tables (see
`13-database-design.md`).

### 3.2 Investigation Engine

**Responsibility.** Orchestrates the flagship flow: receive a user goal,
convene a planning meeting, decompose into department tasks, drive evidence
collection, schedule debate, run peer review, hold a board vote, and emit the
final report. It is a *stateful workflow engine*, not an LLM loop.

**Why it passes the One Agent Test.** A single agent just answers. Only an
organization needs an investigation: decomposition across departments,
parallel evidence collection, cross-examination, and a board vote.

**Internal pipeline.** See `19-investigation-engine.md`. Summary stages:

```
INTAKE -> PLANNING_MEETING -> TASK_DISPATCH -> EVIDENCE_COLLECTION
       -> EVIDENCE_CHALLENGE -> DEBATE -> PEER_REVIEW
       -> BOARD_VOTE -> REPORT -> PUBLISHED
```

**Key APIs.**
- `StartInvestigation(org_id, goal) -> investigation_id`
- `GetInvestigation(id) -> InvestigationState`
- `SubscribeStream(id) -> Stream[InvestigationEvent]`

### 3.3 Meeting Engine

**Responsibility.** Schedules and runs meetings: agenda formation, turn
taking, time-boxed rounds, minute-taking, and resolution. Meetings are the
*only* mechanism by which departments make collective decisions. No
back-channel "let me ask the model" shortcut is permitted.

**Why it passes the One Agent Test.** A single agent does not meet with
itself. Meetings exist because conflicting departmental objectives must be
reconciled through structured communication.

**See.** `20-meeting-engine.md`.

### 3.4 Debate Engine

**Responsibility.** Runs the Society-of-Thought debate protocol: position
statements, rebuttals, cross-examination, evidence challenges, and weighted
voting. Produces a decision record with dissenting opinions preserved.

**Why it passes the One Agent Test.** A single agent has no one to disagree
with. Debate exists because departments hold *real conflicting objectives*
(Research wants experimentation, Finance wants savings).

**See.** `10-debate-engine.md`.

### 3.5 Memory System

**Responsibility.** Stores *institutional* memory: past investigations,
decisions, failures, strategies, internal policies, promotions, conflicts,
and the causal history of *why* something happened. Distinct from chat
history — this is organizational memory that survives agent restarts and
outlives any individual worker.

**Why it passes the One Agent Test.** A single agent has a context window.
Only an organization needs institutional memory that outlives its members.

**See.** `06-memory-system.md`.

### 3.6 Trust Engine

**Responsibility.** Maintains per-department, per-domain reputation scores
based on historical accuracy, evidence quality, and debate outcomes. Feeds
weighted voting in the Debate Engine and task routing in the Planner.

**Why it passes the One Agent Test.** A single agent has no peers to be
trusted *by*. Trust is inherently inter-departmental.

**See.** `08-trust-engine.md`.

### 3.7 Evolution Engine

**Responsibility.** Observes organizational performance and proposes
structural mutations: hire a new specialist, split an overloaded department,
merge two underused ones, rewrite a policy, amend the constitution. Proposals
are themselves passed through a constitutional vote before taking effect.

**Why it passes the One Agent Test.** A single agent does not restructure
itself into departments. Evolution exists because the organization is a
*living* entity that rewrites its own shape.

**See.** `07-evolution-engine.md`.

### 3.8 Constitution Engine

**Responsibility.** Stores and enforces the organization's constitution:
mission, principles, core values, ethics, and decision rules. Every
decision, debate, and evolution proposal is checked against the
constitution. Amendments require a supermajority vote.

**Why it passes the One Agent Test.** A single agent has no polity to be
governed by. A constitution presupposes a society.

**See.** `09-constitution-engine.md`.

### 3.9 Communication Engine

**Responsibility.** Internal messaging and meeting infrastructure:
inboxes, channels, threaded conversations, read receipts, and meeting
rooms. All inter-department communication flows through here — there is no
direct worker-to-worker RPC for content exchange.

**Why it passes the One Agent Test.** A single agent does not send messages
to itself. Observable, logged communication exists because decisions must be
auditable and criticizable by peers.

**See.** `11-communication-engine.md`.

### 3.10 Caspian Bridge

**Responsibility.** The organization's gateway to the *external world*.
Routes outbound messages from departments to real people over Email,
Telegram, Slack, Discord, WhatsApp, and every Caspian-supported channel.
Inbound replies are normalized, attributed, and fed back as evidence.

**Why it passes the One Agent Test.** A single agent could call an API. The
Caspian Bridge exists because *departments* reach out to different
external constituencies (Research emails professors, Marketing surveys
users, Legal emails lawyers) and the channel choice itself is an
organizational decision.

**See.** `12-caspian-integration.md`.

### 3.11 Knowledge Graph

**Responsibility.** The organization's structured beliefs: entities, claims,
evidence links, supporting/criticizing relations, confidence, and
provenance. Every piece of evidence collected during an investigation
becomes a node; every challenge becomes an edge. The final report is a
projection of the subgraph that survived peer review.

**Why it passes the One Agent Test.** A single agent holds beliefs in
weights. An organization must externalize beliefs into a shared,
criticizable structure — otherwise departments cannot challenge each
other's evidence.

**See.** `14-knowledge-graph.md`.

### 3.12 Event Bus

**Responsibility.** The nervous system. All inter-component communication
flows through it. Provides at-least-once delivery, ordered streams per
organization, replay for late joiners, and back-pressure. No component
calls another component's internals directly for async work.

**Why it passes the One Agent Test.** A single agent has no one to signal.
The bus exists because *many* independent departments must coordinate
without shared memory.

**See.** `16-event-system.md`.

### 3.13 Mobile API Gateway

**Responsibility.** The single public entry point for mobile clients.
Terminates TLS at the deployment edge, fans out REST/WebSocket requests, and
maintains WebSocket subscriptions for live organization status, meeting streams,
and investigation progress. The current alpha has no application-level
authentication; operators must place it on a trusted private network.

**Why it passes the One Agent Test.** A single agent needs a chat UI. An
organization needs a *dashboard* showing many departments working in
parallel — a fundamentally different client contract.

**See.** `22-mobile-ux.md`, `24-api-design.md`.

### 3.14 Worker Runtime

**Responsibility.** The execution sandbox for a single agent (a department
member). Each worker is an isolated process with: an LLM client, a tool
harness, an inbox polled from the Communication Engine, an event publisher
to the bus, and a memory client. Workers are stateless across restarts;
all durable state lives in Memory and the Knowledge Graph.

**Why it passes the One Agent Test.** A single agent *is* the worker. The
*runtime* — with its multi-process isolation, per-worker resource limits,
and independent failure domains — exists only because there are many
workers that must not crash each other.

**See.** `05-agent-architecture.md`.

---

## 4. Component Relationship Matrix

`P` = publishes events consumed by row. `S` = synchronous RPC caller of row.
`R` = reads/writes row's data.

```
                  OrgMgr  InvEng  MeetEng DebEng  Memory  Trust   EvolEng ConstEng CommEng Caspian KnowGr  EventBus MobileGW WorkerRT
OrgMgr            -       S       .       .       R       R       S       R       .       .       R       P/S      S        S
InvEng            S       -       S       S       R       R       .       R       R       .       R       P/S      .        S
MeetEng           .       S       -       S       R       R       .       R       R       .       R       P/S      .        S
DebEng            .       S       S       -       R       R       .       R       R       .       R       P/S      .        S
Memory            R       R       R       R       -       R       R       R       R       .       R       P/S      .        R
Trust             R       R       R       R       R       -       R       .       .       .       R       P/S      .        R
EvolEng           S       .       .       .       R       R       -       S       R       .       R       P/S      .        S
ConstEng          R       R       R       R       R       .       S       -       .       .       R       P/S      .        R
CommEng           .       R       R       R       R       .       .       .       -       S       .       P/S      S        R
Caspian           .       .       .       .       .       .       .       .       S       -       .       P/S      .        R
KnowGr            R       R       R       R       R       R       R       R       .       .       -       P/S      .        R
EventBus          S/R     S/R     S/R     S/R     S/R     S/R     S/R     S/R     S/R     S/R     S/R     -        S/R      S/R
MobileGW          S       R       R       R       R       R       .       R       R       .       R       S/R      -        .
WorkerRT          S       S       S       S       R       R       S       R       R       R       R       S/R      .        -
```

Read across a row to see what that component talks to. The Event Bus column
is dense because *every* component is both publisher and subscriber — that is
the architectural invariant.

---

## 5. Technology Stack Recommendations

Choices are justified against the One Agent Test where relevant. Versions
are illustrative pin targets, not commitments.

### 5.1 Core Language

**Recommendation: Go 1.23+ for control-plane services; Python 3.12 for
worker runtime and LLM-heavy engines.**

| Concern                    | Choice        | Rationale                                                  |
|----------------------------|---------------|------------------------------------------------------------|
| Concurrency model          | Go goroutines | Tens of thousands of concurrent orgs/meetings/investigations |
| Memory footprint           | Go            | Workers are many; small binaries keep scheduler cheap        |
| LLM ecosystem              | Python        | Tooling, evals, prompt libs, and tracers are Python-first  |
| Type safety                | Go + Pyright  | Cross-service contracts enforced via generated stubs        |

A single agent would be fine in one language. The split exists because the
*control plane* (many small long-lived services) and the *worker plane*
(many short-lived LLM-bound processes) have opposite optimization profiles.

### 5.2 Web Framework

| Layer            | Choice                  | Why                                          |
|------------------|-------------------------|----------------------------------------------|
| Public API       | `chi` + `graphql-go`    | Minimal, fast, composable middleware         |
| Worker RPC       | `grpc-go`               | Bidirectional streaming for live meetings    |
| Mobile realtime  | `nhooyr/websocket`      | Per-connection goroutines, context-aware     |

### 5.3 Database

**Recommendation: PostgreSQL 16 as the primary OLTP store; separate
clusters per concern.**

| Store                | Tech              | Holds                                                |
|----------------------|-------------------|------------------------------------------------------|
| Operational DB       | PostgreSQL 16     | orgs, departments, investigations, meetings, votes  |
| Memory DB           | PostgreSQL 16     | institutional memory (separate cluster, separate backup cadence) |
| Knowledge Graph     | Neo4j 5 (or AGE)  | entities, claims, evidence, challenge edges          |
| Event Store         | NATS JetStream    | durable event logs per org                           |
| Blob Store          | S3-compatible     | reports, attachments, Caspian transcripts             |
| Cache               | Redis 7           | hot org state, presence, rate limits                 |

A single agent would need one DB. The split — operational vs. memory vs.
graph vs. event store — exists because institutional memory, structured
beliefs, and replay-able events have different lifetimes and access patterns
than operational state.

### 5.4 Message Queue / Event Bus

**Recommendation: NATS JetStream** as the primary bus, with Kafka as a
drop-in alternative for deployments that already operate Kafka.

| Requirement            | NATS JetStream         |
|------------------------|------------------------|
| Per-org ordered stream | `JS.Stream(org_id.*)`  |
| At-least-once delivery | ack + redelivery       |
| Replay for late join   | `JS.Subscribe(replay)` |
| Back-pressure           | max-inflight + ack      |
| Subject addressing     | `org.<id>.<topic>.<verb>` |

A single agent has no events to publish. The bus exists because many
independent departments must coordinate without shared memory.

### 5.5 Real-Time Layer

**Recommendation: WebSocket fan-out from the Mobile API Gateway, fed by
Event Bus subscriptions.** No long-polling. The mobile client opens one WSS
connection and receives multiplexed frames tagged by `org_id` and `topic`.

```
[Worker] --publish--> [EventBus] --subscribe--> [MobileGW] --wss--> [App]
```

### 5.6 LLM Layer

**Recommendation: provider-agnostic via a thin internal client.** No
provider SDK is imported outside the `llmclient` package. Workers call
`llmclient.Complete(ctx, req)`, not `openai.Chat`. This keeps the
organization free to route per-department (Research → high-reasoning model,
Marketing → fast cheap model) and to swap providers without touching
engine code.

### 5.7 Observability

| Signal     | Tool                       |
|------------|----------------------------|
| Metrics    | Prometheus + Grafana       |
| Traces     | OpenTelemetry → Tempo      |
| Logs       | structured JSON → Loki     |
| Audit      | append-only audit log → S3 |

Every event on the bus carries a `trace_id` and `org_id`; every worker
invocation spans under the triggering event's trace.

---

## 6. Deployment Topology

### 6.1 Logical Topology

```
                       +-----------------------+
                       |    Cloud LB / CDN      |
                       +-----------+-----------+
                                   |
                       +-----------+-----------+
                       |  Mobile API Gateway xN |
                       +-----------+-----------+
                                   |
              +--------------------+--------------------+
              |                                         |
   +----------+----------+                  +-------------+----------+
   |   Control Plane     |                  |      Data Plane         |
   |  (Go services)      |                  |   (Go + Python)         |
   |                     |                  |                         |
   |  Organization Mgr   |                  |  Event Bus (NATS) x3     |
   |  Investigation Eng  |                  |  Knowledge Graph (Neo4j)|
   |  Meeting Engine     |                  |  Memory DB (Postgres)   |
   |  Debate Engine      |                  |  Op DB (Postgres)        |
   |  Trust Engine       |                  |  Redis cache            |
   |  Evolution Engine   |                  |  Blob store             |
   |  Constitution Eng   |                  +-------------+----------+
   |  Communication Eng  |                                |
   |  Caspian Bridge     |                                |
   +----------+----------+                                |
              |                                           |
              |  spawn workers                             |
              v                                           |
   +----------+----------+      LLM calls      +-----------+----------+
   |   Worker Pool       |-------------------->|   LLM Providers        |
   |  (Python, k8s pods) |                      |  (OpenAI/Anthropic/..) |
   +---------------------+                      +------------------------+
```

### 6.2 Physical Topology (Kubernetes)

| Component            | Deployment            | Scaling                |
|----------------------|------------------------|------------------------|
| Mobile API Gateway   | Deployment + HPA       | by CPU + WSS conn count|
| Control-plane svc   | Deployment + HPA       | by RPS                 |
| Worker runtime       | Deployment + KEDA      | by pending-investigations queue depth |
| Event Bus            | StatefulSet (3-node cluster) | fixed 3 for quorum |
| Postgres (Op)        | StatefulSet + HA       | read replicas          |
| Postgres (Memory)    | StatefulSet + HA       | separate failure domain|
| Neo4j                | StatefulSet (cluster)  | causal cluster         |
| Redis                | StatefulSet + Sentinel | per-org sharding       |

Workers are the only autoscaled-by-queue-depth component. Everything else
scales by request rate. This isolates LLM cost (which lives in the worker
pool) from control-plane cost.

### 6.3 Failure Domains

- **Worker crash.** Organization Manager respawns the worker; the worker
  rehydrates from Memory + Event Bus replay. No investigation is lost.
- **Control-plane service crash.** Kubernetes restarts the pod; state is in
  Postgres; in-flight events are redelivered by JetStream.
- **Event Bus node loss.** 3-node Raft quorum tolerates one loss.
- **Memory DB loss.** Restored from PITR; institutional memory is the most
  backed-up store in the system (hourly snapshots + WAL archiving).

A single agent crashing loses a context window. An organization crashing
loses nothing durable, because every department's state is externalized.

---

## 7. Data Flow Diagrams

### 7.1 Control Flow — User Goal to Investigation

```
[App] --POST /orgs/{id}/investigations--> [MobileGW]
                                              |
                                              v
                                    [Organization Manager]
                                              | (state: IDLE -> INVESTIGATING)
                                              v
                                    [Investigation Engine]
                                              | (create investigation, stage=INTAKE)
                                              v
                                    [Meeting Engine]  (planning meeting)
                                              |  (departments propose sub-tasks)
                                              v
                                    [Investigation Engine] (stage=TASK_DISPATCH)
                                              |  (one sub-task per dept)
                                              v
                                    [Worker Runtime]  (N parallel workers)
                                              |  (each dept collects evidence)
                                              v
                                    [Knowledge Graph]  (claims + evidence nodes)
                                              |
                                              v
                                    [Debate Engine]  (evidence challenge rounds)
                                              |  (challenges -> edges)
                                              v
                                    [Meeting Engine]  (peer review meeting)
                                              |
                                              v
                                    [Debate Engine]  (board vote, weighted by Trust)
                                              |  (decision record + dissent)
                                              v
                                    [Investigation Engine] (stage=REPORT)
                                              |
                                              v
                                    [MobileGW] --WSS--> [App]  (final report)
```

### 7.2 Data Flow — Evidence to Belief

```
[Worker] --Claim--> [Knowledge Graph]
                        |  (node: Claim, edge: cites Evidence)
                        v
[Worker (peer)] --Challenge--> [Knowledge Graph]
                        |  (edge: challenges Claim)
                        v
[Debate Engine] --Vote--> [Knowledge Graph]
                        |  (edge: supports/refutes with weight)
                        v
[Memory System] --record--> (institutional memory: "on 2026-08-05,
                              Research's claim X survived peer review by 4-1")
```

### 7.3 Data Flow — External Communication via Caspian

```
[Worker: Marketing] --msg--> [Communication Engine]
                                 |  (outbox pattern)
                                 v
                            [Caspian Bridge]
                                 |  (channel: Telegram)
                                 v
                            [Real Person]
                                 |  (reply)
                                 v
                            [Caspian Bridge]
                                 |  (normalized inbound)
                                 v
                            [Communication Engine] --deliver--> [Worker: Marketing]
                                 |
                                 v
                            [Knowledge Graph]  (evidence node, provenance=Telegram)
```

---

## 8. Inter-Service Communication Patterns

### 8.1 Three Permitted Patterns

1. **Event publication** (async, fire-and-forget, at-least-once). The
   default for anything that does not need an immediate return value.
2. **Request/response RPC** (sync, gRPC, deadline-bounded). Used only
   when the caller cannot proceed without the answer — e.g., Organization
   Manager asking Constitution Engine "is this proposal allowed?"
3. **Stream subscription** (async, long-lived, JetStream replay). Used by
   the Mobile Gateway for live updates and by workers for inbox delivery.

### 8.2 Forbidden Patterns

- **Direct database sharing between services.** Each service owns its
  tables; cross-service reads go through the owning service's API.
- **Worker-to-worker direct calls.** Workers communicate only through the
  Communication Engine. This keeps all inter-department talk observable and
  auditable.
- **Shared in-memory state.** None. The Event Bus is the only shared state.

### 8.3 Subject Naming

```
org.<org_id>.investigation.<inv_id>.stage          # Investigation Engine
org.<org_id>.meeting.<meeting_id>.turn             # Meeting Engine
org.<org_id>.debate.<debate_id>.round              # Debate Engine
org.<org_id>.comm.<channel_id>.message             # Communication Engine
org.<org_id>.memory.<event_type>                   # Memory System
org.<org_id>.trust.<dept_id>.update                # Trust Engine
org.<org_id>.evolution.<proposal_id>.stage         # Evolution Engine
org.<org_id>.caspian.<channel>.<direction>          # Caspian Bridge
org.<org_id>.kg.claim.<claim_id>                   # Knowledge Graph
```

Subjects are hierarchical so subscribers can wildcard (`org.<id>.meeting.*`)
or pin to a specific stream. See `16-event-system.md`.

### 8.4 Delivery Guarantees

| Pattern            | Guarantee       | Idempotency key            |
|--------------------|-----------------|----------------------------|
| Event publication  | at-least-once   | `event_id` (UUIDv7)        |
| RPC                | at-most-once    | deadline + retry-backoff   |
| Stream             | at-least-once   | `event_id` dedup on consumer|

All consumers must be idempotent. The `event_id` is the dedup key; the
`trace_id` is the correlation key.

---

## 9. End-to-End Investigation Flow

This section traces a single user goal through every component, end to end.
It is the canonical reference for "how does a request actually move."

**Goal.** User types: *"Should I build an AI healthcare startup?"*

### Step 1 — Intake (MobileGW → OrgMgr → InvEng)
1. App `POST /orgs/{id}/investigations { goal }` to MobileGW.
2. MobileGW calls `OrgMgr.Transition(org_id, GOAL_RECEIVED)`.
3. OrgMgr validates the org is `IDLE` and not at investigation capacity;
   transitions to `INVESTIGATING` (see `25-state-machine.md`).
4. MobileGW calls `InvEng.StartInvestigation(org_id, goal)`.
5. InvEng creates an investigation record, stage `INTAKE`, publishes
   `org.<id>.investigation.<inv_id>.stage = INTAKE`.
6. MobileGW opens a WSS subscription on
   `org.<id>.investigation.<inv_id>.*` and returns `investigation_id` to
   the app. The app navigates to the live investigation screen.

### Step 2 — Planning Meeting (InvEng → MeetEng)
7. InvEng requests a planning meeting from MeetEng with the CEO and all
   department leads as attendees.
8. MeetEng forms an agenda from the goal, runs a time-boxed round in which
   each department proposes the sub-tasks it *believes* it should own.
   Departments *negotiate* — Engineering may argue a sub-task belongs to
   Research; Research may push back. This is not roleplay; the
   Constitution Engine enforces that each department advocates for its
   constitutional mandate (see `09-constitution-engine.md`).
9. MeetEng emits minutes and a task allocation. InvEng advances to
   `TASK_DISPATCH`.

### Step 3 — Task Dispatch (InvEng → WorkerRT)
10. For each allocated sub-task, InvEng publishes a `task.assigned` event
    on the bus addressed to the owning department.
11. Worker Runtime picks up the task for the department's current worker;
    if no worker is alive, OrgMgr spawns one. Each worker now runs
    independently and in parallel.

### Step 4 — Evidence Collection (Workers → KG → Memory)
12. Each worker collects evidence using its tools: Research reads papers
    and queries the Knowledge Graph; Marketing surveys users via Caspian
    (Telegram/Slack); Engineering evaluates architectures; Finance builds
    a forecast; Legal queries regulation databases.
13. Every collected artifact is written as a `Claim` node in the Knowledge
    Graph with a `cites` edge to its `Evidence` node. Provenance (source,
    channel, timestamp) is mandatory.
14. Significant findings are mirrored to Memory as institutional events so
    they survive even if the investigation is later discarded.

### Step 5 — Evidence Challenge (DebEng)
15. When a department publishes a claim, peer departments are notified.
    Engineering may challenge Research's claim ("sample size too small")
    by publishing a `challenges` edge to the KG with a justification.
16. The challenged department must respond: accept, rebut with new
    evidence, or revise. The Debate Engine mediates the exchange.
17. The Trust Engine observes the exchange and updates reputation: a
    department whose claim survives gains trust; one whose claim is
    refuted loses trust (see `08-trust-engine.md`).

### Step 6 — Debate and Peer Review (DebEng → MeetEng)
18. Once evidence collection converges (a quorum of departments signal
    `done` or a soft deadline fires), InvEng advances to `DEBATE`.
19. The Debate Engine runs Society-of-Thought rounds: position statements,
    rebuttals, cross-examination. Votes are weighted by the Trust Engine.
20. A peer-review meeting is convened to ratify the surviving positions.
    Dissent is preserved in the decision record, not erased.

### Step 7 — Board Vote (MeetEng → DebEng)
21. A board meeting is convened. The CEO presents the synthesized
    recommendation; department leads vote, weighted by trust in the
    relevant domains.
22. The Constitution Engine verifies the vote satisfies the
    organization's decision rules (quorum, supermajority for irreversible
    actions, etc.).

### Step 8 — Report (InvEng → MobileGW → App)
23. InvEng assembles the final report from the surviving Knowledge Graph
    subgraph and the debate record:
    - Executive Summary
    - Final Recommendation
    - Confidence Score (derived from trust-weighted vote margin)
    - Dissenting Opinions
    - Supporting Evidence (KG subgraph projection)
    - Risks / Opportunities
    - Suggested Next Steps
    - Full Organizational Discussion (meeting transcripts)
24. InvEng publishes `stage = REPORT`; the report is stored in the blob
    store and indexed in Memory.
25. MobileGW forwards the report frame over the open WSS connection; the
    app renders it. A push notification is sent if the app is backgrounded.

### Step 9 — Learning (Memory → Evolution)
26. The Memory System records the outcome, the dissent, and the
    per-department performance. The Trust Engine finalizes reputation
    deltas.
27. The Evolution Engine observes the result. If a department repeatedly
    underperformed, it may propose a structural change (split, retrain,
    hire). The proposal goes through a constitutional vote before taking
    effect — the organization evolves itself, without user intervention.

---

## 10. System Boundaries

### 10.1 Trust Boundary

```
[Untrusted]   External users, Caspian correspondents, LLM providers
[Boundary]    Mobile API Gateway (TLS at deployment edge, schema validation, trusted-network boundary)
[Semi-trusted] Workers (sandboxed, no direct network egress except LLM + Caspian)
[Trusted]     Control-plane services, Event Bus, databases
```

Workers are *semi-trusted*: they run LLM-generated tool calls, so they are
sandboxed with no direct egress except to the LLM provider and the Caspian
Bridge. All other network paths are blocked at the network policy layer.

### 10.2 Responsibility Boundaries

| Component          | Owns                          | Does NOT own                |
|--------------------|-------------------------------|-----------------------------|
| Organization Mgr   | org lifecycle, state          | investigation logic         |
| Investigation Eng  | investigation workflow        | org lifecycle               |
| Meeting Engine     | meeting protocol              | debate logic                |
| Debate Engine      | debate + vote                 | meeting scheduling          |
| Memory System      | institutional memory          | operational state           |
| Trust Engine       | reputation scores             | vote decisions              |
| Evolution Engine   | structural proposals          | applying them (ConstEng does)|
| Constitution Eng   | rule enforcement              | proposing changes (EvolEng) |
| Communication Eng  | internal messaging            | external messaging (Caspian) |
| Caspian Bridge     | external messaging            | internal messaging          |
| Knowledge Graph    | beliefs + evidence            | memory narrative            |
| Event Bus          | message delivery              | message semantics           |
| Mobile API Gateway | client contract              | business logic              |
| Worker Runtime     | agent execution               | durable state               |

The "Does NOT own" column is as important as the "Owns" column — it is what
prevents components from accreting responsibilities that belong elsewhere.

---

## 11. Cross-References

| Topic                              | Document                       |
|------------------------------------|--------------------------------|
| Worker/agent internals             | `05-agent-architecture.md`     |
| Institutional memory               | `06-memory-system.md`         |
| Self-modification                  | `07-evolution-engine.md`       |
| Reputation and weighted trust     | `08-trust-engine.md`          |
| Constitutional governance          | `09-constitution-engine.md`   |
| Society-of-Thought debate          | `10-debate-engine.md`         |
| Internal messaging and meetings    | `11-communication-engine.md`  |
| External communication (Caspian)   | `12-caspian-integration.md`   |
| Data model and persistence         | `13-database-design.md`       |
| Knowledge representation           | `14-knowledge-graph.md`       |
| Repo layout                        | `15-folder-structure.md`     |
| Event-driven design                | `16-event-system.md`          |
| Prompt templates and orchestration | `17-prompt-architecture.md`   |
| Investigation planning              | `18-planner-design.md`        |
| Investigation orchestration       | `19-investigation-engine.md`   |
| Meeting scheduling and execution  | `20-meeting-engine.md`        |
| DNA parameter model                | `21-organizational-dna.md`    |
| Mobile app design                   | `22-mobile-ux.md`            |
| End-to-end user flows              | `23-ui-ux-flow.md`            |
| REST/GraphQL/WebSocket surface     | `24-api-design.md`            |
| Organization and investigation FSMs | `25-state-machine.md`        |
| Security model                     | `26-security.md`              |
| Horizontal/vertical scaling         | `27-scaling-strategy.md`      |
| Hackathon MVP scope                | `28-mvp-hackathon.md`         |
| Post-MVP roadmap                   | `29-future-roadmap.md`        |

---

## 12. Open Architectural Questions

These are deferred to downstream docs or to a future revision of this one.

1. **Multi-tenant isolation.** Should each user's organizations share a
   worker pool (cheaper) or run in dedicated namespaces (safer)? See
   `26-security.md`, `27-scaling-strategy.md`.
2. **Memory DB colocation.** Whether institutional memory deserves its own
   physical Postgres cluster or can share the operational cluster with
   table-level encryption. See `06-memory-system.md`,
   `13-database-design.md`.
3. **Knowledge Graph backend.** Neo4j vs. Postgres+AGE vs. a custom
   layered store. Driven by challenge-edge traversal patterns in
   `14-knowledge-graph.md`.
4. **Cross-organization meta-society.** The SPEC's contribution #10
   (multiple independent organizations + a Meta Society) implies
   inter-org event flows not yet specified here. Deferred to
   `29-future-roadmap.md`.
5. **Worker LLM routing policy.** Per-department model selection is
   architecturally supported but the policy (which department gets which
   model tier) is an org-DNA decision. See `21-organizational-dna.md`.

---

## 13. Target Architecture Evolution: The Sofuu In-Process Substrate

In accordance with **`docs/34-sofuu-native-migration-plan.md`**, the Veliora
backend architecture is transitioning from the multi-daemon microservice
cluster (15 Go services + NATS + PostgreSQL/pgvector + Redis + Python workers)
to a **unified in-process architecture hosted on the Sofuu AI-native runtime**:

```
+------------------------------------------------------------------------+
|                          VELIORA CLIENT APP                            |
|               (Native Android Compose / Web Dashboard)                 |
+-----------------------------------+------------------------------------+
                                    | HTTP / WebSockets (:8080)
                                    | (or direct JNI/C-ABI in-memory)
                                    v
+------------------------------------------------------------------------+
|               SOFUU STANDALONE RUNTIME PROCESS (< 5 MB)                |
|                                                                        |
|  • HTTP & Realtime Gateway     (sofuu.http.createServer, WebSockets)  |
|  • In-Process Event Bus        (Lightweight durable FIFO task queue)   |
|  • Multi-Agent Pipeline        (sofuu.agent 7-stage state machine)     |
|  • Continuous Cognitive Memory (sofuu.memory CMA v2 / QTSQ vectors)    |
|  • In-Process Fast Storage     (sofuu.storage zero-IPC KV database)    |
|  • Hardware Local Inference    (sofuu.ai Metal/NEON tensor execution)  |
+------------------------------------------------------------------------+
```

**Key Architectural Benefits:**
- **Zero External Daemons:** Eliminates Docker, PostgreSQL, NATS, and Redis.
- **Extreme Footprint Reduction:** Idle RAM drops from ~3.2 GB to $< 65\text{ MB}$; cold startup drops from 25s to $< 4\text{ ms}$.
- **True Mobile Independence:** Can run directly on-device inside the Android app via `libsofuu.so` completely offline with zero server dependencies.

See **`docs/34-sofuu-native-migration-plan.md`** for the complete phase-by-phase implementation specifications and component mapping.

---

> **Architectural invariant.** If a proposed change would let a single
> agent reproduce the system's behavior without the Event Bus, the
> Knowledge Graph, the Meeting Engine, or the Debate Engine, that change
> violates the One Agent Test and must be rejected. The organization is
> the intelligence; the architecture exists to enforce that.
