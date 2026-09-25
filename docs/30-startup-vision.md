# 30 — Startup Vision

> The company behind Veliora. The market, the go-to-market, the business
> model, the moat, and the three-year plan. This is the document an investor
> reads after `00-product-vision.md` and `28-mvp-hackathon.md`.

---

## 1. Vision Statement

> **Veliora is the first self-evolving autonomous organization.**
>
> We do not build AI agents. We build organizations that investigate, debate,
> and decide — and that remember, evolve, and reach the real world. The
> organization is the intelligence.

The company's job is to make the organizational paradigm the default way
complex decisions are made — not just by founders, but by investors,
researchers, enterprises, and eventually any institution that currently
relies on a single advisor or a static analysis.

---

## 2. The Market

### 2.1 Decision Intelligence Market

Veliora sits at the intersection of three growing markets:

```
                    +-----------------------+
                    |   DECISION INTELLIGENCE|
                    |   $50B+ by 2030       |
                    +-----------+-----------+
                                |
              +-----------------+-----------------+
              |                 |                 |
              v                 v                 v
    +---------+-------+ +-------+-------+ +-------+-------+
    | AI-Powered      | | Autonomous    | | Organizational |
    | Organizational  | | Research      | | Simulation     |
    | Simulation      | |               | |                |
    | $15B            | | $10B          | | $25B           |
    +-----------------+ +---------------+ +----------------+
```

**Decision intelligence** — software that helps humans make better decisions
— is a category being created right now, driven by LLMs crossing the
reasoning threshold. Current players (Gartner, Forrester, McKinsey) sell
**human** analysis at consulting rates. Veliora sells **autonomous
organizational** analysis at software margins.

### 2.2 AI-Powered Organizational Simulation

No one currently simulates an entire organization's decision-making process.
The closest analogs:

| Analog | What it does | What it lacks |
|--------|-------------|---------------|
| McKinsey | Human teams analyze + recommend | Slow, expensive, no memory, no evolution |
| Gartner | Research + recommendations | Static reports, no debate, no real-world reach |
| CrewAI / LangGraph | Multi-agent orchestration | No organization — just task pipelines |
| ChatGPT / Claude | Single-agent reasoning | No debate, no dissent, no division of labor |
| Strategy consulting software | Static models + dashboards | No autonomous investigation |

Veliora is the first product that **simulates the organization itself** as
the intelligence — not as a tool that helps humans, but as an entity that
investigates and decides.

### 2.3 Autonomous Research

The investigation paradigm (see `19-investigation-engine.md`) turns research
from a **human activity augmented by AI** into an **autonomous organizational
process**. The market for autonomous research spans:

- **Investment due diligence** — funds that need 24/7 investigation capacity
- **Market intelligence** — companies that need continuous market monitoring
- **Regulatory analysis** — firms that need to track evolving regulations
- **Technical due diligence** — acquirers evaluating technology targets
- **Academic research synthesis** — labs that need to synthesize literature

Each of these is currently served by **people** at **consulting rates**.
Veliora serves them with **organizations** at **software rates**.

### 2.4 Market Sizing (Bottom-Up)

| Segment | Target users | Price/mo | TAM |
|----------|-------------|----------|-----|
| Startup founders | 500K globally | $99 | $600M |
| Investment firms | 10K firms | $2,000 | $240M |
| Enterprise strategy teams | 50K teams | $10,000 | $6B |
| Research institutions | 20K institutions | $1,000 | $240M |
| Consulting firms (internal tool) | 5K firms | $5,000 | $300M |
| **Total addressable** | | | **~$7.4B** |

These are conservative annual-recurring figures assuming 10% penetration
of each segment. The decision-intelligence umbrella is larger; Veliora
captures the slice that values **autonomous, organizational** investigation.

---

## 3. The One Agent Test as Business Moat

The One Agent Test (see `01-core-philosophy.md`) is not just a design
principle — it is the **competitive moat**.

> Any feature that passes the One Agent Test (would still make sense with one
> agent) can be replicated by OpenAI, Anthropic, or Google in a single model
> update. Any feature that **fails** the test (only makes sense as an
> organization) is defensible, because no single-model vendor has the
> architectural substrate to reproduce it.

