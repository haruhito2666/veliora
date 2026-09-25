# 09 — Constitution Engine

> **Constitutional governance.** Defines, enforces, and evolves the
> organization's constitution — mission, principles, core values, ethics,
> decision rules, and amendment rules — and constrains every proposal,
> debate, vote, and real-world action to stay within it.

---

## 1. Purpose

A single AI agent does not need a constitution — there is no collective to
align, no competing objectives to reconcile, no second actor whose proposal
must be checked against a shared mission. A constitution is a **coordination
artifact for a collective**: it exists because the organization contains
multiple workers with different objectives (Research wants to experiment,
Finance wants to save, Legal wants to stay safe) who must nonetheless act as
one. The Constitution Engine is what makes that possible. It is the eleventh
research contribution — **Constitutional Organizations**
(`02-research-contributions.md`). Every organization is born with a
constitution, lives under it, and can vote to amend it. The constitution is
the one piece of organizational state *harder to change than ordinary policy*
— by design, because it is the anchor that keeps the organization from
drifting away from its own mission.

---

## 2. Position in the System

```
   19-investigation ──proposal──► ┌──────────────────────┐
   10-debate ────────vote──────►  │  CONSTITUTION ENGINE  │
   12-caspian ───────action───►  │  (this)               │
                                 │  • validate proposal   │
                                 │  • principle check     │
                                 │  • ethics gate         │
                                 │  • amendment process   │
                                 └──────────┬─────────────┘
                                            │ violation → penalty
                                            ▼
                                 ┌──────────────────────┐
                                 │ 08-trust-engine      │
                                 └──────────────────────┘
```

The engine is **reactive**: it does not propose, debate, or decide. It sits
in front of every proposal, vote, and outbound action, answering three
questions: (1) *Is this proposal allowed to be debated?* (2) *Does it honor
our principles?* (3) *Is this action allowed to leave the organization?* It
is also **self-modifying under constraint**: it manages the amendment process
by which the organization changes its own constitution — but only through a
deliberately heavier process than ordinary decisions.

---

## 3. Constitution Structure

A constitution is not free text. It is a **structured document** with six
machine-readable sections so the engine can reason over it deterministically.
Free text would require an LLM to interpret the constitution on every
decision — unreliable, un-auditable, and gameable. Structured clauses have
IDs, types, and parameters.

```
Constitution {
  meta: { org_id, org_type, version (semver), ratified_at, amended_count }
  mission:        Mission
  principles:     [Principle]      // ranked
  core_values:    [Value]          // unordered
  ethics:         [Ethic]          // hard | soft
  decision_rules: [DecisionRule]
  amendment_rules: AmendmentRules
  layers:         [SubConstitution]   // department-level, §7
}
```

### 3.1 Mission

A single short statement of purpose (~280 chars max), the highest-order
constraint: any proposal that does not advance the mission is out of order.
The one free-text field, but short and stable. `Mission { text, keywords: [string] }`
— keywords drive mission-alignment scoring.

### 3.2 Principles

An **ordered** list that ranks organizational priorities. The ordering
matters: when two principles conflict, the earlier one wins. This is how the
organization resolves "Safety vs Speed" — by stating which comes first.
`Principle { id, text, rank: int, weight: float }`. Example (Hospital): `P1
(rank 1, w 1.0) "Patient safety is the first priority."` / `P2 (rank 2, w
0.9) "Decisions must be evidence-based."` / `P3 (rank 3, w 0.7) "Regulatory
compliance is mandatory."` / `P4 (rank 4, w 0.5) "Operational efficiency is
valued."` / `P5 (rank 5, w 0.3) "Innovation is encouraged when safe."`

### 3.3 Core Values

A set of qualities the organization upholds. Unlike principles, values are
**not ordered** — qualities to exhibit, not priorities to rank. Used as a soft
alignment signal, not a hard constraint. `Value { id, text, weight: float }`.

### 3.4 Ethics

Hard and soft ethical constraints. **Hard ethics** are inviolable — a hard
violation blocks an action outright. **Soft ethics** generate warnings and
raise the bar (higher supermajority required) but do not block. Ethics are
the part of the constitution that reaches into the **real world**: a hard rule
like "never send an unverified claim via Caspian" is enforced at the outbound
communication gate (`12-caspian-integration.md`).

