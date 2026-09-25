# 22 — Mobile UX

> The mobile app is **not a chat application**. It is a living organization
> dashboard. The user does not "talk to an AI" — the user **watches an
> organization think**.

This document specifies the mobile client for Veliora: framework, information
architecture, every screen, the component library, real-time event handling,
and DNA-driven theming. It is written so a Kotlin/Android engineer can build it
without further design input.

Cross-references: `04-technical-architecture.md` (system topology),
`05-agent-architecture.md` (worker/department model), `06-memory-system.md`,
`07-evolution-engine.md`, `08-trust-engine.md`, `09-constitution-engine.md`,
`10-debate-engine.md`, `11-communication-engine.md`,
`12-caspian-integration.md`, `13-database-design.md`, `16-event-system.md`,
`19-investigation-engine.md`, `20-meeting-engine.md`,
`21-organizational-dna.md`, `24-api-design.md`, `25-state-machine.md`.

---

## 1. Design Philosophy

### 1.1 The One Agent Test, Applied to Mobile

Every screen must answer: *why does this exist because there is an
organization, not a single agent?*

| Screen | One-Agent analogue | Why it survives the test |
|--------|--------------------|-------------------------|
| Home / Org Status | A "model status" spinner | Shows **multiple departments acting in parallel** with divergent activities — a single agent has nothing to parallelize. |
| Department Detail | A "model card" | A department is a **collective** with internal conversations, trust distribution, and relationships — not a single persona. |
| Investigation View | A "thinking…" indicator | Shows **live division of labor**: Research reading, Engineering implementing, Finance forecasting — simultaneously and observably. |
| Meeting View | A monologue stream | Shows **debate between peers**, votes, dissent — impossible with one agent. |
| Report View | A final answer | Embeds **dissenting opinions** and **evidence challenged by peers** — a single agent cannot dissent from itself. |
| Communication (Caspian) | A "web search" log | Shows **different departments reaching different people on different channels** — coordination, not retrieval. |
| Evolution View | A "model update" | Shows the org **restructuring itself** — hires, fires, promotions — emergent, not shipped. |

If a screen reduces to "one model, one stream," it is rejected.

### 1.2 Three Invariants

1. **Always alive.** No screen is ever static. Every view has a heartbeat —
   a pulsing activity indicator, a live counter, a streaming feed. A paused
   org is explicitly marked `PAUSED`; absence of motion is a state, not a bug.
2. **Always transparent.** Every conclusion is traceable to the department,
   worker, evidence, and meeting that produced it. Nothing is "the AI said so."
3. **Always actionable.** Every screen exposes at least one affordance:
   pause, redirect, ask, vote-along, escalate, or open the underlying thread.

### 1.3 What This Is Not

- Not a chat UI. There is no persistent "compose box" on the home screen.
  The user submits **goals**, not messages. Interaction is scoped to an
  investigation or a meeting.
- Not an agent builder. The user never creates, names, or prompts individual
  agents. See `05-agent-architecture.md`.
- Not a dashboard of metrics. Numbers exist only where they change behavior
  (trust, health, confidence). Vanity charts are banned.

---

## 2. Framework & Technology Stack

### 2.1 Stack

| Layer | Choice | Rationale |
|-------|--------|-----------|
| Framework | Kotlin + Jetpack Compose | Native Android, 60fps gesture and animation performance for live feeds. |
| Language | Kotlin | End-to-end type safety with the API contract in `24-api-design.md`. |
| Navigation | Jetpack Navigation Compose | Standard, mature, deep-link friendly. |
| State (server) | Ktor Client + StateFlow | WebSocket-aware cache, normalized by org/investigation/meeting keys. |
| State (realtime) | ViewModel + StateFlow (`collectAsState`) | Low-overhead store fed by a single WS multiplexer (§15). |
| Realtime transport | WebSocket (primary), SSE fallback | See `16-event-system.md` for the event taxonomy. |
| Styling | Material 3 + Compose Animation API | Theme tokens driven by org DNA (§16); 60fps shared-element transitions. |
| Icons | Material Icons | Consistent icon set. |
| Charts | Vico (Compose) | Only for trust-over-time and health-over-time sparklines. |
| Persistence | In-memory StateFlow | No account/session state is stored on-device; the server remains the source of truth for the current local instance. |
| Build | Android Studio (Giraffe+) + Gradle Kotlin DSL | Type-safe build configuration; signed APK / Play Store distribution. |

### 2.2 Why Not a Web View

