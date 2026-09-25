# 27 — Scaling Strategy

> How Veliora scales from one organization to thousands.

Veliora is not a chatbot with a few tool calls. A single user prompt can
spawn an organization of dozens of workers, each running multiple
investigations, each investigation fanning out into meetings, debates,
evidence collection, and external Caspian conversations. The unit of work
is the **LLM call**, and the unit of intelligence is the **organization**.
Scaling Veliora is therefore fundamentally different from scaling a
single-agent system: we are scaling an *institution*, with its memory,
trust, constitution, and real-time observable behavior.

This document defines the scaling dimensions, the per-dimension strategy,
and concrete infrastructure milestones from 1 to 1000 organizations.

---

## 1. The "One Agent" Test for Scaling

Every scaling concern below exists **because this is an organization**.
Scaling a single AI is mostly about throughput and context length. Scaling
Veliora is about coordinating thousands of socially reasoning workers,
keeping their shared institutional memory coherent, and keeping the live
experience responsive while the org thinks.

| Scaling concern             | Single agent? | Why it is org-specific |
|-----------------------------|---------------|------------------------|
| Thousands of concurrent workers | No        | A single agent is one worker |
| Token budgets per department | No           | Departments only exist in an org |
| Inter-investigation resource sharing | No   | Requires multiple investigations |
| Knowledge graph at org scale | No           | Org memory, not chat history |
| WebSocket fan-out of org activity | No    | The "live org" is the product |
| Org isolation               | No            | Multi-org is a core feature |

---

## 2. Scaling Dimensions

```
                   VELIORA SCALING DIMENSIONS

   Users ──┬── Organizations ──┬── Investigations ──┬── Workers (LLM calls)
           │                   │                   │
           │                   ├── Departments ─────┤
           │                   │                   │
           │                   └── Meetings ───────┤
           │                                       │
           ├── Knowledge Graph ─────────────────────┤
           │                                       │
           └── Real-time layer (WebSocket fan-out) ─┘

   Each dimension scales independently, but they share:
     • a distributed task queue (work dispatch)
     • a shared state database (single source of truth)
     • a token budget authority (cost control)
```

The design rule: **workers are stateless and disposable; state lives in the
database.** This makes every dimension horizontally scalable.

---

## 3. Worker Scaling

### 3.1 A worker is an LLM call

A worker is not a long-running process. It is a **single LLM call** (or a
short chain of calls) that reads its context from the database, produces a
proposal, writes the result back, and exits. This is the single most
important scaling decision in the system.

```
   Task (from queue) ──▶ Load context from DB ──▶ LLM call ──▶ Write result ──▶ Exit
                                                    │
                                                    └─▶ billed to a token budget
```

Because workers are stateless:

- Any worker task can run on any executor node.
- A node crash loses only in-flight tasks, which the queue redelivers.
- We can scale workers by adding executor nodes, with no sharding logic.
- We can put a hard cap on concurrency per node to control LLM spend.

### 3.2 Concurrency management

The system targets **thousands of concurrent workers across all
investigations**. Concurrency is managed at three layers:

```
   ┌─────────────────────────────────────────────────────┐
   │  GLOBAL EXECUTOR POOL                                │
   │  ┌──────────┐ ┌──────────┐ ┌──────────┐            │
   │  │ node 1   │ │ node 2   │ │ node N   │  ...       │
   │  │ 64 slots │ │ 64 slots │ │ 64 slots │            │
   │  └──────────┘ └──────────┘ └──────────┘            │
   └──────────────────────┬──────────────────────────────┘
                          │ pull
                          ▼
   ┌─────────────────────────────────────────────────────┐
   │  DISTRIBUTED TASK QUEUE (per-org, per-investigation) │
   │  ┌─────┐ ┌─────┐ ┌─────┐ ┌─────┐ ┌─────┐           │
   │  │ org │ │ org │ │ inv  │ │ inv  │ │ mtg │  ...   │
   │  │ A   │ │ B   │ │ 17   │ │ 23   │ │ 9  │         │
   │  └─────┘ └─────┘ └─────┘ └─────┘ └─────┘           │
   └─────────────────────────────────────────────────────┘
```

