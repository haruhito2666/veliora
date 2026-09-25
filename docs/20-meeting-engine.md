# 20 — Meeting Engine

> Meeting scheduling and execution. The synchronous coordination
> layer — where information is shared, decisions are framed, and
> debates are triggered.

## 1. Overview

A meeting in Veliora is *not* a debate and *not* a chat. It is a
**scheduled, time-boxed, agenda-driven coordination event** in which
multiple agents share status, surface blockers, frame decisions, and
assign action items. Where the Debate Engine (`10-debate-engine.md`)
is the *adversarial* channel, the Meeting Engine is the *cooperative*
channel: alignment, status, and handoffs.

The two are complementary and frequently chained: a meeting surfaces a
disagreement that status-sharing cannot resolve, and the chair
*resolves the meeting to a debate*; the debate's verdict is reported
back to a follow-up meeting. This chaining is the normal rhythm of an
investigation.

```
Investigation progress
    │
    ▼
┌──────────┐  surfaces fork  ┌──────────┐
│ MEETING  │────────────────▶│ DEBATE   │
│ (sync)   │                 │ (adversarial)│
└──────────┘◀────────────────└──────────┘
    │       verdict reported      │
    ▼                             ▼
action items ──────────▶ investigation continues
```

The engine is invoked by the Investigation Engine (see
`19-investigation-engine.md`) at scheduled checkpoints, by the CEO
ad-hoc, or by the Emergency trigger (§4.4). It is event-driven (see
`16-event-system.md`) and persists every meeting as an immutable
`MeetingRecord` in institutional memory (see `06-memory-system.md`).

## 2. The One Agent Test

> *Would this feature still exist if there were only one AI agent?*

A meeting requires multiple participants with independent status to
report and independent stakes in the outcome. A single agent has no
one to coordinate with, no one to hand off to, no one whose status it
needs to hear. The Meeting Engine exists *only because* Veliora is
an organization. Every primitive — turn-taking, speaking order by
trust and relevance, agenda negotiation, action-item assignment — is
meaningless without multiple agents. If a "meeting" could be replaced
by a single agent's internal monologue, it is not a meeting. The
engine enforces this by requiring at least two participants with
standing for any meeting to open.

## 3. Meetings vs Debates — The Distinction

The second most misunderstood boundary (after debate vs roleplay).
Both involve multiple agents talking in turns, but they are
structurally different.

| Aspect | Meeting | Debate |
|--------|---------|--------|
| Mode | Cooperative — share, align, assign | Adversarial — argue, challenge, verdict |
| Goal | Coordination and awareness | Resolving a contested question |
| Output | Minutes + action items | Verdict + dissenting opinions |
| Turn logic | By agenda item, status-driven | By protocol phase, argument-driven |
| Termination | Agenda exhausted | Consensus, timeout, or vote |
| Trigger | Schedule or ad-hoc call | A contested question exists |
| Who speaks | Anyone with status on the agenda item | Anyone with standing on the question |
| Record | `MeetingRecord` (minutes, actions) | `DebateRecord` (verdict, tally, dissent) |

A meeting that hits an irreconcilable disagreement does not *become* a
debate — it *suspends* and *spawns* a debate, then resumes when the
debate closes. The meeting owns the agenda; the debate owns the
question.

## 4. Meeting Types

Five types, each with a distinct trigger, participant set, and
authority level.

**4.1 All-Hands.** Trigger: scheduled cadence (e.g., weekly) or CEO
call for org-wide alignment. Participants: every agent. Purpose:
status across all investigations, org health, announcements. Authority:
informational — no binding decisions; decisions are framed here and
resolved in Departmental, Cross-Functional, or Board meetings.

**4.2 Departmental.** Trigger: department head call or department
cadence. Participants: all agents in one department. Purpose:
department-internal coordination — task assignment, internal status,
handoffs. Authority: binding on department-internal matters only.

**4.3 Cross-Functional.** Trigger: an investigation touches multiple
departments and the Investigation Engine schedules a sync point (§6),
or the CEO calls one to resolve a cross-departmental dependency.
Participants: one representative per relevant department (highest-trust
agent by default; the head may nominate another). Purpose: share
progress across departments, surface cross-departmental blockers, frame
decisions needing multiple departments' input. Authority: binding on
coordination and action items; may *spawn* a debate on a contested
question but does not itself render a verdict.

