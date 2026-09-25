# 02 — Novel Research Contributions

> **Veliora is not a multi-agent platform.** It is an autonomous
> organization. Each of the twelve contributions below exists **because** the
> unit of intelligence is an organization, not a single model. Every
> contribution is evaluated against the **One Agent Test**: *would this
> feature still make sense if there were only one AI?* If yes, it does not
> belong in this document.

This document frames the twelve innovations introduced in `SPEC.md` as
formal research contributions. For each contribution we give: the problem
it solves, the existing state of the art, the novel approach Veliora
takes, why it matters academically, and the **One Agent Test** verdict
that justifies its existence. A consolidated comparison table and a
research-agenda roadmap close the document.

Cross-references:
- `01-core-philosophy.md` — Organizations-as-intelligence paradigm.
- `04-technical-architecture.md` — System topology that hosts these
  contributions.
- `10-debate-engine.md` — Society-of-Thought mechanics.
- `06-memory-system.md` — Institutional Memory.
- `08-trust-engine.md` — Weighted Trust.
- `09-constitution-engine.md` — Constitutional Organizations.
- `07-evolution-engine.md` — Living Organizations & Organization Evolution.
- `21-organizational-dna.md` — Organizational DNA parameters.
- `12-caspian-integration.md` — External Intelligence.
- `19-investigation-engine.md` — Live Investigation & Evidence Challenge.

---

## Table of Contents