- **Slot count per node** is derived from the LLM provider's max concurrent
  requests per key and the node's memory. Default 64.
- **Fair-share across orgs.** The queue is partitioned by `org_id`, and the
  executor pool pulls round-robin across org partitions so one busy org
  cannot starve others.
- **Backpressure.** If the queue depth for an investigation exceeds a
threshold, the Planner (see `18-planner-design.md`) is told to slow down
task emission rather than pile up work.

### 3.3 LLM call batching and prioritization

Not all workers are equally urgent. The queue assigns each task a
**priority class**:

| Priority | Class       | Examples                              | Target latency |
|----------|-------------|---------------------------------------|----------------|
| P0       | Real-time   | CEO coordination, meeting turn         | < 2s           |
| P1       | Interactive | User-visible investigation step        | < 10s          |
| P2       | Background  | Evidence corroboration, memory compaction | < 60s       |
| P3       | Maintenance | Trust decay, audit rollup, graph reindex | best-effort  |

Within a priority class, tasks are FIFO. Across classes, executors strictly
prefer higher priority. P0 tasks can preempt by being pulled first on any
free slot.

**Batching** is applied where the LLM provider supports it and the task
shape allows it — for example, several independent evidence-verification
sub-checks from the same investigation can be packed into a single batched
request to the provider, cutting per-call overhead. Batching is never used
for P0 or for tasks that must produce distinct proposals, because batching
erodes the per-worker accountability that the Trust Engine (see
`08-trust-engine.md`) depends on.

### 3.4 Cost management — token budgets

Every LLM call is billed to a budget. Budgets are hierarchical and enforced
**before** the call is made; a worker whose budget is exhausted is not
dispatched.

```
   ORG BUDGET (per billing period)
   ├── per-investigation budget   (allocated at investigation start)
   │   ├── per-department budget   (split by the Planner)
   │   │   └── per-worker budget   (per task, hard cap)
   │   └── per-meeting budget      (turn cap)
   └── per-external-comms budget   (Caspian sends)
```

**Enforcement (illustrative):**

```python
def reserve_tokens(ctx: WorkerCtx, est_tokens: int) -> Reservation:
    budgets = [ctx.worker_budget, ctx.dept_budget,
               ctx.investigation_budget, ctx.org_budget]
    for b in budgets:
        if b.remaining() < est_tokens:
            return Reservation(denied=True, reason=b.name)
    for b in budgets:
        b.debit(est_tokens)          # optimistic
    return Reservation(denied=False, tokens=est_tokens)

# After the call, reconcile actual usage:
def reconcile(res: Reservation, actual_tokens: int):
    delta = actual_tokens - res.tokens
    if delta > 0:
        # over-budget; recorded, may trigger a Planner slowdown
        audit.overrun(res, delta)
    else:
        # refund the unused portion
        for b in res.budgets:
            b.refund(-delta)
```

**Hard caps prevent runaway.** A single worker task has a hard token cap
(default 8k input + 2k output). A single investigation has a cap (default
500k tokens). An org has a cap per billing period. Hitting a cap pauses new
tasks in that scope and surfaces a budget event to the user — it never
silently spends unbounded money.

**Cost-aware planning.** The Planner (see `18-planner-design.md`) receives
the remaining investigation budget as an input and chooses cheaper plans
(fewer workers, shorter contexts, smaller models) as the budget depletes.
Model selection is itself a scaling lever: P3 maintenance tasks can run on
a smaller, cheaper model, while P0 coordination runs on the flagship model.

---

## 4. Horizontal Scaling

### 4.1 Stateless worker execution

As above: workers carry no in-memory state between calls. All state —
worker identity, role, trust score, current task, partial investigation
context — is loaded from the database at the start of a task and written
back at the end. This means:

- Executor nodes are interchangeable and ephemeral.
- Autoscaling is simple: add nodes when queue depth grows, remove when idle.
- There is no session affinity; any node can run any worker's next task.
- Rolling deploys do not drain workers; in-flight tasks finish, new tasks
  pick up new code on the next pull.

