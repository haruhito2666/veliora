# 11 — Communication Engine

> The internal messaging and communication system of Veliora.
>
> **Status:** Design doc
> **Depends on:** `04-technical-architecture.md`, `05-agent-architecture.md`,
> `06-memory-system.md`, `08-trust-engine.md`, `10-debate-engine.md`,
> `16-event-system.md`, `21-organizational-dna.md`
> **Consumed by:** `12-caspian-integration.md`, `19-investigation-engine.md`,
> `20-meeting-engine.md`, `24-api-design.md`

---

## 1. Purpose

The Communication Engine is the substrate on which the organization
**thinks**. In Veliora, intelligence does not emerge from a single
model's hidden chain-of-thought — it emerges from **observable,
structured communication between specialized agents**
(`01-core-philosophy.md`, `10-debate-engine.md`). Every reasoning step
is a message; every disagreement is a message; every decision is
preceded by a traceable sequence of messages.

This document specifies the message bus, types, channels, routing,
protocols, archival, and how organizational DNA shapes communication.
It is **strictly internal** — communication with real-world humans and
services is handled by Caspian (`12-caspian-integration.md`).

### 1.1 The "One Agent" Test

A single AI agent has no one to send messages to. The Communication
Engine exists **only because** Veliora is an organization:

- **Routing** exists because messages travel *between* agents.
- **Channels** exist because the org has *boundaries* (departments,
  cross-department teams, all-hands).
- **Protocols** (request-response, RFC) exist because agents have
  *independent goals* and must coordinate.
- **DNA effects** exist because a 5-person startup and a 200-person
  enterprise communicate differently — a single agent has no "culture."

Any feature here that would also make sense for one agent talking to
itself has been removed or redesigned.

---

## 2. Communication Topology

The organization is not a flat bus. It is a **layered topology** with
department channels at the bottom, cross-department channels in the
middle, and all-hands at the top. Direct messages run alongside as
point-to-point links.

```
                      ┌─────────────────────────────────────────┐
                      │            ALL-HANDS CHANNEL             │
                      │   (org-wide broadcasts, votes, alerts)    │
                      └────────────────────┬────────────────────┘
                           ▲                │                ▲
              broadcast   │                │  broadcast      │
                           │                ▼                │
        ┌──────────────────┴───────────────────────────┬────────────┐
        │              CROSS-DEPARTMENT CHANNELS       │            │
        │  research-engineering  research-finance  marketing-finance │
        └──────┬───────────────────┬───────────────────┴────────────┘
               │                   │
        ┌──────▼──────┐    ┌───────▼──────┐    ┌────────▼─────┐
        │  RESEARCH   │    │ ENGINEERING  │    │   MARKETING  │
        │  DEPT       │    │  DEPT        │    │   DEPT       │
        │  CHANNEL    │    │  CHANNEL     │    │   CHANNEL    │
        └──────┬──────┘    └───────┬──────┘    └────────┬─────┘
               │     ┌─────────────┴──────────────┐     │
               │     │   DIRECT / DM CHANNELS     │     │
               └────►│   agent ↔ agent (1:1)       │◄────┘
                     └────────────────────────────┘
        ┌──────────────────────────┐
        │   MEETING CHANNELS        │  (transient, scoped)
        └──────────────────────────┘
```

The topology is **not static**. The Evolution Engine
(`07-evolution-engine.md`) opens new cross-department channels when two
departments repeatedly need ad-hoc coordination, and closes them when
traffic drops below a threshold (§8).

---

## 3. Message Types

Every message is one of a fixed set of typed messages. The type drives
routing priority, archival policy, and UI rendering (`22-mobile-ux.md`).

| Type                  | Sent By          | Purpose                                       | Default Channel    | Priority |
|-----------------------|------------------|-----------------------------------------------|--------------------|----------|
| `task_assignment`     | CEO / lead       | Assign a unit of work                          | direct / dept      | high     |
| `status_update`       | any agent        | Report progress on an assigned task            | dept / cross-dept  | normal   |
| `evidence_submission` | any agent        | Submit evidence to the knowledge graph         | dept + cross-dept  | high     |
| `meeting_invitation`  | meeting engine   | Invite agents to a scheduled meeting           | direct + meeting   | high     |
| `vote_call`           | constitution     | Call a formal vote (`09-constitution-engine.md`)| all-hands        | critical |
| `alert`               | any agent        | Surface a blocking risk or failure             | all-hands (scoped) | critical |
| `report`              | lead / CEO       | Publish a structured report                    | cross-dept / all    | high     |
| `direct_message`      | any agent        | 1:1 coordination                               | direct             | normal   |
| `broadcast`           | CEO / constitution | Org-wide announcement                        | all-hands          | high     |

