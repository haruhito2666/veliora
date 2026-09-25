# 25 — State Machine

> Every meaningful entity in Veliora is a finite-state machine whose
> transitions are *organizational acts*, not function calls. This
> document specifies the states, transitions, guards, and actions for
> the Organization, Investigation, Meeting, Worker, Evolution, and
> Constitution machines — and how they drive each other.

The API surface in `24-api-design.md` is a thin wrapper over these
machines: every write endpoint resolves to an event fed into one of
them, and every `409 *_invalid_transition` is a guard rejection
documented here.

---

## 1. Why State Machines

Veliora is an organization, not a single agent (see
`01-core-philosophy.md`). An organization has *process*: investigations
move through phases, meetings have agendas, workers have careers,
constitutions are amended by vote. Modeling these as explicit FSMs —
rather than ad-hoc flags — gives us three properties the system
depends on:

1. **Observable process.** The live dashboard (see `22-mobile-ux.md`)
   renders state, not progress bars. A user sees "Debating" and knows
   the org is in peer review.
2. **Guarded organizational acts.** You cannot challenge evidence
   after an investigation completes; you cannot dissolve an org
   mid-vote. Guards encode the rules of organizational procedure.
3. **Recoverable evolution.** Every transition emits an event (see
   `16-event-system.md`). Crash recovery is event replay, not a
   re-derivation from logs.

---

## 2. Machine Model

Every machine is a 5-tuple `(S, E, T, G, A)`:

- `S` — finite set of **states**. Exactly one is current.
- `E` — set of **events** that may be submitted to the machine.
- `T ⊆ S × E → S` — **transition function**. Partial: not every
  (state, event) pair is defined.
- `G` — **guards**: predicates over machine context + org context that
  must hold for a transition to fire. A guard failure is a
  `409 *_invalid_transition` at the API (see `24-api-design.md` §2.4).
- `A` — **actions**: side effects executed *atomically* with the
  transition (emit event, persist state, enqueue downstream events).

```
          submit(event)
              │
       ┌──────▼──────┐
       │  current state │
       └──────┬──────┘
              │  T(s,e) defined?  no ──► reject (404 no_such_transition)
              │  G(s,e) holds?    no ──► reject (409 invalid_transition)
              │  yes
       ┌──────▼──────┐
       │  A(s,e,s')   │  persist new state, emit events, enqueue downstream
       └──────┬──────┘
              ▼
          new state
```

All transitions are **persisted before observed**: the new state and
its `state_history` entry are committed to the store (see
`13-database-design.md`) *before* any event is published. This
guarantees that a subscriber never sees a state the store cannot
confirm. Transitions are idempotent on `(machine_id, event_id,
attempt)`; duplicate submissions return the original result.

---

## 3. Organization State Machine

The top-level machine. One per organization. See `24-api-design.md` §5.

### 3.1 States

| State       | Meaning                                                        |
|-------------|----------------------------------------------------------------|
| `forming`   | Org created; constitution drafted; departments seeded by template. Not yet operational. |
| `active`    | Operating. Accepts investigations, runs meetings, evolves.     |
| `evolving`  | A structural evolution is in progress (hire/fire/merge/split/constitution amendment). Investigations may continue but no new evolution proposal is accepted. |
| `dissolved` | Terminal. No further transitions. Data retained per retention policy. |

### 3.2 Diagram

```
                    ┌─────────────┐
        create()    │   forming   │
           ───────► │             │
                    └──────┬──────┘
                           │ constitution.ratified
                           ▼
                    ┌─────────────┐    evolution.proposed
                    │   active    │◄──────────────────┐
                    │             │                   │
                    └──────┬──────┘                   │
                           │ evolution.proposed       │
                           │   (guard: no evolution  │
                           │    in progress)         │
                           ▼                         │
                    ┌─────────────┐                   │
                    │  evolving   │                   │
                    │             │   evolution.applied
                    └──────┬──────┘───────────────────┘
                           │
                           │ evolution.rejected
                           │ (returns to active unchanged)
                           │
        dissolve()  ┌──────▼──────┐
           ───────► │  dissolved  │  ◄── also reachable from forming/active/evolving
                    │  (terminal) │      via dissolve() with force=true
                    └─────────────┘
```

