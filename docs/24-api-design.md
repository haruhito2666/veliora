# 24 — API Design

> The complete external interface to a Veliora organization. REST for
> resource state, GraphQL subscriptions for typed real-time feeds, and
> raw WebSocket channels for low-latency live streaming of investigations
> and meetings.

This document defines the public API surface that the Mobile UX
(see `22-mobile-ux.md`) and UI/UX Flow (see `23-ui-ux-flow.md`) are built
against, and that third-party integrations use to drive organizations
programmatically. The state transitions triggered by these endpoints are
formalized in `25-state-machine.md`.

---

## 1. Design Principles

The API is shaped by the same rule that shapes the rest of Veliora —
the **One Agent Test** (see `01-core-philosophy.md`):

1. **There is no `/agents` resource.** A caller never addresses an
   individual worker. The caller addresses the *organization*, and the
   organization decides internally who responds. A `Worker` resource
   exists for *inspection* (status, memory, trust) — never for direct
   command. This is the single most important constraint on the API
   surface and is enforced at the gateway (see §10.2).

2. **The primary unit of work is the Investigation**, not the request.**
   Most POST calls do not return a synchronous answer; they return an
   investigation handle and the answer emerges over time through
   streaming. See `19-investigation-engine.md`.

3. **Reads are cheap, writes are organizational acts.** Listing workers
   is a cache hit. Creating an investigation, amending a constitution,
   or challenging evidence are acts that trigger debate, voting, and
   state-machine transitions. The API reflects this asymmetry: read
   endpoints are flat and fast; write endpoints are thin wrappers over
   organizational processes.

4. **Real-time is first-class.** Investigations and meetings are watched
   live (see `16-event-system.md`). REST alone is insufficient; GraphQL
   subscriptions and WebSocket event streams are part of the core
   contract, not an add-on.

5. **Streaming is scoped, not global.** A client subscribes to a specific
   investigation or meeting, not to a firehose. Backpressure is per
   stream.

---

## 2. Conventions

### 2.1 Base URL & Versioning

```
https://api.veliora.io/v1/...
```

- All REST paths are prefixed with `/v1`. Breaking changes bump the
  prefix. GraphQL lives at `/v1/graphql`. WebSocket lives at
  `wss://stream.veliora.io/v1`.
- Resources are pluralized nouns: `/organizations`, `/investigations`,
  `/departments`, etc.
- IDs are URL-safe ULIDs (26 chars, time-sortable). Resource handles
  are namespaced under their parent organization:
  `/organizations/{org_id}/investigations/{inv_id}`.

### 2.2 Content Types

- Request bodies and REST responses: `application/json; charset=utf-8`.
- GraphQL: `application/json` over HTTP, `graphql-ws` subprotocol over
  WebSocket for subscriptions.
- Report downloads: `application/pdf`, `text/markdown`,
  `application/vnd.openxmlformats-officedocument.wordprocessingml.document`.
- WebSocket event frames: JSON text frames as defined in §7.

### 2.3 Pagination

Cursor-based, never offset-based (investigations and messages are
high-churn and time-ordered).

```
GET /v1/organizations/{org_id}/investigations?limit=20&cursor=01H...
```

Response envelope:

```json
{
  "data": [ /* ... */ ],
  "pagination": {
    "next_cursor": "01HXY...",
    "has_more": true,
    "count": 20
  }
}
```

- `limit` defaults to 20, max 100.
- `cursor` is opaque; clients must not construct it.
- `order` accepts `asc` | `desc` on the resource's natural sort key
  (creation time by default).

### 2.4 Errors

A single, machine-parseable error envelope. HTTP status codes follow
RFC 9110. The `code` field is a stable string clients switch on.

```json
{
  "error": {
    "code": "investigation_not_researchable",
    "message": "Investigation inv_01HXY is in state 'completed' and cannot transition to 'researching'.",
    "request_id": "req_01HXY...",
    "details": {
      "investigation_id": "inv_01HXY",
      "current_state": "completed",
      "attempted_event": "resume_research"
    }
  }
}
```

