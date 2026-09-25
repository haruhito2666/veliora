# 07 — Organizational Evolution Engine

> **The organization modifies itself.**
>
> Veliora does not "tune agents." It restructures itself — hiring, firing,
> promoting, splitting departments, rewriting policy, amending its own
> constitution — based on observed performance, not hardcoded rules.

This document specifies the **Evolution Engine**: the subsystem that detects
when the organization's structure is suboptimal, proposes structural
mutations, evaluates them through debate and voting, and executes the
approved mutations. It is one of the twelve novel research contributions
(see `02-research-contributions.md`, contribution #12: Organization Evolution).

---

## 1. Purpose & Scope

The Evolution Engine is the **meta-cognitive loop** of the organization.
While the Investigation Engine (see `19-investigation-engine.md`) answers
external questions, the Evolution Engine answers an internal one:

> *Is the current organizational structure the right one to accomplish our
> mission, and if not, what should we change about ourselves?*

### 1.1 The "One Agent" Test

Evolution is the clearest pass of the One Agent Test in the entire system.
None of the following actions are meaningful with a single agent:

| Evolution Action      | Single-Agent Equivalent | Passes Test? |
|----------------------|-------------------------|--------------|
| Hire                 | N/A — one agent cannot hire another | ✅ |
| Fire                 | N/A — one agent cannot fire itself usefully | ✅ |
| Promote / Demote     | Meaningless without peers to be promoted above | ✅ |
| Create Department    | N/A — one agent has no departments | ✅ |
| Split Department     | N/A — requires ≥1 department with ≥2 members | ✅ |
| Merge Departments    | N/A — requires ≥2 departments | ✅ |
| Rewrite Policy       | Trivially a config edit for one agent; only meaningful as a *political act* between factions | ✅ |
| Change Reporting Line| N/A — one agent has no reporting structure | ✅ |
| Amend Constitution   | Trivially self-prompt editing; only meaningful as governance | ✅ |

If Veliora were a single LLM, this entire document would be empty. It is
not. Therefore the engine exists.

### 1.2 What Evolution Is Not

- **Not hyperparameter tuning.** We are not adjusting learning rates or
  prompt temperatures. We are changing the *shape* of the org.
- **Not a meta-prompt.** Evolution is debated, voted on, and recorded —
  not silently applied.
- **Not a restart.** Evolution preserves institutional memory
  (see `06-memory-system.md`); fired workers leave behind their work,
  promoted workers inherit their prior reputation.
- **Not deterministic.** Two organizations with identical performance data
  but different DNA (see `21-organizational-dna.md`) will evolve
  differently. That is by design.

---

## 2. System Position

```
                         ┌──────────────────────────────────────┐
                         │           Organization               │
                         │  ┌────────┐  ┌────────┐  ┌────────┐  │
                         │  │ Agents │  │  Depts  │  │ Const. │  │
                         │  └───┬────┘  └───┬────┘  └───┬────┘  │
                         │      │           │           │       │
                         │      ▼           ▼           ▼       │
                         │  ┌─────────────────────────────────┐ │
                         │  │      Telemetry Bus (16)         │ │
                         │  └─────────────┬───────────────────┘ │
                         └────────────────┼──────────────────────┘
                                          │
                          ┌───────────────▼────────────────┐
                          │     Evolution Engine (07)      │
                          │  ┌──────────────────────────┐  │
                          │  │ 1. Trigger Detection     │  │
                          │  │ 2. Proposal Generation   │  │
                          │  │ 3. Proposal Evaluation   │  │
                          │  │ 4. Execution             │  │
                          │  └──────────────────────────┘  │
                          └───────────────┬────────────────┘
                                          │
                          ┌───────────────▼────────────────┐
                          │   Constitution Engine (09)      │
                          │   (gates amendments)            │
                          └───────────────┬────────────────┘
                                          │
                          ┌───────────────▼────────────────┐
                          │   Trust Engine (08)             │
                          │   (reputation after mutation)   │
                          └────────────────────────────────┘
```

The engine consumes **telemetry** from every worker, department, meeting,
and investigation (see `16-event-system.md`), and emits **mutation events**
back into the org. The Constitution Engine (see `09-constitution-engine.md`)
gates any amendment; the Trust Engine (see `08-trust-engine.md`) recomputes
reputation after structural changes.

---

## 3. Evolution Triggers

Evolution is **reactive**: it begins only when a trigger fires. There are
four trigger families. Each is computed from rolling telemetry windows
over the last `W` events (default `W = 50 investigations` or `30 days`,
whichever comes first).

### 3.1 Trigger Catalogue

| ID | Trigger Family       | Signal                                            | Threshold (default)             |
|----|----------------------|---------------------------------------------------|---------------------------------|
| T1 | Performance Metrics  | Org-wide success rate vs. historical baseline      | `success_rate < baseline − 0.10`|
| T2 | Repeated Failures    | Same failure class recurs ≥ N times in window     | `N = 3`                         |
| T3 | Workload Imbalance   | Gini coefficient of work-hours across departments  | `Gini > 0.55`                   |
| T4 | Strategic Shift      | Mission-relevant topic drift > Δ in embedding space| `cos(θ) < 0.72` vs. last quarter |

Each trigger carries a **severity** in `[0,1]` computed by the function in
§6.2. Severity gates how aggressive the resulting proposals may be.

### 3.2 T1 — Performance Metrics

Computed from the `InvestigationOutcome` records written by the
Investigation Engine. The baseline is the org's own trailing 90-day success
rate; an absolute floor is also enforced so a young org does not evolve
frantically from a small sample.

```
severity_T1 = clamp( (baseline - current_rate) / baseline, 0, 1 )
```

### 3.3 T2 — Repeated Failures

A *failure class* is the tuple `(department, failure_mode, topic_cluster)`.
The Memory System (see `06-memory-system.md`) tags every failed
investigation with this tuple. When the same tuple recurs `≥ N` times, T2
fires. This is the trigger most likely to produce a *fire*, *demote*, or
*rewrite policy* action — the org has evidence that something specific is
broken.

### 3.4 T3 — Workload Imbalance

Work-hours per department are emitted as `WorkloadReport` events. We
compute the Gini coefficient across departments:

```
        n * Σ_i ( (2i - n - 1) * x_i )
Gini = ─────────────────────────────────
              n² * μ_x
```

where `x_i` are sorted department work-hours and `μ_x` is their mean. A
Gini above 0.55 means one or two departments are saturated while others
idle. This is the trigger most likely to produce a *hire*, *split*, or
*create department* action.

### 3.5 T4 — Strategic Shift

The org's mission embedding is recomputed weekly from the topics of
accepted investigations. If the current mission embedding drifts more than
a threshold from the prior quarter's, the org's structure may no longer
match what it is being asked to do. This is the trigger most likely to
produce a *create department*, *merge*, or *amend constitution* action.

### 3.6 Trigger Suppression

To prevent oscillation, a trigger that fired and led to an executed
mutation is **suppressed** for a cooldown period `C` (default `C = 7 days`
or `5 investigations`). The engine records `(trigger_id, mutation_id,
expires_at)` in the `evolution_cooldowns` table.

---

## 4. Evolution Actions

Actions are the *verbs* of evolution. Each action is a structured mutation
with a typed payload, an *authority level* (who can propose it), and a
*reversibility* flag.

| ID | Action                | Payload (sketch)                                  | Authority       | Reversible? |
|----|----------------------|----------------------------------------------------|-----------------|-------------|
| A1 | Hire                 | `{ role, dept, dna_profile, sponsor }`             | Dept Head       | Yes (Fire)  |
| A2 | Fire                 | `{ worker_id, reason, severance_memory }`         | Dept Head + CEO | No          |
| A3 | Promote              | `{ worker_id, new_level, new_authority }`         | Dept Head       | Yes (Demote)|
| A4 | Demote               | `{ worker_id, new_level, reason }`                | Dept Head + CEO | Yes         |
| A5 | Create Department    | `{ name, mission, seed_workers, dna }`             | CEO             | Yes         |
| A6 | Split Department     | `{ dept_id, split_axis, child_a, child_b }`        | CEO             | No          |
| A7 | Merge Departments    | `{ dept_a, dept_b, new_name, new_head }`           | CEO             | No          |
| A8 | Rewrite Policy       | `{ policy_id, old_text, new_text, rationale }`    | Any Head        | Yes         |
| A9 | Change Reporting     | `{ worker_id, old_manager, new_manager, reason }`  | CEO             | Yes         |
| A10| Amend Constitution   | `{ article_id, old_text, new_text, rationale }`   | Supermajority   | Yes         |

### 4.1 Action Semantics

- **Hire (A1)** creates a new worker via the Agent Architecture (see
  `05-agent-architecture.md`). The new worker inherits a *role DNA* derived
  from the department DNA and the sponsor's profile. It starts at trust
  `T₀ = 0.5` (see `08-trust-engine.md`) — not zero, so it can participate
  immediately, but not high enough to sway votes alone.
- **Fire (A2)** removes a worker but **preserves their authored artifacts**
  in the Knowledge Graph (see `14-knowledge-graph.md`). The worker's
  reputation is archived; if rehired within 90 days, prior reputation is
  restored at 0.8× to honor institutional memory without rewarding churn.
- **Promote / Demote (A3/A4)** change a worker's `influence_weight` in
  votes. Promotion above a threshold (default `0.8`) grants the worker
  *authority* to propose A5–A10 actions.
- **Create Department (A5)** is the engine's primary *growth* verb. The
  new department starts with a seed of 1–3 workers seconded from existing
  departments and inherits a department DNA.
- **Split (A6)** partitions a department along an explicit *split axis*
  (e.g., "frontend vs. backend", "research vs. applied"). Workers are
  assigned to a child by clustering on their recent work embeddings.
- **Merge (A7)** dissolves two departments into one. The new head is
  elected by the merged department's members; ties broken by the CEO.
- **Rewrite Policy (A8)** edits a single named policy (`policy_id`). All
  policy text is versioned; the prior version remains queryable.
- **Change Reporting (A9)** rewires the org chart. It is the cheapest
  structural change and the most common remedy for T3 (workload imbalance).
- **Amend Constitution (A10)** is the most consequential action. It is
  gated by the Constitution Engine (see `09-constitution-engine.md`) and
  requires a supermajority (default `2/3` of weighted votes).

### 4.2 Action Side-Effects

Every action emits a structured event onto the telemetry bus so that
downstream engines can recompute their views:

```
event: OrgMutated
payload: { action_id, mutation_id, affected_workers[], affected_depts[],
          before_snapshot, after_snapshot, rationale, enacter }
```

The Trust Engine listens for `OrgMutated` and recomputes influence weights;
the Memory System writes an `EvolutionRecord` (see §8) into long-term
memory; the UI (see `22-mobile-ux.md`) surfaces the mutation in the
organization's activity feed.

---

## 5. The Evolution Algorithm

Evolution runs as a **four-phase pipeline**. Each phase is a separate
state in the engine's state machine (see §6) and is checkpointed to the
database so a crash mid-evolution does not leave the org half-mutated.

```
┌──────────┐   triggers   ┌──────────┐  proposals  ┌──────────┐  verdict  ┌──────────┐
│  IDLE    │─────────────▶│ DETECTING │────────────▶│ EVALUATING │──────────▶│ EXECUTING│
└──────────┘             └──────────┘             └──────────┘            └──────────┘
     ▲                          │                        │                       │
     │           no triggers    │  proposals rejected    │  mutation applied    │
     └──────────────────────────┴────────────────────────┴───────────────────────┘
```

### 5.1 Phase 1 — Detect

The engine polls the telemetry bus on a configurable cadence (default:
end of every investigation, or every 24h, whichever is sooner). For each
trigger family, it computes the current signal value and severity. If any
severity `> 0`, the engine transitions to **Propose** with the highest-
severity trigger as the lead.

### 5.2 Phase 2 — Propose

Proposals are **generated by the org, not by the engine**. The engine
convenes a *Mutation Proposal Meeting* (a special meeting type in the
Meeting Engine, see `20-meeting-engine.md`) attended by:

- The CEO (always)
- The head of the affected department (always)
- A rotating *Devil's Advocate* worker (rotates per meeting to prevent
  groupthink; selected by lowest correlation to the affected dept head)
