# Veliora Sofuu Gateway

The Veliora gateway, written against the **Sofuu SDK**. It uses only the
public runtime surface (`sofuu.storage`, `sofuu.ai`, `sofuu.agent`,
`sofuu.crypto`, and `sofuu.http` for the optional host server) and does not
import Sofuu's internal source files.

There are two hosts for the same gateway code:

- **Android (product):** `embedded.js` is bundled into the APK by
  `scripts/build_embedded_js.sh` and executed inside
  `com.sofuu:sofuu-android:0.2.0`. Requests, event reads, and loop ticks go
  through the SDK's in-process funnel; there is no HTTP listener and no
  `sofuu` executable in the app.
- **Host (tests/reference):** `veliora.js` starts `sofuu.http.createServer`
  for the smoke tests and for a desktop client. The Android app does not use
  this path.

## Run the host reference

```bash
sofuu doctor
sofuu run backend/sofuu/veliora.js
```

The runtime must expose `sofuu.storage` **and** `sofuu.crypto`. The source
build in `ai-native-js-runtime` does (after the 2026-10-26 crypto change);
some older installed binaries do not. Verify with:

```bash
sofuu eval 'console.log(typeof sofuu.storage, typeof sofuu.crypto?.verifyJWT)'
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
| `VELIORA_BASE_URL` | optional provider or local model endpoint |
| `VELIORA_CONFIDENCE_THRESHOLD` | confidence threshold (default `0.72`) |
| `VELIORA_MAX_CYCLES` | research loop cap (default `3`) |
| `VELIORA_STEP_DELAY_MS` | pacing delay between worker steps |
| `VELIORA_FALLBACK_SCRIPTED` | fall back to fixtures if an SDK model call fails |
| `VELIORA_AUTH_MODE` | `clerk` (reference verifier) or `dev` (local tests only) |
| `VELIORA_APP_ORIGINS` | comma-separated CORS allow-list |
| `VELIORA_DEV_USER` | identity used by `Bearer dev` in dev mode |
| `CLERK_ISSUER` | your Clerk Frontend API URL (expected `iss`) |
| `CLERK_JWKS_URL` | your Clerk JWKS endpoint |
| `CLERK_AUTHORIZED_PARTIES` | comma-separated allowed `azp` origins |
| `CLERK_AUDIENCE` | optional expected `aud` |
| `CLERK_CLOCK_SKEW_SECONDS` | expiry/not-before tolerance (default `5`) |
| `CLERK_JWKS_CACHE_MS` | JWKS cache lifetime (default one hour) |
| `CLERK_JWKS_TIMEOUT_MS` | JWKS fetch timeout (default `10000`) |

`scripted` mode is deterministic and needs no API key or local model. `sofuu`
mode calls `sofuu.ai.complete`; `agent` mode defines the six department agents
with `sofuu.agent.define` and runs them with `sofuu.agent.run`.

The repository ships no Clerk instance or maintainer domain. Set the `CLERK_*`
values for your own instance, or replace the verifier as described below.

## Authentication (reference adapter: Clerk)

The gateway is fail-closed: every `/v1` route except `/v1/health` requires a
bearer token. There is no anonymous route. The **reference adapter** verifies
Clerk session tokens, but Clerk is not a project requirement and no Clerk
instance or maintainer domain is shipped in the defaults.

```http
Authorization: Bearer <JWT>
```

The reference verifier checks the token locally against the configured public
JWKS with `sofuu.crypto.verifyJWT`. It checks the RS256 signature, `exp`,
`nbf`, `iss` (when configured), `azp` (when the claim is present), and `sub`.
No Clerk secret key is used. Native mobile tokens may omit `azp` because there
is no browser `Origin`; a missing `azp` is accepted, while a present `azp` must
be allow-listed.

- `GET /v1/me` returns the authenticated `user_id`, `session_id`, a minimal
  `account` record, and the token claims.
- The account record contains only the authenticated `sub`, `first_seen_at`,
  `last_seen_at`, coarse `app_version`, and `platform`. Email, name, avatar,
  tokens, and application content are never stored there. Updates are
  throttled to one write per user every 15 minutes.
- Clients may send `X-Veliora-App-Version` and `X-Veliora-Platform` on
  authenticated requests. The Android client sends `android` and
  `BuildConfig.VERSION_NAME` automatically.
- Organizations are owned by the authenticated `sub`. Cross-user reads and
  writes return `404`.
- SSE (`/v1/organizations/{org}/events` and investigation events) also accepts
  `?token=<jwt>` because browser `EventSource` cannot set headers. Query tokens
  are rejected on every other route, and the stream closes when the token's
  `exp` is reached.
- CORS reflects only `VELIORA_APP_ORIGINS`; unknown origins get no
  `Access-Control-Allow-Origin` header.

`VELIORA_AUTH_MODE=dev` is a deliberate test mode. It accepts
`Authorization: Bearer dev:<user_id>` and logs a warning at startup. It is not
an authless fallback: a missing token is still `401`. Do not ship a product
client against dev mode.

### Using your own Clerk instance

1. In your Clerk dashboard, copy the Frontend API URL (or custom domain) and
   confirm `https://<frontend-api>/.well-known/jwks.json` is reachable.
