# 28 — MVP (Hackathon)

> The build plan for a 48-hour hackathon demo of Veliora. One organization
> template, five workers, one live investigation, one board report, one mobile
> app — enough to prove that an **organization** is the intelligence, not any
> single agent.

---

## 1. Goals

The hackathon demo must prove exactly one thesis:

> **An organization investigates, debates, and decides better than a single AI
> — and the user can watch it happen live.**

Everything in the MVP exists to make that thesis visible. If a feature does not
serve it, it is out of scope. See `01-core-philosophy.md` for the paradigm this
demo is selling.

### Success Criteria

| # | Criterion | How we prove it |
|---|-----------|----------------|
| 1 | The user creates an **organization**, not agents | One tap → "Startup Organization" is live |
| 2 | Investigation is **live and observable** | Mobile UI shows 5 departments working in real time |
| 3 | Reasoning is **social, not hidden** | A debate between departments is visible on screen |
| 4 | Evidence is **challenged**, not just collected | One department publicly criticizes another's evidence |
| 5 | The org **reaches the real world** | Real email + Telegram messages are sent via Caspian |
| 6 | The output is a **board report**, not an answer | Structured report with dissent, confidence, evidence |
| 7 | The org **feels alive** | Statuses change, messages flow, health meter moves |

---

## 2. Scope

### In Scope (Build Real)

- One organization template: **Startup**
- Five workers: **CEO, Research, Engineering, Finance, Marketing**
- Society-of-Thought debate (see `10-debate-engine.md`)
- Live Investigation with real-time status (see `19-investigation-engine.md`)
- Evidence Challenge between departments
- Caspian communication: **Email + Telegram** (see `12-caspian-integration.md`)
- Final board report
- Mobile UI showing live organization status (see `22-mobile-ux.md`)

### Out of Scope (Roadmap — see `29-future-roadmap.md`)

- Institutional memory beyond the current investigation
- Trust engine / weighted reputation
- Evolution engine (hiring, firing, restructuring)
- Constitution engine
- Organizational DNA parameter system
- Multiple organizations / meta-organization
- Additional Caspian channels (Slack, Discord, WhatsApp)
- Knowledge graph
- Custom organization templates

### The One Agent Test (Applied to the MVP)

Every MVP feature must fail the test — it must only make sense because there is
an **organization**, not a single agent.

| Feature | One Agent Test | Verdict |
|---------|----------------|---------|
| Live Investigation status | One agent would show "Thinking...". Five departments working concurrently is organizationally meaningful. | PASS |
| Society-of-Thought debate | One agent cannot debate with itself meaningfully. Conflict between departments with different objectives is the point. | PASS |
| Evidence Challenge | One agent critiquing its own evidence is theater. Cross-department criticism creates real peer review. | PASS |
| Caspian (multi-channel) | One agent could message people. But Marketing surveying users while Research emails professors is division of labor only an org justifies. | PASS |
| Board report with dissent | One agent has no dissenting opinions. Dissent requires separate stakeholders. | PASS |
| Mobile org dashboard | One agent needs a chat UI. An org needs a dashboard showing its living state. | PASS |

---

## 3. System Architecture

```
+-------------------------------------------------------------+
|                        Mobile App (Kotlin/Android)          |
|  Org Dashboard | Live Investigation | Debate View | Report |
+----------------------------+--------------------------------+
                             | WebSocket (status stream)
                             | REST (create investigation, fetch report)
+----------------------------v--------------------------------+
|                     FastAPI Backend (Python)               |
|                                                             |
|  +-------------------+      +------------------------+      |
|  | Investigation     |      | Real-time Hub          |      |
|  | Orchestrator      |----->| (WebSocket manager)    |      |
|  +---+---------------+      +-----+------------------+      |
|      |                            |                         |
|      | tasks                      | status events           |
|      v                            v                         |
|  +---+-------------------+  +-----+------------------+      |
|  | Redis Event Queue    |  | Redis Pub/Sub          |      |
|  | (task lists per dept)|  | (status channel)       |      |
|  +---+-------------------+  +------------------------+      |
|      |                                                     |
|      v                                                     |
|  +---+-----------------------------------------------+       |
|  | Worker Pool (5 workers, one per department)       |       |
|  |                                                   |       |
|  |  CEO       Research    Engineering   Finance  Mktg |       |
|  +---+---------------------+---------------------+---+       |
|      |                     |                     |           |
|      v                     v                     v           |
|  +---+---------------------+---------------------+---+       |
|  | Caspian SDK (Email + Telegram)                    |       |
|  +---------------------------------------------------+      |
|                                                             |
|  +---------------------------------------------------+      |
|  | PostgreSQL (investigations, messages, evidence,   |      |
|  | reports, org state)                               |      |
|  +---------------------------------------------------+      |
+-------------------------------------------------------------+
```

