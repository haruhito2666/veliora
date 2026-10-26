# 34 — Sofuu Native Runtime Migration: Replacing 100% of the Backend Stack

> **Document Type:** Master Systems Migration Plan & In-Process Architecture Blueprint  
> **Status:** APPROVED ENGINEERING SPECIFICATION — October 2026  
> **Target Audience:** Systems Architects, Core Engine Developers, Mobile Engineers, AI Platform Leads  
> **Repository:** Veliora / SocietyOS  
> **Target Substrate:** `sofuu v0.2.0-beta` (Rust `crates/sofuu-core`, QuickJS/libuv substrate, CMA v2 QTSQ memory, in-process inference)  
> **Core Objective:** Collapse 15 Go microservices, NATS JetStream, PostgreSQL with pgvector, Redis, and Python worker daemons into a **single, zero-dependency standalone Sofuu runtime (< 5 MB binary)** that runs identically on local workstations, cloud containers, and directly on Android via `libsofuu`.

---

## 1. Executive Summary: The Architecture Dilemma

### 1.1 The Incumbent Stack Problem
Veliora (SocietyOS) is designed as a self-evolving autonomous organization. In its alpha implementation, achieving this required the typical fragmented multi-daemon infrastructure stack:
- **15 Go Microservices:** `api-gateway`, `org-manager`, `investigation-engine`, `debate-engine`, `evidence-engine`, `meeting-engine`, `memory-system`, `trust-engine`, `constitution-engine`, `dna-engine`, `knowledge-graph`, `report-engine`, `evolution-engine`, `communication-engine`, `caspian-bridge`.
- **NATS JetStream:** External messaging broker with durable streams, subjects, and consumer groups.
- **PostgreSQL 16 + pgvector:** Dedicated database server for relational schema, JSON documents, and vector embeddings.
- **Redis 7:** External memory store for caching and live pub/sub state.
- **Python 3.11 Worker Runtime:** Virtual environment with `uv`, prompt composition, and LLM bridges.
- **Docker Compose:** Required orchestrator to bring up 4 separate server containers before running tests.

```
┌────────────────────────────────────────────────────────────────────────┐
│                   THE FRAGMENTED INCUMBENT STACK                       │
├────────────────────────────────────────────────────────────────────────┤
│ Total Running Processes:   18+ separate processes & daemons            │
│ Idle RAM Consumption:      2.8 GB – 4.2 GB RSS                         │
│ Cold Startup Time:         15 – 35 seconds                             │
│ Operational Complexity:    High (Docker, port clashes, schema migs)   │
│ Client Constraint:         Mobile cannot run backend; localhost only   │
└────────────────────────────────────────────────────────────────────────┘
```

### 1.2 The Sofuu In-Process Revolution
By migrating Veliora's backend 100% to **Sofuu**, all 18 background processes collapse into **one single process running on the host**:

```
┌────────────────────────────────────────────────────────────────────────┐
│                    THE UNIFIED SOFUU IN-PROCESS STACK                  │
├────────────────────────────────────────────────────────────────────────┤
│ Total Running Processes:   1 single standalone process (< 5 MB binary) │
│ Idle RAM Consumption:      < 65 MB RSS (45x reduction)                 │
│ Cold Startup Time:         < 4 milliseconds (3,000x faster)            │
│ Operational Complexity:    Zero (No Docker, no Postgres, no daemons)   │
│ Client Constraint:         Runs on Mac/Linux OR 100% offline inside   │
│                            the Android app via `libsofuu.so`           │
└────────────────────────────────────────────────────────────────────────┘
```

---

## 2. 1-to-1 Substrate Mapping Matrix

Every piece of the incumbent infrastructure maps directly into a built-in Sofuu native primitive:

