# 05 — Agent Architecture

> Distributed Worker & Department Design

This document specifies how individual **workers** and **departments** are
designed inside a Veliora organization. It is the atomic layer of the system:
everything above this layer (investigations, debates, evolution, trust) is
built on the worker and department primitives defined here.

For the system-level topology these workers live inside, see
`04-technical-architecture.md`. For the state machines that govern
investigation-level flow (which drives many worker transitions), see
`25-state-machine.md`.

---

## 1. The One Agent Test, Applied

> *Every feature must exist ONLY because this is an organization, not a single
> AI. If a feature would still make sense with one agent, redesign it.*

A Veliora worker is **not** an "AI agent" in the CrewAI/AutoGen sense. A
worker is a **role-bound member of an organization**. The distinction is
load-bearing: most of a worker's structure exists *only* because it operates
inside a society of other workers. The table below audits each worker property
against the One Agent Test.

| Worker property        | Would a single agent need it? | Why it exists here (org-only)                                |
|------------------------|-------------------------------|--------------------------------------------------------------|
| Identity / persona     | No                            | Others must address, remember, and trust *someone*.          |
| Role / department      | No                            | Division of labor requires bounded responsibility.           |
| DNA-influenced person. | No                            | Org culture must express differently per member.             |
| Memory context (3 scopes) | Partially                 | Department & org scopes only exist with peers.              |
| Trust score            | No                            | Weighted influence requires a population to weigh against.    |
| Relationships graph    | No                            | Trust/affinity/conflict edges require other workers.          |
| Current task / deps    | Partially                     | Dependencies only exist across a multi-worker plan.           |
| State (meeting/voting) | No                            | Meetings and votes are collective acts.                       |

Any property that fails this test (e.g. "a worker's private scratchpad") is
**not** a worker-level feature — it is an engine-internal detail and lives in
the engine that needs it. This keeps the worker schema purely organizational.

---

## 2. Worker Anatomy

A worker is the atomic unit of organizational intelligence. Intelligence does
not live *in* a worker; it emerges *between* workers. A worker is therefore
defined mostly by its **relationships and context**, not by its reasoning
capacity (which is delegated to a shared LLM substrate — see §8).

```
                       ┌─────────────────────────────────────────────┐
                       │                   WORKER                     │
                       │                                             │
   identity ─────────► │  worker_id   : UUID                         │
                       │  name        : "Dr. Elena Vasquez"          │
                       │  persona     : "cautious, evidence-first"  │
                       │  role        : RESEARCHER                  │
                       │  department  : Research Division            │
                       │                                             │
   dna personality ──► │  innovation : 0.62   risk : 0.30           │
                       │  hierarchy   : 0.80   speed : 0.45           │
                       │  accuracy    : 0.91   transparency: 0.70   │
                       │  (derived from org DNA + individual delta)  │
                       │                                             │
   memory context ───► │  personal_scope    -> MemoryRef            │
                       │  department_scope  -> MemoryRef (shared)    │
                       │  org_scope         -> MemoryRef (shared)    │
                       │                                             │
   trust score ──────► │  overall : 96.0   tasks: 184                 │
                       │  per_domain { research:97, forecast:81 }    │
                       │  confidence_interval : [92.1, 98.4]        │
                       │                                             │
   relationships ────► │  trust_edges    : [ {to:w_17, w:0.88}, ... ] │
                       │  affinity_edges : [ {to:w_04, w:0.71}, ...] │
                       │  conflict_edges : [ {to:w_09, reason:...} ] │
                       │                                             │
   current task ─────► │  task_id, investigation_id, assignment,     │
                       │  deadline, dependencies[], status          │
                       │                                             │
   state ────────────► │  IDLE | ASSIGNED | WORKING | IN_MEETING |   │
                       │  REPORTING | VOTING | AWAITING_INPUT |      │
                       │  BLOCKED | TERMINATED                       │
                       └─────────────────────────────────────────────┘
```

### 2.1 Identity