The organization is **alive**. A web view cannot sustain 60fps streaming
transcripts, shared-element transitions between a department card and its
detail view, or push notifications that wake the app into a specific meeting.
Native is the floor, not the ceiling.

---

## 3. Information Architecture

### 3.1 Navigation Graph

```
                       ┌──────────────────────────┐
                       │      Org Selector         │
                       │   (list of user's orgs)    │
                       └─────────────┬────────────┘
                                     │ select
                                     ▼
        ┌──────────────────────── Home / Org Dashboard ─────────────────────┐
        │  Org header · Health · DNA · Constitution · Tabs:                 │
        │  [Status] [Investigations] [Meetings] [Comms] [Evolution]           │
        └───┬──────────┬──────────────┬────────────┬───────────┬────────────┘
            │          │              │            │           │
            ▼          ▼              ▼            ▼           ▼
       Department   Investigation   Meeting    Caspian     Evolution
        Detail        Detail         Live       Channel      Timeline
            │          │  │            │
            ▼          ▼  ▼            ▼
        Worker      Report  Evidence   Vote
        Detail       View   Feed        Tally
            │
            ▼
        Memory / Conversation / Trust / Relationships
```

### 3.2 Tab Bar (within an org)

Five tabs, fixed order. The tab bar is the spine of the app.

```
┌──────────────────────────────────────────────────────────┐
│  ● Status   ▣ Investigations   ◉ Meetings   ✉ Comms   ⟳ Evo │
└──────────────────────────────────────────────────────────┘
```

- `● Status` — the living dashboard (default).
- `▣ Investigations` — active + past investigations.
- `◉ Meetings` — scheduled, live, and archived meetings.
- `✉ Comms` — Caspian external channel feed.
- `⟳ Evo` — org self-modification timeline.

A badge on each tab shows the live count (e.g., `2` on Meetings = two meetings
in progress). The badge pulses while the count is changing.

---

## 4. Screen: Home / Organization Dashboard

### 4.1 Wireframe

```
┌─────────────────────────────────────┐
│ ☰  Veliora            ⚙  🔔 3    │  ← top bar: drawer, settings, alerts
├─────────────────────────────────────┤
│  ▼ Startup Organization             │  ← org selector (tap to switch)
│  Health 96%  ·  DNA: High Risk       │  ← health + DNA summary
│  ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━ │
│                                     │
│  DEPARTMENTS                  live ● │  ← pulsing dot = org is active
│  ┌─────────────────────────────────┐│
│  │ CEO            Coordinating   ▸ ││  ← tap → Department Detail
│  │ Research       Reading 15 papers▸││
│  │ Engineering    Implementing   ▸ ││
│  │ Finance        Forecasting    ▸ ││
│  │ Marketing      Surveying 42 u▸ ││
│  └─────────────────────────────────┘│
│                                     │
│  ACTIVE INVESTIGATIONS         9 ●  │
│  ┌─────────────────────────────────┐│
│  │ ◐ AI healthcare startup          ││  ← phase indicator (◐ = gathering)
│  │   Research ▸ Eng ▸ Finance       ││  ← dept involvement chips
│  │   68%  ·  4 meetings  ·  12 ev  ││  ← progress, meetings, evidence
│  ├─────────────────────────────────┤│
│  │ ◑ Series A pricing model         ││  ← (◑ = debating)
│  └─────────────────────────────────┘│
│                                     │
│  MEETINGS                     2 ●   │
│  ┌─────────────────────────────────┐│
│  │ ● LIVE  Budget Review  · 4/5    ││  ← live badge, attendees present
│  │   CEO, Fin, Eng, Mkt  · 12:04   ││  ← participants, elapsed
│  └─────────────────────────────────┘│
│                                     │
│  MESSAGES                    34 ●   │  ← internal + Caspian combined
│  ┌─────────────────────────────────┐│
│  │ Fin→Eng: "runway assumes 18mo"   ││
│  │ Res→Caspian: Prof. Lin replied  ││
│  └─────────────────────────────────┘│
│                                     │
│  [ + New Investigation ]            │  ← primary action
└─────────────────────────────────────┘
```

### 4.2 Component Tree