| HTTP | code prefix            | meaning                                   |
|------|------------------------|-------------------------------------------|
| 400  | `invalid_*`            | Malformed request body or parameters      |
| 404  | `*_not_found`          | Resource does not exist                   |
| 409  | `*_invalid_transition` | State-machine guard rejected the action   |
| 422  | `*_violation`          | Semantic violation (e.g., no quorum)     |
| 500  | `internal_*`           | Unhandled server error                    |

### 2.5 Idempotency

All write endpoints accept `Idempotency-Key` (UUID v4 recommended).
The server caches the response for 24h; retries with the same key
return the original result. Required for investigation creation and
constitution amendment to prevent duplicate organizational acts.

---

## 3. Deployment Boundary (Authless Local Mode)

The current open-source alpha intentionally has no application-level
authentication or authorization. REST requests and WebSocket connections do
not carry user identities, and the gateway does not enforce organization
ownership. Organization IDs in paths are still validated and every domain
query is scoped by the organization ID.

**Operational rule:** run the gateway on localhost or a trusted private
network. Anyone who can reach it can read and modify every organization in the
instance. Do not publish it to the public internet or use it as a multi-tenant
service.

If the project grows beyond a trusted single-user deployment, add an explicit
security design before implementing auth. A future design must cover API keys or
OAuth 2.1/PKCE, organization membership, WebSocket authorization, revocation,
rate limits, migrations, and the Android session lifecycle. No bearer token is
required or accepted by the current API contract.

---

## 4. Rate Limiting

The alpha does not implement an application-level rate limiter. Operators must
apply connection and request limits at the deployment edge (for example, a
private reverse proxy or firewall) before exposing the gateway beyond a trusted
host. A future remote deployment should add per-IP and per-organization limits.

---

## 5. REST API — Organizations

The top-level resource. Everything else is namespaced under an org.

### 5.1 Create Organization

```
POST /v1/organizations
Idempotency-Key: <uuid>
```

Request:
```json
{
  "name": "Startup Society",
  "template": "startup",            // "startup" | "research_lab" | "investment_firm" | "custom"
  "constitution": {                  // optional; template provides defaults
    "mission": "Decide whether to build an AI healthcare startup.",
    "principles": ["evidence_first", "dissent_encouraged"],
    "values": ["accuracy", "speed"],
    "decision_rules": { "voting": "weighted_trust", "quorum": 0.6 }
  },
  "dna": {                           // optional; see 21-organizational-dna.md
    "innovation": 0.7, "risk": 0.4, "hierarchy": 0.5,
    "accuracy": 0.8, "speed": 0.6, "transparency": 0.7,
    "creativity": 0.6, "long_term": 0.5, "security": 0.6
  },
  "caspian_channels": ["email", "telegram"]   // see 12-caspian-integration.md
}
```

Response `201 Created`:
```json
{
  "organization_id": "org_01HXY...",
  "name": "Startup Society",
  "state": "forming",                // see 25-state-machine.md §3
  "template": "startup",
  "constitution_id": "con_01HXY...",
  "dna_id": "dna_01HXY...",
  "departments": [                   // seeded by template
    { "department_id": "dep_01HXY...", "name": "CEO", "role": "executive" },
    { "department_id": "dep_01HXY...", "name": "Research", "role": "research" }
  ],
  "created_at": "2026-08-05T16:53:14Z"
}
```

The organization enters the `forming` state. It transitions to
`active` once the constitution is ratified (auto for templates; via
vote for custom). Poll `GET /v1/organizations/{org_id}` or subscribe
to `organization.stateChanged` (§6).

### 5.2 List / Get / Patch / Status / Dissolve

| Method | Path                                       | Purpose                                  |
|--------|--------------------------------------------|------------------------------------------|
| GET    | `/v1/organizations`                        | List orgs the principal may access       |
| GET    | `/v1/organizations/{org_id}`               | Full org snapshot                        |
| GET    | `/v1/organizations/{org_id}/status`        | Lightweight live status (health, counts) |
| PATCH  | `/v1/organizations/{org_id}`               | Update name/metadata (not state)         |
| DELETE | `/v1/organizations/{org_id}`              | Dissolve (transitions to `dissolved`)    |

`GET /status` returns the live dashboard payload the mobile app renders
(see `22-mobile-ux.md`):

