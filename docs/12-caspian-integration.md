# 12 — Caspian Integration

> The external communication nervous system of Veliora.
>
> **Status:** Design doc
> **Depends on:** `04-technical-architecture.md`, `05-agent-architecture.md`,
> `06-memory-system.md`, `08-trust-engine.md`, `09-constitution-engine.md`,
> `11-communication-engine.md`, `14-knowledge-graph.md`,
> `16-event-system.md`, `21-organizational-dna.md`
> **Consumed by:** `19-investigation-engine.md`, `24-api-design.md`,
> `26-security.md`

---

## 1. Purpose

Caspian is the bridge between Veliora and the real world. Through
Caspian, agents **talk to real humans and services**: Research emails a
professor, Marketing runs a Telegram survey, Finance messages an
accountant, Legal consults a lawyer, Recruiter reaches a candidate.
The organization does not merely *search* the web — it actively
*gathers evidence from people* (contribution #9, "External
Intelligence," `02-research-contributions.md`).

This document specifies how outbound messages are drafted, approved,
sent, and tracked; how inbound responses are routed and incorporated
as evidence; how external contacts are managed; and how privacy and
consent are enforced. It is the **external** counterpart to the
internal Communication Engine (`11-communication-engine.md`).

### 1.1 The "One Agent" Test

A single AI agent could in principle send an email. So why is Caspian
integration an *organizational* feature?

- **Channel selection** is driven by *which department owns the
  relationship*, not by a single agent's preference.
- **Outbound approval** is gated by the *constitution* and the *trust*
  of the sending agent — no single agent speaks for the org unchecked.
- **Inbound routing** dispatches a reply to the *right* agent by
  context, not to whoever happens to be listening.
- **Evidence incorporation** routes external replies into the
  *knowledge graph* and *trust system*, where they are then *challenged
  by other departments* (`10-debate-engine.md`).
- **Contact management** is an *organizational asset*: the org owns the
  relationship with the professor, not any one agent.

The unit of external communication is the **organization**, acting
through a delegated agent. Every design choice below reflects that.

---

## 2. Caspian in the System Topology

```
                       ┌──────────────────────────────────────┐
                       │          Veliora Organization        │
                       │  ┌─────────┐  internal   ┌──────────┐  │
                       │  │ Agents  │◄──messages──│ Comm.     │  │
                       │  │ (depts) │             │ Engine    │  │
                       │  └────┬────┘            └────┬─────┘  │
                       │       │  draft/approve/send │          │
                       │       ▼                     ▼          │
                       │  ┌──────────────────────────────────┐ │
                       │  │        Caspian Adapter            │ │
                       │  │ (channel select, lifecycle,       │ │
                       │  │  contact lookup, consent check)   │ │
                       │  └────────────────┬─────────────────┘ │
                       └───────────────────┼───────────────────┘
                                           │ outbound
                                           ▼
        ┌──────────────────────────────────────────────────────────────┐
        │                      Caspian (external)                        │
        │  ┌──────┐ ┌─────────┐ ┌───────┐ ┌────────┐ ┌──────────┐       │
        │  │Email │ │Telegram │ │ Slack │ │Discord │ │WhatsApp │       │
        │  └──┬──┘ └────┬────┘ └───┬───┘ └───┬────┘ └────┬─────┘       │
        └─────┼─────────┼──────────┼──────────┼───────────┼────────────┘
              ▼         ▼          ▼          ▼           ▼
          ┌──────┐ ┌──────────┐ ┌────────┐ ┌─────────┐ ┌──────────┐
          │Prof. │ │Founder   │ │#mktg   │ │#eng-dev │ │Beta user │
          │Lee   │ │@alice    │ │channel │ │server   │ │+1555...  │
          └──────┘ └──────────┘ └────────┘ └─────────┘ └──────────┘
```

The Caspian Adapter is the only component inside Veliora that talks
to the outside world. Agents never touch Caspian directly — they go
through the adapter, which enforces channel selection, approval,
consent, and audit (trust boundary in `26-security.md`).

---

## 3. Channel Catalog

Caspian exposes a normalized set of channels. Each channel's
characteristics drive selection (§4).

| Channel  | Latency      | Richness                | Async | Identity Verifiable | Typical Use                                  |
|----------|--------------|-------------------------|:-----:|:--------------------:|----------------------------------------------|
| Email    | minutes–days | high (files, long-form) | yes   | strong (SMTP/DKIM)   | Professors, lawyers, accountants, formal     |
| Telegram | seconds      | medium (text, media, polls) | mostly | medium (handle) | Founders, beta users, quick surveys           |
| Slack    | seconds      | medium (threads, files) | mostly| strong (workspace)   | Partner teams, vendor PMs                     |
| Discord  | seconds      | medium (threads, voice) | mostly| medium (handle)     | Developer communities, open-source SIGs       |
| WhatsApp | seconds      | medium (text, media)    | mostly| strong (phone)      | Consumer-facing, WhatsApp-default regions      |

All channels are accessed through a single Caspian API; the adapter
handles per-channel idiosyncrasies (polls, threads, attachments, rate
limits).

---

## 4. Channel Selection Logic

Channel selection is a function of the **contact**, the **purpose**,
and the **DNA**. The Caspian Adapter evaluates the decision tree below
on every outbound message.

### 4.1 Decision Tree

```
                    ┌─────────────────────────────────┐
                    │  Outbound message requested       │
                    │  (agent, purpose, contact)        │
                    └────────────────┬────────────────┘
                                     │
                      ┌──────────────▼──────────────┐
                      │ Does the contact have a      │
                      │ preferred channel on file?   │
                      └──────────────┬──────────────┘
                              yes    │    no
                  ┌─────────────────┴─────────────────┐
            ┌─────▼──────────┐              ┌──────────▼──────────┐
            │ Use preferred   │              │ Pick by purpose:    │
            │ channel (skip  │              │  formal → Email      │
            │ to §5 lifecycle)│             │  survey → Telegram   │
            └────────────────┘              │  dev_qa → Discord    │
                                            │  vendor → Slack      │
                                            │  consumer → WhatsApp │
                                            └──────────┬──────────┘
                                                       │
                                       ┌────────────────▼──────────────┐
                                       │ DNA: Speed high → prefer async; │
                                       │ Security high → prefer          │
                                       │  verifiable identity             │
                                       │  (Email/Slack > Telegram)        │
                                       └────────────────┬──────────────┘
                                                       │
                                       ┌────────────────▼──────────────┐
                                       │ Consent + rate-limit check (§9) │
                                       │ Pass? → send. Fail? → queue.   │
                                       └───────────────────────────────┘
```

### 4.2 Selection Inputs

| Input                       | Source                                                  |
|-----------------------------|---------------------------------------------------------|
| `contact.preferred_channel` | Contact record (§8)                                     |
| `purpose`                   | Agent's request: `formal`, `survey`, `dev_qa`, `vendor`, `consumer`, `recruiting`, `support` |
| `org_dna`                   | `21-organizational-dna.md` — Speed, Security, Transparency |
| `channel_health`            | Recent delivery success rate per channel (Caspian telemetry) |
| `consent_state`             | §9 — does the contact have consent for this purpose?    |
| `rate_limit`                | Per-channel, per-contact throttles (anti-spam)         |

If the preferred channel fails (bounce, no reply within
`channel.expected_reply_window`), the adapter may fall back to a
second channel **only if** the contact record permits it. Silent
fallback is forbidden — the contact must have opted into multiple
channels.

---

## 5. Outbound Communication Lifecycle

Every outbound message goes through seven stages. The lifecycle is
the unit of audit and the basis for the "communication is visible"
principle in `SPEC.md`.

```
draft → review → send → wait → receive → process → store
  │       │       │       │         │          │         │
  │       │       │       │         │          │         └─ archive + KG node
  │       │       │       │         │          └─── route to agent, challenge
  │       │       │       │         └────────── inbound adapter (§6)
  │       │       │       └────────── ack window, reminders
  │       │       └────────── Caspian send
  │       └────────── constitutional / authority check
  └────────── agent composes
```

**Draft** — The agent composes a draft using a channel-appropriate
prompt template (`17-prompt-architecture.md`), including `to` (a
contact ID, §8), `channel`, `purpose`, `subject`, `body`, the
`expected_reply_window`, and `evidence_intent`:

```json
{
  "draft_id": "draft_01HZ...", "org_id": "org_01HZ...",
  "investigation_id": "inv_01HZ...", "requested_by": "agent.research_lead",
  "to": "contact.professor_lee", "channel": "email", "purpose": "formal",
  "subject": "Question on RWE methods for AI triage validation",
  "body": "Dear Prof. Lee, ...", "expected_reply_window": "72h",
  "evidence_intent": "expert_opinion"
}
```

**Review** — The adapter checks whether the draft needs constitutional
approval (`09-constitution-engine.md`). The approval policy is part of
the constitution, not the adapter. Typical rules:

| Trigger                                              | Required Approval      |
|------------------------------------------------------|------------------------|
| First contact with a new external party               | Dept lead              |
| Message to a regulated profession (lawyer, doctor)    | Legal dept sign-off    |
| Public-facing post (Slack/Discord public channel)     | CEO + constitution     |
| Message containing org-confidential data              | Constitution (auto-deny if not whitelisted) |
| Routine follow-up to existing thread                  | None (auto-approve)    |

If approval is required and not granted, the draft is **rejected**, the
rejection is logged, and the requesting agent is notified via an
internal `alert` (`11-communication-engine.md` §3). The agent may
revise and resubmit.

**Send** — On approval, the adapter calls Caspian with the normalized
payload. Caspian returns a `caspian_msg_id` and delivery status. The
adapter records an `outbound_messages` row keyed by `caspian_msg_id`
(`draft_id`, `org_id`, `investigation_id`, `contact_id`, `channel`,
`sent_at`, `delivered_at`, `status`, `approved_by`).

**Wait** — The adapter opens an ack window of `expected_reply_window`.
If no inbound arrives, it sends up to `N` reminders (default `N=1`,
tunable by DNA — high-Speed orgs remind sooner, high-Long-Term-Thinking
orgs wait longer). A lapsed window is recorded as a
`missed_external_response` event and surfaced to the requesting agent.

**Receive** — Inbound messages arrive via Caspian webhooks (or polling
for channels without push). The inbound adapter normalizes them into a
canonical inbound envelope (§6.1).

**Process** — The inbound is routed to the right agent (§6.2), and —
critically — the content is **incorporated as evidence** (§7). This is
where Caspian integration stops being "messaging" and starts being
"organizational intelligence."

**Store** — The outbound, any reminders, and the inbound are archived
together as a single `external_thread`, linked to a knowledge graph
node (`14-knowledge-graph.md`) and searchable via the internal
Communication Engine's search API (`11-communication-engine.md` §10).

---

## 6. Inbound Communication Handling

### 6.1 Canonical Inbound Envelope

```json
{
  "caspian_msg_id": "cas_in_01HZ...",
  "in_reply_to": "cas_out_01HZ...",
  "org_id": "org_01HZ...",
  "channel": "email",
  "from_contact": "contact.professor_lee",
  "from_identity": { "email": "lee@university.edu", "dkim_pass": true },
  "received_at": "2026-08-05T18:02:00Z",
  "subject": "Re: Question on RWE methods ...",
  "body_text": "...",
  "body_structured": { "answers": [ ... ], "poll_results": null },
  "trust_signals": { "identity_verified": true,
                     "prior_thread_count": 3, "contact_trust": 0.71 }
}
```

### 6.2 Routing to the Right Agent

Inbound routing is **not** "whoever sent the outbound." People reply
late, forward to colleagues, or send unsolicited follow-ups. The
adapter routes based on:

1. **Thread match** — If `in_reply_to` resolves to an outbound, route
   to that outbound's `requested_by` agent, *unless* that agent has
   been retired or reassigned (`07-evolution-engine.md`).
2. **Topic match** — If no thread match, classify the inbound topic
   and route to the department that owns it (topic ownership lives in
   the knowledge graph, `14-knowledge-graph.md`).
3. **Escalation** — If routing is ambiguous, route to the CEO, who may
   delegate. The CEO's routing decision becomes a training signal for
   future routing.

Routing is logged so the org can audit "who handled the professor's
reply and why."

### 6.3 Inbound as Internal Message

Once routed, the inbound is wrapped as an internal `evidence_submission`
message (`11-communication-engine.md` §3) and posted to the relevant
department channel, then flows through the normal Evidence Challenge
process (`10-debate-engine.md`):

```
External reply ──► Inbound Adapter ──► Internal evidence_submission
                                         │  open_to_challenge = true
                                         ▼
                                  Department channel
                                         │
                                  Other departments may challenge / support
                                         │
                                         ▼
                                  Debate Engine → verdict
```

This is the heart of contribution #7 ("Evidence Challenge"): the
professor's reply is not accepted uncritically. Engineering may say
"sample size too small," Marketing may say "this matches our survey,"
Research updates its conclusions. The final recommendation survives
peer review.

---

## 7. Evidence Incorporation: Knowledge Graph and Trust

### 7.1 Knowledge Graph Node

Every inbound carrying substantive content creates (or updates) a
node in the knowledge graph (`14-knowledge-graph.md`):

```
kg_node {
  id:           kg_01HZ...
  type:         external_evidence
  source:       caspian:cas_in_01HZ...
  contact:      contact.professor_lee
  contact_trust: 0.71
  claim:        "RWE validation for AI triage requires n>=1000 across 3 sites"
  confidence:   0.66                    // f(contact_trust, identity, corroboration)
  submitted_by: agent.research_lead
  challenged_by: [agent.engineering_lead] // filled by debate engine
  status:       active | refuted | superseded
  investigation_id: inv_01HZ...
}
```

### 7.2 Trust Effects (Two Directions)

- **Contact trust** — A contact whose evidence is repeatedly
  corroborated by other sources gains trust; one whose evidence is
  repeatedly refuted loses trust. This is the external analog of the
  internal Trust Engine (`08-trust-engine.md`).
- **Agent trust** — The agent that *submitted* the evidence gains or
  loses trust depending on whether it holds up under challenge. An
  agent that repeatedly cites low-trust contacts without
  corroboration loses trust.

### 7.3 Corroboration Rule (DNA-Driven)

Per DNA `Accuracy` (`21-organizational-dna.md`):

| `Accuracy`        | Corroboration Rule for External Evidence                 |
|------------------|----------------------------------------------------------|
| low (<0.4)       | Single external source accepted at face value            |
| mid (0.4–0.7)    | Requires 1 independent internal source OR 2 external     |
| high (>0.7)      | Requires 2 independent external sources AND survives challenge |

This is why two identical prompts to two different orgs can produce
different conclusions: a high-Accuracy org refuses to act on a single
professor's email; a low-Accuracy org proceeds.

---

## 8. External Contact Management

Contacts are **organizational assets**, not personal address books. No
agent "owns" a contact; agents have *stewardship* of a relationship.

### 8.1 Contact Record

```json
{
  "contact_id": "contact.professor_lee",
  "org_id": "org_01HZ...",
  "display_name": "Prof. Lee",
  "identities": [
    { "channel": "email",   "address": "lee@university.edu", "verified": true },
    { "channel": "telegram", "address": "@prof_lee",          "verified": false }
  ],
  "preferred_channel": "email",
  "consent": { "purposes": ["formal", "research_followup"],
               "granted_at": "2026-08-01T10:00:00Z",
               "expires_at": "2027-08-01T10:00:00Z" },
  "steward_dept": "research",
  "steward_agent": "agent.research_lead",
  "contact_trust": 0.71,
  "stewardship_history": [ ... ]
}
```

### 8.2 Stewardship Transfer and Discovery

When an agent is retired or a department restructured
(`07-evolution-engine.md`), stewardship transfers to a successor; the
`stewardship_history` field preserves who built each relationship —
institutional memory (`06-memory-system.md`) applied externally.

Contacts enter the registry through: **user-provided** (the user seeds
the org with contacts); **investigation-discovered** (Research finds a
relevant expert, the CEO approves, and the first message is treated as
cold outreach with stricter review, §5); or **inbound-initiated** (an
unsolicited inbound creates a *provisional* contact with
`contact_trust = 0.2` until the org decides to engage).

---

## 9. Privacy and Consent Management

External communication is the highest-risk surface in Veliora
(`26-security.md`). The adapter enforces consent and privacy at every
stage.

### 9.1 Consent Model

Consent is **purpose-scoped**, **time-bounded**, and **revocable**. A
contact who consented to `formal` follow-up has *not* consented to
`survey` — sending a survey requires fresh consent or a
constitutionally approved override. Consent expires (default 1 year),
triggering a re-consent flow before any new outbound. A contact can
revoke via any inbound ("please stop"); the adapter parses opt-out
signals, immediately marks the contact `do_not_contact`, and re-
contacting a revoked contact is a constitutional violation that emits
a critical `alert`.

### 9.2 PII Handling and Rate Limiting

Contact identity fields (email, phone, handle) are stored encrypted at
rest (`13-database-design.md`, `26-security.md`). Internal messages
quoting external replies must not include PII beyond what is
necessary; the adapter redacts per a channel policy before posting to
internal channels. High-`Transparency` DNA orgs mirror internal
messages to `all_hands` (`11-communication-engine.md` §9), so the
adapter applies an **extra** redaction pass on any
externally-originated message, regardless of DNA, to prevent PII
leaking org-wide.

| Limit                        | Default | Tunable By        |
|------------------------------|---------|-------------------|
| Outbound per contact / day    | 3       | DNA `Security`    |
| Outbound per channel / hour   | 50      | Caspian config    |
| Reminders per thread          | 1       | DNA `Speed`       |
| Cold outreach per org / day  | 10      | Constitution      |

Hitting a limit queues the message and emits a `rate_limited` event;
it does not silently drop. The requesting agent sees a `status_update`
explaining the delay. Every outbound and inbound is appended to a
tamper-evident audit log (hashed chain) — the basis for post-hoc
review of "who said what to whom and why," regulatory compliance (Legal
interprets, `09-constitution-engine.md`), and trust adjustments when a
contact or agent is investigated.

---

## 10. Constitutional Approval for Outbound

The constitution (`09-constitution-engine.md`) defines which outbound
communications require approval and from whom. The adapter does **not**
decide policy — it enforces it. A clause might auto-approve formal
thread continuations, require a dept lead for first contact, require
Legal for regulated professions, require the CEO for public channels,
and deny any message containing org-confidential data unless
whitelisted or any message to a contact with revoked consent.

```
draft ──► adapter evaluates constitution ──► auto_approve? ──► send
                                          │
                                          └─► require_approval? ──► route to approver
                                                                   │
                                              approve ─► send       │
                                              deny ─────► reject + alert
```

Approvals are themselves internal `report` messages addressed to the
requesting agent, so the decision is archived and searchable.

---

## 11. Integration with Other Engines

| Engine                   | Relationship                                                          |
|--------------------------|----------------------------------------------------------------------|
| `05-agent-architecture`  | Agents are initiators and recipients; they never touch Caspian.       |
| `06-memory-system`       | External threads are part of institutional memory.                   |
| `07-evolution-engine`    | Stewardship transfers on agent/department changes.                   |
| `08-trust-engine`        | Contact trust and agent trust both update from external evidence.   |
| `09-constitution-engine`| Defines outbound approval policy (§10).                            |
| `10-debate-engine`       | External evidence is challenged here; verdicts update KG nodes.    |
| `11-communication-engine`| Inbound is wrapped as internal `evidence_submission`.                |
| `14-knowledge-graph`     | External evidence becomes KG nodes (§7.1).                          |
| `16-event-system`        | `outbound_sent`, `inbound_received`, `consent_revoked` events.       |
| `17-prompt-architecture` | Channel-specific draft templates.                                    |
| `19-investigation-engine`| Investigations are the main driver of external outreach.            |
| `21-organizational-dna`  | DNA shapes channel selection, reminders, corroboration rules.       |
| `26-security`            | PII, audit log, trust boundary.                                     |

---

## 12. API Surface

The Caspian Adapter exposes an internal API (public REST surface in
`24-api-design.md`):

```
POST /internal/caspian/draft                    # create a draft (§5)
POST /internal/caspian/draft/{id}/submit        # submit for review + send
GET  /internal/caspian/threads                   # list external threads
GET  /internal/caspian/threads/{id}             # full thread incl. inbound
POST /internal/caspian/contacts                 # add contact (§8.2)
GET  /internal/caspian/contacts/{id}
PATCH /internal/caspian/contacts/{id}/consent   # update consent
POST /internal/caspian/inbound/webhook/{channel} # Caspian webhook target
```

A draft is created with `POST /internal/caspian/draft`, then submitted
with `POST /internal/caspian/draft/{id}/submit`, which triggers review
and send. Inbound webhooks carry an `X-Caspian-Signature` header; the
adapter verifies the signature, normalizes to the inbound envelope
(§6.1), and proceeds with routing.

---

## 13. MVP Scope

For the hackathon MVP (`28-mvp-hackathon.md`), the Caspian Adapter
implements: **Email** and **Telegram** channels only (§3) — Telegram
for the live survey demo, Email for the professor outreach demo; the
full outbound lifecycle (§5) with a simplified review step (first
contact requires CEO approval, everything else auto-approves);
inbound routing by thread match only (§6.2 step 1), with topic match
deferred; the contact registry with stewardship (§8) but no
stewardship transfer yet (no agent retirement in MVP); the consent
model (§9) with purpose-scoping and revocation, time-bounding
hardcoded to 1 year; knowledge graph node creation for inbound (§7.1)
and contact trust updates (§7.2), with agent trust from external
evidence deferred; and the corroboration rule fixed at the "mid" tier
(§7.3) — no DNA-driven variation in MVP.

Deferred: Slack, Discord, WhatsApp channels; topic-match inbound
routing and CEO escalation routing; stewardship transfer on evolution
events; DNA-driven corroboration and reminder timing;
cross-organization contact sharing (contribution #10); and external
attestation of the audit log (MVP uses append-only with a hash chain).