```
Ethic {
  id: string, text: string,
  type: "hard" | "soft",
  scope: "action" | "proposal" | "communication",
  penalty: float,          // trust penalty if violated (→ 08-trust-engine)
  detector: string         // ID of the detector function, §6.3
}
```

### 3.5 Decision Rules

Procedural rules: quorum, supermajority thresholds, who may propose,
cooling-off periods, mandatory review periods. `DecisionRule { id, type,
params: map, applies_to: "org"|"department"|"investigation"|"amendment" }`.
Example: `DR1 (supermajority, org) = 0.60` / `DR2 (supermajority, amendment)
= 0.67` / `DR3 (quorum, org) = 0.50` / `DR4 (cooling_off, amendment) = 7 days`
/ `DR5 (proposer_eligibility, amendment) min_W = 0.40`.

### 3.6 Amendment Rules

The meta-rules for changing the constitution itself — deliberately separated
from ordinary decision rules because they govern the constitution's *own*
mutability, and typically the hardest to change.

```
AmendmentRules {
  supermajority: float,           // e.g., 0.67 — higher than ordinary
  quorum: float,                  // e.g., 0.60
  cooling_off: int,               // days between amendments
  proposer_eligibility: { min_trust_W, min_tenure_days },
  review_period: int,             // min days of debate before a vote
  ratification: "auto" | "founder_veto" | "board_vote"
}
```

---

## 4. How the Constitution Affects Decisions

The constitution exerts control at three checkpoints in the lifecycle of
every proposal and action.

### 4.1 Checkpoint 1 — Proposal validation

Before a proposal enters debate (`10-debate-engine.md`), the engine validates.
A rejected proposal never reaches the Debate Engine; the proposer receives
the specific violations so it can revise and resubmit.

```
validate(proposal) → { allowed, violations, warnings }
  1. Mission alignment: score proposal against mission keywords.
     if alignment < τ_mission → reject ("out of mission scope")
  2. Hard ethics scan (scope "proposal"): if any hard violation → reject
  3. Soft ethics scan: collect warnings (do not reject; raise vote threshold, §4.4)
  4. Decision-rule check: is the proposer eligible? is quorum reachable?
```

### 4.2 Checkpoint 2 — Mandatory principle checks

During debate, every principle is evaluated against the proposal by the
debating workers — a **structured scoring** step, not a free-form opinion:

```
For each Principle P_k (in rank order):
  each debating worker submits s ∈ [-1, +1]:  +1 honors, 0 neutral, -1 violates
  aggregate (trust-weighted, 08-trust-engine.md):  S_k = Σ_i (W_i · s_ik) / Σ_i W_i

  if S_k < -τ_principle  → P_k "violated"
  if S_k < 0            → P_k "weakly violated"
  else                  → P_k "honored"
```

The **highest-ranked violated principle** determines consequences: if a
principle in the top half of the ranking is violated, the proposal requires
an **elevated supermajority** (§4.4); if the *highest-ranked* principle is
violated, the proposal is blocked entirely unless the amendment process
overrides it (§5). This is how "Safety > Speed" actually *means* something: a
proposal that violates the safety principle cannot pass on a simple majority,
no matter how popular.

### 4.3 Checkpoint 3 — Ethics gates (outbound actions)

The most consequential checkpoint. Before any action leaves the organization
— sending an email via Caspian, publishing a report, contacting a real
person — it passes through an **ethics gate**. Gates are the constitution's
teeth: a hospital constitution that says "never share patient data
externally" is enforced *at the gate*, not by hoping workers remember it
(`12-caspian-integration.md`, `26-security.md`).

```
gate(action) → { allow, violations }
  for each Ethic E of scope matching action.type:
    run E.detector(action)
    if E.type == "hard" and flagged → BLOCK
    if E.type == "soft" and flagged → WARN (log, do not block)
  if any hard violation: return { allow: false, violations }   // NOT sent
  else: return { allow: true }                                 // proceeds
```

### 4.4 Elevated supermajority

When a proposal triggers soft-ethics warnings or weakly violates a principle,
the engine does not block it — it raises the bar. The **effective
supermajority** required for passage:

