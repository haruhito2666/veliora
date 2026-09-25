# 14 — Knowledge Graph: Organizational Knowledge Representation

> **The organization's knowledge is a graph, not a document.** Every
> claim the organization has evaluated, every piece of evidence it has
> collected, every decision it has reached, and every external person it
> has spoken to lives as a node. Every support, contradiction, cause,
> dependency, trust, and supersession between them lives as an edge. The
> graph is what lets the organization answer *"why did we decide that?"*,
> *"who disagrees with this?"*, and *"what evidence survives peer
> review?"* — questions no chat history can answer.

This document specifies the organizational knowledge graph of Veliora:
its schema (node and edge types with their properties), how the graph is
constructed during investigations, the query patterns it supports, how it
evolves over time, the graph-based reasoning operations layered on top of
it, and its integration with the memory system.

Cross-references:
- `04-technical-architecture.md` — where the graph service sits in the
  system topology
- `06-memory-system.md` — memory is layered over the graph; the graph is
  the structured substrate, memory is the temporal, decay-aware view
- `08-trust-engine.md` — trust weights that propagate onto graph edges
- `10-debate-engine.md` — debates are triggered by graph-detected
  contradictions
- `13-database-design.md` — persistence schema for graph nodes and edges
- `16-event-system.md` — events that trigger graph writes
- `17-prompt-architecture.md` — graph queries feed prompt context
- `19-investigation-engine.md` — the primary producer of graph nodes

---

## 1. The "One Agent" Test

A single agent's "knowledge graph" is a personal knowledge base: notes,
clippings, and the links between them. It is useful, but it is not what
this document specifies. Every mechanism below exists because knowledge is
*produced, contested, and adjudicated across many agents*:

| Mechanism | Why a single agent does NOT need it |
|---|---|
| **Per-node producer provenance** | One agent is its own only producer. |
| **Trust-weighted edges** | No reputations to weight edges by. |
| **Contradiction-driven debate** | No one to disagree with. |
| **Cross-department decision rationale chains** | No handoffs to trace. |
| **Supersession history of policies and claims** | No institution to amend itself. |
| **Consensus measurement across producers** | No population to measure consensus over. |
| **External-person nodes with trust edges** | No division of labor in contacting people. |
| **Reified Relationship nodes** (n-ary, claimable) | No one to make claims *about* a relationship. |

If a feature here would also make sense as a personal note-taking graph,
it is scoped down or removed. The graph is the *organizational* knowledge
representation — its structure encodes who knew what, who challenged it,
who trusted whom, and how the organization revised itself.

---

## 2. Graph Schema Overview

The graph is a property graph (nodes and edges, each carrying a
properties map). It is stored in a graph database; the persistence schema
is in `13-database-design.md`. The conceptual schema is below.

```
                        ┌──────────────────────────────┐
                        │       KNOWLEDGE GRAPH         │
                        └──────────────────────────────┘

   NODE TYPES                  EDGE TYPES
   ──────────                  ─────────
   Entity                      supports        (Evidence → Claim)
   Claim                       contradicts     (Evidence|Claim → Claim)
   Evidence                    caused-by       (Event|Decision → Event|Decision)
   Decision                    depends-on      (Decision|Claim → Claim|Evidence)
   Person                      reports-to      (Person → Person)
   Event                       trusts          (Agent|Dept → Person|Source)
   Policy                      superseded-by   (Policy|Decision|Claim → Policy|Decision|Claim)
   Relationship (reified)      produced-by     (Node → Agent|Dept)   [provenance]
                               participates-in (Agent → Event)
                               mentions        (Node → Entity)
                               member-of       (Person → Entity)   [e.g. employee-of]
                               derived-from    (Claim|Policy → Claim|Policy)
```

Every node and edge carries a common envelope:

