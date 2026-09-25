# 23 — UI/UX Flow

> A user does not "use" Veliora the way they use a chat app. They **witness
> an organization work**, and they steer it. This document specifies the
> end-to-end journeys that make that witnessing legible and that steering
> precise.

This document defines seven canonical user flows, the ASCII flow diagram for
each, the screen transitions, the data exchanged at every step, and the
shared interaction patterns that recur across flows. It is the companion to
`22-mobile-ux.md` (screen-level design) and should be read with it.

Cross-references: `04-technical-architecture.md`, `05-agent-architecture.md`,
`06-memory-system.md`, `07-evolution-engine.md`, `08-trust-engine.md`,
`09-constitution-engine.md`, `10-debate-engine.md`, `11-communication-engine.md`,
`12-caspian-integration.md`, `16-event-system.md`, `17-prompt-architecture.md`,
`18-planner-design.md`, `19-investigation-engine.md`, `20-meeting-engine.md`,
`21-organizational-dna.md`, `22-mobile-ux.md`, `24-api-design.md`,
`25-state-machine.md`.

---

## 1. Design Principles

### 1.1 Always Alive

No flow ever lands the user on a dead screen. Between every step there is a
living organization doing something, and the UI surfaces it:

- A **heartbeat pulse** (`org.{id}.heartbeat`, see `16-event-system.md`) drives
  a `LivePulse` indicator on every screen header.
- A **current activity line** under every department card
  (`dept.{id}.activity.changed`) replaces the notion of "idle."
- A **phase indicator** on every investigation and meeting advances in real
  time (see `25-state-machine.md`).

If the org is genuinely paused, that pause is an **explicit, labeled state**
(`PAUSED — user-requested` or `PAUSED — awaiting quorum`), never an absence
of motion mistaken for a bug.

### 1.2 Always Transparent

Every conclusion, recommendation, vote, hire, fire, and message is
**traceable** to its origin:

- A report recommendation links to the meeting that produced it.
- A meeting decision links to the votes that produced it.
- A vote links to the worker who cast it and the argument they gave.
- An argument links to the evidence it cites.
- Evidence links to the department that gathered it and the peer that
  challenged it.
- A hire links to the performance signal that triggered it.

There is no "the AI said so." There is always a *who* and a *why*. This is the
mobile expression of contribution #7 (Evidence Challenge) and #4
(Institutional Memory).

### 1.3 Always Actionable

Every screen the user lands on during a flow exposes at least one
affordance. The canonical affordance set, scoped by context:

| Context | Affordances |
|---------|-------------|
| Investigation (live) | `Pause`, `Ask a Question`, `Redirect` |
| Meeting (live) | `Join as Observer`, `Submit Comment to Chair` |
| Report (delivered) | `Start Follow-up Investigation`, `Share`, `Archive` |
| Department | `Open Memory`, `Open Conversations`, `View Relationships` |
| Caspian thread | `Escalate to Investigation`, `Mark Evidence` |
| Evolution event | `View Org Chart Diff`, `Override` (rare) |
| Constitution | `Amend` |

An affordance is never greyed out without a reason string
(e.g., *"Redirect unavailable — investigation is voting"*).

### 1.4 The One Agent Test, Applied to Flows

Each flow must answer: *what about this journey only makes sense because
there is an organization?*

| Flow | One-agent analogue | Why it survives |
|------|--------------------|-----------------|
| 1 Create Org | "Pick a model" | Templates encode **organizational culture, hierarchy, specialists** — not a persona. |
| 2 Submit Goal | "Ask a question" | The user watches **division of labor** across departments, debate, and vote — not a single stream. |
| 3 Explore Org | "Inspect model internals" | The user explores **collective memory, peer conversations, trust distribution** — none exist for one agent. |
| 4 Watch Meeting | (none) | A meeting is intrinsically multi-party. |
| 5 View Comms | "View web search log" | Different departments reach different people on different channels — coordination, not retrieval. |
| 6 View Evolution | "View changelog" | The org **restructures itself**; the user reads decisions the org made about itself. |
| 7 Amend Constitution | "Edit system prompt" | Amendment is a **governed, debated, voted** act — not an edit. |