- Any worker whose `influence_weight ≥ 0.8` (the "senior staff")

The lead trigger is presented as evidence. Each attendee proposes one or
more candidate actions. The engine does **not** write proposals itself;
it only hosts the meeting and records the proposals. This is the
emergence boundary (see §7): the *need* for evolution is detected
algorithmically, but the *form* of evolution is proposed socially.

### 5.3 Phase 3 — Evaluate

Each proposal is scored by the evaluation algorithm in §9. The top
scoring proposals (default: top 3) are put to a **weighted vote** of all
workers, with weights from the Trust Engine. The Constitution Engine may
veto any proposal that violates the constitution; an A10 (Amend
Constitution) proposal is automatically routed through the constitution's
amendment process instead of the normal vote.

A proposal passes if:

```
weighted_yes_votes / (weighted_yes_votes + weighted_no_votes)  ≥  threshold(action)
```

Default thresholds:

| Action class        | Threshold |
|---------------------|-----------|
| A1, A3, A4, A8, A9  | 0.50      |
| A2, A6, A7          | 0.60      |
| A5                  | 0.60      |
| A10                 | 0.67      |

### 5.4 Phase 4 — Execute

The engine applies the winning mutation through the Agent Architecture's
mutation API. Execution is **transactional**: all sub-changes for a
mutation either commit together or roll back. After commit, the engine
writes an `EvolutionRecord`, lifts the trigger cooldown, and returns to
IDLE.