```typescript
interface GraphElement {
  id: string;                    // "N-000123" / "E-000456"
  orgId: string;
  type: string;
  properties: Record<string, any>;
  createdAt: string;
  updatedAt: string;
  // Provenance — required on every node and edge
  producedBy: string;            // agent id
  producedByDept: string;        // department id
  producingEvent?: string;       // event id (16-event-system.md)
  producingMemory?: string;      // memory id (06-memory-system.md)
  // Trust — edge weight, node confidence
  trustWeight?: number;          // [0,1], from 08-trust-engine.md
  confidence?: number;           // [0,1], for Claim nodes
  // Lifecycle
  status: "active" | "stale" | "superseded" | "retracted";
  supersededById?: string;
}
```

Provenance on every element is non-negotiable. It is the reason the graph
can answer *"who said this, when, in which investigation, with what
evidence, and how much do we trust them?"* — and it is the reason the
graph fails the One Agent Test in a productive way.

---

## 3. Node Types

### 3.1 Entity

A thing the organization reasons about: a company, product, technology,
market, regulation, asset, or any other named object. Entities are the
nouns of the graph.

```typescript
interface EntityNode extends GraphElement {
  type: "Entity";
  properties: {
    name: string;
    entityType: "company" | "product" | "technology" | "market"
             | "regulation" | "asset" | "other";
    aliases: string[];
    description: string;
    externalRefs?: { source: string; id: string }[];  // e.g. Crunchbase, arXiv
  };
}
```

### 3.2 Claim

A proposition the organization has considered. Claims are the assertions
the graph evaluates. Every claim has a confidence score, a producer, and
zero or more supporting and contradicting evidence edges.

```typescript
interface ClaimNode extends GraphElement {
  type: "Claim";
  properties: {
    text: string;                // the proposition
    claimType: "factual" | "predictive" | "normative" | "methodological";
    confidence: number;          // [0,1], org-level consensus confidence
    status: "open" | "accepted" | "rejected" | "contested" | "superseded";
    investigationId?: string;
    supersededByClaimId?: string;
  };
}
```

Confidence is *not* the producer's confidence — it is the organization's
trust-weighted consensus, computed by the consensus operation (§7.3).

### 3.3 Evidence

A piece of data offered in support of or against a claim. Evidence is
always produced by some department and may itself be challenged.

```typescript
interface EvidenceNode extends GraphElement {
  type: "Evidence";
  properties: {
    kind: "paper" | "survey" | "forecast" | "expert-opinion"
        | "data-set" | "observation" | "internal-analysis";
    source: string;              // citation, URL, or person node id
    summary: string;
    reliability: number;         // [0,1], producer-assessed
    challengedBy?: string[];     // agent ids who challenged this
    challengeStatus?: "unchallenged" | "challenged" | "upheld" | "withdrawn";
  };
}
```

### 3.4 Decision

A formal organizational decision: proceed, reject, defer, adopt policy,
hire, fire, promote, split department, amend constitution. Decisions are
the *commit* points of the organization.

```typescript
interface DecisionNode extends GraphElement {
  type: "Decision";
  properties: {
    decisionType: "recommendation" | "policy" | "personnel" | "structural"
                | "constitutional";
    outcome: string;             // "PROCEED" | "REJECT" | "DEFER" | ...
    rationale: string;
    voteTally: Record<string, number>;
    dissentingOpinions: { dept: string; reason: string }[];
    confidenceScore: number;
    meetingId: string;
    investigationId?: string;
  };
}
```

### 3.5 Person

An external person the organization has interacted with via Caspian
(`12-caspian-integration.md`), or an internal agent. Person nodes are the
bridge to the real world and the substrate of the trust network.

```typescript
interface PersonNode extends GraphElement {
  type: "Person";
  properties: {
    name: string;
    role: "external" | "internal-agent";
    affiliation?: string;        // Entity node id (e.g. university)
    channels: { type: string; handle: string }[];  // email, telegram, ...
    contactHistory: string[];    // event ids
    trustScore?: number;         // org-level trust in this person
  };
}
```

### 3.6 Event

An occurrence: a meeting, an investigation, an external communication, a
trust change. Event nodes anchor the causal chain.

