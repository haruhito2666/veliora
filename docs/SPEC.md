# Veliora — Design Specification

> **Veliora is not a multi-agent platform.**
>
> It is an autonomous organization that investigates, debates, evolves, and
> communicates with the real world to accomplish complex goals.

## Tagline

**The first self-evolving autonomous organization.**

## Mission

Instead of making individual AI agents smarter, Veliora makes entire
organizations intelligent. The organization itself becomes the intelligence.

---

## Core Philosophy

```
Current AI:        User → One LLM → Answer

Multi-Agent:       Planner → Research → Coder → Reviewer → Answer

Veliora:         User → Organization → Investigation → Evidence Collection
                   → Debate → Peer Review → Voting → Decision → Action
                   → Continuous Learning → Organizational Evolution
```

The intelligence **emerges from the organization**, not from any single AI
model. The key message is:

> **Organizations think better than individuals.**

---

## The "One Agent" Test

> Whenever you propose a feature, ask yourself:
> "Would this still exist if there were only one AI agent?"
> If the answer is yes, **redesign it**.

Every important feature should exist **because this is an organization**
rather than an individual AI.

---

## Twelve Novel Research Contributions

### 1. Society-of-Thought

Instead of Chain-of-Thought, the organization reasons **socially**. Every
answer is produced by discussion, not hidden reasoning. Reasoning emerges
through communication between departments and is observable.

### 2. Living Organizations

The organization changes itself. Marketing repeatedly fails → loses
influence. Research repeatedly predicts trends → gains influence. Engineering
overloaded → society hires another engineer. Nobody programs this — it
**emerges**.

### 3. Organizational DNA

Every organization has parameters: Innovation, Risk, Hierarchy, Accuracy,
Speed, Transparency, Creativity, Long-Term Thinking, Security. These affect
communication, planning, approvals, decisions, hiring, voting. Two identical
prompts produce different outcomes depending on organizational DNA.

### 4. Institutional Memory

Not chat history. The organization remembers previous projects, failures,
successful strategies, conflicts, promotions, internal policies, decisions.
Years later it knows **why** something happened.

### 5. Internal Politics

Nobody always agrees. Marketing wants growth. Finance wants savings. Research
wants experimentation. Legal wants safety. The society debates — not
roleplay, but **real conflicting objectives**.

### 6. Live Investigation

Instead of "Thinking..." the user watches Research reading 15 papers, Marketing
surveying users, Finance building a forecast, Engineering evaluating
architecture, CEO waiting for reports. It feels alive.

### 7. Evidence Challenge

Departments don't only collect evidence — they **criticize** it. Research
finds a paper; Engineering says the sample size is too small; Research
rejects; Marketing says customers disagree; Research updates conclusions. The
final recommendation survives peer review.

### 8. Weighted Trust

Every agent earns reputation. Research may have 96% historical accuracy;
Marketing 81%; Finance 94%. Future decisions weigh expertise based on
historical performance.

### 9. External Intelligence (Caspian)

Instead of only searching the web, agents **communicate with real people**.
Marketing asks users on Telegram. Research emails professors. Finance contacts
accountants. Legal emails lawyers. Recruiter contacts candidates. Customer
Success messages beta testers. The organization actively gathers evidence
from people through Caspian's channels.

### 10. Multiple Independent Organizations

Three organizations investigate independently: Startup Society says YES,
Investor Society says MAYBE, Consumer Society says NO. A Meta Society creates
the final recommendation. Different organizations can disagree.

### 11. Constitutional Organizations

Every organization has a constitution: Mission, Principles, Core Values,
Ethics, Decision Rules. The constitution affects every decision. The
organization can later vote to amend it.

### 12. Organization Evolution

The society can hire, fire, promote, create departments, split departments,
merge departments, rewrite policies — without user intervention.

---

## User Experience

The user **never creates agents**. The user creates **organizations**:

- Startup
- Research Lab
- Investment Firm
- Hospital
- Law Firm
- Game Studio
- University
- Personal Decision Council

Every organization has different culture, hierarchy, specialists,
communication rules.

### User Workflow Example

User types: **"Should I build an AI healthcare startup?"**

Immediately Veliora creates an investigation:

