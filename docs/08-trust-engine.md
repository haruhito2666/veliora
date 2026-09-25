# 08 — Trust Engine

> **Reputation and weighted trust.** Records how often each worker's
> predictions, evidence, endorsements, and decisions turn out correct, and
> converts that history into a numerical trust weight that influences vote
> weight, evidence acceptance, meeting influence, hiring, promotion, and firing.

---

## 1. Purpose

The Trust Engine maintains a continuously updated reputation score for every
worker (agent) and department. It is the single source of truth for *whose
judgment the organization relies on, and how heavily*.

A single AI agent has no use for trust — there is no one to defer to, no one
whose track record competes with its own. Trust is inherently **relational**:
it exists only because the organization contains multiple workers whose
judgments must be compared and weighted. Every feature here exists because
Veliora is an organization, not an individual (the "One Agent Test," see
`01-core-philosophy.md`). This is the eighth research contribution — **Weighted
Trust** (`02-research-contributions.md`). It turns a collection of agents into
a *meritocracy*: workers who are repeatedly right gain influence; workers who
are repeatedly wrong lose it. No one programs this. It emerges from outcomes.

---

## 2. Position in the System

```
   19-investigation ──► Vote Engine ──outcomes──► ┌────────────┐
   10-debate ─────────► Evidence Gate ──endorse─► │  TRUST     │
   11-communication ──► Meeting Eng. ──outcomes─► │  ENGINE    │
   20-meeting ────────►                         │ (this)     │
                                                 │ Beta(α,β)  │
   ┌──────────────────────────────────────────┐ └─────┬──────┘
   │ 07-evolution: hire/fire/promote  ◄─weight───────┘
   │ 09-constitution: ethics penalties        │
   │ 10-debate: vote weight                   │
   │ 19-investigation: evidence acceptance     │
   └──────────────────────────────────────────┘
```

The Trust Engine is **passive**: it never initiates action. It observes
outcomes emitted by other engines and answers one question when asked:
*"How much should this worker's judgment count right now?"*

---

## 3. Trust Score Model

### 3.1 Why Bayesian

Trust is a belief about an unknown quantity — the probability that a worker's
next judgment will be correct. Beliefs should update with evidence and carry
uncertainty. A point estimate ("Research is at 96%") hides the difference
between 96/100 correct and 24/25 correct; the first is far more trustworthy,
the second might just be lucky. We model each worker's trust as a **Beta
distribution** — the conjugate prior for a Bernoulli likelihood. Its two
parameters accumulate positive and negative evidence, its mean is a clean
point estimate, and its variance gives a confidence interval for free.

### 3.2 The Beta-Bernoulli model

For each worker `i`, the engine maintains `α_i` (weighted positive outcomes)
and `β_i` (weighted negative outcomes). Trust is `Beta(α_i, β_i)`:

```
Mean:          T_i = α_i / (α_i + β_i)
Variance:      σ²_i = (α_i · β_i) / ((α_i + β_i)² · (α_i + β_i + 1))
Std. dev:      σ_i = √σ²_i
95% CI:        T_i ± 1.96 · σ_i   (normal approx.; exact Beta quantile for small n)
```

`T_i ∈ (0, 1)` is the **raw trust score** — the expected probability the
worker's next judgment is correct, given evidence so far.

### 3.3 The skeptical prior (Sybil resistance, part 1)

New workers do not start at `T = 0.5` (`Beta(1,1)`) — that would let a
freshly-spawned worker enter a vote with the same weight as a veteran. They
start with a **skeptical prior**:

```
α₀ = 1,  β₀ = 3   →   T₀ = 0.25,  σ₀ ≈ 0.19
```

This encodes a default: *a worker is untrustworthy until it proves
otherwise.* A new worker needs ~4–5 positive outcomes just to reach `T = 0.5`.
This is the first layer of Sybil resistance (full defense in §8).

### 3.4 The update rule

When an outcome event `e` is observed:

```
positive:   α_i ← α_i + w_e
negative:   β_i ← β_i + w_e
```

where `w_e` is the **event weight** (§4.3). Updates are atomic and
idempotent — each event carries a unique `event_id`; the engine deduplicates
on replay.

### 3.5 Exponential decay (regression to the prior)