### 3.1 Type Semantics

- **`task_assignment`** — Carries a `task_id` (`18-planner-design.md`),
  deadline, success criterion, and assigning authority. The receiver
  MUST emit at least one `status_update` before the deadline, or the
  Trust Engine (`08-trust-engine.md`) records a missed commitment.
- **`evidence_submission`** — Carries a knowledge graph node pointer
  (`14-knowledge-graph.md`) plus a claim, source, and confidence. It
  is the **only** type that creates new evidence nodes, and is
  immediately eligible for challenge (`10-debate-engine.md`).
- **`vote_call`** — Only the Constitution Engine or a delegated agent
  may issue it. It freezes the decision context, opens a voting window,
  and routes results back to the caller.
- **`alert`** — Has a **scope** field: a scoped alert goes to the
  affected cross-department channel and the CEO; a critical alert
  escalates to all-hands.

### 3.2 What Is NOT a Message

System signals — `agent_spawned`, `trust_score_changed`, `dna_mutated`,
`meeting_started` — are **events** on the Event Bus
(`16-event-system.md`), not messages. Messages are **agent-to-agent**
communication; events are **system-to-system** signals. Mixing them
collapses the organization back into a single process.

---

## 4. Communication Channels

A channel is a named, addressable destination with a membership list
and a retention policy — the unit of *who is allowed to hear what*.

| Channel Type       | Scope            | Membership                     | Retention            | Example                        |
|--------------------|------------------|--------------------------------|----------------------|--------------------------------|
| `department`       | one department   | all agents in the department   | permanent            | `dept:research`               |
| `cross_department` | two or more depts| named members from each dept   | permanent            | `xdept:research.finance`       |
| `all_hands`        | whole org        | every agent                    | permanent            | `all:org`                     |
| `direct`           | 1:1              | exactly two agents             | permanent            | `dm:a.research_lead:a.ceo`     |
| `meeting`           | transient        | invitees of a specific meeting| archived after end  | `meeting:42`                   |

### 4.1 Addressing and Lifecycle

Channels are addressed as `scope ":" name` (e.g. `dept:research`,
`xdept:research.finance`, `dm:a.research_lead:a.ceo`, `meeting:42`).
`department` channels are created/destroyed by the Evolution Engine
with the department. `cross_department` channels are created **lazily**
when two departments exchange more than `N` direct messages in a
rolling window (default `N=20`) — an emergent behavior, §8. `all_hands`
is created with the org and is immutable. `direct` channels are created
on first message and persist. `meeting` channels are created by the
Meeting Engine (`20-meeting-engine.md`) and archived when the meeting
ends.

---

## 5. Message Format Specification

All messages share a canonical JSON envelope, the unit of archival
and search.

### 5.1 Envelope

```json
{
  "msg_id": "msg_01HZX...",            // ULID, globally unique
  "org_id": "org_01HZ...",
  "investigation_id": "inv_01HZ...",   // null for general comms
  "type": "evidence_submission",       // see §3
  "from": "agent.research_lead",
  "to": "channel:dept:research",
  "channel": "dept:research",          // resolved by router
  "subject": "Paper on RWE for AI diagnostics",
  "priority": "high",                  // normal | high | critical
  "dna_fingerprint": "dna_01HZ...",    // DNA at send time (§9)
  "created_at": "2026-08-05T16:53:10Z",
  "ack_deadline": "2026-08-05T17:23:10Z",
  "body": { ... },                     // type-specific payload
  "references": ["msg_01HZY..."],      // threading
  "trust_at_send": 0.96                // sender's trust at send time
}
```

### 5.2 Type-Specific Bodies

```json
// task_assignment
"body": {
  "task_id": "task_01HZ...",
  "description": "Survey 50 clinicians on AI triage willingness",
  "success_criterion": ">=50 responses, confidence interval reported",
  "deadline": "2026-08-06T12:00:00Z",
  "delegated_by": "agent.ceo"
}

// evidence_submission
"body": {
  "kg_node_id": "kg_01HZ...",           // created/linked on send
  "claim": "Clinicians are 3.2x more willing to adopt AI triage when ...",
  "source": { "type": "external_caspian", "ref": "caspian.msg_..." },
  "confidence": 0.78,
  "method": "telegram_survey_n=52",
  "open_to_challenge": true
}
```