### 3.3 Transitions, Guards, Actions

| From → To            | Event                    | Guard                                   | Actions                                                        |
|----------------------|--------------------------|-----------------------------------------|----------------------------------------------------------------|
| forming → active     | `constitution.ratified`  | Constitution in `ratified` state        | Emit `organization.stateChanged`; open investigation intake     |
| active → evolving    | `evolution.proposed`      | No other evolution in `executing`       | Spawn Evolution SM (§7) with `evolution_id`; emit `organization.evolutionStarted` |
| evolving → active     | `evolution.applied`      | Evolution SM in `applied`               | Apply structural change; recompute department influence; emit `organization.stateChanged` |
| evolving → active     | `evolution.rejected`     | Evolution SM in `rejected`              | Emit `organization.evolutionRejected`; no structural change   |
| * → dissolved        | `dissolve`               | `force=true` OR no evolution in progress | Halt all investigations (state → `cancelled`); close all streams; emit `organization.dissolved` |

### 3.4 Notes

- An org in `evolving` **continues** running existing investigations.
  Evolution is structural, not a global pause. This is intentional:
  an organization that freezes whenever it hires someone is not alive.
- `dissolve(force=true)` from `evolving` aborts the vote; the
  Evolution SM is moved to `aborted` (a terminal sub-state).

---

## 4. Investigation State Machine

The flagship process. See `19-investigation-engine.md`. One per
investigation. Namespaced under an organization.

### 4.1 States

| State          | Meaning                                                          |
|----------------|------------------------------------------------------------------|
| `created`      | Investigation created; not yet planned.                          |
| `planning`     | Planner decomposes the prompt into department tasks (see `18-planner-design.md`). |
| `researching`  | Departments gather evidence (internal + Caspian, see `12-caspian-integration.md`). |
| `analyzing`    | Departments analyze their gathered evidence.                     |
| `debating`     | Evidence challenge + peer review. Meetings scheduled.            |
| `synthesizing` | CEO synthesizes findings into a draft recommendation.             |
| `reporting`    | Final report is being generated.                                 |
| `completed`    | Terminal. Report available.                                       |
| `cancelled`    | Terminal. User or org cancelled. Partial report may exist.       |

### 4.2 Diagram

```
        create()
           │
           ▼
       ┌─────────┐  plan.ready
       │ created │─────────────►┌──────────┐  tasks.assigned
       └─────────┘              │ planning │────────────►┌────────────┐
                                └──────────┘             │ researching│
                                       ▲                  └─────┬──────┘
                                       │ plan.revise             │ evidence.gathered
                                       │ (low coverage)          ▼
                                       │                 ┌───────────┐
                                       │                 │ analyzing │
                                       │                 └─────┬─────┘
                                       │                       │ analysis.done
                                       │                       ▼
                                       │                 ┌───────────┐
                                       │                 │ debating  │
                                       │                 └─────┬─────┘
                                       │                       │ debate.converged
                                       │                       ▼
                                       │                 ┌─────────────┐
                                       │     ┌───────────│ synthesizing │
                                       │     │           └──────┬──────┘
                                       │     │                  │ draft.ready
                                       │     │                  ▼
                                       │     │           ┌───────────┐
                                       │     │           │ reporting │
                                       │     │           └─────┬─────┘
                                       │     │                 │ report.generated
                                       │     │                 ▼
                                       │     │           ┌───────────┐
                                       │     │           │ completed │ (terminal)
                                       │     │           └───────────┘
                                       │     │
                                       │     │ confidence.below_threshold
                                       │     └────────────────────────────► researching
                                       │      (guard: depth budget remaining)
                                       │
                                       └── cancel() ──► cancelled (terminal)
```