Old evidence should matter less. The engine applies continuous exponential
decay toward the skeptical prior at every tick:

```
Let Δt = time since last update, λ = decay rate (org-DNA parameter).
α_i ← α₀ + (α_i − α₀) · e^(−λ·Δt)
β_i ← β₀ + (β_i − β₀) · e^(−λ·Δt)
```

This preserves the prior as the resting point, shrinks *evidence counts*
(not the mean directly), so a worker with a long track record decays slowly
in *confidence*. `λ` is an Organizational DNA parameter
(`21-organizational-dna.md`): a "long-term thinking" org sets `λ` low; a
"speed" org sets it high.

| Org DNA profile      | λ (per day) | Half-life of evidence |
|----------------------|-------------|-----------------------|
| Long-term / research | 0.005       | ~138 days             |
| Balanced             | 0.01        | ~69 days              |
| Speed / startup      | 0.03        | ~23 days              |

### 3.6 Effective trust weight (the number actually used)

Raw trust `T_i` is **not** what downstream engines consume. A worker with
`T = 0.9` but a wide CI (few observations) should not weigh as heavily as one
with `T = 0.9` and a tight CI (many observations). The engine exposes the
**effective weight** — the lower bound of a one-sigma credible interval:

```
W_i = max(0,  T_i − z · σ_i)         with z = 1.0 (conservative)
```

This is the most important formula in the engine. It implements **shrinkage
toward distrust**: uncertain trust is treated as low trust. It is also the
second layer of Sybil resistance — a freshly-spawned worker with one lucky
success has `T ≈ 0.4` but `σ ≈ 0.19`, so `W ≈ 0.21`. It cannot dominate a
vote until it has proven itself repeatedly. All downstream engines consume
`W_i`, never `T_i`. `T_i` is for display and diagnostics only.

---

## 4. How Trust Is Earned and Lost

### 4.1 The four trust signals

Trust is a composite of four signals, each backed by a separate Beta
distribution so the organization can reason about *what kind* of trust a
worker has earned:

| Signal | Symbol | Question it answers | Beta params |
|--------|--------|--------------------|-------------|
| Prediction accuracy | `P` | Are this worker's forecasts right? | `α_P, β_P` |
| Evidence quality | `E` | Does this worker's evidence hold up? | `α_E, β_E` |
| Peer endorsement | `N` | Do trusted peers vouch for it? | `α_N, β_N` |
| Decision outcomes | `D` | Do projects this worker leads succeed? | `α_D, β_D` |

The composite is a weighted blend, with weights from Organizational DNA
(`21-organizational-dna.md`):

```
T_i = w_P·P_i + w_E·E_i + w_N·N_i + w_D·D_i,    w_P + w_E + w_N + w_D = 1
```

A research lab sets `w_P` high. A startup sets `w_D` high (ship and succeed).
A hospital sets `w_E` high (evidence above all). The same worker in two
organizations can have different composite trust — and that is correct,
because "trustworthy" is defined by the organization. The effective weight is
computed on the composite: `W_i = max(0, T_i − z·σ_composite_i)`, where
`σ_composite` propagates through the weighted sum of component variances.

### 4.2 How trust is earned

| Mechanism | Signal | Trigger |
|-----------|--------|---------|
| Prediction confirmed | `P` | Forecast at `t₀`, correct outcome at `t₁` |
| Evidence accepted | `E` | Survives the Evidence Challenge (`02-research-contributions.md` §7) |
| Endorsed by a high-trust peer | `N` | A worker with `W_endorser > τ` vouches |
| Project led to success | `D` | Investigation/sub-project led reaches positive outcome |
| Argument adopted in meeting | `P` | Meeting adopts the worker's argument (`20-meeting-engine.md`) |
| Correct dissent | `P` | Voted against a wrong majority — bonus weight `w_e × 1.5` |

The "correct dissent" bonus is critical: it rewards intellectual honesty and
prevents collapse into groupthink. A worker who correctly opposes a popular
but wrong decision earns *more* trust than one who went along with a correct
consensus.

### 4.3 How trust is lost