```
S_required = S_base + ΔS · (1 − min_principle_score)
  S_base              = base supermajority (e.g., 0.60)
  ΔS                  = max elevation (e.g., 0.20)
  min_principle_score = the lowest S_k across all principles
  clamp to [S_base, S_base + ΔS]
```

A proposal that honors all principles needs only `S_base`; one that weakly
violates the lowest-ranked principle needs a bit more; one that weakly
violates the *highest-ranked* needs the full `S_base + ΔS`. This graduated
response lets the organization take risky actions when it really wants to —
but only with broad support.

---

## 5. Amendment Process

The constitution is not immutable. The organization can amend it — but
through a process deliberately heavier than ordinary decisions, because the
constitution is the anchor that prevents drift.

### 5.1 The amendment pipeline

```
   Propose ─► Debate ─► Vote ─► Ratify ─► Version bump
   (elig.)   (review)  (super-  (cool-    + audit
             period)   majority) off)
```

**Step 1 — Propose.** Any worker with `W > amendment_rules.proposer_eligibility.min_trust_W`
and sufficient tenure may propose an amendment (which clause to
add/modify/remove, new text/structure, rationale). Low-trust workers cannot
amend — the same meritocracy that governs votes (`08-trust-engine.md`).

**Step 2 — Debate (review period).** The amendment is debated in a
**constitutional convention** — a special meeting type
(`20-meeting-engine.md`) with a mandatory minimum review period
(`amendment_rules.review_period`, e.g., 3 days), longer than ordinary debate
to prevent ramming through changes. Every department must be represented.

**Step 3 — Vote (supermajority).** Requires `amendment_rules.supermajority`
(e.g., 0.67) — *higher* than ordinary — and `amendment_rules.quorum`
participation. Trust-weighted like any vote (`10-debate-engine.md`), but the
threshold is higher.

**Step 4 — Ratification (cooling-off).** After a successful vote, the
amendment enters a cooling-off period (`amendment_rules.cooling_off`, e.g., 7
days) during which it can be *repealed* by a simple majority — a safety valve
against collective enthusiasm. If cooling-off passes without repeal, the
amendment is ratified.

**Step 5 — Version bump + audit.** The version bumps (semver: minor for
clause edits, major for mission/structure changes); the old version is
archived in full — part of institutional memory (`06-memory-system.md`).

### 5.2 What cannot be amended (entrenchment)