---

## 6. State Diagram

The engine itself is a finite state machine. Transitions are guarded by
trigger severity, proposal count, vote outcome, and execution success.

```
                            ┌─────────────────────────────────────┐
                            │                                     │
                            ▼                                     │
                       ┌────────┐  no trigger  ┌────────┐          │
        ───────────────▶│  IDLE  │────────────▶│ COOLDOWN│          │
        start           └────┬───┘             └────┬───┘          │
                            │                      │              │
                  trigger   │  severity>0          │ cooldown     │
                  poll      ▼                      │ expired      │
                       ┌────────┐                  └──────────────┘
                       │ DETECT │
                       └────┬───┘
                            │ triggers ranked
                            ▼
                       ┌──────────┐  0 proposals
                       │ PROPOSE  │───────────┐
                       └────┬─────┘           │
                            │ ≥1 proposal    │
                            ▼                │
                       ┌──────────┐          │
                       │ EVALUATE │          │
                       └────┬─────┘          │
                            │                │
              ┌─────────────┼────────────┐   │
              │             │            │   │
        all rejected   constitution   pass │
              │          veto            │   │
              │             │            ▼   │
              │             ▼       ┌────────┐
              │        ┌─────────┐  │ EXECUTE│
              │        │  VETOED │  └────┬───┘
              │        └────┬────┘       │
              │             │            │
              │             │     ┌──────┴──────┐
              │             │     │             │
              │             │  success       failure
              │             │     │             │
              │             │     ▼             ▼
              │             │  ┌────────┐  ┌─────────┐
              │             │  │ COMMIT │  │ ROLLBACK│
              │             │  └────┬───┘  └────┬────┘
              │             │       │           │
              └─────────────┴───────┴───────────┘
                              │
                              ▼
                         ┌────────┐
                         │ COOLDOWN│
                         └─────────┘
```