```
┌───────────────────────────────────┬────────────────────────────────────┐
│ Incumbent Component (Go/Py/DB)    │ Sofuu In-Process Replacement       │
├───────────────────────────────────┼────────────────────────────────────┤
│ 1. Mobile API Gateway             │ `sofuu.http.createServer()`        │
│    (Go REST + WebSocket fan-out)  │ Fast libuv HTTP/1.1 + WebSocket    │
├───────────────────────────────────┼────────────────────────────────────┤
│ 2. PostgreSQL 16 + pgvector       │ `sofuu.memory` (CMA v2 Brain)      │
│    (Relational + vector search)   │ Encrypted `.qtsq` vectors + KV DB  │
├───────────────────────────────────┼────────────────────────────────────┤
│ 3. Redis 7                        │ `sofuu.storage`                    │
│    (Ephemeral state & cache)      │ Zero-IPC in-process Key-Value DB   │
├───────────────────────────────────┼────────────────────────────────────┤
│ 4. NATS JetStream Message Bus     │ `sofuu.events` + Async Queue       │
│    (Worker task streams & pub/sub)│ Microsecond in-process event queue │
├───────────────────────────────────┼────────────────────────────────────┤
│ 5. Python Worker Daemons          │ `sofuu.agent` + 5-Shield Plugins   │
│    (Prompt composition & tools)   │ Isolated JS agents with micro-Zod  │
├───────────────────────────────────┼────────────────────────────────────┤
│ 6. External Cloud LLM (OpenAI)    │ `sofuu.ai` In-Process Inference    │
│    (Network latency, cloud bills) │ Native Metal / ARM NEON execution  │
└───────────────────────────────────┴────────────────────────────────────┘
```

---

## 3. High-Level In-Process Architecture

In the Sofuu native architecture, Veliora runs as a clean, modular JavaScript/TypeScript application hosted by the Sofuu engine:

```
+------------------------------------------------------------------------+
|                          VELIORA CLIENT APP                            |
|             (Native Android Jetpack Compose or Web Dashboard)          |
+-----------------------------------+------------------------------------+
                                    | HTTP / WebSockets (:8080)
                                    | (or direct in-memory JNI on phone)
                                    v
+------------------------------------------------------------------------+
|                     SOFUU RUNTIME PROCESS (< 5 MB)                     |
|                                                                        |
|  [HTTP & REALTIME GATEWAY]                                             |
|  • sofuu.http.createServer (REST endpoints: /api/v1/organizations...)   |
|  • WebSocket connection registry & live event fan-out                  |
|                                                                        |
|  [IN-PROCESS MESSAGE BUS & EVENT SCHEDULER]                            |
|  • Durable task queue with timeout sweepers                            |
|  • Organization event stream with idempotent state ledger              |
|                                                                        |
|  [VELIORA CORE AGENT ENGINES]                                          |
|  • Investigation Orchestrator (7-phase state machine)                  |
|  • Department Workers (Research, Eng, Finance, Mktg, Legal, Exec)      |
|  • 2-Round Society-of-Thought Debate Engine                            |
|  • Trust-Weighted Voting & Confidence Synthesizer                      |
|                                                                        |
|  [CONTINUOUS COGNITIVE MEMORY (CMA v2 / QTSQ)]                         |
|  • Encrypted on-device .qtsq container                                 |
|  • Episodic recall, Ebbinghaus decay & associative semantic vectors    |
|                                                                        |
|  [IN-PROCESS INFERENCE & TOOL SANDBOX]                                 |
|  • sofuu.ai (Llama 3.2 / Qwen 2.5 on Apple Metal or ARM NEON)          |
|  • 5-Shield Safety Boundary (SSRF guard, jailed fs, circuit breaker)   |
+------------------------------------------------------------------------+
```

---

## 4. Phase-by-Phase Implementation Plan

### Phase 1: In-Process HTTP Gateway & Storage Substrate
**Objective:** Replace Go `api-gateway`, PostgreSQL, and Redis with native Sofuu services.

1. **Native HTTP & WebSocket Server (`src/server/gateway.js`):**
   - Bind `sofuu.http.createServer` to port `8080`.
   - Implement route handlers matching the exact contracts in `docs/24-api-design.md`:
     - `GET /health` -> `{ status: "ok", timestamp: ... }`
     - `POST /api/v1/organizations` -> Create organization
     - `GET /api/v1/organizations/:id` -> Org details & departments
     - `POST /api/v1/organizations/:id/investigations` -> Launch goal investigation
     - `GET /api/v1/organizations/:id/investigations/:inv_id` -> Investigation status
     - `GET /api/v1/organizations/:id/evidence` -> Evidence records
     - `GET /api/v1/organizations/:id/reports` -> Final board reports
     - `GET /api/v1/organizations/:id/events/ws` -> WebSocket upgrade & event stream