### 4.2 Distributed task queue

The queue is the spine of horizontal scaling. Requirements:

- **Persistent** — tasks survive node failure.
- **Partitioned by org** — fair-share and isolation.
- **Priority-aware** — P0 preemption.
- **At-least-once delivery** — a task that fails is retried; workers must
  be idempotent on retry (proposals carry an idempotency key).
- **Dead-letter queue** — tasks that fail N times go to a DLQ surfaced to
  the user and the Constitution Guard.

Implementation choice is a managed queue (e.g., SQS + DynamoDB streams, or
Redis Streams for lower-latency P0) rather than a custom broker. The
interface is abstracted so the backend is swappable.

### 4.3 Shared state in the database

All durable state lives in a shared, horizontally scalable database. The
schema (see `13-database-design.md`) is designed for this:

- **Hot path** (task dispatch, worker status, meeting turns): a low-latency
  row store (e.g., DynamoDB or Postgres with read replicas).
- **Cold path** (audit log, completed investigations, archived memory): an
  append-only store with tiered storage.
- **Graph path** (knowledge graph): a dedicated graph store (see §7).

The database is the **single source of truth**. Executors never cache
investigation state in memory across tasks; they re-read. This trades a
little latency for correctness under horizontal scale and crash recovery.

---

## 5. Organization Scaling

### 5.1 Multiple organizations per user

A user can run many organizations — a Startup Society, an Investor
Society, a Personal Decision Council — each with its own constitution,
DNA, trust ledger, memory, and Caspian credentials. The user-facing model
is "switch org" in the mobile app (see `22-mobile-ux.md`).

### 5.2 Org isolation

Orgs are **hard-isolated tenants**, not soft-scoped namespaces. See
`26-security.md` §17 for the full isolation controls. From a scaling
perspective:

- **Row-level security** at the DB layer enforces `org_id` filtering, so a
  bug in one org's code path cannot leak another org's data.
- **Per-org rate limits and token budgets** are independent; one org's
  runaway investigation cannot exhaust the shared pool.
- **Per-org Caspian credentials** are vault-scoped, so one org's channel
  identity cannot be used by another.
- **Per-org queue partitions** give fair-share and blast-radius
  containment: a hot org slows itself down, not the platform.

### 5.3 Shared vs. per-org infrastructure

Some infrastructure is intentionally **shared** for efficiency, with
isolation enforced above it:

| Component            | Shared? | Isolation mechanism |
|----------------------|---------|--------------------|
| Executor pool        | Yes     | Fair-share partitions, no cross-org context |
| Task queue           | Yes     | Per-org partitions |
| Primary DB           | Yes     | Row-level security by `org_id` |
| Knowledge graph store | Yes    | Per-org graph namespace |
| Caspian Gateway      | Yes     | Per-org credential scoping |
| Audit log            | Yes     | Per-org rows, per-org encryption keys |
| LLM provider keys    | Yes (pool) | Per-org token budget accounting |

---

## 6. Investigation Scaling

### 6.1 Parallel investigations

An org can run multiple investigations concurrently — for example, one on
"should we build an AI healthcare startup" and one on "should we hire a
second engineer." Each investigation is an independent state machine (see
`25-state-machine.md`) with its own task subtree in the queue.

```
   ORG
   ├── Investigation #1 ──┬── Dept: Research ──┬── Worker tasks
   │                      │                   └── Worker tasks
   │                      ├── Dept: Finance ──┘
   │                      └── Meeting: board review
   ├── Investigation #2 ──┬── Dept: Engineering ─ ...
   │                      └── ...
   └── Investigation #3 (queued, waiting on budget)
```

**Per-investigation concurrency cap.** To prevent one investigation from
monopolizing the org's executor share, each investigation has a max
concurrent workers (default 16). The Planner cannot emit beyond this; it
must sequence.

### 6.2 Inter-investigation resource sharing

Investigations within the same org share institutional memory, the
knowledge graph, and the trust ledger. This sharing is the org's
**institutional memory** (see `06-memory-system.md`) and is what makes the
org smarter over time — a uniquely organizational property.