| Mechanism | Signal | Trigger |
|-----------|--------|---------|
| Prediction falsified | `P` | Forecast at `t₀`, wrong outcome at `t₁` |
| Evidence rejected | `E` | Fails the Evidence Challenge |
| Endorsement withdrawn / criticized | `N` | A trusted peer formally criticizes |
| Project led to failure | `D` | Investigation led fails its objective |
| Constitutional violation | `D` | Action tripped an ethics gate (`09-constitution-engine.md`) — penalty `w_e × 2.0` |
| Collusion detected | `N` | Flagged by the collusion detector (§8.2) |
| Endorsed a wrong agent | `N` | An endorsed worker later fails — endorsement is a prediction |

The last row is key: **endorsement is a prediction**. If A endorses B and B
later turns out wrong, A's `N` signal takes a hit. This makes endorsement
costly and discourages log-rolling.

### 4.4 Event weight table

| Event type | Weight `w_e` | Rationale |
|------------|--------------|-----------|
| Prediction confirmed / falsified | 1.0 | Baseline, symmetric |
| Correct dissent | 1.5 | Rewards honesty against the crowd |
| Evidence accepted / rejected | 0.7 | Softer than a forecast |
| Endorsement (giver / receiver) | 0.5 | Indirect signal |
| Project success / failure (lead) | 2.0 | High-stakes, high-signal |
| Meeting argument adopted / rejected | 0.3 | Weak, frequent signal |
| Constitutional violation | 2.0 | Hard penalty — ethics are sacred |
| Collusion penalty | 1.5 | Strong deterrent |

Weights are tunable per organization via DNA (`21-organizational-dna.md`).

---

## 5. How Trust Affects Decisions

### 5.1 Vote weight (Debate Engine)

In any vote (`10-debate-engine.md`), a ballot is weighted by `W_i`, not
counted as one-vote-one-value:

```
Vote outcome = Σ_i (W_i · b_i) / Σ_i W_i,    b_i ∈ {0, 1}
```

This is **weighted voting** — the core mechanism by which the organization
defers to its most reliable members. A worker with `W = 0.8` casts a ballot
worth eight times a worker with `W = 0.1`. A worker with `W = 0` is heard in
debate but cannot swing a vote.

### 5.2 Evidence acceptance (Evidence Gate)

When a worker submits evidence (`19-investigation-engine.md`), the Evidence
Challenge phase pits it against critics. The evidence's **prior probability
of acceptance** is seeded by the submitter's `E`-signal: `P_accept_prior =
E_i`. A high-trust researcher's evidence enters with a strong prior; a
low-trust worker's must survive more criticism. This is not bias — it is
Bayesian priors doing what they should. The challenge still happens; the
prior just sets the bar.

### 5.3 Meeting influence (Meeting Engine)

In meetings (`20-meeting-engine.md`), argument order and prominence are
influenced by trust. High-trust workers' arguments are placed earlier and
given more "speaking time" (longer generation budgets, more rebuttal rounds).
Low-trust workers can still speak — suppression by trust would be
anti-meritocratic — but their arguments must be more concise. The Meeting
Engine requests a trust-ordered speaker list at meeting start:

```
GET /trust/ranking?department=research&scope=meeting_42
  → [{worker_id, W_i, T_i, σ_i}, ...] sorted by W_i desc