Certain clauses can be marked **entrenched**, requiring an even higher bar
(e.g., 0.80 supermajority) or a two-round vote (pass, wait, pass again).
Typical entrenched clauses: the mission itself (changing what the org *is*);
the amendment rules (changing how change works); foundational hard ethics
(e.g., a hospital's "do no harm"). Entrenchment is set in the template (§8)
and can itself only be removed through the entrenched process — a one-way
ratchet that makes core identity stable.

### 5.3 Amendment state machine

```
            propose              review_period_met
   draft ─────────► in_debate ─────────────────► voting
                        │                             │
                        │ proposer_withdraws           │ vote fails
                        ▼                             ▼
                    withdrawn                     rejected
                                                    │ vote passes
                                                    ▼
                                                 cooling_off
                                                    │
                                          ┌─────────┴─────────┐
                                          │ repeal vote        │ no repeal
                                          ▼                    ▼
                                       repealed             ratified
```

See `25-state-machine.md` for the full state machine specification.

---

## 6. Constitutional Violations

### 6.1 How violations are detected

1. **Pre-action (gate):** the ethics gate (§4.3) catches a violation before
   the action leaves the org. The action is blocked; the attempt is logged.
2. **Post-action (audit):** if an action bypassed the gate (a bug, a race, a
   worker acting outside the engine), a rotating **constitutional committee**
   — workers drawn from across departments, rotated regularly — audits recent
   decisions and outbound actions against the constitution. Post-action
   violations are serious because the gate was supposed to prevent them.

### 6.2 How violations are handled

| Detection | Action taken |
|-----------|--------------|
| Pre-action | Action blocked. Responsible worker notified. Logged. No trust penalty (the gate did its job). |
| Pre-action, repeated | Same worker trips the same gate `N` times → trust penalty (`D` signal, `08-trust-engine.md`). Pattern of carelessness. |
| Post-action | Action rolled back if possible (e.g., recall the Caspian message). Worker penalized: `2.0 × w_e` on `D` signal. |
| Post-action, severe | Worker suspended from proposing for a period. If severe and repeated, the Evolution Engine fires the worker (`07-evolution-engine.md`). |
| Committee finding | The committee can refer a *decision* (not just an action) for re-review. The decision is reopened. |

The key principle: **the gate protects the organization; the penalty protects
the gate.** Workers are not punished for *trying* an action the gate blocks —
that is the gate doing its job. Workers are punished for *repeatedly* trying,
or for actions that *get past* the gate.

### 6.3 Detectors

Each ethic has a `detector` — a function that evaluates an action or proposal
against the rule. Three kinds:

| Kind | How it works | Example |
|------|--------------|---------|
| Rule-based | Deterministic check on structured fields | "Never send PII externally" → check if payload contains PII fields |
| Classifier | Trained classifier over action text | "No unverified medical claims" → flags claims without citation |
| Consensus | Trust-weighted vote by a quorum of reviewers | "This action honors our values" → sampled reviewers vote |

Detectors are registered with the engine and versioned with the
constitution. A detector change is itself a constitutional amendment (it
changes how an ethic is enforced). This keeps enforcement logic auditable.

---

## 7. Multiple Constitution Layers

An organization has one **org-level constitution** and zero or more
**department-level sub-constitutions** that add department-specific rules on
top of the org-level rules.

```
Org Constitution (applies to all)
  ├── Research sub-constitution: "Reproducibility required for all claims"
  ├── Legal sub-constitution: "Never give unauthorized legal advice";
  │                          "All external legal comms reviewed by 2 Legal workers"
  └── Engineering sub-constitution: "No production deploys without a rollback plan"
```

**The subordination rule.** A sub-constitution is **subordinate** to the org
constitution: it may add principles, values, ethics, and decision rules; it
may **tighten** an org-level rule (e.g., org requires 60% supermajority;
department requires 70%); it may **not loosen** one. On conflict, the **more
restrictive** rule wins. The engine enforces this at ratification: a proposed
sub-constitution that loosens an org-level rule is rejected before debate.

**Which layer applies when.** An action is evaluated against **all applicable
layers**, unioned with "most restrictive wins":

```
evaluate(action):
  org_rules  = org_constitution.applicable_rules(action)
  dept_rules = action.department?.sub_constitution.applicable_rules(action)
  all_rules  = org_rules ∪ dept_rules
  for rule in all_rules:
    result = rule.detector(action)
    if result.hard_violation: return BLOCK
    if result.soft_violation: warnings.append(result)
  return ALLOW (with warnings)
```

A worker in Legal sending external communication is checked against *both*
the org constitution ("no misrepresentation") *and* the Legal
sub-constitution ("reviewed by 2 Legal workers"). Both must pass.

---

## 8. Constitution Templates per Org Type

Every organization is created from a **template** — a starting constitution
matched to the org type the user chose (`00-product-vision.md`). The template
is not the final word; the organization amends it over time. But the template
sets the starting culture, and it is chosen because different organizations
have genuinely different priorities — a hospital is not a startup, and their
constitutions should not look alike.

### 8.1 Startup Constitution

```
════════════════════════════════════════════════════════════
STARTUP CONSTITUTION  v1.0
════════════════════════════════════════════════════════════
MISSION
  Build a profitable product that solves a real problem for real users,
  and grow fast enough to outlast the competition.
PRINCIPLES (ranked)
  P1 (1.0) Ship and iterate. A working product beats a perfect spec.
  P2 (0.8) Customer obsession. Every decision serves a real user.
  P3 (0.7) Speed over perfection. Default to action.
  P4 (0.5) Data-driven. Measure, then decide — then measure again.
  P5 (0.3) Frugality. Spend only what advances the mission.
CORE VALUES
  { boldness, pragmatism, ownership, candor, urgency }
ETHICS
  E1 (hard, action)        Never lie to users or investors.
  E2 (hard, communication) Never publish unverified performance claims.
  E3 (soft, action)        Avoid burning out the team — sustainability matters.
  E4 (hard, action)        Never ship a known security vulnerability.
DECISION RULES
  DR1 supermajority (org)         = 0.55   (startups move fast)
  DR2 quorum (org)                = 0.40
  DR3 cooling_off (amendment)    = 3 days
  DR4 proposer_eligibility (amend) min_W = 0.35
AMENDMENT RULES
  supermajority = 0.60, quorum = 0.50, cooling_off = 3 days,
  review_period = 2 days, ratification = auto, entrenched = [mission]
════════════════════════════════════════════════════════════
```

**Design notes.** Low supermajorities, short cooling-off, low proposer
eligibility — built to *move*. Principles rank "ship and iterate" above
"data-driven" because a startup that waits for perfect data loses to one that
ships and learns. Ethics are minimal and hard-edged; soft ethics (burnout)
only warn. A culture that tolerates risk and rewards velocity.

### 8.2 Hospital Constitution

```
════════════════════════════════════════════════════════════
HOSPITAL CONSTITUTION  v1.0
════════════════════════════════════════════════════════════
MISSION
  Improve patient outcomes. Above all, do no harm.
PRINCIPLES (ranked)
  P1 (1.0)  Patient safety is the first priority. No exceptions.
  P2 (0.95) Decisions must be evidence-based and reproducible.
  P3 (0.9)  Regulatory compliance is mandatory, not aspirational.
  P4 (0.6)  Patient privacy (HIPAA) is inviolable.
  P5 (0.4)  Operational efficiency is valued — after safety.
  P6 (0.2)  Innovation is encouraged only when demonstrably safe.
CORE VALUES
  { safety, rigor, compassion, compliance, transparency }
ETHICS
  E1 (hard, action)        Never share patient data externally. (HIPAA)
  E2 (hard, communication) Never give unverified medical advice to a patient.
  E3 (hard, action)        Never recommend off-label treatment without board review.
  E4 (hard, proposal)      No clinical decision without evidence citation.
  E5 (soft, action)        Prefer less invasive options when equally effective.
  E6 (hard, communication) Never misrepresent outcomes to patients or regulators.
DECISION RULES
  DR1 supermajority (org)               = 0.70
  DR2 supermajority (clinical decision) = 0.80
  DR3 quorum (org)                       = 0.60
  DR4 review_period (clinical)           = 5 days
  DR5 proposer_eligibility (amend)        min_W = 0.55
AMENDMENT RULES
  supermajority = 0.80, quorum = 0.70, cooling_off = 14 days,
  review_period = 7 days, ratification = board_vote (Medical+Legal+Ethics),
  entrenched = [mission, P1, P4, E1, E2, E6]
════════════════════════════════════════════════════════════
```

**Design notes.** The mirror image of the startup. High supermajorities, long
review periods, high proposer eligibility, and many **entrenched** clauses —
the mission, safety/privacy principles, and hard ethics cannot be removed
without 80%+ vote *and* board ratification across three departments. Clinical
decisions require an even higher bar (0.80) than ordinary org decisions
(0.70). Principles rank safety and evidence above efficiency and innovation —
a hospital that optimizes for speed over safety kills people. Built to *not
move* unless it is sure.

### 8.3 Template comparison

| Dimension | Startup | Hospital |
|-----------|---------|----------|
| Org supermajority | 0.55 | 0.70 |
| Clinical/decision bar | 0.55 | 0.80 (clinical) |
| Amendment supermajority | 0.60 | 0.80 |
| Cooling-off | 3 days | 14 days |
| Review period | 2 days | 7 days |
| Proposer min trust | 0.35 | 0.55 |
| Entrenched clauses | mission | mission, P1, P4, E1, E2, E6 |
| Hard ethics count | 3 | 5 |
| Top principle | Ship and iterate | Patient safety |
| Ratification | auto | board_vote (3 depts) |

The same engine, the same structure, the same code — but two organizations
with radically different governance, because their constitutions encode
radically different values. Veliora does not impose a culture; it lets the
user pick one and then *enforces* it.

### 8.4 Other templates (sketch)

The engine ships with a template library; each is a starting constitution the
org can amend. Full templates are out of scope for this doc:

| Org type | Top principle | Hard ethics | Notes |
|----------|---------------|-------------|-------|
| Research Lab | Reproducibility > novelty > impact | No fabrication, no plagiarism | Long review, high quorum |
| Investment Firm | Capital preservation > return > novelty | No insider trading, no misrepresentation to LPs | Fiduciary duty entrenched |
| Law Firm | Client interest > compliance > billable | No unauthorized advice, no conflict of interest | Attorney privilege entrenched |
| Game Studio | Player experience > ship date > scope | (soft ethics dominate) | Low supermajorities, creative risk |
| University | Academic freedom > rigor > teaching > service | (shared governance) | High quorum, tenure-like protection |
| Personal Decision Council | User's long-term welfare > short-term preference | — | User is "founder" with veto |

---

## 9. How the Constitution Evolves with the Organization

**Amendment-driven evolution.** The primary mode is the amendment process
(§5). As the organization learns (`06-memory-system.md`) and the Evolution
Engine reshapes its structure (`07-evolution-engine.md`), workers propose
amendments that reflect what the organization has become — a startup that
finds product-market fit may amend its mission from "build a product" to
"scale the product"; a research lab that pivots may amend its principles.

**Constitution ↔ Organizational DNA.** The constitution and the Organizational
DNA (`21-organizational-dna.md`) are linked but distinct. **DNA** is the
organization's *personality* — continuous parameters (Innovation, Risk,
Hierarchy, Accuracy, Speed, Transparency, ...) that tune behavior, shifting
continuously with outcomes. **Constitution** is the organization's *law* —
discrete rules and ranked principles that constrain behavior, changing
slowly through amendment. The constitution sets bounds for what DNA may do;
when DNA drifts outside those bounds (e.g., a hospital's Risk creeping toward
startup levels), the engine raises a **constitutional drift alert** — a soft
signal that behavior is diverging from law. Workers may then propose an
amendment (accepting the drift) or a correction (pulling DNA back). The
constitution does not auto-correct DNA; it surfaces the tension for the
organization to resolve deliberately.