| Feature | One Agent Test | Defensible? |
|---------|----------------|-------------|
| Better reasoning | Passes (one agent can reason) | No — model vendors win |
| Tool use | Passes (one agent can use tools) | No — model vendors win |
| Web search | Passes (one agent can search) | No — model vendors win |
| Multi-agent task routing | Passes (a planner agent suffices) | No — framework vendors win |
| Society-of-Thought debate | **Fails** (needs conflicting objectives) | **Yes** |
| Evidence challenge | **Fails** (needs peer review) | **Yes** |
| Institutional memory | **Fails** (needs persistent collective) | **Yes** |
| Organizational evolution | **Fails** (needs self-restructuring) | **Yes** |
| Organizational DNA | **Fails** (needs parameterized collective) | **Yes** |
| Multiple disagreeing orgs | **Fails** (needs independent collectives) | **Yes** |

The moat is **architectural**, not algorithmic. A model vendor can make a
smarter model, but it cannot make a smarter **organization** without
building the organizational substrate Veliora is building. See
`03-competitive-analysis.md` for the full competitive analysis.

---

## 4. Go-To-Market Strategy

```
Stage 0          Stage 1          Stage 2          Stage 3          Stage 4
Hackathon  ────> YC/Accelerator ──> Pilot with   ──> Investment  ──> Enterprise
Demo            (3-6 months)      founders       firms            strategy
                                  (6-12 mo)      (12-18 mo)       (18+ mo)
```

### Stage 0 — Hackathon Demo (Now)

**Goal:** Prove the thesis with a working demo (see `28-mvp-hackathon.md`).

**What we get:**

- A working prototype that demonstrates the organizational paradigm
- A demo video usable in every future pitch
- Credibility ("we built this in 48 hours")
- Initial interest from founders and investors who see the demo

**Success metric:** Demo is reproducible; 3+ investors/founders express
interest after seeing it.

### Stage 1 — Accelerator (Months 0-6)

**Goal:** Get into YC or a top accelerator. Build Phase 1 (see
`29-future-roadmap.md`): institutional memory, trust engine, evolution
engine, constitution engine.

**Why an accelerator:**

- Credibility + network for enterprise sales later
- Seed funding to hire 3-5 engineers
- Mentorship on GTM for a novel category
- Demo Day exposure to seed investors

**What we build:** Phase 1 features. The product goes from "demo" to "usable
for real investigations with memory and trust."

**Success metric:** Accepted to YC or equivalent; Phase 1 shipped; 10 pilot
founders using it weekly.

### Stage 2 — Pilot with Startup Founders (Months 6-12)

**Target user:** Startup founders making high-stakes decisions — "Should I
build this?", "Should I raise now?", "Should I pivot?", "Should I acquire
this company?"

**Why founders first:**

- High-stakes, frequent decisions → high engagement
- Comfortable with early-stage, imperfect tools
- Vocal + networked → word-of-mouth growth
- Low sales friction (self-serve, freemium)
- Their decisions are verifiable → trains the trust engine

**GTM motion:** Product-led growth. Founders try the free tier for one
investigation, see the depth (debate, dissent, evidence challenge), and
upgrade for ongoing investigations + memory.

**Pilot design:**

- 50 founders, 3 months, free Pro tier
- Weekly interviews: "What did you investigate? Did the report help?"
- Track: decision adoption rate, investigation frequency, NPS
- Goal: 30% of pilot founders become paying; 3 case studies produced

**Success metric:** 100 paying founders ($99/mo); 3 published case studies;
organic referrals driving 20% of new signups.

### Stage 3 — Investment Firms (Months 12-18)

**Target user:** Venture capital firms, angel networks, family offices that
need due diligence on deals.

**Why investment firms next:**

- They make the exact decisions Veliora is built for (invest/no-invest)
- They pay 10-100x more than founders
- They have verifiable outcomes (did the deal return?) → trains trust
- They're networked → one firm adopts, others follow
- Their investigations are deep → showcases the full engine