```
HomeScreen {
    TopBar(org = org, alerts = alerts)
    OrgHeader(org = org, health = health, dna = dna)
    TabBar(active = "status", badges = counts)
    Section(title = "Departments", live = org.isAlive) {
        DepartmentCard(dept = d, activity = d.currentActivity)  // ×N
    }
    Section(title = "Active Investigations", count = counts.investigations) {
        InvestigationCard(investigation = inv)                  // ×N
    }
    Section(title = "Meetings", count = counts.meetings) {
        MeetingCard(meeting = m, live = m.isLive)               // ×N
    }
    Section(title = "Messages", count = counts.messages) {
        MessagePreview(message = m)                             // ×N
    }
    PrimaryAction(label = "New Investigation", onClick = openGoalComposer)
}
```

### 4.3 Data Bindings

| Component | Query key | WS event subscription |
|-----------|-----------|----------------------|
| `OrgHeader` | `org.{id}.health`, `org.{id}.dna` | `org.health.changed`, `org.dna.changed` |
| `DepartmentCard` | `org.{id}.departments[]` | `dept.{id}.activity.changed` |
| `InvestigationCard` | `org.{id}.investigations.active` | `investigation.{id}.phase.changed`, `.progress` |
| `MeetingCard` | `org.{id}.meetings.live` | `meeting.{id}.started`, `.participant.joined` |
| `MessagePreview` | `org.{id}.messages.recent` | `message.{id}.sent`, `caspian.{id}.received` |

The home screen subscribes to the org-level event fan-out (see
`16-event-system.md`). Heartbeat: a `org.{id}.heartbeat` event every 2s drives
the `live ●` pulse. If 3 heartbeats are missed, the dot greys out and the
header reads `STALLED — reconnecting`.

---

## 5. Screen: Organization Selector

### 5.1 Wireframe

```
┌─────────────────────────────────────┐
│  Your Organizations          + New   │
├─────────────────────────────────────┤
│  ┌─────────────────────────────────┐│
│  │ ● Startup Organization    96%   ││  ← current (ring highlight)
│  │   9 investigations · 2 meetings ││
│  └─────────────────────────────────┘│
│  ┌─────────────────────────────────┐│
│  │   Investor Society        88%   ││
│  │   3 investigations · 0 meetings ││
│  └─────────────────────────────────┘│
│  ┌─────────────────────────────────┐│
│  │   Consumer Society        71%   ││
│  │   1 investigation  · 1 meeting  ││
│  └─────────────────────────────────┘│
│                                     │
│  + New Organization                 │
│  →  Template: Startup · Lab · Firm  │
└─────────────────────────────────────┘
```

The selector is reached from the drawer (☰) or by tapping the org name in the
header. Selecting an org swaps the entire tab stack and rehydrates all caches
under the new `org.{id}` namespace. Multiple orgs are first-class — see
contribution #10 in `02-research-contributions.md`.

---

## 6. Screen: Department Detail

### 6.1 Wireframe

```
┌─────────────────────────────────────┐
│ ◀  Research Division          ● live │
│    Trust 96%  ·  4 workers  ·  12 mem│
├─────────────────────────────────────┤
│  NOW                                │
│  Reading 15 papers on AI healthcare │
│  ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━ │
│                                     │
│  WORKERS                            │
│  ┌─────────────────────────────────┐│
│  │ ◉ Dr. Lin    Lead  T:97%  ▸     ││  ← trust, tap → Worker Detail
│  │   reading arxiv:2401.02914      ││
│  ├─────────────────────────────────┤│
│  │ ○ Sam Patel  Analyst T:88%  ▸  ││
│  │   surveying competitors         ││
│  └─────────────────────────────────┘│
│                                     │
│  CURRENT TASKS                      │
│  ▸ Review 3 papers on HIPAA+LLM     │  ◐ 60%
│  ▸ Cross-check Eng feasibility      │  ● done
│                                     │
│  MEMORY  (recent)                   │
│  ▸ "Q3 user survey favored B2B"     │  tap → Memory
│  ▸ "2024: competitor X raised $20M" │
│                                     │
│  CONVERSATIONS                      │
│  ▸ w/ Engineering — feasibility     │  12 msgs
│  ▸ w/ CEO — scope alignment         │  4 msgs
│                                     │
│  EVIDENCE  (12)                     │
│  ▸ [paper] arxiv:2401.02914  ✓peer  │  ✓ = challenged
│  ▸ [survey] n=42, p<.05       ✓peer │
│                                     │
│  RELATIONSHIPS                      │
│  CEO ──strong──▶ Research           │
│  Research ──tense──▶ Marketing       │  ← tension indicator
└─────────────────────────────────────┘
```

### 6.2 Sub-views (pushed, not tabbed)

- **Memory** — paginated institutional memory entries (see
  `06-memory-system.md`). Each entry: timestamp, authoring worker, type
  (decision/failure/policy/strategy), and the investigation that produced it.