### 4.3 Transitions, Guards, Actions

| From → To            | Event                          | Guard                                       | Actions                                                  |
|----------------------|--------------------------------|---------------------------------------------|----------------------------------------------------------|
| created → planning   | `plan.start`                   | Org in `active` or `evolving`                | Spawn Planner; emit `investigation.state`               |
| planning → researching | `plan.ready`                 | Plan has ≥1 task per required department    | Assign tasks → Worker SMs move to `assigned`; emit `investigation.state` |
| planning → planning  | `plan.revise`                  | Coverage < `min_coverage` (DNA-dependent)   | Re-run planner with gap analysis                          |
| researching → analyzing | `evidence.gathered`        | All assigned tasks `done` or `timeout`      | Emit `evidence.submitted` per item; recompute evidence count |
| analyzing → debating | `analysis.done`                | ≥1 evidence item per department engaged      | Schedule debate Meeting SM (§5); emit `meeting.scheduled` |
| debating → synthesizing | `debate.converged`          | All open challenges resolved or `max_rounds` reached | Emit `evidence.upheld`/`retracted`; freeze evidence set |
| synthesizing → reporting | `draft.ready`              | Confidence ≥ `confidence_threshold` OR budget exhausted | Generate report; emit `investigation.confidence` |
| synthesizing → researching | `confidence.below_threshold` | Depth budget remaining (`depth != "quick"`) | Re-plan gaps; Worker SMs re-assigned; emit `investigation.state` with `reason: "low_confidence"` |
| reporting → completed | `report.generated`            | Report artifact persisted                    | Emit `investigation.reportReady`; close investigation stream |
| * → cancelled       | `cancel`                       | State ∉ {completed, cancelled}              | Halt tasks (Worker SMs → `idle`); emit `investigation.cancelled`; close stream |

### 4.4 The Low-Confidence Loop

The defining organizational behavior: the org refuses to report a
low-confidence answer and goes back to research. This is the
realization of "the organization thinks better than individuals" (see
`02-research-contributions.md` §1).

- `confidence_threshold` is derived from DNA `accuracy` (see
  `21-organizational-dna.md`): higher accuracy → higher threshold.
- `max_research_loops` bounds the loop (default 2; DNA `speed`
  lowers it). When the budget is exhausted, the investigation
  proceeds to `reporting` with the best-available confidence and the
  report records the dissent.
- Each loop emits `investigation.confidence` with
  `will_research_again: true` so the live stream shows the org
  rejecting its own draft — a key "feels alive" moment
  (see `22-mobile-ux.md`).

---

## 5. Meeting State Machine

See `20-meeting-engine.md`. One per meeting. A meeting is always
spawned by an investigation (debate phase) or by an evolution proposal
(debate/vote phases).

### 5.1 States

| State        | Meaning                                                          |
|--------------|------------------------------------------------------------------|
| `scheduled`  | Meeting created; participants invited; agenda drafted.          |
| `preparing`  | Participants loading context from memory; agenda finalized.      |
| `active`     | Discussion in progress. Live-streamed (see `24-api-design.md` §10). |
| `voting`     | Votes being cast (weighted by trust, see `08-trust-engine.md`).  |
| `concluded`  | Terminal. Outcome recorded; minutes persisted to memory.         |
| `cancelled`  | Terminal. Quorum not reached or chair cancelled.                |

### 5.2 Diagram

