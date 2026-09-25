# 15 — Folder Structure

> Project Repository Layout

This document defines the complete repository layout for Veliora. The
layout is a **monorepo** organized so that each engine, service, and template
can be developed, tested, and versioned **independently** while sharing a
common contract surface. The guiding rule: a contributor working on the Trust
Engine should never need to touch the Debate Engine, and vice versa.

For what each engine *does*, see the per-engine docs (`06`–`12`,
`17`–`21`). For the services that host these engines, see
`04-technical-architecture.md`. For the worker/department model that the
runtime services execute, see `05-agent-architecture.md`.

---

## 1. Design Principles

1. **Engine independence.** Every engine under `backend/engines/` is a
   self-contained package with its own dependencies, tests, and README. Engines
   communicate only through the shared contract layer in `backend/shared/`
   and the event bus — never by importing each other's internals.
2. **Templates are data, not code.** Organization templates and prompt
   templates live under `templates/` as declarative files (YAML/Markdown) so a
   new organization type can be added without writing code.
3. **One source of truth for schemas.** All cross-service message types, event
   names, and data models are defined once in `backend/shared/contracts/` and
   generated into each language/client.
4. **Migrations are first-class.** Schema changes for every datastore live in
   `migrations/` and are applied by the same tooling in every environment.
5. **Config by environment, not by file edits.** `configs/` holds per-env
   overlays; no secrets in the repo.
6. **Docs live with the repo.** This `docs/` tree is the canonical design record
   (see `INDEX.md`).

---

## 2. Top-Level Tree

```
veliora/
├── README.md
├── LICENSE
├── pyproject.toml                # Python monorepo workspace root (backend)
├── docker-compose.yml            # local dev stack: postgres, neo4j, redis, bus
├── Makefile                      # common tasks: make test, make migrate, make seed
├── .github/
│   └── workflows/                # CI: lint, test, build, migrate-check
├── docs/                         # design docs (this file lives here)
├── backend/                      # all server-side code
├── templates/                    # organization + prompt templates (data)
├── mobile/                       # Kotlin Android app (Jetpack Compose)
├── migrations/                   # DB migrations for all datastores
├── tests/                        # cross-cutting tests (integration, e2e)
├── configs/                      # per-environment configuration overlays
├── scripts/                      # ops/dev tooling
└── infra/                        # deployment manifests (k8s, terraform)
```

The **backend** is Python (`pyproject.toml`, LLM ecosystem, data science)
and the **mobile** client is Kotlin/Jetpack Compose, built with Gradle
inside `mobile/`. They share types via generated contracts from
`backend/shared/contracts/`.

---

## 3. `backend/` — Server-Side Code

```
backend/
├── services/                     # deployable runtime services
│   ├── api-gateway/               # public REST/GraphQL/WebSocket surface (see 24-api-design.md)
│   ├── org-manager/              # org lifecycle: create, instantiate template, archive
│   ├── worker-runtime/          # worker pool, turn execution, LLM calls (see 05-agent-architecture.md)
│   ├── scheduler/                # task DAG scheduling, worker→slot assignment (see 18-planner-design.md)
│   └── event-bus/                # event broker abstraction (NATS/Redis Streams) (see 16-event-system.md)
│
├── engines/                      # the Veliora engines — each independently developable
│   ├── memory-engine/            # institutional memory (see 06-memory-system.md)
│   ├── evolution-engine/         # self-modification (see 07-evolution-engine.md)
│   ├── trust-engine/             # reputation & weighted trust (see 08-trust-engine.md)
│   ├── constitution-engine/      # governance & voting rules (see 09-constitution-engine.md)
│   ├── debate-engine/            # society-of-thought debate (see 10-debate-engine.md)
│   ├── communication-engine/     # internal messaging & meetings glue (see 11-communication-engine.md)
│   ├── caspian-engine/           # external comms nervous system (see 12-caspian-integration.md)
│   ├── knowledge-graph-engine/   # org knowledge representation (see 14-knowledge-graph.md)
│   ├── planner-engine/           # investigation planning & decomposition (see 18-planner-design.md)
│   ├── investigation-engine/      # flagship investigation orchestration (see 19-investigation-engine.md)
│   ├── meeting-engine/           # meeting scheduling & execution (see 20-meeting-engine.md)
│   └── dna-engine/               # DNA parameter model & effects (see 21-organizational-dna.md)
│
└── shared/                       # cross-cutting libraries shared by services + engines
    ├── contracts/                # single source of truth: event types, DTOs, RPC schemas
    ├── models/                   # persisted domain models (Worker, Department, Evidence, ...)
    ├── llm/                      # LLM client abstraction (provider-agnostic)
    ├── events/                   # event publishing/subscription helpers
    ├── telemetry/                # logging, metrics, tracing
    └── config/                   # config loading + validation
```