**4.4 Emergency.** Trigger: the `emergency.raise` event — an agent
detects a blocking issue (deadline about to slip, evidence retracted,
time-sensitive external message via Caspian, constitutional violation).
See `12-caspian-integration.md` for external triggers. Participants:
the reporting agent, relevant department head, CEO, and any agent with
direct standing. Purpose: triage — decide whether the issue blocks the
investigation, and whether to escalate to a Board meeting, spawn a
debate, or defer. Authority: may pause an investigation, reassign
agents, or call a Board meeting. May not amend the constitution or
override a prior debate verdict without a new debate.

**4.5 Board.** Trigger: an investigation reaches its decision point, an
Emergency meeting escalates, or the constitution requires board
approval. Participants: the CEO and heads of all departments (the
"board"). Purpose: render the final organizational decision — review
synthesized evidence, debate verdicts, and dissenting opinions; produce
the organization's position. Authority: maximum. A Board meeting's
decision is the organization's decision, recorded as the
investigation's `final_recommendation` and delivered to the user.
Only a subsequent Board meeting or a constitutional amendment can
override it.

```
Meeting Type       Trigger                 Binding?   Can spawn debate?
────────────────────────────────────────────────────────────────────────
All-Hands         cadence / CEO call       No         No (frames only)
Departmental      dept head / cadence      Dept-only  No
Cross-Functional  investigation sync       Coord only Yes
Emergency         emergency.raise          Triage     Yes
Board             decision point / escal.  Maximum    Yes (often does)
```

## 5. Meeting Lifecycle

Every meeting runs through eight stages. The engine enforces the state
machine (see `25-state-machine.md`).

```
┌──────┐ ┌────────┐ ┌─────────┐ ┌──────────┐
│CALLED│▶│AGENDA  │▶│NOTIFIED │▶│PREPARING │
└──────┘ │  SET   │ └─────────┘ └────┬─────┘
         └────────┘                     │
                                        ▼
┌──────────┐ ┌────────┐ ┌──────────┐
│FOLLOW-UP │◀│MINUTES │◀│CONDUCT   │
└──────────┘ │+ACTIONS│ │          │
             └────────┘ └──────────┘
```

**Stage 1 — Called.** An authorized caller (CEO, department head,
Investigation Engine, or Emergency trigger) creates a `MeetingRecord`
in `CALLED` state with a type, caller, reason, and tentative
participants.

**Stage 2 — Agenda Set.** The chair proposes an ordered agenda. Each
item:

```
AgendaItem { id, title, owner, type, debate_ref, time_budget, desired_outcome }
# type: status | decision | blocker | announcement | debate_ref
```

Participants may *propose* agenda items before finalization; the chair
accepts or rejects. This is itself a mini-coordination step — it
surfaces what agents think is important enough to meet about.

**Stage 3 — Participants Notified.** The `meeting.invite` event is
emitted to each participant. An agent that does not acknowledge before
`notification_deadline` is marked `absent` and the meeting proceeds
without it (its department is unrepresented — recorded as a governance
gap).

**Stage 4 — Preparation.** Between notification and convene, each
participant *prepares*: pulls its department's current status from
memory, reviews the agenda items it owns, and may request evidence from
the Knowledge Graph (see `14-knowledge-graph.md`). Preparation makes
meetings efficient — agents arrive with structured status, not a blank
context. The engine enforces a minimum prep window scaled by DNA (see
`21-organizational-dna.md`): high-Accuracy orgs require longer prep;
high-Speed orgs shorten it.

**Stage 5 — Conduct.** The meeting is convened and runs the agenda in
order (§7). Each agenda item produces a discussion, a decision (or a
spawned debate), and zero or more action items.

**Stage 6 — Minutes.** The engine produces a structured minutes
document — not a raw transcript, but an agenda-item-by-item summary with
decisions and action items. Minutes are drafted by the chair and
reviewed by participants before close; a participant may flag a minute
as inaccurate, forcing a correction.

**Stage 7 — Action Items.** Every decision or blocker produces action
items:

```
ActionItem { id, meeting_id, owner, description, due, status, blocks }
# status: open | in_progress | done | blocked | cancelled
# due: timestamp | investigation_checkpoint
```

Action items are tracked by the engine and surface in the next
meeting's status review. An overdue action item that blocks an
investigation auto-raises an Emergency meeting.