```
   schedule()
      │
      ▼
  ┌───────────┐  participants.ready
  │ scheduled │────────────►┌───────────┐  agenda.locked
  └───────────┘             │ preparing │────────────►┌────────┐
                            └───────────┘             │ active │
                                  ▲                   └───┬────┘
                                  │ quorum.not_met         │
                                  │ (→ cancelled)          │ debate.converged
                                  │                       ▼
                                  │                  ┌────────┐
                                  │                  │ voting │
                                  │                  └───┬────┘
                                  │                      │ votes.closed
                                  │                      ▼
                                  │                  ┌───────────┐
                                  │                  │ concluded │ (terminal)
                                  │                  └───────────┘
                                  │
                                  └── cancel() ──► cancelled (terminal)
```

### 5.3 Transitions, Guards, Actions

| From → To           | Event                | Guard                              | Actions                                                       |
|---------------------|----------------------|------------------------------------|---------------------------------------------------------------|
| scheduled → preparing | `participants.ready` | Quorum projection ≥ `quorum`      | Load memory context per participant; emit `meeting.state`     |
| preparing → active  | `agenda.locked`      | Chair present                      | Open live stream; emit `meeting.started`; Worker SMs of participants → `in_meeting` |
| active → active     | `turn.taken`         | Speaker is a participant           | Append turn; emit `meeting.message` on stream                 |
| active → voting     | `debate.converged`   | `max_rounds` reached OR motion called | Open ballot; emit `meeting.state`                           |
| voting → voting     | `vote.cast`          | Voter is a participant; not voted yet | Record weighted vote; emit `meeting.vote`                  |
| voting → concluded  | `votes.closed`       | All participants voted OR timeout   | Tally; persist minutes to memory (see `06-memory-system.md`); Worker SMs → `idle`; emit `meeting.concluded` with outcome |
| scheduled → cancelled | `cancel`           | Chair or org admin                  | Emit `meeting.cancelled`                                      |
| preparing → cancelled | `quorum.not_met`    | Quorum < `quorum` at start time     | Emit `meeting.cancelled` with `reason: "no_quorum"`           |

### 5.4 Voting & Quorum

- Voting is **weighted by trust** (see `08-trust-engine.md`): each
  participant's vote is multiplied by their current trust score. The
  constitution's `decision_rules.voting` field selects the rule
  (`weighted` | `consensus` | `supermajority`).
- `quorum` is the fraction of trust-weighted participation required
  to conclude. Default `0.6`; set by constitution (see
  `09-constitution-engine.md`).
- A `concluded` meeting emits an outcome event that the **parent
  machine** consumes: an investigation debate meeting emits
  `debate.converged`; an evolution vote meeting emits
  `evolution.vote.closed`.

---

## 6. Worker State Machine

See `05-agent-architecture.md`. One per worker. Workers are
inspectable, not addressable (see `24-api-design.md` §7.2).

### 6.1 States

| State        | Meaning                                                          |
|--------------|------------------------------------------------------------------|
| `idle`       | Available for assignment.                                         |
| `assigned`   | Assigned to a task; not yet started.                              |
| `working`    | Actively executing a task.                                        |
| `reporting`  | Producing the task output / evidence.                             |
| `in_meeting` | Participating in a meeting.                                        |
| `promoted`   | Transient: just promoted; re-enters `idle` with new role/trust.    |
| `terminated` | Terminal. Fired, retired, or org dissolved.                       |

### 6.2 Diagram

```
                       ┌───────┐
                 join  │ idle  │◄──────────────────────────────────────┐
                  ───► └───┬───┘                                         │
                           │ task.assigned                              │
                           ▼                                            │
                    ┌──────────┐  task.start    ┌──────────┐            │
                    │ assigned │──────────────► │ working  │            │
                    └──────────┘                └────┬─────┘            │
                          ▲                         │ task.done         │
                          │                         ▼                   │
                          │                  ┌───────────┐              │
                          │                  │ reporting │              │
                          │                  └─────┬─────┘              │
                          │                        │ output.accepted    │
                          └────────────────────────┘                    │
                                                                        │
   meeting.started ──► in_meeting ◄──┐                                   │
          │           ┌──────────┐    │ meeting.concluded                │
          │           │in_meeting│───┘──────────────────────────────────┘
          │           └──────────┘
          │
   promote ──► promoted ──► idle (new role)
   terminate ──► terminated (terminal)
```