### 3.1 `backend/services/` — Runtime Services

These are the **deployable units**. Each is a service with its own process
boundary, health endpoint, and horizontal-scaling story (see
`27-scaling-strategy.md`).

| Service           | Responsibility                                              | Owns workers? |
|-------------------|-------------------------------------------------------------|---------------|
| `api-gateway`     | Auth, rate limiting, REST/GraphQL/WebSocket fan-out        | No            |
| `org-manager`     | Org CRUD, template instantiation, org archival              | No            |
| `worker-runtime`  | Worker pool, turn execution, LLM calls, heartbeats         | Yes (slots)   |
| `scheduler`       | Reads Planner DAG, assigns tasks to workers                | No            |
| `event-bus`       | Broker abstraction; pub/sub for all engine events          | No            |

### 3.2 `backend/engines/` — The Engines

Engines are **libraries**, not services. They are loaded by the runtime
services (e.g. `worker-runtime` loads `memory-engine` and `trust-engine`) and
react to events from the bus. Keeping them as libraries (rather than
microservices) avoids network hops for hot paths while still enforcing
interface boundaries via `shared/contracts/`. When an engine grows heavy
enough to warrant its own process (see `27-scaling-strategy.md`), it can be
extracted into a service without changing its contract.

Each engine package follows the same internal skeleton:

```
engines/<name>-engine/
├── pyproject.toml               # own deps + entry points; installable standalone
├── README.md                    # what it does, how to run its tests
├── src/<name>_engine/
│   ├── __init__.py
│   ├── engine.py                # implements the Engine interface (start/stop/handle)
│   ├── handlers/                # one file per event handler
│   ├── models.py                # engine-local data structures
│   ├── policy/                  # engine-specific rules (e.g. trust weights)
│   └── cli.py                   # standalone runner for local debugging
└── tests/                       # unit tests scoped to this engine only
```

This uniformity is what makes engines independently developable: a new
contributor can `cd` into any `engines/<x>/`, read its README, run its tests,
and ship a change without learning the rest of the system.

### 3.3 `backend/shared/` — Shared Layer

`shared/contracts/` is the **only** place where cross-component types are
defined. From these source definitions (protobuf/JSON Schema), bindings are
generated for Python (`shared/contracts/_generated/py/`) and Kotlin
(`mobile/app/src/main/java/com/veliora/data/api/_generated/`). This guarantees the mobile app, the API
gateway, and every engine agree on the shape of a `Worker`, an
`EvidenceSubmitted` event, etc. See `24-api-design.md` for the API surface
and `16-event-system.md` for the event catalog.

---

## 4. `templates/` — Organization & Prompt Templates

```
templates/
├── organizations/                # one directory per organization type
│   ├── startup/
│   │   ├── org.yaml              # departments, roles, headcounts, DNA defaults
│   │   ├── constitution.md       # mission, principles, decision rules
│   │   ├── dna.yaml              # innovation/risk/hierarchy/... defaults
│   │   └── README.md
│   ├── research-lab/
│   ├── investment-firm/
│   ├── hospital/
│   ├── law-firm/
│   ├── game-studio/
│   ├── university/
│   └── personal-decision-council/
│
└── prompts/                      # prompt templates (see 17-prompt-architecture.md)
    ├── system/                   # base system prompts (org-level framing)
    ├── roles/                    # one per role: researcher.md, engineer.md, ceo.md, ...
    ├── meetings/                 # meeting chair, turn, agenda prompts
    ├── debate/                   # challenge, rebuttal, verdict prompts
    └── reports/                  # final board report assembly prompts
```