---

## 2. Flow Map

```
                       ┌──────────────────────────────────────┐
                       │           User enters Veliora        │
                       └──────────────────┬───────────────────┘
                                          │
            ┌─────────────────────────────┼─────────────────────────────┐
            ▼                             ▼                             ▼
     (no orgs yet)               (has orgs)                    (wants to steer)
            │                             │                             │
            ▼                             ▼                             ▼
   FLOW 1: Create Org            FLOW 2: Submit Goal         FLOW 7: Amend
   template → DNA → launch       watch → interact → report    propose → debate → vote
            │                             │                             │
            └─────────────┬───────────────┘                             │
                          ▼                                             │
                   FLOW 3: Explore Org ◀────────────────────────────────┤
                   dept → worker → memory → evidence                   │
                          │                                             │
                          ├──────────────┬──────────────┬───────────────┘
                          ▼              ▼              ▼
                   FLOW 4: Meeting  FLOW 5: Comms   FLOW 6: Evolution
                   debate → vote    Caspian → reply   changes over time
```

---

## 3. Flow 1 — Create Organization

**Goal:** A user with no organizations creates their first one and launches it.
**Outcome:** A live org is instantiated and the user lands on its home
dashboard with a heartbeat.

### 3.1 Flow Diagram

```
 ┌─────────────┐     ┌──────────────┐     ┌──────────────┐     ┌──────────┐
 │  Empty State │ ──► │ Pick Template │ ──► │ Configure   │ ──► │ Launch & │
 │  "No orgs"   │     │ Startup/Lab/  │     │ DNA sliders │     │ Seed CEO │
 │  [+ New]     │     │ Firm/...      │     │ + name      │     │ + Depts  │
 └─────────────┘     └──────────────┘     └──────────────┘     └────┬─────┘
                                                                       │
                                                                       ▼
                                                              ┌────────────────┐
                                                              │  Home Dashboard │
                                                              │  ● live         │
                                                              └────────────────┘
```

### 3.2 Step-by-Step

1. **Empty State.** The user opens the app to `Your Organizations` with no
   rows and a single `+ New Organization` affordance. Tap → step 2.

2. **Pick Template.** A carousel of org templates, each rendered as a card
   with name, default departments, and a one-line culture summary:

   ```
   ┌─────────────────────────────────────┐
   │  Choose an Organization Template    │
   ├─────────────────────────────────────┤
   │  ▢ Startup         CEO·Res·Eng·Fin·Mkt│  ← 5 depts, high Risk
   │  ▢ Research Lab    Director·3·Res·Eth │  ← 4 depts, high Accuracy
   │  ▢ Investment Firm  CIO·Anal·Risk·Trd │  ← 4 depts, high Hierarchy
   │  ▢ Law Firm        Partner·Assoc·Paralegal│
   │  ▢ Game Studio     Studio·Design·Eng·QA│
   │  ▢ University     Dean·Faculty·Admin  │
   │  ▢ Decision Council Chair·3·Advisors │  ← personal use
   └─────────────────────────────────────┘
   ```
   Selecting a template pre-loads the DNA sliders (step 3) with the template's
   defaults. See `21-organizational-dna.md` for the parameter set.

3. **Configure DNA.** Nine sliders (Innovation, Risk, Hierarchy, Accuracy,
   Speed, Transparency, Creativity, Long-Term Thinking, Security) plus a
   `Name` field and an optional `Mission` text. Each slider shows a live
   one-line consequence:

   ```
   ┌─────────────────────────────────────┐
   │  Configure: Startup                  │
   │  Name: [ Startup Organization      ] │
   │  Mission: [ Investigate ventures…  ] │
   │                                     │
   │  Risk         ━━━━━━●━━━  High       │
   │  → "Will pursue unproven markets"    │  ← live consequence
   │  Hierarchy    ━━●━━━━━━━━  Low        │
   │  → "Flat approvals, fast debate"     │
   │  Accuracy     ━━━━━●━━━━━  Medium     │
   │  ...                                │
   │  [ Launch Organization ]             │
   └─────────────────────────────────────┘
   ```