```

### 5.4 Hiring, firing, promotion (Evolution Engine)

The Evolution Engine (`07-evolution-engine.md`) consumes trust as its primary
input for self-modification:

| Action | Trust condition |
|--------|-----------------|
| Promote | `W_i > τ_promote` AND trajectory slope > 0 |
| Demote | `W_i < τ_demote` AND trajectory slope < 0 |
| Fire | `W_i < τ_fire` sustained for `N` events |
| Hire into dept | Department aggregate trust (§9) below threshold → open requisition |
| Create department | Sustained overload + high aggregate trust in a sub-area |

Trust is necessary but not sufficient — the Evolution Engine also checks
workload, department needs, and budget. But trust is the gate.

---

## 6. Trust Visibility

**Workers can see each other's trust.** This is deliberate. In a real
organization, reputation is largely public — people know who the reliable
engineer is. Hiding trust would make the meritocracy invisible and remove
the incentive effect. What worker `j` sees about worker `i`:

```
{
  "worker_id": "research-04", "T": 0.94, "W": 0.88, "sigma": 0.06,
  "signals": { "P": 0.96, "E": 0.91, "N": 0.89, "D": 0.97 },
  "trajectory": "improving", "events_30d": 47, "department": "research"
}
```

What is **not** visible: the full event-by-event history (only aggregate +
30-day count); the identity of individual endorsers/critics (to prevent
retaliation — endorsement is a signal, not a social act, §8.2); the exact
`α, β` parameters (to make gaming the math harder). The visibility surface is
the same for all workers — there is no "manager-only" view. The CEO worker
sees the same numbers a junior worker sees. Hierarchy is in decision
authority, not in information access (`05-agent-architecture.md`).

---

## 7. End-to-End Walkthrough

Consider `research-04` over its first 60 days.

**Day 0 — Spawned.** Skeptical prior: `α=1, β=3 → T=0.25, σ=0.19, W=0.06`.
Can speak in meetings but cannot swing a vote.

**Day 3 — First prediction.** Forecasts "competitor X launches in Q3."
`w_e=1.0`. No update yet (pending).

**Day 14 — Prediction confirmed.** After decay: `α≈1.93, β≈2.79 → T=0.41,
σ=0.18, W=0.23`.

**Day 20 — Evidence accepted** (survey of 200 users, survives challenge).
`w_e=0.7 → α_E=1.7, β_E=3 → E=0.36`.

**Day 35 — Leads sub-investigation** to a correct recommendation. `w_e=2.0
→ α_D=3.0, β_D=3 → D=0.50`.

**Day 45 — Correct dissent.** Votes against a popular but flawed plan; plan
fails as predicted. Bonus `1.5 → α_P += 1.5`.

**Day 60 — Snapshot.** ~15 events. With DNA weights `w_P=0.4, w_E=0.3,
w_N=0.1, w_D=0.2` (research org): `P=0.78, E=0.62, N=0.40, D=0.55`:

```
T = 0.4·0.78 + 0.3·0.62 + 0.1·0.40 + 0.2·0.55 = 0.668
σ_composite ≈ 0.09,  W = 0.668 − 0.09 = 0.578
```

`research-04` is now a meaningful voice — earned, not granted, over 60 days
of demonstrated competence.

---

## 8. Trust Gaming Prevention

### 8.1 Sybil resistance

A **Sybil attack** creates many fake workers to inflate collective trust or
swing votes. Three layers of defense:

1. **Hiring gate (Evolution Engine).** Workers cannot be freely spawned. New
   workers are *hired* through the Evolution Engine (`07-evolution-engine.md`),
   which requires organizational approval (a vote or department-head
   decision) and budget. Each hire costs political capital and resources —
   you cannot flood the org with Sybils.
2. **Skeptical prior.** Even if hired, a Sybil starts at `T=0.25, W=0.06`. It
   is nearly voiceless until it proves itself — and proving requires correct
   predictions, which a Sybil cannot fake without actually being competent
   (in which case it is a worker, not a Sybil).
3. **Shrinkage via effective weight.** A ring of Sybils endorsing each other
   cannot inflate trust, because endorsements from low-trust workers carry
   weight proportional to the *endorser's* `W`, and the lower-bound effective
   weight keeps new agents discounted until their CI narrows.

**Endorsement weight is capped by the endorser's trust:**
`w_endorse(e) = w_e · W_endorser`. A Sybil with `W=0.06` endorsing another
Sybil contributes `0.5 · 0.06 = 0.03` to the receiver's `α_N`. Meaningless.

### 8.2 Collusion detection

**Collusion** is two or more workers systematically endorsing each other and
agreeing to mutually inflate trust regardless of outcomes. The engine
maintains an **endorsement graph** and runs three detectors on a rolling
window (e.g., 90 days):

```
  Endorsement ──► (1) Reciprocity R_ij
  graph          (2) Agreement correlation ρ_ij
                 (3) Joint accuracy ratio A_ij
  if R_ij > 0.8 AND ρ_ij > 0.7 AND A_ij > 1.5 → flag pair (i,j)