### 6.3 Transitions, Guards, Actions

| From → To           | Event              | Guard                              | Actions                                                  |
|---------------------|--------------------|------------------------------------|----------------------------------------------------------|
| idle → assigned     | `task.assigned`    | Worker trust ≥ task.min_trust      | Emit `worker.state`; notify department                    |
| assigned → working  | `task.start`       | Task dependencies satisfied       | Emit `worker.state`; begin execution                      |
| working → reporting | `task.done`        | Output artifact produced          | Emit `worker.state`; submit to investigation evidence pool |
| reporting → idle    | `output.accepted` | Peer review passed OR auto-accept | Update trust (see `08-trust-engine.md`); emit `worker.state` |
| reporting → working | `output.rejected` | Challenge upheld                  | Re-execute with feedback; emit `worker.state`            |
| idle/working → in_meeting | `meeting.started` | Participant in meeting roster | Emit `worker.state`; pause task timer                    |
| in_meeting → idle   | `meeting.concluded`| Meeting terminal                  | Emit `worker.state`; resume task if any                   |
| idle → promoted     | `promote`          | Evolution SM `approved` a promotion | Apply new role/trust; emit `worker.promoted`           |
| promoted → idle     | `promotion.applied`| —                                 | Emit `worker.state`                                      |
| * → terminated      | `terminate`        | Evolution SM `approved` a termination OR org `dissolved` | Release tasks; emit `worker.terminated`        |

### 6.4 Notes

- A worker is never directly commanded by the API. All transitions
  into `assigned`/`working` are driven by the Investigation or
  Evolution machines. This is the One Agent Test applied to worker
  lifecycle: a single AI has no "career"; an organization does.
- `promoted` is intentionally a *named transient state*, not a side
  effect, because promotions are organizational events that emit
  `worker.promoted` for the trust engine and the live stream.

---

## 7. Evolution State Machine

See `07-evolution-engine.md`. One per evolution proposal. Spawned by
the Organization machine (§3) when `evolution.proposed` fires.

### 7.1 States

| State        | Meaning                                                          |
|--------------|------------------------------------------------------------------|
| `proposed`   | Proposal created (hire/fire/promote/merge/split/policy/dna_shift/amend_constitution). |
| `debating`   | Org debating the proposal in a meeting.                          |
| `voting`     | Vote open.                                                       |
| `approved`   | Vote passed; queued for execution.                               |
| `rejected`   | Vote failed. Terminal (for this proposal).                      |
| `executing`  | Applying the structural change.                                 |
| `applied`    | Change applied. Terminal.                                         |
| `aborted`    | Force-cancelled (e.g., org dissolved). Terminal.                |

### 7.2 Diagram

```
   propose()
      │
      ▼
  ┌──────────┐  debate.start   ┌──────────┐  vote.open   ┌────────┐
  │ proposed │───────────────►│ debating │────────────►│ voting │
  └──────────┘                 └────┬─────┘             └───┬────┘
                                    │                         │
                          debate.converged                votes.closed
                                    │                         │
                                    │              ┌──────────┴──────────┐
                                    │              │                     │
                                    │         approved                rejected (terminal)
                                    │              │
                                    │              ▼
                                    │         ┌───────────┐  change.applied  ┌────────┐
                                    │         │ executing │────────────────►│ applied│ (terminal)
                                    │         └─────┬─────┘                  └────────┘
                                    │               │
                                    │               │ org.dissolved (force)
                                    │               ▼
                                    │         ┌──────────┐
                                    └────────►│ aborted  │ (terminal)
                                              └──────────┘
```

### 7.3 Transitions, Guards, Actions