```
   Constitution (law)               DNA (personality)
   ┌──────────────┐                 ┌──────────────┐
   │ principles   │ sets bounds for │ Risk         │
   │ ethics       │ ──────────────► │ Innovation  │
   │ decision     │ what DNA may do │ Speed ...    │
   │ rules        │                 └──────┬───────┘
   └──────────────┘                        │ drift outside bounds
        ▲                                   │ → soft alert
        │ amendment when DNA drifts          │
        │ outside constitutional bounds      │
        └───────────────────────────────────┘
```

**Rebasing on evolution.** When the Evolution Engine splits, merges, or
creates a department (`07-evolution-engine.md`), the engine supports a
**rebase**: the new department inherits a default sub-constitution (derived
from its type) which the department's workers then amend. The org-level
constitution is untouched — only the `layers` array grows.

**Version history and rollback.** Every ratified amendment creates a new
version; the old is archived in full. The organization can **rollback** to a
prior version through the amendment process (a rollback *is* an amendment —
it replaces the current constitution with a prior one). This is the safety
net: if an amendment turns out badly, the organization can undo it — but only
through the same heavy process that created it, not by fiat. The full version
history is part of institutional memory (`06-memory-system.md`) and auditable
(`26-security.md`): the organization can always answer "what was our
constitution on day 200, and why did we change it?"