```json
{
  "organization_id": "org_01HXY...",
  "state": "active",
  "health": 0.96,
  "departments": [
    { "name": "CEO",        "worker_state": "working",  "activity": "coordinating" },
    { "name": "Research",   "worker_state": "working",  "activity": "reading papers" },
    { "name": "Engineering","worker_state": "working", "activity": "implementing" }
  ],
  "counts": { "investigations_active": 9, "meetings": 2, "messages": 34 }
}
```

---

## 6. REST API — Investigations

The flagship resource. See `19-investigation-engine.md` for the
orchestration this surface drives.

### 6.1 Create Investigation

```
POST /v1/organizations/{org_id}/investigations
Idempotency-Key: <uuid>
```

Request:
```json
{
  "prompt": "Should I build an AI healthcare startup?",
  "priority": "normal",              // "low" | "normal" | "high" | "critical"
  "depth": "standard",               // "quick" | "standard" | "deep"
  "deadline": null,                   // ISO8601 or null
  "constraints": {
    "must_evaluate": ["regulatory", "market", "technical"],
    "must_consult_departments": ["Legal", "Finance"]
  },
  "caspian_outbound": true            // allow external evidence gathering
}
```

Response `202 Accepted` (work is asynchronous):
```json
{
  "investigation_id": "inv_01HXY...",
  "organization_id": "org_01HXY...",
  "state": "created",
  "plan": null,                       // populated after planning completes
  "created_at": "2026-08-05T16:53:14Z",
  "stream_url": "wss://stream.veliora.io/v1/organizations/org_01HXY/investigations/inv_01HXY"
}
```

The returned `stream_url` is the live investigation channel (§7.2).
The investigation immediately transitions `created → planning`.

### 6.2 Investigation Resource Schema

```json
{
  "investigation_id": "inv_01HXY...",
  "organization_id": "org_01HXY...",
  "prompt": "Should I build an AI healthcare startup?",
  "state": "researching",             // see 25-state-machine.md §4
  "state_history": [
    { "state": "created",     "entered_at": "2026-08-05T16:53:14Z" },
    { "state": "planning",    "entered_at": "2026-08-05T16:53:15Z" },
    { "state": "researching", "entered_at": "2026-08-05T16:53:42Z" }
  ],
  "priority": "normal",
  "depth": "standard",
  "plan": {
    "tasks": [
      { "task_id": "tsk_01HXY...", "department": "Research",
        "objective": "Survey AI healthcare competitors", "status": "in_progress" }
    ]
  },
  "confidence": null,                 // populated during synthesizing
  "departments_engaged": ["Research","Marketing","Engineering","Finance","Legal","CEO"],
  "evidence_count": 12,
  "meetings": ["mtg_01HXY..."],
  "report_id": null,
  "created_at": "2026-08-05T16:53:14Z",
  "completed_at": null
}
```

### 6.3 Endpoints

| Method | Path                                                            | Purpose                          |
|--------|-----------------------------------------------------------------|----------------------------------|
| GET    | `/v1/organizations/{org_id}/investigations`                     | List (filter by `state`)         |
| GET    | `/v1/organizations/{org_id}/investigations/{inv_id}`            | Get full resource                |
| POST   | `/v1/organizations/{org_id}/investigations/{inv_id}/cancel`     | Cancel (state → `cancelled`)     |
| POST   | `/v1/organizations/{org_id}/investigations/{inv_id}/interact`   | Inject user input mid-flight     |
| GET    | `/v1/organizations/{org_id}/investigations/{inv_id}/report`     | Final report metadata            |
| GET    | `/v1/organizations/{org_id}/investigations/{inv_id}/report?format=pdf` | Download report (200/409)   |

`interact` is the user-organization channel. It does **not** address a
worker; it injects a directive the CEO department interprets:

```json
{ "kind": "constraint", "body": "Assume $500k seed budget." }
{ "kind": "question",  "body": "What if the competitor launches first?" }
{ "kind": "redirect",  "body": "Focus on regulatory risk." }
```