| From → To           | Event                | Guard                              | Actions                                                  |
|---------------------|----------------------|------------------------------------|----------------------------------------------------------|
| proposed → debating | `debate.start`       | Org in `evolving`                  | Spawn Meeting SM (§5) of type `evolution_debate`; emit `evolution.state` |
| debating → voting   | `debate.converged`   | Meeting `concluded` with outcome `to_vote` | Open ballot; emit `evolution.state`               |
| voting → approved   | `votes.closed`       | Weighted yes ≥ `pass_threshold` (constitution) | Emit `evolution.approved`                          |
| voting → rejected   | `votes.closed`       | Weighted yes < `pass_threshold`    | Emit `evolution.rejected`; Org SM → `evolution.rejected` |
| approved → executing | `execute.start`     | No conflicting evolution in `executing` | Apply structural change; emit `evolution.state`    |
| executing → applied | `change.applied`     | Structural change persisted        | Recompute influence; Org SM → `evolution.applied`; emit `evolution.applied` |
| * → aborted         | `abort`              | Org `dissolve(force=true)`         | Rollback partial change; emit `evolution.aborted`        |

### 7.4 Proposal Types & Their Effects

| type                | structural effect                                       | machines touched                 |
|---------------------|---------------------------------------------------------|----------------------------------|
| `hire`              | New Worker SM spawned in `idle`                         | Worker, Department               |
| `fire`              | Worker SM → `terminated`                                | Worker, Department, Trust        |
| `promote`           | Worker SM → `promoted`                                  | Worker, Trust                    |
| `merge_dept`        | Two Department records merged; workers re-rostered     | Worker, Department               |
| `split_dept`        | One Department split into two; workers re-assigned     | Worker, Department               |
| `policy`            | New policy record in memory; affects future decisions  | Memory, Investigation (future)   |
| `dna_shift`         | DNA vector updated (see `21-organizational-dna.md`)    | Organization, Investigation (future) |
| `amend_constitution`| Constitution SM (§8) moved to `amending`               | Constitution, Organization       |

---

## 8. Constitution State Machine

See `09-constitution-engine.md`. One per organization (the org's
living constitution).

### 8.1 States

| State         | Meaning                                                          |
|---------------|------------------------------------------------------------------|
| `draft`       | Initial draft (during org `forming`).                             |
| `ratified`    | In force. The org operates under this version.                   |
| `amending`    | An amendment proposal is in debate/vote. Old text still in force. |
| `amended`     | Amendment applied; new version ratified. Re-enters `ratified`.   |
| `rejected_amendment` | Amendment vote failed. Re-enters `ratified`. (Transient record.) |

### 8.2 Diagram

```
   draft()
     │
     ▼
  ┌────────┐  ratify (template auto / founding vote)
  │ draft  │────────────►┌──────────┐
  └────────┘              │ ratified │◄────────────────────┐
                          └────┬─────┘                    │
                               │ amendment.proposed        │
                               ▼                          │
                          ┌──────────┐                    │
                          │ amending │                     │
                          └────┬─────┘                     │
                               │                           │
                  ┌────────────┴────────────┐              │
                  │                         │              │
            vote.passed                vote.failed         │
                  │                         │              │
                  ▼                         ▼              │
          ┌──────────┐           ┌──────────────────────┐  │
          │ amended  │           │ rejected_amendment   │  │
          └────┬─────┘           └──────────┬───────────┘  │
               │                            │              │
               └────────────────────────────┴──────────────┘
                        (both return to ratified)
```

### 8.3 Transitions, Guards, Actions