```

```
Reciprocity:    R_ij = 2·min(E_i→j, E_j→i) / (E_i→j + E_j→i)
Agreement:      ρ_ij = Pearson corr of i's and j's vote vectors over shared events
Joint accuracy: A_ij = P(i correct ∧ j correct) / [P(i correct)·P(j correct)]
```

`A_ij > 1` means they are correct together more than independence predicts.
A flag triggers: (1) **endorsement discount** — endorsements between the pair
weighted by a collusion factor `c ∈ [0,1]` decaying with severity; (2) **trust
penalty** — both take a `β_N` hit of weight `1.5` (§4.4), applied once per
detection. Crucially, detectors compare against **ground-truth outcomes**.
Two workers who always agree *and are always right* are not colluding — they
are both competent. Collusion is flagged only when mutual endorsement is *not
justified by accuracy*. This prevents punishing genuine alignment.

### 8.3 What is NOT gamed

- **Being persuasive.** Convincing others of *correct* positions is the job.
  Persuasion of *wrong* positions is caught by outcome-based trust (the
  persuader's `P` and `D` decay when decisions turn out wrong).
- **Being prolific.** Submitting more evidence does not raise trust; only
  *accepted* evidence raises `E`. Rejected evidence lowers it. Volume alone
  is neutral to negative.
- **Seniority.** Time in the org does not raise trust. Only outcomes do. A
  veteran with a decayed track record has lower `W` than a hot new hire.

---

## 9. Department-Level Trust

Just as workers have trust, departments have **aggregate trust** — the
organization's confidence in a department's collective output. This governs
how heavily a department's claims weigh in cross-department decisions:

```
T_dept = Σ_{i ∈ dept} (W_i · T_i) / Σ_{i ∈ dept} W_i
```

A weighted average of member trust, weighted by each member's effective
weight. A department is only as trusted as its trusted members — a department
full of low-trust workers cannot hide behind a single star. Department trust
is used in: **cross-department votes** (`10-debate-engine.md`) — when Research
claims "the market is saturated" and Marketing claims "demand is rising," the
relative weight is `T_Research` vs `T_Marketing`; **hiring triggers**
(`07-evolution-engine.md`) — if `T_dept < τ_dept_low` sustained, the Evolution
Engine opens a requisition; **resource allocation** — budget and compute are
partially allocated by department trust. Department trust is subject to decay
and recomputed on every member update. It is exposed on the same visibility
surface as worker trust (§6), so workers can see which departments the
organization currently relies on.

---

## 10. Trust Over Time — Trajectory

A single trust value is a snapshot. The organization cares about the
**trajectory** — is this worker improving, plateauing, or declining? The
engine stores periodic snapshots and derives features:

```
trust_history: [{ t, T, W, σ, α, β }, ...]   // snapshot every N events or daily
```

| Feature | Definition | Used by |
|---------|-----------|---------|
| `trend` | Slope of linear fit of `W` over `K` | Evolution Engine |
| `volatility` | Std. dev. of `W` over `K` | Promotion (low vol preferred) |
| `recovery` | Mean time to recover from a `β`-event | Firing decisions |
| `peak` | Max `W` ever achieved | Promotion ceiling |
| `consistency` | Fraction of snapshots where `W > τ` | Seniority badges |

Trajectory is the input to the **firing** decision. A worker with
`W < τ_fire` is not fired on the first dip — the Evolution Engine looks at
trajectory. A worker in sustained decline (`trend < 0` for `K` snapshots,
`recovery` lengthening) is fired. A worker that dips and recovers is kept.
This prevents over-firing on noise and under-firing on real decline.
Trajectory is surfaced in the UI (`22-mobile-ux.md`) as a sparkline next to
each worker — users watch reputation rise and fall, which makes the "living
organization" feel alive.

---

## 11. Data Model

Persisted in the shared database (`13-database-design.md`). The append-only
`trust_events` table is canonical; `trust_state` is a materialized projection
rebuildable from events — making the engine auditable (`26-security.md`) and
replayable (if the decay model changes, trust can be recomputed from scratch).

```sql
CREATE TABLE trust_state (            -- per-worker, per-signal Beta state
    worker_id TEXT NOT NULL, signal TEXT NOT NULL,   -- 'P'|'E'|'N'|'D'
    alpha REAL NOT NULL, beta REAL NOT NULL,
    last_updated REAL NOT NULL, PRIMARY KEY (worker_id, signal));
CREATE TABLE trust_events (           -- append-only event log (source of truth)
    event_id TEXT PRIMARY KEY, worker_id TEXT NOT NULL, signal TEXT NOT NULL,
    outcome TEXT NOT NULL, weight REAL NOT NULL,    -- 'positive'|'negative'
    event_type TEXT NOT NULL, source_engine TEXT NOT NULL,
    ref_id TEXT, timestamp REAL NOT NULL);          -- ref: investigation_id, etc.