**Stage 8 — Follow-Up.** The meeting's action items and decisions feed
back into the investigation (§9). The engine schedules the next meeting
in the cadence or at the next investigation checkpoint, and the cycle
repeats.

## 6. Scheduling Based on Investigation Progress

Meetings are not scheduled on a fixed clock alone — they are scheduled
by the Investigation Engine at *progress checkpoints*. This is what
makes the organization feel alive: meetings happen *because something
changed*, not because the calendar said so.

### 6.1 Checkpoint-Triggered Meetings

The Investigation Engine emits `investigation.checkpoint` events at
defined progress points:

```
Checkpoint              Meeting Type        Participants
─────────────────────────────────────────────────────────────
plan_finalized          Cross-Functional   all dept reps
evidence_phase_done     Cross-Functional   all dept reps
debate_verdict_rendered Board              board
evidence_retracted      Emergency          reporter + CEO + dept head
deadline_at_risk        Emergency          dept head + CEO
investigation_complete  Board              board + all dept heads
```

### 6.2 Cadence Meetings

Each meeting type has a DNA-modulated cadence:

```
cadence(meeting_type) = base_interval * (1 + Long_Term) / (1 + Speed)

base_interval:
  all_hands: 7 days   departmental: 2 days
  cross_functional: 3 days   board: on-demand   emergency: event-driven
```

A high-Speed org meets more often; a high-Long-Term org meets less
often (longer intervals, deeper prep).

### 6.3 Conflict and Overlap

The engine prevents double-booking. If a checkpoint meeting and a
cadence meeting would overlap for an agent, the checkpoint meeting wins
(event-driven, more urgent) and the cadence meeting is rescheduled. If
two investigations both schedule cross-functional meetings for the
same agent, they are merged if the agendas overlap, or sequenced if
they do not.

## 7. Meeting Dynamics

How a meeting runs once convened. The engine enforces turn-taking,
speaking order, and time limits — not as style, but as load-balancing
for the organization's reasoning.

### 7.1 Turn-Taking

Each agenda item is a mini-session: (1) **owner presents** a structured
status or proposal; (2) **clarifying questions** — other participants
ask, the owner answers; (3) **discussion** — open turn-taking governed
by §7.2; (4) **decision or spawn** — the chair calls for a decision; if
uncontested it is recorded, if contested a debate is spawned and the
item is marked `deferred_to_debate`; (5) **action items** — any
decisions or blockers produce action items (Stage 7).

### 7.2 Speaking Order

In the open discussion phase, who speaks is governed by **trust and
relevance**, not by who speaks first:

```
speaking_priority(agent, agenda_item) =
    trust(agent) * relevance(agent, agenda_item) * recency_penalty(agent)

relevance(agent, agenda_item) =
    mandate_relevance(dept(agent), agenda_item.topic)   # Constitution Engine

recency_penalty(agent) =
    1.0 if not spoken this item | 0.3 if once | 0.1 if 2+
```

This ensures: high-trust agents speak first on items in their mandate;
an agent who has already spoken yields to one who has not; an agent
speaking out of mandate contributes less. It is *not* a hard silence —
any agent may raise a `point_of_order` to interrupt with a blocker or
correction, which the chair must acknowledge.

### 7.3 Time Limits

Each agenda item has a `time_budget`. The engine tracks token spend per
item and per agent. When exhausted: `status`/`announcement` → closed,
deferred to minutes; `decision` (contested) → *spawned to a debate*
(which has its own budget; see `10-debate-engine.md` §7); `blocker` →
escalated — an Emergency meeting is scheduled if the blocker is
investigation-critical. Budgets are DNA-modulated: high-Speed orgs
have tight budgets; high-Accuracy orgs have generous ones.

### 7.4 The Chair

Every meeting has a chair. By default: All-Hands, Board, Emergency →
the CEO; Departmental → the department head; Cross-Functional → the
CEO or a rotating chair. The chair sets and orders the agenda, enforces
time budgets, calls for decisions, spawns debates on contested items,
and drafts the minutes. The chair does *not* vote-weight more than
other participants in any spawned debate — the chair's authority is
procedural, not substantive. This separation is enforced to prevent the
chair from becoming a single-agent decision-maker, which would fail the
One Agent Test.

## 8. Meeting Protocol Specification

The protocol is defined as typed messages on the `meeting.*` event
channel (see `16-event-system.md`). All messages are signed and
persisted.