### 4.1 `templates/organizations/`

An organization template is **declarative data**. Adding the "Hospital"
organization type means adding `templates/organizations/hospital/` with an
`org.yaml`, a `constitution.md`, and a `dna.yaml` — **no Python code**. The
`org-manager` service reads the directory and materializes workers from it
(see `05-agent-architecture.md` §3.3). Example `org.yaml`:

```yaml
name: Startup Society
departments:
  - id: ceo_office
    roles: [{ role: CEO, count: 1 }]
    head: auto
  - id: research
    roles: [{ role: RESEARCHER, count: 2 }]
    head: elected
  - id: engineering
    roles: [{ role: ENGINEER, count: 2 }]
    head: elected
  - id: finance
    roles: [{ role: FINANCE_ANALYST, count: 1 }]
    head: auto
  - id: marketing
    roles: [{ role: MARKETER, count: 1 }]
    head: auto
dna_ref: dna.yaml
constitution_ref: constitution.md
```

### 4.2 `templates/prompts/`

Prompts are **templates**, not hardcoded strings, so they can be tuned
without redeploying code (see `17-prompt-architecture.md`). Role prompts are
selected by a worker's `role`; meeting/debate/report prompts are selected by
the relevant engine. Keeping them in the repo (rather than a DB) means they
are versioned, peer-reviewed, and diffable.

---

## 5. `mobile/` — Living Organization Dashboard

```
mobile/
├── build.gradle.kts             # root Gradle build (project-level)
├── settings.gradle.kts          # module includes, dependency resolution
├── gradle/
│   └── libs.versions.toml       # version catalog (Compose, Navigation, OkHttp, ...)
└── app/
    ├── build.gradle.kts         # app module build (Compose, dependencies, SDK config)
    └── src/
        ├── main/
        │   ├── AndroidManifest.xml
        │   ├── java/com/veliora/
        │   │   ├── MainActivity.kt     # single-activity entry (Compose host)
        │   │   ├── ui/
        │   │   │   ├── screens/        # Home (org dashboard), Department, Meeting, Investigation, Report
        │   │   │   ├── components/     # WorkerCard, EvidenceList, LiveMeetingView, TrustMeter, ...
        │   │   │   ├── theme/          # Material 3 theming, design tokens
        │   │   │   └── navigation/     # Jetpack Navigation Compose graph
        │   │   ├── data/
        │   │   │   ├── api/            # Retrofit + WebSocket client; _generated/ types from shared/contracts
        │   │   │   └── realtime/       # WebSocket subscription manager (live worker state, meetings)
        │   │   └── store/             # state management (ViewModel + StateFlow; org state, realtime feed)
        │   └── res/
        │       ├── values/            # strings, colors, Material 3 themes
        │       └── drawable/          # icons, illustrations
        ├── test/                     # unit tests (JUnit)
        └── androidTest/              # instrumentation tests (Compose UI tests)
```

The mobile app is **not a chat app** — it is a living organization dashboard
(see `22-mobile-ux.md` and `23-ui-ux-flow.md`). It subscribes to the realtime
worker-state and meeting streams from `api-gateway` and renders the org as
it thinks. Generated Kotlin types in
`app/src/main/java/com/veliora/data/api/_generated/` are produced from
`backend/shared/contracts/` so the client and server never drift.

---

## 6. `migrations/` — Database Migrations

