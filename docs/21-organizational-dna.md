# 21 — Organizational DNA Model

> **Veliora is not a multi-agent platform. It is an autonomous organization.**
> This document specifies the Organizational DNA parameter system — the genome
> that gives each organization its distinct personality and shapes how its
> workers behave, decide, communicate, hire, and debate.
>
> **Cross-references:** `04-technical-architecture.md`, `05-agent-architecture.md`,
> `06-memory-system.md`, `07-evolution-engine.md`, `08-trust-engine.md`,
> `09-constitution-engine.md`, `10-debate-engine.md`, `12-caspian-integration.md`,
> `17-prompt-architecture.md`, `18-planner-design.md`, `19-investigation-engine.md`,
> `20-meeting-engine.md`. (Engine-by-engine DNA translation is tabulated in §9.)

---

## 1. Overview

Every Veliora organization carries a **DNA vector** — nine integer
parameters in `[0, 100]` that collectively define the organization's
temperament. Two organizations given the identical prompt produce materially
different investigations: a Startup Society ships a fast, opinionated
recommendation; a Hospital Society ships a slow, heavily cited,
consensus-signed recommendation; a Research Lab Society ships a nuanced,
long-horizon, dissent-rich report.

DNA is **not** a per-agent personality. A single LLM has no use for Hierarchy
(no one to be above), Transparency (no internal audience), or a debate style
(no one to debate). DNA is a **coordination genome** — it only has meaning
across many workers. This is the core reason DNA exists in Veliora and not
in a single-agent system (see §2).

```
                         ┌─────────────────────────────────────────┐
                         │            Organizational DNA            │
                         │  Innovation · Risk · Hierarchy · Accuracy │
                         │  Speed · Transparency · Creativity       │
                         │  Long-Term · Security                     │
                         └────────────────────┬────────────────────┘
              ┌───────────────┬───────────────┼───────────────┬───────────────┐
              ▼               ▼               ▼               ▼               ▼
         Prompts(17)     Planner(18)     Debate(10)     Meeting(20)   Evolution(07)
              │               │               │               │               │
              └───────────────┴───────┬───────┴───────────────┴───────────────┘
                                      ▼   Outcomes → Memory(06) → DNA Drift (§6)
```

DNA flows **downward** into every engine and is shaped **upward** by the
outcomes those engines produce. The genome both drives and is driven by the
organization's lived experience.

---

## 2. The One Agent Test

> *Every feature must only exist because this is an organization, not a single
> AI. If a feature would still make sense with one agent, rethink it.*

DNA passes the One Agent Test decisively. Three parameters are **meaningless
for a single agent**, and the remaining six only become *coordination*
parameters (not mere personality) when many workers must align:

| Parameter | Single-agent meaning? | Why it is organizational |
|-----------|----------------------|-------------------------|
| Hierarchy | None — no one to be above/below | Shapes authority, escalation, deference across workers |
| Transparency | None — no internal audience | Controls cross-department visibility of reasoning |
| (debate style) | None — no one to debate | Sets rules of multi-party argumentation |
| Innovation | Personality only | Becomes an exploration/exploitation policy the org enforces collectively |
| Risk Tolerance | Personality only | Becomes a shared evidence threshold and approval policy |
| Accuracy | Personality only | Becomes a mandatory peer-review and citation regime |
| Speed | Personality only | Becomes a shared meeting/vote cadence and MVP policy |
| Creativity | Personality only | Becomes a shared brainstorming and option-generation norm |
| Long-Term Thinking | Personality only | Becomes a shared time-horizon for roadmaps and hiring |
| Security | Personality only | Becomes a shared external-comms and data-exposure policy |

A single agent could carry numbers labeled "Innovation = 80," but they would
be inert — they would only rephrase one model's system prompt. In Veliora
the same numbers become **coordination laws**: they decide whether Engineering
must wait for Research's peer review, whether the CEO can override a
department vote, whether a worker is hired, whether a debate is time-boxed,
and whether an external message leaves the organization. **DNA exists because
the organization, not the agent, is the intelligence.**