- **Conversations** — internal threads between this department and others
  (see `11-communication-engine.md`). Read-only on mobile; reply is desktop-
  scoped to keep the user from impersonating a worker.
- **Evidence** — the evidence ledger for this department, with peer-challenge
  status (see `19-investigation-engine.md` § Evidence Challenge).
- **Relationships** — a force-directed mini-graph of trust edges between this
  department and the others. Edge thickness = trust weight; edge color =
  sentiment (green cooperative, amber tense, red blocked). See
  `08-trust-engine.md`.

### 6.3 Why This Survives the One Agent Test

A single agent has no peers to converse with, no trust distribution across
workers, no tense relationships, and no evidence that another part of itself
challenged. The department is a **collective**; the screen makes the
collective legible.

---

## 7. Screen: Worker Detail

```
┌─────────────────────────────────────┐
│ ◀  Dr. Lin · Research Lead          │
├─────────────────────────────────────┤
│  TRUST              97%  ▁▂▄▆▇▇▇    │  ← 30-day sparkline
│  ACCURACY           94% (12 calls)  │  ← historical decision accuracy
│  INFLUENCE          High            │
│                                     │
│  CURRENT                            │
│  Reading arxiv:2401.02914 (4 min)   │
│                                     │
│  RECENT DECISIONS                    │
│  ▸ "Reject Eng's sample-size claim" │  → meeting link
│  ▸ "Endorse B2B pivot"              │  → report link
│                                     │
│  MEMORY AUTHORED  (8)               │
│  ▸ policy: "Always request n≥30"    │
│  ▸ failure: "GPT-4 citation halluc" │
│                                     │
│  RELATIONSHIPS                      │
│  CEO      ──strong──▶ Lin            │
│  Lin      ──tense──▶ Mkt-Analyst    │
└─────────────────────────────────────┘
```

Workers are not editable personas. The user observes a worker's **earned
reputation** (see `08-trust-engine.md`) and the decisions it has authored.
There is no "edit worker" button — the Evolution Engine hires and fires
(see `07-evolution-engine.md`).

---

## 8. Screen: Investigation View

### 8.1 Wireframe

```
┌─────────────────────────────────────┐
│ ◀  Investigation            ⏸ Pause │  ← pause/redirect/ask affordance
├─────────────────────────────────────┤
│  Should we build an AI healthcare   │
│  startup?                           │
│                                     │
│  PHASE:  ◐ Gathering ──○ Debate ──○ │  ← phase indicator
│              ●          Vote    Rep │
│                                     │
│  PROGRESS  ━━━━━━━━━━━━ 68%         │
│  ELAPSED   12m  ·  ETA  6m          │
│                                     │
│  DEPARTMENT STATUS                  │
│  Research    ◐ Reading 15 papers    │
│  Engineering ● Implementing spike  │
│  Finance     ○ Waiting on Res       │
│  Marketing   ◐ Surveying 42 users  │
│  Legal       ○ Queued               │
│  CEO         ◯ Coordinating         │
│                                     │
│  EVIDENCE FEED                live ●│
│  ┌─────────────────────────────────┐│
│  │ 12:04 Res  + arxiv:2401.02914    ││  ← streaming, newest at bottom
│  │ 12:05 Eng  ✗ "sample size n=12" ││  ← challenge
│  │ 12:05 Res  ↺ "fetching n=30"    ││  ← response
│  │ 12:06 Mkt  + survey: 42/50 B2B  ││
│  └─────────────────────────────────┘│
│                                     │
│  MEETINGS  (4)                      │
│  ▸ ● LIVE Budget Review  4/5  12:04 │
│  ▸ ○ Scope Alignment    Tue 14:00  │
│                                     │
│  [ Ask a Question ]  [ Redirect ]   │
└─────────────────────────────────────┘
```

### 8.2 Phase Indicator

Maps 1:1 to the investigation state machine in `25-state-machine.md`:

```
   Gathering ──► Debate ──► Voting ──► Report
   ◐                ◑          ●          ◉
```

The filled fraction of the glyph reflects intra-phase progress. Tapping a
phase glyph scrolls the evidence feed to the first event of that phase.

### 8.3 Interaction Affordances