```
migrations/
├── postgres/                     # relational store: orgs, workers, tasks, trust, votes
│   ├── 001_init.sql
│   ├── 002_workers.sql
│   └── ...
├── neo4j/                        # graph store: relationships, knowledge graph (see 14-knowledge-graph.md)
│   ├── 001_constraints.cypher
│   └── ...
└── redis/                        # ephemeral: worker state cache, meeting turn buffers
    └── seed.lua
```

Each datastore has its own migration directory and tooling, invoked by
`make migrate`. Migrations are numbered, forward-only, and reviewed in CI
(`migrate-check` job applies them against a throwaway DB to catch drift). See
`13-database-design.md` for the schema each datastore holds.

---

## 7. `tests/` — Cross-Cutting Tests

```
tests/
├── unit/                         # (mostly lives inside each engine/service; cross-cutting only)
├── integration/                  # multi-service: org create → investigate → report
├── e2e/                          # full stack incl. mobile client against a real backend
└── fixtures/                     # org templates, prompt snapshots, golden investigation outputs
```

Engine-internal unit tests live in `backend/engines/<x>/tests/` so they run
with that engine alone. `tests/` holds only tests that **span** components —
e.g. an end-to-end investigation from org creation through board report. The
MVP test path is defined in `28-mvp-hackathon.md`.

---

## 8. `configs/` — Environment Configuration

```
configs/
├── base.yaml                    # defaults shared by all envs
├── dev/                         # local dev overrides
├── staging/
├── prod/
└── seeds/                        # seed data: sample orgs, demo investigations
```

Configs are layered: `base.yaml` + `<env>/<service>.yaml`. Secrets are
injected via environment variables / a secrets manager — **never** committed.
`seeds/` lets a fresh dev environment boot with a working Startup Society so
the mobile dashboard is non-empty on first run.

---

## 9. `scripts/` and `infra/`

```
scripts/
├── bootstrap.sh                  # install deps, generate contracts, run migrations
├── gen_contracts.py             # shared/contracts → py + kotlin bindings
└── seed_demo_org.py             # create a demo Startup Society for local dev

infra/
├── docker/                      # service Dockerfiles
├── k8s/                         # manifests for each service + engine-as-service
└── terraform/                   # cloud resources (DBs, broker, object store)
```

---

## 10. How Engine Independence Works

The independence guarantee rests on three rules, enforced by CI:

1. **No cross-engine imports.** An engine may import only from
   `backend/shared/*` and its own package. A lint rule
   (`scripts/check_engine_boundaries.py`) fails the build if, say,
   `trust-engine` imports `debate-engine`.
2. **All cross-engine interaction is via events.** Engines publish and
   subscribe to events defined in `shared/contracts/events/`. Adding a new
   interaction means adding an event type, not a function call. See
   `16-event-system.md` for the catalog and naming conventions.
3. **Engines declare their events in their own contract file.**
   `engines/<x>/src/<x>_engine/contracts.py` lists the events it produces and
   consumes; the CI check diffs this against `shared/contracts/` to catch
   drift.

Result: a team can own the Trust Engine end-to-end — its schema, its tests,
its events — and ship on its own cadence. The diagram below shows the allowed
dependency directions:

```
                       ┌───────────────────────────────────┐
                       │          backend/shared           │
                       │   contracts  models  llm  events   │
                       └────────────────┬──────────────────┘
                                        │ (everyone may depend on shared)
          ┌─────────────┬───────────────┼───────────────┬──────────────┐
          ▼             ▼               ▼               ▼              ▼
     ┌─────────┐  ┌─────────┐    ┌──────────┐    ┌──────────┐   ┌──────────┐
     │ memory  │  │  trust  │    │  debate  │    │   dna    │   │   ...    │
     │ engine  │  │ engine  │    │  engine  │    │  engine  │   │ engines  │
     └─────────┘  └─────────┘    └──────────┘    └──────────┘   └──────────┘
          │             │               │               │              │
          └─────────────┴───────────────┴───────────────┴──────────────┘
                                        │
                                        ▼  (engines never import each other;
                                           they only exchange events)
                              ┌────────────────────┐
                              │      event-bus      │
                              └────────────────────┘
                                        ▲
                                        │ (services host engines + emit/consume events)
          ┌─────────────┬───────────────┼───────────────┬──────────────┐
          ▼             ▼               ▼               ▼              ▼
     api-gateway   org-manager    worker-runtime     scheduler      (mobile client
                                                   (loads engines)   via gateway)
```