1. [Contribution Index](#1-contribution-index)
2. [C1 — Society-of-Thought (vs Chain-of-Thought / Tree-of-Thought)](#c1--society-of-thought-vs-chain-of-thought--tree-of-thought)
3. [C2 — Living Organizations (vs Static Agent Graphs)](#c2--living-organizations-vs-static-agent-graphs)
4. [C3 — Organizational DNA (vs Hyperparameter Tuning)](#c3--organizational-dna-vs-hyperparameter-tuning)
5. [C4 — Institutional Memory (vs RAG / Chat History)](#c4--institutional-memory-vs-rag--chat-history)
6. [C5 — Internal Politics (vs Cooperative Agents)](#c5--internal-politics-vs-cooperative-agents)
7. [C6 — Live Investigation (vs Streaming / Progress Indicators)](#c6--live-investigation-vs-streaming--progress-indicators)
8. [C7 — Evidence Challenge (vs Self-Consistency)](#c7--evidence-challenge-vs-self-consistency)
9. [C8 — Weighted Trust (vs Uniform Agents)](#c8--weighted-trust-vs-uniform-agents)
10. [C9 — External Intelligence (vs Web Search)](#c9--external-intelligence-vs-web-search)
11. [C10 — Multiple Independent Organizations (vs Ensemble Methods)](#c10--multiple-independent-organizations-vs-ensemble-methods)
12. [C11 — Constitutional Organizations (vs System Prompts)](#c11--constitutional-organizations-vs-system-prompts)
13. [C12 — Organization Evolution (vs Manual Orchestration)](#c12--organization-evolution-vs-manual-orchestration)
14. [Consolidated Comparison Table](#13-consolidated-comparison-table)
15. [Research Agenda](#14-research-agenda)

---

## 1. Contribution Index

```
+------+---------------------------+--------------------------------+----------------------+
| #    | Contribution              | Replaces                       | One Agent Test       |
+------+---------------------------+--------------------------------+----------------------+
| C1   | Society-of-Thought        | Chain/Tree-of-Thought          | FAILS → social       |
| C2   | Living Organizations      | Static agent graphs            | FAILS → emergent     |
| C3   | Organizational DNA        | Hyperparameter tuning          | FAILS → cultural     |
| C4   | Institutional Memory      | RAG / chat history             | FAILS → collective   |
| C5   | Internal Politics         | Cooperative agents             | FAILS → adversarial  |
| C6   | Live Investigation         | Streaming / spinners           | marginal → process   |
| C7   | Evidence Challenge        | Self-consistency               | FAILS → peer review  |
| C8   | Weighted Trust            | Uniform agents                 | FAILS → reputation   |
| C9   | External Intelligence     | Web search                     | FAILS → outreach     |
| C10  | Multiple Organizations    | Ensemble methods               | FAILS → societies     |
| C11  | Constitutional Orgs       | System prompts                 | FAILS → governance   |
| C12  | Organization Evolution    | Manual orchestration           | FAILS → self-modify  |
+------+---------------------------+--------------------------------+----------------------+
```

Each contribution below follows the same structure: **Problem → State of
the Art → Novel Approach → Academic Significance → One Agent Test**.

---

## C1 — Society-of-Thought (vs Chain-of-Thought / Tree-of-Thought)

### Problem

Reasoning in LLMs is **unobservable and individual**. Chain-of-Thought
(Wei et al., 2022) and Tree-of-Thought (Yao et al., 2023) improve answer
quality by exposing intermediate steps, but the steps are produced by a
single model reasoning privately. There is no debate, no dissent, no
cross-examination. Errors in the hidden chain propagate to the answer
because nothing challenges them.

### State of the Art

```
CoT:    Q → [step1 → step2 → step3] → A          (one model, linear)
ToT:    Q → {branch1, branch2, branch3} → A     (one model, tree search)
GoT:    Q → graph(steps) → A                    (one model, graph)
LLM-Debate: n models argue → vote → A           (multi-model, but flat)
```

LLM-Debate (Du et al., 2023) and Multi-Agent Debate (Liang et al., 2023)
introduce adversarial rounds, but the participants are **peers without
specialization**, the debate is **stateless across sessions**, and there
is no institutional structure that persists the conclusions.

### Novel Approach

Veliora introduces **Society-of-Thought (SoT)**: reasoning is the
**observable communication between specialized departments**, persisted in
institutional memory, governed by a constitution, and resolved through
weighted voting rather than a single model's private chain.

```
            ┌───────────  Research  ───────────┐
            │              │                   │
            ▼              ▼                   ▼
         Evidence     Methodology         Prior Work
            │              │                   │
            └──────────────┴───────────┬───────┘
                                       │
                          ┌────────────▼────────────┐
                          │   Engineering critique   │
                          │   (sample size, bias)    │
                          └────────────┬────────────┘
                                       │
                          ┌────────────▼────────────┐
                          │  Marketing: user signal  │
                          └────────────┬────────────┘
                                       │
                                 ┌─────▼─────┐
                                 │  Vote (w) │  ← weighted trust
                                 └─────┬─────┘
                                       │
                                       ▼
                                    Decision
```

Three structural differences from prior work:

1. **Specialization.** Participants are not interchangeable debaters; they
   are departments with distinct objectives, tooling, and memory.
2. **Persistence.** The reasoning trace is stored as institutional memory
   and influences future investigations (see `06-memory-system.md`).
3. **Weighted resolution.** Convergence is not majority vote but a
   reputation-weighted aggregate (see C8 and `08-trust-engine.md`).

### Academic Significance

SoT reframes reasoning as an **organizational process** rather than a
cognitive one. It predicts that reasoning quality scales with
**institutional design** (specialization, trust, memory) rather than
model size alone — a testable hypothesis that diverges from the
scaling-laws school. It also makes reasoning **auditable**: every
conclusion has a provenance graph traceable to a department, an
evidence artifact, and a vote.

### One Agent Test

**Fails.** A single agent has nobody to argue with, no specialization to
divide, and no vote to weight. SoT only exists because the unit of
intelligence is plural.

---

## C2 — Living Organizations (vs Static Agent Graphs)

### Problem

Multi-agent systems are **specified at design time**. The graph of
agents, their roles, their connections, and their relative influence is
authored by a developer and frozen for the lifetime of the system. Real
organizations are not like this: they grow, shrink, promote, demote, and
restructure themselves in response to outcomes.

### State of the Art

- **CrewAI** — agents and tasks are defined in Python and fixed per run.
- **LangGraph** — the graph is compiled; nodes and edges are static.
- **AutoGen** — conversations are scripted; agent set is fixed.
- **OpenAI Swarm** — handoff routines are pre-authored.

In all cases, the topology is a **design-time artifact**. Adaptation, when
it exists, is limited to routing decisions inside a frozen graph.

### Novel Approach

Veliora organizations are **living**: their structure emerges from
performance and changes themselves. The Evolution Engine (see
`07-evolution-engine.md`) continuously evaluates departments against
organizational outcomes and applies structural mutations:

```
  outcome signal: Marketing repeatedly fails to predict demand
        │
        ▼
  ┌──────────────────────────────────────────┐
  │  Evolution Engine                         │
  │  • decreases Marketing influence weight   │
  │  • flags Marketing as underperforming     │
  │  • if budget allows, spawns a Data Analyst│
  │    to support Marketing                  │
  │  • if persistent, opens a "replace CMO"  │
  │    motion in the next board meeting       │
  └──────────────────────────────────────────┘
        │
        ▼
  new topology persisted to Institutional Memory
```

Mutations are not random; they are **outcome-driven** and **constitutionally
governed** (see C11). A department that repeatedly fails loses influence
*automatically*; nobody programs this — it emerges from the trust and
evolution engines reacting to evidence.

### Academic Significance

This introduces **structural self-modification** to LLM systems. The
closest analog is neural architecture search (NAS), but NAS operates on
continuous weights; Veliora operates on a **discrete organizational
graph** with semantic roles. The research question is whether
organizational structure can be **learned online** from environmental
feedback the way neural weights are learned from gradients — and whether
the resulting structures outperform human-authored ones.

### One Agent Test

**Fails.** A single agent cannot restructure itself into multiple
agents, cannot promote or demote peers it does not have, and cannot
reallocate budget across departments that do not exist. Living structure
requires plurality.

---

## C3 — Organizational DNA (vs Hyperparameter Tuning)

### Problem

Hyperparameters tune a **model**; they do not tune a **culture**. Two
calls to the same LLM with the same prompt and same hyperparameters yield
near-identical behavior. But two organizations given the same goal — a
startup and a research lab — should behave radically differently, and
those differences should be **first-class, named, and tunable**.

### State of the Art

- **Hyperparameter tuning** (learning rate, temperature, top-p) —
  continuous, model-level, optimization-targeted.
- **System prompts** — string-level behavior steering, single-agent.
- **Persona assignment** — "you are a cautious analyst" — flat, ad hoc.

None of these capture **organizational culture**: how risk-averse the
whole society is, how hierarchical its decisions are, how transparent its
debates are, how long-term its planning horizon is.

### Novel Approach

Veliora introduces **Organizational DNA**: a vector of named
organizational parameters that shape communication, planning, approvals,
hiring, and voting across the entire society. See `21-organizational-dna.md`
for the full parameter model.

```
  DNA = {
    Innovation:      0.82   # funds speculative research
    Risk:            0.30   # vetoes high-variance bets
    Hierarchy:       0.65   # CEO approval required for >$50k
    Accuracy:        0.90   # requires 2-source evidence
    Speed:           0.40   # tolerates long investigations
    Transparency:    0.75   # debates visible to user
    Creativity:      0.70   # rewards dissenting opinions
    LongTerm:        0.85   # 5-year horizon in forecasts
    Security:        0.60   # legal review on external comms
  }
```

Two identical prompts produce **different outcomes** depending on DNA.
A "Startup Society" (high Innovation, high Risk, low Hierarchy) and a
"Research Lab" (low Risk, high Accuracy, high LongTerm) given the same
question *"Should we pursue nuclear fusion?"* will produce different
recommendations because their DNA shapes every sub-decision downstream.

DNA is **inherited** by spawned sub-organizations and can be
**mutated** by the Evolution Engine (C2) and **amended** by constitutional
vote (C11).

### Academic Significance

DNA lifts hyperparameters from the **model** level to the **institution**
level. It predicts that organizational behavior is parameterizable along
stable cultural axes, and that these axes compose multiplicatively
across decisions. This is a candidate for empirical validation: do
DNA-tuned organizations produce measurably different decision
distributions on a fixed benchmark?

### One Agent Test

**Fails.** Hierarchy, transparency, and dissent have no meaning for a
single agent. DNA is inherently plural.

---

## C4 — Institutional Memory (vs RAG / Chat History)

### Problem

RAG retrieves **documents**; chat history retrieves **utterances**.
Neither captures the **organizational knowledge** that accumulates over
time: *why* a decision was made, *who* dissented, *which* strategy failed
and *why*, *what* policy was enacted after the last crisis. Years later,
a real organization remembers its own history; an LLM does not.

### State of the Art

- **RAG** — vector search over external corpora; the model has no memory
  of its own prior actions.
- **Long-context chat** — linear transcript; unstructured, expensive,
  forgetful under compression.
- **Agent memory frameworks** (MemGPT, A-MEM) — per-agent episodic
  memory; not shared, not institutional.

### Novel Approach

Veliora introduces **Institutional Memory**: a shared, structured,
queryable store of organizational experience that is **collective**
(owned by the society, not any agent), **causal** (records *why*, not
just *what*), and **influential** (shapes future decisions, not just
recalls facts). See `06-memory-system.md`.

```
  Institutional Memory
  ├── decisions/         # every concluded decision + rationale
  │   ├── 2026-04-12-merge-acme.md
  │   └── 2026-05-03-pivot-b2b.md
  ├── failures/          # post-mortems with root-cause
  ├── strategies/        # playbooks that worked
  ├── conflicts/         # recorded dissent + resolution
  ├── policies/          # internal rules enacted by vote
  ├── promotions/        # trust-weight changes over time
  └── dna-amendments/    # constitutional history
```

Each entry is a **typed record** with provenance, not free text. When
Research begins a new investigation, the Planner queries Institutional
Memory for analogous prior investigations and injects the relevant
playbooks, failures, and dissenting opinions into the planning context.

### Academic Significance

This is **organizational learning** for LLM systems, distinct from
model fine-tuning. The research question: can an LLM organization
**compound knowledge** across investigations without retraining, by
writing to and reading from a structured institutional store? If yes,
the organization improves with experience at **zero gradient cost**.

### One Agent Test

**Fails.** A single agent has no peers whose dissent to remember, no
promotions to record, no policies enacted by vote. Institutional memory
is inherently the memory of a collective.

---

## C5 — Internal Politics (vs Cooperative Agents)

### Problem

Most multi-agent frameworks assume **cooperative** agents with aligned
objectives. Real organizations are not like this: Marketing wants growth,
Finance wants savings, Legal wants safety, Research wants experimentation.
These objectives genuinely conflict, and the conflict is the **engine**
of better decisions, not a bug to be suppressed.

### State of the Art

- **Cooperative multi-agent RL** — shared reward, no conflict.
- **LLM debate** — conflict is manufactured for the debate round, then
  dropped; no persistent opposing objectives.
- **Roleplay personas** — agents are *told* to be cautious or bold, but
  have no stake in the outcome.

### Novel Approach

Veliora departments have **persistent, objective-driven mandates**
that genuinely conflict. Conflict is not roleplay; it is the natural
consequence of distinct mandates interacting over scarce resources
(budget, attention, time).

```
  Marketing mandate:  maximize growth and market share
  Finance mandate:    maximize runway and minimize burn
  Research mandate:   maximize evidence quality and novelty
  Legal mandate:      minimize regulatory exposure
  Engineering mandate: maximize technical feasibility and quality
```

When the question is *"Should we spend $200k on a launch campaign?"*,
Marketing will advocate, Finance will resist, Legal will check
compliance, and Research will ask for evidence of demand. The
**resolution** is a weighted vote (C8) governed by the constitution
(C11), not a hand-coded arbitration.

### Academic Significance

This models **multi-objective decision-making under stakeholder
conflict** with LLMs as the stakeholders. It predicts that decisions
produced by genuine mandate conflict are **more robust** than those
produced by cooperative agents, because the conflict forces evidence
gathering and stress-testing that cooperation skips. This is
empirically testable on decision-quality benchmarks.

### One Agent Test

**Fails.** A single agent cannot have conflicting mandates with itself
in any structured sense; there is no second party to negotiate with.

---

## C6 — Live Investigation (vs Streaming / Progress Indicators)

### Problem

LLM interfaces show a spinner or a streaming token sequence. The user
has no visibility into *what is being investigated*, *which sources are
being consulted*, or *which departments are active*. The process is a
black box between prompt and answer.

### State of the Art

- **Streaming tokens** — character-level visibility into the answer.
- **Progress bars / "Thinking..."** — opaque waiting indicators.
- **Agent trace logs** (LangSmith, Langfuse) — developer-facing, not
  user-facing; not real-time to the end user.

### Novel Approach

Veliora investigations are **live observable organizational activity**.
The user watches Research reading 15 papers, Marketing surveying users,
Finance building a forecast, Engineering evaluating architecture, CEO
waiting for reports. See `19-investigation-engine.md` and `22-mobile-ux.md`.

```
  Veliora                    09:42
  ─────────────────────────────────
  CEO          Waiting for reports
  Research      Reading paper 7/15  ▓▓▓▓▓▓▓░░░  47%
  Engineering   Evaluating architecture
  Finance       Building forecast model
  Marketing     Surveying 23 users   ▓▓▓▓▓▓▓▓░░  82%
  ─────────────────────────────────
  9 Active Investigations
  2 Meetings in progress
  34 Messages today
  Organization Health  96%
```

This is not a progress bar; it is a **live organizational dashboard**.
Each row is a department with a current activity, a progress indicator,
and a tap target that opens the department's live conversation, evidence
queue, or meeting room.

### Academic Significance

Live Investigation turns the reasoning process into a **first-class
observable artifact** rather than a means to an answer. It enables a
new kind of study: *what does an LLM organization actually do* while it
decides? The trace is structured (per-department, per-evidence,
per-message) and can be replayed, audited, and benchmarked.

### One Agent Test

**Marginal.** A single agent could show "reading paper 7/15", but the
multi-department simultaneity — Research reading while Marketing surveys
while Finance forecasts — only makes sense as **parallel
organizational activity**. The feature earns its place because it
exposes the *organizational* nature of the work.

---

## C7 — Evidence Challenge (vs Self-Consistency)

### Problem

Self-consistency (Wang et al., 2022) samples *n* answers from the same
model and takes the majority. This catches sampling noise but not
**systematic bias**: if the model is wrong for the same reason *n* times,
the majority is still wrong. There is no adversarial challenge to the
evidence itself.

### State of the Art

- **Self-consistency** — majority vote over iid samples.
- **Universal Self-Consistency** — LLM picks the best answer from *n*.
- **Self-refine / Self-critique** — the same model critiques itself;
  limited by the model's own blind spots.

### Novel Approach

Veliora introduces **Evidence Challenge**: departments do not only
collect evidence, they **criticize** each other's evidence. Research
finds a paper; Engineering says the sample size is too small; Research
rejects; Marketing says customers disagree; Research updates
conclusions. The final recommendation **survives peer review**.

```
  Research:    "Paper X shows 94% efficacy (n=12)."
       │
       ▼
  Engineering:"n=12 is underpowered. Reject."
       │
       ▼
  Research:    "Rejected. Searching for replication."
       │
       ▼
  Marketing:   "Our survey of 240 users shows 61% interest."
       │
       ▼
  Research:    "Updated conclusion: demand signal strong,
               efficacy signal weak. Confidence: 0.62."
       │
       ▼
  Vote:        Recommendation conditionally approved,
               contingent on larger study.
```

Crucially, the critic is a **different department** with a different
mandate and different blind spots, not the same model sampling again.
This is **adversarial cross-examination** of evidence, not statistical
averaging.

### Academic Significance

Evidence Challenge predicts that **cross-specialization critique**
catches biases that self-consistency cannot, because the critics bring
different priors and different tooling. This is testable: run the same
evidence-bearing question under self-consistency and under Evidence
Challenge and measure the bias-reduction delta.

### One Agent Test

**Fails.** A single agent cannot cross-examine itself with a different
mandate and different tools; it can only sample itself again.

---

## C8 — Weighted Trust (vs Uniform Agents)

### Problem

In multi-agent systems, agents are typically **uniform**: each agent's
opinion counts equally. But in a real organization, the Head of Research
with 96% historical accuracy should outweigh the Head of Marketing with
81% accuracy on questions of evidence. Uniform aggregation throws away
the signal of who has been right before.

### State of the Art

- **Majority vote / plurality** — uniform weights.
- **LLM-Debate** — uniform debaters, uniform vote.
- **Mixture-of-Experts** — learned routing weights, but the experts are
  anonymous and the weights are not interpretable reputation.

### Novel Approach

Veliora introduces **Weighted Trust**: every agent earns a reputation
score that is updated by outcome feedback and used to weight its
influence in future votes. See `08-trust-engine.md`.

```
  reputation(agent, domain) ∈ [0, 1]

  Research  accuracy: 0.96   → vote weight 2.4
  Finance   accuracy: 0.94   → vote weight 2.2
  Marketing accuracy: 0.81   → vote weight 1.3
  Legal     accuracy: 0.92   → vote weight 2.0
  Engineering accuracy: 0.88 → vote weight 1.7

  decision = Σ (weight_i × position_i) / Σ weight_i
```

Reputation is **domain-specific** (Research may be trusted on evidence,
not on financial forecasting), **outcome-driven** (updated when
predictions are later verified), and **interpretable** (a user can ask
"why does Research outvote Marketing?" and get a provenance trace).

### Academic Significance

Weighted Trust introduces **interpretable, outcome-calibrated
reputation** to LLM aggregation. Unlike MoE routing weights, Veliora
trust scores are **human-readable**, **auditable**, and **causally tied
to verified outcomes**. The research question: does reputation-weighted
voting outperform uniform voting on decision benchmarks, and how
quickly does the trust signal become informative?

### One Agent Test

**Fails.** A single agent has no peers to outvote or be outvoted by;
reputation is meaningless without a collective to weigh it against.

---

## C9 — External Intelligence (vs Web Search)

### Problem

Web search returns documents. But many questions require **live human
signal**: what do users think, what does a professor say, what does an
accountant recommend, is a candidate interested in a role. Search cannot
ask follow-up questions, cannot negotiate, cannot poll a community.

### State of the Art

- **Web search / RAG** — passive retrieval of published documents.
- **Tool-using agents** — call search APIs; still passive retrieval.
- **Browser agents** (Manus, Devin) — navigate the web; still
  interacting with documents, not people.

None of these **initiate conversations with humans** as part of
reasoning.

### Novel Approach

Veliora introduces **External Intelligence via Caspian**: agents
**communicate with real people** through real channels. Marketing asks
users on Telegram. Research emails professors. Finance contacts
accountants. Legal emails lawyers. Recruiter contacts candidates. See
`12-caspian-integration.md`.

```
  Research   ──email──►  Professor of epidemiology
  Research   ──telegram──►  Founder of competitor
  Marketing  ──telegram──►  240 beta users (survey)
  Finance    ──email──►  Accountant (tax question)
  Legal      ──email──►  Outside counsel (licensing)
  Recruiter  ──email──►  Candidate (interest probe)
```

The organization **actively gathers evidence from people**, not just
documents. Communication is bidirectional, visible, and logged to
Institutional Memory. The organization naturally chooses the best
channel for each interaction (email for formality, Telegram for speed,
Slack for internal, Discord for community).

### Academic Significance

This makes **human-in-the-loop evidence gathering** a first-class
reasoning step rather than a UI afterthought. The research question:
does active human consultation improve decision quality over
document-only retrieval, and at what latency cost? This connects to
active learning and value-of-information theory, but applied to
**people as evidence sources** rather than unlabeled data.

### One Agent Test

**Fails.** A single agent could email a professor, but the
**specialization** — Research talks to professors, Marketing talks to
users, Legal talks to lawyers — only makes sense as an organizational
division of outreach labor.

---

## C10 — Multiple Independent Organizations (vs Ensemble Methods)

### Problem

Ensemble methods average models with **aligned objectives**. But some
questions deserve **genuinely independent perspectives with different
stakes**: a startup society, an investor society, and a consumer society
will look at the same product from different standpoints and may
**disagree**. Averaging them loses the disagreement; the disagreement is
the signal.

### State of the Art

- **Bagging / boosting** — ensemble of same-objective learners.
- **Mixture-of-Experts** — learned routing among specialists.
- **LLM-Debate** — multiple models, same objective, no persistent
  institutional framing.

None of these model **organizations with different constitutions and
stakes** debating each other.

### Novel Approach

Veliora introduces **Multiple Independent Organizations**: three
organizations investigate independently — Startup Society says YES,
Investor Society says MAYBE, Consumer Society says NO — and a **Meta
Society** synthesizes the final recommendation. See
`04-technical-architecture.md` for the multi-organization topology.

```
                ┌──────────────────────────┐
                │   Meta Society            │
                │   (synthesizes, does not  │
                │    average)               │
                └─────────┬────────────────┘
                          │
        ┌─────────────────┼─────────────────┐
        │                 │                 │
        ▼                 ▼                 ▼
  Startup Society   Investor Society   Consumer Society
  DNA: high risk    DNA: low risk      DNA: user-first
  Constitution:     Constitution:     Constitution:
  "ship fast"       "protect capital"  "no dark patterns"
```

Each society has its **own DNA, constitution, memory, and trust
weights**. They are not three samples of the same model; they are three
**different organizations**. The Meta Society does not average their
votes; it reads their reports, identifies the points of agreement and
disagreement, and produces a synthesis that preserves the dissenting
opinions in the final output.

### Academic Significance

This is **stakeholder-structured multi-organization reasoning**, a
generalization of ensemble methods to the case where the ensemble
members have **different objectives and constitutions**. The research
hypothesis: preserving disagreement across differently-constituted
organizations yields more robust decisions than averaging aligned
models, because the disagreement surfaces stakeholder-specific risks
that averaging smooths over.

### One Agent Test

**Fails.** A single agent cannot be three organizations with three
constitutions and three stakeholder positions.

---

## C11 — Constitutional Organizations (vs System Prompts)

### Problem

System prompts are **strings** that steer a single model. They are
ephemeral, unversioned, and have no governance: the model cannot refuse
a system prompt, cannot amend it, cannot appeal to it. Real
organizations have **constitutions**: foundational rules that constrain
every decision, that can be amended only by formal vote, and that the
organization itself can invoke in disputes.

### State of the Art

- **System prompts** — string-level steering, single-agent, no
  governance.
- **Constitutional AI** (Anthropic) — a single model critiques its own
  outputs against a set of principles; no voting, no amendment, no
  organization.
- **Guardrails / policies** — hard-coded rules enforced externally, not
  by the organization.

### Novel Approach

Veliora introduces **Constitutional Organizations**: every
organization has a constitution — Mission, Principles, Core Values,
Ethics, Decision Rules — that **constrains every decision** and can be
**amended by formal vote**. See `09-constitution-engine.md`.

```
  Constitution of the Startup Society
  ─────────────────────────────────────
  Mission:        Build and ship products users love.
  Principles:     • Ship fast, learn fast.
                  • Never ship known-broken.
                  • Evidence before opinion.
  Core Values:    Speed, Honesty, User-obsession.
  Ethics:         No dark patterns. No deceptive data.
  Decision Rules:
    - Any spend > $50k requires CEO + Finance sign-off.
    - Any external communication requires Legal review.
    - Any constitutional amendment requires 2/3 board vote.
```

The constitution is **binding**: a department's proposal that violates
the constitution is **rejected by the Constitution Engine**, not by a
prompt. Amendments require a formal motion, a debate, and a supermajority
vote — the constitution is **self-governed**, not author-governed.

### Academic Significance

This lifts constitutional governance from **single-model critique**
(Constitutional AI) to **organizational self-governance**. The research
question: can an LLM organization **stably self-govern** through a
written constitution that it enforces on itself and amends by vote,
without drifting into self-serving interpretation? This connects to
mechanism design and institutional economics.

### One Agent Test

**Fails.** A single agent cannot have a constitution that constrains
peers it does not have, cannot hold a vote, and cannot be bound by a
supermajority of itself.

---

## C12 — Organization Evolution (vs Manual Orchestration)

### Problem

Multi-agent systems are **orchestrated by humans**: a developer writes
the workflow, decides which agents exist, defines their connections,
and updates them when the environment changes. The system does not
restructure itself; the human does. This caps the system's autonomy at
the granularity of the human's willingness to rewrite the graph.

### State of the Art

- **LangGraph** — graph is compiled; changes require redeploy.
- **CrewAI** — crew is defined in code; changes require code edit.
- **AutoGen** — conversations are scripted; changes require script
  edit.
- **OpenAI Swarm** — routines are authored; changes require authoring.

All of these require **human-in-the-loop restructuring**. None of them
hire, fire, promote, create, split, or merge departments on their own.

### Novel Approach

Veliora introduces **Organization Evolution**: the society can
**hire, fire, promote, create departments, split departments, merge
departments, and rewrite policies** — without user intervention. See
`07-evolution-engine.md`.

```
  trigger: Engineering overloaded (queue depth > threshold)
       │
       ▼
  Evolution Engine proposes motion:
    "Hire a second Engineer (backend specialization)."
       │
       ▼
  Board debate:
    Finance: "Cost?"
    CEO:     "We are blocking on Engineering. Approve."
    Legal:   "Role spec compliant with constitution."
       │
       ▼
  Vote (weighted by trust):
    Approve 4.1 / Reject 1.3 → motion carries
       │
       ▼
  Evolution Engine:
    • creates new agent with Engineering mandate
    • inherits Engineering DNA with backend specialization
    • initializes reputation at 0.5 (neutral)
    • logs to Institutional Memory
```

Evolution is **governed**: every structural change is a motion that goes
through debate and vote (C11), not a unilateral mutation. This prevents
runaway self-modification while preserving autonomy.

### Academic Significance

This is **autonomous structural self-modification** under
constitutional governance. The research question: can an LLM
organization **improve its own structure** over time through governed
motions, and does the resulting structure outperform the
human-authored initial structure on long-horizon tasks? This is the
organizational analog of learned-to-learn meta-learning, applied to
**graph structure** rather than weights.

### One Agent Test

**Fails.** A single agent cannot hire a peer, split itself into two
departments, or hold a board vote on its own restructuring. Evolution is
inherently organizational.

---

## 13. Consolidated Comparison Table

```
+------+---------------------------+----------------------------+-----------------------------+----------------------+
| #    | Veliora contribution    | Prior work                 | Unit of intelligence        | One Agent Test       |
+------+---------------------------+----------------------------+-----------------------------+----------------------+
| C1   | Society-of-Thought        | CoT, ToT, GoT, LLM-Debate  | specialized departments     | FAILS → social       |
| C2   | Living Organizations      | static agent graphs        | self-modifying topology     | FAILS → emergent     |
| C3   | Organizational DNA        | hyperparameter tuning      | cultural parameter vector   | FAILS → cultural      |
| C4   | Institutional Memory      | RAG, chat history, MemGPT | shared causal store         | FAILS → collective   |
| C5   | Internal Politics         | cooperative agents         | conflicting mandates        | FAILS → adversarial  |
| C6   | Live Investigation        | streaming, spinners       | parallel department work    | marginal → process   |
| C7   | Evidence Challenge        | self-consistency           | cross-specialization review | FAILS → peer review   |
| C8   | Weighted Trust            | uniform vote, MoE          | outcome-calibrated rep      | FAILS → reputation   |
| C9   | External Intelligence     | web search, browser agents | human-in-loop outreach      | FAILS → outreach     |
| C10  | Multiple Organizations    | ensemble methods           | stakeholder-structured soc. | FAILS → societies    |
| C11  | Constitutional Orgs       | system prompts, ConstAI    | self-governed constitution  | FAILS → governance   |
| C12  | Organization Evolution    | manual orchestration       | governed self-modification  | FAILS → self-modify  |
+------+---------------------------+----------------------------+-----------------------------+----------------------+
```

### Prior-work mapping (extended)

| Veliora | Closest prior work | What is the same | What is fundamentally new |
|------------|-------------------|------------------|--------------------------|
| C1 Society-of-Thought | LLM-Debate (Du 2023) | Multiple LLMs argue | Specialized departments, persistent memory, weighted vote |
| C2 Living Organizations | Neural Architecture Search | Structure is learned | Discrete org graph, online, outcome-driven |
| C3 Organizational DNA | Hyperparameter tuning | Behavior is parameterized | Cultural, named, composable across decisions |
| C4 Institutional Memory | MemGPT, A-MEM | Persistent agent memory | Shared, causal, organizational, typed |
| C5 Internal Politics | LLM-Debate | Adversarial argumentation | Persistent conflicting mandates, scarce resources |
| C6 Live Investigation | Agent trace logs | Process is observable | User-facing, parallel, per-department, real-time |
| C7 Evidence Challenge | Self-consistency | Multiple passes improve answers | Cross-specialization critique, not iid sampling |
| C8 Weighted Trust | Mixture-of-Experts | Aggregation is weighted | Interpretable, outcome-calibrated reputation |
| C9 External Intelligence | Tool-using agents | Agents call external tools | Bidirectional human conversation via Caspian |
| C10 Multiple Organizations | Ensemble methods | Multiple models combine | Different constitutions, stakes, disagreement preserved |
| C11 Constitutional Orgs | Constitutional AI | Principles constrain outputs | Organizational, votable, amendable, binding |
| C12 Organization Evolution | Meta-learning | System improves itself | Governed structural mutation of org graph |

---

## 14. Research Agenda

The twelve contributions define a research program. We list the
open questions and the validation experiments each contribution
suggests, ordered by dependency (earlier contributions enable later
ones).

### Phase 1 — Foundational (MVP-adjacent)

- **C1 Society-of-Thought** — Does specialization + weighted voting
  outperform same-model LLM-Debate on decision benchmarks? *Experiment*:
  run the same multi-hop QA set under CoT, ToT, LLM-Debate, and SoT;
  measure accuracy and audit cost.
- **C7 Evidence Challenge** — Does cross-specialization critique
  reduce systematic bias relative to self-consistency? *Experiment*:
  inject biased evidence and measure whether each method corrects.
- **C8 Weighted Trust** — How quickly do reputation scores become
  informative, and does reputation-weighted voting outperform uniform
  voting as the organization accumulates history?

### Phase 2 — Structural

- **C3 Organizational DNA** — Do DNA-tuned organizations produce
  measurably different decision distributions on a fixed benchmark?
- **C4 Institutional Memory** — Does the organization compound
  knowledge across investigations without retraining? Measure
  decision quality as a function of institutional history length.
- **C5 Internal Politics** — Do decisions produced under genuine
  mandate conflict outperform cooperative-agent decisions on
  robustness (sensitivity to perturbed inputs)?

### Phase 3 — Autonomous

- **C2 Living Organizations** — Does self-modified structure
  outperform the human-authored initial structure on long-horizon
  tasks? Compare against a frozen-topology control.
- **C12 Organization Evolution** — Does governed self-modification
  stay stable over long horizons, or drift into self-serving
  restructuring? Measure constitution-drift and trust-collapse.
- **C11 Constitutional Orgs** — Can an organization enforce its own
  constitution without drifting into self-serving interpretation?

### Phase 4 — Multi-Organization & External

- **C10 Multiple Organizations** — Does preserving disagreement
  across differently-constituted organizations yield more robust
  decisions than averaging aligned models?
- **C9 External Intelligence** — Does active human consultation
  improve decision quality over document-only retrieval, and at
  what latency cost?
- **C6 Live Investigation** — Does user-facing live observability
  change user trust and calibration relative to spinner-style
  interfaces?

### Cross-cutting validation

Every contribution should be re-evaluated against the **One Agent
Test** at each milestone. If a contribution can be re-expressed as a
single-agent feature without loss, it has been mis-scoped and should
be redesigned or dropped. The organization is the intelligence; the
contributions exist because of that, not in spite of it.

---

> **Next**: `03-competitive-analysis.md` compares Veliora against
> existing agent orchestration frameworks and standalone AI products,
> and articulates the fundamental distinction: competitors coordinate
> agents to complete tasks; Veliora creates organizations that
> investigate, debate, and decide.