4. **Launch.** On tap, the client calls `POST /orgs` (see `24-api-design.md`).
   The org is seeded with a CEO and the template's departments. The user
   immediately transitions to the Home Dashboard with a `● live` pulse and a
   transient toast: *"Startup Organization is now live. 5 departments
   standing by."* The org is **idle but alive** — no investigation yet.

### 3.3 Data Exchanged

| Step | Client → Server | Server → Client |
|------|-----------------|-----------------|
| Pick Template | — (local) | Template catalog (cached) |
| Configure DNA | — (local) | — |
| Launch | `POST /orgs { template, name, mission, dna }` | `org.{id}`, initial `departments[]`, authless WS stream |
| Land | `WS subscribe org.{id}.*` | `org.{id}.heartbeat` (2s) |

### 3.4 Failure Modes

- Template catalog unreachable → fall back to bundled defaults; banner
  `Using default templates (offline)`.
- `POST /orgs` fails → inline error on the Launch button; no partial org is
  created (the server is transactional — see `13-database-design.md`).
- DNA out of range → slider clamps with a haptic tick.

---

## 4. Flow 2 — Submit Investigation Goal

**Goal:** The user gives the org a goal and watches it investigate live,
steering as needed, then receives a report.
**Outcome:** A delivered report with recommendation, dissent, and evidence.

This is the flagship flow — the one the entire product is built around.

### 4.1 Flow Diagram

```
 ┌──────────┐   ┌───────────┐   ┌────────────┐   ┌────────────┐
 │  Home     │   │ Goal      │   │  Planner  │   │ Gathering  │
 │  [+ New   │──►│ Composer  │──►│  decomposes│──►│  phase     │
 │  Invest.] │   │ "Should I │   │  into dept │   │  (live)    │
 └──────────┘   │  build…?"  │   │  tasks     │   └─────┬──────┘
                └───────────┘   └────────────┘         │
                                                       │ user may:
                                                       │  ⏸ Pause
                                                       │  ✎ Redirect
                                                       │  ? Ask
                                                       ▼
                                          ┌─────────────────────────┐
                                          │   Debate phase          │
                                          │   meetings · challenges │
                                          └────────────┬────────────┘
                                                       ▼
                                          ┌─────────────────────────┐
                                          │   Voting phase          │
                                          │   tally → decision      │
                                          └────────────┬────────────┘
                                                       ▼
                                          ┌─────────────────────────┐
                                          │   Report delivered      │
                                          │   summary · dissent · ev│
                                          └────────────┬────────────┘
                                                       ▼
                                          ┌─────────────────────────┐
                                          │   Next Steps → new Flow 2│
                                          └─────────────────────────┘
```

### 4.2 Step-by-Step

1. **Goal Composer.** From Home, tap `+ New Investigation`. A bottom-sheet
   composer appears:

   ```
   ┌─────────────────────────────────────┐
   │  New Investigation                   │
   │  ┌─────────────────────────────────┐│
   │  │ Should I build an AI healthcare  ││
   │  │ startup?                         ││
   │  └─────────────────────────────────┘│
   │  Depth:  ◉ Standard  ○ Deep  ○ Quick │
   │  [ Launch Investigation ]            │
   └─────────────────────────────────────┘
   ```
   The composer accepts a free-text goal. There is **no agent picker** — the
   org decides who participates (see `18-planner-design.md`).

2. **Planner Decomposition.** On submit, the client calls
   `POST /orgs/{id}/investigations`. The server returns an
   `investigation.{id}` and a plan. The user is transitioned to the
   Investigation View (see `22-mobile-ux.md` §8) where the plan is rendered as
   department task chips:

   ```
   Research   ▸ Review 3 papers on HIPAA+LLM
   Marketing  ▸ Survey 50 users on B2B demand
   Engineering▸ Spike: 6mo to MVP feasibility
   Finance    ▸ Forecast 12mo runway @ $40k/mo
   Legal      ▸ Review HIPAA compliance
   CEO        ▸ Coordinate, schedule review meeting
   ```