### Component Responsibilities

| Component | Responsibility | Tech |
|-----------|---------------|------|
| Mobile App | Display live org status, create investigations, view debate, read report | Kotlin + Jetpack Compose |
| FastAPI Backend | REST + WebSocket endpoints, investigation orchestration | Python 3.11, FastAPI |
| Investigation Orchestrator | Decompose question into department tasks, sequence phases | Python (asyncio) |
| Redis Event Queue | Distribute tasks to workers, buffer status events | Redis 7 |
| Worker Pool | Execute department tasks, emit status, collect evidence, debate | Python async workers |
| Caspian SDK | Send/receive Email + Telegram messages on behalf of workers | Caspian SDK |
| PostgreSQL | Persist all investigation state, messages, evidence, reports | PostgreSQL 15 |

See `04-technical-architecture.md` for the full system topology and
`16-event-system.md` for the event-driven design this MVP simplifies.

---

## 4. Tech Stack

| Layer | Choice | Why |
|-------|--------|-----|
| Backend language | Python 3.11 | Fast LLM SDK support, async, team familiarity |
| Web framework | FastAPI | Native async, WebSocket support, OpenAPI auto-docs |
| Database | PostgreSQL 15 | Relational, JSONB for flexible evidence, reliable |
| Cache / queue | Redis 7 | Pub/sub for status, lists for task queues, simple |
| Mobile | Kotlin + Jetpack Compose (Android Studio) | Native Android, fast iteration, WebSocket client |
| LLM provider | OpenAI API (gpt-4o) | Best reasoning quality for debate + synthesis |
| External comms | Caspian SDK | Pre-built Email + Telegram channels |
| Real-time | WebSocket (FastAPI) | Push status updates to mobile without polling |

### Why Not a Full Agent Framework?

We deliberately avoid CrewAI / AutoGen / LangGraph for the MVP. The
organization's orchestration — task decomposition, debate, evidence challenge,
report synthesis — is the **product**, not a dependency. Building it directly
on FastAPI + Redis keeps the org's behavior fully observable and debuggable.
See `03-competitive-analysis.md` for the reasoning.

---

## 5. Data Model (Simplified for MVP)

Six tables. The full model is in `13-database-design.md`; the MVP keeps only
what one investigation needs.

```sql
-- The organization (singleton for MVP, but modeled for future expansion)
CREATE TABLE organizations (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name          TEXT NOT NULL,           -- 'Startup Organization'
    template      TEXT NOT NULL,           -- 'startup'
    health        INT DEFAULT 100,         -- 0-100, shown on mobile
    created_at    TIMESTAMPTZ DEFAULT now()
);

-- A worker = a department in the org
CREATE TABLE workers (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    org_id         UUID REFERENCES organizations(id),
    role           TEXT NOT NULL,          -- 'ceo','research','engineering',...
    display_name   TEXT NOT NULL,          -- 'Research Division'
    status         TEXT DEFAULT 'idle',    -- 'idle','working','debating','done'
    current_action TEXT,                   -- 'Reading 3 papers on AI healthcare'
    updated_at     TIMESTAMPTZ DEFAULT now()
);

-- One investigation per demo
CREATE TABLE investigations (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    org_id       UUID REFERENCES organizations(id),
    question     TEXT NOT NULL,            -- 'Should I build an AI healthcare startup?'
    state        TEXT DEFAULT 'planning',  -- see state machine below
    created_at   TIMESTAMPTZ DEFAULT now(),
    completed_at TIMESTAMPTZ
);

-- Every status update, message, evidence chunk, debate turn
CREATE TABLE events (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    investigation_id UUID REFERENCES investigations(id),
    worker_id       UUID REFERENCES workers(id),
    kind            TEXT NOT NULL,         -- 'status','evidence','challenge','debate','message'
    payload         JSONB NOT NULL,        -- flexible per kind
    created_at      TIMESTAMPTZ DEFAULT now()
);

-- Evidence collected by a worker
CREATE TABLE evidence (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    investigation_id UUID REFERENCES investigations(id),
    worker_id       UUID REFERENCES workers(id),
    source          TEXT,                 -- 'web','caspian_email','caspian_telegram','internal'
    claim           TEXT NOT NULL,         -- 'AI healthcare market is $XB by 2028'
    confidence      REAL,                  -- 0.0-1.0
    challenged_by   UUID REFERENCES workers(id),
    challenge_note  TEXT,                  -- 'Sample size too small (n=12)'
    status          TEXT DEFAULT 'active', -- 'active','challenged','rejected','upheld'
    created_at      TIMESTAMPTZ DEFAULT now()
);

-- The final board report
CREATE TABLE reports (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    investigation_id UUID REFERENCES investigations(id),
    summary         TEXT,
    recommendation  TEXT,
    confidence      REAL,
    dissent         JSONB,                  -- array of {worker, opinion}
    evidence_refs   JSONB,                  -- array of evidence ids
    risks           JSONB,
    opportunities   JSONB,
    next_steps      JSONB,
    full_discussion TEXT,                   -- transcript of the debate
    created_at      TIMESTAMPTZ DEFAULT now()
);
```