### 6.1 State Definitions

| State     | Entry condition                  | Exit condition                          |
|-----------|----------------------------------|-----------------------------------------|
| IDLE      | Engine start / after COMMIT      | Trigger poll finds severity > 0         |
| DETECT    | Severity > 0                     | Triggers ranked                         |
| PROPOSE   | Triggers ranked                  | ≥1 proposal or meeting timeout         |
| EVALUATE  | ≥1 proposal                      | Vote complete or constitutional veto    |
| VETOED    | Constitution Engine rejects     | Auto-transition to COOLDOWN after log   |
| EXECUTE   | Proposal passes vote             | Mutation applied or execution error     |
| COMMIT    | Mutation applied successfully   | Auto-transition to COOLDOWN             |
| ROLLBACK  | Execution error                  | Auto-transition to COOLDOWN after log   |
| COOLDOWN  | Any terminal transition          | Cooldown timer expires (default 24h)    |

### 6.2 Trigger Severity Function

Each trigger family contributes a severity in `[0,1]`. The engine takes
the max across families as the lead severity, but also computes a
**composite** to detect multi-signal stress:

```
severity_composite = 1 - Π_family (1 - severity_family)
```

If `severity_composite ≥ 0.75`, the engine enters an **urgent** mode in
which the PROPOSE meeting is convened immediately rather than at the next
scheduled cadence, and the cooldown is halved.

---

## 7. Emergence vs Programming

