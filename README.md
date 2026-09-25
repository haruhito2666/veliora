# Veliora

**The first self-evolving autonomous organization — as an open-source, local-first research project.**

Veliora turns a goal into an investigation run by an organization of specialized workers. Departments gather evidence, challenge claims, deliberate, vote, and expose the resulting state and decisions through a native Android dashboard.

> **Alpha / trusted-network-only:** the current release is authless. Anyone who can reach the gateway can read and modify every organization in that instance. Run it on localhost or a private network you control. It is not ready for public or multi-tenant deployment.

## What is implemented

- Authless local REST API with a `/health` endpoint.
- Organization creation from the bundled Startup Society template.
- Six departments and eight materialized workers per startup organization.
- Constitution, DNA, trust, memory, knowledge-graph, communications, meeting, debate, evolution, investigation, evidence, and report service libraries.
- NATS JetStream event envelopes, per-organization event streams, and WebSocket fan-out.
- Android Compose client with organization selection, live dashboard, investigations, real evidence, reports, communications, constitution, evolution, worker/department inspection, and instance information.
- Python worker package with prompt composition, OpenAI adapter, scripted test adapter, and Tavily/DuckDuckGo search.

The investigation orchestrator currently creates a deterministic fallback plan and advances the investigation to `researching`. Full worker-task dispatch and autonomous progression remain future work.

The Android alpha contains no mock/design package and no placeholder-only screens. Unwired pages and local-only actions were removed; empty UI states are used only when a real API has no records.

## Quick start

### Requirements

- Go 1.26+
- Python 3.11+
- [uv](https://docs.astral.sh/uv/)
- PostgreSQL 16+ with pgvector
- NATS JetStream
- Redis (reserved for future infrastructure; the current control plane does not depend on it)
- Android Studio / JDK 17 for the Android client

### Native development

```bash
cp .env.example .env
set -a; source .env; set +a

# macOS/Homebrew helper; use Docker Compose for portable infrastructure.
make up
make migrate
bash scripts/run_backend.sh
```

The API gateway listens on `http://localhost:8080` by default.

```bash
make test
bash scripts/e2e_golden_path.sh
```

Build the Android app from `veliora/`:

```bash
cd veliora
./gradlew :app:assembleDebug
```

The emulator uses `http://10.0.2.2:8080` by default. Override it with:

```bash
./gradlew :app:assembleDebug -PVELIORA_API_BASE=http://10.0.2.2:8080
```

### Docker infrastructure

The checked-in Compose file starts PostgreSQL, NATS JetStream, and Redis. Run the Go services and migration command on the host after exporting `.env`:

```bash
docker compose up -d
set -a; source .env; set +a
make migrate
bash scripts/run_backend.sh
```

Do not publish the API gateway to the internet in this mode.

## Repository map

| Path | Purpose |
|------|---------|
| `backend/go/` | Go control plane, gateway, event bus, and engine services |
| `backend/workers/` | Python worker runtime and task handlers |
| `veliora/` | Native Android Compose application |
| `templates/organizations/` | Organization templates |
| `docs/` | Product, architecture, API, security, and roadmap documents |
| `scripts/` | Local service, backend, and E2E helpers |

## Configuration

Copy `.env.example` and set only the integrations you need. LLM and external-channel credentials are optional for the deterministic local path. Without Caspian credentials, outbound messages remain honestly queued as `unsent`.

## Contributing

Read [CONTRIBUTING.md](CONTRIBUTING.md) before opening a pull request. Small, focused changes with tests and documentation are preferred.

## Security

Read [SECURITY.md](SECURITY.md) before exposing any deployment. The current authless design is intentional for a trusted single-user instance, not a recommendation for public hosting.

## License

MIT — see [LICENSE](LICENSE). Third-party dependencies and bundled assets are listed in [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