---

## 3. DNA Parameter Model

The DNA vector is an ordered record of nine independent integers (no enforced
correlations, though presets and drift create empirical patterns):

```
DNA := { innovation, risk_tolerance, hierarchy, accuracy, speed,
         transparency, creativity, long_term_thinking, security }  // each uint8 [0..100]
```

| Parameter | Low (0–33) | Mid (34–66) | High (67–100) |
|-----------|-----------|-------------|---------------|
| Innovation | Exploit proven solutions | Balanced | Pursue novel approaches |
| Risk Tolerance | Act only on strong evidence | Calibrated | Act under uncertainty |
| Hierarchy | Flat, self-organizing | Mixed | Top-down authority |
| Accuracy | Estimate, approximate | Verify key claims | Validate everything |
| Speed | Deliberate, thorough | Steady | Ship fast, iterate |
| Transparency | Need-to-know | Selective | Open by default |
| Creativity | In-domain, literal | Conventional | Lateral, cross-domain |
| Long-Term Thinking | This quarter | Multi-quarter | Multi-year horizons |
| Security | Open external comms | Vetting | Locked-down, red-teamed |

Each parameter is specified along six effect axes in §4: what it *measures*,
and how it affects *worker behavior*, *decision-making*, *communication*,
*hiring/evolution*, and *debate style*. Cross-references to the engine each
axis touches are consolidated in §9.

---

## 4. Parameter Specifications

### 4.1 Innovation (0–100)

**Measures.** Position on the exploration–exploitation tradeoff: willingness
to pursue unproven, high-variance approaches versus reusing validated patterns.

- **Worker behavior.** High: workers propose novel approaches and are rewarded for original contributions even when they fail. Low: workers retrieve and reuse prior solutions from memory and are penalized for reinventing wheels.
- **Decision-making.** High: the Planner widens the option space and tolerates speculative branches. Low: the Planner prunes to proven options and ranks by precedent.
- **Communication.** High: workers share half-formed ideas and broadcast "what if we tried X" on open channels. Low: workers communicate only validated findings.
- **Hiring & evolution.** High: bias toward generalists/experimenters and R&D departments. Low: hires specialists with proven track records in the exact domain.
- **Debate style.** High: devil's-advocate reframing ("are we solving the right problem?"). Low: precedent-based ("this worked at company Y in 2023").

### 4.2 Risk Tolerance (0–100)

**Measures.** Acceptable variance in outcomes; willingness to act under
uncertainty rather than wait for more evidence.

- **Worker behavior.** High: workers act on incomplete information and are not penalized for fast failures. Low: workers demand more evidence and escalate when evidence is thin.
- **Decision-making.** High: lowers the evidence threshold for approval and accepts larger downside tails. Low: requires multiple sign-offs, worst-case analysis, and a red-team pass before any irreversible action.
- **Communication.** High: terse, action-oriented ("proceed, monitor"). Low: hedged messages with explicit confidence intervals and rollback plans.
- **Hiring & evolution.** High: hires fast, fires fast, promotes bold bets that paid off. Low: hires slowly, retains long, promotes on consistency.
- **Debate style.** High: debates focus on upside scenarios and option value. Low: debates dominated by failure-mode enumeration; the red-team role is weighted heavily.

### 4.3 Hierarchy (0–100)

**Measures.** Centralization of authority; deference owed to senior workers
and department heads.

- **Worker behavior.** High: workers wait for direction, escalate upward, rarely self-initiate cross-department work. Low: workers self-organize and form ad-hoc working groups without approval.
- **Decision-making.** High: routes decisions to the CEO or department heads; the Planner emits a top-down task tree. Low: distributes decisions to whoever has the highest Trust score on the topic; votes for contested calls.
- **Communication.** High: formal channels and status reports up the chain; cross-level messages require introduction. Low: flat and peer-to-peer; any worker may message any other.
- **Hiring & evolution.** High: grows vertically — promotions create layers, span-of-control limits trigger middle-management roles. Low: grows horizontally — few titles, wide spans.
- **Debate style.** High: senior workers' arguments weighted more heavily in vote aggregation. Low: equal voice; decisions purely on argument quality as scored by the Trust Engine.