| Affordance | Effect | Backed by |
|------------|--------|-----------|
| `⏸ Pause` | Suspends new task dispatch; in-flight tasks drain. | `19-investigation-engine.md` § pause |
| `Ask a Question` | Opens a scoped composer; question is routed to the CEO, who may convene a meeting. | `11-communication-engine.md` |
| `Redirect` | User supplies a new constraint; planner re-plans (see `18-planner-design.md`). | `18-planner-design.md` |

These are the **only** ways the user intervenes. The user never addresses a
worker directly — the user addresses the **organization**, which routes the
input through its own chain of command.

---

## 9. Screen: Meeting View

### 9.1 Wireframe

```
┌─────────────────────────────────────┐
│ ◀  Meeting: Budget Review    ● LIVE │
│     Phase: Debate  ·  12:04 elapsed │
├─────────────────────────────────────┤
│  PARTICIPANTS                       │
│  ◉ CEO(moderator)  ◉ Finance  ◉ Eng │  ◉ = present, ○ = absent
│  ◉ Marketing      ○ Legal          │
│                                     │
│  ┌─────────────────────────────────┐│
│  │  ▶ Finance: "Runway is 18mo if  ││  ← speaking indicator ▶
│  │     we hold burn at $40k/mo"     ││
│  │  ▶ Engineering: "Spike suggests ││
│  │     6mo to MVP, not 4"          ││
│  │  ▶ Marketing: "B2B demand       ││
│  │     supports 12mo runway"       ││
│  │  ▶ CEO: "Finance, revise to 12" ││
│  │  ▶ Finance: typing…             ││  ← typing indicator
│  └─────────────────────────────────┘│
│                                     │
│  PROPOSAL ON THE TABLE              │
│  "Hold burn at $40k/mo, 12mo runway"│
│                                     │
│  VOTE  (live)                       │
│  For: 3   Against: 0   Abstain: 1   │
│  [████████████░░░░] 75%              │  ← fills as votes arrive
│                                     │
│  [ Join as Observer ]               │  ← read-only presence
└─────────────────────────────────────┘
```

### 9.2 Live Transcript Mechanics

The transcript is a streaming view backed by `meeting.{id}.utterance` events
(see `20-meeting-engine.md`). Each utterance card shows:

- Speaker (with `▶` while actively speaking, fading 400ms after stop).
- Department color stripe on the left edge (DNA-derived, §16).
- Timestamps collapsed to relative (`12:04`) until tapped.
- A "typing…" ghost card when a `meeting.{id}.typing` event is received.

The transcript auto-scrolls only while the user is at the bottom; scrolling
up pins the view and shows a `↓ jump to live` pill.

### 9.3 Vote Display

Votes stream in via `meeting.{id}.vote.cast`. The tally bar animates with
Compose Animation API shared transitions. A two-thirds supermajority threshold line is
drawn on the bar (see `09-constitution-engine.md` for per-org thresholds).
When the threshold is crossed, the bar turns green and a `DECISION: …` card
slides in below.

---

## 10. Screen: Report View

### 10.1 Wireframe

```
┌─────────────────────────────────────┐
│ ◀  Report  ·  AI healthcare startup │
│     Confidence 82%  ·  18m elapsed  │
├─────────────────────────────────────┤
│  EXECUTIVE SUMMARY                  │
│  Build, but B2B-first. 12mo runway… │
│                                     │
│  RECOMMENDATION        ✦ BUILD (B2B) │
│  Confidence            ████████░ 82% │
│                                     │
│  DISSENTING OPINIONS  (1)           │
│  ▸ Marketing: "D2C demand untested" │  tap → full argument
│                                     │
│  SUPPORTING EVIDENCE   (12)          │
│  ▸ [paper] arxiv:2401.02914  ✓peer  │
│  ▸ [survey] n=42 B2B p<.05   ✓peer  │
│  ▸ [forecast] 18mo @ $40k/mo        │
│                                     │
│  RISKS                              │
│  ▸ HIGH  Regulatory (HIPAA)         │
│  ▸ MED   Competition (X raised $20M)│
│                                     │
│  OPPORTUNITIES                      │
│  ▸ B2B pilot w/ Hospital Y          │
│                                     │
│  NEXT STEPS                         │
│  ▸ Spin up Legal compliance review  │  → new investigation
│  ▸ Draft 12mo financial plan        │  → new investigation
│                                     │
│  FULL DISCUSSION                    │
│  ▸ Open 4 meetings, 87 messages    │  → archive
└─────────────────────────────────────┘
```

### 10.2 The Dissent Surface