Response `202 Accepted` with `{ "accepted": true, "acknowledged_by": "dep_01HXY...CEO" }`.
A `409 investigation_invalid_transition` is returned if the
investigation is already `completed` or `cancelled`.

---

## 7. REST API — Departments, Workers, Meetings, Messages

### 7.1 Departments

| Method | Path                                                        | Purpose                          |
|--------|-------------------------------------------------------------|----------------------------------|
| GET    | `/v1/organizations/{org_id}/departments`                   | List                             |
| GET    | `/v1/organizations/{org_id}/departments/{dep_id}`          | Get (with current worker roster)|
| GET    | `/v1/organizations/{org_id}/departments/{dep_id}/status`   | Live status (activity, load)     |

Department schema:
```json
{
  "department_id": "dep_01HXY...",
  "organization_id": "org_01HXY...",
  "name": "Research",
  "role": "research",
  "charter": "Gather and validate external evidence.",
  "workers": ["wrk_01HXY...", "wrk_01HXY..."],
  "influence": 0.23,                    // see 08-trust-engine.md
  "load": 0.71,                         // 0..1 current utilization
  "created_at": "2026-08-05T16:53:14Z"
}
```

### 7.2 Workers

Workers are **inspectable, not addressable.** There is no
`POST /workers/{id}/do` endpoint. Worker state changes are driven
entirely by the organization internally (see `25-state-machine.md` §5).

| Method | Path                                                    | Purpose                              |
|--------|---------------------------------------------------------|--------------------------------------|
| GET    | `/v1/organizations/{org_id}/workers`                    | List (filter by `department`, `state`)|
| GET    | `/v1/organizations/{org_id}/workers/{wrk_id}`          | Get                                  |
| GET    | `/v1/organizations/{org_id}/workers/{wrk_id}/status`    | Live status (state, current task)    |
| GET    | `/v1/organizations/{org_id}/workers/{wrk_id}/memory`   | Institutional memory slice           |

Worker schema:
```json
{
  "worker_id": "wrk_01HXY...",
  "organization_id": "org_01HXY...",
  "department_id": "dep_01HXY...",
  "name": "Research Lead",
  "role": "researcher",
  "state": "working",                   // see 25-state-machine.md §5
  "trust_score": 0.96,                  // see 08-trust-engine.md
  "current_task_id": "tsk_01HXY...",
  "promotions": 2,
  "joined_at": "2026-08-05T16:53:14Z"
}
```

`/memory` returns a paginated slice of the institutional memory graph
(see `06-memory-system.md`): decisions, prior investigations, learned
policies, and conflicts this worker participated in. It is read-only.

### 7.3 Meetings

| Method | Path                                                          | Purpose                              |
|--------|---------------------------------------------------------------|--------------------------------------|
| GET    | `/v1/organizations/{org_id}/meetings`                        | List (filter by `state`, `investigation`) |
| GET    | `/v1/organizations/{org_id}/meetings/{mtg_id}`                 | Get (agenda, participants, minutes)  |
| GET    | `/v1/organizations/{org_id}/meetings/{mtg_id}/stream`          | 302 redirect to WebSocket `stream_url` |
| POST   | `/v1/organizations/{org_id}/meetings/{mtg_id}/messages`        | Inject a message (gated, see §3.3)   |

Meeting schema (see `20-meeting-engine.md`):
```json
{
  "meeting_id": "mtg_01HXY...",
  "organization_id": "org_01HXY...",
  "investigation_id": "inv_01HXY...",
  "title": "Evidence Challenge: AI healthcare market size",
  "state": "active",                   // see 25-state-machine.md §6
  "agenda": [ "Review Research evidence", "Engineering critique", "Vote" ],
  "participants": ["wrk_01HXY...","wrk_01HXY...","wrk_01HXY..."],
  "chair": "wrk_01HXY...",
  "started_at": "2026-08-05T17:02:11Z",
  "concluded_at": null,
  "stream_url": "wss://stream.veliora.io/v1/.../meetings/mtg_01HXY..."
}
```

### 7.4 Messages

Internal organizational communication (see `11-communication-engine.md`).
Messages are append-only and immutable.