### 4.4 Accuracy (0–100)

**Measures.** Tolerance for error; demand for correctness and verifiability.

- **Worker behavior.** High: workers double-check, cite sources, run verification passes before publishing. Low: workers estimate and approximate to keep momentum.
- **Decision-making.** High: peer review mandatory; validated evidence (not just plausibility) required for any claim that changes a recommendation. Low: decides on best-available information and revises later.
- **Communication.** High: messages include confidence intervals, citations, caveats ("p > 0.05, n = 412"). Low: direct assertions.
- **Hiring & evolution.** High: creates QA / verification departments; hires reviewers, fact-checkers, red-teamers. Low: hires generalists and front-line doers.
- **Debate style.** High: debates center on evidence quality, methodology, sample size — the Evidence Challenge runs aggressively. Low: debates center on intuition and narrative coherence.

### 4.5 Speed (0–100)

**Measures.** Preference for fast iteration versus thorough deliberation.

- **Worker behavior.** High: workers parallelize, ship MVPs, iterate. Low: workers sequence carefully and polish before publishing.
- **Decision-making.** High: shortens debates, uses majority vote, prefers reversible decisions. Low: extends debates, requires supermajority or consensus, treats all decisions as if irreversible.
- **Communication.** High: short async messages, fewer meetings. Low: long meetings and written records with full rationale.
- **Hiring & evolution.** High: hires "doers," grows execution departments (Engineering, Operations). Low: hires "thinkers," grows planning departments (Strategy, Research).
- **Debate style.** High: time-boxes every debate, falls back to majority vote when the timer expires. Low: open-ended debates, closes only at consensus or supermajority.

### 4.6 Transparency (0–100)

**Measures.** How much internal state — reasoning, work-in-progress, votes,
hiring rationale — is visible across departments and to the user.

- **Worker behavior.** High: workers publish reasoning and share work-in-progress openly. Low: workers share only finished conclusions.
- **Decision-making.** High: every decision logged with rationale; votes public. Low: only the outcome recorded; votes secret, rationale private to the decider.
- **Communication.** High: all internal channels readable by all departments (write-restricted, read-open). Low: need-to-know channels; cross-department reads require authorization.
- **Hiring & evolution.** High: hiring criteria public; every promotion and firing explained. Low: closed hiring; only outcomes announced.
- **Debate style.** High: every debate visible to every worker. Low: debates confined to private committees; only the resolved position published.

### 4.7 Creativity (0–100)

**Measures.** Willingness to generate and entertain unconventional,
cross-domain ideas.

- **Worker behavior.** High: workers brainstorm, propose analogies from unrelated fields, reframe problems. Low: workers stay strictly in-domain and follow established mental models.
- **Decision-making.** High: expands the option space laterally (adjacent disciplines, historical analogues). Low: narrows to incremental, in-domain options.
- **Communication.** High: metaphor-rich, exploratory messages. Low: literal, structured messages.
- **Hiring & evolution.** High: hires polymaths and cross-disciplinary workers. Low: hires deep domain experts.
- **Debate style.** High: analogies and problem reframing accepted as valid argument forms. Low: only precedent and formal logic admitted.

### 4.8 Long-Term Thinking (0–100)

**Measures.** The time horizon the organization considers when deciding.

- **Worker behavior.** High: workers consider multi-year implications and second-order effects. Low: workers optimize for the current quarter or sprint.
- **Decision-making.** High: invests in infrastructure, R&D, worker training even at the cost of short-term output. Low: ships fast, defers maintenance and debt.
- **Communication.** High: messages reference strategy, roadmap, compounding effects. Low: messages reference status, blockers, this-week deliverables.
- **Hiring & evolution.** High: hires for potential, mentors junior workers; promotions reward compounding skill. Low: hires for immediate output; promotions reward this-quarter results.
- **Debate style.** High: debates weight future scenarios and optionality. Low: debates weight immediate cost/benefit and payback period.

