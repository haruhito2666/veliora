# Veliora

**The first self-evolving autonomous organization — an open-source, local-first research project.**

Veliora turns a goal into an investigation run by an organization of specialized departments. Departments gather evidence, challenge claims, debate, vote, and expose the resulting state and decisions through a native Android dashboard.

> **Alpha / trusted-network-only:** the gateway is authless. Anyone who can reach it can read and modify every organization in that instance. Run it on localhost or a private network you control. It is not ready for public or multi-tenant deployment.

## Backend: Sofuu

Veliora's backend is a single JavaScript/TypeScript application hosted by the Sofuu runtime (`sofuu 0.2.0-beta` or newer). Sofuu provides the HTTP server, agent loops, in-process inference, encrypted memory, and structured storage in one process, so the target backend needs no Docker, PostgreSQL, NATS, Redis, or Python worker daemon.

| Concern | Sofuu primitive |
|---|---|
| REST API and live events | `sofuu.http.createServer` (HTTP and streaming responses) |
| Department workers | `sofuu.agent` (`define`, `run`, `runMany`) |
| Durable organization, investigation, task, evidence, vote, and report records | `sofuu.storage` strict-schema tables in `.qtsq` segments |
| Institutional memory and semantic recall | `sofuu.memory` (CMA v2 / QTSQ) |
| Local or cloud inference | `sofuu.ai` (`streamLocal` / `inferLocal` for local models, `stream` / `complete` for providers) |
| Web research | `sofuu.web` |

The Sofuu migration is in progress. The target entry point is `backend/sofuu/veliora.js`; the Go control plane and Python worker runtime under `backend/go/` and `backend/workers/` are retained only as the parity reference until the Sofuu backend replaces them.

See [docs/34-sofuu-native-migration-plan.md](docs/34-sofuu-native-migration-plan.md) for the substrate mapping, phase plan, and target directory layout, and [docs/33-build-status.md](docs/33-build-status.md) for what is implemented versus still pending.

## Current status

- The legacy Go/Python alpha remains in the tree as the parity reference. It is not the supported deployment path.
- The Sofuu backend is the active implementation target. No Sofuu entry point is committed yet; Phase 1 is the in-process HTTP gateway and storage substrate.
- The Android alpha contains no mock/design package and no placeholder-only screens. Screens use real API data with loading, error, and empty states.

## Quick start

### Requirements

- Sofuu `0.2.0-beta` or newer (`sofuu doctor` should report healthy)
- A local GGUF or SafeTensors model for offline inference, or a provider API key for cloud models
- Android Studio / JDK 17 for the Android client

### Backend (target)

```bash
sofuu doctor
sofuu run backend/sofuu/veliora.js
```

The backend will serve the REST API on `http://localhost:8080` once Phase 1 of the migration lands. Until then, the legacy reference stack is still runnable; its commands are in `docs/33-build-status.md`.

### Android

Build the Android app from `veliora/`:

```bash
cd veliora
./gradlew :app:assembleDebug
```

The emulator uses `http://10.0.2.2:8080` by default. Override it with:

```bash
./gradlew :app:assembleDebug -PVELIORA_API_BASE=http://10.0.2.2:8080
```

## Repository map

| Path | Purpose |
|------|---------|
| `backend/sofuu/` | Target Sofuu backend (JS/TS; migration Phase 1) |
| `backend/go/`, `backend/workers/` | Legacy Go/Python implementation retained for parity |
| `veliora/` | Native Android Compose application |
| `templates/organizations/` | Organization templates |
| `docs/` | Product, architecture, API, and migration documents |
| `scripts/` | Legacy local service and E2E helpers |

## Configuration

The Sofuu backend will read its configuration from `backend/sofuu/veliora.json` (port, model path, provider) plus Sofuu's own `~/.sofuu/config.json`. Local inference is fully offline after the model file is provisioned; cloud inference requires a provider key.

## License

MIT — see [LICENSE](LICENSE). Third-party dependencies and bundled assets are listed in [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