```typescript
interface EventNode extends GraphElement {
  type: "Event";
  properties: {
    eventType: "meeting" | "investigation" | "external-comm"
             | "trust-change" | "policy-vote" | "structural-change";
    timestamp: string;
    participants: string[];     // agent ids
    investigationId?: string;
    meetingId?: string;
    memoryId?: string;           // corresponding episodic memory
  };
}
```

### 3.7 Policy

An organizational rule. Policy nodes are a special, constitution-aware
form of Claim: they are normative, they are adopted by vote, and they can
supersede one another.

```typescript
interface PolicyNode extends GraphElement {
  type: "Policy";
  properties: {
    ruleText: string;
    scope: "org" | "department";
    scopeDept?: string;
    constitutional: boolean;     // see 09-constitution-engine.md
    adoptedByVote: string;       // meeting id
    supersedesPolicyId?: string;
    supersededByPolicyId?: string;
    active: boolean;
  };
}
```

### 3.8 Relationship (reified)

A reified, claimable relationship between two or more entities. Edges
express binary relationships directly, but some relationships need to be
*first-class objects* — so that the organization can make claims about
them, attach evidence to them, and supersede them. "Acme competes with
Beta" is a Relationship node; the organization can then attach a Claim
"the competition is intensifying" and Evidence (a market report) to that
relationship.

```typescript
interface RelationshipNode extends GraphElement {
  type: "Relationship";
  properties: {
    relationType: "competes-with" | "partners-with" | "acquired"
                | "invests-in" | "supplies" | "employs" | "regulates"
                | "custom-of" | "other";
    members: string[];           // Entity (or Person) node ids
    direction: "directed" | "undirected";
    strength?: number;           // [0,1]
    active: boolean;
  };
}
```

Reified relationships are the graph's answer to n-ary relations: a single
edge cannot be the subject of a claim, but a Relationship node can.

---

## 4. Edge Types

All edges are directed unless noted. Edge direction is read as
`source ──edgeType──► target`.

### 4.1 `supports`

```
Evidence ──supports──► Claim
Claim    ──supports──► Claim        (an argument supports another)
```

Properties: `weight: number` (trust-adjusted support strength),
`producedBy`, `producingInvestigation`. A `supports` edge from a
high-trust Evidence to a Claim raises the Claim's consensus confidence.

### 4.2 `contradicts`

```
Evidence ──contradicts──► Claim
Claim    ──contradicts──► Claim
Evidence ──contradicts──► Evidence  (two pieces of evidence are incompatible)
```

A `contradicts` edge is the trigger for the Debate Engine
(`10-debate-engine.md`). Every contradiction creates a debate agenda item
unless it is resolved by supersession or context-split. The presence of
both `supports` and `contradicts` edges on the same Claim is the
organizational definition of *contested*.

### 4.3 `caused-by`

```
Event    ──caused-by──► Event
Decision ──caused-by──► Event
Decision ──caused-by──► Decision
```

The causal backbone of the organization. `caused-by` edges let the graph
answer *"trace this decision back to the events that produced it"* — the
core of decision rationale. Cycles are prevented by timestamp
validation: an edge may only point backward in time.

### 4.4 `depends-on`

```
Decision ──depends-on──► Claim
Decision ──depends-on──► Evidence
Claim    ──depends-on──► Evidence
Policy   ──depends-on──► Claim       (policy justified by claim)
```

`depends-on` is the *justification* edge: a decision depends on the claims
and evidence that justified it. If any depended-on Claim is later
superseded or retracted, the dependent Decision is flagged for
re-examination (see §6.3).

### 4.5 `reports-to`

```
Person ──reports-to──► Person
```

