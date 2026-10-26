# Veliora

**The first self-evolving autonomous organization — an open-source, local-first research project.**

Veliora turns a goal into an investigation run by an organization of specialized departments. Departments gather evidence, challenge claims, debate, vote, and expose the resulting state and decisions through a native Android dashboard.

> **Alpha / trusted-network-only:** the gateway is authless. Anyone who can reach it can read and modify every organization in that instance. Run it on localhost or a private network you control. It is not ready for public or multi-tenant deployment.

## Backend: Sofuu

Veliora's backend is a single JavaScript/TypeScript application hosted by the Sofuu runtime (`sofuu 0.2.0-beta` or newer). The legacy Go/Python stack has been removed; `backend/sofuu/` is the only backend. Sofuu supplies the HTTP server, agent loops, in-process inference, encrypted memory, and structured storage in one process.

| Concern | Sofuu primitive |
|---|---|
| REST API and live events | `sofuu.http.createServer` (HTTP and Server-Sent Events) |
| Department workers | `sofuu.agent` (`define`, `run`, `runMany`) |
| Durable organization, investigation, task, evidence, vote, and report records | `sofuu.storage` strict-schema tables in `.qtsq` segments |
| Institutional memory and semantic recall | `sofuu.memory` (CMA v2 / QTSQ) |
| Local or cloud inference | `sofuu.ai` (`streamLocal` / `inferLocal` for local models, `stream` / `complete` for providers) |
| Web research | `sofuu.web` |

The backend uses only the public Sofuu SDK surface; it does not import Sofuu's internal source files. See [backend/sofuu/README.md](backend/sofuu/README.md) for the implementation notes.

## Current status

- The Sofuu backend runs the full investigation pipeline end to end in deterministic `scripted` mode: research, cross-department challenge, two-round debate, synthesis, trust-weighted vote, confidence loop, and board report.
- Real `sofuu.ai` and `sofuu.agent` modes are wired behind the same adapter but have not yet been validated against a provider or local model.
- Live events use Server-Sent Events because Sofuu's HTTP server has no WebSocket upgrade. The Android client still uses WebSocket and will be switched next.
- The Android alpha contains no mock/design package and no placeholder-only screens. Screens use real API data with loading, error, and empty states.

## Quick start

### Requirements

- Sofuu `0.2.0-beta` or newer, built with `sofuu.storage` support (`sofuu doctor` should report healthy)
- A local GGUF or SafeTensors model for offline inference, or a provider API key for cloud models
- Android Studio / JDK 17 for the Android client

### Backend

```bash
sofuu doctor
sofuu run backend/sofuu/veliora.js
```

The API listens on `http://127.0.0.1:8080` by default. Run the end-to-end smoke test with:

```bash
SOFUU_BIN=/path/to/sofuu bash backend/sofuu/test/smoke.sh
```

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
| `backend/sofuu/` | The Sofuu backend (JS/TS) |
| `veliora/` | Native Android Compose application |

Design documents and organization templates are kept in local `docs/` and
`templates/` folders and are not part of the published repository.

## Configuration

The backend reads `backend/sofuu/veliora.json` and supports environment overrides. Copy `.env.example` for the variable names. Local inference is fully offline after the model file is provisioned; cloud inference requires a provider key.

## License

MIT — see [LICENSE](LICENSE). Third-party dependencies and bundled assets are listed in [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
