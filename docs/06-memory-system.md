# 06 — Memory System: Institutional Memory

> **The organization remembers.** Not as chat history, not as a vector
> database of past prompts — as an institution. It remembers the meeting where
> Engineering overrode Research, the policy change that followed a failed
> product launch, the playbook that worked for the last three investigations,
> and the reason a decision was made two years ago.

This document specifies the institutional memory subsystem of Veliora:
its three-layer architecture, the formation pipeline that turns raw events
into durable knowledge, the sharing model that distinguishes
department-private memory from shared institutional memory, the decay and
reinforcement dynamics that keep memory alive, the conflict-resolution
rules that handle contradictions, and the retrieval algorithm that injects
memory into agent prompts at decision time.

Cross-references:
- `04-technical-architecture.md` — system topology and service boundaries
- `05-agent-architecture.md` — agents as memory producers and consumers
- `08-trust-engine.md` — trust weights that modulate memory ranking
- `09-constitution-engine.md` — policies stored as semantic memory
- `13-database-design.md` — persistence schema for memory objects
- `14-knowledge-graph.md` — the graph store that memory is layered over
- `17-prompt-architecture.md` — where retrieved memory is injected
- `19-investigation-engine.md` — the richest source of episodic events

---

## 1. The "One Agent" Test

A single AI agent needs memory to maintain conversational continuity. That
need is fully satisfied by a sliding context window plus a vector store.
**None of what follows exists for that purpose.**

Every mechanism in this document exists because intelligence is distributed
across many agents that join and leave, debate, specialize, and inherit each
other's work. Specifically:

| Mechanism | Why a single agent does NOT need it |
|---|---|
| **Provenance** (which department/agent produced this memory) | A single agent is its own only source. |
| **Department-private memory** | One agent has no compartments to keep secrets between. |
| **Shared institutional memory with consensus-gated writes** | No peers to reach consensus with. |
| **Memory of debates, dissents, and votes** | No debate occurs. |
| **Causal chains across departments** | No handoffs occur. |
| **Trust-weighted memory ranking** | No reputations to weigh. |
| **Memory-driven hiring, firing, promotion** | No org to evolve. |
| **Forgetting curve tied to organizational reinforcement** | No institution to reinforce a memory. |

If a feature here would also make sense for a lone chatbot, it is scoped down
or removed. The memory system is the substrate of organizational
*institutionalization* — the process by which a group of agents becomes a
durable institution whose behavior is shaped by its own past.

---

## 2. Three-Layer Architecture

Veliora borrows the cognitive-science trichotomy of episodic, semantic,
and procedural memory, but reinterprets each layer as an *organizational*
construct rather than a neural one.

```
                      INSTITUTIONAL MEMORY
   ┌────────────────────────────────────────────────────────────┐
   │                                                            │
   │  EPISODIC          SEMANTIC            PROCEDURAL           │
   │  (what happened)   (what it means)     (how we do things)   │
   │                                                            │
   │  meetings          policies            playbooks            │
   │  decisions         patterns            SOPs                │
   │  debates           generalized        best practices       │
   │  external comms      claims            investigation        │
   │  trust changes       learned             templates          │
   │  hires/fires        biases              meeting formats     │
   │                                                            │
   │  short-lived,       consolidated,      reinforced by        │
   │  high-volume        lower-volume        successful reuse    │
   │                                                            │
   │  decays fast        decays slow         decays slowest       │
   │                                                            │
   └────────────────────────────────────────────────────────────┘
                            ▲
                            │ layered over
                            ▼
                   KNOWLEDGE GRAPH
                   (14-knowledge-graph.md)
                   structured node/edge store
```

### 2.1 Episodic Memory — "What happened"

Episodic memories are timestamped records of specific organizational events.
Each carries the participants, the investigation or meeting it belongs to,
the raw content, and links to the graph nodes it created or touched.

Examples:
- `2026-08-05 14:03 — Meeting #142 started. Participants: CEO, Research, Engineering, Finance. Topic: investigation #47 feasibility.`
- `2026-08-05 14:11 — Engineering challenged Research's evidence E-2207 on sample size.`
- `2026-08-05 14:18 — Vote called: proceed with recommendation YES. Result 4-1, Research dissenting.`
- `2026-08-05 14:22 — Decision D-0193 created, depends-on claims C-104, C-211, evidence E-2207, E-2298.`