3. **Gathering (live).** The user watches the phase indicator move
   `◐ Gathering`. The evidence feed streams: `Res + paper`, `Eng ✗ sample
   size`, `Res ↺ fetching n=30`, `Mkt + survey 42/50`. Department status rows
   update their activity text in real time.

4. **Steering (anytime).** Three affordances are always available during
   Gathering and Debate:

   - **⏸ Pause** → `POST .../investigations/{id}/pause`. In-flight tasks
     drain; the phase indicator shows `⏸ PAUSED`. A `Resume` affordance
     replaces `Pause`.
   - **✎ Redirect** → opens a small composer for a new constraint
     ("Focus only on B2B"). The planner re-plans; a diff of the old vs new
     plan is shown briefly. See `18-planner-design.md` § re-plan.
   - **? Ask a Question** → the question is routed to the CEO, who may answer
     directly or convene a meeting. The answer surfaces as a card in the
     evidence feed tagged `[USER Q → CEO]`.

5. **Debate.** The phase indicator advances to `◑ Debate`. Meetings are
     scheduled (see `20-meeting-engine.md`); the user can tap into any live
     meeting (Flow 4). Evidence is challenged and defended; the evidence feed
     shows `✗` (challenged), `↺` (rebutted), `✓` (survived peer review).

6. **Voting.** Phase indicator → `● Voting`. The CEO calls a vote on the
     recommendation. The Investigation View surfaces a live `VoteTally` even
     if the user is not in a meeting. When the threshold (see
     `09-constitution-engine.md`) is crossed, the tally bar turns green.

7. **Report Delivered.** Phase indicator → `◉ Report`. A push notification
   fires (`investigation.{id}.completed`). The user lands on the Report View
   (see `22-mobile-ux.md` §10): executive summary, recommendation, confidence,
   dissenting opinions, supporting evidence, risks, opportunities, next
   steps, and a link to the full discussion.

8. **Next Steps.** Each next step is tappable and pre-fills a new Goal
   Composer, chaining a new Flow 2. The institutional memory links the two
   investigations (see `06-memory-system.md`).

### 4.3 Data Exchanged

| Step | Client → Server | Server → Client |
|------|-----------------|-----------------|
| Goal Composer | `POST /orgs/{id}/investigations { goal, depth }` | `investigation.{id}`, initial plan |
| Gathering | `WS subscribe investigation.{id}.*` | `.phase.changed`, `.evidence.added`, `.dept.activity` |
| Pause | `POST .../investigations/{id}/pause` | `.phase.changed → PAUSED` |
| Redirect | `POST .../investigations/{id}/redirect { constraint }` | new plan, `.plan.replaced` |
| Ask | `POST .../investigations/{id}/questions { text }` | `.evidence.added [USER Q → CEO]` |
| Report | `GET /investigations/{id}/report` | full report payload |

### 4.4 Notification Triggers

- `investigation.{id}.phase.changed` → silent update if in-app, push if
  backgrounded and phase is `Voting` or `Report`.
- `investigation.{id}.completed` → push with the recommendation headline.
- `investigation.{id}.stalled` → push *"Investigation stalled — needs your
  input"* if the CEO escalates a question to the user.

---

## 5. Flow 3 — Explore Organization

**Goal:** The user wanders the org to understand *who is doing what, why, and
how well*.
**Outcome:** The user has traced a recommendation back to its workers,
memory, and evidence.

### 5.1 Flow Diagram

```
 ┌──────────┐   ┌────────────┐   ┌──────────────┐   ┌──────────────┐
 │  Home     │   │ Department │   │  Worker      │   │  Memory /     │
 │  Status   │──►│  Detail    │──►│  Detail      │──►│  Evidence     │
 │  tab      │   │  workers · │   │  trust · dec │   │  trace        │
 └──────────┘   │  tasks ·   │   │  memory · rel│   └──────┬───────┘
                │  evidence  │   └──────────────┘          │
                └─────┬──────┘                              │
                      │                                     │
                      ▼                                     ▼
                ┌──────────────┐                   ┌────────────────┐
                │  Memory      │                   │  Evidence →    │
                │  institutional│                  │  meeting that  │
                │  entries     │                   │  challenged it │
                └──────────────┘                   └────────────────┘
```