2. Set `CLERK_ISSUER` to that Frontend API URL, `CLERK_JWKS_URL` to the JWKS
   endpoint, and `CLERK_AUTHORIZED_PARTIES` to the browser origin(s) that will
   call the API.
3. Add the same origins to `VELIORA_APP_ORIGINS`.
4. Have the client send the session token as a Bearer token. Clerk's session
   cookie is not used by this API.

The gateway starts even when the issuer/JWKS values are empty for `dev` mode.
In `clerk` mode, an empty `CLERK_JWKS_URL` is a startup error rather than an
unauthenticated fallback.

### Replacing the auth provider

Keep the gateway contract (a verified bearer token, a stable `sub`, and
standard claims) and replace:

- `auth/clerk.js` — the verifier. If your provider does not issue RS256 JWTs,
  replace the `sofuu.crypto.verifyJWT` call and the claim checks.
- `core/config.js` — `authMode` and the verifier settings.
- The Android side: `veliora/app/src/main/java/com/veliora/ui/auth/` and
  `EmbeddedSofuu.authToken()`.

The routes, ownership checks, account projection, and SSE token policy do not
depend on Clerk.

If the JWKS endpoint is temporarily unreachable, the reference verifier starts
the service and returns `503 auth_unavailable` for authenticated requests
until a retry succeeds. A cached key set is used during transient refresh
failures.

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
No SQL, Postgres, or external daemon is involved. Organizations carry an
`owner_user_id`; legacy rows without one are not visible through the API.

## API surface

- `GET /health`, `GET /v1/health` (public)
- `GET /v1/me`
- `GET /v1/organizations/templates`
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

## Embedded Android runtime

`scripts/build_embedded_js.sh` bundles `embedded.js` into
`veliora/app/src/main/assets/veliora-embedded.js`. The Kotlin wrapper starts
it on one worker thread and calls:

- `sofuu.veliora.init(config)` once at app startup;
- `sofuu.veliora.request({method,url,headers,body})` for each gateway route;
- `sofuu.veliora.tick({ms})` to drain the embedded libuv loop while a
  pipeline is running;
- `sofuu.veliora.events({org_id,user_id,since_seq})` for durable event
  reads (available for a future live UI; the current app polls the gateway
  routes instead).

The embedded entry disables the SSE heartbeat and never creates a listening
socket. Configuration is passed from Kotlin (`storePath`, `mode`, Clerk
issuer/JWKS, pacing) rather than read from the host environment. See
`veliora/app/src/main/java/com/veliora/data/sofuu/EmbeddedSofuu.kt`.

## Test

```bash
# Both suites (dev-mode pipeline + real Clerk-mode JWT verification via a
# local JWKS fixture):
SOFUU_BIN=/path/to/sofuu make test

# Individually:
SOFUU_BIN=/path/to/sofuu bash backend/sofuu/test/smoke.sh
SOFUU_BIN=/path/to/sofuu bash backend/sofuu/test/clerk-auth.sh
```

`clerk-auth.sh` serves a public JWKS fixture from
`backend/sofuu/test/fixtures/` and uses a pre-signed test token; it never
contacts Clerk. The test private key was discarded, so the token cannot be
used against a real deployment.

See [SOFUU-GAPS.md](SOFUU-GAPS.md) for the runtime gaps found while adding
auth and the `sofuu.crypto` change that resolved them.