Episodic memory is the **highest-volume, fastest-decaying** layer. Most
specific events fade unless they are reinforced by being cited, re-examined,
or consolidated into a semantic memory.

### 2.2 Semantic Memory — "What it means"

Semantic memories are generalized propositions abstracted from many
episodic memories, or formally adopted as policy. They are the
organization's *beliefs* and *rules*.

Examples:
- `Regulated-industry startups face an average 18-month licensing delay.` (generalized from 5 past investigations)
- `Engineering overestimates feasibility by ~23% on novel architectures.` (pattern learned from history)
- `Marketing's user surveys carry 81% historical reliability.` (derived from the Trust Engine)
- `All recommendations require ≥2 independent supporting evidence pieces.` (policy)
- `The organization is currently risk-averse (DNA risk=0.3).` (organizational self-model)

Semantic memory is **lower-volume, slower-decaying**, and is the primary
substrate injected into prompts as "what we know."

### 2.3 Procedural Memory — "How we do things"

Procedural memories are playbooks: reusable, parameterized sequences of
actions that worked in the past. They are the organization's *competencies*.

Examples:
- `Investigation playbook: startup-feasibility-v3` (the steps that produced accurate recommendations in the last 3 startup investigations)
- `Evidence-challenge protocol v2` (how to run peer review on a piece of evidence)
- `Board-meeting template v1` (agenda, speaking order, vote procedure)
- `Engineering dev-time estimation checklist` (Engineering-internal SOP)

Procedural memory is **reinforced by successful reuse** and decays slowest
of the three layers. A playbook that has not been used in 50 investigations
loses strength but is never deleted — it is archived and can be revived.

---

## 3. Memory Data Structures

All three layers share a common envelope and add type-specific payloads.
The structures below are the canonical in-memory representations; the
persistence schema is defined in `13-database-design.md`.

### 3.1 Common envelope

```typescript
interface Memory {
  id: string;                    // "M-000123"
  orgId: string;                 // owning organization
  layer: "episodic" | "semantic" | "procedural";
  type: string;                  // e.g. "meeting", "policy", "playbook"
  content: string;               // human- and model-readable summary
  structuredPayload: object;     // type-specific (see below)

  // Provenance — why the One Agent Test demands this
  createdBy: string;             // agent id
  createdByDept: string;         // department id
  participants: string[];        // agent ids involved (episodic)
  investigationId?: string;      // investigation this memory belongs to
  meetingId?: string;             // meeting this memory belongs to

  // Causal chain — links to the knowledge graph
  graphNodeIds: string[];        // nodes this memory created/touched
  causedByMemoryIds: string[];   // memories that led to this one
  supersededByMemoryId?: string; // if a newer memory replaced this one

  // Lifecycle
  createdAt: string;             // ISO timestamp
  lastAccessedAt: string;
  accessCount: number;
  reinforcementCount: number;    // citations, re-examinations
  strength: number;               // [0,1], see §6 forgetting curve
  classification: "public" | "shared" | "department-private" | "need-to-know";
  accessList?: string[];         // department ids, for need-to-know

  // Conflict metadata
  conflictsWith?: string[];      // memory ids this contradicts
  conflictStatus?: "open" | "resolved" | "superseded";
}

interface MemoryStore {
  memories: Map<string, Memory>;
  byOrg: Map<string, Set<string>>;
  byDept: Map<string, Set<string>>;
  byInvestigation: Map<string, Set<string>>;
  byLayer: Map<MemoryLayer, Set<string>>;
  embeddingIndex: EmbeddingIndex;  // for semantic retrieval
}
```

### 3.2 Episodic payloads