### 4.9 Security (0–100)

**Measures.** Caution around external communication, data exposure, and
trust of outside parties (people, other organizations, external sources).

- **Worker behavior.** High: workers verify external sources, redact sensitive information, treat outside parties as untrusted by default. Low: workers share freely externally and assume good faith.
- **Decision-making.** High: threat modeling and a Legal/Compliance review required before any external-facing action. Low: ships external actions without review.
- **Communication.** High: encrypts, logs, restricts external messages to need-to-know; all Caspian channel use audited. Low: broadcasts openly.
- **Hiring & evolution.** High: creates Compliance, Security, Legal departments; background checks on external contacts. Low: hires for output; external outreach is routine.
- **Debate style.** High: a red-team / adversarial role always argues the malicious-actor case. Low: assumes good faith, skips the adversarial role.

---

## 5. DNA Presets by Organization Type

Presets are the **founding temperament** — the DNA vector an organization
starts with when the user creates it. DNA then drifts (§6) based on
experience, so a mature organization's DNA may differ from its preset, but
the preset remains the regression anchor.

| Parameter | Startup | Hospital | Research Lab | Investment Firm | Law Firm | Game Studio | University |
|-----------|:------:|:--------:|:------------:|:---------------:|:--------:|:-----------:|:----------:|
| Innovation | **85** | 30 | **90** | 50 | 20 | 80 | 70 |
| Risk Tolerance | **80** | **15** | 60 | 70 | 25 | 75 | 40 |
| Hierarchy | **25** | **80** | 40 | 60 | **85** | 35 | 50 |
| Accuracy | 50 | **95** | **90** | 80 | **95** | 60 | 85 |
| Speed | **90** | 70 | **40** | 75 | 50 | 80 | 35 |
| Transparency | 75 | 60 | 85 | 50 | 40 | 80 | 80 |
| Creativity | 80 | 40 | 75 | 45 | 30 | **90** | 75 |
| Long-Term Thinking | 40 | 70 | **85** | 65 | 70 | 60 | **90** |
| Security | 40 | **90** | 50 | 85 | **90** | 50 | 60 |

**Preset rationales.**

- **Startup** — High Innovation, high Risk, low Hierarchy, high Speed: ship fast, try novel things, flat structure, accept failure. Matches the SPEC's flagship example.
- **Hospital** — Low Risk, high Accuracy, high Hierarchy, high Security: patient safety dominates; top-down (attending → resident), evidence-validated, externally locked-down. Errors unacceptable.
- **Research Lab** — High Accuracy, high Long-Term, low Speed, high Transparency: pursue multi-year questions, validate rigorously, publish openly, do not rush. Matches the SPEC's stated profile.
- **Investment Firm** — Moderate Risk, high Accuracy, high Security, mid Hierarchy: calibrated risk-taking with strict controls, audited external comms, portfolio-style long-term thinking.
- **Law Firm** — Low Innovation, low Risk, high Hierarchy, high Accuracy, high Security: precedent-bound, risk-averse, top-down (partner → associate), citation-obsessed, privilege enforced.
- **Game Studio** — High Creativity, high Innovation, high Speed, low Hierarchy: creative-first, ship-playtest-iterate, flat teams of generalists.
- **University** — High Long-Term Thinking, high Transparency, high Accuracy, low Speed: multi-decade horizons, open scholarship, rigorous peer review, deliberate pace, mid Hierarchy.

---

## 6. DNA Drift Model

DNA is not static. The organization's lived experience reshapes its genome
over time — the mechanism behind "Living Organizations" and "Organization
Evolution" (SPEC §2, §12). A Startup that repeatedly fails on high-Risk bets
drifts toward lower Risk; a Research Lab whose speculative branch produces a
breakthrough drifts toward higher Innovation.