The report **leads with consensus but never hides dissent**. Each dissenting
opinion is a first-class card linking to the meeting where it was raised and
the worker who raised it. This is the mobile manifestation of contribution #7
(Evidence Challenge) and #5 (Internal Politics). A single agent cannot
dissent from itself; the dissent surface exists only because this is an
organization.

### 10.3 Actionable Next Steps

Every "next step" is tappable and pre-fills a new investigation goal composer.
The org can thus **chain investigations** — the output of one becomes the
input of the next, with institutional memory linking them (see
`06-memory-system.md`).

---

## 11. Screen: Communication (Caspian) View

### 11.1 Wireframe

```
┌─────────────────────────────────────┐
│ ◀  External Communications          │
├─────────────────────────────────────┤
│  FILTER  [All] [Email] [Telegram]   │
│          [Slack] [Discord] [WhatsApp]│
│                                     │
│  ┌─────────────────────────────────┐│
│  │ ✉ Res → Prof. Lin (Email)       ││  ← channel icon
│  │   "Can we discuss HIPAA+LLM?"    ││
│  │   ↩ "Yes, free Thursday."  2m   ││  ← reply indicator
│  ├─────────────────────────────────┤│
│  │ ✈ Mkt → Beta cohort (Telegram)  ││
│  │   "Would you pay $50/mo for…?"   ││
│  │   ↩ 18 replies  · 12 yes / 6 no ││  ← aggregate reply
│  ├─────────────────────────────────┤│
│  │ # Eng → #architecture (Slack)   ││
│  │   "Spike suggests 6mo to MVP"   ││
│  │   …  ·  awaiting reply          ││
│  └─────────────────────────────────┘│
│                                     │
│  Channel indicators:                │
│  ✉ Email  ✈ Telegram  # Slack       │
│  ⌘ Discord  ☎ WhatsApp              │
└─────────────────────────────────────┘
```

### 11.2 Why This Survives the One Agent Test

A single agent "searches the web." An organization **reaches different people
on different channels through different departments**. The Caspian view shows
the **division of communicative labor**: Research emails a professor,
Marketing polls a Telegram cohort, Engineering asks a Slack channel. Each
outbound message is tagged with its originating department; each reply is
folded back into the evidence ledger (see `12-caspian-integration.md`).

---

## 12. Screen: Evolution View

### 12.1 Wireframe

```
┌─────────────────────────────────────┐
│ ◀  Evolution History        last 30d│
├─────────────────────────────────────┤
│  ORG CHANGES                        │
│  ┌─────────────────────────────────┐│
│  │ + HIRE  Sam Patel → Research    ││  ← green, hire
│  │   reason: Research overloaded    ││  ← emergent trigger
│  │   2 days ago                     ││
│  ├─────────────────────────────────┤│
│  │ ↑ PROMOTE Dr. Lin → Research Lead││  ← amber, promotion
│  │   reason: 94% accuracy, 12 calls ││
│  │   5 days ago                     ││
│  ├─────────────────────────────────┤│
│  │ ⚡ SPLIT  Engineering → Eng + DevOps││  ← structural
│  │   reason: Eng scope too broad    ││
│  │   9 days ago                     ││
│  ├─────────────────────────────────┤│
│  │ − FIRE  Temp-Analyst (Finance)  ││  ← red, fire
│  │   reason: 3 failed forecasts     ││
│  │   12 days ago                    ││
│  └─────────────────────────────────┘│
│                                     │
│  ORG CHART  (then → now)            │
│  [before] ──▶ [after]               │  ← diff view
└─────────────────────────────────────┘
```

### 12.2 Why This Survives the One Agent Test

A single agent does not restructure itself, hire peers, or get promoted by
its own outputs. Evolution is the org **modifying its own topology** in
response to performance — contribution #12 and `07-evolution-engine.md`. The
view shows the *reason* for every change, because the change was *decided*
by the org, not configured by the user.

---

## 13. Screen: Constitution View

```
┌─────────────────────────────────────┐
│ ◀  Constitution    [ ✎ Amend ]       │
├─────────────────────────────────────┤
│  MISSION                            │
│  "Investigate and decide on ventures"│
│                                     │
│  PRINCIPLES                         │
│  ▸ Evidence over intuition          │
│  ▸ Dissent is required               │
│                                     │
│  CORE VALUES                        │
│  ▸ Accuracy · Transparency · Speed │
│                                     │
│  DECISION RULES                     │
│  ▸ Supermajority 2/3 for hires      │
│  ▸ Unanimity for constitutional edit│
│                                     │
│  AMENDMENTS  (2)                     │
│  ▸ A1: "Require n≥30 for surveys"   │  ratified 5d
│  ▸ A2: "Legal review mandatory"    │  debating ●
└─────────────────────────────────────┘
```