CREATE TABLE endorsements (           -- endorsement graph (for collusion)
    endorser_id TEXT NOT NULL, receiver_id TEXT NOT NULL, event_id TEXT NOT NULL,
    weight REAL NOT NULL, timestamp REAL NOT NULL,
    PRIMARY KEY (endorser_id, receiver_id, event_id));
CREATE TABLE trust_snapshots (        -- periodic snapshots (for trajectory)
    worker_id TEXT NOT NULL, timestamp REAL NOT NULL,
    T REAL NOT NULL, W REAL NOT NULL, sigma REAL NOT NULL,
    PRIMARY KEY (worker_id, timestamp));
CREATE TABLE collusion_flags (
    worker_a TEXT NOT NULL, worker_b TEXT NOT NULL,
    R REAL NOT NULL, rho REAL NOT NULL, A REAL NOT NULL,
    detected_at REAL NOT NULL, PRIMARY KEY (worker_a, worker_b));
```

---

## 12. API Surface

Narrow API (`24-api-design.md`). Workers **cannot** write to the Trust Engine
directly — only the Debate, Investigation, Meeting, and Constitution engines
emit trust events. This prevents self-endorsement and keeps trust grounded in
real outcomes.

```
GET  /trust/weight/{worker_id}        → { worker_id, T, W, sigma, signals:{P,E,N,D} }
GET  /trust/department/{dept_id}       → { dept_id, T_dept, members:[...] }
GET  /trust/ranking?department&limit  → [{ worker_id, W, T, sigma, trajectory }]
POST /trust/events                    → { worker_id, signal, outcome, weight, ... }
                                      → { event_id, new_state }   (called by engines)
GET  /trust/profile/{worker_id}       → { worker_id, T, W, sigma, signals, trajectory, events_30d }
```

---

## 13. Integration Map

| Engine / Doc | Relationship |
|--------------|--------------|
| `04-technical-architecture.md` | Core service, one process per org |
| `05-agent-architecture.md` | Workers carry `worker_id`; trust is per-worker |
| `06-memory-system.md` | Trust events are part of institutional memory |
| `07-evolution-engine.md` | Consumes `W` and trajectory for hire/fire/promote; gates Sybils |
| `09-constitution-engine.md` | Ethics violations emit trust penalties (`D` signal) |
| `10-debate-engine.md` | Consumes `W` for vote weighting; emits debate outcomes |
| `11-communication-engine.md` | Endorsements happen via messages; feed `N` signal |
| `13-database-design.md` | Trust tables in the shared DB |
| `14-knowledge-graph.md` | Endorsement graph is a subgraph of the org KG |
| `16-event-system.md` | Trust events flow on the event bus |
| `19-investigation-engine.md` | Investigation outcomes emit `D` and `P` events |
| `20-meeting-engine.md` | Meeting outcomes emit `P`; consumes ranking |
| `21-organizational-dna.md` | DNA sets `λ`, signal weights `w_*`, event weights |
| `26-security.md` | Collusion/Sybil defense; audit of trust events |

---

## 14. Summary

The Trust Engine is the meritocratic backbone of Veliora. It is why the
organization is neither a democracy (one worker, one vote) nor a
dictatorship (one worker, all the votes) — it is a **weighted meritocracy**,
where influence is continuously earned through demonstrated competence and
continuously lost through demonstrated failure. Four choices each pass the
One Agent Test: (1) **Relational by nature** — trust only exists because
there are multiple workers; (2) **Bayesian, not point-estimated** — the Beta
distribution carries uncertainty, and the effective weight shrinks uncertain
trust to zero, which is what makes the system Sybil-resistant; (3)
**Outcome-grounded** — trust moves only on real outcomes, so persuasion and
volume do not move it, making trust hard to fake; (4) **Trajectory-aware** —
the organization fires on sustained decline, not on noise, and promotes on
sustained excellence, not on a single lucky outcome. The result is an
organization whose collective judgment improves over time, because the
voices that have been right grow louder and the voices that have been wrong
grow quieter — automatically, without anyone programming it.
