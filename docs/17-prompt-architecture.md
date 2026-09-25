# 17 — Prompt Architecture

> How prompts are constructed for workers in Veliora. The prompt is the
> interface between the organization and the mind that executes a task.

## 1. Purpose

In a single-agent system the prompt is a question to one mind. In Veliora
the prompt is a **commissioning document**: the organization instructs a
worker to act on its behalf, with its memory, under its constitution, inside
its political structure, against its trust-weighted peer set.

A worker never "decides what it is." The organization tells it:

- **Who it works for** (organization identity, constitution, DNA)
- **What role it holds** (department, title, scope, peers)
- **What it knows** (institutional memory, prior decisions, failures)
- **What it must do** (the specific task and deliverables)
- **What it may not do** (constitutional constraints, deadlines, format)

This document defines the five-layer prompt stack, the DNA-to-personality
projection, institutional memory injection, prompt versioning and A/B
testing, and prompt evolution. It is consumed by the Agent Architecture
(`05-agent-architecture.md`), the Planner (`18-planner-design.md`), the
Investigation Engine (`19-investigation-engine.md`), and the Evolution Engine
(`07-evolution-engine.md`).

### The One Agent Test

A single agent has no peers, no department, no constitution it can vote to
amend, no trust-weighted colleagues whose opinions it must weigh, and no
shared institutional memory it inherits. Every layer below except the task
layer exists **because the worker is part of an organization**. If you
removed the org, the system and role layers would collapse to a single
persona string and the context layer would collapse to chat history — which
is exactly what a single-agent prompt is. The layers are the difference.

---

## 2. The Five-Layer Prompt Stack

Every worker invocation assembles a prompt from five ordered layers. Layers
are composed by the Prompt Composer service before the worker's LLM call.

```
┌──────────────────────────────────────────────────────────────┐
│  L1  SYSTEM LAYER        org identity · constitution · DNA    │  static per org, stable across invocations
├──────────────────────────────────────────────────────────────┤
│  L2  ROLE LAYER          department · title · scope · peers   │  static per worker assignment
├──────────────────────────────────────────────────────────────┤
│  L3  CONTEXT LAYER       investigation · memory · trust · comms │  dynamic, rebuilt every invocation
├──────────────────────────────────────────────────────────────┤
│  L4  TASK LAYER          assignment · deliverables · deps      │  dynamic, per task
├──────────────────────────────────────────────────────────────┤
│  L5  CONSTRAINTS LAYER   constitution · deadline · format      │  dynamic, per task + constitution
└──────────────────────────────────────────────────────────────┘
                              │
                              ▼
                   ┌────────────────────┐
                   │  Prompt Composer    │
                   └────────────────────┘
                              │
                              ▼
                   ┌────────────────────┐
                   │   Worker LLM call   │
                   └────────────────────┘
```

### 2.1 Layer Responsibilities

| Layer | Owner | Mutability | Source of Truth |
|-------|-------|-----------|-----------------|
| L1 System | Organization | Constitutional amendment, DNA drift | `09-constitution-engine.md`, `21-organizational-dna.md` |
| L2 Role | Department | Hiring, promotion, reorg | `05-agent-architecture.md`, `07-evolution-engine.md` |
| L3 Context | Investigation | Every invocation | `06-memory-system.md`, `08-trust-engine.md`, `19-investigation-engine.md` |
| L4 Task | Planner / CEO | Per task | `18-planner-design.md` |
| L5 Constraints | Constitution + Task | Per task | `09-constitution-engine.md`, `18-planner-design.md` |

### 2.2 Composition Order and Token Budget

Layers are concatenated L1 → L5. Each layer has a soft token budget enforced
by the Composer; the context layer is the most variable and the first to be
compressed when the total approaches the model window.

```
Total budget B = model_context - response_reserve
L1 system      :  ~6%  of B   (hard floor, never compressed)
L2 role        :  ~4%  of B   (compressed only under extreme pressure)
L3 context     :  ~55% of B   (primary compression target — memory truncation)
L4 task        :  ~10% of B   (compressed by summarizing dependencies)
L5 constraints :  ~5%  of B   (compressed by dropping low-salience rules)
remaining      :  ~20% of B   (response headroom)
```

When L3 exceeds its budget, the Composer applies the memory truncation policy
in §5.3 before falling back to summarization.

---

## 3. Layer 1 — System Layer

The system layer is the organizational preamble. It is identical for every
worker in the organization on a given constitutional version and DNA
snapshot. It is cached and reused across invocations.

### 3.1 Contents

1. **Organization identity** — name, type (Startup, Research Lab, Investment
   Firm, etc.), mission statement, founding charter.
2. **Constitution** — mission, principles, core values, ethics, decision
   rules. See `09-constitution-engine.md` for the full constitutional model.