- **Research Division** — finds papers, reads reports, finds competitors
- **Marketing** — analyzes demand, surveys users, studies trends
- **Engineering** — checks technical feasibility, estimates dev time, identifies risks
- **Finance** — estimates costs, projects revenue, calculates runway
- **Legal** — checks compliance, reviews regulations, flags licensing issues
- **CEO** — coordinates everyone, schedules meetings, makes the final recommendation

The user watches everything happen live.

---

## Mobile App

**NOT a chat application.** A living organization dashboard.

Home screen shows:

```
Veliora
Startup Organization
────────────────
CEO          Working
Research      Reading papers
Engineering   Implementing
Finance       Reviewing budget
Marketing     Surveying users
────────────────
9 Active Investigations
2 Meetings
34 Messages
Organization Health  96%
```

Tap a department → see memory, conversations, evidence, trust, relationships,
tasks. Tap a meeting → watch the live discussion.

---

## Caspian Integration

Caspian is the **communication nervous system**, not a notification system.

```
Research → Email → Professor
       → Telegram → Founder
       → Discord → Engineering
       → Slack → Marketing
```

The organization reaches the real world. Communication is visible. The
organization naturally chooses the best channel for each interaction.

Supported channels: Email, Telegram, Slack, Discord, WhatsApp, and every
Caspian-supported channel.

---

## Investigation Mode (Flagship Feature)

Instead of answering, the organization launches an **investigation**.

At the end, the organization delivers:

- Executive Summary
- Final Recommendation
- Confidence Score
- Dissenting Opinions
- Supporting Evidence
- Risks
- Opportunities
- Suggested Next Steps
- Full Organizational Discussion

---

## MVP (Hackathon)

Build only:

- Startup Organization template
- Five agents (CEO, Research, Engineering, Finance, Marketing)
- Society-of-Thought
- Live Investigation
- Evidence Challenge
- Caspian communication
- Final board report
- Mobile UI with live organization status

Everything else is the roadmap.

---

## Key Framing

> **Veliora doesn't answer questions. It conducts investigations.**

Do **not** market it as "AI employees." Market it as:

> **"The first autonomous organization that investigates and makes decisions
> like a real company."**

---

## Document Index

| #  | Document                        | Description                                         |
|----|---------------------------------|-----------------------------------------------------|
| 00 | Product Vision                  | The vision, positioning, and narrative              |
| 01 | Core Philosophy                 | Organizations-as-intelligence paradigm             |
| 02 | Research Contributions          | Novel academic/research contributions               |
| 03 | Competitive Analysis            | vs CrewAI, AutoGen, LangGraph, Manus, Devin, Swarm  |
| 04 | Technical Architecture          | System-level architecture overview                  |
| 05 | Agent Architecture              | Distributed worker/department design                |
| 06 | Memory System                   | Institutional memory design                         |
| 07 | Evolution Engine                | Org self-modification engine                        |
| 08 | Trust Engine                    | Reputation and weighted trust system                |
| 09 | Constitution Engine             | Constitutional governance system                    |
| 10 | Debate Engine                   | Society-of-Thought debate mechanics                 |
| 11 | Communication Engine            | Internal messaging and meeting system               |
| 12 | Caspian Integration             | External communication nervous system               |
| 13 | Database Design                 | Data model and persistence                          |
| 14 | Knowledge Graph                 | Organizational knowledge representation             |
| 15 | Folder Structure                | Project/repo layout                                 |
| 16 | Event System                    | Event-driven architecture design                    |
| 17 | Prompt Architecture             | Prompt templates and orchestration                  |
| 18 | Planner Design                  | Investigation planning and task decomposition       |
| 19 | Investigation Engine            | Flagship investigation orchestration                 |
| 20 | Meeting Engine                  | Meeting scheduling and execution                    |
| 21 | Organizational DNA              | DNA parameter model and effects                     |
| 22 | Mobile UX                       | Mobile app design                                   |
| 23 | UI/UX Flow                      | End-to-end user flows                               |
| 24 | API Design                      | REST/GraphQL/WebSocket API surface                  |
| 25 | State Machine                   | Organization and investigation state machines        |
| 26 | Security                        | Security model and threat surface                   |
| 27 | Scaling Strategy                | Horizontal/vertical scaling plan                     |
| 28 | MVP (Hackathon)                 | Hackathon MVP scope and build plan                  |
| 29 | Future Roadmap                  | Post-MVP roadmap                                    |
| 30 | Startup Vision                  | Company vision, GTM, business model                 |