### Investigation State Machine (MVP)

```
                  +-----------+
                  | planning  |   CEO decomposes question into tasks
                  +-----+-----+
                        |
                        v
                  +-----+-----+
                  | collecting|   Workers gather evidence (web + Caspian)
                  +-----+-----+
                        |
                        v
                  +-----+-----+
                  | challenging|  Workers criticize each other's evidence
                  +-----+-----+
                        |
                        v
                  +-----+-----+
                  | debating  |   Society-of-Thought debate meeting
                  +-----+-----+
                        |
                        v
                  +-----+-----+
                  | synthesizing| CEO writes board report
                  +-----+-----+
                        |
                        v
                  +-----+-----+
                  | delivered |   Report visible on mobile
                  +-----------+
```

See `25-state-machine.md` for the full state machine design.

---

## 6. Worker Design

Five workers. Each is an async Python coroutine that pulls tasks from its
Redis queue, emits status events, and writes evidence. They share a common
loop but have role-specific prompt templates (see `17-prompt-architecture.md`).

### Worker Roles

| Worker | Role | Objectives (what it optimizes for) | MVP Tools |
|--------|------|-----------------------------------|-----------|
| CEO | Coordinator | Balanced recommendation; coordinates others | Task dispatch, synthesis |
| Research | Evidence gatherer | Accuracy, thoroughness, novelty | Web search, Caspian Email |
| Engineering | Feasibility | Technical risk, buildability, cost of dev | Web search, internal reasoning |
| Finance | Viability | Cost, revenue, runway, ROI | Forecast model, Caspian Email |
| Marketing | Demand | User demand, market size, positioning | Caspian Telegram (user surveys) |

### Why These Objectives Matter

Each worker has a **different objective function**. This is not roleplay — it is
the source of genuine conflict:

- Research wants to be thorough (slows things down).
- Engineering wants to derisk (kills ambitious ideas).
- Finance wants ROI (rejects long-term bets).
- Marketing wants growth (pushes aggressive moves).
- CEO wants a balanced recommendation (mediates).

This conflict is what makes the debate and evidence challenge **real**. A
single agent with one objective function cannot reproduce it. See
`05-agent-architecture.md` for the full worker design.

### Worker Loop (Pseudocode)

```python
async def worker_loop(role: str, org_id: str, investigation_id: str):
    queue = f"tasks:{org_id}:{role}"
    while True:
        task = await redis.blpop(queue, timeout=30)
        if task is None:
            break
        task = json.loads(task)
        await emit_status(role, "working", task["action_label"])
        # Phase-specific behavior
        if task["phase"] == "collect":
            evidence = await collect_evidence(role, task, investigation_id)
            await store_evidence(investigation_id, role, evidence)
        elif task["phase"] == "challenge":
            await challenge_others_evidence(role, investigation_id)
        elif task["phase"] == "debate":
            await debate_turn(role, task, investigation_id)
        await emit_status(role, "idle", None)
```

---

## 7. Investigation Flow (Step by Step)

For the demo question: **"Should I build an AI healthcare startup?"**