2. **In-Process Persistence (`src/storage/db.js`):**
   - Use `sofuu.storage` for relational entities (Orgs, Workers, Investigations, Debates, Votes).
   - Use `sofuu.memory` for vector embeddings and semantic search on evidence.
   - Zero database drivers, zero connection pooling overhead, zero TCP serialization delay.

### Phase 2: In-Process Event Bus & Task Queue
**Objective:** Replace NATS JetStream and background sweeper daemons.

1. **Lightweight Durable Event Bus (`src/events/bus.js`):**
   - In-memory event stream with disk write-ahead log (WAL) persistence.
   - Pub/sub topics matching Veliora event conventions (`org.<id>.investigation.started`, `org.<id>.evidence.collected`, etc.).
   - Broadcast events directly to subscribed WebSockets for real-time mobile UI updates.
2. **Task Queue with Watchdog Sweeper (`src/queue/task_queue.js`):**
   - Durable worker task queue replacing NATS JetStream consumers.
   - Automated deadline sweeper using `sofuu.timer` (`uv_timer_t`) to detect and retry timed-out worker tasks without external cron jobs.

### Phase 3: Multi-Agent Investigation Pipeline
**Objective:** Replace Python `backend/workers/` with native Sofuu agent loops.

1. **Department Worker Definitions (`src/agents/departments.js`):**
   - Define the 6 core departments and 8 workers using `sofuu.plugins.defineTool` and `sofuu.agent`:
     - **Research Division:** Web retrieval, fact gathering.
     - **Engineering:** Feasibility analysis, technical risks.
     - **Finance:** Unit economics, cost projections.
     - **Marketing:** Customer persona, positioning.
     - **Legal & Compliance:** Regulatory challenges, IP risks.
     - **Executive (CEO):** Synthesis, board reporting.
2. **The 7-Phase Investigation State Machine (`src/orchestration/investigation.js`):**
   - **Phase 1: Planning (`planning`):** Decompose the user goal into per-department deliverables.
   - **Phase 2: Research (`researching`):** Parallel evidence gathering using web retrieval tools protected by Shield 2 (SSRF guard).
   - **Phase 3: Peer Challenge (`analyzing`):** Cross-assign each evidence item to an outside department for rigorous critique.
   - **Phase 4: Debate (`debating`):** Multi-turn Society-of-Thought debate (Opening statements $\to$ Rebuttals).
   - **Phase 5: Synthesis (`synthesizing`):** CEO synthesizes debate findings, dissenting views, and accepted claims.
   - **Phase 6: Trust-Weighted Voting (`vote` & `confidence_chk`):** Workers cast trust-weighted ballots; evaluate confidence threshold ($\ge 0.85$ to report, $< 0.85$ triggers research loop).
   - **Phase 7: Board Report (`reporting` $\to$ `completed`):** Emit comprehensive executive report with confidence score, evidence index, and strategic recommendation.

### Phase 4: Inference Engine Wiring
**Objective:** Provide zero-cost, local-first inference with optional cloud fallback.

1. **In-Process Inference Engine:**
   - Run local models (`Llama-3.2-1B-Instruct` or `Qwen2.5-1.5B`) directly inside the Sofuu process via `sofuu.ai.chat()`.
   - 100% private, zero API keys required, zero cloud compute bills.
2. **Hybrid Cloud Fallback:**
   - Support OpenAI / Anthropic / Groq endpoint configuration in `veliora.json` for developers who want frontier cloud models for synthesis.

### Phase 5: Android Mobile Embed (`libsofuu.so`)
**Objective:** Run the full organization on Android devices.

1. **Direct Android Compose Integration:**
   - Link `libsofuu.so` into `veliora/app/src/main/cpp`.
   - Provide a JNI bridge so the Android app can run the backend in-process on-device.
   - When running on-device, `VelioraApi` connects to `http://localhost:8080` (or directly via memory channels) without requiring any external server.