---

## 11. Where Each Component Lives (Quick Map)

| Component                      | Location                                          | Doc |
|--------------------------------|---------------------------------------------------|-----|
| Public API surface             | `backend/services/api-gateway/`                   | `24-api-design.md` |
| Org creation/instantiation     | `backend/services/org-manager/` + `templates/organizations/` | `05-agent-architecture.md` |
| Worker pool & turn execution   | `backend/services/worker-runtime/`                | `05-agent-architecture.md` |
| Task scheduling                | `backend/services/scheduler/` + `engines/planner-engine/` | `18-planner-design.md` |
| Event broker                   | `backend/services/event-bus/`                     | `16-event-system.md` |
| Institutional memory           | `engines/memory-engine/`                          | `06-memory-system.md` |
| Reputation/trust               | `engines/trust-engine/`                           | `08-trust-engine.md` |
| Constitution/voting            | `engines/constitution-engine/`                     | `09-constitution-engine.md` |
| Debate/evidence challenge      | `engines/debate-engine/`                          | `10-debate-engine.md` |
| Internal messaging             | `engines/communication-engine/`                   | `11-communication-engine.md` |
| External comms (Caspian)       | `engines/caspian-engine/`                         | `12-caspian-integration.md` |
| Knowledge graph/relationships  | `engines/knowledge-graph-engine/`                 | `14-knowledge-graph.md` |
| Investigation orchestration    | `engines/investigation-engine/`                   | `19-investigation-engine.md` |
| Meetings                       | `engines/meeting-engine/`                         | `20-meeting-engine.md` |
| Org DNA                        | `engines/dna-engine/` + `templates/organizations/*/dna.yaml` | `21-organizational-dna.md` |
| Prompt templates               | `templates/prompts/`                             | `17-prompt-architecture.md` |
| Mobile dashboard               | `mobile/`                                         | `22-mobile-ux.md` |
| DB schemas                     | `migrations/`                                    | `13-database-design.md` |
| Cross-component types          | `backend/shared/contracts/`                       | `24-api-design.md`, `16-event-system.md` |

---

## 12. Cross-References

| Topic                                | See                                    |
|--------------------------------------|----------------------------------------|
| System topology & services           | `04-technical-architecture.md`         |
| Worker/department model             | `05-agent-architecture.md`             |
| Memory system & scopes              | `06-memory-system.md`                  |
| Evolution engine                    | `07-evolution-engine.md`               |
| Trust engine                         | `08-trust-engine.md`                   |
| Constitution engine                  | `09-constitution-engine.md`            |
| Debate engine                       | `10-debate-engine.md`                  |
| Communication engine                 | `11-communication-engine.md`           |
| Caspian integration                 | `12-caspian-integration.md`            |
| Database design                      | `13-database-design.md`                |
| Knowledge graph                      | `14-knowledge-graph.md`                |
| Event system & catalog              | `16-event-system.md`                   |
| Prompt architecture                  | `17-prompt-architecture.md`            |
| Planner design                       | `18-planner-design.md`                 |
| Investigation engine                 | `19-investigation-engine.md`           |
| Meeting engine                       | `20-meeting-engine.md`                 |
| Organizational DNA                   | `21-organizational-dna.md`             |
| Mobile UX                            | `22-mobile-ux.md`                      |
| UI/UX flow                           | `23-ui-ux-flow.md`                     |
| API design                           | `24-api-design.md`                     |
| State machine                        | `25-state-machine.md`                  |
| Security                             | `26-security.md`                       |
| Scaling strategy                     | `27-scaling-strategy.md`               |
| MVP (hackathon)                      | `28-mvp-hackathon.md`                  |