**GTM motion:** Outbound + referrals. Case studies from Stage 2 founders
(who are often LPs or ex-investors) open doors. Custom DNA presets for
investment DNA (conservative, risk-aware, accuracy-extreme).

**Pilot design:**

- 10 firms, 6 months, custom onboarding
- Each firm gets a custom org (Investment Firm template + their DNA)
- Track: due diligence time reduction, decision quality (retrospective)
- Goal: 5 firms convert to enterprise contracts

**Success metric:** 5 enterprise investment firms ($2K-$10K/mo); 1 marquee
case study (named firm); ARR $500K.

### Stage 4 — Enterprise Strategy (Months 18+)

**Target user:** Corporate strategy teams at large enterprises — "Should we
enter this market?", "Should we acquire this company?", "Should we build or
buy?"

**Why enterprise last:**

- Long sales cycles (6-12 months)
- High contract value ($10K-$100K/mo)
- Need on-premise / private deployment (security)
- Need custom org types (Phase 3 templates + Phase 6 SDK)
- Reference customers (Stages 2-3) de-risk the purchase

**GTM motion:** Enterprise sales team + channel partners. Named accounts
with ABM. Custom org types built by Veliora (early Phase 6).

**Success metric:** 20 enterprise customers; ARR $5M; 1-2 public case
studies.

---

## 5. Business Model

Three tiers. The free tier is generous enough to prove the paradigm; the paid
tiers monetize the features that only matter at organizational scale.

```
+================================================================+
|  FREEMIUM              PRO                  ENTERPRISE          |
|  $0/mo                 $99/mo               $10K+/mo            |
|  ----------            ----------           ----------          |
|  1 organization        Multiple orgs        Custom org types    |
|  Basic templates       Custom DNA           On-premise option   |
|  1 investigation/mo    Unlimited inv.       API access          |
|  No memory             Full memory          SSO + audit         |
|  2 Caspian channels    All channels         Dedicated support   |
|  Community support     Email support        SLA + onboarding    |
+================================================================+
```

### Tier Details

| Feature | Free | Pro ($99/mo) | Enterprise ($10K+/mo) |
|---------|------|--------------|------------------------|
| Organizations | 1 | Up to 5 | Unlimited |
| Templates | Startup, Personal Council | All templates | Custom org types |
| Investigations/mo | 1 | Unlimited | Unlimited |
| Institutional memory | None | Full | Full + private memory |
| Trust engine | None | Full | Full + custom calibration |
| Evolution engine | None | Full | Full + governance controls |
| Constitution | Default only | Custom | Custom + legal review |
| Caspian channels | Email + Telegram | All | All + private channels |
| DNA customization | Presets only | Full sliders | Per-department DNA |
| Meta-organization | No | Yes (3 sub-orgs) | Unlimited |
| API access | No | Read-only | Full read/write |
| Deployment | Cloud | Cloud | Cloud or on-premise |
| Support | Community | Email, 48h SLA | Dedicated, 4h SLA |
| Security | Standard | SOC2 | SOC2 + HIPAA + custom |

### Why This Pricing Works

- **Free** proves the paradigm. One investigation is enough to see the
  debate, dissent, and evidence challenge. The hook is the org dashboard.
- **Pro** monetizes **organizational** features: memory (needs persistence),
  multiple orgs (needs isolation), evolution (needs history). These all fail
  the One Agent Test — a single-agent tool can't offer them.
- **Enterprise** monetizes **control + scale**: custom org types, on-premise,
  API access, security. These are the needs of large institutions.

The pricing is deliberately **not per-agent** or **per-query** — it's
**per-organization**, because the organization is the unit of value.

### Revenue Projections (3 Years)

| Period | Free users | Pro users | Enterprise | ARR |
|--------|-----------|-----------|------------|-----|
| Year 1 (post-accelerator) | 2,000 | 100 | 0 | $120K |
| Year 2 | 15,000 | 800 | 5 | $1.4M |
| Year 3 | 50,000 | 3,000 | 25 | $6.3M |