`vote_call`, `alert`, `report`, and `broadcast` bodies follow the same
pattern with type-specific fields (`vote_call`: `options`,
`voting_method`, `window_closes_at`, `quorum`; `alert`: `severity`,
`scope`, `blocking`, `summary`).

### 5.3 Threading

Messages can `reference` prior messages to reconstruct discussions,
debates, and meeting transcripts. The Debate Engine
(`10-debate-engine.md`) uses threading to identify which claim a
challenge targets.

---

## 6. Message Routing and Delivery

The router is a stateless function of `(message, channel, membership,
DNA)` running as part of the Communication Engine service.

### 6.1 Routing Pipeline

```
┌──────────┐   ┌────────────┐   ┌──────────────┐   ┌────────────┐   ┌──────────┐
│  send()  │──►│  validate  │──►│  resolve     │──►│  DNA shape  │──►│  deliver │
│  API     │   │  schema +  │   │  channel +   │   │  (§9)       │   │  + ack   │
└──────────┘   │  authority │   │  membership  │   └─────────────┘   └────┬─────┘
               └─────┬──────┘   └──────────────┘                            │
                     │ reject                                                │
                     ▼                                                       ▼
               ┌──────────────┐                                  ┌──────────────────┐
               │ dead-letter   │                                  │  archive + index │
               │  + alert      │                                  │  (§10)            │
               └──────────────┘                                  └──────────────────┘
```

1. **Validate** — Schema check (envelope + body) and authority check:
   is the sender permitted to emit this `type` on this channel? Only
   the Constitution Engine may emit `vote_call` on `all_hands`.
2. **Resolve** — Expand `to` into a recipient list via channel
   membership. A `cross_department` channel resolves to the named
   members from each side, not the whole department.
3. **DNA shape** — Apply DNA-driven transformations (§9): verbosity
   reduction, visibility widening, priority escalation.
4. **Deliver** — Push to each recipient's inbox queue. Each message
   carries an `ack_deadline`; missing an ack is a Trust Engine event.
5. **Archive + index** — Append to the archival log and index (§10).

### 6.2 Delivery Guarantees

- **At-least-once** within the org; duplicates deduplicated by
  `msg_id`; ordered per channel by `created_at` (cross-channel order is
  not guaranteed — use the Event Bus for a global order).
- **Critical priority preempts**: `vote_call` and `critical` `alert`
  are delivered before `normal` messages in the same queue.

### 6.3 Authority Matrix

| Message Type        | dept | xdept | all_hands | direct | meeting |
|--------------------|:----:|:-----:|:---------:|:------:|:-------:|
| task_assignment     | lead | lead  | CEO       | any    | chair   |
| status_update       | any  | any   | lead      | any    | any     |
| evidence_submission | any  | any   | lead      | any    | any     |
| meeting_invitation  | —    | —     | CEO       | any    | engine  |
| vote_call           | —    | —     | const.    | —      | chair   |
| alert               | any  | any   | any       | any    | any     |
| report              | lead | lead  | CEO       | any    | chair   |
| direct_message      | —    | —     | —         | any    | —       |
| broadcast           | —    | —     | CEO/const.| —      | —       |

"—" means the type is not valid on that channel; the router rejects it.

---

## 7. Communication Protocols

A protocol is a constrained pattern of message exchange. Protocols are
**not enforced by the router** — they are conventions agents follow,
shaped by their prompts (`17-prompt-architecture.md`). The engine
exposes helpers for each so agents do not reinvent them.

### 7.1 Request-Response