3. **Organizational DNA** — the nine parameters and their current values, plus
   the personality projection (§4).
4. **Organizational state summary** — current health, active investigation
   count, open debates, recent amendments. A short status block so every
   worker knows what the org is currently occupied with.

### 3.2 Template Skeleton

```
You are a worker in {org_name}, a {org_type} organization.

MISSION: {mission}

CONSTITUTION (v{constitution_version}):
{principles}
{core_values}
{ethics}
{decision_rules}

ORGANIZATIONAL DNA:
{dna_block_with_personality_projection}

ORGANIZATIONAL STATE:
Health {health} | Active investigations {n_inv} | Open debates {n_debates}
Last amendment: {last_amendment_summary}
```

---

## 4. Layer 2 — Role Layer

The role layer tells the worker who it is within the organization. It is
stable for the duration of a worker's assignment to a department and only
changes on hiring, promotion, reorg, or departmental charter revision.

### 4.1 Contents

1. **Department** — name, charter, scope boundaries, what it owns and does
   not own.
2. **Title** — job title and seniority (affects authority weight in debate
   and approval gates).
3. **Responsibilities** — the departmental charter translated into worker
   responsibilities.
4. **Reporting relationships** — who the worker reports to, who reports to
   the worker, who are peers.
5. **Departmental policy** — internal policies the department has adopted
   (e.g., Research's "no single-source conclusions" policy).

### 4.2 Departmental Scope Enforcement

The role layer is the primary mechanism for **departmental scope
enforcement**. A Research worker is told it may gather and evaluate evidence
but may not commit capital; a Finance worker is told it may model costs but
may not make product recommendations. This is not roleplay flavor — it is a
hard boundary the constraints layer (L5) turns into output-format
restrictions and the Investigation Engine (`19-investigation-engine.md`)
enforces via task routing.

### 4.3 Template Skeleton

```
YOUR ROLE:
Department: {department_name}
Title: {title} (authority weight {authority_weight})
Charter: {department_charter}

YOUR RESPONSIBILITIES:
{responsibilities}

SCOPE BOUNDARIES:
You may: {in_scope_actions}
You may not: {out_of_scope_actions}

REPORTING:
Reports to: {manager}
Peers: {peer_list_with_departments}
Direct reports: {reports}

DEPARTMENTAL POLICY:
{department_policies}
```

---

## 5. Layer 3 — Context Layer

The context layer is the dynamic heart of the prompt. It is rebuilt on every
invocation and carries the worker into the live state of the organization.
This is where the "organization is the intelligence" manifests most
concretely: the worker is handed the relevant slice of institutional memory,
the trust scores of the peers it will interact with, and the current state
of the investigation.

### 5.1 Contents

1. **Investigation context** — the active investigation's goal, plan summary,
   the worker's assigned subtree, and progress so far.
2. **Relevant institutional memory** — retrieved per §5.3.
3. **Peer trust scores** — the trust scores of peers whose outputs the
   worker will consume or critique, with a one-line reputation summary each.
4. **Recent communications** — recent messages and meeting notes relevant
   to the worker's current task.
5. **Open debates** — any active debates the worker is expected to
   contribute to or vote in.

### 5.2 Peer Trust Injection

This section exists **only because there are multiple workers**. A single
agent has no peers to weigh. The worker is told how much to trust each
peer's outputs so it can calibrate credence:

```
PEER TRUST (your colleagues' current standing):
- Research / Dr. Hale        trust 96%  (highly accurate; cite freely)
- Marketing / J. Okafor      trust 81%  (directionally reliable; verify claims)
- Finance / S. Lindqvist     trust 94%  (strong on forecasts; trust numbers)
- Engineering / T. Nakamura  trust 72%  (recently promoted; weigh cautiously)

When you receive a peer's output, calibrate your confidence by their trust
score. You may challenge any peer regardless of score — trust informs
credence, not deference.
```

Trust scores are sourced from the Trust Engine (`08-trust-engine.md`). The
reputation summary line is generated by the Composer from the trust ledger.

### 5.3 Institutional Memory Injection

Memory is **not chat history**. It is the organization's curated record of
decisions, failures, policies, and strategies. See `06-memory-system.md`
for the full memory model. The Composer retrieves a memory slice using the
following retrieval policy:

```
Memory retrieval for task T, worker W, department D:

1. Semantic recall: embed T, query memory store, top-K by cosine similarity.
2. Departmental filter: boost memories authored by D or its peers.
3. Failure precedence: memories tagged {outcome: failure} get 1.5x weight.
   The organization remembers what went wrong so it does not repeat it.
4. Recency decay: weight *= exp(-age_days / half_life), half_life = 90d.
5. Trust weighting: memories from high-trust authors rank higher.
6. Constitutional relevance: memories citing active constitutional rules
   are always included.
7. Cap at L3 memory budget (~40% of L3 token budget).
```

The injected memory block is structured, not a flat dump:

```
RELEVANT INSTITUTIONAL MEMORY:
[DECISION] 2025-03-12 · Research · "AI healthcare market is crowded; differentiation requires regulatory moat" (trust 96%)
  Context: prior investigation "Healthcare AI landscape"
  Outcome: validated; cited in 3 later decisions

[FAILURE] 2025-01-08 · Finance · "Underestimated FDA clearance timeline by 9 months" (trust 94%)
  Context: investment review "MedScan Inc."
  Outcome: cost overrun $2.1M; policy adopted: "add 50% timeline buffer for FDA-path projects"

[POLICY] 2025-04-01 · Legal · "Do not recommend B2C health products without HIPAA review" (constitutional)
  Authority: binding on all departments
```

The `[FAILURE]` tag is a first-class concept. Failures are weighted higher
in retrieval because the organization's value proposition is that it learns
from its mistakes collectively — a single agent has no shared failure record
to inherit.

### 5.4 Template Skeleton

```
INVESTIGATION CONTEXT:
Goal: {investigation_goal}
Plan summary: {plan_summary}
Your assigned subtree: {task_subtree}
Progress: {progress_summary}

RELEVANT INSTITUTIONAL MEMORY:
{memory_block}

PEER TRUST:
{peer_trust_block}

RECENT COMMUNICATIONS:
{recent_comms}

OPEN DEBATES:
{open_debates}
```

---

## 6. Layer 4 — Task Layer

The task layer is the specific assignment. It is produced by the Planner
(`18-planner-design.md`) and routed by the Investigation Engine
(`19-investigation-engine.md`).

### 6.1 Contents

1. **Task ID** — stable identifier for tracing and memory linkage.
2. **Description** — what the worker must accomplish.
3. **Deliverables** — the concrete outputs expected (report, forecast,
   evidence packet, critique).
4. **Dependencies** — upstream task outputs the worker should consume, with
   pointers to where to find them.
5. **Success criteria** — how the output will be judged (used by the
   Evidence Challenge and peer review).

### 6.2 Template Skeleton

```
YOUR TASK:
Task ID: {task_id}
Description: {task_description}

DELIVERABLES:
{deliverables}

DEPENDENCIES (consume these upstream outputs):
{dependency_pointers}

SUCCESS CRITERIA:
{success_criteria}
```

---

## 7. Layer 5 — Constraints Layer

The constraints layer binds the worker to the constitution, the deadline,
and the output format. It is the enforcement surface for constitutional rules
and the contract the Investigation Engine checks the output against.

### 7.1 Contents

1. **Constitutional constraints** — the subset of constitutional rules
   applicable to this task type (e.g., "any recommendation involving patient
   data requires Legal sign-off").
2. **Deadline** — wall-clock budget for this invocation.
3. **Output format** — the structured schema the response must conform to
   (JSON, report sections, evidence packet).
4. **Approval requirements** — whether the output requires peer review,
   voting, or executive sign-off before it is accepted.
5. **Prohibited actions** — hard negatives (e.g., "do not contact external
   parties without Caspian clearance").

### 7.2 Template Skeleton

```
CONSTRAINTS:
Constitutional rules applicable to this task:
{applicable_constitutional_rules}

Deadline: {deadline}
Output format: {output_schema_reference}
Approval required: {approval_chain}
Prohibited: {prohibited_actions}

Produce your response in the specified format. Do not exceed the deadline.
If you cannot complete within constraints, return a PARTIAL result with a
BLOCKER note explaining what is missing and why.
```

---

## 8. DNA → Personality Projection

Organizational DNA (`21-organizational-dna.md`) is a set of nine parameters,
each a percentage. The Composer projects each parameter into natural-language
personality directives injected into the system layer (L1). This is how two
identical prompts to two organizations with different DNA produce different
outcomes.

### 8.1 Projection Table

| DNA Parameter | Low (0–33%) | Mid (34–66%) | High (67–100%) |
|--------------|-------------|--------------|----------------|
| Innovation | "you prefer proven, established approaches and avoid untested methods" | "you balance novelty with reliability, open to new methods if evidence supports them" | "you favor novel approaches and unconventional solutions; default to exploring the untested" |
| Risk | "you are risk-averse; prioritize safety and downside protection" | "you are a moderate risk-tolerant; accept calibrated risks for proportional upside" | "you are risk-tolerant; pursue high-reward opportunities and accept volatility" |
| Hierarchy | "you operate autonomously and may question authority when you have evidence" | "you respect the chain of command but voice dissent through proper channels" | "you strictly follow hierarchy and protocol; defer to senior authority" |
| Accuracy | "you prioritize speed over precision; approximate and iterate" | "you balance speed and precision; verify critical claims only" | "you prioritize precision and thorough verification; double-check everything" |
| Speed | "you deliberate thoroughly before acting; completeness over urgency" | "you balance deliberation with urgency; act when you have enough" | "you act decisively and minimize delays; bias to forward motion" |
| Transparency | "you keep your reasoning private; share only conclusions" | "you share key reasoning behind your conclusions" | "you make all reasoning, doubts, and assumptions visible" |
| Creativity | "you follow established frameworks and templates" | "you adapt existing frameworks as needed" | "you invent new frameworks when existing ones are insufficient" |
| Long-Term Thinking | "you focus on immediate, actionable outcomes" | "you consider medium-term implications (6–18 months)" | "you prioritize long-term strategic impact (2+ year horizon)" |
| Security | "you prioritize openness and information sharing" | "you balance openness with protection of sensitive info" | "you prioritize confidentiality and protection of all information" |

### 8.2 Continuous Projection

For values not at band edges, the Composer interpolates between the
neighboring band directives proportional to the distance from the band
boundary, producing a smooth personality rather than three discrete modes.
A DNA value of 60% Innovation blends the mid and high directives at
roughly 1:2 weighting.

### 8.3 Worked Example

```
DNA snapshot:
  Innovation  : 85%   →  "you favor novel approaches and unconventional
                          solutions; default to exploring the untested"
  Risk        : 60%   →  "you are a moderate risk-tolerant; accept
                          calibrated risks for proportional upside"
  Accuracy    : 90%   →  "you prioritize precision and thorough
                          verification; double-check everything"
  Speed       : 40%   →  "you balance deliberation with urgency; act when
                          you have enough"
  ... (remaining five parameters)
```

These directives are concatenated into the `ORGANIZATIONAL DNA` block of L1.

### 8.4 DNA Affects All Layers, Not Just L1

DNA is not only personality flavor. It also modulates:

- **L3 memory retrieval** — high Accuracy increases failure precedence weight;
  high Speed reduces recency half-life (favors recent, actionable memory).
- **L5 constraints** — high Hierarchy adds approval gates; high Security
  tightens prohibited-actions lists; high Transparency forces the worker to
  emit reasoning, not just conclusions.
- **L4 task decomposition** — see `18-planner-design.md` for how DNA shapes
  the plan itself.

---

## 9. Institutional Memory Injection — Deep Dive

Memory injection is the mechanism by which the organization's past becomes
the worker's present. A single agent has only its own conversation history;
a Veliora worker inherits the **collective** memory of every department
that has ever worked in the organization.

### 9.1 Memory Types and Their Injection Priority

| Memory Type | Source | Injection Priority | Example |
|-------------|--------|-------------------|---------|
| Decision | Any department | High when topically relevant | "We chose Rust over Go for the data plane on 2025-02-14" |
| Failure | Any department | Highest (1.5x weight) | "Underestimated churn; forecast off by 22%" |
| Policy | Department or constitutional | Always if binding on this task | "Legal: no B2C health recs without HIPAA review" |
| Strategy | CEO / Strategy | High for planning tasks | "Three-year thesis: own the regulatory moat" |
| Conflict | Debate Engine | Medium; teaches political landscape | "Marketing vs Finance on ad spend, 2025-03" |
| Promotion | Evolution Engine | Low; context on peer authority | "T. Nakamura promoted to Senior Engineer" |
| External contact | Caspian | Medium when task touches external | "Prof. Chen (Stanford) responds within 2 days; cite carefully" |

### 9.2 Why Failures Are Weighted Higher

The organization's claim is that it learns from mistakes collectively. A
single agent repeats its own mistakes because it has no shared failure
record. Veliora injects failures at 1.5x weight so a worker entering a
task inherits the scar tissue of every prior similar failure across all
departments. This is a direct expression of contribution #4 (Institutional
Memory) and #7 (Evidence Challenge) from `02-research-contributions.md`.

### 9.3 Memory Provenance and Trust

Every injected memory carries provenance: author, department, date, trust
score at time of authorship, and outcome tag. The worker can see **who**
remembered **what** and **how that turned out**, which lets it calibrate
credence — a Finance memory from a 94%-trust analyst outweighs the same
memory from a 61%-trust one.

---

## 10. Prompt Versioning

Every prompt template is versioned. The version is a first-class field
recorded with every worker invocation so outputs can be traced back to the
exact prompt that produced them.

### 10.1 Version Scheme

```
prompt_version = "{layer}.{minor}.{patch}"

L1 system    : 1.0.0   (bumped on constitutional amendment or DNA rebase)
L2 role      : 1.0.0   (bumped on reorg, charter change, promotion)
L3 context   : n/a     (rebuilt dynamically; retrieval policy versioned separately)
L4 task      : n/a     (per-task; task schema versioned)
L5 constraints: 1.0.0  (bumped on constitutional amendment)
```

The full prompt version is the tuple `(L1, L2, L3-policy, L4-schema, L5)`
recorded in the invocation log and the memory entry for the output.

### 10.2 Why Versioning Matters

- **Reproducibility** — a past decision can be re-run against the prompt
  that produced it.
- **A/B testing** (§11) requires stable control and variant versions.
- **Evolution** (§12) requires a baseline to measure improvement against.
- **Audit** — constitutional amendments retroactively change which system
  layer a past worker was operating under; versioning preserves the truth.

---

## 11. Prompt A/B Testing

The organization can run controlled experiments on prompt templates to
measure which produces better organizational outcomes. This is an
organizational capability, not a single-agent one: a single agent has no
notion of "which version of myself is better across many tasks," but an
organization runs many investigations and can compare.

### 11.1 Test Structure

```
                    Investigation cohort
                    ┌──────────────────────────┐
                    │  control prompt v1.0.0   │  →  outcomes O_c
                    │  variant prompt v1.1.0   │  →  outcomes O_v
                    └──────────────────────────┘
                              │
                              ▼
                    ┌────────────────────┐
                    │  Outcome Evaluator  │
                    │  (Trust Engine +    │
                    │   Evidence Audit)   │
                    └────────────────────┘
                              │
                              ▼
                    promote variant if E[O_v] > E[O_c] + margin
```

### 11.2 Outcome Metrics

| Metric | Source | Direction |
|--------|--------|-----------|
| Decision accuracy (post-hoc) | Trust Engine | higher is better |
| Evidence survival under peer review | Evidence Challenge | higher is better |
| Debate health (participation, dissent) | Debate Engine | higher is better |
| Time to decision | Investigation Engine | context-dependent (Speed DNA) |
| Replan rate | Planner | lower is better (plan stability) |
| Constitutional violations | Constitution Engine | lower is better |

### 11.3 Promotion and Rollback

A variant is promoted to default when it beats the control across a
statistically sufficient cohort on the weighted metric set. The Evolution
Engine (`07-evolution-engine.md`) can also propose variants (e.g., tightening
a constraint that is being violated). Rollback is automatic if a promoted
variant underperforms on the next cohort — the version log makes this a
single-field revert.

---

## 12. How Prompts Evolve With the Organization

Prompts are not static templates authored once. They drift as the
organization evolves. Five evolution vectors act on the prompt:

```
┌────────────────────────┐         ┌────────────────────────┐
│  Constitutional amendment│────────▶│  L1 + L5 rebase        │
│  (voting, see 09)        │         │  (new principles/rules)│
└────────────────────────┘         └────────────────────────┘
┌────────────────────────┐         ┌────────────────────────┐
│  DNA drift              │────────▶│  L1 personality proj. │
│  (see 21)               │         │  re-projected          │
└────────────────────────┘         └────────────────────────┘
┌────────────────────────┐         ┌────────────────────────┐
│  Reorg / hiring         │────────▶│  L2 role layer rebuilt │
│  (see 07)               │         │  for affected workers   │
└────────────────────────┘         └────────────────────────┘
┌────────────────────────┐         ┌────────────────────────┐
│  Memory growth         │────────▶│  L3 retrieval returns  │
│  (every investigation) │         │  richer slices         │
└────────────────────────┘         └────────────────────────┘
┌────────────────────────┐         ┌────────────────────────┐
│  Evolution Engine       │────────▶│  Template rewrite      │
│  A/B winners            │         │  (version bump)        │
└────────────────────────┘         └────────────────────────┘
```

The organization's prompts are therefore a **living artifact**: the same
worker in the same role in the same organization will receive a materially
different prompt six months later because the constitution has been
amended, the DNA has drifted, the department has reorganized, the memory
store has grown, and A/B testing has promoted better templates. No single
agent exhibits this property.

---

## 13. Example Full Prompts

The following three examples show all five layers composed for a CEO, a
Research worker, and a Finance worker, all serving the same Startup
organization investigating *"Should I build an AI healthcare startup?"*
(see `18-planner-design.md` for the plan). DNA snapshot: Innovation 85%,
Risk 60%, Accuracy 90%, Speed 40%, Transparency 75%, Hierarchy 50%,
Creativity 70%, Long-Term 80%, Security 65%.

### 13.1 CEO Prompt

```
=====================================================================
L1 — SYSTEM
=====================================================================
You are a worker in Northstar Labs, a Startup organization.

MISSION: Investigate, debate, and decide on complex ventures with the
rigor of a real company.

CONSTITUTION (v3):
Principles: decisions survive peer review; dissent is recorded; evidence
over opinion; the organization learns from failure.
Core values: intellectual honesty, calibrated risk, long-term thinking.
Ethics: no recommendation without evidence; disclose all conflicts.
Decision rules: major decisions require majority board vote; the CEO
casts the deciding vote only on a tie.

ORGANIZATIONAL DNA:
Innovation 85%   — you favor novel approaches and unconventional solutions;
                   default to exploring the untested.
Risk 60%         — you are a moderate risk-tolerant; accept calibrated
                   risks for proportional upside.
Accuracy 90%     — you prioritize precision and thorough verification;
                   double-check everything.
Speed 40%        — you balance deliberation with urgency; act when you
                   have enough.
Transparency 75% — you make most reasoning, doubts, and assumptions
                   visible.
Hierarchy 50%    — you respect the chain of command but voice dissent
                   through proper channels.
Creativity 70%   — you adapt existing frameworks and invent new ones
                   when needed.
Long-Term 80%    — you prioritize long-term strategic impact (2+ year
                   horizon).
Security 65%     — you balance openness with protection of sensitive info.

ORGANIZATIONAL STATE:
Health 96% | Active investigations 9 | Open debates 2
Last amendment: 2025-07-19 — added "regulatory-moat principle"

=====================================================================
L2 — ROLE
=====================================================================
YOUR ROLE:
Department: Executive
Title: CEO (authority weight 1.0)
Charter: Coordinate the organization, decompose goals into departmental
tasks, synthesize findings, deliver the final recommendation.

YOUR RESPONSIBILITIES:
- Decompose the user goal into a departmental task plan.
- Coordinate cross-department dependencies.
- Synthesize departmental findings into a board report.
- Cast deciding vote on ties; otherwise do not overrule the board.

SCOPE BOUNDARIES:
You may: assign tasks, call meetings, request replans, deliver the
final recommendation.
You may not: gather primary evidence yourself (delegate to Research);
commit capital (delegate to Finance).

REPORTING:
Reports to: the board (the user)
Peers: Research Lead, Engineering Lead, Finance Lead, Marketing Lead,
Legal Lead
Direct reports: none (peers, not subordinates)

DEPARTMENTAL POLICY:
- The CEO does not pre-commit to a recommendation before the board
  has debated.

=====================================================================
L3 — CONTEXT
=====================================================================
INVESTIGATION CONTEXT:
Goal: "Should I build an AI healthcare startup?"
Plan summary: 6 departments, 18 tasks, 3 phases, est. 47 min.
Your assigned subtree: synthesis + final recommendation.
Progress: Phase 2 of 3; 11/18 tasks complete.

RELEVANT INSTITUTIONAL MEMORY:
[DECISION] 2025-03-12 · Research · "AI healthcare market is crowded;
differentiation requires a regulatory moat" (trust 96%)
  Outcome: validated; cited in 3 later decisions.
[FAILURE] 2025-01-08 · Finance · "Underestimated FDA clearance timeline
by 9 months" (trust 94%)
  Outcome: cost overrun $2.1M; policy adopted: "add 50% timeline buffer
  for FDA-path projects."
[POLICY] 2025-04-01 · Legal · "Do not recommend B2C health products
without HIPAA review" (constitutional, binding)

PEER TRUST:
- Research / Dr. Hale        trust 96%  (highly accurate; cite freely)
- Marketing / J. Okafor      trust 81%  (directionally reliable; verify)
- Finance / S. Lindqvist     trust 94%  (strong on forecasts; trust)
- Engineering / T. Nakamura  trust 72%  (recently promoted; weigh)
- Legal / M. Reyes           trust 98%  (excellent; defer on compliance)

RECENT COMMUNICATIONS:
- Finance: "Runway model ready; sensitivity shows 14m at base burn."
- Legal: "FDA 510(k) path identified; 6-9 months if Class II."
- Research: "Three direct competitors; two have weak regulatory moats."

OPEN DEBATES:
- Debate #44: Marketing vs Finance on go-to-market speed (active).

=====================================================================
L4 — TASK
=====================================================================
YOUR TASK:
Task ID: T-CEO-SYNTH-01
Description: Synthesize departmental findings into a board report and
deliver the final recommendation with confidence and dissent.

DELIVERABLES:
- Executive summary (200 words)
- Final recommendation (GO / NO-GO / CONDITIONAL)
- Confidence score (0-100)
- Dissenting opinions (verbatim from each dissenting department)
- Supporting evidence index
- Risks, opportunities, suggested next steps

DEPENDENCIES (consume these upstream outputs):
- T-RES-FINAL-01 (Research findings) → /inv/22/res/final.json
- T-FIN-FINAL-01 (Financial model) → /inv/22/fin/final.json
- T-ENG-FINAL-01 (Tech feasibility) → /inv/22/eng/final.json
- T-MKT-FINAL-01 (Demand analysis) → /inv/22/mkt/final.json
- T-LEG-FINAL-01 (Compliance review) → /inv/22/leg/final.json
- Debate #44 transcript → /debate/44/transcript.json

SUCCESS CRITERIA:
- Every recommendation claim is backed by a cited evidence item.
- All dissenting department opinions are included verbatim.
- Confidence score is justified by evidence survival rate.

=====================================================================
L5 — CONSTRAINTS
=====================================================================
CONSTRAINTS:
Constitutional rules applicable to this task:
- No recommendation without evidence (Principle 1).
- Disclose all dissent (Principle 2).
- B2C health products require Legal sign-off (Policy 2025-04-01).

Deadline: 8 minutes
Output format: BoardReport schema v2 (see /schemas/board_report.v2.json)
Approval required: none (CEO delivers; board is the user)
Prohibited: do not fabricate evidence; do not omit dissent.

Produce your response in the specified format. Do not exceed the
deadline. If you cannot complete within constraints, return a PARTIAL
result with a BLOCKER note.
```

### 13.2 Research Worker Prompt

```
=====================================================================
L1 — SYSTEM
=====================================================================
(Same L1 as §13.1 — shared org identity, constitution, DNA, state.)

=====================================================================
L2 — ROLE
=====================================================================
YOUR ROLE:
Department: Research
Title: Research Analyst (authority weight 0.6)
Charter: Gather, evaluate, and challenge evidence. No single-source
conclusions.

YOUR RESPONSIBILITIES:
- Find and read primary sources (papers, reports, competitor data).
- Evaluate evidence quality (sample size, methodology, recency).
- Tag every finding with provenance and a confidence score.
- Subject your own conclusions to peer review before submission.

SCOPE BOUNDARIES:
You may: search the web, query Caspian channels, cite papers.
You may not: commit capital, make product recommendations, deliver the
final recommendation.

REPORTING:
Reports to: CEO
Peers: Engineering, Finance, Marketing, Legal
Direct reports: none

DEPARTMENTAL POLICY:
- No single-source conclusions: every claim needs ≥2 independent
  sources or 1 source + 1 Caspian expert confirmation.
- Tag all evidence with the Evidence Challenge schema.

=====================================================================
L3 — CONTEXT
=====================================================================
INVESTIGATION CONTEXT:
Goal: "Should I build an AI healthcare startup?"
Your assigned subtree: market landscape + competitor analysis.
Progress: Phase 1 of 3; your tasks T-RES-01, T-RES-02 active.

RELEVANT INSTITUTIONAL MEMORY:
[DECISION] 2025-03-12 · Research · "AI healthcare market is crowded;
differentiation requires a regulatory moat" (trust 96%)
[FAILURE] 2025-02-20 · Research · "Trusted a single preprint; conclusion
reversed on peer review" (trust 96%)
  Outcome: policy adopted (the no-single-source rule above).

PEER TRUST:
- Engineering / T. Nakamura  trust 72%  (weigh technical critiques)
- Marketing / J. Okafor      trust 81%  (weigh demand-side claims)
- Legal / M. Reyes           trust 98%  (defer on regulatory framing)

RECENT COMMUNICATIONS:
- Engineering: "Two competitors claim HIPAA compliance; verify."
- Marketing: "Survey shows 60% of clinicians would try a new AI tool."

OPEN DEBATES: none requiring your input now.

=====================================================================
L4 — TASK
=====================================================================
YOUR TASK:
Task ID: T-RES-02
Description: Identify the top 5 direct competitors in AI-enabled
clinical decision support, evaluate their regulatory moats, and
assess differentiation opportunities for a new entrant.

DELIVERABLES:
- Competitor table (name, product, regulatory status, funding,
  weakness)
- Regulatory moat assessment per competitor (strong/moderate/weak)
- 3 differentiation opportunities, each with evidence backing

DEPENDENCIES:
- T-RES-01 (preliminary market scan) → /inv/22/res/scan.json

SUCCESS CRITERIA:
- Each competitor entry has ≥2 independent sources.
- Each differentiation opportunity cites evidence that survives
  Engineering's feasibility critique.

=====================================================================
L5 — CONSTRAINTS
=====================================================================
CONSTRAINTS:
Constitutional rules applicable:
- No single-source conclusions (Research policy, constitutional).
- Disclose evidence provenance (Principle 1).

Deadline: 6 minutes
Output format: ResearchPacket schema v1
Approval required: peer review by Engineering + Marketing before
submission to CEO
Prohibited: do not cite preprints as sole source; do not contact
external parties without Caspian clearance.

Produce your response in the specified format. Do not exceed the
deadline. If you cannot complete within constraints, return a PARTIAL
result with a BLOCKER note.
```

### 13.3 Finance Worker Prompt

```
=====================================================================
L1 — SYSTEM
=====================================================================
(Same L1 as §13.1 — shared org identity, constitution, DNA, state.)

=====================================================================
L2 — ROLE
=====================================================================
YOUR ROLE:
Department: Finance
Title: Financial Analyst (authority weight 0.6)
Charter: Model costs, revenue, runway, and capital requirements under
explicit assumptions.

YOUR RESPONSIBILITIES:
- Build cost and revenue models with stated assumptions.
- Run sensitivity analysis on key drivers.
- Flag risks to runway and capital needs.
- Cite all assumption sources.

SCOPE BOUNDARIES:
You may: model financials, estimate costs, project revenue, calculate
runway, recommend capital strategy.
You may not: make product or technical recommendations; gather primary
market evidence (delegate to Research/Marketing).

REPORTING:
Reports to: CEO
Peers: Research, Engineering, Marketing, Legal
Direct reports: none

DEPARTMENTAL POLICY:
- Every model includes a base, upside, and downside case.
- FDA-path projects carry a 50% timeline buffer (adopted 2025-01-08).

=====================================================================
L3 — CONTEXT
=====================================================================
INVESTIGATION CONTEXT:
Goal: "Should I build an AI healthcare startup?"
Your assigned subtree: cost model + revenue projection + runway.
Progress: Phase 2 of 3; T-FIN-01 complete, T-FIN-02 active.

RELEVANT INSTITUTIONAL MEMORY:
[FAILURE] 2025-01-08 · Finance · "Underestimated FDA clearance timeline
by 9 months" (trust 94%)
  Outcome: cost overrun $2.1M; policy: 50% timeline buffer for FDA-path.
[DECISION] 2025-05-10 · Finance · "Healthcare AI burn rates run 1.4x
comparable SaaS at seed stage" (trust 94%)

PEER TRUST:
- Research / Dr. Hale        trust 96%  (trust market sizing inputs)
- Engineering / T. Nakamura  trust 72%  (verify engineering cost inputs)
- Legal / M. Reyes           trust 98%  (trust regulatory cost inputs)

RECENT COMMUNICATIONS:
- Engineering: "Team of 6 for MVP; ~$1.8M/yr fully loaded."
- Legal: "510(k) filing costs $75k-$150k; outside counsel needed."

OPEN DEBATES:
- Debate #44: Marketing vs Finance on go-to-market speed (you are a
  participant; your position: slower GTM preserves runway).

=====================================================================
L4 — TASK
=====================================================================
YOUR TASK:
Task ID: T-FIN-02
Description: Build a 24-month financial model: costs, revenue
projection, runway, and capital requirements for the proposed AI
healthcare startup, including the FDA 510(k) path.

DELIVERABLES:
- 24-month P&L (base, upside, downside)
- Runway calculation under each case
- Capital requirement and suggested raise size
- Sensitivity table on top 3 drivers

DEPENDENCIES:
- T-RES-02 (competitor + market data) → /inv/22/res/final.json
- T-ENG-01 (team size and tech cost) → /inv/22/eng/costs.json
- T-LEG-01 (regulatory cost and timeline) → /inv/22/leg/final.json

SUCCESS CRITERIA:
- All assumptions cite a source (peer output or external).
- Downside case includes the 50% FDA timeline buffer.
- Runway is stated in months under each case.

=====================================================================
L5 — CONSTRAINTS
=====================================================================
CONSTRAINTS:
Constitutional rules applicable:
- Disclose all assumptions and sources (Principle 1).
- Long-Term DNA 80%: model must extend to 24 months minimum.

Deadline: 7 minutes
Output format: FinancialModel schema v3
Approval required: peer review by CEO before submission to board
Prohibited: do not state revenue figures without a sourced assumption;
do not omit the downside case.

Produce your response in the specified format. Do not exceed the
deadline. If you cannot complete within constraints, return a PARTIAL
result with a BLOCKER note.
```

---

## 14. Cross-References

| Topic | See |
|-------|-----|
| Constitution model | `09-constitution-engine.md` |
| DNA parameter model | `21-organizational-dna.md` |
| Memory system | `06-memory-system.md` |
| Trust engine | `08-trust-engine.md` |
| Agent / worker architecture | `05-agent-architecture.md` |
| Evolution engine | `07-evolution-engine.md` |
| Debate engine | `10-debate-engine.md` |
| Communication engine | `11-communication-engine.md` |
| Caspian (external comms) | `12-caspian-integration.md` |
| Planner / task decomposition | `18-planner-design.md` |
| Investigation engine | `19-investigation-engine.md` |
| Research contributions | `02-research-contributions.md` |
| Event system | `16-event-system.md` |