**Shared resources:**

- **Memory** — conclusions, failures, and strategies from past
  investigations are available (read-only) to new investigations.
- **Knowledge graph** — entities and relationships asserted in one
  investigation are visible to others, subject to `classification` labels.
- **Trust ledger** — a worker's reputation earned in investigation #1
  carries into investigation #2.

**Conflict controls:**

- **Write isolation** — an investigation can only write to its own
  evidence store; it reads shared memory but does not mutate it until its
  conclusions are ratified (see `19-investigation-engine.md`).
- **Staleness budget** — shared graph reads are point-in-time snapshots
  per investigation, so a long-running investigation is not surprised by
  mid-flight writes from another.
- **Cross-investigation locking** — when two investigations need to
  amend the same constitutional clause or hire into the same department,
  a coordinator serializes the structural mutations to avoid
  inconsistent org state.

---

## 7. Knowledge Graph Scaling

The knowledge graph (see `14-knowledge-graph.md`) is the org's structured
memory: entities, relationships, evidence links, and classification labels.
At scale it is the heaviest read path — every worker task loads a slice
of it as context.

### 7.1 Growth model

```
   nodes  ≈ O(investigations × entities_per_investigation)
   edges  ≈ O(nodes × avg_degree)
```

With institutional memory accumulating across years, the graph for a
single active org can reach 10^6–10^7 nodes. Across 1000 orgs, the platform
holds 10^9–10^10 nodes — firmly in "graph database at scale" territory.

### 7.2 Scaling techniques

- **Per-org graph namespace.** The graph is physically partitioned by
  `org_id`. Cross-org queries are forbidden by the query layer and
  unnecessary for correctness. This turns a global 10^10-node graph into
  1000 independent 10^7-node graphs — far easier to shard, cache, and
  reason about.