| Method | Path                                                              | Purpose              |
|--------|-------------------------------------------------------------------|----------------------|
| GET    | `/v1/organizations/{org_id}/messages`                             | List (filter heavily)|
| GET    | `/v1/organizations/{org_id}/messages/{msg_id}`                    | Get                  |
| POST   | `/v1/organizations/{org_id}/messages`                              | Send (restricted)    |

`POST /messages` is **not** a free chat. It is a constrained
inter-departmental message and requires a `kind`:

```json
{
  "from_department_id": "dep_01HXY...Research",
  "to_department_id":   "dep_01HXY...Engineering",
  "kind": "evidence_request",          // "evidence_request" | "critique" | "clarification" | "directive"
  "investigation_id": "inv_01HXY...",
  "body": "Please validate the sample size in evidence evd_01HXY..."
}
```

Free-form user-to-org chat is **not** modeled at the API level; the
user interacts via `POST /investigations/{id}/interact`. This enforces
the One Agent Test: the user talks to the organization, not to a worker.

---

## 8. REST API — Evidence, Reports, Constitution, DNA, Evolution, Trust

### 8.1 Evidence

Evidence is the unit of organizational knowledge under challenge
(see `02-research-contributions.md` §7, `19-investigation-engine.md`).

| Method | Path                                                                  | Purpose                |
|--------|-----------------------------------------------------------------------|------------------------|
| GET    | `/v1/organizations/{org_id}/investigations/{inv_id}/evidence`         | List                   |
| GET    | `/v1/organizations/{org_id}/investigations/{inv_id}/evidence/{evd_id}`| Get                   |
| POST   | `/v1/organizations/{org_id}/investigations/{inv_id}/evidence/{evd_id}/challenge` | Challenge evidence |

Evidence schema:
```json
{
  "evidence_id": "evd_01HXY...",
  "investigation_id": "inv_01HXY...",
  "submitted_by": "dep_01HXY...Research",
  "source": { "type": "paper", "url": "...", "caspian_channel": null },
  "claim": "AI healthcare market will reach $45B by 2030.",
  "confidence": 0.78,
  "status": "challenged",               // "open" | "challenged" | "upheld" | "retracted"
  "challenges": [
    { "challenge_id": "chl_01HXY...", "by": "dep_01HXY...Engineering",
      "critique": "Sample size n=12 too small.", "resolution": "upheld" }
  ],
  "trust_weight": 0.71                  // post-challenge adjusted weight
}
```

`POST /challenge` is a `write_light` act. It does not delete evidence;
it creates a `challenge` record and triggers a meeting if the
challenge is contested (see `25-state-machine.md` §9).

### 8.2 Reports

```
GET /v1/organizations/{org_id}/investigations/{inv_id}/report
GET /v1/organizations/{org_id}/investigations/{inv_id}/report?format=pdf
GET /v1/organizations/{org_id}/investigations/{inv_id}/report?format=markdown
GET /v1/organizations/{org_id}/investigations/{inv_id}/report?format=docx
```

Returns `409 investigation_not_complete` if `state != completed`. The
report body is defined in `19-investigation-engine.md`: executive
summary, recommendation, confidence, dissenting opinions, evidence,
risks, opportunities, next steps, full discussion transcript.

### 8.3 Constitution

| Method | Path                                                       | Purpose                          |
|--------|------------------------------------------------------------|----------------------------------|
| GET    | `/v1/organizations/{org_id}/constitution`                  | Current ratified constitution    |
| GET    | `/v1/organizations/{org_id}/constitution/history`         | Amendment history                |
| POST   | `/v1/organizations/{org_id}/constitution/amendments`       | Propose amendment (triggers vote)|

Amendment proposal:
```json
{
  "section": "decision_rules.voting",
  "change": { "from": "weighted", "to": "consensus" },
  "rationale": "Weighted voting over-amplifies Research; consensus forces debate."
}
```

Returns `202` with an `evolution_id`. The amendment is debated and
voted on as an evolution event (see §8.5, `25-state-machine.md` §7).
The constitution does **not** change until the vote passes — this is
an organizational act, not an API call.

### 8.4 DNA

