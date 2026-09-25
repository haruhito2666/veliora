# 10 — Debate Engine

> Society-of-Thought debate mechanics. Where Chain-of-Thought ends,
> Society-of-Thought begins.

## 1. Overview

In a single-agent system, reasoning happens inside one model's hidden
context — a Chain-of-Thought the user never sees and no one challenges.
Veliora replaces this with **Society-of-Thought**: reasoning that
happens *between* agents, in the open, under cross-examination, with
real disagreement and a recorded verdict.

The Debate Engine orchestrates this. It is *not* a chat room and *not*
roleplay. It is a structured, protocol-governed proceeding in which
departments with **real, conflicting objectives** argue from their
mandate and DNA until a defensible position survives peer review.

```
Chain-of-Thought (single agent):
    Premise → Step → Step → Step → Conclusion
    (hidden, unchallenged, unrecorded)

Society-of-Thought (organization):
    Premise → [Research argues] ↔ [Engineering challenges]
            ↔ [Finance counters] ↔ [Legal objects]
            → Synthesis → Vote → Recorded Verdict
    (visible, challenged, recorded, weighted by trust)
```

The engine is invoked by the Investigation Engine (see
`19-investigation-engine.md`) whenever a claim, proposal, policy, or
strategic fork needs to survive organizational scrutiny. It is also
invoked ad-hoc by any agent who formally challenges another's output
(§4.1).

## 2. The One Agent Test

> *Would this feature still exist if there were only one AI agent?*

A debate requires at least two parties with independent objectives. A
single agent has no independent mandate to represent, no separate
trust score to weigh, no department whose interests it is bound to
defend. The Debate Engine exists *only because* Veliora is an
organization. Every primitive here — cross-examination, weighted votes,
dissenting opinions, evidence challenges — is meaningless without
multiple agents. If a feature could be replaced by a single prompt
asking the model to "argue both sides," it is wrong. The point is not
to *simulate* disagreement but to *produce* it, from agents whose
mandates genuinely conflict.

## 3. Debate vs Roleplay — The Critical Distinction

The most misunderstood aspect of the engine, and the one most likely
to be implemented wrong.

| Aspect | Roleplay (wrong) | Veliora Debate (right) |
|--------|------------------|--------------------------|
| Position source | Assigned by a prompt | Derived from department mandate + DNA |
| Motivation | Performative — to fill a role | Instrumental — to advance real objectives |
| Switching sides | Trivial — re-prompt | Impossible — an agent cannot abandon its mandate |
| Stake in outcome | None — it's a simulation | Real — the outcome changes what the org does next |
| Trust weighting | Irrelevant | Central — high-trust agents sway votes |
| Memory | Discarded | Recorded in institutional memory with full transcript |
| Termination | When the prompt says so | Consensus, timeout, or vote |