| From → To            | Event                    | Guard                              | Actions                                                  |
|----------------------|--------------------------|------------------------------------|----------------------------------------------------------|
| draft → ratified     | `ratify`                 | All required sections present      | Emit `constitution.ratified`; Org SM → `constitution.ratified` |
| ratified → amending   | `amendment.proposed`     | Evolution SM of type `amend_constitution` in `debating` | Emit `constitution.state`; old text remains in force |
| amending → amended   | `amendment.vote.passed`  | Evolution SM `approved`            | Persist new version; bump version number; emit `constitution.amended` |
| amending → rejected_amendment | `amendment.vote.failed` | Evolution SM `rejected`  | Emit `constitution.amendmentRejected`                   |
| amended → ratified   | `version.locked`         | —                                  | Emit `constitution.state`                                |
| rejected_amendment → ratified | `dismiss`        | —                                  | Emit `constitution.state`                                |

### 8.4 In-Force Invariant

While in `amending`, the **previous ratified version remains in
force** for all decisions. An amendment never takes effect mid-vote.
This is enforced by the Constitution Engine reading the
`ratified_version_id`, not the `pending_version_id`, when evaluating
decision rules.

---

## 9. Cross-Machine Interactions

The machines are not independent. A transition in one frequently
submits an event to another. The rule: **a parent machine owns the
lifecycle of its child machines**, and child machines report outcomes
upward via events. There is no direct cross-child coupling.

```
   Organization
        │
        ├───► Investigation  (1 org : N investigations)
        │         │
        │         ├───► Meeting        (1 inv : N meetings, debate phase)
        │         └───► Worker        (via task assignment)
        │
        ├───► Evolution      (1 org : N proposals, one executing at a time)
        │         │
        │         ├───► Meeting        (debate + vote)
        │         ├───► Worker        (hire/fire/promote effects)
        │         ├───► Constitution  (if type = amend_constitution)
        │         └───► (self)         (if type = dna_shift)
        │
        ├───► Constitution    (1 org : 1 constitution)
        │
        └───► Worker          (1 org : N workers)
```

### 9.1 Interaction Table

| Trigger (parent)                      | Effect (child)                                  |
|---------------------------------------|-------------------------------------------------|
| Org `evolution.proposed`              | Spawn Evolution SM; Org → `evolving`             |
| Evolution `debate.start`              | Spawn Meeting SM (type `evolution_debate`)       |
| Evolution `approved` (type `hire`)    | Spawn Worker SM in `idle`                        |
| Evolution `approved` (type `fire`)    | Worker SM → `terminated`                         |
| Evolution `approved` (type `amend_constitution`) | Constitution SM → `amending`          |
| Evolution `applied`                   | Org → `evolution.applied` → `active`             |
| Investigation `analysis.done`          | Spawn Meeting SM (type `investigation_debate`)   |
| Investigation `synthesizing` (low conf.) | Worker SMs re-assigned → `researching`        |
| Meeting `started`                     | Participant Worker SMs → `in_meeting`           |
| Meeting `concluded` (investigation)   | Investigation `debate.converged`                |
| Meeting `concluded` (evolution vote)  | Evolution `votes.closed`                         |
| Constitution `ratified`               | Org (in `forming`) → `active`                    |

### 9.2 Worked Example — "Should I build an AI healthcare startup?"

```
1.  POST /investigations                         → Investigation: created
2.  Investigation: created → planning             → Planner runs
3.  Investigation: planning → researching         → 6 Worker SMs → assigned → working
4.  Workers: working → reporting (×12 evidence)   → Investigation evidence pool grows
5.  Investigation: researching → analyzing        → Workers analyze
6.  Investigation: analyzing → debating           → Meeting SM spawned (scheduled)
7.  Meeting: scheduled → preparing → active      → 6 Workers → in_meeting
8.  Engineering challenges Research's evidence    → Evidence SM: open → challenged
9.  Meeting: active → voting                      → weighted votes cast
10. Meeting: voting → concluded (to_vote)         → Workers → idle; Investigation: debate.converged
11. Investigation: debating → synthesizing        → CEO Worker → working
12. CEO confidence = 0.41 < threshold 0.7         → Investigation: synthesizing → researching
                                                  → (low-confidence loop, §4.4)
13. (loop 2) synthesizing confidence = 0.78      → Investigation: synthesizing → reporting
14. Investigation: reporting → completed           → report ready; stream closes
```