| Method | Path                                            | Purpose                          |
|--------|-------------------------------------------------|----------------------------------|
| GET    | `/v1/organizations/{org_id}/dna`                | Current DNA vector               |
| PATCH  | `/v1/organizations/{org_id}/dna`                 | Propose DNA shift (triggers vote)|

DNA is not directly mutable by the user; a PATCH creates an evolution
proposal of type `dna_shift`. The organization debates whether the
shift is healthy (see `21-organizational-dna.md`, `07-evolution-engine.md`).

### 8.5 Evolution

| Method | Path                                                          | Purpose                          |
|--------|---------------------------------------------------------------|----------------------------------|
| GET    | `/v1/organizations/{org_id}/evolution/events`                | List evolution events            |
| GET    | `/v1/organizations/{org_id}/evolution/events/{evo_id}`       | Get one event                    |
| POST   | `/v1/organizations/{org_id}/evolution/proposals`             | Propose (hire/fire/merge/split/policy) |

Proposal:
```json
{
  "type": "hire",                       // "hire"|"fire"|"promote"|"merge_dept"|"split_dept"|"policy"|"dna_shift"|"amend_constitution"
  "payload": { "department": "Engineering", "role": "backend_specialist", "rationale": "Load 0.91 sustained 3 investigations" }
}
```

Returns `202` with `evolution_id`. The proposal traverses the
Evolution state machine (see `25-state-machine.md` §7): `proposed →
debating → voting → approved|rejected → executing → applied`.

### 8.6 Trust

| Method | Path                                                | Purpose                          |
|--------|-----------------------------------------------------|----------------------------------|
| GET    | `/v1/organizations/{org_id}/trust`                  | Org-wide trust scores            |
| GET    | `/v1/organizations/{org_id}/trust?department=Research` | Department-scoped scores      |
| GET    | `/v1/organizations/{org_id}/workers/{wrk_id}/trust`  | Single worker trust history      |

Trust is read-only at the API. It is recomputed by the Trust Engine
(see `08-trust-engine.md`) as evidence is upheld/retracted and
predictions are validated against outcomes.

---

## 9. GraphQL Subscriptions

GraphQL provides typed, selective real-time feeds over the same
WebSocket transport as raw events. Endpoint: `/v1/graphql`
(subprotocol `graphql-ws`). REST is still the write path; GraphQL is
the subscription path.

```graphql
type Subscription {
  organizationState(orgId: ID!): OrganizationStateEvent!
  investigationState(invId: ID!): InvestigationStateEvent!
  investigationProgress(invId: ID!): InvestigationProgressEvent!
  meetingLive(mtgId: ID!): MeetingEvent!                # union below
  evidenceChallenged(invId: ID!): EvidenceChallengeEvent!
  evolutionEvent(orgId: ID!): EvolutionEvent!
  trustUpdated(orgId: ID!): TrustUpdateEvent!
  messageStream(orgId: ID!, investigationId: ID): MessageEvent!
}
```

Example client subscription:

```graphql
subscription WatchInvestigation($invId: ID!) {
  investigationState(invId: $invId) { from to enteredAt confidence }
  investigationProgress(invId: $invId) {
    department activity task { id objective status } evidenceCount
  }
}
```

`MeetingEvent` is a union so clients can render a live transcript
without polling:

```graphql
union MeetingEvent =
    MeetingStateChanged
  | MeetingMessage
  | MeetingVoteCast
  | MeetingConcluded
```

---

## 10. WebSocket Event Streams

For low-latency live streaming of investigations and meetings (the
"feels alive" UX, see `22-mobile-ux.md`). Connect to the `stream_url`
returned by the resource. One connection per stream.

### 10.1 Frame Format

Every frame is a JSON text frame:

```json
{
  "stream": "investigation",            // "investigation" | "meeting" | "organization"
  "stream_id": "inv_01HXY...",
  "seq": 42,                            // monotonic per-stream, gap detection
  "event": "department.activity",
  "ts": "2026-08-05T17:02:11.123Z",
  "payload": { /* ... */ }
}
```

Clients track `seq` and request resync via `POST .../stream/resync?from=43`
if a gap is detected. The server buffers the last 1000 events per
stream for resync.

### 10.2 Investigation Stream Events