Year 3 ARR assumes 6% Pro conversion of free users + 25 enterprise contracts
at $150K average. Conservative; enterprise contracts could be larger.

---

## 6. Differentiation

### The Core Differentiator

> **Veliora does not sell "AI employees." It sells autonomous
> organizations that investigate.**

Every competitor frames AI as **individual workers** (agents, copilots,
assistants). Veliora frames AI as an **organization** — a collective that
investigates, debates, and decides. This is not a feature difference; it is a
**category difference**.

### Competitive Positioning

```
                    SINGLE AGENT          MULTI-AGENT          ORGANIZATION
                    ----------          -----------          ------------
    Speed/low cost  ChatGPT             CrewAI               Veliora
                    Claude              AutoGen
                    Gemini               LangGraph
                                        |
                    Task completion      Task routing          Investigation
                    "Answer this"       "Do these steps"      "Decide this"
                                        |
                    No debate            Simulated debate     Real debate
                    No dissent           No dissent           Real dissent
                    No memory            No memory            Institutional memory
                    No evolution         No evolution        Self-evolving
                                        |
                    ------------------- THESIS LINE -------------------
                    "AI helps you."      "AI agents do work." "An AI org decides."
```

### Positioning vs Specific Competitors

| Competitor | Their frame | Our frame | Why we win |
|------------|-----------|-----------|------------|
| CrewAI | Multi-agent framework | We're not a framework — we're an org | They help devs build agents; we give users an org |
| AutoGen | Conversational agents | Conversation ≠ organization | We have structure, memory, evolution, constitution |
| LangGraph | Graph-based agent orchestration | Orchestration ≠ intelligence | The intelligence is the org, not the graph |
| Manus / Devin | Autonomous task agent | Task completion ≠ investigation | We investigate; they execute tasks |
| ChatGPT/Claude | Smarter single model | Smarter model ≠ smarter org | Model vendors can't build orgs without our substrate |
| McKinsey/Gartner | Human consulting | Humans don't scale | We deliver org-quality analysis at software cost |

See `03-competitive-analysis.md` for the full analysis.

### The Line We Will Not Cross

We will **never** market Veliora as "AI employees" or "AI agents." Those
frames reduce us to a multi-agent tool and invite comparison on model quality
— a race we lose to OpenAI. The organizational frame is the **only** frame
that makes our architectural choices legible as features rather than
limitations.

---

## 7. The Moat

Three layers, each deeper than the last.

### Layer 1 — Institutional Memory (Data Moat)

Every investigation produces memory. The longer an org exists, the richer its
memory, and the better its decisions. A new competitor starts with zero
memory; Veliora orgs have months or years of context.

**Why it's a moat:** Memory is **non-transferable**. A user's org has memory
of *their* decisions, *their* industry, *their* failures. Switching to a
competitor means losing that institutional context. This is the same moat
that makes enterprises sticky (ERP systems, CRM data) — but applied to
decision intelligence.

### Layer 2 — Organizational DNA (Parameter Moat)

Every org develops a DNA profile tuned to its user's decision style. A
founders' org, an investor's org, an enterprise's org each have different
DNA. This tuning is the result of months of investigations + evolution.

**Why it's a moat:** DNA is **co-evolved** with the user. A competitor can
offer the same parameters, but not the tuned values that reflect the user's
history. Switching means re-tuning from scratch.

### Layer 3 — Network Effects of Multiple Organizations (Network Moat)

In Phase 5 (see `29-future-roadmap.md`), organizations communicate and form
ecosystems. The more orgs on Veliora, the richer the ecosystem
intelligence. A startup org can query an investor org; a research org can
peer-review a hospital org.

**Why it's a moat:** Network effects are the **deepest moat**. A competitor
with fewer orgs offers a weaker ecosystem. The first platform to reach
critical mass in organizational intelligence wins the category — the same
way LinkedIn won professional networks.

### Moat Durability

```
Time ──────────────────────────────────────────────────────>

  Competitor copies Veliora:

  Layer 1 (Memory)    [-------- can't copy --------]  never
  Layer 2 (DNA)       [---- can copy params ----]    months
                       [can't copy tuned values]      years
  Layer 3 (Network)   [-------- can't copy --------]  never

  Model vendor improves model:

  Single-agent features  [copied in one model update]  weeks
  Org features           [-------- can't copy --------]  never
```