### 5.2 Step-by-Step

1. **Home → Department.** Tap any `DepartmentCard` on the Status tab. A
   shared-element transition opens the Department Detail
   (`22-mobile-ux.md` §6).

2. **Department → Worker.** Tap a worker row. The Worker Detail
   (`22-mobile-ux.md` §7) shows trust, accuracy, recent decisions, authored
   memory, and relationships.

3. **Worker → Memory.** Tap "Memory Authored" → a paginated list of
   institutional memory entries. Each entry has a type badge
   (`decision`/`failure`/`policy`/`strategy`) and links to the investigation
   that produced it. See `06-memory-system.md`.

4. **Department → Evidence.** From the Department Detail, tap "Evidence" →
   the evidence ledger. Each evidence row carries a peer-challenge status:
   `✓ survived`, `✗ rejected`, `↺ rebutted`. Tapping a challenged row jumps
   to the meeting where the challenge occurred (Flow 4).

5. **Evidence → Origin.** Each evidence item links to its source: a paper, a
   survey, a Caspian thread (Flow 5), or a forecast. The user can thus trace
   any recommendation back to its raw evidence in ≤ 4 taps.

### 5.3 The Trace Heuristic

A core UX invariant: **any recommendation is reachable to its evidence in four
taps or fewer.**

```
Report ──tap──► Meeting ──tap──► Worker ──tap──► Evidence ──tap──► Source
   1            2            3            4
```

If a trace exceeds four taps, the information architecture is wrong and the
screen is restructured. This invariant is the operational form of "Always
Transparent."

---

## 6. Flow 4 — Watch Meeting

**Goal:** The user observes a live debate, sees the vote, and sees the
decision.
**Outcome:** The user has witnessed a decision being made, with dissent
visible.

### 6.1 Flow Diagram

```
 ┌──────────┐   ┌──────────────┐   ┌──────────────┐   ┌──────────────┐
 │  Meetings │   │  Meeting     │   │  Debate      │   │  Vote tally  │
 │  tab      │──►│  View (live) │──►│  transcript  │──►│  fills live  │
 │  ● LIVE   │   │  participants│   │  ▶ speaking  │   │  threshold  │
 └──────────┘   └──────────────┘   └──────────────┘   └──────┬───────┘
                                                               ▼
                                                ┌──────────────────────────┐
                                                │  Decision card            │
                                                │  "DECISION: …" + haptic   │
                                                │  → links to report / mem  │
                                                └──────────────────────────┘
```

### 6.2 Step-by-Step

1. **Meetings Tab.** The Meetings tab lists scheduled, live, and archived
   meetings. Live meetings carry a `● LIVE` badge and an attendee count.

2. **Meeting View.** Tap a live meeting → Meeting View
   (`22-mobile-ux.md` §9). Participants are shown as `◉` (present) / `○`
   (absent). The transcript streams with a `▶` on the active speaker.

3. **Debate.** The user watches utterances arrive. A "Proposal on the
   table" card pins the current motion. The user may tap `Join as Observer`
   to register read-only presence (the meeting engine may surface this to
   participants as "1 observer present").

4. **Vote.** When the chair calls a vote, a `VoteTally` animates in. The
   threshold line (2/3 supermajority by default, configurable per org — see
   `09-constitution-engine.md`) is drawn on the bar. Votes arrive as
   `meeting.{id}.vote.cast` events.

5. **Decision.** When the threshold is crossed, the tally bar turns green, a
   heavy haptic fires, and a `DECISION: …` card slides in. The card links to:
   - the report this decision feeds (if part of an investigation),
   - the memory entry that will be written (see `06-memory-system.md`),
   - the dissenting opinions recorded (each dissenter's argument is
     preserved, not overruled).

