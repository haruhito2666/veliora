# 19 — Investigation Engine

> **Veliora doesn't answer questions. It conducts investigations.**

The Investigation Engine is the flagship feature of Veliora — the
orchestration layer that turns a single user prompt into a multi-department,
evidence-driven, debate-tested, peer-reviewed organizational process. This
document specifies the investigation lifecycle, the phase state machine,
the live display protocol, Caspian integration, concurrency, confidence
assessment, the final board report structure, and a complete end-to-end
walkthrough. It is the **orchestration spine** and cross-references the
Planner (`18-planner-design.md`), Debate Engine (`10-debate-engine.md`),
Meeting Engine (`20-meeting-engine.md`), Caspian (`12-caspian-integration.md`),
Trust Engine (`08-trust-engine.md`), Memory (`06-memory-system.md`),
Knowledge Graph (`14-knowledge-graph.md`), Event System (`16-event-system.md`),
and State Machine (`25-state-machine.md`).

---

## 1. Overview

An **investigation** is the atomic unit of organizational work. A user does
not ask Veliora a question and receive an answer. A user **commissions**
an investigation, and the organization delivers a **board report** — the
artifact a real board would receive from a real company that spent real
weeks researching a real question. The intelligence is **not** in any one
department. It emerges from the **interaction** between departments:
Research finding a paper, Engineering challenging its sample size, Marketing
surveying users who disagree, Finance building a contradicting forecast,
Legal flagging a regulation nobody considered, and the CEO synthesizing the
survivors into a recommendation that has already survived peer review.

---

## 2. The "One Agent Test" Applied

Every component below exists **only because this is an organization**.

| Subsystem | Why it requires an organization | Single-agent equivalent (rejected) |
|---|---|---|
| Scoping | Multiple departments must agree the question is answerable and decide who is needed | A single prompt template |
| Parallel Research | Departments investigate different facets simultaneously | Sequential tool calls |
| Evidence Challenge | One department criticizes another's evidence — different objective function | Self-critique by the same model |
| Debate | Conflicting objectives (growth vs savings vs safety) produce real disagreement | Role-played debate by one model |
| Meetings | Synchronous resolution only needed when async debate stalls between parties | Not needed |
| Voting | A collective decision with a tally and a minority record | A single confidence score |
| Dissenting Opinions | A minority that lost the vote but whose argument is preserved | Impossible |
| Confidence Score | Aggregate across departments weighted by historical trust | A single logprob |
| Live Display | Multiple departments working in parallel is genuinely watchable | A "Thinking..." spinner |
| Caspian outreach | Different departments reach different real-world contacts | A single web search |
| Concurrent investigations | The org partitions departments across open questions | Not meaningful |

If a proposed feature passes the One Agent Test — a single agent would still
want it — it does not belong in the Investigation Engine.

---

## 3. Investigation Lifecycle

```
USER SUBMITS GOAL
   │
   ▼
[1] ORG CREATES INVESTIGATION      (CEO opens case, assigns case ID)
[2] PLAN GENERATED                 (Planner decomposes goal → tasks; see 18)
[3] DEPARTMENTS ASSIGNED           (CEO assigns tasks to departments)
[4] PARALLEL INVESTIGATION BEGINS  (RESEARCH phase; all depts work at once)
[5] EVIDENCE COLLECTED             (each dept produces evidence records)
[6] EVIDENCE CHALLENGED           (cross-department critique loop)
[7] DEBATES HELD                   (Society-of-Thought; see 10)
[8] MEETINGS CALLED                (only when async debate stalls; see 20)
[9] VOTES TAKEN                    (org votes on the recommendation)
[10] CONFIDENCE ASSESSED
       ├─ low  ─► return to [4] (continue, with refined scope)
       └─ high ─► [11] FINAL REPORT GENERATED → BOARD REPORT DELIVERED
                    │
                    ▼
                ARCHIVED TO INSTITUTIONAL MEMORY (see 06)
```

The loop at step [10] is **not** a retry. It is the organization deciding,
on its own, that it does not yet know enough to commit. The loop is bounded
by the constitution (`09-constitution-engine.md`) — typically a maximum of
three cycles before the org must either deliver a low-confidence report or
escalate to the user.

---

## 4. Investigation Phases

Six canonical phases. Each has an **entry condition**, a **body of work**,
and an **exit condition**, enforced as guards on the state machine (§5).