```typescript
interface MeetingMemory extends Memory {
  type: "meeting";
  structuredPayload: {
    meetingId: string;
    agenda: string[];
    turns: { speaker: string; dept: string; content: string; ts: string }[];
    motions: { text: string; proposer: string; votes: Record<string, "yea"|"nay"|"abstain">; result: string }[];
    dissents: { voter: string; reason: string }[];
    outcomes: string[];            // decision ids, action items
  };
}

interface DecisionMemory extends Memory {
  type: "decision";
  structuredPayload: {
    decisionId: string;
    rationale: string;
    dependsOnClaims: string[];     // graph claim node ids
    dependsOnEvidence: string[];   // graph evidence node ids
    voteTally: Record<string, number>;
    dissentingOpinions: string[];
    confidenceScore: number;       // [0,1]
  };
}

interface ExternalCommMemory extends Memory {
  type: "external-comm";
  structuredPayload: {
    channel: string;               // email, telegram, slack...
    counterpart: string;           // person node id
    direction: "outbound" | "inbound";
    body: string;
    extractedClaims: string[];     // claim node ids created
  };
}

interface TrustChangeMemory extends Memory {
  type: "trust-change";
  structuredPayload: {
    agentId: string;
    delta: number;                 // reputation change
    reason: string;                // e.g. "accurate forecast on inv #31"
    triggeringMemoryId: string;    // the memory that caused the change
  };
}
```

### 3.3 Semantic payloads

```typescript
interface PolicyMemory extends Memory {
  type: "policy";
  structuredPayload: {
    policyId: string;
    ruleText: string;
    scope: "org" | "department";
    adoptedByVote: string;         // meeting id
    supersedesPolicyId?: string;
    constitutional: boolean;       // see 09-constitution-engine.md
  };
}

interface PatternMemory extends Memory {
  type: "pattern";
  structuredPayload: {
    generalization: string;       // the abstracted proposition
    sourceEpisodicIds: string[];   // memories it was generalized from
    supportCount: number;         // how many episodes support it
    counterexampleCount: number;
    confidence: number;            // [0,1]
    departmentBias?: string;       // e.g. "Engineering overestimates feasibility"
  };
}
```

### 3.4 Procedural payloads

```typescript
interface PlaybookMemory extends Memory {
  type: "playbook";
  structuredPayload: {
    playbookId: string;
    trigger: string;               // when to invoke, e.g. "investigation.type == startup-feasibility"
    steps: { actor: string; action: string; produces: string }[];
    parameters: Record<string, string>;
    successCount: number;
    failureCount: number;
    successRate: number;           // [0,1], drives reinforcement
    derivedFromInvestigations: string[];
  };
}
```

---

## 4. Memory Formation Pipeline

Memory is not dumped into a store. It is *formed* through a four-stage
pipeline. The pipeline is what separates institutional memory from a
transcript log.

```
   EVENT ─────► ENCODING ─────► CONSOLIDATION ─────► RETRIEVAL
   (raw)       (structured)     (episodic→semantic,      (prompt-time
                               episodic→procedural)      context injection)

   ┌─────┐    ┌─────────┐     ┌──────────────┐         ┌───────────┐
   │ msg │───►│ encoder │────►│ consolidator │◄────────│ retriever │
   │ mtg │    └────┬────┘     └──────┬───────┘         └─────▲─────┘
   │ dec │         │                 │                       │
   │ ext │         ▼                 ▼                       │
   └─────┘    episodic M.       semantic M. /                 │
                              procedural M.                   │
                                                              │
                              strength decay ◄────────────────┘
                              (retrieval reinforces)
```

### Stage 1 — Event

An event is any occurrence the organization should remember: a message on
an internal channel, a meeting turn, a vote, a decision, an external
communication via Caspian, a trust update, a hiring or firing, a policy
amendment. Events are emitted by the Event System
(`16-event-system.md`) and consumed by the memory encoder.

### Stage 2 — Encoding

The encoder maps a raw event to a typed `Memory` object:

1. **Classify** the event type (meeting, decision, external-comm, ...).
2. **Extract participants** and the owning department(s).
3. **Resolve graph links** — which existing knowledge-graph nodes the event
   touches, and which new nodes it should create (a new Claim, Evidence,
   Decision, etc.). See `14-knowledge-graph.md` for the node-creation rules.
4. **Compute initial classification** — department-private by default for
   internal deliberations; shared for decisions and policies; need-to-know
   for sensitive findings (e.g. Legal compliance issues) routed to the CEO.