### 8.1 Message Types

```
meeting.call         { meeting_id, type, caller, reason, tentative_participants }
meeting.agenda        { meeting_id, chair, items: [AgendaItem] }
meeting.invite        { meeting_id, agent_id, role: participant|chair|observer }
meeting.ack           { meeting_id, agent_id, prepared, prep_notes_ref }
meeting.convene       { meeting_id, attendees: [agent_id] }
meeting.turn          { meeting_id, agenda_item, speaker, kind, text }
                         # kind: present | question | discuss | order
meeting.decision      { meeting_id, agenda_item, decision, rationale }
meeting.spawn_debate  { meeting_id, agenda_item, debate_id, debate_type, question }
meeting.action        { meeting_id, agenda_item, action: ActionItem }
meeting.minutes       { meeting_id, minutes_ref, items: [{agenda_item, summary, decision, actions}] }
meeting.close         { meeting_id, attendees, action_items, next_meeting_id }
```

### 8.2 Ordering Rules

1. `agenda` after `call`, before any `invite`.
2. `invite` to all participants; `ack` expected before `convene`. No
   `ack` → `absent`.
3. `turn` only after `convene` and only from `attendees`. A `turn` from
   an `observer` is rejected (observers watch but do not speak —
   relevant for the live user view, §10).
4. `decision` and `action` emitted by the chair, in response to
   discussion on an agenda item.
5. `spawn_debate` suspends the agenda item, opens a debate, and the
   meeting continues to the next item. The debate's verdict is reported
   back via a `debate.close` event the meeting engine listens for; it
   schedules a follow-up agenda item to present the verdict.
6. `minutes` drafted by the chair, reviewed by participants (each may
   emit `meeting.minutes_review`), finalized before `close`.

### 8.3 State Machine

```
IDLE ─call─▶ CALLED ─agenda─▶ AGENDA ─invite─▶ NOTIFYING
                                                  │ prep
                                                  ▼
                                             PREPARING
                                                  │ convene
                                                  ▼
              ┌─spawn_debate─▶ DEBATE SUSPENDED ─┐
              │                                │ debate.close
CONDUCT ─agenda done─▶ MINUTES ─close─▶ CLOSED ◀┘
```

## 9. Outcomes Feedback to Investigations

A meeting is not an end — it is a transform on the investigation's
state. The engine wires meeting outputs back through three channels:

**9.1 Action Items → Investigation Tasks.** Each `ActionItem` whose
`blocks` field references an investigation is converted (by the
Investigation Engine) into a task on that investigation's task graph
(see `18-planner-design.md`). The `owner` becomes the task's assignee;
the `due` becomes the task's deadline. This is how a meeting *changes
what the organization does next*.

**9.2 Decisions → Evidence Ledger.** A meeting `decision` on a
`decision`-type item is recorded in the investigation's evidence ledger
as a *decision evidence* entry citing the `MeetingRecord`. It is
debatable later (an agent may file an `evidence.challenge` if new
evidence contradicts it), but it stands until challenged.

**9.3 Spawned Debates → Verdicts.** When a meeting spawns a debate, the
debate's verdict is recorded as the authoritative resolution of the
contested question, with a back-reference to both the meeting and the
debate. The investigation's final recommendation (produced at the Board
meeting) cites these debate verdicts as its justification.

### 9.4 The Feedback Loop

```
Investigation checkpoint
    │ ▼
Cross-Functional Meeting
    │ (surfaces a fork) ▼
Debate ──verdict──▶ Evidence Ledger
    │                   │
    ▼                   ▼
Action Items ──▶ Investigation Tasks
    │                   │
    ▼                   ▼
Follow-up Meeting ◀── progress ◀── tasks execute
    │ ▼
... until Board Meeting ──▶ Final Recommendation ──▶ User
```

This loop is the heartbeat of an investigation. The user watches it
happen live (§10).

## 10. Live Meeting View

The user does not read minutes after the fact — they watch meetings
happen in real time, if the organization's DNA permits it (see
`21-organizational-dna.md`, Transparency parameter).

### 10.1 Visibility Rules

```
if Transparency > 0.7: live_view=true;  publish_minutes=true
elif Transparency > 0.3: live_view=true;  publish_minutes=false
else: live_view=false; publish_minutes=false
```

### 10.2 Live View Layout

