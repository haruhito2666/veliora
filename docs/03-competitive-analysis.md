# 03 — Competitive Analysis

> **The thesis of this document**: every existing competitor is an
> **agent orchestration framework** — it coordinates agents to complete
> tasks. Veliora is an **organization operating system** — it creates
> organizations that investigate, debate, and decide. The distinction is
> not incremental; it is categorical.

This document compares Veliora against nine competitors across three
tiers: multi-agent orchestration frameworks, autonomous task agents,
and consumer AI products. For each competitor we describe what they do,
their architecture, their limitations, and why Veliora is
fundamentally different. A feature comparison matrix and a categorical
distinction table close the document.

Cross-references:
- `00-product-vision.md` — Positioning and narrative.
- `01-core-philosophy.md` — Organizations-as-intelligence paradigm.
- `02-research-contributions.md` — The twelve contributions that
  differentiate Veliora.
- `04-technical-architecture.md` — System topology.
- `19-investigation-engine.md` — Investigation as the flagship unit of
  work.

---

## Table of Contents

1. [The Categorical Distinction](#1-the-categorical-distinction)
2. [Competitor Index](#2-competitor-index)
3. [Tier 1 — Multi-Agent Orchestration Frameworks](#3-tier-1--multi-agent-orchestration-frameworks)
   - 3.1 [CrewAI](#31-crewai)
   - 3.2 [Microsoft AutoGen](#32-microsoft-autogen)
   - 3.3 [LangGraph](#33-langgraph)
   - 3.4 [OpenAI Swarm](#34-openai-swarm)
4. [Tier 2 — Autonomous Task Agents](#4-tier-2--autonomous-task-agents)
   - 4.1 [Manus](#41-manus)
   - 4.2 [Devin](#42-devin)
5. [Tier 3 — Consumer AI Products](#5-tier-3--consumer-ai-products)
   - 5.1 [ChatGPT](#51-chatgpt)
   - 5.2 [Claude](#52-claude)
6. [Feature Comparison Matrix](#6-feature-comparison-matrix)
7. [Why Veliora Is Not a Framework](#7-why-veliora-is-not-a-framework)
8. [Market Positioning](#8-market-positioning)

---

## 1. The Categorical Distinction

```
┌─────────────────────────────────────────────────────────────────────┐
│  COMPETITORS                                                        │
│                                                                     │
│  User → Framework → [Agent1, Agent2, Agent3] → Task completed       │
│                                                                     │
│  • The user (developer) authors the agent graph.                    │
│  • The framework coordinates agents to complete a task.             │
│  • The output is a completed task.                                  │
│  • The graph is static; the human rewrites it for the next task.    │
│  • There is no organization; there is a pipeline.                   │
└─────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────┐
│  VELIORA                                                          │
│                                                                     │
│  User → Organization → Investigation → Debate → Vote → Decision     │
│                                                                     │
│  • The user creates an organization (startup, lab, firm).           │
│  • The organization investigates, debates, and decides.             │
│  • The output is a decision with dissent, evidence, and risk.       │
│  • The organization evolves its own structure over time.            │
│  • There is no pipeline; there is a living institution.             │
└─────────────────────────────────────────────────────────────────────┘
```

The distinction is **not** "Veliora has more agents." The distinction
is that the unit of intelligence is different:

| Aspect | Competitors | Veliora |
|--------|-------------|-----------|
| Unit of intelligence | The agent | The organization |
| Unit of work | The task | The investigation |
| Unit of output | A completed task | A decision with dissent |
| Who authors the graph | The developer | The organization itself |
| Persistence | Stateless per run | Institutional memory |
| Adaptation | Human rewrites the graph | The organization evolves |
| Conflict | Suppressed or scripted | Real, mandate-driven |
| Resolution | Hand-coded arbitration | Weighted vote under constitution |
| User role | Developer writes code | User creates an organization |

This is the lens for every comparison below.

---

## 2. Competitor Index

```
+------+----------------+-------------------------------------------+----------------+
| Tier | Competitor     | Category                                  | Verdict        |
+------+----------------+-------------------------------------------+----------------+
| 1    | CrewAI         | Role-based agent orchestration            | framework       |
| 1    | MS AutoGen     | Conversational multi-agent framework      | framework       |
| 1    | LangGraph      | Stateful graph orchestration              | framework       |
| 1    | OpenAI Swarm   | Lightweight handoff orchestration         | framework       |
| 2    | Manus          | General-purpose autonomous task agent     | single agent    |
| 2    | Devin          | Autonomous software engineering agent     | single agent    |
| 3    | ChatGPT        | Consumer assistant (single model)         | single model    |
| 3    | Claude         | Consumer assistant (single model)         | single model    |
+------+----------------+-------------------------------------------+----------------+
```

Veliora is in a **new category** — *organization operating system* —
that none of the above occupy.

---

## 3. Tier 1 — Multi-Agent Orchestration Frameworks

### 3.1 CrewAI

**What it does.** CrewAI is a Python framework for orchestrating
role-playing autonomous agents. A developer defines agents (with a
role, goal, and backstory), tasks (with a description and expected
output), and a crew (a set of agents and tasks with a process model —
sequential or hierarchical). The crew executes the tasks and returns
results.

**Architecture.**

```
  Developer writes:
    agent_researcher = Agent(role="Researcher", goal="...", backstory="...")
    agent_writer     = Agent(role="Writer",   goal="...", backstory="...")
    task_research   = Task(description="...", agent=agent_researcher)
    task_write      = Task(description="...", agent=agent_writer)
    crew = Crew(agents=[...], tasks=[...], process=Process.sequential)
    crew.kickoff()
```

The crew runs the tasks in order, passing each task's output to the
next. There is no persistent organization, no memory across crews, no
debate, no voting, no evolution. The graph is **frozen at design
time**.

**Limitations.**
- Agents are stateless across crews; no institutional memory.
- No genuine conflict; agents are cooperative by construction.
- No weighted trust; every agent's opinion is uniform.
- No self-modification; the developer rewrites the crew for each task.
- The user is a **developer**, not a person asking an organization a
  question.

**Why Veliora is fundamentally different.** CrewAI is a **framework**
a developer uses to build a one-shot pipeline. Veliora is an
**operating system** that hosts a **persistent organization** the user
creates once and which then runs many investigations, learns from them,
evolves its structure, and remembers. The user never writes Python;
the user creates a Startup Society and asks it questions. CrewAI
answers "how do I orchestrate agents to do X?"; Veliora answers "what
does an organization that knows how to investigate X look like, and
what does it conclude?"

### 3.2 Microsoft AutoGen

**What it does.** AutoGen is a conversational multi-agent framework
from Microsoft Research. It supports conversable agents that exchange
messages, with patterns for two-agent dialogue, group chat, sequential
chat, and nested chat (a sub-conversation spawned within a
conversation). AutoGen Studio provides a no-code UI for composing
agents.

**Architecture.**

```
  user_proxy = UserProxyAgent("User", ...)
  assistant  = AssistantAgent("Assistant", llm_config=...)
  critic     = AssistantAgent("Critic",    llm_config=...)

  groupchat = GroupChat(agents=[user_proxy, assistant, critic],
                       messages=[], max_round=10)
  manager   = GroupChatManager(groupchat)
  user_proxy.initiate_chat(manager, message="...")
```

The manager routes messages among agents until a termination condition
is met. The conversation is the unit of work; when it ends, the state
ends with it.

**Limitations.**
- Conversations are **stateless across sessions**; no institutional
  memory.
- Agents are **flat peers** in a group chat; no specialization, no
  departments, no hierarchy that persists.
- No weighted trust; the manager routes uniformly.
- No self-modification; the agent set is fixed per conversation.
- No constitution; behavior is steered by prompts, not by a votable
  charter.
- The user is in the loop as a `UserProxyAgent`; the system is not
  autonomous.

**Why Veliora is fundamentally different.** AutoGen's group chat is a
**conversation**; Veliora's organization is an **institution**. A
conversation ends; an institution persists, accrues memory, evolves
its structure, and enforces a constitution. AutoGen asks "how do I
script a conversation among agents?"; Veliora asks "what does an
organization conclude after investigating, debating, and voting?"

### 3.3 LangGraph

**What it does.** LangGraph (from LangChain) is a library for building
**stateful, cyclical multi-actor graphs** on top of language models. A
developer defines nodes (agents or functions), edges (transitions),
conditional routing, and shared state. The graph is compiled and
invoked; it supports human-in-the-loop interrupts, persistence
(checkpoints), and streaming.

**Architecture.**

```
  graph = StateGraph(State)
  graph.add_node("research", research_node)
  graph.add_node("draft",    draft_node)
  graph.add_node("review",   review_node)
  graph.add_edge("research", "draft")
  graph.add_conditional_edges("draft", should_review,
                               {True: "review", False: END})
  graph.add_edge("review", "draft")     # cycle
  app = graph.compile(checkpointer=MemorySaver())
```

The graph is the **authoritative structure**. State flows through it;
cycles allow rework; checkpoints allow resume. But the graph itself is
**compiled and frozen**.

**Limitations.**
- The graph is **design-time authored**; nodes and edges do not change
  at runtime. No living organization.
- State is per-run; there is no **institutional memory** across
  investigations (checkpoints resume a run; they do not accumulate
  organizational knowledge).
- No genuine conflict; nodes are cooperative by construction.
- No weighted trust; routing is conditional logic, not reputation.
- No constitution; behavior is encoded in node logic, not governed.
- No self-modification; restructuring requires recompiling the graph.
- The user is a **developer** composing a graph.

**Why Veliora is fundamentally different.** LangGraph is the most
general framework here — it can in principle express any topology,
including one that mimics an organization. But expressing an
organization in LangGraph requires the developer to **author and
maintain** the topology, the memory, the trust, the constitution, and
the evolution. Veliora **provides** all of these as primitives; the
developer does not build them per graph. LangGraph is a **graph
runtime**; Veliora is an **organization runtime**. LangGraph answers
"how do I compile a stateful agent graph?"; Veliora answers "what
does an organization decide?"

### 3.4 OpenAI Swarm

**What it does.** OpenAI Swarm (experimental) is a lightweight,
educational framework for agent orchestration via **routines** and
**handoffs**. An agent is defined by instructions and a set of
functions; a function can return a handoff to another agent. The
orchestrator (a `Runner`) executes the active agent until it hands off
or finishes.

**Architecture.**

```
  triage_agent = Agent(
      name="Triage",
      instructions="Route the user to the right agent.",
      functions=[transfer_to_billing, transfer_to_support],
  )
  billing_agent = Agent(name="Billing", instructions="...", functions=[...])
  # transfer_to_billing returns Agent(billing_agent)
  result = Runner(triage_agent).run("I was overcharged.")
```

Swarm is deliberately minimal: no state management, no memory, no
planning, no evaluation — just routines and handoffs.

**Limitations.**
- **No persistence**; each run is stateless.
- **No memory**; no institutional memory, no per-agent memory.
- **No conflict**; agents hand off cooperatively.
- **No trust**; no reputation, no weighting.
- **No constitution**; instructions are strings.
- **No evolution**; the routine set is fixed.
- Explicitly **educational**, not production-grade.

**Why Veliora is fundamentally different.** Swarm is a **routing
pattern**, not an organization. It is the minimal expression of
multi-agent handoff; Veliora is the maximal expression of
organizational intelligence. Swarm answers "which agent should handle
this message?"; Veliora answers "what does an organization conclude
after investigating this question?"

---

## 4. Tier 2 — Autonomous Task Agents

### 4.1 Manus

**What it does.** Manus is a general-purpose autonomous agent that
executes multi-step tasks in a computer-use environment: browsing,
coding, file operations, shell commands. It plans, acts, observes, and
reflects in a loop until the task is complete. It is a **single agent**
with tool access, not a multi-agent system.

**Architecture.**

```
  User: "Research the top 5 CRM tools and make a comparison table."
       │
       ▼
  Manus (single agent):
    plan → browse → read → extract → browse → read →
    extract → compile → write table → done
```

The agent maintains a working memory of the task and a plan; it does
not maintain an organization, a constitution, or institutional memory.

**Limitations.**
- **Single agent**; no specialization, no debate, no voting.
- **No organization**; the unit of intelligence is the agent.
- **No persistent memory** across tasks; each task is fresh.
- **No conflict**; there is no second perspective.
- **No evolution**; the agent does not restructure itself.
- **No external human outreach**; it browses documents, not people.

**Why Veliora is fundamentally different.** Manus is a **capable
individual**; Veliora is an **organization**. Manus executes a task;
Veliora conducts an investigation. Manus produces a completed task;
Veliora produces a decision with dissent, evidence, risks, and a
provenance trace through departments. Manus answers "can an agent do
this task for me?"; Veliora answers "what would an organization
conclude after investigating this?"

### 4.2 Devin

**What it does.** Devin (Cognition) is an autonomous software
engineering agent: it plans a coding task, writes code, runs tests,
reads logs, debugs, and iterates until the task is done or it is
stuck. It is a **single agent** specialized for software engineering.

**Architecture.**

```
  User: "Implement issue #142."
       │
       ▼
  Devin (single agent):
    plan → write code → run tests → read failures →
    debug → patch → run tests → green → PR
```

Devin maintains a plan, a codebase context, and a test loop. It does
not maintain an organization, peers, a constitution, or institutional
memory across projects.

**Limitations.**
- **Single agent**; no peer review, no debate, no voting.
- **No organization**; the unit of intelligence is the agent.
- **No persistent memory** across projects.
- **No conflict**; no second perspective on architecture decisions.
- **No evolution**; the agent does not restructure itself.
- **Domain-bound** to software engineering.

**Why Veliora is fundamentally different.** Devin is a **specialist
individual**; Veliora is an **organization of specialists**. A
Veliora Engineering Department could in principle use a Devin-like
agent as one of its workers, but the **decision** of whether to ship a
feature would come from the organization (Research on demand, Finance
on cost, Legal on compliance, Engineering on feasibility, CEO on
synthesis), not from Devin alone. Devin answers "can an agent write
this code?"; Veliora answers "should we build this, and why?"

---

## 5. Tier 3 — Consumer AI Products

### 5.1 ChatGPT

**What it does.** ChatGPT (OpenAI) is a consumer AI assistant: a single
LLM (with tool use, web search, code execution, and image generation)
that answers questions, generates content, and assists with tasks in
a chat interface. It is the canonical **single-model** product.

**Architecture.**

```
  User → [LLM + tools (search, code, image)] → Answer
```

The model is stateless across conversations (modulo memory features);
there is no organization, no debate, no voting, no evolution.

**Limitations.**
- **Single model**; the unit of intelligence is the model.
- **No organization**; no departments, no specialization, no hierarchy.
- **No debate**; the model reasons privately (CoT).
- **No voting**; there is no second perspective.
- **No evolution**; the model does not restructure itself.
- **No institutional memory**; chat history is not organizational.
- **No external human outreach**; search is passive retrieval.

**Why Veliora is fundamentally different.** ChatGPT is the **smartest
individual** in the market; Veliora is an **organization**. ChatGPT
answers; Veliora investigates. ChatGPT produces a response; Veliora
produces a decision with dissent, evidence, and risk, traceable to the
departments that produced it. ChatGPT answers "what is the answer?";
Veliora answers "what does an organization conclude, and who
disagrees?"

### 5.2 Claude

**What it does.** Claude (Anthropic) is a consumer AI assistant built on
the Claude family of models, with extended context, tool use, code
execution, and a strong emphasis on safety and alignment via
Constitutional AI. It is a **single-model** product.

**Architecture.**

```
  User → [Claude + tools + Constitutional AI training] → Answer
```

Constitutional AI (Bai et al., 2022) is a **training method**: a model
critiques its own outputs against a set of principles during training.
It is not a runtime organization; there is no voting, no amendment, no
persistent institution.

**Limitations.**
- **Single model**; the unit of intelligence is the model.
- **No organization**; no departments, no specialization.
- **Constitutional AI is a training-time technique**, not a runtime
  constitution the organization enforces on itself and amends by vote.
- **No debate**; the model reasons privately.
- **No evolution**; the model does not restructure itself.
- **No institutional memory**; chat history is not organizational.
- **No external human outreach**.

**Why Veliora is fundamentally different.** Claude's Constitutional
AI is the closest prior art to Veliora's Constitutional Organizations
(C11 in `02-research-contributions.md`), but the gap is categorical:
Constitutional AI is a **single model critiquing itself during
training**; Veliora's constitution is a **runtime charter that an
organization enforces on its own decisions and amends by formal vote**.
Claude answers "what would a principled model say?"; Veliora answers
"what would a principled organization decide, and how did it vote?"

---

## 6. Feature Comparison Matrix

Legend: `✓` = supported, `~` = partial / requires developer effort,
`✗` = not supported.

```
+-----------------------------+--------+---------+----------+--------+---------+--------+--------+----------+----------+
| Feature                     | CrewAI | AutoGen | LangGraph| Swarm  | Manus   | Devin  | ChatGPT | Claude   | Veliora|
+-----------------------------+--------+---------+----------+--------+---------+--------+--------+----------+----------+
| Multi-agent                 | ✓      | ✓       | ✓        | ✓      | ✗       | ✗      | ~ (tools)| ✗       | ✓        |
| Persistent organization     | ✗      | ✗       | ~        | ✗      | ✗       | ✗      | ✗       | ✗        | ✓        |
| Institutional memory        | ✗      | ✗       | ✗        | ✗      | ✗       | ✗      | ~ (chat) | ~ (chat) | ✓        |
| Specialized departments     | ~      | ~       | ~        | ✗      | ✗       | ✗      | ✗       | ✗        | ✓        |
| Genuine mandate conflict    | ✗      | ✗       | ✗        | ✗      | ✗       | ✗      | ✗       | ✗        | ✓        |
| Weighted trust / reputation | ✗      | ✗       | ✗        | ✗      | ✗       | ✗      | ✗       | ✗        | ✓        |
| Society-of-Thought debate  | ~      | ~       | ~        | ✗      | ✗       | ✗      | ✗       | ✗        | ✓        |
| Evidence Challenge (peer)  | ✗      | ✗       | ✗        | ✗      | ✗       | ✗      | ✗       | ✗        | ✓        |
| Live investigation UX       | ✗      | ✗       | ~ (trace)| ✗      | ~       | ~      | ✗       | ✗        | ✓        |
| External human outreach    | ✗      | ✗       | ✗        | ✗      | ✗       | ✗      | ✗       | ✗        | ✓ (Casp.)|
| Multiple organizations      | ✗      | ✗       | ✗        | ✗      | ✗       | ✗      | ✗       | ✗        | ✓        |
| Runtime constitution        | ✗       | ✗      | ✗        | ✗      | ✗       | ✗      | ~ (train)| ~ (train)| ✓        |
| Constitutional amendment    | ✗      | ✗       | ✗        | ✗      | ✗       | ✗      | ✗       | ✗        | ✓ (vote) |
| Self-evolving structure    | ✗      | ✗       | ✗        | ✗      | ✗       | ✗      | ✗       | ✗        | ✓        |
| Hire / fire / promote       | ✗      | ✗       | ✗        | ✗      | ✗       | ✗      | ✗       | ✗        | ✓        |
| Organizational DNA          | ✗      | ✗       | ✗        | ✗      | ✗       | ✗      | ✗       | ✗        | ✓        |
| Investigation as unit of work| ✗      | ✗       | ✗        | ✗      | ✗       | ✗      | ✗       | ✗        | ✓        |
| Dissenting opinions in output| ✗     | ✗       | ✗        | ✗      | ✗       | ✗      | ✗       | ✗        | ✓        |
| User creates org, not code  | ✗      | ✗       | ✗        | ✗      | ~       | ~      | ✓        | ✓        | ✓        |
+-----------------------------+--------+---------+----------+--------+---------+--------+--------+----------+----------+
```

### Categorical distinction table

| Question | Competitors (frameworks) | Competitors (single agents) | Veliora |
|----------|--------------------------|------------------------------|-----------|
| What is the unit of intelligence? | The agent | The agent / model | The organization |
| What is the unit of work? | The task | The task | The investigation |
| What is the unit of output? | A completed task | A completed task | A decision with dissent |
| Who authors the structure? | The developer | The developer / vendor | The organization itself |
| Does it persist? | Per run | Per run / per chat | Across investigations (institutional) |
| Does it evolve? | No (human rewrites) | No | Yes (governed self-modification) |
| Does it debate? | Scripted, if at all | No | Yes (Society-of-Thought) |
| Does it vote? | No | No | Yes (weighted by trust) |
| Does it have a constitution? | No (prompts) | No (prompts / training) | Yes (runtime, votable) |
| Does it reach humans? | Via tools (search) | Via tools (search) | Yes (Caspian, bidirectional) |
| Does it remember org history? | No | No | Yes (Institutional Memory) |
| Does it have internal politics? | No | No | Yes (mandate conflict) |

---

## 7. Why Veliora Is Not a Framework

A frequent misreading is that Veliora is "CrewAI with more agents" or
"LangGraph with a constitution plugin." It is not. The difference is
**what the user builds** and **what the system is**.

### 7.1 What the user builds

```
  CrewAI / AutoGen / LangGraph / Swarm:
    User (developer) writes Python:
      agent1 = Agent(role=..., goal=...)
      task1  = Task(description=..., agent=agent1)
      crew  = Crew(agents=[...], tasks=[...])
      crew.kickoff()
    → The user authors the agent graph.

  Manus / Devin:
    User types a task:
      "Research the top 5 CRM tools."
    → The user delegates a task to an agent.

  ChatGPT / Claude:
    User chats:
      "What are the top 5 CRM tools?"
    → The user asks a model.

  Veliora:
    User creates an organization:
      "Create a Startup Society."
    User asks the organization a question:
      "Should we build an AI healthcare startup?"
    → The user creates an institution and asks it a question.
```

The Veliora user **never** writes an agent, a task, a graph, or a
routine. The user creates an organization (from a template or from
scratch) and asks it questions. The organization decides how to
investigate, which departments to activate, how to debate, and how to
vote.

### 7.2 What the system is

A **framework** is a library the developer uses to build a one-shot
artifact (a crew, a graph, a conversation). When the artifact finishes,
the framework's job is done. The next task requires a new artifact.

An **organization operating system** is a runtime that hosts a
**persistent institution**. The institution:
- Remembers its history (Institutional Memory, `06-memory-system.md`).
- Enforces its own rules (Constitution Engine, `09-constitution-engine.md`).
- Weighs its members by reputation (Trust Engine, `08-trust-engine.md`).
- Debates through Society-of-Thought (`10-debate-engine.md`).
- Reaches the real world through Caspian (`12-caspian-integration.md`).
- Restructures itself through the Evolution Engine
  (`07-evolution-engine.md`).
- Carries its culture through Organizational DNA (`21-organizational-dna.md`).

None of these are "features you can add to CrewAI." They are the
**primitives of a different category**. Adding Institutional Memory to
CrewAI does not make CrewAI into Veliora, because CrewAI still has no
persistent organization to remember anything *about*. Adding a
constitution to LangGraph does not make LangGraph into Veliora,
because LangGraph still has no organization to enforce the constitution
*on*. The primitives only compose into an organization when the
**unit of intelligence** is the organization, not the agent.

### 7.3 The One Agent Test, applied to competitors

Every competitor in this document **fails** the One Agent Test from
`SPEC.md` and `01-core-philosophy.md`:

- CrewAI with one agent is just a single-agent task runner.
- AutoGen with one agent is just a single-agent chat.
- LangGraph with one node is just a single function call.
- Swarm with one agent is just a single-agent routine.
- Manus *is* one agent.
- Devin *is* one agent.
- ChatGPT *is* one model.
- Claude *is* one model.

Veliora **cannot be reduced to one agent** without ceasing to be
Veliora. Strip the organization and you have a single LLM answering a
question — which is ChatGPT. The organization is the product.

---

## 8. Market Positioning

```
                          Single-agent ────────── Multi-agent framework
                          │                                       │
            Devin ── Manus │       CrewAI ── AutoGen ── LangGraph   │
                          │                  ── Swarm               │
                          │                                       │
                          │                                       │
                          │       ┌─────────────────────────┐       │
                          │       │   Veliora             │       │
                          │       │   (organization OS)     │       │
                          │       │   new category         │       │
                          │       └─────────────────────────┘       │
                          │                                       │
            ChatGPT ── Claude                                     │
                          │                                       │
                          Single-model ──────── (no organization)   │
```

Veliora occupies a **new category** that sits orthogonal to the
existing landscape. It is not "more agents than CrewAI" or "more
autonomous than Manus." It is a different thing entirely: an
**operating system for organizations**, where the organization is the
intelligence, the investigation is the unit of work, and the decision
with dissent is the unit of output.

### Positioning statement

> For users who face **decisions** — not tasks — and who want a
> **reasoned, debated, evidence-backed recommendation with dissenting
> opinions preserved**, Veliora is the **organization operating
> system** that creates a living institution to investigate the
> decision. Unlike **agent orchestration frameworks** (CrewAI,
> AutoGen, LangGraph, Swarm), which coordinate agents to complete
> tasks, Veliora creates **organizations that investigate, debate,
> and decide**. Unlike **autonomous task agents** (Manus, Devin) and
> **consumer assistants** (ChatGPT, Claude), which are smart
> individuals, Veliora is a **collective intelligence** that
> outlives any single investigation.

### The one-sentence distinction

> **Competitors coordinate agents to complete tasks. Veliora creates
> organizations that investigate, debate, and decide.**

---

> **Next**: `04-technical-architecture.md` describes the system
> topology that hosts an organization — the runtime, the engines, the
> event bus, and the persistence layer that together make a living
> institution computationally real.