The `✎ Amend` affordance opens the amendment flow described in Flow 7 of
`23-ui-ux-flow.md`. The constitution affects every decision the org makes
(see `09-constitution-engine.md`); surfacing it on mobile makes the org's
governing law **inspectable and mutable by the user**.

---

## 14. Component Library

### 14.1 Primitive Components

| Component | Parameters | Purpose |
|-----------|------------|---------|
| `OrgHeader` | `org, health, dna` | Sticky header with health + DNA summary. |
| `DepartmentCard` | `dept, activity` | Row in the departments list. |
| `InvestigationCard` | `investigation` | Row with phase glyph + progress. |
| `MeetingCard` | `meeting, live` | Row with live badge + attendees. |
| `PhaseIndicator` | `phase, progress` | The ◐/◑/●/◉ phase strip. |
| `EvidenceRow` | `evidence, challenged` | Evidence item with peer-challenge mark. |
| `TrustBar` | `trust, history` | Sparkline + percentage. |
| `VoteTally` | `for, against, abstain, threshold` | Animated vote bar. |
| `TranscriptBubble` | `utterance, speaking` | Meeting utterance with speaker state. |
| `CaspianMessage` | `message, channel, replies` | External message with channel icon. |
| `EvolutionEvent` | `change, reason, when` | Org change row. |
| `RelationshipEdge` | `from, to, weight, sentiment` | Graph edge in mini relationship view. |
| `DnaChips` | `dna` | DNA parameter chips (Risk, Speed, …). |
| `LivePulse` | `alive` | The pulsing dot used across screens. |

### 14.2 The Even-Spacing Rule (mandatory)

Every spacing value in every screen, component, and layout **must be an even
number of dp** — 0, 2, 4, 6, 8, 10, 12, 16, 20, 24, 32, 48, 64, … Values that
divide by 2 keep the vertical rhythm aligned across cards, chips, and lists,
and prevent the half-pixel rendering that odd dp values cause on sub-pixel
density displays.

- Applies to: `padding`, `Spacer`/`Arrangement.spacedBy` gaps, `offset`,
  icon sizes, and any layout dimension (`height`, `width`, `size`).
- Corner radii and elevations follow the same rule (see `Spacing.kt`).
- The token set in `ui/theme/Spacing.kt` is the single source of truth:
  `xs=4, sm=8, smMd=12, md=16, mdLg=20, lg=24, xl=32, xxl=48, xxxl=64`.
  Prefer a token over a raw literal.
- Exceptions: font sizes (`sp`) and stroke widths are typography, not
  spacing, and are governed by the type scale (`22-mobile-ux.md §17`), not
  this rule.
- A new screen is not complete until a reviewer confirms every spacing value
  is even.

### 14.3 Composite Patterns

- **Shared-element transition**: tapping a `DepartmentCard` morphs it into the
  `DepartmentDetail` header (Compose Animation API shared transition). The org "opens up"
  rather than "navigates to."
- **Pull-to-refresh** is present but discouraged: data is live. Pulling shows
  a hint: *"This org is live — no refresh needed."*
- **Haptics**: light tick on vote cast, medium on decision reached, heavy on
  org evolution event. Haptics are the user's peripheral awareness of the
  org's life.

---

## 15. Real-time Event Handling

### 15.1 Single Multiplexer

All WebSocket traffic flows through one `EventMultiplexer` (singleton). It
demultiplexes by event namespace into ViewModel StateFlows:

```
   WS ──► EventMultiplexer ──┬─► orgStore        (org.{id}.*)
                             ├─► investigationStore (investigation.{id}.*)
                             ├─► meetingStore    (meeting.{id}.*)
                             ├─► caspianStore    (caspian.{id}.*)
                             └─► evolutionStore  (evolution.{id}.*)
```

### 15.2 Event → Screen Mapping