An agent in Veliora does not "play" Research. It *is* Research. Its
department has a mandate ("establish empirical truth through
evidence"), a trust score (e.g., 96% historical accuracy), a budget,
and a history of prior positions. It argues for the position that
best serves its mandate given the evidence it has — not the position
it was told to argue.

This means debates can be **asymmetric**. Research may have
overwhelming evidence and a strong case; Marketing may have weak
evidence and lose. That loss is recorded and affects Marketing's
trust. In roleplay, both sides are usually balanced by construction.
In Veliora, the imbalance is the signal.

## 4. Debate Types

Four types, each with a distinct trigger, structure, and outcome. All
share the six-phase protocol (§5) but differ in scope, participants,
and what the verdict means.

### 4.1 Evidence Challenge

**Trigger.** One agent formally challenges another's submitted
evidence, via the `evidence.challenge` event (see
`16-event-system.md`).

**Question.** "Is this evidence sufficient to support the claim made
from it?"

**Participants.** The challenger, the submitter, and a peer panel drawn
from unrelated departments (to avoid tribal defense).

**Outcome.** `UPHELD` (claim survives), `QUALIFIED` (claim narrowed),
`REJECTED` (evidence discarded, claim withdrawn), or `REBUTTED`
(counter-evidence accepted, claim inverted).

**Example.** Research cites a paper showing 87% user retention.
Engineering challenges: n=23, single cohort, no control. Panel
returns `QUALIFIED` — the claim narrows to "one small cohort of one
competitor retained users well."

### 4.2 Position Debate

**Trigger.** A proposal is put forward and at least one department
files `position.objection`.

**Question.** "Should the organization adopt this proposal?"

**Participants.** All departments whose mandate intersects the
proposal. The Constitution Engine (see `09-constitution-engine.md`)
determines standing — a department with no stake has no vote.

**Outcome.** Binding: `ADOPT`, `ADOPT_WITH_AMENDMENTS`, `REJECT`, or
`DEFER` (send back for more investigation).

### 4.3 Policy Debate

**Trigger.** A proposed change to internal rules — a new policy, a
constitutional amendment, a DNA parameter change, a hiring/firing
decision, a department split/merge. See `07-evolution-engine.md`.

**Question.** "Should the organization change its own rules in this
way?"

**Participants.** All agents in the organization — policy debates
govern the org itself, so every agent has standing. The Constitution
Engine may require a supermajority depending on the change type.

**Outcome.** `AMEND`, `REJECT`, or `REFERENDUM` (escalate to a
full-org vote including agents not in the debate). Thresholds in
`09-constitution-engine.md`.

### 4.4 Strategic Debate

**Trigger.** The organization faces a fork: two or more viable paths
and the Planner cannot resolve on cost alone. See
`18-planner-design.md`.

**Question.** "Which option should the organization pursue?"

**Participants.** A proponent for each option (self-nominated or
assigned by the CEO by mandate fit), plus a neutral synthesis panel.

**Outcome.** A ranked preference: `OPTION_A > OPTION_B > OPTION_C`,
with a recorded dissenting opinion for any non-winning option.

```
Debate Type        Trigger                    Binding?   Supermajority?
──────────────────────────────────────────────────────────────────────
Evidence Challenge  evidence.challenge event   No*        No
Position Debate    position.objection event    Yes        No
Policy Debate      constitution.amend event   Yes        Often yes
Strategic Debate   planner.fork event         Yes        No

* Binding on the claim, not on the org's direction; the investigation
  absorbs the verdict.
```

## 5. The Six-Phase Debate Protocol

Every debate runs through six phases, enforced by the Debate Engine
state machine (see `25-state-machine.md`).

```
┌──────────┐   ┌──────────┐   ┌───────────────┐
│ 1.OPENING │──▶│ 2.EVIDENCE│──▶│ 3.CROSS-EXAM │
└──────────┘   └──────────┘   └──────┬───────┘
                                      ▼
┌──────────┐   ┌──────────┐   ┌───────────────┐
│ 6.VOTE   │◀──│ 5.SYNTHESIS│◀─│ 4.REBUTTALS  │
└──────────┘   └──────────┘   └───────────────┘
```

**Phase 1 — Opening Positions.** Each participant states its position
and the mandate-grounded reason. No evidence yet — the *stance*, not
the *case*. One opening per participant, in trust-rank order (highest
trust first — their framing anchors the debate). Each opening is
capped at `opening_turn_tokens` from the DNA-derived budget (§7). A
participant may `PASS` ("no strong view, will decide on evidence").

**Phase 2 — Evidence Presentation.** Each participant presents
evidence supporting its position. Evidence is structured (see
`06-memory-system.md`), not free text:

```
EvidenceRecord {
  id, debate_id, submitter,
  claim, source_type, source_ref, confidence, collected_at
}
```

Evidence may be challenged on the spot, spawning a nested Evidence
Challenge (§4.1) that must resolve before the parent continues. This
recursion is what makes Society-of-Thought self-cleaning.

**Phase 3 — Cross-Examination.** Participants question one another's
evidence and reasoning. A participant issues a `question` directed at
a specific participant and a specific piece of evidence. The target
*must* respond — refusal is recorded as `evasion` and penalized in
trust (see `08-trust-engine.md`). This is where weak evidence dies.

**Phase 4 — Rebuttals.** Each participant gets one rebuttal turn to
address the strongest objection from cross-examination. New evidence
is not permitted — only re-interpretation of evidence on the record.
This prevents endless escalation.

**Phase 5 — Synthesis.** A neutral synthesis agent (the CEO, or a
rotating duty for policy debates) produces a *single* synthesized
position reconciling the strongest arguments. The synthesis is not a
compromise — it is the best defensible position given the evidence. If
no synthesis is possible, the synthesis records the disagreement and
the debate proceeds to a vote on the unreconciled positions.

**Phase 6 — Vote.** Each participant with standing casts a vote,
weighted by trust (§8.4). The result is recorded with the full tally,
individual votes, and any dissenting opinions. Dissenting opinions
are first-class outputs — delivered to the user alongside the verdict,
not buried.

## 6. Formal Debate Protocol Specification

The protocol is a sequence of typed messages on the `debate.*` event
channel (see `16-event-system.md`). All messages are signed by the
originating agent and persisted to the debate transcript.

### 6.1 Message Types

```
debate.open        { debate_id, type, topic, participants, deadline }
debate.position    { debate_id, agent_id, position, rationale }
debate.evidence    { debate_id, agent_id, evidence: EvidenceRecord }
debate.challenge   { debate_id, challenger, target_evidence, basis }
debate.question    { debate_id, from, to, re: evidence_id|position_id, text }
debate.answer      { debate_id, from, to, text }
debate.rebuttal    { debate_id, agent_id, re: question_id|challenge_id, text }
debate.synthesis   { debate_id, synthesizer, synthesized_position, unresolved }
debate.vote        { debate_id, agent_id, vote, weight, rationale }
debate.close       { debate_id, verdict, dissenting_opinions, transcript_ref }
```

### 6.2 Ordering Rules

1. `position` accepted only in Phase 1, in trust-rank order.
   Out-of-order positions are queued, not rejected.
2. `evidence` accepted only in Phase 2. A `challenge` in Phase 2
   suspends the parent and opens a nested Evidence Challenge; the
   parent resumes when the nested debate closes.
3. `question`/`answer` accepted only in Phase 3. An unanswered
   question after `cross_examine_timeout` becomes an `evasion` record.
4. `rebuttal` accepted only in Phase 4, one per agent, no new
   `evidence`.
5. `synthesis` produced exactly once, by the designated synthesizer.
6. `vote` accepted only in Phase 6, before `vote_deadline`. Late
   votes are recorded as `abstain`.

### 6.3 State Machine

```
IDLE ──open──▶ OPENING ──▶ EVIDENCE ──▶ CROSS-EXAM ──▶ REBUTTAL
                                  │ challenge        │
                                  ▼                 ▼
                              NESTED DEBATE     SYNTHESIS ──▶ VOTE ──▶ close ──▶ IDLE
                                  │                 ▲
                                  └──── close ───────┘
```

## 7. DNA Parameters and Debate Style

Organizational DNA (see `21-organizational-dna.md`) shapes *how*
debates happen, not *whether*. The engine reads DNA at debate open
and derives concrete protocol parameters.

| DNA Parameter | High Value Effect | Low Value Effect |
|---------------|------------------|------------------|
| Transparency | Debates fully visible in live view; transcript published | Internal; user sees only verdict + dissent |
| Hierarchy | Only higher-trust agents may challenge | Any agent may challenge any other |
| Speed | Short deadlines, fewer rounds, small token budgets | Long deadlines, many rounds, large budgets |
| Accuracy | Synthesis required; unresolved debates deferred | Synthesis best-effort; vote on available evidence |
| Risk | Rebuttals may introduce new evidence; challenges encouraged | Rebuttals evidence-locked; challenges discouraged |
| Innovation | Strategic debates common; forks debated even when one path is cheaper | Strategic debates rare; Planner resolves most forks on cost |
| Long-Term Thinking | Policy debates weight future-state arguments | Policy debates weight current-state arguments |
| Security | Strict source verification; anonymous evidence rejected | Evidence accepted on plausibility; verification best-effort |

### 7.1 Derived Protocol Parameters

```
opening_turn_tokens  = base_open   * (1 + Accuracy) / (1 + Speed)
evidence_turn_tokens = base_evid   * (1 + Accuracy) / (1 + Speed)
cross_examine_rounds = floor(3 * (1 + Accuracy) * (1 - Hierarchy))
rebuttal_rounds      = 1 + floor(Risk)        # 1 or 2
vote_deadline        = base_deadline * (1 + Long_Term) / (1 + Speed)

challenge_standing:
  Hierarchy > 0.7 → only agents with trust_rank >= target_rank - 1
  Hierarchy < 0.3 → any agent
  else            → agents in same or adjacent departments

visibility:
  Transparency > 0.7 → live_view=true,  publish_transcript=true
  Transparency < 0.3 → live_view=false, publish_transcript=false
  else               → live_view=true,  publish_transcript=false
```

### 7.2 Worked Example

A Startup Society with `Transparency=0.8, Hierarchy=0.2, Speed=0.6,
Accuracy=0.7` runs a Position Debate on "Build the MVP in Rust?":
any agent may challenge (Hierarchy 0.2); the debate is visible
(Transparency 0.8); cross-examination gets `floor(3 * 1.7 * 0.8) = 4`
rounds; rebuttals get `1 + floor(Risk)` rounds; the vote deadline is
moderate. The same proposal in a Hospital Society with `Hierarchy=0.8`
would silently queue objections from junior agents and resolve on
senior agents' positions alone. *Same protocol, different
organization, different debate.* This is the DNA effect.

## 8. Debate Termination

A debate ends in exactly one of three ways:

**8.1 Consensus.** All participants with standing converge on the
synthesized position before the deadline. The vote is recorded as
unanimous. Consensus is *not* required and *not* the default — it is
the exception. The engine does not engineer consensus; it records it
when it emerges.

**8.2 Timeout.** The `vote_deadline` passes. Uncast votes become
`abstain`. The verdict is computed from votes cast. Most common in
high-Speed orgs and for Evidence Challenges, where deadlines are short
by design.

**8.3 Vote.** The deadline arrives or all eligible votes are cast.
The weighted tally (§8.4) produces the verdict. A vote termination
always produces a recorded dissenting opinion for any minority
position above `dissent_threshold` (default 10% of weighted votes), so
minority positions are never silently dropped.

### 8.4 Trust-Weighted Voting

```
weighted_vote(agent, vote) = trust(agent) * mandate_relevance(agent, debate)

verdict = argmax_option  Σ weighted_vote(agent, vote) for vote == option
                       agent∈standing

pass_threshold = simple_majority   for Position/Strategic
               = supermajority (2/3) for Policy (constitution-defined)
```

`mandate_relevance` is a 0–1 score from the Constitution Engine
indicating how directly the topic falls in the agent's departmental
mandate. An agent voting far from its mandate contributes little
weight — this prevents Marketing from outvoting Engineering on a
technical architecture question purely because Marketing has higher
general trust.

## 9. Memory Recording

Every debate, on close, is persisted as an immutable `DebateRecord` in
institutional memory (see `06-memory-system.md`), referenced by all
future decisions that depend on the debated question.

```
DebateRecord {
  id, type, topic, opened_at, closed_at,
  termination:         consensus | timeout | vote
  participants:        [agent_id]
  evidence:            [EvidenceRecord]
  transcript_ref:      memory_uri
  synthesis:           string | null
  verdict:             upheld|qualified|rejected|rebutted
                     | adopt|adopt_with_amendments|reject|defer
                     | amend|referendum | ranked_preference
  vote_tally:          { option: weighted_score }
  individual_votes:    [ { agent, vote, weight, rationale } ]
  dissenting_opinions: [ { agent, position, rationale } ]
  trust_deltas:        [ { agent, delta, reason } ]
  related_investigation: iid | null
  related_memory:      [memory_uri]
}
```

The `trust_deltas` field is the bridge to the Trust Engine (see
`08-trust-engine.md`). After every debate:
- Agents whose evidence was `REJECTED` lose trust.
- Agents whose evidence survived cross-examination gain trust.
- Agents who voted against the eventual verdict lose a small amount
  (prediction penalty), *unless* they filed a dissenting opinion
  that later proves correct — in which case they gain trust
  retroactively. This is the long-game incentive that makes dissent
  rational rather than career-ending.

## 10. Example Debate Transcript

Condensed transcript of an Evidence Challenge in a Startup Society
investigating "Should we build an AI healthcare startup?" Shown as
the live view renders it, with phase markers.

```
DEBATE dc-0142  evidence_challenge
TOPIC   Research's claim: "Regulatory burden for AI healthcare is
        low and decreasing."  (evidence: paper, n=1, 2023)
PARTICIPANTS  Research (submitter), Legal (challenger),
              Engineering, Finance (peer panel)
DNA      Transparency=0.8 (visible), Hierarchy=0.3 (open challenge)

─── PHASE 1: OPENING POSITIONS ────────────────────────────────────
[Research, trust 0.96]  POSITION: claim stands.
    "The 2023 review surveys FDA AI/ML guidance updates and shows
    net reduction in approval friction YoY. Burden is low and
    decreasing."

[Legal, trust 0.91]  POSITION: claim is overstated.
    "One review is insufficient. It covers guidance, not
    enforcement. Enforcement actions increased in the same period.
    The claim conflates guidance velocity with regulatory burden."

[Engineering]  PASS — will decide on evidence.
[Finance]      PASS — will decide on evidence.

─── PHASE 2: EVIDENCE PRESENTATION ────────────────────────────────
[Research]  EVIDENCE e-0301  paper, peer-reviewed, n=1, conf 0.7
    "Guidance docs decreased 14→9 YoY; median time-to-comment
    fell 22%."

[Legal]  EVIDENCE e-0302  observation: FDA enforcement DB, conf 0.85
    "Warning letters citing AI/ML devices rose 3→11 in the same
    window. Guidance velocity ≠ burden."

[Legal]  CHALLENGE on e-0301
    basis: "n=1; source type mismatch — guidance count is not
    regulatory burden; conflates two variables."

─── PHASE 3: CROSS-EXAMINATION ────────────────────────────────────
[Legal → Research]  Q q-0088
    "Does your evidence distinguish guidance *issued* from burden
    *experienced* by applicants?"
[Research → Legal]  A a-0088
    "No. The review measures guidance output. I concede it does
    not measure applicant-experienced burden."

[Engineering → Research]  Q q-0089
    "Is the 22% time-to-comment reduction significant given n=1?"
[Research → Engineering]  A a-0089
    "The review reports it as descriptive, not inferential. I
    overreached in framing it as a trend."

─── PHASE 4: REBUTTALS ────────────────────────────────────────────
[Research]  R r-0044 (re: q-0088, q-0089)
    "I withdraw 'decreasing.' I maintain guidance output is
    falling, a leading indicator, but I cannot support 'burden is
    low and decreasing' from this evidence alone."

[Legal]  R r-0045 (re: a-0088)
    "Accepted. The leading-indicator argument is reasonable but is
    a hypothesis, not a finding. The claim should reflect that."

─── PHASE 5: SYNTHESIS ────────────────────────────────────────────
[CEO, synthesizer]  S s-0021
    "FDA AI/ML guidance output is declining, which may indicate
    reduced future burden, but current applicant-experienced
    burden is not established by the cited evidence. Enforcement
    actions are rising."
    unresolved: ["whether guidance decline predicts burden decline"]

─── PHASE 6: VOTE ────────────────────────────────────────────────
[Research]    QUALIFIED  0.96*0.4 = 0.384
[Legal]       QUALIFIED  0.91*0.9 = 0.819
[Engineering] QUALIFIED  0.82*0.3 = 0.246
[Finance]     QUALIFIED  0.94*0.2 = 0.188

VERDICT: QUALIFIED  (1.637 / 1.637 — unanimous weighted)
DISSENTING OPINIONS: none

TRUST DELTAS:
  Research  -0.02  (overreached in framing; self-corrected)
  Legal     +0.01  (challenge well-founded; improved the claim)
─── CLOSE dc-0142 ────────────────────────────────────────────────
```

The verdict flows back to the parent investigation: the claim in the
evidence ledger is rewritten to the synthesized position, with a
back-reference to `dc-0142`. Future investigations that touch
regulatory burden cite this debate rather than re-running it —
institutional memory, not re-derivation.

## 11. Relationship to Other Engines

- **Investigation Engine** (`19-investigation-engine.md`) — primary
  caller; every claim in the evidence ledger is debatable.
- **Trust Engine** (`08-trust-engine.md`) — consumes `trust_deltas`;
  supplies scores that weight votes and set speaking order.
- **Constitution Engine** (`09-constitution-engine.md`) — determines
  standing, supermajority thresholds, `mandate_relevance`.
- **Memory System** (`06-memory-system.md`) — persists the immutable
  `DebateRecord` and transcript; citation source for future debates.
- **Evolution Engine** (`07-evolution-engine.md`) — Policy Debates are
  the deliberative layer for every self-modification.
- **Organizational DNA** (`21-organizational-dna.md`) — supplies
  parameters that shape debate style (§7).
- **Event System** (`16-event-system.md`) — carries all `debate.*`
  messages; the engine is event-driven.
- **Meeting Engine** (`20-meeting-engine.md`) — meetings *resolve*
  to a debate on irreconcilable forks; debates *report* verdicts to
  follow-up meetings.

## 12. Open Questions (Roadmap)

1. **Appeals.** Should a losing party in an Evidence Challenge appeal
   to a full Position Debate? Currently the verdict is final; a
   `debate.appeal` message type is contemplated.
2. **Inter-organization debates.** When two orgs disagree (SPEC §10),
   should a Meta Society host a debate between them? The protocol
   supports it; standing rules need definition.
3. **Evidence decay.** A verdict based on 2024 evidence should not
   bind a 2027 decision. A TTL on `DebateRecord` authority is
   contemplated, after which the claim is re-debatable.
4. **Filibuster detection.** In low-Hierarchy orgs, an agent could
   stall with endless challenges. A challenge-rate limit per agent
   per debate is contemplated.

---

*See `20-meeting-engine.md` for the complementary synchronous channel;
`19-investigation-engine.md` for the orchestration that triggers most
debates; and `08-trust-engine.md` for the trust model that weights
every vote.*