The central design tension: how much of evolution is *algorithmic* and
how much is *emergent*? Veliora errs toward emergence, but uses
algorithms where emergence is unsafe or too slow.

### 7.1 The Emergence Boundary

```
   Algorithmic (engine-coded)          │   Emergent (org-produced)
   ─────────────────────────────────    │   ────────────────────────────
   Trigger detection                    │   Proposal content
   Severity computation                 │   Choice of action type
   Meeting convening                    │   Rationale text
   Vote weighting                       │   Debate arguments
   Threshold enforcement                │   Constitution interpretation
   Transactional execution             │   Whether to evolve at all (vote)
```

The rule: **the engine detects *that* the org should evolve; the org
decides *how*.** This is the same separation as in the Investigation
Engine (the engine orchestrates, the org investigates).

### 7.2 Why Not Hardcoded Rules?

A naive design would map triggers directly to actions: `T3 → A1 (hire)`.
Veliora rejects this for three reasons:

1. **Context matters.** A workload imbalance in a 3-person startup org
   should produce a *hire*; in a 50-person org it should produce a
   *split* or *rebalance*. The org knows its own size; the engine does not.
2. **Politics matters.** Marketing may argue the imbalance is because
   Engineering is slow, not because Engineering is overloaded. That
   argument is a *debate*, not a function.
3. **DNA matters.** A high-Hierarchy org should resist structural changes
   that a flat org should embrace (see §8). Hardcoded rules cannot encode
   this without becoming a forest of conditionals.

### 7.3 Where Hardcoding Is Allowed

The engine hardcodes only **safety invariants**, never structural
decisions:

- A department cannot fall below 1 worker (would create an empty dept).
- A worker cannot be fired while leading an active investigation.
- A constitution amendment cannot remove the org's *Core Values* article
  (the Constitution Engine enforces this; see `09-constitution-engine.md`).
- A mutation that would reduce org-wide trust below `0.30` is auto-vetoed.

---

## 8. DNA Influence on Evolution

Organizational DNA (see `21-organizational-dna.md`) modulates every stage
of the evolution pipeline. DNA is a 9-dimensional vector in `[0,1]`:
`Innovation`, `Risk`, `Hierarchy`, `Accuracy`, `Speed`, `Transparency`,
`Creativity`, `LongTerm`, `Security`.

### 8.1 DNA → Evolution Modulation Table

| DNA Parameter     | Effect on Evolution                                              | Stage Affected   |
|-------------------|------------------------------------------------------------------|------------------|
| Innovation ↑      | A5 (Create Dept) and A6 (Split) proposals get bonus score       | Evaluate         |
| Innovation ↓      | A7 (Merge) and A8 (Rewrite Policy) get bonus score                | Evaluate         |
| Risk ↑            | Lowers vote threshold for all actions by up to 0.10             | Evaluate         |
| Risk ↓            | Raises vote threshold for A2 (Fire), A6, A7 by up to 0.10       | Evaluate         |
| Hierarchy ↑       | Slower structural changes: cooldown ×1.5, severity floor ×1.2   | Detect, Execute  |
| Hierarchy ↓       | Faster structural changes: cooldown ×0.7                       | Execute          |
| Accuracy ↑        | Requires more evidence in PROPOSE meeting (min 2 citations)    | Propose          |
| Speed ↑           | PROPOSE meeting timeout shortened                                | Propose          |
| Transparency ↑    | All mutations published to user feed immediately                 | Execute          |
| Creativity ↑      | A8 (Rewrite Policy) proposals get bonus score                   | Evaluate         |
| LongTerm ↑        | T4 (Strategic Shift) severity weighted higher                   | Detect           |
| Security ↑        | A2 (Fire) and A10 (Amend) require supermajority regardless      | Evaluate         |

### 8.2 Worked DNA Examples

**Example A — High Innovation (0.9), Low Hierarchy (0.2):**
A startup society hits T3 (workload imbalance in Engineering). The
PROPOSE meeting generates three candidates: hire one engineer, split
Engineering into "Frontend" and "Backend", or create a new "DevOps"
department. The Innovation bonus lifts the *create* and *split* scores;
the low Hierarchy shrinks the cooldown. Result: a new DevOps department
is created within 24h.

