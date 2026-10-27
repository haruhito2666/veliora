# Veliora

**The first self-evolving autonomous organization — an open-source research project with local-first inference.**

Veliora turns a goal into an investigation run by an organization of specialized departments. Departments gather evidence, challenge claims, debate, vote, and expose the resulting state and decisions through a native Android dashboard.

> **Alpha:** Clerk sign-in is mandatory. Every `/v1` request must send `Authorization: Bearer <Clerk session JWT>`; organizations are scoped to the authenticated user. The Android app has no anonymous mode: it launches into Clerk's native sign-in/sign-up flow and composes the app only after a session exists. It needs a Clerk publishable key at build time and the Native API enabled in the Clerk Dashboard.

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
- Clerk authentication is mandatory on the backend: RS256 session tokens are verified locally against the Clerk JWKS, `/v1/me` exposes the identity plus a minimal account record, and organizations are owner-scoped. The account record stores only the Clerk subject, first/last seen timestamps, app version, and platform — no email, name, or content. A local JWKS test exercises the real verification path.
- Real `sofuu.ai` and `sofuu.agent` modes are wired behind the same adapter but have not yet been validated against a provider or local model.
- Live events use Server-Sent Events because Sofuu's HTTP server has no WebSocket upgrade. The Android client connects to the SSE endpoint with the Clerk bearer token and reconnects when the token expires.
- The Android app is gated behind Clerk's native sign-in/sign-up UI; there is no anonymous route. The signed-in identity and sign-out live on the Instance screen.
- The Android alpha contains no mock/design package and no placeholder-only screens. Screens use real API data with loading, error, and empty states.

## Quick start

### Requirements

- Sofuu `0.2.0-beta` or newer, built with `sofuu.storage` and `sofuu.crypto` support (`sofuu doctor` should report healthy)
- A Clerk application with the Frontend API URL and JWKS endpoint. `VELIORA_AUTH_MODE=dev` is available for local tests only, never for a product deployment.
- A local GGUF or SafeTensors model for offline inference, or a provider API key for cloud models
- Android Studio, Android SDK 36, and JDK 17 for the Android client

### Backend

```bash
sofuu doctor
sofuu run backend/sofuu/veliora.js
```

The API listens on `http://127.0.0.1:8080` by default. Clerk is required for
the product; configure it with the `CLERK_*` variables in `.env.example`.
For automated local tests only, dev auth is available:

```bash
VELIORA_AUTH_MODE=dev sofuu run backend/sofuu/veliora.js
# then send: Authorization: Bearer dev:user_local
```

Dev mode is still fail-closed (a missing token returns `401`) and must not be
used by a shipped client.

Run both the pipeline smoke test and the real Clerk verification test with:

```bash
SOFUU_BIN=/path/to/sofuu make test
```

### Android

The Android app uses the official Clerk Android SDK (`com.clerk:clerk-android-ui`)
and requires Android SDK 36 and JDK 17. In the Clerk Dashboard:

1. Copy the application's **Publishable Key** (`pk_test_...` or `pk_live_...`).
2. Open **Native applications** and enable the **Native API**.
3. Configure the sign-in methods you want (email/password, email code, Google, etc.).
4. Add the client origin that appears in your tokens to `CLERK_AUTHORIZED_PARTIES`
   on the backend if your Clerk instance includes one.

Build with the key:

```bash
cd veliora
./gradlew :app:assembleDebug \
  -PVELIORA_CLERK_PUBLISHABLE_KEY=pk_test_your_key
```

You can also export `VELIORA_CLERK_PUBLISHABLE_KEY` instead of using `-P`.
From Android Studio, put the same line in `~/.gradle/gradle.properties` (or
the run configuration's Gradle arguments). The key is public; never put a
Clerk secret key (`sk_...`) in the app.

The emulator uses `http://10.0.2.2:8080` by default. Override the API base for
a device or production deployment:

```bash
./gradlew :app:assembleDebug \
  -PVELIORA_CLERK_PUBLISHABLE_KEY=pk_test_your_key \
  -PVELIORA_API_BASE=https://veliora-app.rikuhana.com
```

If the key is missing, the app launches to an explicit "Clerk is not
configured" screen instead of crashing or sending unauthenticated requests.

### User count and account metadata

Clerk is the source of truth for accounts and monthly active users; its
dashboard shows those numbers without a custom analytics pipeline. The backend
also keeps a deliberately small account projection per Clerk subject
(`user_id`, `first_seen_at`, `last_seen_at`, `app_version`, `platform`), so
support and app-version checks do not require storing email addresses, names,
or application content. The Android client sends its version and platform
headers automatically; `/v1/me` returns the record.

## Repository map

| Path | Purpose |
|------|---------|
| `backend/sofuu/` | The Sofuu backend (JS/TS) |
| `veliora/` | Native Android Compose application |

Design documents and organization templates are kept in local `docs/` and
`templates/` folders and are not part of the published repository.

## Configuration

The backend reads `backend/sofuu/veliora.json` and supports environment overrides. Copy `.env.example` for the variable names. Clerk auth needs `CLERK_ISSUER`, `CLERK_JWKS_URL`, and `CLERK_AUTHORIZED_PARTIES`; CORS needs `VELIORA_APP_ORIGINS`. Local inference is fully offline after the model file is provisioned; cloud inference requires a provider key.

## License

MIT — see [LICENSE](LICENSE). Third-party dependencies and bundled assets are listed in [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