---

## 5. Directory Structure of the Migrated Backend

The new backend lives in `backend/sofuu/` (or replaces `backend/` entirely):

```
societyos/
├── backend/
│   └── sofuu/                    # The entire backend in clean modern JS/TS
│       ├── veliora.js            # Main entry point (starts server & worker loop)
│       ├── veliora.json          # Configuration, ports, model paths, org settings
│       ├── server/
│       │   ├── gateway.js        # REST router & WebSocket server
│       │   └── routes/           # Org, investigation, evidence, report routes
│       ├── orchestration/
│       │   ├── investigation.js  # 7-phase state machine
│       │   └── debate.js         # Society-of-Thought 2-round debate engine
│       ├── agents/
│       │   ├── prompt_context.js # System prompts, constitutions, DNA tokens
│       │   ├── departments.js    # 6 departments & 8 specialized workers
│       │   └── tools/            # Web search, document parser (5-Shield isolated)
│       ├── events/
│       │   └── bus.js            # In-process pub/sub event bus
│       └── storage/
│           ├── db.js             # Relational & entity store (sofuu.storage)
│           └── memory.js         # Cognitive vector memory (sofuu.memory / CMA v2)
├── veliora/                      # Android Jetpack Compose native mobile client
│   └── app/                      # Talk to :8080 or runs in-process via libsofuu.so
├── docs/                         # Specifications & architecture docs
└── Makefile                      # Simple dev commands (`make run`, `make test`)
```

---

## 6. Verification & E2E Golden Path Parity

The new Sofuu backend must satisfy the exact contract verified in `scripts/e2e_golden_path.sh`:

1. **Cold Start:** `sofuu run backend/sofuu/veliora.js` boots in $< 50\text{ ms}$ (including model loader).
2. **Health Check:** `curl http://localhost:8080/health` returns HTTP 200 within 1 ms.
3. **Investigation Run:** Submitting `{"goal": "Investigate smart glasses market"}` executes:
   - 5 department research tasks $\to$ 5 evidence items persisted.
   - 5 peer challenges assigned outside submitting departments.
   - 2-round debate with 10 persisted turns.
   - CEO synthesis + 5 trust-weighted ballots.
   - Final board report generated with confidence $> 0.85$ and status `completed`.
4. **WebSocket Stream:** Live event envelopes broadcasted to the Android Compose client without dropped packets.

---

## 7. Migration Benefits & Strategic Impact

```
┌─────────────────────────┬──────────────────────┬───────────────────────┐
│ Metric                  │ Incumbent (Go/Python)│ Sofuu Native          │
├─────────────────────────┼──────────────────────┼───────────────────────┤
│ Background Daemons      │ 4 (Postgres, NATS,   │ 0 (Single binary)     │
│                         │    Redis, Docker)    │                       │
├─────────────────────────┼──────────────────────┼───────────────────────┤
│ Backend Services        │ 15 Go + 1 Python     │ 1 In-Process Runtime  │
├─────────────────────────┼──────────────────────┼───────────────────────┤
│ Setup Time for Devs     │ 25 - 45 minutes      │ 10 seconds            │
│                         │ (Docker, UV, Go, DB) │ (`sofuu run ...`)     │
├─────────────────────────┼──────────────────────┼───────────────────────┤
│ Memory Footprint        │ ~3.2 GB RSS          │ ~65 MB RSS            │
├─────────────────────────┼──────────────────────┼───────────────────────┤
│ Offline Mobile Support  │ Impossible           │ 100% Native on-device │
│                         │ (Requires server)    │ via `libsofuu.so`     │
├─────────────────────────┼──────────────────────┼───────────────────────┤
│ Cloud Token Costs       │ High (OpenAI API)    │ $0 (Local Metal/NEON) │
└─────────────────────────┴──────────────────────┴───────────────────────┘
```

By completing this migration, **Veliora transforms from a complex, hard-to-deploy research prototype into the world's most accessible, portable autonomous AI organization**—showcasing the full power of the Sofuu runtime to developers, enterprises, and potential acquirers.