### 6.1 Drift Principles

1. **Slow.** DNA moves at most `±DRIFT_MAX` per epoch (default `±2`); one epoch = one completed investigation or `N` significant events.
2. **Evidence-based.** Every drift is triggered by a recorded outcome in `06-memory-system.md`; no random walk.
3. **Bounded by constitution.** DNA cannot cross amendment-set floors/ceilings without a constitutional vote (§8).
4. **Regresses toward preset.** A small pull toward the founding preset keeps the organization recognizable as its type.
5. **Observable.** Every drift event is logged and visible to the user and to workers above a Transparency threshold.

### 6.2 Drift Triggers

| Event | Parameter(s) | Direction | Magnitude |
|------|--------------|-----------|----------|
| High-Risk decision succeeds | Risk Tolerance | ↑ | +1 to +2 |
| High-Risk decision fails (costly) | Risk Tolerance | ↓ | −1 to −3 |
| Speculative branch yields breakthrough | Innovation | ↑ | +1 to +2 |
| Speculative branch yields nothing | Innovation | ↓ | −1 |
| Speed-first release causes rework | Speed ↓, Accuracy ↑ | ±1 | — |
| Review catches critical error | Accuracy | ↑ | +1 to +2 |
| Hierarchy bottleneck delays decision | Hierarchy | ↓ | −1 |
| Flat structure causes duplicated work | Hierarchy | ↑ | +1 |
| External leak via Caspian channel | Security ↑, Transparency ↓ | ±2 | — |
| Open debate surfaces hidden conflict | Transparency | ↑ | +1 |
| Long-term investment compounds | Long-Term Thinking | ↑ | +1 |
| Long-term investment starves short-term | Long-Term Thinking | ↓ | −1 |

### 6.3 Drift Algorithm

```pseudo
# Per epoch (e.g., end of a completed investigation)
function drift_epoch(org, events):
  for param in DNA_PARAMETERS:
    delta = 0
    for event in events:
      if event.affects(param):
        delta += event.sign(param) * event.magnitude * event.weight
    preset = org.founding_dna[param]                       # regression anchor
    reg = sign(preset - org.dna[param]) * min(REGRESSION_RATE, abs(preset - org.dna[param]))
    delta += reg * REGRESSION_WEIGHT                       # default 0.5
    delta = clamp(delta, -DRIFT_MAX, +DRIFT_MAX)           # DRIFT_MAX = 2
    org.dna[param] = clamp(org.dna[param] + delta,
                           org.constitution.floor[param],
                           org.constitution.ceiling[param])
    log_drift(org, param, delta, events)                   # to Memory (06)
```

`event.weight` scales with outcome magnitude — a failed high-Risk bet that
cost the org a quarter of its trust budget weighs more than a minor missed
estimate. `REGRESSION_WEIGHT < 1` ensures experience dominates founding
temperament over time, while still pulling the org back if drift overshoots.

---

## 7. DNA Inheritance on Organizational Split

Organizations can split — a Startup spins off a Research Lab; Engineering
grows into its own Game Studio; a University spins out an Investment Firm to
manage its endowment. The split is performed by `07-evolution-engine.md` and
produces a **child organization** whose DNA is inherited, not sampled from
scratch.

### 7.1 Inheritance Principles

1. **Child resembles parent.** The child starts from the parent's current (drifted) DNA, not a global default.
2. **Mission reshapes.** The child's charter applies a pull toward the mission-appropriate preset.
3. **Mutation introduces variation.** Small noise prevents two splits from the same parent from being identical.
4. **Constitution locks floors.** The child's constitution may lock parameters above a floor (e.g., a Hospital spin-off locks `Security ≥ 70`).

### 7.2 Inheritance Algorithm