### 6.3 Why This Flow Is the Heart of the Product

A meeting is the moment the organization **thinks socially** (contribution
#1, Society-of-Thought). It is the one flow that has no one-agent analogue
at all — a single agent cannot convene, debate, or vote with itself. The
mobile app makes this moment watchable.

---

## 7. Flow 5 — View External Communications

**Goal:** The user sees the org reaching real people through Caspian and sees
the responses folded back as evidence.
**Outcome:** The user understands that the org's evidence is not just web
scraping but active human outreach.

### 7.1 Flow Diagram

```
 ┌──────────┐   ┌──────────────┐   ┌──────────────┐   ┌──────────────┐
 │  Comms    │   │  Channel     │   │  Thread      │   │  Reply folded │
 │  tab      │──►│  filter list │──►│  outbound +  │──►│  into evidence│
 │  ✉✈#⌘☎   │   │  by channel  │   │  replies     │   │  ledger       │
 └──────────┘   └──────────────┘   └──────────────┘   └──────────────┘
```

### 7.2 Step-by-Step

1. **Comms Tab.** The Comms tab (`22-mobile-ux.md` §11) lists Caspian
   threads. Each row shows the originating department, the channel icon
   (✉ Email, ✈ Telegram, # Slack, ⌘ Discord, ☎ WhatsApp), the recipient,
   and a reply indicator.

2. **Channel Filter.** The user can filter by channel. This matters because
   different departments prefer different channels (Research → Email,
   Marketing → Telegram) — the filter makes the **division of communicative
   labor** visible.

3. **Thread View.** Tap a thread → outbound message + replies. Replies are
   shown individually for 1:1 threads, or as an aggregate ("18 replies · 12
   yes / 6 no") for broadcast threads (e.g., a Telegram cohort poll).

4. **Evidence Fold-back.** Each thread that produced usable evidence carries a
   `→ Evidence` link, jumping to the evidence row in the relevant
   investigation. The user sees that a professor's email reply became
   evidence item #4 in the healthcare investigation.

### 7.3 Escalation Affordance

On any thread, the user may tap `Escalate to Investigation`. This pre-fills a
Goal Composer with a draft derived from the thread ("Investigate: Prof. Lin
suggests HIPAA risk in LLM diagnostics"). See `12-caspian-integration.md`.

---

## 8. Flow 6 — View Evolution History

**Goal:** The user sees how the org has restructured itself over time and
why.
**Outcome:** The user understands that the org is self-modifying, not
static.

### 8.1 Flow Diagram

```
 ┌──────────┐   ┌──────────────┐   ┌──────────────┐   ┌──────────────┐
 │  Evo tab  │   │  Timeline    │   │  Change card │   │  Org chart   │
 │  ⟳       │──►│  hire/fire/  │──►│  reason +   │──►│  diff before │
 └──────────┘   │  promote/split│   │  triggering  │   │  → after     │
                └──────────────┘   │  signal      │   └──────────────┘
                                   └──────────────┘
```

### 8.2 Step-by-Step

1. **Evo Tab.** The Evolution tab (`22-mobile-ux.md` §12) lists org changes
   newest-first: `+ HIRE`, `↑ PROMOTE`, `− FIRE`, `⚡ SPLIT`, `⋈ MERGE`,
   `✎ POLICY`.

2. **Change Card.** Each card shows the **reason** — the performance signal
   that triggered the evolution engine. Examples:
   - `+ HIRE Sam Patel → Research` — reason: "Research overloaded (queue
     depth 14, 3 missed deadlines)."
   - `− FIRE Temp-Analyst (Finance)` — reason: "3 failed forecasts, trust
     dropped to 41%."
   - `⚡ SPLIT Engineering → Eng + DevOps` — reason: "Eng scope too broad,
     debate latency 2× org average."

3. **Org Chart Diff.** Tap a structural change (split/merge) → a before/after
   org chart diff. Added nodes are green, removed nodes red, rewired edges
   amber. See `07-evolution-engine.md`.

4. **Override (rare).** The user may `Override` an evolution event, which
   schedules a constitutional review meeting (Flow 4) on the question "Should
   this change stand?" The override does not silently revert — it routes
   the disagreement through the org's own governance. See
   `09-constitution-engine.md`.

### 8.3 Why This Flow Matters

This is the flow that proves the org is **alive and self-evolving**
(contribution #12). No other product shows an AI system restructuring itself
in response to its own performance. The user is not watching a tool — they
are watching an institution.

---

## 9. Flow 7 — Amend Constitution

**Goal:** The user proposes a change to the org's governing law and watches
the org debate and vote on it.
**Outcome:** The amendment is ratified or rejected, and the constitution is
updated (or not) accordingly.

### 9.1 Flow Diagram

```
 ┌──────────────┐   ┌──────────────┐   ┌──────────────┐   ┌──────────────┐
 │  Constitution │   │  Propose     │   │  Org debates  │   │  Vote        │
 │  View ✎ Amend│──►│  amendment   │──►│  (meeting)    │──►│  (unanimity  │
 └──────────────┘   │  text + type │   │  transcript   │   │   required) │
                    └──────────────┘   └──────────────┘   └──────┬───────┘
                                                                  │
                                              ┌───────────────────┼───────────┐
                                              ▼                               ▼
                                    ┌──────────────┐               ┌──────────────┐
                                    │  Ratified    │               │  Rejected    │
                                    │  A3 added    │               │  no change  │
                                    │  haptic      │               │  haptic     │
                                    └──────────────┘               └──────────────┘
```

### 9.2 Step-by-Step

1. **Constitution View.** From the drawer or the Home header, open the
   Constitution View (`22-mobile-ux.md` §13). Tap `✎ Amend`.

2. **Propose.** A composer appears:

   ```
   ┌─────────────────────────────────────┐
   │  Propose Amendment                   │
   │  Type: ◉ Principle ○ Rule ○ Value    │
   │  ┌─────────────────────────────────┐│
   │  │ Require sample size n≥30 for all ││
   │  │ user surveys.                   ││
   │  └─────────────────────────────────┘│
   │  Rationale: [ optional            ] │
   │  [ Submit for Debate ]               │
   └─────────────────────────────────────┘
   ```
   On submit, `POST /orgs/{id}/constitution/amendments` creates an amendment
   proposal and schedules a debate meeting.

3. **Debate.** The user is transitioned to a Meeting View (Flow 4) scoped to
   the amendment. The org debates the proposal as it would any motion — with
   the difference that **constitutional amendments require unanimity** (or
   whatever supermajority the org's own constitution specifies; see
   `09-constitution-engine.md`). This means a single dissenting department
   can block an amendment — a deliberate friction.

4. **Vote.** The `VoteTally` shows the unanimity threshold (all bars must
   fill). If any department votes against, the bar stops short, turns amber,
   and a `REJECTED` card appears with the dissenting department's argument.

5. **Ratify or Reject.**
   - **Ratified**: the constitution is updated; a memory entry of type
     `policy` is written (`06-memory-system.md`); a heavy haptic fires; the
     Constitution View shows the new amendment as `A3 ratified`.
   - **Rejected**: no change; the proposal is archived with the dissent; a
     light haptic fires. The user may revise and resubmit.

### 9.3 Why Amendment Is Hard

Constitutional amendment is intentionally harder than a normal decision. The
org's governing law should not be mutable by simple majority — that would
make the constitution meaningless. The unanimity default (configurable
upward, never downward) ensures the org's values are **stable** while still
being **evolvable**. This is contribution #11 (Constitutional
Organizations) made tangible.

---

## 10. Shared Interaction Patterns

### 10.1 The Goal Composer

The Goal Composer is the **only** place the user types free text to the org.
It recurs in Flow 1 (Mission), Flow 2 (Investigation goal, redirect,
question), Flow 5 (escalation draft), and Flow 7 (amendment text). It is a
bottom-sheet, not a full screen, so context is never lost.

### 10.2 The Phase Indicator

The `◐ ◑ ● ◉` strip recurs for investigations and meetings. It is the user's
single most reliable answer to *"where are we?"* See `25-state-machine.md`
for the underlying state machine.

### 10.3 The Trace Pill

On any conclusion-bearing card (report recommendation, meeting decision,
evidence row), a `→ trace` pill collapses the four-tap trace (§5.3) into a
single guided tour: the app animates through Meeting → Worker → Evidence →
Source, pausing 1.2s on each.

### 10.4 The Live Badge

`● LIVE` is a single component used on the Home header, the Meetings tab,
the Meeting View, the Investigation View, and the Caspian thread list. It
is the user's peripheral signal that *something is happening right now*.

### 10.5 Haptic Vocabulary

| Event | Haptic | Meaning |
|-------|--------|---------|
| Vote cast (individual) | light tick | A peer voted. |
| Decision reached | medium | The org decided. |
| Evolution event | heavy | The org restructured itself. |
| Amendment ratified | heavy + 200ms delay + heavy | The org changed its law. |
| Phase advance | none | Visual only — too frequent to buzz. |

Haptics are the user's awareness of the org's life when the phone is in
their pocket.

---

## 11. Notification Strategy

Notifications are scoped to **moments the user would regret missing**:

| Trigger | Channel | Copy |
|---------|---------|------|
| `investigation.{id}.completed` | push | *"Startup Org: BUILD (B2B). Confidence 82%."* |
| `meeting.{id}.decision.reached` | push (if bg) | *"Budget Review decided: hold burn $40k/mo."* |
| `evolution.{id}.change` | push | *"Startup Org hired Sam Patel (Research)."* |
| `investigation.{id}.stalled` | push | *"Investigation needs your input."* |
| `caspian.{id}.received` (high-signal) | silent in-app | row badge only |
| `org.{id}.health.changed` (drop > 10%) | push | *"Startup Org health dropped to 71%."* |

Low-signal events (activity changes, individual utterances, individual
votes) **never** push. The app is a window, not a fire alarm.

---

## 12. Flow-Level Failure & Recovery

| Flow | Failure | Recovery |
|------|---------|---------|
| 1 Create | Launch fails | Inline error; no partial org. |
| 2 Investigate | Planner fails to decompose | CEO asks the user to clarify; composer reopens with the goal pre-filled. |
| 2 Investigate | WS drops mid-investigation | Last-known state frozen with `RECONNECTING` toast; on reconnect, state reconciles from REST snapshot. |
| 3 Explore | Memory entry 404 | "This memory was archived" with link to archive. |
| 4 Meeting | Quorum not met | Meeting shows `AWAITING QUORUM 3/5`; auto-retry per `20-meeting-engine.md`. |
| 5 Comms | Caspian channel down | Thread shows `CHANNEL UNREACHABLE`; outbound queued, not dropped. |
| 6 Evolution | Override contested | Routes to a constitutional review meeting (Flow 4). |
| 7 Amend | Vote times out | Amendment auto-rejected as `EXPIRED`; user may resubmit. |

---

## 13. Cross-References

- Screen-level design for every view in these flows — `22-mobile-ux.md`
- System topology & service boundaries — `04-technical-architecture.md`
- Worker/department model — `05-agent-architecture.md`
- Institutional memory entries — `06-memory-system.md`
- Evolution events & overrides — `07-evolution-engine.md`
- Trust scores behind worker cards — `08-trust-engine.md`
- Constitution & amendment rules — `09-constitution-engine.md`
- Debate & voting mechanics — `10-debate-engine.md`
- Internal messaging & meetings — `11-communication-engine.md`
- Caspian external channels — `12-caspian-integration.md`
- Event taxonomy & WS protocol — `16-event-system.md`
- Prompt & goal parsing — `17-prompt-architecture.md`
- Planner decomposition & re-plan — `18-planner-design.md`
- Investigation orchestration & phases — `19-investigation-engine.md`
- Meeting scheduling & quorum — `20-meeting-engine.md`
- DNA parameter model & theming — `21-organizational-dna.md`
- REST/WS API surface — `24-api-design.md`
- State machine for phases — `25-state-machine.md`