5. **Set initial strength** = 1.0 (full strength at encoding time).
6. **Compute embedding** for the `content` field and insert into the
   embedding index.
7. **Write** the memory and update all indices (`byOrg`, `byDept`,
   `byInvestigation`, `byLayer`).

Encoding is synchronous and per-event. It does not generalize.

### Stage 3 — Consolidation

Consolidation is the asynchronous process that turns episodic memory into
semantic and procedural memory. It runs as a periodic background job and is
also triggered at investigation close.

```
┌─────────────────────────────────────────────────────────────┐
│ CONSOLIDATION TRIGGERS                                       │
│  • investigation close  → extract playbook (episodic→procedural)│
│  • N similar episodic   → generalize pattern (episodic→semantic)│
│  • policy vote          → write policy memory (event→semantic) │
│  • trust update         → update pattern confidence            │
│  • contradiction found  → open conflict (see §7)              │
└─────────────────────────────────────────────────────────────┘
```

**Episodic → Semantic (generalization).** When ≥ `N_generalize` (default 5)
episodic memories within the same investigation or topic cluster share a
common structure, the consolidator prompts an agent to propose a
generalized proposition. The proposed pattern is stored with
`sourceEpisodicIds` and a confidence derived from the support/counterexample
ratio. Patterns are *not* written as fact — they are written as beliefs with
a confidence score and are themselves subject to the forgetting curve and
conflict resolution.

**Episodic → Procedural (playbook extraction).** At investigation close, if
the investigation succeeded (recommendation accepted, prediction later
validated), the consolidator extracts the sequence of steps as a candidate
playbook. The candidate is stored with `successCount=1`. It only becomes a
reinforced playbook after ≥ `N_playbook` (default 3) successful reuses; below
that threshold it is a *candidate* with low strength.

**Semantic → Semantic (policy adoption).** When a meeting votes to adopt a
rule, the consolidator writes a `PolicyMemory` with `supersedesPolicyId`
pointing at the prior policy if any. The superseded policy is *not* deleted;
it is marked `superseded` and linked, preserving the institutional history of
why the rule changed.

### Stage 4 — Retrieval

Retrieval is prompt-time and is detailed in §8. Every retrieval event
*reinforces* the retrieved memory (see §6), creating a feedback loop in
which useful memories stay strong and unused memories decay.

---

## 5. Sharing Model: Department-Private vs Shared

This section exists **only because** there is more than one agent. A
single agent has no compartments to keep secrets between, no peers to
reach consensus with, and no institution to inherit shared memory from.

### 5.1 Classification levels

```
┌──────────────────────────────────────────────────────────────────┐
│  PUBLIC        — visible to the user and every agent              │
│                  (final recommendations, executive summaries)     │
│  SHARED         — visible to every agent in the organization       │
│                  (decisions, policies, trust scores, org DNA)     │
│  NEED-TO-KNOW  — visible to a named subset of departments         │
│                  (Legal compliance findings → CEO + Legal)        │
│  DEPT-PRIVATE  — visible only within the producing department     │
│                  (Engineering's internal deliberations,           │
│                   Marketing's survey methodology,                │
│                   Finance's forecasting models)                  │
└──────────────────────────────────────────────────────────────────┘
```

### 5.2 Why department-private memory exists

Departments develop *competencies* that are partly tacit. Engineering's
internal reasoning about a codebase, Marketing's survey-instrument design,
Finance's spreadsheet models — these are the department's "trade secrets."
If every memory were shared, two failures would follow:

1. **Context flooding** — every agent's prompt would be polluted with
   every other department's internals, drowning the signal.