A worker has a stable, generated identity (`name`, `persona`, `avatar_seed`).
Identity is **not** cosmetic: other workers address each other by name in
meetings, the mobile UI renders a recognizable member, and institutional
memory attributes decisions to specific people ("*Dr. Vasquez flagged the
sample-size issue in the Q3 healthcare investigation*"). A single agent has no
one to address; an organization must name its members. See `22-mobile-ux.md`
for how identity renders on the dashboard.

### 2.2 Role & Department

`role` is a functional specialty (`RESEARCHER`, `ENGINEER`, `MARKETER`,
`FINANCE_ANALYST`, `LEGAL_ADVISOR`, `CEO`, …). `department_id` binds the
worker to a shared-context group (§4). A role constrains *which* tasks a
worker may accept and *which* prompt template is loaded (see
`17-prompt-architecture.md`). Roles are defined per organization template
(see `templates/organizations/` described in `15-folder-structure.md`).

### 2.3 DNA-Influenced Personality

Each worker carries a personality vector derived from the organization's DNA
(see `21-organizational-dna.md`) **plus an individual delta** so members of
the same department are not identical:

```
worker.dna_personality[dim] = clamp( org_dna[dim] + individual_delta[dim], 0, 1 )
```

Personality modulates behavior: a high-`risk_appetite` researcher proposes
bolder hypotheses; a high-`hierarchy_respect` worker defers to the department
head in meetings; a low-`speed_bias` worker spends more turns gathering
evidence. A single agent has no peers whose behavior to differ from; the
*variance across members* is what produces internal politics (Contribution #5).
Two workers from the same org can disagree because their deltas differ.

### 2.4 Memory Context (Three Scopes)

A worker reads and writes across three memory scopes (see `06-memory-system.md`):

| Scope             | Visibility        | Example content                                  |
|-------------------|-------------------|--------------------------------------------------|
| `personal_scope`  | This worker only  | Own past tasks, notes, prior mistakes            |
| `department_scope`| Whole department  | Dept findings, shared evidence, dept policies   |
| `org_scope`       | Whole organization| Institutional memory, past investigations, constitution |

A worker cannot read another worker's `personal_scope`. This enforces that
organizational knowledge is **shared**, not hoarded — and that a worker's
private reasoning is not the system's source of truth (the *department* and
*org* scopes are). A single agent would have one scope; the three-scope split
exists only because knowledge must be partitioned across a society.

### 2.5 Trust Score

Trust is a per-worker, per-domain reputation (see `08-trust-engine.md`). It is
**not** a confidence score on a single answer — it is a longitudinal track
record (`accuracy_history`, `task_count`, `confidence_interval`). Trust
determines **voting weight**, **task assignment priority**, and **influence in
debate**. A single agent has no population to be weighed against; weighted
trust (Contribution #8) is meaningless without peers.

### 2.6 Relationships

Relationships are a graph stored in the knowledge graph (see
`14-knowledge-graph.md`) with three edge types:

- **trust_edges** — directional, updated by the Trust Engine after outcomes.
- **affinity_edges** — working rapport; affects meeting dynamics and team formation.
- **conflict_edges** — recorded disagreements with a reason and timestamp;
  surface as "dissenting opinions" in final reports and feed internal politics.

A single agent has no edges. The relationship graph is what makes the
organization *social* rather than monolithic.

### 2.7 Current Task

```
current_task = {
  task_id, investigation_id,
  assignment:        "Assess technical feasibility of HIPAA-compliant inference",
  deadline, dependencies: [task_id, ...],   # tasks that must finish first
  status:            ASSIGNED | WORKING | ...
}
```

`dependencies` is the key org-only field: a worker's task can *depend on
another worker's task*. This is how the Planner (see `18-planner-design.md`)
encodes division of labor. A single agent has no one to depend on.

### 2.8 State

Worker state is a small, explicit enum (see §3). State transitions are emitted
as events (see `16-event-system.md`) so the scheduler, meeting engine, and
mobile UI all react consistently.

---

## 3. Worker Lifecycle

### 3.1 State Diagram

```
            [org template instantiate]
                      │
                      ▼
                  ┌───────┐
                  │CREATED│
                  └───┬───┘
                onboard │  (load constitution + dept memory + role prompt)
                      ▼
   trust update     ┌──────┐    assign(task)     ┌──────────┐
   ───────────────► │ IDLE │ ─────────────────► │ ASSIGNED │
   (after review)   └──┬───┘                    └─────┬────┘
                       ▲                             │ start()
                       │                             ▼
                       │                        ┌──────────┐  deps unmet
                       │                        │AWAITING  │──────────► (wait)
                       │                        │_INPUT    │◄────────── (met)
                       │                        └────┬─────┘
                       │                             │
                       │                             ▼
                       │      convene         ┌────────┐  adjourn
                       │      ┌──────────────►│WORKING │◄─────────┐
                       │      │               └────┬───┘          │
                       │      │                    │ done()       │
                       │      │                    ▼             │
                       │   ┌──────────┐        ┌─────────┐        │
                       │   │IN_MEETING│        │REPORTING│        │
                       │   └────┬─────┘        └────┬────┘        │
                       │        │                   │ vote?       │
                       │        │                   ▼             │
                       │        │              ┌────────┐         │
                       │        │              │ VOTING │         │
                       │        │              └───┬────┘         │
                       │        │                  │              │
                       └────────┴──────────────────┴──────────────┘
                       │          (peer review → trust update)
                       │
   escalation ──► BLOCKED ──resolve──► IDLE
   fired ──────► TERMINATED
```

### 3.2 Lifecycle Stages

| Stage              | Trigger                         | Owner              | Org-only rationale                              |
|--------------------|---------------------------------|--------------------|-------------------------------------------------|
| **Creation**       | Org template instantiation      | Org Manager svc    | Templates define a *society*, not a prompt.     |
| **Onboarding**     | `CREATED` entered               | Worker runtime     | Reads shared constitution + dept memory.        |
| **Assignment**     | Planner/CEO dispatches a task   | Planner Engine     | Task split across members; deps between them.   |
| **Execution**      | `start()`                       | Worker runtime     | Produces evidence for *others* to challenge.    |
| **Reporting**      | `done()`                        | Worker runtime     | Findings enter the shared dept/org memory.       |
| **Peer review**    | Evidence submitted              | Debate Engine      | Others criticize evidence (Contribution #7).   |
| **Reputation update** | Review outcome              | Trust Engine       | Recomputes weight *relative to peers*.          |
| **Promotion/Firing** | Evolution tick / vote         | Evolution Engine   | The org reshapes itself (Contribution #12).     |

### 3.3 Creation via Org Template

Workers are **never** created by the user. The user creates an *organization*;
the organization template declares departments, roles, headcounts, and a
constitution. The Org Manager service materializes workers to fill the
template:

```
template: startup.yaml
  └─ departments:
       ├─ ceo_office      { roles: [CEO],                 headcount: 1 }
       ├─ research        { roles: [RESEARCHER x2],        head: w_01 }
       ├─ engineering     { roles: [ENGINEER x2],          head: w_03 }
       ├─ finance         { roles: [FINANCE_ANALYST x1],   head: w_05 }
       └─ marketing       { roles: [MARKETER x1],         head: w_06 }
  └─ constitution: startup.constitution.md
  └─ dna: { innovation:0.7, risk:0.6, hierarchy:0.5, ... }

  ──► OrgManager.instantiate(template)
        for each role slot:
            worker = spawn_worker(role, dept, org_dna)
            worker.dna_personality = org_dna + sample_delta()
            worker.state = CREATED
            emit worker.created
```

### 3.4 Assignment

The Planner (see `18-planner-design.md`) decomposes an investigation into a
task DAG. The Scheduler (§6) assigns each task to the worker with the best
`(role_match, trust_score, availability)` tuple, respecting dependencies.
The CEO worker may also re-assign or escalate. Assignment emits
`worker.assigned` and moves the worker `IDLE → ASSIGNED`.

### 3.5 Task Execution & Reporting

A worker in `WORKING` invokes the LLM substrate with its role prompt, the
three memory scopes, and the task brief (see `17-prompt-architecture.md`).
Output is an **evidence artifact** (findings + sources + confidence), not a
free-form answer. On `done()` the worker enters `REPORTING`, writes the
artifact to department memory, and emits `evidence.submitted`.

### 3.6 Reputation Update

After evidence is peer-reviewed (see `10-debate-engine.md`), the Trust Engine
(see `08-trust-engine.md`) updates the worker's `trust_score` and
relationship edges. A worker whose forecast later proves correct gains
domain trust; one whose evidence is rejected loses it. This closes the loop
that makes the org *learn* across members.

### 3.7 Promotion & Firing

The Evolution Engine (see `07-evolution-engine.md`) periodically evaluates
the workforce:

- **Promotion** — a high-trust worker may be elected department head (replacing
  or adding a head), gaining agenda-setting power and a vote weight bonus.
- **Hiring** — an overloaded department (queue depth > threshold) proposes a
  new slot; the constitution's hiring rule (see `09-constitution-engine.md`)
  may require a board vote.
- **Firing** — a persistently low-trust worker is terminated
  (`→ TERMINATED`); their personal scope is archived to org memory so
  institutional knowledge survives turnover.

None of this is triggered by the user. The organization reshapes itself.

---

## 4. Department Design

### 4.1 Departments as Shared-Context Groups

A department is a group of workers that share a `department_scope` memory, a
set of department goals, and a department head. Departments are the unit at
which **context is pooled**: a Research Division accumulates a body of
findings that any researcher can draw on, without re-reading every paper. A
single agent has no one to pool context with.

### 4.2 Department Topology

```
                         ┌─────────────────────────────────────┐
                         │            ORGANIZATION             │
                         │   (constitution, DNA, org memory)    │
                         └─────────────────┬───────────────────┘
                                           │
        ┌──────────────────┬───────────────┼───────────────┬──────────────┐
        ▼                  ▼               ▼               ▼              ▼
  ┌──────────┐      ┌──────────┐    ┌──────────┐    ┌──────────┐   ┌──────────┐
  │CEO Office│      │ Research │    │Engineering│   │ Finance  │   │Marketing │
  │          │      │ Division │    │           │   │          │   │          │
  │ [CEO]★   │      │ [R1]★head│    │ [E1]★head │   │ [F1]★head│   │ [M1]★head│
  │          │      │ [R2]     │    │ [E2]      │   │          │   │          │
  └────┬─────┘      └────┬─────┘    └─────┬─────┘   └────┬─────┘   └────┬─────┘
       │                 │                │              │              │
       │   cross-dept channel (async)     │              │              │
       │ ◄─────────────► │ ◄───────────► │ ◄──────────►│ ◄──────────► │
       │                 │                │              │              │
       └────────────── inter-department meeting bus (see 20-meeting-engine.md)
       ★ = department head      head = dept's representative + agenda setter
```

### 4.3 Department Heads

Each department has a **head** (the `★` worker). The head is not a manager in
the human sense (the org is autonomous); the head's powers are narrow and
organizational:

- **Agenda setting** — proposes the department's task priorities to the Planner.
- **Representation** — is the default attendee of cross-department meetings,
  though any member may be delegated.
- **Tie-breaking** — breaks deadlocks in intra-department votes (weighted by
  the constitution — see `09-constitution-engine.md`).
- **Escalation target** — receives `BLOCKED` escalations from members.

A head is elected/promoted by the Evolution Engine from high-trust members
(§3.7). If a head is terminated, a deputy (next-highest-trust member) takes
over automatically (§7.3).

### 4.4 Inter-Department Communication Protocol

Departments do **not** share a single message bus with free-for-all access.
Inter-department communication is structured to preserve the division of
labor that makes the org more than the sum of its parts:

| Channel            | Direction        | Purpose                                    | Ref              |
|--------------------|------------------|--------------------------------------------|------------------|
| `dept.request`     | Dept → Dept      | Ask another dept for evidence/analysis      | `11-communication-engine.md` |
| `dept.challenge`   | Dept → Dept      | Formally challenge another dept's evidence | `10-debate-engine.md`      |
| `meeting.convene`   | Head → Heads     | Call a cross-dept meeting                   | `20-meeting-engine.md`     |
| `dept.report`      | Dept → CEO Office| Status report up the hierarchy             | `11-communication-engine.md` |
| `evidence.broadcast`| Dept → All      | Publish reviewed evidence org-wide          | `14-knowledge-graph.md`     |

All channels are **async** and **event-sourced** (see `16-event-system.md`).
A department never synchronously blocks on another; it posts a request and
continues, consuming the reply when it arrives. This is what lets the
organization run many investigations concurrently (see `27-scaling-strategy.md`).

---

## 5. Worker Communication Model

### 5.1 Async Messaging

Workers communicate via the Communication Engine (see
`11-communication-engine.md`), never via direct shared memory. A message is:

```
Message {
  message_id, from: worker_id, to: worker_id | dept_id | "all",
  channel:   DIRECT | DEPT | ORG_BROADCAST | MEETING,
  kind:      QUESTION | FINDING | CHALLENGE | REBUTTAL | REQUEST | VOTE,
  ref:       evidence_id | task_id | meeting_id,   # what it's about
  body, ts, thread_id
}
```

Messages are threaded (so a challenge/rebuttal pair is linked) and persisted
to the knowledge graph (see `14-knowledge-graph.md`) as the permanent record
of *how* the organization reasoned — not just *what* it concluded.

### 5.2 Meeting Participation

Meetings (see `20-meeting-engine.md`) are the synchronous-ish collective act.
A worker in `IN_MEETING` takes turns (turn order decided by the Meeting Engine
using trust + personality + agenda). A meeting is **not** a chat: it has an
agenda, a chair (often a department head), a turn protocol, and a termination
condition (consensus, vote, or time-box). Workers enter `IN_MEETING` from
`WORKING` and return to `WORKING` on adjournment.

### 5.3 Evidence Submission & Challenge

When a worker reports, it submits an **evidence artifact**:

```
Evidence {
  evidence_id, submitted_by, dept, investigation_id,
  claim, sources[], confidence, methodology,
  status: SUBMITTED | CHALLENGED | UPHELD | RETRACTED,
  challenges: [ {by, critique, severity} ],
  verdict
}
```

Other workers/departments may `CHALLENGE` it (Contribution #7). The
originator may `REBUTTAL` or `RETRACT`. Only evidence that survives
challenge is admissible in the final board report (see
`19-investigation-engine.md`). This peer-review gate is meaningless with one
agent — there is no one to challenge you.

### 5.4 Voting

Decisions (admit evidence, amend constitution, hire/fire, finalize
recommendation) are settled by **weighted vote** (see `09-constitution-engine.md`
and `10-debate-engine.md`). Vote weight = `f(trust_score, role_relevance,
dna.hierarchy_respect)`. A worker in `VOTING` casts a vote with an optional
justification; the justification is recorded so the org can later explain
*why* it decided as it did (Contribution #4 — institutional memory).

---

## 6. Distribution & Scheduling

### 6.1 Worker Pool

Workers are logical entities, not processes. A **Worker Runtime** service
(see `04-technical-architecture.md`) owns a pool of execution slots. A worker
is *scheduled onto* a slot for the duration of a turn, then the slot is
released. This decouples the *organizational* population (which can be large)
from the *compute* population (which is bounded by cost).

### 6.2 Task Scheduling

The Scheduler assigns tasks from the Planner's DAG. Assignment policy:

```
score(worker, task) =
      α * role_match(worker.role, task.required_role)
    + β * trust_score(worker, task.domain)
    + γ * (1 - load(worker))              # availability
    + δ * affinity_to_team(worker, task.co_workers)
    - ε * conflict_penalty(worker, task.co_workers)
```

Dependencies are honored: a task is only assignable when all its
`dependencies` are `REPORTED`/done. The Scheduler emits `worker.assigned`
events consumed by the Worker Runtime.

### 6.3 Concurrency Model

Many workers can be `WORKING` simultaneously, each on a different slot. They
coordinate **only** through events and the shared memory scopes — never through
shared mutable state. This is what makes the organization horizontally
scalable (see `27-scaling-strategy.md`): adding capacity means adding slots,
not rewriting coordination logic.

---

## 7. Worker Isolation & Failure Handling

### 7.1 Context Isolation

Each worker turn runs in an isolated context: its own prompt assembly, its
own memory-scope read set, and its own LLM call. A worker **cannot** mutate
another worker's `personal_scope` or another department's
`department_scope`. Writes to shared scopes go through the Memory Engine
(see `06-memory-system.md`) with conflict detection (last-writer-wins on
notes, append-only on evidence). Isolation guarantees that a misbehaving
worker cannot corrupt the organization's shared knowledge directly.

### 7.2 Failure Modes & Recovery

| Failure                  | Detection              | Recovery                                   |
|--------------------------|------------------------|--------------------------------------------|
| LLM call timeout/error   | Worker Runtime watchdog| Retry with backoff; then `BLOCKED` + escalate to head |
| Worker produces no evidence | Reporting validator   | Mark task failed; reassign to next-best worker |
| Worker stuck in `WORKING` | Heartbeat timeout      | Force `BLOCKED`; head reassigns or decomposes task |
| Worker hallucinates sources | Evidence validator    | Evidence auto-`CHALLENGED`; Trust Engine penalizes |
| Department head terminated | Evolution Engine       | Deputy (next-highest-trust) auto-promoted (§7.3) |
| Whole department down     | Health monitor         | CEO redistributes dept's tasks to siblings or hires |

All failures are emitted as events and recorded in institutional memory, so
the organization *learns from its own failures* (Contribution #4) — e.g., a
department that repeatedly times out on forecasting may lose influence.

### 7.3 Department Head Failover

```
head_terminated(dept):
    members = dept.workers sorted by trust_score desc, excluding TERMINATED
    if members is empty:
        emit dept.headless
        CEO proposes hiring or merging dept (Evolution Engine)
    else:
        new_head = members[0]
        promote(new_head, dept)
        emit dept.head_changed
```

Failover is automatic and trust-based, not configured. This is why the org
keeps running when individuals fail — the *role* persists, the *person* is
replaceable, exactly as in a real organization.

---

## 8. Worker vs LLM — What a Worker Is Not

A frequent confusion is to equate "worker" with "an LLM instance." They are
different layers:

```
   ORGANIZATION  ── has ──►  DEPARTMENTS  ── have ──►  WORKERS
        │                                              │
        │   (intelligence emerges here, between workers)│
        │                                              │
        └──────────── shared LLM substrate ◄────────────┘
                  (stateless reasoning capacity)
```

- The **LLM substrate** provides raw reasoning. It is stateless and shared.
- A **worker** is an *organizational identity* with memory, trust, and
  relationships. Two workers may use the same LLM call and still disagree,
  because they have different memory scopes, personalities, and roles.
- The **organization** is the intelligence. No single worker (and no single
  LLM call) is "the answer."

This is why a worker's schema is dominated by *relational* and *contextual*
fields rather than by reasoning parameters. The reasoning is a commodity; the
organization is the product.

---

## 9. Cross-References

| Topic                                | See                                    |
|--------------------------------------|----------------------------------------|
| System topology & services           | `04-technical-architecture.md`         |
| Institutional memory & scopes        | `06-memory-system.md`                 |
| Promotion / hiring / firing          | `07-evolution-engine.md`              |
| Trust score computation              | `08-trust-engine.md`                  |
| Constitution & voting rules          | `09-constitution-engine.md`           |
| Debate & evidence challenge          | `10-debate-engine.md`                  |
| Messaging channels                   | `11-communication-engine.md`          |
| External (Caspian) comms by workers  | `12-caspian-integration.md`           |
| Knowledge graph / relationships      | `14-knowledge-graph.md`               |
| Repo layout (templates, runtime)     | `15-folder-structure.md`               |
| Event-driven transitions            | `16-event-system.md`                  |
| Role prompts & assembly             | `17-prompt-architecture.md`            |
| Task DAG & decomposition            | `18-planner-design.md`                |
| Investigation orchestration          | `19-investigation-engine.md`           |
| Meetings & turn protocol            | `20-meeting-engine.md`                |
| DNA → personality derivation        | `21-organizational-dna.md`            |
| Worker identity on mobile           | `22-mobile-ux.md`                     |
| Worker/investigation state machines | `25-state-machine.md`                 |
| Scaling the worker pool             | `27-scaling-strategy.md`              |