---

## 8. Team Needs

### Founding Team (Pre-Seed)

| Role | Count | Profile | Why |
|------|-------|---------|-----|
| CEO / Product | 1 | Ex-product founder, understands the org paradigm | Vision + GTM |
| Backend / Architecture | 1 | Strong async Python, distributed systems | The org substrate |
| ML / Prompt engineering | 1 | LLM orchestration, prompt design, evals | The org intelligence |
| Full-stack / Mobile | 1 | Kotlin/Android + backend, real-time UX | The org interface |

### Seed Stage (Post-Accelerator, 3-5 People)

Add:

| Role | Count | Why |
|------|-------|-----|
| Backend engineer | 1 | Phase 1 engines (memory, trust, evolution) |
| Infrastructure engineer | 1 | Multi-tenancy, scaling, Caspian integration |
| Designer | 1 | Mobile UX is the product surface; needs dedicated design |

### Series A (Year 2, 8-12 People)

Add:

| Role | Count | Why |
|------|-------|-----|
| Enterprise sales | 2 | Stage 3-4 GTM |
| Backend / platform | 2 | Phase 3-4 features, marketplace, API |
| ML research | 1 | Debate quality, trust calibration, evals |
| Customer success | 1 | Onboarding pilots + enterprise |
| Security / compliance | 1 | SOC2, HIPAA for enterprise |

### Hiring Principle

We hire for **organizational thinking**, not agent thinking. Every engineer
must internalize the One Agent Test. A candidate who frames problems as "how
do we make the agent smarter" is wrong for us; we want "how do we make the
organization smarter." This is a filter, not a nice-to-have.

---

## 9. Funding Strategy

### Stage 0 — Hackathon (Now)

- **Cost:** $0 (sweat equity + existing LLM API credits)
- **Goal:** Working demo + video
- **Funding:** None needed

### Stage 1 — Pre-Seed / Accelerator (Months 0-6)

- **Source:** YC ($500K standard) or angel round ($500K-$1M)
- **Use:** Hire founding team (4 people), build Phase 1, run founder pilots
- **Valuation target:** $5M-$10M (pre-product, post-demo)
- **Key milestone for raise:** Demo + Phase 1 design + 10 committed pilot
  founders

### Stage 2 — Seed (Months 6-12)

- **Source:** Seed VCs ($2M-$5M)
- **Use:** Scale team to 7-8, ship Phase 2 (DNA + multiple orgs), reach 100
  paying founders
- **Valuation target:** $20M-$40M (post-Phase 1, with revenue + pilots)
- **Key milestone for raise:** $20K MRR, 3 case studies, Phase 2 shipped

### Stage 3 — Series A (Months 12-24)

- **Source:** Series A VCs ($10M-$20M)
- **Use:** Scale team to 12-15, ship Phase 3-4, enter investment firms +
  enterprise, build sales team
- **Valuation target:** $80M-$150M (post-seed metrics, enterprise pipeline)
- **Key milestone for raise:** $1M ARR, 5 enterprise contracts, Phase 4
  shipped

### Stage 4 — Series B (Year 3)

- **Source:** Growth VCs ($30M-$50M)
- **Use:** Scale team to 30-40, ship Phase 5-6, international expansion,
  marketplace launch
- **Valuation target:** $300M-$500M
- **Key milestone for raise:** $5M ARR, 25 enterprise customers, marketplace
  with 3rd-party org types

### Funding Philosophy

We raise to **accelerate the roadmap**, not to extend runway. Each raise is
tied to a phase milestone (see `29-future-roadmap.md`). If we can't ship the
phase, we don't deserve the next round. The roadmap is the fundraising
narrative.

---

## 10. Three-Year Vision

### Year 1 — Prove the Paradigm