```
T=0:00  User taps "New Investigation" → types question → submits
        └─> POST /investigations
            └─> Orchestrator creates investigation, sets state=planning

T=0:02  CEO decomposes the question into 5 department tasks:
        - Research:    "Assess AI healthcare market size, growth, competitors"
        - Engineering: "Evaluate technical feasibility (HIPAA, ML, EHR integration)"
        - Finance:     "Estimate 18-month cost, project revenue, compute runway"
        - Marketing:   "Survey potential users about willingness to pay"
        - CEO:         "Synthesize inputs into recommendation" (deferred to end)
        └─> Tasks pushed to Redis queues, state=collecting

T=0:04  All four non-CEO workers start concurrently:
        - Research:   status="Searching AI healthcare market reports"
        - Engineering: status="Evaluating HIPAA compliance requirements"
        - Finance:     status="Building 18-month cost model"
        - Marketing:  status="Drafting Telegram user survey"

T=0:08  Marketing sends a real Telegram survey to a demo user group via Caspian
        Research emails a (pre-seeded) healthcare AI expert via Caspian Email
        └─> status events stream to mobile via WebSocket

T=0:20  Workers finish collecting, submit evidence rows
        └─> state=challenging

T=0:22  Evidence Challenge phase:
        - Engineering challenges Research's market-size claim:
          "Source is a vendor report — sample size not disclosed"
        - Finance challenges Marketing's demand signal:
          "Survey is n=8 — not statistically significant"
        - Research defends / updates confidence
        └─> evidence.status updated to 'challenged' or 'upheld'

T=0:28  state=debating — Society-of-Thought debate meeting
        - CEO opens: "Research, what does the market say?"
        - Research presents upheld evidence
        - Engineering raises feasibility risk
        - Finance raises runway risk
        - Marketing argues demand is strong despite small sample
        - 3-4 rounds of back-and-forth
        └─> all turns stored as events, streamed to mobile

T=0:35  state=synthesizing — CEO writes the board report
        └─> draws on upheld evidence + debate transcript

T=0:38  state=delivered — report appears on mobile
        └─> user reads: summary, recommendation, confidence, dissent, evidence
```

See `19-investigation-engine.md` for the full investigation orchestration and
`20-meeting-engine.md` for the meeting/debate mechanics.

---

## 8. Society-of-Thought Debate (MVP Implementation)

The MVP debate is a **structured meeting** with visible turns, not free-form
chat. This keeps it watchable on mobile and bounded in time.

### Debate Format

```
Round 1: Opening statements (each worker, 1 turn)
Round 2: Cross-examination (CEO asks targeted questions)
Round 3: Rebuttal (workers respond to challenges)
Round 4: Closing positions (each worker states final stance)
Final:   CEO synthesizes
```

### Debate Prompt Structure (Simplified)

```python
DEBATE_PROMPT = """
You are the {role} of a startup organization debating the question:
"{question}"

Your objective: {objective}
Your stance based on evidence you collected: {your_evidence}
Evidence challenged by others: {challenges_to_you}
Other workers' statements this round: {other_turns}

State your position for this round. Be specific. Cite evidence.
Disagree openly if another worker's claim is unsupported.
"""
```

### Why This Passes the One Agent Test

A single agent asked to "debate with itself" produces no real conflict — it
shares one objective function and one set of beliefs. Here, each worker has
**different evidence, different objectives, and different confidence levels**,
so disagreement is emergent, not performed. See `10-debate-engine.md`.

---

## 9. Evidence Challenge (MVP Implementation)

After collection, before debate, workers enter a **challenge phase** where
each worker reviews others' evidence and may file a challenge.

### Challenge Flow

```
For each worker W:
  For each evidence E collected by another worker:
    If W believes E is weak (prompted to evaluate):
      Create challenge: E.challenged_by = W, E.challenge_note = reason
      E.status = 'challenged'

Owner of E may:
  - Uphold:  provide stronger support → E.status = 'upheld'
  - Concede: accept the challenge → E.status = 'rejected'
```

### Challenge Prompt (Simplified)

```python
CHALLENGE_PROMPT = """
You are the {role} reviewing evidence collected by {other_role} for the
question: "{question}"

Evidence claim: {claim}
Source: {source}
Confidence stated: {confidence}

Is this evidence strong enough to rely on for a go/no-go decision?
If not, explain the weakness specifically (sample size, bias, relevance,
recency). If it is strong, say "no challenge".
"""
```

### Demo-Visible Output

The mobile app shows a "Evidence Challenge" view:

```
EVIDENCE CHALLENGE
─────────────────────────────────────────
Research: "AI healthcare market $44B by 2028"
  └─ Challenged by Engineering: "Vendor report, no sample size"
     └─ Research upheld: "Corroborated by 2 peer-reviewed studies"
        Status: UPHELD

Marketing: "78% of surveyed users would pay for AI triage"
  └─ Challenged by Finance: "n=8, not significant"
     └─ Marketing conceded: "Agreed, downgrading confidence to 0.55"
        Status: REJECTED
```