**Example B — Low Innovation (0.2), High Hierarchy (0.9):**
The same trigger in a traditional law-firm society. The Innovation
penalty suppresses *create* and *split*; the Hierarchy multiplier raises
the severity floor so the trigger barely fires. The PROPOSE meeting
generates one candidate: hire one paralegal. The cooldown is 1.5×
longer. Result: a single hire, after 3 days of debate.

**Example C — High Security (0.9), High LongTerm (0.8):**
A healthcare org hits T2 (repeated compliance failures). Security
forces A2 (Fire) to require supermajority; LongTerm amplifies T4
severity, surfacing a strategic mismatch. The PROPOSE meeting
generates an A10 (Amend Constitution) to add a "Patient Safety First"
article. The amendment passes supermajority and is recorded permanently.

### 8.3 DNA Drift

DNA is not immutable. Repeated evolution of a particular kind shifts
DNA in the direction of the successful mutations:

```
Innovation_DNA ← Innovation_DNA + λ * (success_innovative_mutations
                                       - success_conservative_mutations)
```

with `λ = 0.02` per committed mutation, clamped to `[0,1]`. An org that
successfully innovates becomes more innovative; one that fails becomes
more conservative. This is the engine's only feedback loop into DNA;
all other DNA changes are explicit A10 amendments.

---

## 9. Proposal Evaluation Algorithm

Each candidate proposal is scored on five weighted dimensions. The
weights are derived from DNA so that the org's values shape which
proposals it prefers.

### 9.1 Score Dimensions

| Dimension    | Symbol | Question it answers                          | Default Weight |
|--------------|--------|----------------------------------------------|---------------|
| Efficacy     | `E`    | Does it address the trigger's root cause?     | 0.30          |
| Cost         | `C`    | How disruptive is the mutation?              | 0.20          |
| Evidence     | `V`    | Is there supporting evidence in memory?      | 0.20          |
| Reversibility| `R`    | Can we undo it if it fails?                  | 0.15          |
| Alignment    | `A`    | Does it fit the mission & constitution?      | 0.15          |

### 9.2 Scoring Function

Each dimension is scored in `[0,1]` by a combination of LLM-as-judge
(the PROPOSE meeting attendees) and deterministic signals (memory
lookups, cost heuristics). The final score is:

```
score(proposal) = Σ_dim  w_dim * s_dim(proposal)

where weights are DNA-modulated:

w_Efficacy    = 0.30 * (1 + 0.5 * Accuracy_DNA)
w_Cost        = 0.20 * (1 + 0.5 * (1 - Risk_DNA))
w_Evidence    = 0.20 * (1 + 0.5 * Accuracy_DNA)
w_Reversibility= 0.15 * (1 + 0.5 * (1 - Innovation_DNA))
w_Alignment   = 0.15 * (1 + 0.5 * LongTerm_DNA)

# then renormalized so Σ w = 1
```

### 9.3 Efficacy Score (`E`)

Efficacy is computed by an LLM prompt that takes the trigger evidence,
the proposed action, and the affected department's recent history, and
returns a score in `[0,1]`:

```
prompt: |
  Trigger: {trigger_id, severity, evidence}
  Proposal: {action_id, payload}
  Affected dept history: {last 10 outcomes}
  Question: How likely is this proposal to resolve the trigger's
  root cause? Answer with a single float in [0,1] and one sentence.
```

The meeting attendees each produce an `E` score; the final `E` is the
trust-weighted average (see `08-trust-engine.md`).

### 9.4 Cost Score (`C`)

Cost is deterministic. Each action has a base cost; modifiers apply for
org size and affected population.

```
base_cost = {
  A1: 0.10, A2: 0.40, A3: 0.05, A4: 0.15, A5: 0.35,
  A6: 0.55, A7: 0.60, A8: 0.20, A9: 0.10, A10: 0.80
}[action_id]

C = 1 - base_cost * (1 + log(1 + affected_workers) / 4)
```

`C` is inverted (higher is better) because low cost is good; the formula
yields `C ∈ [0,1]`.

### 9.5 Evidence Score (`V`)

The Memory System is queried for prior occurrences of similar proposals
and their outcomes:

```
V = (prior_similar_proposals_passed + 1) /
    (prior_similar_proposals_total  + 2)   # Laplace smoothing
```

A proposal with no precedent scores `0.5` (uniform prior). A proposal
type that has historically failed scores low.

### 9.6 Reversibility Score (`R`)

```
R = reversibility_flag(action) ? 0.8 : 0.2
```