| Event | Screen effect | Source doc |
|-------|---------------|------------|
| `org.{id}.heartbeat` | `LivePulse` on home | `16-event-system.md` |
| `dept.{id}.activity.changed` | `DepartmentCard` activity text | `05-agent-architecture.md` |
| `investigation.{id}.phase.changed` | `PhaseIndicator` advances | `25-state-machine.md` |
| `investigation.{id}.evidence.added` | `EvidenceRow` prepended | `19-investigation-engine.md` |
| `meeting.{id}.utterance` | `TranscriptBubble` appended | `20-meeting-engine.md` |
| `meeting.{id}.vote.cast` | `VoteTally` animates | `10-debate-engine.md` |
| `meeting.{id}.decision.reached` | Decision card + haptic | `09-constitution-engine.md` |
| `caspian.{id}.received` | `CaspianMessage` reply indicator | `12-caspian-integration.md` |
| `evolution.{id}.change` | `EvolutionEvent` prepended + haptic | `07-evolution-engine.md` |
| `org.{id}.health.changed` | `OrgHeader` health updates | `08-trust-engine.md` |

### 15.3 Backpressure & Battery

The multiplexer coalesces `activity.changed` events at 500ms granularity to
avoid thrash on rapid activity switches. When the app is backgrounded, the WS
downgrades to a push-notification channel only (vote decisions, evolution
events, investigation completion). On foreground, the store rehydrates from
the REST snapshot endpoint (see `24-api-design.md`) and the WS resumes.

---

## 16. DNA-Driven Theming

Organizational DNA (see `21-organizational-dna.md`) does not only shape
behavior — it shapes the **look** of the org on screen.

| DNA parameter | Visual token |
|---------------|--------------||
| Risk | Accent hue warmth (cool blue → hot red as Risk rises). |
| Hierarchy | Card elevation (flat for low hierarchy, shadowed for high). |
| Transparency | Background opacity of evidence/meeting cards. |
| Speed | Animation duration scale (fast orgs animate quicker). |
| Creativity | Layout grid variance (strict grid → organic offsets). |

A high-Risk, high-Speed startup org thus *feels* different from a low-Risk,
high-Hierarchy law firm org — before the user reads a single word. The DNA
theme is computed once per org and injected into the Material 3 theme.

---

## 17. Accessibility & Performance

### 17.1 Accessibility

- All live regions use `Modifier.semantics { liveRegion = LiveRegionMode.Polite }` so TalkBack
  announces new utterances, votes, and evolution events without stealing focus.
- The `LivePulse` carries `contentDescription = "Organization active"` and
  switches to `"Organization paused"` — never relies on color alone.
- Every phase glyph has a text alternative (`Gathering`, `Debate`, `Voting`,
  `Report`).
- Minimum touch target 48×48dp; department/worker rows are 72dp tall.

### 17.2 Performance Budgets

| Metric | Budget |
|--------|--------|
| Home screen TTI (cold) | < 1.5s on mid-tier Android |
| Transcript scroll FPS | 60 fps with 200 utterances |
| WS event → screen paint | < 100ms p95 |
| Memory footprint (idle) | < 180 MB |
| Background wake (push → render) | < 800ms |

Transcript virtualization uses `LazyColumn` with item recycling keyed by
utterance id. Evidence feeds use `LazyColumn` windowing with a 50-item
look-ahead.

---

## 18. Offline & Error States

- **Offline**: the home screen renders the last cached state with a
  `OFFLINE — showing 12:04 state` banner. The `LivePulse` becomes a hollow
  ring. No optimistic actions are allowed offline (the org cannot be steered
  blindly).
- **WS disconnect**: exponential backoff (1s, 2s, 4s, …, 30s cap). After 3
  failures, a `RECONNECTING` toast appears; the home screen continues
  rendering the last known state with timestamps frozen.
- **Org stalled** (no heartbeat for 10s on a live WS): the header reads
  `ORG STALLED — engine may be down` and offers `Open Diagnostics` (a
  desktop-routed screen, out of mobile scope).

---

## 19. Cross-References

- System topology & service boundaries — `04-technical-architecture.md`
- Worker/department model — `05-agent-architecture.md`
- Institutional memory entries — `06-memory-system.md`
- Org self-modification events — `07-evolution-engine.md`
- Trust scores & reputation — `08-trust-engine.md`
- Constitution & amendments — `09-constitution-engine.md`
- Debate & voting mechanics — `10-debate-engine.md`
- Internal messaging & meetings — `11-communication-engine.md`
- Caspian external channels — `12-caspian-integration.md`
- Data model behind every card — `13-database-design.md`
- Event taxonomy & WS protocol — `16-event-system.md`
- Investigation orchestration — `19-investigation-engine.md`
- Meeting engine — `20-meeting-engine.md`
- DNA parameter model — `21-organizational-dna.md`
- REST/WS API surface — `24-api-design.md`
- State machine for phases — `25-state-machine.md`
- End-to-end user journeys — `23-ui-ux-flow.md`