Used for direct questions between agents ("Can Engineering estimate
the API cost for 10k RPS?"). The request carries `ack_deadline`; the
response references it. No response by `ack_deadline` → router emits
an `alert` and the Trust Engine records a missed commitment; repeated
misses reduce the responder's trust.

### 7.2 Publish-Subscribe

Used for `report`, `broadcast`, and most `evidence_submission` flows.
The publisher posts to a channel; all members receive it. There is no
per-recipient ack; instead, the channel has a `read_quorum` (default
`0.5` for `cross_department`, `1.0` for `all_hands` votes) that the
Meeting Engine uses to decide whether a topic is "settled."

### 7.3 Request-for-Comment (RFC)

The flagship protocol for Society-of-Thought reasoning. An agent
posts a **proposal** to a `cross_department` channel with
`open_to_challenge: true` and an `rfc_window`. Other departments post
replies that **support**, **challenge**, or **extend**. The Debate
Engine (`10-debate-engine.md`) aggregates the thread into a verdict.

```
┌─────────┐  proposal (RFC open)  ┌─────────────┐
│ Research │──────────────────────►│ xdept channel│
└─────────┘                       └──────┬──────┘
       ▲                                  │
       │ challenge   ┌────────┐  support  │
       └─────────────┤Engineering│◄────────┘
                     └────┬───┘
                  extend │
                         ▼
                   ┌──────────┐
                   │ Marketing│ ──► Debate Engine → verdict → report
                   └──────────┘
```

The RFC protocol is the structural backbone of "Live Investigation"
(`19-investigation-engine.md`): the user watches an RFC thread resolve
in real time on the mobile app.

---

## 8. Emergent Communication Patterns

The engine does not hardcode "Research talks to Marketing." It observes
traffic and lets patterns emerge — a core research contribution
(`02-research-contributions.md`).

### 8.1 Observed Metrics

For each pair of agents `(i, j)` and departments `(D_a, D_b)`, the
engine maintains rolling counts: `msg_count[i,j]` (DMs / 24h),
`cross_count[D_a,D_b]` (cross-dept / 7d), `evidence_flow[D_a→D_b]`
(submissions consumed by `D_b`), and `challenge_rate[D_a,D_b]`
(fraction of `D_a`'s submissions challenged by `D_b`).

### 8.2 Emergent Behaviors

| Signal                                | Emergent Behavior                            |
|---------------------------------------|----------------------------------------------|
| `cross_count[D_a,D_b] > N` for 7d     | Auto-create `xdept:D_a.D_b` channel          |
| `cross_count[D_a,D_b] < N/10` for 14d | Archive `xdept:D_a.D_b` channel             |
| `msg_count[i,j] > N` for 24h          | Suggest a standing meeting to Meeting Engine|
| `challenge_rate[D_a,D_b] > 0.5`       | Flag structural conflict → Evolution Engine |
| `evidence_flow` consistently one-way  | Flag information bottleneck → CEO alert      |

These are **suggestions**, not commands. The Evolution Engine
(`07-evolution-engine.md`) decides whether to act, subject to the
constitution (`09-constitution-engine.md`).

### 8.3 Information Flow Signature

Over time the org develops a characteristic flow signature, rendered
as a directed graph for the mobile app (`22-mobile-ux.md`):

```
        ┌──────┐  evidence   ┌──────────┐  forecast  ┌──────┐
        │Research│──────────►│  Finance  │──────────►│ CEO  │
        └──┬───┘             └─────▲────┘            └──┬───┘
           │ challenge             │ challenge          │ report
           ▼                       │                    ▼
        ┌──────┐              ┌─────┴────┐         ┌────────┐
        │Engineering│         │ Marketing │        │All-Hands│
        └──────┘              └──────────┘         └────────┘
```

This signature is part of the organization's **DNA fingerprint**
(`21-organizational-dna.md`): two orgs solving the same problem
produce visibly different flow graphs.

---

## 9. How DNA Affects Communication

Organizational DNA (`21-organizational-dna.md`) does not change *what*
is possible — it changes *how* the org communicates. The DNA shaper
runs at step 3 of the routing pipeline (§6.1).

| DNA Parameter          | Low Value Effect                        | High Value Effect                                |
|-----------------------|-----------------------------------------|--------------------------------------------------|
| **Speed**             | Verbose messages, full context, slow acks| Terse messages, abbreviations, tight ack deadlines|
| **Transparency**      | DMs private, dept-only visible          | All messages mirrored to `all_hands` read-only log|
| **Hierarchy**         | Any agent can message any agent          | Messages to CEO must route through dept lead     |
| **Accuracy**          | Single source is fine                    | Every claim requires 2+ supporting evidence refs |
| **Long-Term Thinking**| Short ack deadlines, reactive            | Long ack deadlines, strategic context required  |
| **Innovation**        | Standard protocols only                 | RFC encouraged, novel channels allowed           |
| **Security**         | Open cross-dept channels                 | Cross-dept traffic audited, sensitive msgs redacted |

### 9.1 Worked Example: Speed and Transparency

With `Speed = 0.8`, the shaper rewrites the body before delivery:

```
INPUT:  "Hi, I've finished reviewing the three papers. Paper A supports
         the hypothesis (p<0.05, n=18). Paper B is a survey of 240
         clinicians, supportive. Paper C is skeptical. Proceed cautiously?"
OUTPUT: "3 papers reviewed. A: supports (p<.05, n=18). B: survey n=240,
         supportive. C: skeptical. Recommend cautious proceed. RFC open."
```

The original is preserved in the archive; the shaper only changes what
is delivered. High `Transparency` similarly mirrors every
`direct_message` to a read-only `all_hands.log` — agents self-curate
because they know DMs are visible, which is intentional: high-
Transparency orgs reduce hidden friction.

### 9.2 DNA Fingerprint on Messages

Every message carries `dna_fingerprint` — the hash of the DNA vector
at send time. This lets the archive reconstruct "what the org was like
when this was said," which matters for institutional memory
(`06-memory-system.md`) and post-hoc analysis of decisions made under
different DNA.

---

## 10. Message Archival and Search

Messages are appended to an append-only log keyed by
`(org_id, channel, created_at)`. The log is the source of truth;
inboxes are materialized views (schema in `13-database-design.md`).

**Indexing** — full-text on `subject` + body text; per-channel;
per-agent; per-thread (following `references`); and per-type ("all
`vote_call` messages in this investigation").

**Search API** —

```http
GET /orgs/{org_id}/messages?q=triage+willingness&channel=dept:research
    &type=evidence_submission&investigation_id=inv_01HZ...
    &from=2026-08-01&to=2026-08-05&limit=50
```

Returns a paginated list of envelopes, with thread context expanded on
request (`?expand_thread=true`).

**Retention** — `department`, `cross_department`, `all_hands`, and
`direct` are permanent for the life of the org (institutional memory
depends on this, `06-memory-system.md`). `meeting` is archived to cold
storage when the meeting ends, with a summary node kept in the
knowledge graph (`14-knowledge-graph.md`). Critical `alert` messages
are never purged; they are used for post-mortems. Archival exists
because **other agents**, and **future versions of agents**, need to
read messages they were not originally party to — the archive is the
organization's shared memory, not any one agent's.

---

## 11. Integration with Other Engines

| Engine                   | Relationship                                                          |
|--------------------------|-----------------------------------------------------------------------|
| `05-agent-architecture`  | Agents are the endpoints; the Communication Engine is the connective tissue.|
| `06-memory-system`       | Archive feeds institutional memory; recalls influence message context.|
| `07-evolution-engine`    | Consumes traffic metrics (§8) to open/close channels and restructure depts.|
| `08-trust-engine`        | Missed acks, unanswered RFCs, and challenged evidence all flow here.  |
| `09-constitution-engine` | Authorizes `vote_call` and `broadcast`; gates some cross-dept traffic. |
| `10-debate-engine`       | RFC threads (§7.3) are debate input; verdicts published as `report`.  |
| `12-caspian-integration` | External inbound messages are converted to internal messages here.    |
| `19-investigation-engine`| Investigations are the dominant producer of RFC threads and evidence.|
| `20-meeting-engine`      | Owns `meeting` channels; emits `meeting_invitation` messages.         |
| `21-organizational-dna`  | DNA shaper (§9) runs in the routing pipeline.                         |

---

## 12. API Surface

The engine exposes a small internal API (public REST surface in
`24-api-design.md`):

```
POST /internal/messages                  # send
GET  /internal/messages/{msg_id}         # fetch
GET  /internal/channels/{channel}/messages
POST /internal/channels                  # create (restricted)
GET  /internal/orgs/{org_id}/flow-graph  # emergent pattern (§8.3)
GET  /internal/orgs/{org_id}/search      # §10
```

---

## 13. MVP Scope

For the hackathon MVP (`28-mvp-hackathon.md`), the Communication Engine
implements: all 9 message types (§3); all 5 channel types (§4); the
routing pipeline with authority matrix (§6); request-response and RFC
protocols (§7.1, §7.3), with pub-sub implicit; the DNA shaper for
**Speed** and **Transparency** only (§9); append-only archive with
full-text search (§10); and the emergent channel-creation threshold
(§8.2, first row only).

Deferred: the full emergent-behavior table; the DNA shaper for the
remaining 5 parameters; meeting-transcript cold storage; and
cross-organization message forwarding (contribution #10, "Multiple
Independent Organizations").