Reversible actions (A1, A3, A4, A5, A8, A9, A10) score 0.8; irreversible
ones (A2, A6, A7) score 0.2. The 0.8 / 0.2 split (rather than 1.0 / 0.0)
reflects that even "reversible" actions leave traces in memory.

### 9.7 Alignment Score (`A`)

The Constitution Engine scores the proposal against the org's mission
and constitution by an embedding-similarity check plus an LLM-as-judge
safety check. A proposal that violates a constitutional principle is
scored `0.0` and **auto-vetoed** regardless of total score.

### 9.8 Final Ranking

```
function rank(proposals, org):
    scored = []
    for p in proposals:
        if constitution_veto(p): continue
        s = score(p, org)
        s *= dna_bonus(p, org.dna)        # §8.1 bonuses
        scored.append((p, s))
    scored.sort(by=s, desc)
    return scored[:3]                     # top 3 to vote
```

---

## 10. Worked Example — End to End

**Scenario:** A startup society has 5 workers (CEO, Research, Engineering,
Finance, Marketing). Over 30 days, Engineering accounts for 71% of
work-hours (Gini = 0.62 > 0.55), and two Engineering-led investigations
failed due to underestimated technical complexity.

### Step 1 — Detect

```
T3 (Workload Imbalance): severity = (0.62 - 0.55) / 0.45 = 0.16
T2 (Repeated Failures):  severity = 2/3 = 0.67   (lead)
composite = 1 - (1-0.16)(1-0.67) = 0.72
```

Composite < 0.75, so not urgent; PROPOSE meeting scheduled at next
cadence.

### Step 2 — Propose

Meeting attendees: CEO, Engineering Head, Devil's Advocate (Finance),
Senior Staff (none yet — org too small). Three proposals:

1. **A1 Hire** a second Engineer with "Backend" specialization.
2. **A5 Create** a new "DevOps" department, seconding 0 workers, hiring 1.
3. **A8 Rewrite Policy** "Engineering estimates must be peer-reviewed by
   Research before commitment."

### Step 3 — Evaluate

Assume `Innovation_DNA = 0.7`, `Risk_DNA = 0.5`, `Accuracy_DNA = 0.6`.

| Proposal | E    | C    | V    | R    | A    | DNA bonus | Total  |
|----------|------|------|------|------|------|-----------|--------|
| P1 Hire  | 0.78 | 0.90 | 0.62 | 0.80 | 0.85 | ×1.00     | **0.79**|
| P2 Create| 0.72 | 0.65 | 0.50 | 0.80 | 0.80 | ×1.15 (Inn)| **0.70**|
| P3 Policy| 0.81 | 0.80 | 0.55 | 0.80 | 0.90 | ×1.00     | **0.77**|

Top 3 to vote. After weighted vote (weights from Trust Engine):
P1 = 0.61, P3 = 0.55, P2 = 0.48. P1 passes the 0.50 threshold.

### Step 4 — Execute

Engine calls `agent_architecture.hire(role="Engineer", dept="Engineering",
dna_profile=backend_specialist, sponsor="Engineering Head")`. New worker
created with `T₀ = 0.5`. `OrgMutated` event emitted. Cooldown set for T2
and T3 for 7 days. `EvolutionRecord` written.

### Step 5 — Feedback

Over the next 30 days, Engineering's Gini drops to 0.41, and no
technical-complexity failures recur. The mutation is marked *successful*
in memory; `Innovation_DNA` drifts up by `λ = 0.02` (because the
innovative P2 was *not* chosen, drift is actually 0 here — drift is keyed
to the *kind* of mutation that succeeded, and P1 is a conservative hire).

---

## 11. Data Model

The engine persists four primary tables (see `13-database-design.md` for
the full schema).

### 11.1 `evolution_triggers`

```
trigger_id        UUID PK
org_id            UUID FK
family            ENUM(T1,T2,T3,T4)
signal_value      FLOAT
severity          FLOAT
detected_at       TIMESTAMP
lead              BOOLEAN        # true if highest severity this cycle
```

### 11.2 `evolution_proposals`

```
proposal_id       UUID PK
trigger_id        UUID FK
action_id         ENUM(A1..A10)
payload           JSONB
proposer_id       UUID FK -> workers
score             FLOAT
rank              INT
vote_threshold    FLOAT
created_at        TIMESTAMP
```