2. **Loss of specialization** — departments would converge on a single
   shared worldview and stop producing independent, contradicting analyses
   — the very thing the Evidence Challenge
   (contribution #7 in `02-research-contributions.md`) depends on.

Department-private memory is therefore a *feature*, not a limitation. It
preserves the cognitive diversity that makes the organization smarter than
its parts.

### 5.3 Shared memory and consensus-gated writes

`SHARED` memories belong to the organization, not to any department. Writes
to shared memory are **consensus-gated**:

- A new shared memory (e.g. a policy) requires a successful vote
  (`09-constitution-engine.md`, `10-debate-engine.md`).
- A *modification* to an existing shared memory requires either (a) a vote
  or (b) a supersession — a new memory that supersedes the old one, with the
  old one preserved and linked.
- Shared memories are never deleted in-place. History is part of the
  institution.

### 5.4 Memory handoff on department changes

When the Evolution Engine (`07-evolution-engine.md`) splits, merges, or
dissolves a department, its department-private memory is handled by rules
in the constitution:

- **Split** — private memory is duplicated to both successors, each of which
  may then diverge.
- **Merge** — private memories of both predecessors become private to the
  new department.
- **Dissolve** — private memories are downgraded to `need-to-know` and
  routed to the CEO's office for archival; they are not auto-shared.

---

## 6. Memory Decay and Reinforcement

### 6.1 The forgetting curve

Veliora adapts the Ebbinghaus forgetting curve to organizational memory.
Each memory has a `strength` in `[0,1]`. Strength decays with time and is
reinforced by access, citation, and reuse.

```
                    strength
        1.0 ─────► ●
                   │╲
                   │ ╲
        0.5 ─────► │  ╲─────●  (reinforced by retrieval)
                   │    ╲   │╲
                   │     ╲  │ ╲
        0.1 ─────► │      ╲ │  ╲─────●  (reinforced again)
                   │       ╲│    ╲   │
        0.0 ─────► ─└────────└─────└──└──────────────── time
                   encode   r1    r2   archive threshold
```

The decay function per layer:

```
S(t) = S0 · exp( -t / τ_layer(reinforcementCount) )
```

where:
- `S0` = strength immediately after last reinforcement (≤ 1.0, scales with
  reinforcement count, capped at 1.0),
- `t` = elapsed time since last reinforcement,
- `τ_layer` = time constant, larger for slower-decaying layers:
  - `τ_episodic`   = 30 days  · (1 + 0.5 · reinforcementCount)
  - `τ_semantic`   = 180 days · (1 + 0.5 · reinforcementCount)
  - `τ_procedural` = 365 days · (1 + 0.5 · reinforcementCount)

Reinforcement increases the effective time constant, so heavily-cited
memories decay much more slowly than uncited ones.

### 6.2 Reinforcement events

A memory's `reinforcementCount` is incremented and `lastAccessedAt`
updated by any of:

| Event | Reinforcement weight |
|---|---|
| Retrieved into a prompt | 1 |
| Cited by another agent in a meeting | 2 |
| Cited as support for a winning decision | 3 |
| Used as the basis for a successful prediction (post-validation) | 5 |
| Re-examined during a contradiction resolution | 2 |

### 6.3 Archive threshold

When `strength` falls below `archive_threshold` (default 0.1) the memory is
**archived**, not deleted. Archived memories:
- Are excluded from default retrieval (they no longer surface in prompts
  unless explicitly requested).
- Retain their graph links and provenance.
- Can be *revived* by explicit retrieval (a one-time strength boost to
  0.3), e.g. when an agent explicitly queries "have we ever seen something
  like this before?"

Institutional memory never truly dies. It becomes harder to reach. This
mirrors real organizations, where institutional knowledge fades unless
someone remembers to ask.

### 6.4 Differential decay by layer

| Layer | Default τ | Rationale |
|---|---|---|
| Episodic | 30 days | Specific events are numerous and mostly transient; only the cited ones matter long-term. |
| Semantic | 180 days | Generalized beliefs and policies persist, but must stay current — unchallenged beliefs that no one cites should fade. |
| Procedural | 365 days | Competencies are the slowest to decay; an unused playbook is still a competency, just a rusty one. |

---

## 7. Memory Conflict Resolution

Two memories can contradict. "Engineering judged X feasible" vs "Engineering
judged X infeasible." "Policy: require 2 evidence pieces" vs "Policy:
require 3 evidence pieces." Conflict resolution is itself an organizational
process — a single agent never has to reconcile its own contradictions
against itself.

### 7.1 Detection

The consolidator runs a contradiction detector over each newly encoded
memory against existing memories in the same scope (org, department, or
investigation). Detection uses:

- **Embedding similarity** above a threshold (default 0.78) — candidate
  contradictions are semantically close.
- **A classifier prompt** that asks: "Does memory A contradict memory B,
  support it, or is it neutral?" Only `contradicts` results open a conflict.
- **Graph signals** — if two memories create claim nodes linked by a
  `contradicts` edge in the knowledge graph, the conflict is opened
  automatically (see `14-knowledge-graph.md` §contradiction detection).

### 7.2 Resolution rules

When a conflict is opened, the resolver applies rules in priority order:

```
1. SUPERSESSION — one memory has supersededByMemoryId pointing to the
   other. The newer one wins. (Policy amendment, decision reversal.)

2. CONTEXT SPLIT — both are valid under different conditions. Both are
   kept, each tagged with its applicability condition, and the conflict
   is marked "resolved-contextual."
   e.g. "X is feasible IF budget > $500k; infeasible otherwise."

3. TRUST-WEIGHTED — neither supersedes, both are unconditional. The
   memory from the higher-trust producer (per 08-trust-engine.md) is
   preferred; the other is marked "superseded-weak" but retained.

4. RECENCY — equal trust, no context split. The more recent memory is
   preferred; the older is marked "superseded-stale."

5. ESCALATION — none of the above resolves cleanly. The conflict is
   queued as an agenda item for the next relevant meeting
   (20-meeting-engine.md) and the resolver emits a Debate Engine event
   (10-debate-engine.md).
```

### 7.3 Memory versioning

Resolved conflicts never delete the loser. The losing memory is linked to
the winner via `supersededByMemoryId`, its `conflictStatus` becomes
`superseded`, and its strength is frozen (it no longer decays further nor
reinforces — it is preserved as history). This guarantees the organization
can always answer *"why did we change our mind?"* — the superseded memory,
the winning memory, and the resolution rule are all retrievable.

---

## 8. Retrieval and Context Injection at Prompt Time

When an agent is about to be prompted, the Prompt Architecture
(`17-prompt-architecture.md`) calls the memory retriever to assemble a
context block. The retriever is the only path by which memory reaches an
agent's prompt.

### 8.1 Retrieval algorithm

```
function retrieveMemoryContext(query, agent, task, K=12):
    # 1. Candidate set: memories this agent is allowed to see
    candidates = store.byOrg[query.orgId]
        .filter(m => canAccess(agent, m))            # classification + accessList
        .filter(m => m.strength >= archive_threshold # not archived...
                  or query.explicitArchiveLookup)     # ...unless explicitly asked
        .filter(m => m.layer in query.layers)        # e.g. only semantic+procedural

    # 2. Score each candidate
    scored = []
    for m in candidates:
        sim     = cosine(query.embedding, m.embedding)         # relevance
        trust   = trustWeight(m.createdByDept, agent.dept)      # cross-dept trust
        recency = exp(-age(m) / tau(m.layer, m.reinforcementCount))
        strength= m.strength
        boost   = layerBoost(m.layer, task.type)               # procedural boost for
                                                                # "how-to" tasks, etc.
        score = w_rel*sim + w_trust*trust + w_rec*recency
                + w_str*strength + w_boost*boost
        scored.append((m, score))

    # 3. Diversity filter — avoid K near-duplicate memories
    selected = mmrDiversify(scored, K, lambda=0.5)

    # 4. Reinforce selected memories (feedback loop)
    for m in selected: reinforce(m, weight=1)

    # 5. Format into a context block
    return formatContextBlock(selected)
```

Default weights: `w_rel=0.40, w_trust=0.25, w_rec=0.15, w_str=0.10,
w_boost=0.10`. Weights are tunable per organization via the DNA
(`21-organizational-dna.md`) — a long-term-thinking org may raise
`w_rec` and lower `w_str` to favor durable knowledge over recent events.

### 8.2 Cross-department trust weighting

`trustWeight(producerDept, consumerDept)` returns a value in `[0,1]`
derived from the Trust Engine (`08-trust-engine.md`). When Engineering
retrieves a memory produced by Marketing, the memory's score is scaled by
how much Engineering trusts Marketing. This is the mechanism by which
*reputation shapes future cognition*: a department with a high trust score
sees its memories surface more readily in other departments' prompts.

### 8.3 Layer-specific query examples

```
# "What past investigations resemble this one?"  → episodic
retrieve(layers=[episodic], embedding=task.embedding, K=5)

# "What do we believe about regulated markets?"  → semantic
retrieve(layers=[semantic], embedding=query.embedding, K=8)

# "How do we run a feasibility investigation?"   → procedural
retrieve(layers=[procedural], embedding=task.embedding, K=3)

# "Why did we decide X last year?"                → episodic + causal walk
retrieve(layers=[episodic], embedding=query.embedding, K=5)
  then walk causedByMemoryIds backward to root
```

### 8.4 Context block format

The retriever returns a structured block that the Prompt Architecture
inserts before the task instruction:

```
[INSTITUTIONAL MEMORY — retrieved 12 items, strength-weighted]

## Relevant past investigations
- M-00188 (inv #31, 2026-06): Regulated-market startup. Outcome: PROCEED.
  Confidence 0.71. Dissent: Engineering (feasibility).  [strength 0.83]

## Organizational beliefs
- M-00402 (pattern): Regulated-industry startups face ~18mo licensing delay.
  Support: 5 episodes. Counterexamples: 0. Confidence 0.79.  [strength 0.91]
- M-00411 (policy): Recommendations require ≥2 independent evidence pieces.
  Adopted 2026-05-12 by vote #88.  [strength 1.00]

## Applicable playbooks
- M-00510 (playbook): startup-feasibility-v3. Success rate 3/3.  [strength 0.88]

## Cautions
- M-00399 (pattern): Engineering overestimates feasibility by ~23% on novel
  architectures. Apply discount.  [strength 0.74]
```

---

## 9. How Memory Affects Future Decisions

Memory is not a passive archive. It changes the organization's behavior
through four distinct mechanisms, each of which fails the One Agent Test
for an organization-specific reason.

### 9.1 Context injection (immediate)

The retriever above injects relevant memory into every prompt. An agent
that would otherwise re-derive a conclusion instead inherits the
organization's prior position — and can either adopt it, challenge it, or
supersede it. This is the *immediate* effect.

### 9.2 Trust modulation (medium-term)

Every Trust Change Memory updates the Trust Engine, which in turn changes
the `trustWeight` applied to future memory retrieval. A department that
was repeatedly right sees its memories surface more readily; a department
that was repeatedly wrong sees its memories discounted. See
`08-trust-engine.md`.

### 9.3 Policy evolution (long-term)

Policies stored as semantic memory govern future decisions directly: a
policy requiring 2 evidence pieces blocks any recommendation that does not
meet it. Policy amendments — themselves memories — change the rules under
which the next decision is made. See `09-constitution-engine.md`.

### 9.4 Organizational self-modification (longest-term)

Patterns such as "Engineering overestimates feasibility by ~23%" feed the
Evolution Engine (`07-evolution-engine.md`): the organization may decide to
apply a systematic discount to Engineering's feasibility estimates, hire
a second engineer to cross-check, or retrain the playbook. The
organization *adapts its own structure* in response to what it remembers
about itself.

---

## 10. Difference from Chat History

| Property | Chat history | Veliora institutional memory |
|---|---|---|
| Structure | Linear sequence of messages | Typed objects with layer, provenance, causal links, classification |
| Provenance | Implicit (the speaker) | Explicit: agent, department, meeting, investigation, vote tally |
| Generalization | None | Episodic → semantic consolidation; episodic → procedural extraction |
| Queryability | Substring search | Embedding + structured + graph traversal + causal walk |
| Evolution | Append-only | Decay, reinforcement, supersession, conflict resolution |
| Conflict handling | Latest message wins | Detection + 5-rule resolver + versioning + escalation to debate |
| Cross-investigation linking | None | Shared semantic and procedural memories link investigations across time |
| Decay | None (or hard truncation) | Ebbinghaus curve, layer-specific τ, archive threshold |
| Access control | All-or-nothing | 4-level classification + per-department access lists |
| Causal chains | None | `causedByMemoryIds` + graph `caused-by` edges trace any decision to its roots |
| Effect on future behavior | None (context window only) | Context injection + trust modulation + policy evolution + org self-modification |

The single sentence that captures the difference:

> **Chat history is what was said. Institutional memory is what the
> organization now is.**

---

## 11. Integration Map

```
                       ┌─────────────────────┐
                       │  Event System (16)  │
                       └──────────┬──────────┘
                                  │ events
                                  ▼
   ┌──────────────────────────────────────────────┐
   │              MEMORY SYSTEM (this doc)          │
   │  episodic │ semantic │ procedural │ retriever  │
   └──────┬──────────────┬──────────────┬──────────┘
          │              │              │
          ▼              ▼              ▼
   ┌──────────┐   ┌────────────┐  ┌────────────────┐
   │ Knowledge │   │ Trust Eng. │  │ Prompt Arch.   │
   │ Graph(14) │   │   (08)     │  │   (17)         │
   └──────────┘   └────────────┘  └────────────────┘
          │              │              │
          ▼              ▼              ▼
   ┌──────────┐   ┌────────────┐  ┌────────────────┐
   │Constitution│ │ Evolution  │  │ Investigation   │
   │  Eng.(09) │   │  Eng.(07)  │  │  Engine (19)   │
   └──────────┘   └────────────┘  └────────────────┘
```

- **Knowledge Graph (14)** is the structured substrate. Memory objects
  reference graph nodes via `graphNodeIds`; the graph holds the
  entity/claim/evidence/decision network, memory holds the temporal,
  provenance-tagged, decay-aware view over it.
- **Trust Engine (08)** supplies the weights used in retrieval scoring and
  in the trust-weighted conflict resolver.
- **Prompt Architecture (17)** is the sole consumer of the retriever at
  decision time.
- **Constitution Engine (09)** reads and writes policy memories.
- **Evolution Engine (07)** reads pattern memories and triggers structural
  change; its actions (hire/fire/split/merge) produce their own episodic
  memories, closing the loop.
- **Investigation Engine (19)** is the densest producer of episodic memory
  and the trigger for procedural consolidation at investigation close.

---

## 12. Operational Parameters (defaults, tunable via DNA)

| Parameter | Default | Meaning |
|---|---|---|
| `N_generalize` | 5 | Episodic memories needed to propose a semantic pattern |
| `N_playbook` | 3 | Successful reuses needed to promote a candidate playbook |
| `archive_threshold` | 0.1 | Strength below which a memory is archived |
| `revive_strength` | 0.3 | Strength boost on explicit archive retrieval |
| `τ_episodic` | 30 days | Episodic decay time constant |
| `τ_semantic` | 180 days | Semantic decay time constant |
| `τ_procedural` | 365 days | Procedural decay time constant |
| `reinforcement_factor` | 0.5 | τ growth per reinforcement count |
| `w_rel / w_trust / w_rec / w_str / w_boost` | 0.40 / 0.25 / 0.15 / 0.10 / 0.10 | Retrieval scoring weights |
| `mmr_lambda` | 0.5 | Diversity vs relevance tradeoff in retrieval |
| `contradiction_sim_threshold` | 0.78 | Embedding similarity to flag a candidate contradiction |

All defaults are overridable per organization through the DNA model
(`21-organizational-dna.md`), allowing two organizations with identical
agents to develop materially different memory behaviors — one
fast-decaying and present-focused, another slow-decaying and
long-memory-oriented.

---

## 13. Summary

The memory system is what makes Veliora an *institution* rather than a
*session*. Its three layers capture what happened, what it means, and how
we do things. Its formation pipeline turns raw events into durable,
generalized, reusable knowledge. Its sharing model preserves departmental
cognitive diversity while building a shared institutional core. Its
forgetting curve keeps memory current without ever erasing history. Its
conflict resolver turns contradictions into versioned, explainable changes
of position. And its retriever injects the right memory, weighted by trust
and recency, into every agent prompt — so that the organization's past
shapes its present cognition, and its present cognition shapes its future
structure.

> A single agent remembers. An organization *institutionalizes*.