- Hackathon demo → accelerator → Phase 1 shipped
- 100 paying founders using Veliora for real decisions
- 3 published case studies ("How a founder used Veliora to decide not to
  build a startup — and saved $500K")
- Seed raised; team at 7-8
- **The org paradigm is real.**

### Year 2 — Expand the Category

- Phase 2-3 shipped: DNA, multiple orgs, templates marketplace
- 1,000 paying users (founders + early investors)
- 5 enterprise investment firms as customers
- Series A raised; team at 12-15
- First 3rd-party org templates published
- **Decision intelligence is an autonomous category, not a human one.**

### Year 3 — Own the Ecosystem

- Phase 4-5 shipped: full Caspian suite, action execution, org ecosystems
- 25 enterprise customers; $5M+ ARR
- Marketplace with 50+ org types
- Org-to-org ecosystem live (startup orgs querying investor orgs)
- Series B raised; team at 30-40
- **Veliora is the platform where organizations live.**

### The North Star

```
Year 1:  "An organization can decide."        (Prove it)
Year 2:  "Organizations can differ."          (Parameterize it)
Year 3:  "Organizations can collaborate."    (Network it)
Year 5:  "Organizations are the default       (Own it)
          way complex decisions are made."
```

---

## 11. Risks & Mitigations

| Risk | Likelihood | Impact | Mitigation |
|------|-----------|--------|------------|
| Model vendors add "org mode" | Medium | High | They can't — org features fail the One Agent Test. But they can fake it; our moat is real memory + DNA + network, not surface features. |
| LLM cost makes investigations expensive | High | Medium | Model routing (cheap models for routine turns, expensive for debate/synthesis); caching; self-hosted open models for high-volume tasks. |
| Users don't trust autonomous decisions | High | High | Transparency is the product — debate, dissent, and evidence are visible. Trust is earned by showing the work, not hiding it. |
| Enterprise sales cycle too slow | Medium | Medium | Start with founders (low friction); use founder case studies to open enterprise doors; offer pilot-first enterprise terms. |
| Org behavior is non-deterministic | Medium | Medium | Determinism mode for testing; low-temperature defaults; recorded LLM responses for regression suites. See `25-state-machine.md`. |
| Safety of autonomous actions (Phase 4) | Medium | High | Constitutional gates, approval workflows, spending limits, sandbox mode. No irreversible action without human approval. See `26-security.md`. |
| Category never forms | Low | High | The decision-intelligence category is already forming (Gartner, Forrester tracking it). We define the autonomous subcategory. |
| We get copied by a framework vendor | Medium | Medium | Frameworks help devs build agents; we give users an org. Different buyer, different value. Our moat is data (memory, DNA, network), not code. |

---

## 12. Why Now

Three trends converge in 2026-2027:

1. **LLM reasoning is good enough** — models can sustain a multi-turn debate
   with genuine conflict, not just agree with themselves.
2. **Multi-agent fatigue** — the market has seen CrewAI, AutoGen, LangGraph,
   and realized that gluing agents together doesn't produce intelligence. The
   organizational paradigm is the next frame.
3. **Decision intelligence is a funded category** — enterprises are buying
   tools to make better decisions, but current tools are dashboards and
   human-written reports. The gap between "dashboard" and "autonomous
   investigation" is exactly what Veliora fills.

The window is now because the **frame** is ready. In 2023, "autonomous
organization" sounded like science fiction. In 2026, after two years of
multi-agent frameworks that didn't deliver intelligence, the market is ready
to hear that the **organization** is the intelligence — not the agent.

---

## 13. Summary

Veliora is not a better agent. It is a **new unit of intelligence**: the
organization. The market is decision intelligence, currently served by humans
at consulting rates. The product is an autonomous organization that
investigates, debates, and decides — with memory, DNA, and a network of
peer organizations. The moat is architectural: features that fail the One
Agent Test cannot be copied by model vendors or framework vendors. The GTM
starts with founders (low friction, verifiable decisions), expands to
investors (high value, networked), and ends at enterprise (high contract
value, custom org types). The roadmap (see `29-future-roadmap.md`) is the
fundraising narrative. The MVP (see `28-mvp-hackathon.md`) is the proof.

> **Organizations think better than individuals. We build the organizations.**