---

## 10. Persistence & Recovery

### 10.1 Storage

Each machine's current state and `state_history` are persisted in the
`state_machines` table (see `13-database-design.md`). Schema sketch:

```
state_machines
─────────────────────────────────────────────
machine_id      ULID PK
machine_type    ENUM(org, investigation, meeting, worker, evolution, constitution)
parent_id       ULID NULL        -- e.g., investigation's org_id
current_state   TEXT
context         JSONB            -- machine-specific working data
version         BIGINT           -- optimistic concurrency
updated_at      TIMESTAMPTZ
```

```
state_transitions
─────────────────────────────────────────────
machine_id      ULID  → state_machines
seq             BIGINT
from_state      TEXT
to_state        TEXT
event           TEXT
event_id        ULID   -- idempotency key
guard_result    BOOLEAN
action_log      JSONB
occurred_at     TIMESTAMPTZ
PRIMARY KEY (machine_id, seq)
UNIQUE (machine_id, event_id)
```

### 10.2 Recovery

On restart, the engine loads each machine's `current_state` and
re-subscribes to its event sources. Because transitions are
persisted-before-published and idempotent on `event_id`, replay is
safe: a downstream event already applied is a no-op. In-flight
investigations resume from their persisted state; in-flight meetings
are moved to `cancelled` with `reason: "recovery"` if the live
stream cannot be reconnected within the heartbeat window (see
`24-api-design.md` §10.4).

### 10.3 Optimistic Concurrency

Every transition increments `version`. A submit that reads an
out-of-date `version` is rejected with `409 stale_version` and the
client retries with the fresh snapshot. This handles the case where
two departments try to drive an investigation forward simultaneously.

---

## 11. Event Emission Summary

Every transition emits exactly one canonical event on the bus (see
`16-event-system.md`), consumed by the API gateway (for WebSocket
streams, `24-api-design.md` §10), the trust engine, the memory
system, and the live dashboard.

| machine      | event prefix          | example                                   |
|--------------|-----------------------|-------------------------------------------|
| Organization | `organization.*`     | `organization.stateChanged`               |
| Investigation| `investigation.*`    | `investigation.state`, `investigation.confidence` |
| Meeting      | `meeting.*`           | `meeting.started`, `meeting.concluded`    |
| Worker       | `worker.*`            | `worker.state`, `worker.promoted`         |
| Evolution    | `evolution.*`         | `evolution.applied`, `evolution.rejected`|
| Constitution | `constitution.*`      | `constitution.amended`                    |

Events carry `machine_id`, `parent_id`, `from_state`, `to_state`,
`event`, `seq`, and a typed `payload`. Subscribers filter by
`machine_type` and `parent_id` to scope their streams — which is how
the API gateway turns a single bus topic into per-investigation and
per-meeting WebSocket channels without a firehose.

---

## 12. Cross-Document References

| Topic                       | See                                              |
|-----------------------------|--------------------------------------------------|
| API surface & error codes   | `24-api-design.md`                               |
| Event bus internals         | `16-event-system.md`                              |
| Database schema             | `13-database-design.md`                           |
| Investigation orchestration  | `19-investigation-engine.md`                     |
| Meeting mechanics           | `20-meeting-engine.md`                            |
| Planner                     | `18-planner-design.md`                            |
| Trust & weighted voting     | `08-trust-engine.md`                              |
| Evolution engine            | `07-evolution-engine.md`                           |
| Constitution engine         | `09-constitution-engine.md`                       |
| DNA & thresholds            | `21-organizational-dna.md`                         |
| Memory persistence          | `06-memory-system.md`                              |
| Worker/department design    | `05-agent-architecture.md`                         |
| Live dashboard rendering    | `22-mobile-ux.md`                                  |