```pseudo
function inherit_dna(parent, charter, constitution):
  child_dna = {}
  for param in DNA_PARAMETERS:
    inherited = parent.dna[param]    * INHERITANCE_WEIGHT   # 0.4
    mission   = charter.preset[param] * MISSION_WEIGHT      # 0.6
    mutation  = rand_in(-MUTATION_AMP, +MUTATION_AMP)         # ±5
    child_dna[param] = clamp(inherited + mission + mutation,
                             constitution.floor[param], constitution.ceiling[param])
  return child_dna
```

`INHERITANCE_WEIGHT + MISSION_WEIGHT = 1`. The default split (0.4 / 0.6)
favors the new mission over the parent's temperament, so a Startup spinning
off a Research Lab ends up more Research-Lab-like than Startup-like — but
still carries traces of the parent (e.g., a slightly higher Speed than a
fresh Research Lab would have).

```
Parent (Startup, drifted)        Child (Research Lab charter)
  Innovation  78   ─┐              Innovation  86
  Risk        65    │  inherit 0.4  Risk        55
  Hierarchy   30    ├────────────►  Hierarchy   38
  Accuracy    58    │  mission 0.6  Accuracy    84
  Speed       85   ─┘              Speed       52   ← lower than parent,
                                                 higher than fresh preset
```

### 7.3 Why Inheritance Is Organizational

A single agent does not split. Inheritance only exists because the
organization can divide into sub-organizations, each carrying a genome shaped
by both its parent and its new mission. This is the mechanism by which a
Veliora "family tree" of organizations retains lineage — see
`07-evolution-engine.md` for the split/merge topology.

---

## 8. Constitutional Bounds and Amendment

DNA is not unconstrained. Every organization's constitution
(`09-constitution-engine.md`) may declare **floors** and **ceilings** on any
DNA parameter — values the organization has committed to and cannot drift
across.

```jsonc
// Example: Hospital constitution bounds
{
  "accuracy":       { "floor": 80, "ceiling": 100 },
  "risk_tolerance": { "floor": 0,  "ceiling": 30  },
  "security":       { "floor": 70, "ceiling": 100 },
  "hierarchy":       { "floor": 60, "ceiling": 100 }
}
```

- **Drift respects bounds.** §6.3 clamps drift to `[floor, ceiling]`.
- **Crossing a bound requires amendment.** Moving a parameter outside its constitutional range requires a constitutional amendment — a supermajority vote per `09-constitution-engine.md` and `20-meeting-engine.md`.
- **Founder locks.** The user who creates the organization may set immutable bounds (e.g., a Hospital founder locks `Risk ≤ 30` forever) that even amendments cannot cross without user consent.

---

## 9. DNA Injection into Engines

DNA is not a passive label. It is **injected into every engine** as
operational parameters; each engine translates DNA into concrete behavior.

| Engine | DNA translation |
|--------|----------------|
| Prompt Architecture (`17`) | DNA becomes a "personality preamble" in every worker's system prompt; debate-style and comms norms encoded as instructions |
| Planner (`18`) | Innovation/Creativity widen the option space; Risk/Speed set selection thresholds; Long-Term sets the planning horizon |
| Debate Engine (`10`) | Hierarchy weights senior arguments; Accuracy demands citations; Creativity admits analogies; Security mandates a red-team role |
| Meeting Engine (`20`) | Speed sets time-boxes and vote rule (majority vs. supermajority); Hierarchy sets who chairs; Transparency sets attendance |
| Investigation Engine (`19`) | Accuracy sets evidence thresholds; Risk sets the bar to ship a recommendation; Security sets external-source vetting |
| Trust Engine (`08`) | DNA does not override Trust but sets the prior weight of seniority (Hierarchy) vs. merit |
| Evolution Engine (`07`) | Innovation/Creativity bias hiring toward generalists; Accuracy/Security create review departments; Speed grows execution roles |
| Constitution Engine (`09`) | Defines floors/ceilings; amendments are the only way to cross them |
| Memory (`06`) | Records every drift event and its trigger, so the org remembers *why* its DNA changed |
| Caspian (`12`) | Security gates external channel use; Transparency logs external messages; Risk sets outreach aggressiveness |

---

## 10. Data Model