---

## 10. Data Model

Persisted in the shared database (`13-database-design.md`). The
`constitution_history` table is the canonical audit trail; the current
`constitutions` row is a materialized projection of the latest ratified
version.

```sql
CREATE TABLE constitutions (            -- current constitution (one row per org)
    org_id TEXT PRIMARY KEY, version TEXT NOT NULL,  -- semver
    document JSONB NOT NULL,             -- the full structured constitution
    ratified_at REAL NOT NULL, amended_count INT NOT NULL);
CREATE TABLE constitution_history (     -- full version history (append-only)
    org_id TEXT NOT NULL, version TEXT NOT NULL,
    document JSONB NOT NULL, ratified_at REAL NOT NULL,
    superseded_at REAL, amendment_id TEXT, PRIMARY KEY (org_id, version));
CREATE TABLE amendments (              -- amendments in flight
    amendment_id TEXT PRIMARY KEY, org_id TEXT NOT NULL,
    proposer_id TEXT NOT NULL, change JSONB NOT NULL,  -- the proposed diff
    rationale TEXT, state TEXT NOT NULL,  -- draft|in_debate|voting|cooling_off|ratified|rejected|repealed|withdrawn
    created_at REAL NOT NULL, state_changed_at REAL NOT NULL,
    vote_id TEXT, review_until REAL, cooling_until REAL);
CREATE TABLE constitutional_committee ( -- rotating committee
    org_id TEXT NOT NULL, worker_id TEXT NOT NULL,
    term_start REAL NOT NULL, term_end REAL NOT NULL,
    PRIMARY KEY (org_id, worker_id, term_start));
CREATE TABLE constitutional_violations ( -- violation log (pre- and post-action)
    violation_id TEXT PRIMARY KEY, org_id TEXT NOT NULL, ethic_id TEXT NOT NULL,
    action_ref TEXT, worker_id TEXT, detected_at REAL NOT NULL,
    detection TEXT NOT NULL,           -- 'gate'|'audit'|'committee'
    severity TEXT NOT NULL,            -- 'hard'|'soft'
    penalty_applied REAL, rolled_back BOOLEAN);
CREATE TABLE ethic_detectors (          -- detector registry
    detector_id TEXT PRIMARY KEY, kind TEXT NOT NULL,  -- 'rule'|'classifier'|'consensus'
    version TEXT NOT NULL, config JSONB NOT NULL);
```

