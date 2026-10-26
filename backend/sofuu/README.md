# Veliora Sofuu Backend

This is the in-process backend for Veliora, written against the **Sofuu SDK**.
It uses only the public runtime surface (`sofuu.http`, `sofuu.storage`,
`sofuu.ai`, `sofuu.agent`) and does not import Sofuu's internal source files.

The current pass is intentionally small and runnable. The legacy Go/Python
backend remains under `backend/go/` and `backend/workers/` as a parity
reference until this implementation is complete.

## Run

```bash
sofuu doctor
sofuu run backend/sofuu/veliora.js
```

The runtime must expose `sofuu.storage`; the source build in
`ai-native-js-runtime` does, while some older installed binaries do not. Verify
with:

```bash
sofuu eval 'console.log(typeof sofuu.storage, typeof sofuu.storage?.open)'
```

The API listens on `http://127.0.0.1:8080` by default. Configuration lives in
`veliora.json`; environment variables override it:

| Variable | Purpose |
|---|---|
| `VELIORA_PORT` | HTTP port |
| `VELIORA_HOST` | bind address |
| `VELIORA_STORE` | persistent store directory |
| `VELIORA_MODE` | `scripted`, `sofuu`, or `agent` |
| `VELIORA_PROVIDER` / `VELIORA_MODEL` | provider + model for `sofuu` mode |
| `VELIORA_CONFIDENCE_THRESHOLD` | confidence threshold (default `0.72`) |
| `VELIORA_MAX_CYCLES` | research loop cap (default `3`) |
| `VELIORA_STEP_DELAY_MS` | pacing delay between worker steps |
| `VELIORA_FALLBACK_SCRIPTED` | fall back to fixtures if an SDK model call fails |

`scripted` mode is deterministic and needs no API key or local model. `sofuu`
mode calls `sofuu.ai.complete`; `agent` mode defines the six department agents
with `sofuu.agent.define` and runs them with `sofuu.agent.run`.

## Storage

Documents are stored through `sofuu.storage` using a uniform append-only
schema:

```text
key         stable document key
ref         owning organization / investigation
payload     JSON document
created_at  insertion time
```

Mutable records append a new row with the same key; reads take the newest row.
No SQL, Postgres, or external daemon is involved.

## API surface

The first pass mirrors the legacy REST contract:

- `GET /health`
- `GET/POST /v1/organizations`
- `GET /v1/organizations/{org}/status`
- `GET /v1/organizations/{org}/constitution`
- `GET /v1/organizations/{org}/workers`
- `GET/POST /v1/organizations/{org}/investigations`
- `GET /v1/organizations/{org}/investigations/{inv}`
- `POST /v1/organizations/{org}/investigations/{inv}/cancel`
- `GET .../evidence`, `.../challenges`, `.../debate`, `.../votes`, `.../report`
- `GET /v1/organizations/{org}/meetings`
- `GET /v1/organizations/{org}/events` (SSE)
- `GET /v1/organizations/{org}/investigations/{inv}/events` (SSE)

`/api/v1/...` is accepted as an alias. Sofuu's HTTP server does not expose a
WebSocket upgrade, so live events use Server-Sent Events; the old `/ws/...`
paths return an explicit `426`.

## Test

```bash
bash backend/sofuu/test/smoke.sh
```

Use `SOFUU_BIN=/path/to/sofuu` to test a specific runtime build.