```jsonc
// /orgs/{org_id}/dna.json — persisted DNA record
{
  "org_id": "org_01HXYZ...",
  "founding_preset": "startup",
  "founding_dna": {                    // immutable, the regression anchor
    "innovation": 85, "risk_tolerance": 80, "hierarchy": 25,
    "accuracy": 50, "speed": 90, "transparency": 75,
    "creativity": 80, "long_term_thinking": 40, "security": 40
  },
  "current_dna": {                     // live, drifts over time
    "innovation": 78, "risk_tolerance": 65, "hierarchy": 30,
    "accuracy": 58, "speed": 85, "transparency": 75,
    "creativity": 80, "long_term_thinking": 47, "security": 44
  },
  "constitution_bounds": {
    "accuracy":       { "floor": 0, "ceiling": 100 },
    "risk_tolerance": { "floor": 0, "ceiling": 100 }
  },
  "drift_log": [                       // append-only, mirrored to Memory (06)
    { "epoch": 14, "param": "risk_tolerance", "delta": -2,
      "trigger": "event_ev_01H...", "reason": "high-risk market-entry bet failed" }
  ],
  "lineage": { "parent_org_id": null, "split_charter": null, "split_mutation": null }
}
```

The DNA record is read by every engine at the start of each task and written
only by the drift algorithm at epoch boundaries. The `drift_log` is mirrored
into institutional memory so the organization can later answer "why did we
become more risk-averse last quarter?"

---

## 11. Worked Example

A **Startup Society** (Innovation 85, Risk 80, Hierarchy 25, Accuracy 50,
Speed 90) is asked *"Should we build an AI healthcare startup?"*

1. **Prompt injection (`17`).** Every worker's system prompt receives the DNA preamble: "fast, flat, risk-tolerant startup; prefer novel approaches; act on incomplete information; communicate tersely; debates are time-boxed to majority vote."
2. **Planning (`18`).** The Planner generates a wide option space (high Innovation) including lateral options ("build for veterinary AI first" — high Creativity) and selects a fast, parallel task tree (high Speed).
3. **Investigation (`19`).** Research reads papers; Marketing surveys users via Caspian (`12`); Engineering prototypes. Evidence thresholds are low (low Accuracy, high Risk) — the org ships on best-available evidence.
4. **Debate (`10`).** A time-boxed debate (high Speed) runs with majority vote (low Hierarchy). The red-team role is light (low Security). Devil's-advocate reframing is welcomed (high Innovation).
5. **Outcome and drift (`06`, `07`).** The recommendation is delivered. Six months later, a tracked outcome shows the high-Risk bet paid off → `risk_tolerance` +1; the speed-first prototype caused rework → `speed` −1, `accuracy` +1. The org is now slightly more accurate and slightly less fast than its founding preset.
6. **Split.** The org later spins off a Research Lab. The child inherits the parent's drifted DNA (Innovation 78, Speed 85, …) pulled toward the Research Lab preset (Speed 40, Accuracy 90, …) with ±5 mutation. The child is a Research Lab that still moves faster than a fresh Research Lab would — the parent's lineage shows.

---

## 12. Open Questions

- **Correlated drift.** Should some parameters be coupled (e.g., Accuracy ↑ implies Speed ↓) by default, or always drift independently?
- **DNA and model selection.** Should DNA influence which underlying LLM profile each worker uses (e.g., high-Creativity orgs favor divergent-thinking models)? See `17-prompt-architecture.md`.
- **DNA conflict across a Meta Society.** When multiple independent organizations disagree (SPEC §10), how does the Meta Society reconcile their DNA vectors — averaging, Trust-weighted vote, or adversarial debate? See `19-investigation-engine.md`.
- **User override of drift.** Should the user be able to pin a DNA parameter and freeze drift, and does that count as a constitutional amendment? See `09-constitution-engine.md`.
- **DNA measurement validity.** How do we empirically validate that a given DNA vector produces the intended behavior — what observables correlate with each parameter? See `02-research-contributions.md`.