- **Read-time subgraph extraction.** Workers do not load the whole graph.
  The Planner computes a relevance-bounded subgraph (k-hop around the
  investigation's seed entities, capped at a node budget) and the worker
  receives only that. This caps per-task context size regardless of total
  graph size.
- **Materialized hot paths.** Frequently traversed patterns (e.g., "all
  evidence cited by investigation N") are materialized as secondary
  indexes or denormalized edges, so hot reads do not require graph
  traversal.
- **Tiered storage.** Recently asserted nodes live in hot storage; nodes
  not touched in N days sink to cold storage and are rehydrated on read.
- **Graph reindex as a P3 task.** Reindexing and compaction run as
  background worker tasks (priority P3), so maintenance never blocks
  interactive investigation work.

---

## 8. Real-Time Scaling

The mobile app shows the org **living** — workers status, meeting turns,
evidence arriving, trust changing (see `22-mobile-ux.md`). This is a
real-time fan-out problem that a single-agent system simply does not have.

### 8.1 WebSocket connection management

```
   Mobile client ──WebSocket──┐
                              │
   ┌──────────────────────────▼──────────────────┐
   │       EDGE GATEWAY (sticky, per user)        │
   │   ┌──────────┐ ┌──────────┐ ┌──────────┐    │
   │   │ ws node 1│ │ ws node 2│ │ ws node N│    │
   │   └────┬────┘ └────┬────┘ └────┬────┘    │
   └────────┼───────────┼───────────┼──────────┘
            │           │           │
            └───────────┴───────────┘
                        │ subscribe (org_id, filter)
                        ▼
   ┌──────────────────────────────────────────────┐
   │       EVENT BUS (per-org topics)             │
   │   org.A.status │ org.A.meeting.9 │ org.B. ... │
   └──────────────────────┬───────────────────────┘
                          │ publish
                          ▼
   ┌──────────────────────────────────────────────┐
   │       ORG RUNTIME (workers, meetings)         │
   └──────────────────────────────────────────────┘
```

- **Sticky connections.** A user's WebSocket is pinned to one edge node for
  the session's lifetime; that node holds the user's subscription set.
- **Per-org topics.** The event bus (see `16-event-system.md`) is
  partitioned by `org_id`. A ws node subscribes only to the orgs its
  connected users care about, so fan-out is bounded by active interest,
  not by total org count.
- **Connection cap per node.** Each ws node holds up to C connections
  (tuned to memory; default 50k). Beyond that, new connections are routed
  to another node via a consistent-hash router.
- **Heartbeat + reconnect.** Clients heartbeat every 15s; missed
  heartbeats trigger reconnect to a fresh node, which re-subscribes from
  the user's session profile.

### 8.2 Event fan-out

A single worker status change should reach exactly the users watching that
org, and no one else. Fan-out is **push-based from the runtime to the bus,
and from the bus to subscribed ws nodes** — there is no polling.

```
   Worker status change
        │
        ▼
   publish to org.{org_id}.status topic
        │
        ├─▶ ws node 3 (has user X subscribed) ──▶ user X
        └─▶ ws node 7 (has user Y subscribed) ──▶ user Y
```

**Fan-out scaling controls:**

- **Coalescing.** Rapid-fire status changes for the same worker are
  coalesced within a 250ms window so the client sees the latest state, not
  a burst of intermediate ones. This cuts fan-out volume dramatically
  during a busy meeting.
- **Per-topic rate cap.** A topic cannot publish faster than R msg/s; bursts
  are dropped with a "compressed" marker so the client knows it is seeing
  a sampled view, not a lie.
- **Backpressure to runtime.** If a topic's subscriber queue backs up, the
  bus tells the runtime to slow that org's event emission, which the
  Planner translates into lower task emission — the real-time layer and the
  worker layer share a single backpressure loop.

---

## 9. Scaling Milestones

Each milestone lists the **load profile**, the **infrastructure** required,
and the **primary bottleneck** to solve before the next milestone.

### Milestone 1 — One org (hackathon / single user)

```
   Load:       1 org, ~5 workers, 1–2 investigations, 1 WebSocket
   Workers:    ~10 concurrent LLM calls peak
   Tokens:     ~100k / investigation
   Graph:      < 10^3 nodes
   Real-time:  1 connection
```

**Infrastructure:**

- 1 executor node (8 vCPU, 16 GB), 64 slots.
- 1 Postgres instance (shared hot + cold path; no separation yet).
- 1 in-process event emitter (no bus; WebSocket served from the API node).
- 1 LLM provider key.
- Secrets in a local vault file.

**Primary bottleneck to solve:** none — the goal is correctness, not
throughput. This is the MVP scope (see `28-mvp-hackathon.md`).

### Milestone 10 — Ten orgs (early beta)

```
   Load:       10 orgs, ~50 workers total, ~20 investigations, ~10 WebSockets
   Workers:    ~100 concurrent LLM calls peak
   Tokens:     ~1M / day across orgs
   Graph:      < 10^4 nodes per org
   Real-time:  ~10 connections
```

**Infrastructure:**

- 2–3 executor nodes behind a managed queue (introduce SQS/Redis Streams).
- Postgres primary + 1 read replica; RLS enabled for org isolation.
- Dedicated graph store (e.g., a hosted Neo4j or Postgres+AGE), per-org
  namespaces.
- Lightweight event bus (Redis Pub/Sub) feeding 1–2 ws nodes.
- Pooled LLM provider keys with per-org budget accounting.
- Vault (managed) for Caspian credentials.

**Primary bottleneck to solve:** **queue fairness** — without per-org
partitioning, one busy org starves the others. This is where the fair-share
pull loop is first exercised.

### Milestone 100 — One hundred orgs (public beta)

```
   Load:       100 orgs, ~500 workers total, ~200 investigations, ~500 WebSockets
   Workers:    ~2k concurrent LLM calls peak
   Tokens:     ~10M / day
   Graph:      < 10^6 nodes per org, ~10^8 platform-wide
   Real-time:  ~500–2k connections
```

**Infrastructure:**

- 20–40 executor nodes, autoscaled on queue depth.
- Postgres cluster (primary + read replicas) **or** migration to a
  distributed row store (e.g., CockroachDB / Aurora) for hot path; cold
  path to S3 + Athena-style scan.
- Graph store sharded by `org_id` (each shard holds a subset of orgs).
- Real event bus (Kafka or Kinesis), per-org topics, ~5 ws nodes behind a
  consistent-hash router.
- Multiple LLM provider keys with key-pool load balancing; per-org token
  budgets enforced at the gateway.
- DLP and injection classifiers moved to dedicated inference nodes so they
  do not compete with workers for executor slots.

**Primary bottleneck to solve:** **graph read path** — at 10^6 nodes per
org, naive traversal is too slow; this is where subgraph extraction and
materialized hot paths become mandatory. Secondary: **WebSocket fan-out**
volume during concurrent meetings across many orgs.

### Milestone 1000 — One thousand orgs (production scale)

```
   Load:       1000 orgs, ~5k workers total, ~2k investigations, ~10k WebSockets
   Workers:    ~20k concurrent LLM calls peak
   Tokens:     ~100M / day
   Graph:      < 10^7 nodes per org, ~10^10 platform-wide
   Real-time:  ~10k–50k connections
```

**Infrastructure:**

- 200–400 executor nodes in a regional pool, autoscaled; multi-region for
  failover.
- Distributed hot-path store (CockroachDB / Spanner / Aurora Global) with
  RLS; cold path in object storage with a query layer.
- Graph store fully sharded by `org_id` across a cluster; per-org
  namespaces pinned to shards with hot/cold tiering inside each shard.
- Kafka (or equivalent) with per-org topics, partitioned; ~20–50 ws nodes
  behind a consistent-hash router with anycast routing.
- LLM provider: multiple vendors, key pools per vendor, per-org budget
  authority as a dedicated microservice (the "budget authority") so
  enforcement is never a bottleneck.
- Dedicated security plane: Caspian Gateway, DLP, injection classifier, and
  audit log as isolated services with their own capacity, so a spike in
  external traffic cannot starve worker execution.
- Per-org encryption keys via KMS; key rotation automated.

**Primary bottleneck to solve:** **cost** — at 100M tokens/day the LLM
bill dominates, and model selection / batching / context compression become
the highest-leverage scaling work. Secondary: **cross-region consistency**
for shared state and the **isolation boundary** under a noisy-neighbor
tenant.

---

## 10. Cross-Cutting Scaling Principles

1. **Stateless workers, shared state.** Never hold investigation state in
   executor memory across tasks.
2. **Partition by org.** Every scaling dimension — queue, DB, graph, bus,
   budgets — is partitioned by `org_id` first. This gives fair-share,
   isolation, and bounded blast radius in one stroke.
3. **Backpressure everywhere.** The real-time layer, the worker layer, and
   the planner share one backpressure loop; no component silently queues
   unbounded work.
4. **Budgets are hard, not advisory.** Token budgets are enforced before
   the LLM call; exhaustion pauses work and surfaces to the user.
5. **Model selection is a scaling lever.** P3 maintenance on small models,
   P0 coordination on flagship models. Cost scales with the *mix*, not
   just the *count*.
6. **Isolation is a security control and a scaling control.** The same
   `org_id` partitioning that prevents data leakage (see `26-security.md`)
   also prevents noisy-neighbor starvation. One design, two benefits.

---

## 11. Cross-References

- `04-technical-architecture.md` — system topology underlying the executor pool, queue, and bus.
- `05-agent-architecture.md` — the stateless worker contract.
- `06-memory-system.md` — institutional memory shared across investigations.
- `08-trust-engine.md` — trust ledger shared across investigations.
- `13-database-design.md` — schema and RLS for shared state.
- `14-knowledge-graph.md` — graph partitioning and subgraph extraction.
- `16-event-system.md` — event bus topics and fan-out.
- `18-planner-design.md` — cost-aware planning and task emission.
- `19-investigation-engine.md` — per-investigation concurrency and ratification.
- `22-mobile-ux.md` — the real-time org view that drives WebSocket fan-out.
- `25-state-machine.md` — investigation and org state machines under load.
- `26-security.md` — org isolation, rate limits, and the security plane.
- `28-mvp-hackathon.md` — Milestone 1 scope.
- `29-future-roadmap.md` — post-MVP scaling work.