| event                       | payload (sketch)                                            |
|-----------------------------|-------------------------------------------------------------|
| `investigation.state`       | `{ from, to, reason }`                                      |
| `department.activity`       | `{ department, worker, activity, task_id }`                 |
| `evidence.submitted`        | `{ evidence_id, by, claim, confidence }`                    |
| `evidence.challenged`       | `{ evidence_id, by, critique }`                             |
| `evidence.upheld`           | `{ evidence_id, resolution, trust_weight }`                 |
| `message.sent`              | `{ message_id, from, to, kind, body }`                      |
| `meeting.scheduled`         | `{ meeting_id, title, starts_at }`                           |
| `meeting.started`           | `{ meeting_id }`                                             |
| `meeting.message`           | `{ meeting_id, speaker, body, kind }`                       |
| `meeting.vote`               | `{ meeting_id, voter, vote, weight }`                       |
| `meeting.concluded`         | `{ meeting_id, outcome }`                                    |
| `investigation.confidence`  | `{ confidence, threshold, will_research_again }`            |
| `investigation.report_ready`| `{ report_id, formats: ["pdf","md","docx"] }`               |

### 10.3 Meeting Stream Events

A dedicated stream for a single meeting. Higher fidelity than the
investigation stream's meeting events — includes turn-taking and
deliberation context (see `10-debate-engine.md`):

```json
{
  "stream": "meeting",
  "stream_id": "mtg_01HXY...",
  "seq": 7,
  "event": "turn.taken",
  "ts": "2026-08-05T17:02:14.500Z",
  "payload": {
    "speaker": "wrk_01HXY...",
    "department": "Engineering",
    "turn_id": "trn_01HXY...",
    "in_reply_to": "trn_01HXY...",
    "stance": "dissent",
    "body": "The market sizing assumes a 40% CAGR with n=12. I reject."
  }
}
```

| event              | meaning                                            |
|--------------------|----------------------------------------------------|
| `turn.taken`       | A participant took the floor                        |
| `turn.yielded`     | A participant yielded                               |
| `vote.cast`        | A vote was recorded (with weight)                  |
| `vote.tally`       | Running tally                                       |
| `meeting.concluded`| Final outcome and minutes pointer                    |

### 10.4 Backpressure & Lifecycle

- The server sends a `stream.heartbeat` every 5s; missing 3 in a row
  → client should reconnect and resync.
- On `investigation.report_ready` or `meeting.concluded`, the server
  sends the terminal event and closes the stream with code 1000.
- Inbound frames from the client are limited to `stream_msgs_in`
  (§4) and are only accepted on meeting streams where the principal
  has `meeting:write`. Inbound frames use the same envelope with
  `event: "client.message"` and a server-assigned `seq`.

---

## 11. Webhooks (Outbound)

For integrations that cannot hold a WebSocket. Configured per org:

```
POST /v1/organizations/{org_id}/webhooks
{ "url": "https://hooks.example.com/veliora",
  "events": ["investigation.completed","evolution.applied","meeting.concluded"],
  "secret": "whsec_..." }
```

Deliveries are signed `HMAC-SHA256` over the raw body with the
`secret`, sent in `X-Veliora-Signature`. Retries with exponential
backoff up to 24h; delivery log queryable at
`GET /v1/organizations/{org_id}/webhooks/deliveries`.

---

## 12. Cross-Document References

| Topic                       | See                                                |
|-----------------------------|----------------------------------------------------|
| System topology             | `04-technical-architecture.md`                     |
| State transitions           | `25-state-machine.md`                              |
| Event bus internals         | `16-event-system.md`                                |
| Investigation orchestration  | `19-investigation-engine.md`                       |
| Meeting mechanics           | `20-meeting-engine.md`                              |
| Trust scoring               | `08-trust-engine.md`                                |
| DNA parameters              | `21-organizational-dna.md`                          |
| Evolution engine            | `07-evolution-engine.md`                            |
| Constitution engine         | `09-constitution-engine.md`                         |
| Memory graph                | `06-memory-system.md`                               |
| External comms              | `12-caspian-integration.md`                         |
| Security model              | `26-security.md`                                    |
| Scaling the gateway          | `27-scaling-strategy.md`                            |