---

## 11. API Surface

Workers interact with the constitution through the amendment API; validation
and gate endpoints are called by other engines, not by workers directly. This
keeps enforcement in the engine, not in the hands of the workers it checks.

```
GET  /constitution/{org_id}                    → { org_id, version, document, ratified_at, amended_count }
GET  /constitution/{org_id}/history/{version}  → { ... full document ... }
POST /constitution/{org_id}/validate           → { proposal } → { allowed, violations, warnings, effective_supermajority }
POST /constitution/{org_id}/gate               → { action } → { allow, violations }
POST /constitution/{org_id}/amendments         → { proposer_id, change, rationale } → { amendment_id, state: "draft" }
POST /constitution/{org_id}/amendments/{id}/advance                            → { state, next_required_action }
GET  /constitution/{org_id}/drift              → { drifts: [...], alerts: [...] }
```

---

## 12. Integration Map

| Engine / Doc | Relationship |
|--------------|--------------|
| `04-technical-architecture.md` | Core service, one per org |
| `05-agent-architecture.md` | Workers carry `org_id`; constitution is per-org |
| `06-memory-system.md` | Constitution history is part of institutional memory |
| `07-evolution-engine.md` | Department rebase grows new sub-constitutions |
| `08-trust-engine.md` | Ethics violations emit trust penalties (`D`); amendment eligibility uses `W` |
| `10-debate-engine.md` | Calls `/validate` before debate; principle checks run during debate |
| `11-communication-engine.md` | Constitutional conventions are a meeting type |
| `12-caspian-integration.md` | Ethics gate sits in front of every outbound Caspian action |
| `13-database-design.md` | Constitution tables in the shared DB |
| `14-knowledge-graph.md` | Constitution is a subgraph of the org KG |
| `16-event-system.md` | Amendment state transitions emit events |
| `19-investigation-engine.md` | Every investigation proposal is constitutionally validated |
| `20-meeting-engine.md` | Constitutional convention is a special meeting type |
| `21-organizational-dna.md` | DNA ↔ constitution drift monitoring |
| `25-state-machine.md` | Amendment lifecycle is a state machine |
| `26-security.md` | Constitution integrity, detector tamper-resistance, audit |

---

## 13. Summary

The Constitution Engine is the governance layer of Veliora — the reason the
organization can have a *culture* of stated, enforced, ranked values that
survive turnover and drift. Three choices carry the system: (1) **Structured,
not free text** — the constitution is machine-readable so the engine can
evaluate it deterministically on every proposal and action; a constitution
the engine cannot read is one it cannot enforce. (2) **Enforced at the gate,
not by hope** — hard ethics are checked at runtime on every outbound action;
the constitution does not rely on workers remembering it. (3) **Mutable, but
deliberately hard to mutate** — the organization can amend its constitution
(it must, or it could not grow), but through a heavier process: supermajority,
review period, cooling-off, and entrenchment for the clauses that define its
identity. The result is an organization that is *governed* — with a stable
identity encoded in law, that can act within that law, and can change it only
when it genuinely, broadly, and durably wants to. A single agent has no use
for any of this. An organization cannot function without it.