The live meeting view (mobile and web) renders:

```
┌─────────────────────────────────────────────┐
│ ◉ LIVE  Cross-Functional Meeting m-0087     │
│ Investigation: "AI Healthcare Startup"       │
│ Chair: CEO    Agenda: 3/5 items             │
├─────────────────────────────────────────────┤
│ ▸ Agenda 3: Regulatory feasibility          │
│   Owner: Legal                              │
│   ─────────────────────────────────────────  │
│   [Legal]   presenting: 3 jurisdictions      │
│              mapped, 2 require licensing...  │
│   [Research] question: source on the 2?     │
│   [Legal]   answer: FDA enforcement DB      │
│   [Finance] discuss: licensing cost est.?   │
│   [Legal]   ~$180k per jurisdiction, 6 mo.  │
│   ⏱  budget: 320/500 tokens                 │
├─────────────────────────────────────────────┤
│ Next: Agenda 4 — Go-to-market timing       │
│ Pending: 1 action item (Finance: cost model)│
└─────────────────────────────────────────────┘
```

The user may tap any participant to see their trust score, mandate, and
recent contributions; tap an agenda item to see its discussion history;
tap a spawned debate to jump to the live debate view (see
`10-debate-engine.md` §10).

### 10.3 User Interaction

The user is an *observer* by default. In organizations configured for
high user participation (a DNA parameter beyond the MVP), the user may
raise a `point_of_order` (interrupt with a question), propose an agenda
item (before finalization), or request a debate on a decision (which
the chair may accept or reject; if accepted, the user's question becomes
a debate topic). User interaction is out of scope for the MVP (see
`28-mvp-hackathon.md`) and is roadmap (see `29-future-roadmap.md`).

## 11. Example Meeting Transcript

Condensed transcript of a Cross-Functional Meeting in a Startup Society
investigating "Should we build an AI healthcare startup?" Shown as the
live view renders it, with stage and agenda markers.

```
MEETING m-0087  cross_functional
INVESTIGATION iv-0019  "Should we build an AI healthcare startup?"
CHAIR  CEO
DNA    Transparency=0.8 (visible), Speed=0.6, Accuracy=0.7

─── STAGE 4: PREPARATION (condensed) ──────────────────────────────
[Research]   14 papers, 3 competitor analyses
[Marketing]  surveyed 240 beta users via Caspian (Telegram)
[Engineering] 2 architecture sketches, 1 risk matrix
[Finance]    18-month forecast, 3 scenarios
[Legal]      3 jurisdictions mapped, 2 require licensing

─── STAGE 5: CONDUCT ─────────────────────────────────────────────
─── Agenda 1: Status across departments ──────────────── status
  [CEO]        investigation at 62%, on schedule, 1 blocker
  [Research]   evidence strong on demand, weak on regulation
  [Marketing]  71% of surveyed users would try; n=240
  [Engineering] feasible in 6mo; 2 critical risks identified
  [Finance]    18mo runway at $1.2M burn; tight
  [Legal]      2/3 jurisdictions need licensing; 6mo each
  DECISION: status noted; no action.

─── Agenda 2: Critical risks (Engineering) ───────────── blocker
  [Engineering] Risk 1 — licensing blocks launch in 2/3
              jurisdictions. Risk 2 — privacy compliance adds 3mo.
  [Legal]      Q: which jurisdictions are blocking?
  [Engineering] A: the two requiring licensing (Agenda 4).
  [Finance]    3mo privacy delay pushes runway negative. Re-forecast.
  DECISION: spawn action — Finance re-forecast with +3mo build.
  ACTION ai-0203  Finance  due: next checkpoint
              "Re-forecast runway with 3-month privacy build delay."

─── Agenda 3: Regulatory feasibility (Legal) ──────── decision
  [Legal]      3 jurisdictions mapped. A and B require licensing
              (~$180k, 6mo each). C does not.
  [Research]   Q: source on the licensing requirement?
  [Legal]      A: regulatory database + 2 Caspian emails to local
              counsel in A and B. Confidence 0.85.
  [Marketing]  if we launch in C only, we lose 60% of addressable
              market per the survey.
  [Finance]    C-only is profitable but slow. A+B unlock 2.4x revenue.
  [CEO]        point_of_order: contested strategic fork — C-only
              vs A+B. Spawning a debate.
  SPAWN DEBATE dd-0044  strategic
              "Launch C-only now, or delay for A+B licensing?"
  DEFERRED to debate; meeting continues.

─── Agenda 4: Go-to-market timing (Marketing) ─────── decision
  [Marketing]  71% will try; competitor B launching in Q3. Window 4mo.
  [Engineering] Q: can we ship C-only in 4 months?  A: yes, defer
              privacy build to v2.
  [Finance]    Q: deferred privacy build — legal exposure?
  [Legal]      A: acceptable in C under current law; not in A/B.
  DECISION: C-only v1 in 4mo; privacy build in v2; A/B after licensing.
              Contingent on debate dd-0044 outcome.
  ACTION ai-0204  Engineering  due: 4mo  "Ship C-only v1, defer privacy to v2."
  ACTION ai-0205  Marketing    due: 2mo  "Prepare C-only GTM; competitor B watch."

─── Agenda 5: Next checkpoint ──────────────────── announcement
  [CEO]        next cross-functional at dd-0044 verdict + Finance
              re-forecast. Board meeting at investigation complete.
  DECISION: next meeting scheduled at dd-0044 close.

─── STAGE 6: MINUTES ─────────────────────────────────────────────
[CEO]        minutes drafted. All participants ack.

─── STAGE 7: ACTION ITEMS ─────────────────────────────────────────
  ai-0203  Finance     re-forecast runway (+3mo)    next checkpoint
  ai-0204  Engineering ship C-only v1               4 months
  ai-0205  Marketing   C-only GTM + competitor watch 2 months

─── STAGE 8: FOLLOW-UP ────────────────────────────────────────────
  next_meeting: m-0088  trigger: debate dd-0044 close
  investigation iv-0019 state: 62% → 68% (action items added)
─── CLOSE m-0087 ──────────────────────────────────────────────────
```