### 11.3 `evolution_mutations`

```
mutation_id       UUID PK
proposal_id       UUID FK
state             ENUM(COMMITTED, ROLLED_BACK, VETOED)
before_snapshot   JSONB
after_snapshot    JSONB
enacter_id        UUID FK -> workers
committed_at      TIMESTAMP
outcome           ENUM(SUCCESS, FAILURE, PENDING)  # filled later
```

### 11.4 `evolution_cooldowns`

```
cooldown_id       UUID PK
trigger_id        UUID FK
mutation_id       UUID FK
expires_at        TIMESTAMP
```

---

## 12. API Surface

The engine exposes a minimal internal API (see `24-api-design.md`).

```
POST /orgs/{id}/evolution/poll         # force a detect cycle
GET  /orgs/{id}/evolution/triggers      # list current triggers
GET  /orgs/{id}/evolution/proposals     # list pending proposals
POST /orgs/{id}/evolution/proposals/{pid}/vote   # cast a vote
POST /orgs/{id}/evolution/mutations/{mid}/rollback  # emergency rollback
GET  /orgs/{id}/evolution/history       # paginated mutation log
```

All endpoints are *internal* — the user does not invoke evolution
directly. The user may *observe* it through the Mobile UX (see
`22-mobile-ux.md`) and may, via the constitution, hold a veto (see
`09-constitution-engine.md`).

---

## 13. Safety, Limits, and Failure Modes

### 13.1 Rate Limits

- Max **1 mutation per org per 24h** (configurable per org).
- Max **3 mutations per org per 7 days**.
- A *failing* mutation (outcome = FAILURE) doubles the cooldown for its
  action family.

### 13.2 Oscillation Prevention

If a mutation is rolled back and the same `(trigger, action)` pair is
proposed again within 14 days, the engine raises that proposal's vote
threshold by 0.10. A third attempt within 30 days auto-vetoes.

### 13.3 Cascading Evolution

A mutation may itself create a new trigger (e.g., a Merge creates
workload imbalance). To prevent infinite chains, the engine caps
**evolution depth** at 2 per cycle: a mutation triggered by a mutation
is allowed; a third-level mutation is queued for the next cycle.

### 13.4 Failure Modes

| Failure                          | Detection                  | Recovery                  |
|----------------------------------|----------------------------|---------------------------|
| PROPOSE meeting deadlocks        | Timeout (DNA-scaled)       | CEO tiebreak vote         |
| Vote quorum not reached          | <50% weighted turnout      | Re-convene after 24h      |
| Execution partial failure       | Transaction monitor        | Full rollback + log       |
| Constitution veto storm          | >3 vetoes per cycle       | Escalate to user via UI   |
| DNA drift to extreme (0 or 1)   | Periodic clamp check       | Force A10 to recalibrate  |

---

## 14. Cross-References

- `02-research-contributions.md` — contribution #12 (Organization Evolution).
- `04-technical-architecture.md` — engine placement in system topology.
- `05-agent-architecture.md` — the mutation API for hire/fire/promote.
- `06-memory-system.md` — `EvolutionRecord` and prior-outcome lookups.
- `08-trust-engine.md` — influence weights and post-mutation reputation.
- `09-constitution-engine.md` — gates A10 and auto-vetoes.
- `10-debate-engine.md` — the debate mechanics inside PROPOSE meetings.
- `11-communication-engine.md` — Mutation Proposal Meeting transport.
- `13-database-design.md` — full schema for the four evolution tables.
- `16-event-system.md` — `OrgMutated` and `WorkloadReport` events.
- `20-meeting-engine.md` — the special "Mutation Proposal" meeting type.
- `21-organizational-dna.md` — the 9-DNA vector and its semantics.
- `22-mobile-ux.md` — surfacing evolution to the user.
- `25-state-machine.md` — this engine's FSM in the global state model.

---

## 15. Summary

The Evolution Engine is what makes Veliora a *living* organization
rather than a fixed orchestration. It detects stress algorithmically,
but delegates the form of response to the org itself through debate and
vote. DNA modulates every stage so that two orgs under identical pressure
evolve differently. The engine is bounded by safety invariants, rate
limits, and the constitution — but within those bounds, the org is free
to rewrite itself. That freedom, exercised collectively, is what
"self-evolving" means.