Organizational hierarchy, both internal (agent to CEO) and external (an
external contact's reporting line, where known). `reports-to` is used
for routing (who needs to see this finding) and for trust propagation
(trust in a manager partially extends to their reports).

### 4.6 `trusts`

```
Agent ──trusts──► Person
Dept  ──trusts──► Person
Agent ──trusts──► Source          (a publication, dataset, channel)
```

The trust network. Edge weight is the trust score from the Trust Engine
(`08-trust-engine.md`). `trusts` edges are dynamic: they strengthen on
validated accuracy and weaken on validated error. Trust propagates
transitively with decay (§7.4).

### 4.7 `superseded-by`

```
Policy   ──superseded-by──► Policy
Decision ──superseded-by──► Decision
Claim    ──superseded-by──► Claim
Evidence ──superseded-by──► Evidence
```

The versioning edge. Supersession is *never* a deletion: the old node is
marked `superseded`, its edges are preserved, and the new node links back.
This is what lets the organization answer *"how did our position on X
change, and why?"* — a single supersession chain tells the whole story.

### 4.8 Supporting edges (provenance & structure)

| Edge | From → To | Purpose |
|---|---|---|
| `produced-by` | any node → Agent\|Dept | Every node's producer (redundant with envelope, but queryable as an edge) |
| `participates-in` | Agent → Event | Who was in a meeting/investigation |
| `mentions` | any node → Entity | Free-form reference to an entity |
| `member-of` | Person → Entity | Person belongs to entity (employee, customer) |
| `derived-from` | Claim\|Policy → Claim\|Policy | Intellectual lineage (not supersession — derivation) |

---

## 5. Graph Construction During Investigations

The investigation (`19-investigation-engine.md`) is the densest producer of
graph nodes. The graph is built *incrementally and observably* — the user
watching the live investigation sees the graph grow.

### 5.1 Construction timeline

```
Investigation starts
   │
   ▼
┌────────────────────────────────────────────────────────────────┐
│ T0  Investigation Event node created.                          │
│     participants linked via participates-in.                   │
│                                                                │
│ T1  Research reads a paper →                                   │
│     • Entity nodes: paper, authors (Person), venue             │
│     • Evidence node E1 (kind=paper)                             │
│     • Claim node C1 ("market will reach $X by 2027")            │
│     • E1 ──supports──► C1                                       │
│     • E1, C1 produced-by Research                               │
│                                                                │
│ T2  Engineering challenges sample size →                       │
│     • Evidence node E2 (kind=internal-analysis)                 │
│     • E2 ──contradicts──► E1                                    │
│     • Debate Engine triggered (contradiction detected)         │
│                                                                │
│ T3  Marketing surveys users →                                  │
│     • Evidence node E3 (kind=survey)                           │
│     • E3 ──supports──► C1                                       │
│     • Person nodes for surveyed users (trusts edges)           │
│                                                                │
│ T4  Debate resolves: E1 upheld, E2 withdrawn →                 │
│     • E2.challengeStatus = "withdrawn"                         │
│     • E2 ──contradicts──► E1 edge marked stale                  │
│     • C1.confidence recomputed (consensus)                     │
│                                                                │
│ T5  Decision: PROCEED →                                        │
│     • Decision node D1                                         │
│     • D1 ──depends-on──► C1, E1, E3                            │
│     • D1 ──caused-by──► Investigation Event                     │
│     • D1 ──caused-by──► Meeting Event (the vote)               │
│                                                                │
│ T6  Investigation closes →                                      │
│     • consolidation in memory system (06-memory-system.md)      │
│     • playbook extraction (episodic → procedural)              │
└────────────────────────────────────────────────────────────────┘
```

### 5.2 Construction rules

1. **One event, one batch.** All nodes and edges created in response to a
   single event share the same `producingEvent` and are written in one
   transaction. This preserves the invariant that the graph is
   reconstructable from the event log.
2. **Producer is mandatory.** A node with no `producedBy` is rejected by
   the graph service. Anonymous knowledge does not exist in Veliora —
   everything is owned by an agent and a department.
3. **Claims are never asserted as fact.** A new Claim enters with
   `status="open"` and `confidence=0.5`. Confidence only rises through
   the consensus operation (§7.3) as support accumulates and
   contradictions are resolved.
4. **Contradictions are written, not avoided.** When Engineering
   challenges Research, the graph does not silently pick a winner. It
   writes the `contradicts` edge and hands the resolution to the Debate
   Engine. The graph records disagreement; the organization resolves it.
5. **External contacts become Person nodes.** Every Caspian
   communication creates or updates a Person node and a `trusts` edge
   from the contacting department. The real-world footprint of the
   organization is part of the graph.

---

## 6. Graph Evolution Over Time

The graph is not append-only; it is *revision-aware*. Four operations
govern its evolution.

### 6.1 Supersession

When a Policy, Decision, Claim, or Evidence is replaced:

```
   old node                       new node
   ┌──────────┐                   ┌──────────┐
   │ status:  │ ◄──superseded-by── │ status:  │
   │superseded│                   │ active   │
   └──────────┘                   └──────────┘
        │                              │
        │ original edges preserved     │ new edges from new node
        ▼                              ▼
   (no longer queried by         (queried by default;
    default; still reachable     inherits relevant context
    via supersession chain)      from old node)
```

Supersession preserves history. A query for "the current policy on X"
returns the active node; a query for "the history of policy on X" walks
the `superseded-by` chain backward.

### 6.2 Trust reweighting

When the Trust Engine updates a trust score, every edge whose weight
depends on that score is recomputed. This is a bulk operation triggered
by a Trust Change Memory (see `06-memory-system.md` §3.2). Affected
Claim confidences are recomputed via the consensus operation.

### 6.3 Dependency invalidation

When a Claim is superseded or an Evidence is retracted, every Decision
that `depends-on` it is flagged:

```
function invalidateDependents(nodeId):
    dependents = query("MATCH (d)-[:depends-on]->(n) WHERE id(n)=$nodeId RETURN d")
    for d in dependents:
        d.properties.staleBecause = nodeId
        d.properties.reexaminationRequired = true
        emit Event("decision-reexamination-required", d)
```

This is the mechanism by which *new knowledge invalidates old
decisions*. A decision reached on evidence that is later retracted is not
silently kept; it is surfaced for re-examination.

### 6.4 Contradiction resolution writes

When the Debate Engine resolves a contradiction, the resolution is
written as graph state:

- **Upheld** — the winning Evidence/Claim keeps its edges; the loser's
  `contradicts` edge is marked `stale`.
- **Context-split** — both nodes are kept; a new `Relationship` node or a
  condition annotation records the applicability condition.
- **Supersession** — a new Claim supersedes one or both of the
  contradicting Claims via `superseded-by`.

---

## 7. Graph-Based Reasoning

Four reasoning operations are layered over the graph. Each is exposed as
a query API consumed by the Prompt Architecture (`17-prompt-architecture.md`)
and the Investigation Engine (`19-investigation-engine.md`).

### 7.1 Path finding — decision rationale tracing

Given a Decision node, walk backward through `depends-on` and `caused-by`
edges to reconstruct the full rationale tree.

```
function traceRationale(decisionId, maxDepth=5):
    tree = {}
    frontier = [(decisionId, 0)]
    while frontier:
        (id, depth) = frontier.pop()
        if depth > maxDepth: continue
        node = graph.get(id)
        tree[id] = { node, depth, deps: [] }
        for edge in graph.outgoing(id, ["depends-on", "caused-by"]):
            tree[id].deps.append(edge.target)
            frontier.append((edge.target, depth + 1))
    return tree
```

Example output (ASCII):

```
D-0193 (Decision: PROCEED, confidence 0.71)
├── depends-on ► C-104 (Claim: market will reach $X by 2027, conf 0.79)
│   ├── supports ◄ E-2207 (Evidence: paper, upheld)
│   ├── supports ◄ E-2298 (Evidence: survey, upheld)
│   └── contradicts ◄ E-2245 (Evidence: internal-analysis, withdrawn)
├── depends-on ► C-211 (Claim: tech is feasible, conf 0.62)
│   └── supports ◄ E-2310 (Evidence: eng spike)
└── caused-by ► Event-88 (Meeting #142, vote 4-1)
    └── caused-by ► Event-12 (Investigation #47 started)
```

This is the answer to *"why did we decide this?"* — a single graph query.

### 7.2 Contradiction detection

Find all Claim nodes that have both `supports` and `contradicts` edges,
ranked by the trust-weighted balance.

```
function detectContradictions(orgId):
    return query(`
        MATCH (c:Claim {orgId: $orgId, status: "active"})
        WHERE EXISTS { MATCH (e)-[:supports]->(c) }
          AND EXISTS { MATCH (e)-[:contradicts]->(c) }
        WITH c,
             sum(trustWeight(e)*1  FOR e IN supports(c))   AS sup,
             sum(trustWeight(e)*-1 FOR e IN contradicts(c)) AS con
        RETURN c, sup, con, (sup+con) AS balance
        ORDER BY abs(balance) ASC           # closest to 0 = most contested
    `)
```

Claims near balance zero are the most contested and are the highest-priority
agenda items for the next debate cycle.

### 7.3 Consensus measurement

For a Claim, compute the organization's trust-weighted consensus
confidence:

```
function consensusConfidence(claimId):
    supports = edges(claimId, incoming, "supports")
    contradicts = edges(claimId, incoming, "contradicts")

    supWeight = sum(e.trustWeight * e.properties.weight
                    for e in supports
                    if e.status == "active"
                       and e.source.challengeStatus != "withdrawn")
    conWeight = sum(e.trustWeight * e.properties.weight
                    for e in contradicts
                    if e.status == "active"
                       and e.source.challengeStatus != "withdrawn")

    total = supWeight + conWeight
    if total == 0: return 0.5            # no evidence → neutral
    return supWeight / total             # (0,1); 0.5 = perfect split
```

Confidence is *not* a vote. It is a trust-weighted evidence balance. A
single high-trust Evidence can outweigh many low-trust ones — this is the
graph-level expression of Weighted Trust (contribution #8).

### 7.4 Transitive trust

Trust propagates along `trusts` and `reports-to` edges with decay:

```
trust(A, C) = max over all paths A → ... → C of:
              product(edgeWeight) · decay^pathLength

decay = 0.6   # each hop reduces propagated trust to 60%
```

A department trusts an external professor (direct, weight 0.9). The
professor trusts a colleague (direct, weight 0.8). The department's
derived trust in the colleague is `0.9 · 0.8 · 0.6 = 0.432`. Derived
trust is used only when no direct trust edge exists; direct trust always
overrides.

---

## 8. Query Patterns

The graph exposes a query API used by the Prompt Architecture and the
Investigation Engine. The most common patterns:

### 8.1 "Find all evidence supporting claim X"

```
MATCH (e:Evidence)-[r:supports]->(c:Claim {id: $X})
WHERE r.status = "active" AND e.challengeStatus <> "withdrawn"
RETURN e, r.trustWeight AS w
ORDER BY w DESC
```

### 8.2 "Find all contradictions involving X"

```
MATCH (e)-[r:contradicts]->(c:Claim {id: $X})
WHERE r.status = "active"
OPTIONAL MATCH (e)-[:supports|:contradicts]->(other:Claim)
RETURN e, r, other
```

### 8.3 "Trace the rationale for decision D"

```
traceRationale("D-0193", maxDepth=5)   # see §7.1
```

### 8.4 "What is our current policy on topic T?"

```
MATCH (p:Policy {status: "active"})
WHERE p.ruleText CONTAINS $T AND p.constitutional IN [true, false]
OPTIONAL MATCH (p)-[:superseded-by*]->(old:Policy)
RETURN p, collect(old) AS history
```

### 8.5 "Who have we contacted about topic T, and how much do we trust them?"

```
MATCH (dept)-[t:trusts]->(p:Person)
WHERE EXISTS { MATCH (e:Evidence {investigationId: $I})-[:mentions]->(p) }
RETURN dept, p, t.trustWeight AS trust
ORDER BY trust DESC
```

### 8.6 "Which decisions are now stale because their evidence was retracted?"

```
MATCH (d:Decision {reexaminationRequired: true})
OPTIONAL MATCH (d)-[:depends-on]->(n) WHERE n.status IN ["superseded","retracted"]
RETURN d, collect(n) AS invalidatedBy
```

### 8.7 "What is the consensus confidence on claim X?"

```
consensusConfidence("C-104")           # see §7.3
```

---

## 9. Integration with the Memory System

The graph and the memory system (`06-memory-system.md`) are two views
over the same underlying organizational knowledge. They are not
redundant; they serve different roles.

```
   ┌──────────────────────────┐        ┌──────────────────────────┐
   │      MEMORY SYSTEM        │        │      KNOWLEDGE GRAPH      │
   │      (06-memory)          │        │      (this doc)           │
   ├──────────────────────────┤        ├──────────────────────────┤
   │ temporal, decay-aware    │        │ structural, queryable     │
   │ provenance-tagged        │◄──────►│ provenance-tagged         │
   │ retrieval interface       │        │ reasoning substrate       │
   │ three layers (E/S/P)     │        │ nodes + edges             │
   │ prompt-time context       │        │ rationale, consensus,    │
   │                          │        │ contradiction, trust       │
   └──────────────────────────┘        └──────────────────────────┘
```

- **Memory references the graph.** Every `Memory` object carries
  `graphNodeIds` — the nodes it created or touched. An episodic meeting
  memory points to the Decision and Claim nodes produced in that meeting.
- **The graph references memory.** Every graph element carries
  `producingMemory` — the memory that produced it. Walking from a graph
  node to its producing memory yields the full temporal and provenance
  context.
- **Consolidation is graph-driven.** The memory consolidator's
  episodic → semantic generalization is informed by graph patterns: a
  pattern is proposed when a subgraph recurs across N investigations.
- **Retrieval is graph-informed.** The memory retriever (§8 of
  `06-memory-system.md`) may expand its candidate set by one-hop graph
  traversal: retrieving a memory about Claim C also surfaces memories
  about Evidence that `supports` C and Decisions that `depends-on` C.
- **Contradiction flows both ways.** A `contradicts` edge in the graph
  opens a memory conflict (memory §7.1); an unresolved memory conflict
  triggers a graph contradiction write. The two systems keep each other
  consistent via the Event System (`16-event-system.md`).

The division of labor:

> **The graph answers "what is the case and how do we know?" The memory
> system answers "when did we learn it, who learned it, and how strongly
> do we still hold it?"**

---

## 10. Example Subgraph

A worked example: the graph state after investigation #47 ("Should we
build an AI healthcare startup?") reaches a PROCEED decision.

```
                         ┌──────────────────────┐
                         │  Investigation #47     │
                         │  (Event-12)            │
                         └──────────┬─────────────┘
                                    │ caused-by
                                    ▼
                         ┌──────────────────────┐
                         │  Meeting #142          │
                         │  (Event-88)            │
                         │  vote 4-1, Research    │
                         │  dissenting            │
                         └──────────┬─────────────┘
                                    │ caused-by
                                    ▼
                         ┌──────────────────────┐
                         │  Decision D-0193      │
                         │  PROCEED, conf 0.71   │
                         └──┬─────────┬──────────┘
                            │         │
            depends-on ─────┘         └───── depends-on
                ▼                              ▼
   ┌────────────────────┐          ┌────────────────────┐
   │ Claim C-104         │          │ Claim C-211         │
   │ "AI hc market        │          │ "Tech is feasible"  │
   │  reaches $X by 2027" │          │ conf 0.62           │
   │ conf 0.79            │          └──────────┬──────────┘
   └──┬───────┬──────────┘                     │
      │       │                                │ supports
      │       │ contradicts                    │
      │       ▼                                ▼
      │  ┌────────────────┐          ┌──────────────────┐
      │  │ Evidence E-2245 │          │ Evidence E-2310   │
      │  │ internal-analysis│          │ eng spike         │
      │  │ withdrawn        │          │ produced-by Eng   │
      │  │ produced-by Eng │          └──────────────────┘
      │  └────────────────┘
      │ supports                   supports
      ├────────────►┌────────────────┐
      │              │ Evidence E-2207 │
      │              │ paper            │
      │              │ produced-by Res  │
      │              │ upheld           │
      │              └────────────────┘
      │ supports
      └────────────►┌────────────────┐
                     │ Evidence E-2298 │
                     │ survey           │
                     │ produced-by Mktg │
                     │ upheld           │
                     └────────┬────────┘
                              │ mentions
                              ▼
                  ┌────────────────────┐
                  │ Entity: "AI hc mkt" │
                  └────────────────────┘

   External contacts (Caspian):
   ┌────────────────────┐    trusts     ┌────────────────────┐
   │ Dept: Research      │─────────────►│ Person: Prof. Lee   │
   └────────────────────┘   w=0.91      │ (Person-44)         │
                                         │ affiliation: MIT    │
                                         └─────────┬───────────┘
                                                   │ member-of
                                                   ▼
                                         ┌────────────────────┐
                                         │ Entity: MIT          │
                                         └────────────────────┘
```

Reading the subgraph:
- The decision depends on two claims, each supported by upheld evidence.
- One contradiction (Engineering's withdrawn internal analysis) is
  preserved in the graph — the organization *remembers* the disagreement
  even though it was resolved.
- External contact with Prof. Lee is part of the graph, with a trust edge
  from Research and a `member-of` edge to MIT.
- Every node carries a producer; nothing in this subgraph is anonymous.

---

## 11. Storage and Performance

The graph service is a single logical component in the technical
architecture (`04-technical-architecture.md`). Operational notes:

- **Backend.** A property-graph store (e.g. Neo4j, or a Postgres+recursive-CTE
  fallback for the MVP per `28-mvp-hackathon.md`). The schema above is
  backend-agnostic.
- **Scale.** Per organization, the graph is bounded by the number of
  investigations × nodes per investigation. A typical investigation
  produces 20–80 nodes and 40–200 edges. Long-lived organizations
  accumulate 10^4 – 10^6 nodes; the query patterns above are all
  index-backed (producer, type, status, trustWeight).
- **Consistency.** Graph writes are transactional per event batch
  (§5.2 rule 1). Reads are eventually consistent with the memory system
  via the Event System; the graph is the source of truth for structure,
  memory is the source of truth for strength and recency.
- **Partitioning.** Graphs are partitioned per organization
  (`orgId`). Cross-organization queries (e.g. Meta Society comparing
  three societies' graphs) are read-only joins over per-org partitions;
  see `27-scaling-strategy.md`.
- **Retention.** Superseded and retracted nodes are never deleted; they
  are marked and excluded from default queries. Hard deletion requires a
  constitutional vote (`09-constitution-engine.md`) — institutional
  knowledge is never silently lost.

---

## 12. Summary

The knowledge graph is the structural representation of what the
organization knows. Its eight node types and seven primary edge types
capture entities, claims, evidence, decisions, people, events, policies,
and reified relationships — each with mandatory producer provenance. Its
construction during investigations is incremental, observable, and
disagreement-preserving. Its evolution is governed by supersession,
trust reweighting, dependency invalidation, and contradiction resolution.
Its reasoning operations — path finding, contradiction detection,
consensus measurement, and transitive trust — give the organization the
ability to trace any decision to its roots, surface contested claims,
and weigh evidence by reputation. And its tight bidirectional integration
with the memory system gives each system the role it is best at: the
graph holds structure and justification, memory holds time and strength.

> A single agent takes notes. An organization *maintains a knowledge
> graph* — and reasons over it.