The spawned debate `dd-0044` runs to verdict per `10-debate-engine.md`.
Its verdict is reported back to follow-up meeting `m-0088`, which
incorporates it into the evidence ledger and adjusts action items.

## 12. Relationship to Other Engines

- **Investigation Engine** (`19-investigation-engine.md`) — primary
  scheduler; checkpoints trigger meetings; outputs feed back.
- **Debate Engine** (`10-debate-engine.md`) — meetings spawn debates on
  contested items; debates report verdicts to follow-up meetings.
- **Communication Engine** (`11-communication-engine.md`) — the async
  layer between meetings; meetings reference threads, threads may
  request a meeting.
- **Trust Engine** (`08-trust-engine.md`) — supplies trust scores that
  govern speaking order and action-item ownership weighting.
- **Constitution Engine** (`09-constitution-engine.md`) — supplies
  `mandate_relevance` for speaking priority; defines who may call each
  meeting type.
- **Memory System** (`06-memory-system.md`) — persists the immutable
  `MeetingRecord`, minutes, and action items; audit trail.
- **Organizational DNA** (`21-organizational-dna.md`) — modulates
  cadence, time budgets, prep windows, and live-view visibility.
- **Event System** (`16-event-system.md`) — carries all `meeting.*`
  messages; the engine is fully event-driven.
- **Planner Design** (`18-planner-design.md`) — meeting action items
  become tasks on the investigation's task graph.
- **Caspian Integration** (`12-caspian-integration.md`) — external
  messages may trigger Emergency meetings; prep may pull Caspian threads.

## 13. Open Questions (Roadmap)

1. **User as participant.** Beyond observation, how much should the
   user steer meetings? The MVP treats the user as an observer; the
   roadmap contemplates `point_of_order` and agenda-proposal rights.
2. **Inter-organization meetings.** When two orgs coordinate (SPEC
   §10), should a Meta Society host a joint meeting? The protocol
   supports it; standing and chair rules need definition.
3. **Meeting fatigue.** In large orgs, an agent could be in
   back-to-back meetings all day. A meeting-load limit per agent, with
   overflow deferred to async threads, is contemplated.
4. **Action-item auto-escalation.** Currently an overdue blocking item
   auto-raises an Emergency meeting. Should repeated overdue items from
   the same agent trigger a Trust Engine review instead? Likely yes.

---

*See `10-debate-engine.md` for the adversarial channel that meetings
spawn into; `19-investigation-engine.md` for the orchestration that
schedules meetings; and `11-communication-engine.md` for the async layer
that carries coordination between meetings.*