### 4.1 Scoping

**Entry:** User goal accepted by the CEO. **Body:** The CEO (and optionally
the constitution's quorum) determines whether the goal is **answerable** by
this organization — a Startup Society can scope "Should I build an AI
healthcare startup?"; a Research Lab cannot. The CEO defines **success
criteria**, selects which departments are **relevant**, and sets a
**confidence threshold** (default from DNA; see `21-organizational-dna.md`),
a **deadline**, and a **priority** (P0/P1/P2). **Exit:** A signed
**Investigation Charter** is written to memory. The charter is immutable
for the life of the investigation; any change requires a new investigation
or a constitutional amendment vote.

```
INVESTIGATION CHARTER (example)
─────────────────────────────────────────────────────────────
case_id:         INV-2026-08-0007
goal:            "Should I build an AI healthcare startup?"
owner:           CEO
departments:     [Research, Engineering, Finance, Marketing, Legal]
success_criteria:
  - market size and growth estimate with cited sources
  - technical feasibility verdict with effort estimate
  - 24-month financial projection with assumptions
  - regulatory risk register (HIPAA, FDA, EU AI Act)
  - go/no-go recommendation with dissent
confidence_threshold: 0.72
deadline:        2026-08-07T16:53:12Z   (48h)
priority:        P1
─────────────────────────────────────────────────────────────
```

### 4.2 Research

**Entry:** Charter signed. **Body:** The Planner (`18-planner-design.md`)
decomposes the charter into a **task graph** — one subtree per department.
Each department begins its subtree **in parallel**; this is the phase the
user watches on the live display (§6). Departments collect **evidence
records** — typed, cited, signed artifacts (a paper, a survey response, a
forecast, a code spike, a regulatory memo). Departments may request
**Caspian outreach** (§7) to gather evidence from real people. Outreach is
async; the engine tracks open requests and their response windows. **Exit:**
Every department has either (a) submitted its evidence records or (b)
declared `INSUFFICIENT_EVIDENCE` with a written justification. The phase
exits on **closure**, not on a timeout.

### 4.3 Analysis

**Entry:** All departments closed Research. **Body:** Each department
processes raw evidence into **findings** — a claim backed by one or more
evidence records, with a departmental confidence score. The Knowledge
Graph (`14-knowledge-graph.md`) is updated: findings become nodes, evidence
becomes edges, contradictions become flagged edges. Cross-department
**evidence challenge** begins: Engineering reads Research's findings and may
file a **challenge** (e.g., "sample size n=12, underpowered"). The
challenged department must **respond**: accept, rebut, or amend. Challenges
and responses are first-class records, visible in the final report. A
finding that survives challenge has a higher trust weight
(`08-trust-engine.md`). **Exit:** Every finding has a challenge status of
`ACCEPTED`, `REBUTTED`, or `AMENDED`. No finding may enter Debate with an
open challenge.

### 4.4 Debate

**Entry:** All findings closed their challenge status. **Body:** The
Debate Engine (`10-debate-engine.md`) runs a **Society-of-Thought** debate.
Departments argue for or against candidate recommendations. The debate is
**not roleplay** — Marketing genuinely argues for growth; Finance for
savings; Legal for safety. Their objective functions conflict by design.
When async debate stalls — N rounds pass with no movement in positions —
the engine may **call a meeting** (`20-meeting-engine.md`) for synchronous
resolution. Meetings are the exception, not the default. The debate produces
a set of **candidate recommendations**, each with a sponsor and an argument
graph. **Exit:** The candidate set is frozen. No new candidates may be
introduced after this point without restarting Debate (which requires a
constitutional quorum vote).

### 4.5 Synthesis

**Entry:** Candidate set frozen. **Body:** The CEO drafts a **synthesis** —
a single recommendation (or a small set of ranked options) that best fits
the surviving evidence, the debate outcome, and the organizational DNA. The
CEO does **not** override the vote; the CEO represents the vote honestly,
including the minority. A draft board report is assembled (§10). **Exit:**
Draft report exists and is routed to the organization for a vote.

### 4.6 Report

**Entry:** Draft report routed. **Body:** The organization **votes** on the
recommendation. Voting is weighted by trust (`08-trust-engine.md`) and by
DNA (a hierarchical org gives the CEO a tie-breaking vote; a flat org does
not). **Confidence is assessed** (§9). If confidence is below the charter
threshold, the investigation loops back to Research with a refined scope
(the CEO writes a memo explaining what is missing). If confidence meets
threshold, the final board report is sealed, signed by every participating
department, and delivered to the user. **Exit:** Report `PUBLISHED`. The
investigation transitions to `ARCHIVED` and is written to institutional
memory (`06-memory-system.md`) as a learnable case for future investigations
and for the Evolution Engine (`07-evolution-engine.md`).

### 4.7 Phase Transition Summary

```
Scoping ──► Research ──► Analysis ──► Debate ──► Synthesis ──► Report
   ▲                                                       │
   └───────────── low confidence loop (max 3 cycles) ───────┘
```

| Transition | Guard |
|---|---|
| Scoping → Research | Charter signed and stored |
| Research → Analysis | All departments closed (submitted or INSUFFICIENT) |
| Analysis → Debate | All findings have closed challenge status |
| Debate → Synthesis | Candidate set frozen, no open debate threads |
| Synthesis → Report | Draft report routed to all participating departments |
| Report → Scoping | Confidence < threshold AND cycle count < max |
| Report → Published | Confidence ≥ threshold OR cycle count = max |
| Published → Archived | Report signed and stored to memory |

---

## 5. Investigation State Machine

The investigation is a finite-state machine. States are persisted (see
`13-database-design.md`) and replayable. Every transition emits an event
(see `16-event-system.md`). The current state is a **fold** over the event
stream, not a mutable column — making investigations replayable for
debugging and for the Evolution Engine (`07-evolution-engine.md`).

### 5.1 State Diagram

```
                    ┌──────────┐
                    │ CREATED  │
                    └────┬─────┘
                         │ charter proposed
                         ▼
                    ┌──────────┐    reject
                    │ SCOPING  │──────────────► FAILED
                    └────┬─────┘
                         │ charter signed
                         ▼
                    ┌──────────┐
                    │ PLANNING │
                    └────┬─────┘
                         │ task graph ready
                         ▼
                    ┌──────────┐
           ┌────────►│ RESEARCH │
           │         └────┬─────┘
           │              │ all depts closed
           │              ▼
           │         ┌──────────┐
           │         │ ANALYSIS │
           │         └────┬─────┘
           │              │ challenges closed
           │              ▼
           │         ┌──────────┐
           │         │  DEBATE  │
           │         └────┬─────┘
           │              │ candidates frozen
           │              ▼
           │         ┌──────────┐
           │         │SYNTHESIS │
           │         └────┬─────┘
           │              │ draft routed
           │              ▼
           │         ┌──────────┐
           │         │  VOTE    │
           │         └────┬─────┘
           │              │ tally certified
           │              ▼
           │         ┌──────────────┐
           │         │CONFIDENCE_CHK│
           │         └──────┬───────┘
           │                │
           │  ┌─────────────┴─────────────┐
           │  ▼                           ▼
           │ conf<thr & cycles<max    conf≥thr OR cycles=max
           │  │                           │
           └──┘                           ▼
                                   ┌──────────┐
                                   │  REPORT  │
                                   └────┬─────┘
                                        │ signed
                                        ▼
                                   ┌──────────┐
                                   │ PUBLISHED│
                                   └────┬─────┘
                                        │ archived
                                        ▼
                                   ┌──────────┐
                                   │ ARCHIVED │
                                   └──────────┘
  CANCELLED may occur from any state via user action.
  FAILED may occur from SCOPING (unanswerable) or unrecoverable errors.
```

### 5.2 Transition Table

| From | Event | To | Guard | Emitted Event |
|---|---|---|---|---|
| CREATED | `charter_proposed` | SCOPING | goal parseable | `investigation.scoping.started` |
| SCOPING | `charter_signed` | PLANNING | charter stored | `investigation.planning.started` |
| SCOPING | `charter_rejected` | FAILED | unanswerable | `investigation.failed` |
| PLANNING | `task_graph_ready` | RESEARCH | graph validated | `investigation.research.started` |
| RESEARCH | `all_departments_closed` | ANALYSIS | closure manifest | `investigation.analysis.started` |
| ANALYSIS | `all_challenges_closed` | DEBATE | no open challenges | `investigation.debate.started` |
| DEBATE | `candidates_frozen` | SYNTHESIS | debate log sealed | `investigation.synthesis.started` |
| SYNTHESIS | `draft_routed` | VOTE | draft exists | `investigation.vote.started` |
| VOTE | `tally_certified` | CONFIDENCE_CHK | quorum met | `investigation.confidence.checked` |
| CONFIDENCE_CHK | `below_threshold` | RESEARCH | cycles < max | `investigation.research.looped` |
| CONFIDENCE_CHK | `meets_threshold` | REPORT | — | `investigation.report.started` |
| REPORT | `report_signed` | PUBLISHED | all depts signed | `investigation.published` |
| PUBLISHED | `archived` | ARCHIVED | stored to memory | `investigation.archived` |
| any | `user_cancelled` | CANCELLED | — | `investigation.cancelled` |

---

## 6. Live Investigation Display

The live display is what makes an investigation feel **alive** — not a
progress bar, but a real-time view of multiple departments working in
parallel, each with its own status, activity feed, and current artifact.

### 6.1 Department Status Model

Each department exposes a status object updated on every event:

```
DepartmentStatus {
  department:     "Research",
  investigation:  "INV-2026-08-0007",
  phase:          "RESEARCH",
  state:          "WORKING",          # WORKING | WAITING | DONE | BLOCKED
  activity:       "Reading 15 papers on HIPAA-compliant ML",
  current_task:   "T-007",
  artifacts:      3,                  # evidence records produced so far
  open_challenges: 0,
  caspian_open:   2,                  # unanswered external messages
  trust:          0.96,               # from Trust Engine
  last_event_at:  2026-08-05T16:51:03Z
}
```

### 6.2 Mobile Dashboard (Live)

```
Veliora
Startup Organization
────────────────────────────────────────
INVESTIGATION  INV-2026-08-0007
"Should I build an AI healthcare startup?"
Phase: RESEARCH          Cycle 1/3
────────────────────────────────────────
CEO          Coordinating        Working
Research     Reading 15 papers  Working   ●●●○○
Engineering  Spiking HIPAA API  Working   ●●○○○
Finance       Building forecast  Working   ●○○○○
Marketing     Surveying users    Waiting   ●●●○○  (2 Caspian pending)
Legal         Reading FDA draft  Working   ●●○○○
────────────────────────────────────────
Evidence      14 records   Challenges  2
Caspian       5 open       Meetings    0
Confidence    pending      Deadline    47h
────────────────────────────────────────
[ Activity Feed ]
16:51  Research   cited paper: "ML in Radiology, 2024"
16:50  Engineering challenged Research.evidence[3] (sample size)
16:49  Marketing  sent Telegram survey to 40 beta users
16:47  Finance    forecast draft v1 started
```

### 6.3 Activity Feed Protocol

The feed is a time-ordered projection of the investigation's event stream
(`16-event-system.md`), filtered to human-readable summaries. Each entry is
a `(timestamp, department, verb, object)` tuple. The UI subscribes via
WebSocket (see `24-api-design.md`) and renders entries as they arrive. The
display is **organizational, not individual** — the user does not see
"Agent #3 is thinking"; the user sees "Research is reading 15 papers." This
is the One Agent Test in the UI: a single agent's spinner is not a live
organization.

---

## 7. Caspian Integration

Caspian (`12-caspian-integration.md`) is the organization's
**communication nervous system** with the real world. During Research (and
sometimes Analysis), departments reach out to real people through Caspian's
channels. Each Caspian outreach is an **async evidence request**:

```
CaspianOutreach {
  outreach_id:   "OUT-2026-08-0007-12",
  investigation: "INV-2026-08-0007",
  department:   "Marketing",
  channel:      "Telegram",
  recipient:    "@beta_user_group",
  message:      "Quick 3-question survey on AI symptom checkers...",
  evidence_type:"survey_response",
  expected_by:  2026-08-06T16:53:12Z,
  status:       "PENDING" | "ANSWERED" | "EXPIRED" | "REFUSED"
}
```

### 7.1 Channel Selection by Department

| Department | Typical channels | Typical recipients |
|---|---|---|
| Research | Email, Telegram | Professors, founders, domain experts |
| Marketing | Telegram, Discord, Slack | Users, beta groups, communities |
| Finance | Email, Slack | Accountants, CFOs, analysts |
| Legal | Email | Lawyers, compliance consultants |
| Engineering | Discord, Slack | Engineers, OSS maintainers |
| Customer Success | Telegram, WhatsApp | Beta testers, customers |

The organization **chooses the channel** based on the recipient and the
department's established relationships in institutional memory
(`06-memory-system.md`). It does not blast every channel.

### 7.2 Blocking vs. Non-Blocking Outreach

- **Blocking** — the finding cannot close without this evidence. Research
  waits up to `expected_by`; if no response, the department files
  `INSUFFICIENT_EVIDENCE` and confidence is penalized.
- **Non-blocking** — nice-to-have. The investigation proceeds; late
  responses fold into Analysis if they arrive before it closes, else they
  log for the next cycle.

Every Caspian message is visible in the live display and in the final
report's "Full Organizational Discussion" section. The organization cannot
silently email a professor; outreach is part of the organizational record.

---

## 8. Concurrent Investigations

A real organization has multiple open matters at once. The Investigation
Engine is a **scheduler**, not a singleton. Each department has a
**capacity** — the number of concurrent tasks it can hold. Capacity emerges
from the Evolution Engine (`07-evolution-engine.md`): an overloaded
department may trigger hiring; an underloaded one may be merged.

```
capacity(dept) = base_slots(dept) + hired_slots(dept) - reserved(dept)
```

Investigations carry a priority (P0/P1/P2) from the charter. The scheduler
uses **priority-weighted fair-share**: P0 investigations may **preempt** P2
tasks (preempted tasks are paused, not dropped; partial evidence is
preserved); P1 investigations share capacity fairly via round-robin within
their tier; P2 investigations run on leftover capacity. When two P0
investigations compete for the same overloaded department, the CEO
arbitrates — and the CEO's decision is itself a recorded event, reviewable
in the final report and learnable by the Evolution Engine. This is a
One-Test-passing feature: a single agent has no one to arbitrate with.

Investigations are **memory-isolated** by default. Department A working on
investigation X cannot read investigation Y's evidence — unless the CEO
issues a cross-investigation reference, which is logged. This prevents
contamination and preserves the independence that makes the "Multiple
Independent Organizations" contribution (`02-research-contributions.md`)
meaningful at the intra-org level.

---

## 9. Confidence Assessment

Confidence is **not** a single model's self-reported probability. It is an
**aggregate, trust-weighted, challenge-aware** organizational measure.

```
                      Σ  trust(dept) · dept_confidence(dept) · challenge_survival(dept)
org_confidence = ────────────────────────────────────────────────────────────────────
                                    Σ  trust(dept)

where:
  trust(dept)              ∈ [0,1]  from Trust Engine (08)
  dept_confidence(dept)    ∈ [0,1]  department's own confidence in its findings
  challenge_survival(dept) ∈ [0,1]  fraction of the dept's findings that
                                   survived cross-department challenge
```

A department whose findings were all rebutted contributes near-zero, even
if it self-reported high confidence. A department with high trust and
uncontested findings dominates the aggregate. The charter's
`confidence_threshold` (default 0.72, tunable by DNA) is the gate. If
`org_confidence < threshold` and `cycles < max_cycles` (default 3), the
investigation loops to Research with a CEO memo specifying what is missing.
If `cycles == max_cycles`, the report is delivered with the achieved
confidence and an explicit "low confidence" caveat in the Executive
Summary. A single agent's "confidence" is a logprob; organizational
confidence is a **negotiated** quantity that depends on trust earned over
prior investigations, on whether findings survived peer challenge, and on
whether dissenting departments were convinced. It is irreducibly social.

---

## 10. The Final Board Report

The board report is the deliverable — structured so a human board member
could read it and reach the same conclusion the organization did.

```
BOARD REPORT — INV-2026-08-0007
══════════════════════════════════════════════════════════════
1.  Executive Summary ............................ (CEO)
2.  Final Recommendation .......................... (vote tally)
3.  Confidence Score .............................. (aggregate + per-dept)
4.  Dissenting Opinions ........................... (minority record)
5.  Supporting Evidence ........................... (cataloged, challenged)
6.  Risks ......................................... (Risk/Finance/Legal)
7.  Opportunities ................................ (Marketing/Research)
8.  Suggested Next Steps .......................... (actionable, owned)
9.  Full Organizational Discussion ................. (threaded transcript)
Appendices: Charter, Task Graph, Challenge Log, Caspian Log, Vote Record
```

| # | Section | Author | Content |
|---|---|---|---|
| 1 | Executive Summary | CEO | ≤300 words. The question, recommendation, confidence, headline risk. Written last, placed first. |
| 2 | Final Recommendation | Org (vote) | The recommendation plus the vote tally (e.g., 4–1 with Legal dissenting). Ranked alternatives if no majority. |
| 3 | Confidence Score | Engine | Aggregate score, per-department breakdown, challenge-survival rates, cycle count. |
| 4 | Dissenting Opinions | Minority | Each dissenting department's argument, in its own voice. Not rebutted here — preserved. |
| 5 | Supporting Evidence | All | Every evidence record cited by the recommendation, with its challenge history (accepted/rebutted/amended). |
| 6 | Risks | Risk/Finance/Legal | Ranked risk register with severity, likelihood, mitigation owner. |
| 7 | Opportunities | Marketing/Research | Upside cases with conditions under which they materialize. |
| 8 | Suggested Next Steps | CEO | Concrete, owned, time-bound actions (e.g., "Research to validate TAM via 50-user survey by 2026-08-12"). |
| 9 | Full Organizational Discussion | All | The complete threaded transcript: per-department findings, challenges, debate, meeting minutes, vote reasoning. |

Every participating department **signs** the report — a cryptographic
attestation (see `26-security.md`) that its contribution is accurately
represented. A department may sign **with reservation**, recorded as a
flagged dissent in section 4. The report is not `PUBLISHED` until all
signatures are collected or the charter deadline expires (in which case
unsigned departments are noted as `non_signing`). A single agent produces a
single answer; a board report has a **minority record**, a **vote tally**, a
**challenge log**, and a **transcript of organizational discussion**. These
sections are meaningless without an organization — they are the proof that
the recommendation survived peer review.

---

## 11. Walkthrough — "Should I build an AI healthcare startup?"

A complete end-to-end trace. Timestamps are illustrative (`T+xh` = x hours
after submission).

### 11.1 Submission & Scoping (T+0 → T+10m)

```
T+0   User submits goal.
T+1   CEO opens INV-2026-08-0007, state = CREATED.
T+2   CEO proposes charter: depts = {Research, Engineering, Finance,
      Marketing, Legal}; threshold = 0.72; deadline = T+48h; P1.
T+8   Legal confirms answerability (HIPAA/FDA in scope).
T+10  Charter signed. State = SCOPING → PLANNING.
```

### 11.2 Planning (T+10m → T+30m)

The Planner (`18-planner-design.md`) produces a task graph:

```
Research ──┬── T-001  Survey AI-healthcare literature (last 24 months)
           ├── T-002  Identify top 10 competitors and their traction
           └── T-003  Email 3 professors (Caspian, blocking)
Engineering ─┬── T-004  Spike a HIPAA-compliant inference endpoint
             └── T-005  Estimate 6-month build effort
Finance ────┬── T-006  Build 24-month P&L with 3 scenarios
            └── T-007  Email 2 CFOs for benchmarking (Caspian, non-blocking)
Marketing ──┬── T-008  Survey 40 beta users (Caspian, Telegram, blocking)
            └── T-009  Analyze demand trends from public data
Legal ──────┬── T-010  HIPAA compliance memo
            ├── T-011  FDA SaMD classification check
            └── T-012  EU AI Act risk classification
```

State = PLANNING → RESEARCH.

### 11.3 Research (T+30m → T+20h)

All departments begin in parallel. Live display lights up:

```
T+0:31  Research    cited 5 papers (T-001)
T+0:42  Marketing   sent Telegram survey to 40 users (T-008, blocking)
T+1:05  Engineering spike started (T-004)
T+1:20  Finance     forecast v1 draft (T-006)
T+2:00  Legal       HIPAA memo v1 (T-010)
T+3:15  Research    emailed 3 professors (T-003, blocking, expected T+24h)
T+6:00  Engineering spike done: feasible, ~14 weeks for MVP
T+8:00  Finance     forecast done: 24-mo runway requires $1.8M seed
T+12:00 Marketing   28/40 survey responses, demand signal moderate
T+18:00 Legal       FDA SaMD likely Class II → 510(k) needed
T+20:00 Research    2/3 professors responded; 1 says "saturated market"
```

State = RESEARCH → ANALYSIS (all departments closed; 1 professor response
expired and was logged as `INSUFFICIENT`).

### 11.4 Analysis & Challenge (T+20h → T+30h)

```
F-001 (Research)  "AI radiology market is saturated (n=2 expert responses)"
   └─ Challenge by Marketing: "Survey shows 62% of users want this"
      └─ Research response: AMENDED → "saturated for radiology,
         underserved for primary-care triage"
F-002 (Engineering) "MVP feasible in 14 weeks, HIPAA-compliant"
   └─ Challenge by Legal: "510(k) adds 6-9 months not in estimate"
      └─ Engineering response: ACCEPTED, effort revised to 14wk + 6mo
F-003 (Finance) "$1.8M seed for 24-month runway"
   └─ Challenge by CEO: "assumes 0 revenue; survey shows some demand"
      └─ Finance response: REBUTTED → "survey demand ≠ paying demand"
F-004 (Legal) "FDA 510(k) required; EU AI Act high-risk"
   └─ no challenges
```

Knowledge Graph updated with 4 findings, 3 challenges (1 amended, 1
accepted, 1 rebutted). State = ANALYSIS → DEBATE.

### 11.5 Debate (T+30h → T+40h)

```
Round 1:
  Marketing   → "Recommend GO. Demand is real, MVP feasible."
  Finance     → "Recommend HOLD. $1.8M with no revenue is risky."
  Legal       → "Recommend NO-GO solo; GO only with regulatory co-founder."
  Research    → "Recommend GO for primary-care triage, not radiology."
  Engineering → "Recommend GO with revised 14wk+6mo timeline."
Round 2:
  Finance    challenges Marketing's demand evidence (survey ≠ paying).
  Marketing  challenges Finance's zero-revenue assumption.
  Research   introduces a pivot: target primary-care triage, not radiology.
Round 3 (stall detected):
  Positions: GO (Marketing, Engineering, Research-pivoted)
             HOLD (Finance);  CONDITIONAL-GO (Legal)
  → Meeting called (20-meeting-engine.md) to resolve GO vs HOLD.
```

### 11.6 Meeting (T+40h → T+42h)

```
MEETING M-2026-08-0007-01   Chair: CEO
Attendees: Research, Finance, Marketing, Legal, Engineering
Agenda: resolve GO vs HOLD given pivot to primary-care triage.
Outcome:
  - Finance accepts GO if seed ≥ $2.2M (revised runway with regulatory cost)
  - Legal accepts GO with condition: hire regulatory lead by month 2
  - Marketing accepts pivot to primary-care triage
  - Research withdraws radiology framing
  - Engineering updates effort to 14wk + 6mo + 1 regulatory hire
Candidate set frozen:
  CAND-A: GO (pivot to primary-care triage, $2.2M seed, regulatory hire)
  CAND-B: HOLD (re-evaluate in 90 days)
```

State = DEBATE → SYNTHESIS. The CEO drafts synthesis recommending CAND-A
with the meeting's conditions encoded; draft report routed. State =
SYNTHESIS → VOTE.

### 11.7 Vote & Confidence (T+44h → T+45h)

```
Vote (weighted by trust, see 08-trust-engine.md):
  Research     trust 0.96  → GO    (weight 0.96)
  Engineering  trust 0.91  → GO    (weight 0.91)
  Finance      trust 0.94  → GO*   (weight 0.94)  *with conditions
  Marketing    trust 0.81  → GO    (weight 0.81)
  Legal        trust 0.93  → COND  (weight 0.93)  *with conditions
Tally: 4.62 GO / 0.93 COND-GO / 0.00 NO-GO   → Recommendation: GO w/ conditions

Confidence:
  Research     0.88 × survival 1.00 × trust 0.96 = 0.845
  Engineering  0.82 × survival 0.75 × trust 0.91 = 0.560  (510(k) challenge)
  Finance      0.79 × survival 0.66 × trust 0.94 = 0.491  (rebutted)
  Marketing    0.74 × survival 0.50 × trust 0.81 = 0.300  (survey challenge)
  Legal        0.90 × survival 1.00 × trust 0.93 = 0.837
  org_confidence = 0.723   (threshold 0.72)  → MEETS
```

State = VOTE → CONFIDENCE_CHK → REPORT (meets threshold, cycle 1/3).

### 11.8 Report & Publish (T+45h → T+47h)

Board report assembled (§10). All five departments sign; Legal and Finance
sign **with reservation** (their conditions are recorded in Dissenting
Opinions even though they voted GO). Report sealed and delivered to the
user. State = REPORT → PUBLISHED → ARCHIVED. The archived investigation
becomes a learnable case for the Evolution Engine (`07-evolution-engine.md`):
Research's trust rises (its pivot framing was adopted); Marketing's trust
dips slightly (its survey evidence was rebutted); Engineering's trust
rises (its 510(k) challenge was accepted). The next investigation benefits.

---

## 12. Data Model & Event Stream (Brief)

The full schema lives in `13-database-design.md`. Investigation-relevant
entities: `Investigation` (case_id, goal, charter jsonb immutable after
SCOPING, state enum folded over events, phase, cycle_count, priority,
confidence, timestamps); `Evidence` (evidence_id, case_id, department, type,
citation, challenge_status, signed_by); `Challenge` (challenge_id,
evidence_id, challenger_dept, claim, response, resolution); `CaspianOutreach`
(outreach_id, case_id, department, channel, recipient, message,
expected_by, status); `Vote` (vote_id, case_id, department, candidate,
weight, reservation).

Every state transition and departmental action is an event on the
`investigation.{case_id}` stream (see `16-event-system.md`). The live display
(§6) is a projection of this stream; the state machine (§5) is a fold over
it. Sample events: `investigation.created`, `investigation.scoping.started`,
`investigation.research.started`, `evidence.submitted`,
`evidence.challenged`, `evidence.resolution`, `caspian.outreach.sent`,
`caspian.outreach.answered`, `debate.round.completed`, `meeting.called`,
`vote.cast`, `investigation.confidence.checked`,
`investigation.research.looped`, `investigation.published`,
`investigation.archived`.

---

## 13. Failure & Cancellation

- **FAILED** — reached from SCOPING if the goal is unanswerable by this org,
  or from unrecoverable engine errors. A failure memo is written to memory;
  the user is notified with the reason.
- **CANCELLED** — reachable from any state via explicit user action. Partial
  evidence is preserved in memory as a `cancelled` case; it may be resumed
  as a new investigation that inherits the prior evidence graph.
- **Deadline expiry** — if the charter deadline passes while in RESEARCH or
  ANALYSIS, the engine does not auto-fail. It forces a CONFIDENCE_CHK with
  whatever evidence exists; low-confidence reports are delivered with an
  explicit caveat. The organization does not silently drop work.

---

## 14. Cross-References

| Topic | Document |
|---|---|
| Task decomposition | `18-planner-design.md` |
| Society-of-Thought debate mechanics | `10-debate-engine.md` |
| Meeting scheduling & execution | `20-meeting-engine.md` |
| Internal messaging | `11-communication-engine.md` |
| External communication | `12-caspian-integration.md` |
| Reputation & weighted trust | `08-trust-engine.md` |
| Institutional memory | `06-memory-system.md` |
| Knowledge graph | `14-knowledge-graph.md` |
| Event-driven architecture | `16-event-system.md` |
| State machines (org & investigation) | `25-state-machine.md` |
| Constitutional governance | `09-constitution-engine.md` |
| Organizational DNA | `21-organizational-dna.md` |
| Org self-modification | `07-evolution-engine.md` |
| Data model & persistence | `13-database-design.md` |
| API surface (REST/WebSocket) | `24-api-design.md` |
| Security & signing | `26-security.md` |
| Mobile UX | `22-mobile-ux.md` |
| MVP scope | `28-mvp-hackathon.md` |

---

## 15. Design Invariants

These invariants are non-negotiable. A violation is a bug.

1. **No silent evidence.** Every evidence record is visible in the live
   display and in the final report, including its challenge history.
2. **No closed challenges entering debate.** A finding with an OPEN
   challenge cannot enter the Debate phase. The state machine enforces this.
3. **No CEO override of the vote.** The CEO synthesizes; the org votes. The
   CEO may break a tie only if the constitution permits it.
4. **No unsigned reports.** A report is not PUBLISHED until all
   participating departments sign or the deadline expires with the
   non-signers noted.
5. **No unbounded loops.** The confidence loop is capped at `max_cycles`
   (default 3) by the constitution.
6. **No intra-investigation memory leak.** Investigations are memory-isolated
   unless the CEO issues a logged cross-reference.
7. **The organization is the intelligence.** No phase, no score, no report
   section may be producible by a single agent. Apply the One Agent Test to
   every change to this engine.