This is the heart of the demo — it shows the organization **self-correcting**
through internal peer review. See `02-research-contributions.md` (Contribution
#7, Evidence Challenge).

---

## 10. Caspian Integration (Email + Telegram)

The MVP integrates two Caspian channels to prove the organization reaches the
real world. See `12-caspian-integration.md` for the full channel design.

### Channel Assignments

| Worker | Channel | Recipient | Purpose |
|--------|---------|----------|---------|
| Research | Email | Pre-seeded healthcare AI expert | Validate market assumptions |
| Finance | Email | Pre-seeded accountant/VC contact | Sanity-check cost model |
| Marketing | Telegram | Demo user group (3-5 people) | Survey willingness to pay |

### Sending (Real)

Messages are sent for real via the Caspian SDK:

```python
async def caspian_send(channel: str, to: str, body: str):
    msg = await caspian.messages.create(
        channel=channel,       # 'email' or 'telegram'
        to=to,
        body=body,
    )
    return msg.id
```

### Receiving (Simulated for the Demo)

For the hackathon, replies from external people are **simulated** with
pre-scripted responses triggered after a delay, to keep the demo deterministic.
In production, Caspian webhooks would deliver real replies. See "What to Fake"
below.

```python
async def simulate_reply(channel: str, to: str, delay: int = 12):
    await asyncio.sleep(delay)
    canned = SCRIPTED_REPLIES[(channel, to)]
    await caspian.messages.ingest(channel=channel, from_=to, body=canned)
```

### Why Two Channels (Not One)

One channel could be dismissed as "the agent sent an email." Two channels used
by **different departments for different purposes** makes the division of labor
visible — Marketing surveys users on Telegram while Research emails experts.
That division is organizational, not agentic.

---

## 11. Live Investigation Status (WebSocket)

The mobile app subscribes to a WebSocket per investigation and receives status
events in real time. No polling.

### Event Stream Format

```json
{
  "type": "worker.status",
  "worker": "research",
  "status": "working",
  "action": "Reading 3 papers on AI healthcare regulation",
  "investigation_id": "...",
  "timestamp": "2026-08-05T16:53:00Z"
}
```

```json
{
  "type": "evidence.challenged",
  "evidence_id": "...",
  "claim": "AI healthcare market $44B by 2028",
  "challenged_by": "engineering",
  "challenge_note": "Vendor report, no sample size",
  "timestamp": "..."
}
```

```json
{
  "type": "debate.turn",
  "worker": "marketing",
  "round": 2,
  "text": "Demand is strong — even at n=8, 78% willingness to pay is a signal...",
  "timestamp": "..."
}
```

### Backend WebSocket Endpoint

```python
@app.websocket("/ws/investigations/{inv_id}")
async def investigation_ws(ws: WebSocket, inv_id: str):
    await ws.accept()
    async for event in redis_pubsub(f"events:{inv_id}"):
        await ws.send_text(event)
```

See `24-api-design.md` for the full API surface.

---

## 12. Final Board Report

The CEO synthesizes the board report after the debate. The report is the
**deliverable** — not a chat answer.

### Report Structure (Rendered on Mobile)

```
╔══════════════════════════════════════════════════════════╗
║  BOARD REPORT                                            ║
║  Should I build an AI healthcare startup?                ║
╠══════════════════════════════════════════════════════════╣
║  RECOMMENDATION:  Proceed with conditions                ║
║  CONFIDENCE:      0.68                                    ║
║                                                          ║
║  EXECUTIVE SUMMARY                                        ║
║  The market is large and growing, but regulatory and     ║
║  technical risk is high. Recommend a 90-day validation   ║
║  phase before full commitment...                          ║
║                                                          ║
║  DISSENTING OPINIONS                                      ║
║  • Engineering: "HIPAA compliance will cost 4-6 months.  ║
║    Start with a non-diagnostic wedge."                   ║
║  • Finance: "Runway is thin. Raise before building."     ║
║                                                          ║
║  KEY EVIDENCE (upheld)                                    ║
║  • Market $44B by 2028 (Research, upheld after challenge)║
║  • 78% willingness to pay, n=8 (Marketing, rejected)      ║
║  • HIPAA dev cost $180-260k (Engineering)                ║
║                                                          ║
║  RISKS                                                   ║
║  • Regulatory uncertainty (FDA SaMD classification)       ║
║  • Long sales cycles to health systems                    ║
║                                                          ║
║  NEXT STEPS                                              ║
║  1. Run 90-day validation with 3 design partners         ║
║  2. Engage FDA pre-submission Q&A                        ║
║  3. Raise $1.5M seed before build                         ║
║                                                          ║
║  [View Full Discussion →]                                 ║
╚══════════════════════════════════════════════════════════╝
```

### Synthesis Prompt (Simplified)

```python
REPORT_PROMPT = """
You are the CEO writing the final board report for:
"{question}"

Upheld evidence: {upheld_evidence}
Rejected evidence: {rejected_evidence}
Debate transcript: {debate_transcript}
Each worker's final stance: {final_stances}

Produce a structured board report:
- Recommendation (proceed / proceed with conditions / do not proceed)
- Confidence (0.0-1.0)
- Executive summary (3-5 sentences)
- Dissenting opinions (per worker who disagreed)
- Key evidence (upheld only, with source)
- Risks, Opportunities, Next steps
"""
```

---

## 13. Mobile UI (Kotlin/Android)

The mobile app is **not a chat interface**. It is a living organization
dashboard. See `22-mobile-ux.md` for the full design.

### Screens (MVP)

```
+------------------+   +------------------+   +------------------+
|  ORG DASHBOARD   |   | INVESTIGATION    |   |  DEBATE VIEW     |
|                  |   |   LIVE           |   |                  |
|  Startup Org     |   |  [progress bar]  |   | Round 2 of 4     |
|  Health: 96%     |   |                  |   |                  |
|                  |   |  CEO      synth  |   | Marketing:       |
|  CEO     idle    |   |  Research reading|   | "Demand is..."   |
|  Research working|   |  Eng.    coding  |   |                  |
|  Eng.    working |   |  Finance modeling|   | Engineering:     |
|  Finance working |   |  Mktg   surveying|   | "But HIPAA..."   |
|  Mktg.   working |   |                  |   |                  |
|                  |   |  [Evidence tab]  |   | [Next round →]   |
|  1 investigation |   |  [Debate tab]    |   |                  |
|  9 messages      |   |  [Report tab]   |   |                  |
+------------------+   +------------------+   +------------------+

+------------------+   +------------------+
| EVIDENCE CHAL.   |   |  BOARD REPORT    |
|                  |   |                  |
| Research: $44B   |   |  RECOMMENDATION   |
|  └ Eng: no n    |   |  Proceed w/ cond. |
|     UPHELD      |   |  Confidence 0.68  |
|                  |   |                  |
| Mktg: 78% WTP   |   |  [Dissent ▼]      |
|  └ Fin: n=8     |   |  [Evidence ▼]     |
|     REJECTED    |   |  [Risks ▼]       |
|                  |   |  [Next steps ▼]  |
+------------------+   +------------------+
```

### Real-Time Updates

The dashboard subscribes to the org-level WebSocket and re-renders worker
status rows as events arrive. No pull-to-refresh needed.

---

## 14. 48-Hour Build Timeline

Hour-by-hour breakdown. Two engineers assumed (E1: backend, E2: full-stack
mobile). A third person (E3) joins for Caspian + demo prep.

| Hour | Phase | E1 (Backend) | E2 (Mobile) | E3 (Caspian/Demo) |
|------|-------|--------------|-------------|-------------------|
| 0-2 | Setup | Repo scaffold, Docker compose (pg+redis), FastAPI skeleton | Android Studio project scaffold, navigation | Read Caspian SDK docs, get API keys |
| 2-4 | Data layer | DB schema, migrations, seed org + 5 workers | Design system, base components | Script demo recipient contacts (email + Telegram) |
| 4-7 | Worker framework | Redis queue, worker loop, status emit | Org dashboard screen (static) | Draft demo script |
| 7-10 | Workers | Research + Engineering workers (web search) | Live status binding (WebSocket) | Set up Telegram bot + test email send |
| 10-13 | Workers | Finance + Marketing workers | Investigation live screen | Seed canned Caspian replies |
| 13-16 | Orchestration | Investigation orchestrator, state machine | Debate view screen | Test end-to-end Caspian send |
| 16-19 | Evidence | Evidence storage + challenge phase logic | Evidence challenge screen | Rehearse demo flow (dry run) |
| 19-22 | Debate | Debate meeting engine, turn management | Debate real-time rendering | Polish demo script |
| 22-25 | Caspian | Wire Caspian send + simulated receive | Caspian message view (optional) | Verify all canned replies trigger |
| 25-28 | Report | CEO synthesis, report storage | Board report screen | Buffer |
| 28-31 | Integration | End-to-end wiring, error handling | End-to-end wiring | First full dry run |
| 31-34 | Polish | Latency tuning, retry logic | Animations, transitions | Fix demo script issues |
| 34-37 | Demo data | Seed a clean investigation for demo | Demo mode toggle | Record backup video |
| 37-40 | Rehearsal | Bug fixing | Bug fixing | Full timed rehearsal |
| 40-44 | Buffer | Bug fixing | Bug fixing | Buffer |
| 44-46 | Final prep | Deploy to demo environment | Install on demo phone | Final checklist |
| 46-48 | Demo | Live demo to judges | — | — |

### Critical Path

```
DB + Queue ──> Worker loop ──> Orchestrator ──> Debate engine ──> Report synth
                                                            │
                                                            v
                 Mobile live status ──> Debate view ──> Report view
```

The debate engine is the latest "must work" piece — everything before it can
be simplified, but the debate must be visible and real.

---

## 15. What to Fake vs What to Build Real

Honest scoping is what makes a hackathon demo survive scrutiny. We fake the
**non-essential variability** and build real the **organizational behavior**.

### Build Real (The Org Behavior)

| Built real | Why it must be real |
|------------|---------------------|
| Investigation orchestration | This is the product |
| Worker task decomposition | Proves the org plans, not the user |
| Concurrent worker execution | Proves division of labor |
| Status event streaming | Proves the org is alive and observable |
| Evidence collection + storage | Proves evidence-based reasoning |
| Evidence challenge logic | Proves peer review (core thesis) |
| Society-of-Thought debate | Proves social reasoning (core thesis) |
| Board report synthesis | Proves org-level decision |
| Mobile real-time UI | Proves the UX paradigm |
| Caspian message **sending** | Proves real-world reach |

### Fake (The Variability)

| Faked | How | Why it's OK to fake |
|------|-----|---------------------|
| Caspian **replies** | Pre-scripted, triggered after delay | Replies depend on humans; can't be deterministic in a live demo |
| Web search depth | Limited to 3-5 hardcoded sources per topic | Full search is slow and non-deterministic; depth isn't the thesis |
| LLM determinism | Temperature 0.2, cached where possible | Keeps the demo on-script without faking the reasoning |
| Org health meter | Computed from a simple formula | Full trust engine is roadmap |
| Worker "current action" text | Templated from task labels | The action is real; the label is cosmetic |
| Multiple investigations | One investigation per demo | Multi-tenancy is roadmap |

### The Line We Will Not Cross

We will **not** pre-script the debate, the evidence challenge, or the report.
Those must be generated live by the LLM from real evidence, because they are
the thesis. If a judge asks "is this real?", the answer for those three is
**yes**. For Caspian replies and web depth, we disclose the simulation.

---

## 16. Build Checklist

### Backend

- [ ] Docker compose: PostgreSQL 15, Redis 7
- [ ] FastAPI app with CORS + WebSocket
- [ ] DB schema (6 tables) + migrations
- [ ] Seed script: 1 org, 5 workers
- [ ] Redis task queues per worker role
- [ ] Redis pub/sub for status events
- [ ] Worker loop (async, 5 workers)
- [ ] Investigation orchestrator + state machine
- [ ] Research worker (web search → evidence)
- [ ] Engineering worker (feasibility → evidence)
- [ ] Finance worker (cost model → evidence)
- [ ] Marketing worker (survey design → evidence)
- [ ] CEO worker (synthesis only)
- [ ] Evidence challenge phase
- [ ] Debate meeting engine (4 rounds)
- [ ] Board report synthesis
- [ ] REST: POST /investigations, GET /reports/:id
- [ ] WebSocket: /ws/investigations/:id
- [ ] Caspian send (email + telegram)
- [ ] Simulated Caspian reply ingest

### Mobile

- [ ] Android Studio project + Jetpack Navigation (5 screens)
- [ ] Org dashboard (live worker statuses)
- [ ] Investigation live screen (progress + worker cards)
- [ ] Debate view (rounds + turns, real-time)
- [ ] Evidence challenge view
- [ ] Board report view
- [ ] WebSocket client + reconnect
- [ ] Demo mode toggle (clean seeded state)

### Demo

- [ ] Demo phone charged + offline fallback
- [ ] Backup video recorded (in case of network failure)
- [ ] Caspian contacts confirmed (email + Telegram)
- [ ] Canned replies tested and timed
- [ ] Demo script printed
- [ ] Full timed rehearsal (≤ 5 min)
- [ ] Judge Q&A prep (anticipated questions)

---

## 17. Demo Script

**The scenario shown to judges.** Target duration: 5 minutes.

### Setup (Before Judges Arrive)

- Demo phone on, app open, org dashboard visible
- Fresh investigation state (no prior data)
- Caspian contacts standing by (Telegram group + email recipient)
- Backup video loaded on laptop

### The Script

```
[0:00] "This is Veliora — not a chatbot, an organization."
       Show the org dashboard. Five workers, all idle.
       "Watch what happens when I give it a real question."

[0:15] Type: "Should I build an AI healthcare startup?"
       Tap "Start Investigation."

[0:20] "Notice — I didn't assign tasks. The CEO decomposed the question
       and dispatched work to four departments automatically."
       Show worker statuses changing: Research 'Searching markets',
       Engineering 'Evaluating HIPAA', Finance 'Building cost model',
       Marketing 'Drafting user survey'.

[1:00] "Marketing is now surveying real users on Telegram."
       Show Telegram message sent (real).
       "Research just emailed a healthcare AI expert."
       Show email sent (real).
       "These are real messages to real people — the organization
       reaches outside itself to gather evidence."

[1:45] "Now watch — the org doesn't just collect evidence, it
       criticizes it."
       Switch to Evidence Challenge view.
       Show Engineering challenging Research's market-size claim.
       Show Finance challenging Marketing's survey (n=8).
       "Marketing conceded — the sample was too small. The org
       self-corrected. A single AI would have reported n=8 as fact."

[2:45] "Now the departments debate. This is Society-of-Thought —
       reasoning happens socially, not in a hidden chain."
       Switch to Debate view. Show live turns.
       "Engineering says HIPAA will take 6 months. Finance says
       runway is thin. Marketing argues demand is strong. They
       have different objectives — this isn't roleplay, it's
       real conflict."

[3:45] "The CEO now synthesizes a board report."
       Switch to Report view.
       "Recommendation: proceed with conditions. Confidence 0.68.
       But look here — dissenting opinions from Engineering and
       Finance are preserved. You see not just the decision, but
       who disagreed and why."

[4:30] "This is the point. Veliora doesn't answer questions.
       It conducts investigations — with real evidence, peer
       review, debate, and dissent. The organization is the
       intelligence."

[5:00] Q&A.
```

### Anticipated Judge Questions

| Question | Answer |
|----------|--------|
| "Is the debate real or scripted?" | Real — generated live by the LLM from collected evidence. Temperature is low for consistency, but turns are not pre-written. |
| "Are the Telegram/email messages real?" | Sending is real. Replies are simulated for determinism — we can't guarantee a human replies in 5 minutes. In production, Caspian webhooks deliver real replies. |
| "How is this different from CrewAI?" | CrewAI is a multi-agent framework. Veliora is an organization — with division of labor, conflicting objectives, evidence peer review, and debate. See `03-competitive-analysis.md`. |
| "Does it remember past investigations?" | Not in the MVP. Institutional memory is Phase 1 of the roadmap. See `29-future-roadmap.md`. |
| "What if the LLM hallucinates evidence?" | Evidence challenge is exactly the mitigation — other workers criticize unsupported claims. Hallucinated evidence gets rejected in the challenge phase. |

---

## 18. Risk Mitigation

| Risk | Mitigation |
|------|------------|
| Network failure during demo | Backup video on laptop; demo phone has offline-cached UI |
| LLM latency stalls the demo | Pre-warm LLM connections; cap debate rounds at 4; cache where safe |
| Caspian API down | Fallback to showing sent messages without waiting for replies |
| Debate goes off-script | Low temperature + structured prompt; 4-round cap; CEO synthesis normalizes |
| DB/Redis crash | Docker compose healthchecks; restart on failure; seed script is idempotent |
| Judge asks about missing features | Pre-prepared "that's Phase N" answers referencing `29-future-roadmap.md` |

---

## 19. Post-Hackathon Handoff

After the demo, the MVP becomes the foundation for Phase 1 (see
`29-future-roadmap.md`):

- Worker loop → extend to trust-weighted execution (see `08-trust-engine.md`)
- Evidence store → extend to institutional memory (see `06-memory-system.md`)
- Debate engine → extend to constitutional debate (see `09-constitution-engine.md`)
- Orchestrator → extend to evolution (hiring/firing) (see `07-evolution-engine.md`)
- Caspian 2 channels → expand to all channels (see `12-caspian-integration.md`)

The MVP is deliberately architected so each roadmap phase is an **extension**,
not a rewrite. The worker loop, event system, and state machine are the
load-bearing walls; everything else is finishings added in later phases.

---

## 20. Summary

The hackathon MVP proves one thing: **an organization can investigate, debate,
and decide — visibly, in real time, reaching the real world.** Five workers,
one investigation, two Caspian channels, one board report. Nothing faked that
matters; everything faked that doesn't. The demo is 5 minutes. The thesis is
forever.
