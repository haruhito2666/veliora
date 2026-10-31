# Veliora

**The first self-evolving autonomous organization — an open-source research project with local-first inference.**

Veliora turns a goal into an investigation run by an organization of specialized departments. Departments gather evidence, challenge claims, debate, vote, and expose the resulting state and decisions through a native Android dashboard.

> **Alpha:** The reference Android app uses Clerk for sign-in and has no anonymous mode. That is an integration choice for this app, not a requirement of the open-source project: no Clerk instance, key, secret, or maintainer domain is shipped or expected. Every build configures its own auth provider. The embedded gateway verifies the bearer token against the configured issuer/JWKS with `sofuu.crypto.verifyJWT` before owner-scoping local records.

## Project status

Veliora is maintained by a single author. Commits and pushes go directly to
`main`; pull requests are not accepted. The source is open under the MIT
license, so forks are welcome, but this repository does not operate a
contribution or pull-request review workflow.

## Runtime: Sofuu, on-device

Veliora's application logic is the JavaScript gateway in `backend/sofuu/`. On Android it is bundled into the APK and executed inside the Sofuu SDK (`com.sofuu:sofuu-android:0.2.0`), in the app process. There is no Veliora HTTP server, no API base URL, and no `sofuu` executable in the APK.

| Concern | Sofuu primitive |
|---|---|
| Gateway routes | In-process `sofuu.veliora.request` over the same JS gateway |
| Department workers | `sofuu.agent` (`define`, `run`, `runMany`) |
| Durable organization, investigation, task, evidence, vote, and report records | `sofuu.storage` strict-schema tables in QTSQ segments |
| Institutional memory and semantic recall | `sofuu.memory` (not wired in this alpha) |
| Local or cloud inference | `sofuu.ai` (`streamLocal` for local models, `stream` / `complete` for providers) |
| Web research | `sofuu.web` |
| Session auth | `sofuu.crypto.verifyJWT` against the configured issuer/JWKS (Clerk is the reference adapter) |

`backend/sofuu/` uses only the public Sofuu SDK surface; it does not import Sofuu's internal source files. The same source can still run as a host-side HTTP service for CLI tests (`sofuu run backend/sofuu/veliora.js`), but the Android app does not use that path. See [backend/sofuu/README.md](backend/sofuu/README.md).

## Current status

- The embedded gateway runs the full investigation pipeline end to end in deterministic `scripted` mode: research, cross-department challenge, two-round debate, synthesis, trust-weighted vote, confidence loop, and board report.
- The reference app requires authentication. Its Clerk adapter verifies RS256 session tokens locally against the configured issuer/JWKS, keeps a minimal local account projection, and owner-scopes records. The account record stores only the subject, first/last seen timestamps, app version, and platform — no email, name, or content. Forks can replace the adapter without changing storage or ownership.
- Real `sofuu.ai` and `sofuu.agent` modes are wired behind the same adapter but have not yet been validated against a provider or local model.
- Live organization screens poll the in-process gateway and stop polling when backgrounded or when no live screen is visible. There is no Server-Sent Events connection or listening HTTP server.
- The reference Android app is gated behind custom Clerk sign-in/sign-up screens; there is no anonymous route in this build. The signed-in identity and sign-out live on the Instance screen. See [Auth provider](#auth-provider) to use your own Clerk app or a different provider.
- External communications and evolution history have real read paths in the gateway, but this alpha has no producer that writes those records; the screens show explicit empty states until one exists. The previous synthetic org-chart diff was removed.
- The Android alpha contains no mock/design package. Screens use real on-device data with loading, error, retry, and empty states.
- The Instance screen reports the loaded Sofuu ABI, capability bits, a SHA-256 vector, and the local embedding dimension, so the SDK integration can be checked without a backend.

## Quick start

### Requirements

- Android Studio, Android SDK 36, and JDK 17 for the Android app
- A Clerk application (the reference provider) or another auth provider you wire in
- A local GGUF or SafeTensors model for offline inference, or a provider API key for cloud models (optional; the default `scripted` mode needs neither)
- For host-side gateway tests only: a Sofuu `0.2.0` build with `sofuu.storage` and `sofuu.crypto`

### Android

The reference Android app uses the official Clerk Android SDK
(`com.clerk:clerk-android-api`) with a custom white sign-in/sign-up flow, and
embeds `com.sofuu:sofuu-android:0.2.0` from the public GitHub Pages Maven repo.
This is the reference auth adapter, not a dependency on the maintainer's Clerk
application: the repository ships no Clerk instance, publishable key, secret,
or domain. See [Auth provider](#auth-provider) to use your own Clerk app or a
different provider.

The app is built and tested as a **signed release**, not a debug variant.
Configure your Clerk values and release-signing values as described in
[Signed release build](#signed-release-build), then run:

```bash
cd veliora
./gradlew :app:assembleRelease
```

The installable artifact is
`app/build/outputs/apk/release/app-release.apk`. Export the values or put them
in `~/.gradle/gradle.properties`; the publishable key is public, but never put
a Clerk secret key (`sk_...`) in the app.
`VELIORA_CLERK_AUTHORIZED_PARTIES` is optional unless your tokens carry an
`azp` claim. The Google redirect is
`clerk://<your.applicationId>.callback` and is declared by the Clerk SDK, so no
extra manifest entry is required.

### Credentials

`.env`, provider keys, signing keys, `local.properties`, `google-services.json`,
and runtime/model files are gitignored and must never be committed. Enable the
checked-in pre-commit guard once per clone:

```bash
make install-hooks
```

`make check-secrets` audits `HEAD` and the Git index. It prints only
`file:line`, never the matched value, and refuses credential-shaped strings and
sensitive filenames. The same scan runs in GitHub Actions on every push to
`main` and every pull request. If a credential is ever pushed, rotate it
immediately; a history rewrite does not make it private. The Clerk publishable
key (`pk_live_...`) is public, but the secret key (`sk_...`) is not needed by
this app and must never enter the repository or the APK.

The app runs entirely on the device; there is no API base to configure. The
gateway bundle is committed at
`veliora/app/src/main/assets/veliora-embedded.js`. Regenerate it after changing
`backend/sofuu/`:

```bash
make embedded-js
```

The SDK path also has a host-side test that evaluates the committed bundle
through a Sofuu build with `sofuu.storage`/`sofuu.crypto`:

```bash
SOFUU_BIN=/path/to/sofuu make test-embedded
```

If a required auth setting is missing, the app launches to an explicit
auth-configuration screen instead of crashing or sending unauthenticated
requests.

### Auth provider

The shipped adapter uses Clerk. It is not tied to the maintainer's Clerk
application. To use your own Clerk instance:

1. In **your** Clerk dashboard, enable **Native API** and add your Android app
   (package name = your `applicationId`, plus debug/release SHA-256
   fingerprints).
2. Enable **Email**; for the reference email flow enable **Email verification
   code** (it does not use magic links). Enable **Google** if you want the
   Google button.
3. Copy the values from Clerk:

   | Setting | Where it comes from |
   |---|---|
   | `VELIORA_CLERK_PUBLISHABLE_KEY` | **Configure → API keys → Publishable key**. Use the `pk_test_...` key for a development instance and `pk_live_...` for production. Never use the secret key (`sk_...`). |
   | `VELIORA_CLERK_ISSUER` | **Configure → Domains → Frontend API URL** (or the development Frontend API URL). Use the base URL with no trailing slash; it must match the token's `iss` claim. |
   | `VELIORA_CLERK_JWKS_URL` | Normally `<issuer>/.well-known/jwks.json`. Some dashboard versions also show the JWKS URL directly. Verify it returns JSON with `curl -fsS <url>`. |
   | `VELIORA_CLERK_AUTHORIZED_PARTIES` | Not a Clerk dashboard value. Comma-separated values for the `azp` claim in your tokens: your web app's origin plus localhost for tests. Native Android tokens may use the application ID, which the app adds automatically. |

4. For a host-side backend process, set `CLERK_ISSUER` / `CLERK_JWKS_URL` to
   the same values. The Android build passes its values into the embedded
   gateway automatically.

To use a different provider, keep the gateway contract
(`Authorization: Bearer <JWT>`, a stable `sub`, and standard claims) and
replace these seams:

- `veliora/app/src/main/java/com/veliora/ui/auth/` — the reference sign-in UI
  and `AuthViewModel`.
- `veliora/app/src/main/java/com/veliora/data/sofuu/EmbeddedSofuu.kt` —
  `authToken()`, which currently returns the Clerk session token.
- `backend/sofuu/auth/clerk.js` — the JWT verifier used by the gateway.
- `backend/sofuu/core/config.js` — auth mode and verifier settings.

If your provider does not issue RS256 JWTs, replace the `verifyJWT` call in
the verifier; the rest of the gateway does not depend on Clerk. The reference
app has no anonymous route, and `VELIORA_AUTH_MODE=dev` remains a test-only
mode, not a product authless fallback.

### User count and account metadata

With the reference adapter, Clerk is the source of truth for accounts and
monthly active users; its dashboard shows those numbers without a custom
analytics pipeline. The embedded gateway also keeps a deliberately small local
account projection per authenticated subject (`user_id`, `first_seen_at`,
`last_seen_at`, `app_version`, `platform`), so app-version checks do not
require storing email addresses, names, or application content. The Android
client supplies its version and platform to the in-process gateway; the
`/v1/me` route remains available inside the embedded gateway. A different auth
provider can populate the same subject/claim shape.

### Signed release build

Create a release keystore and read its fingerprint:

```bash
keytool -genkeypair -v \
  -keystore veliora-release.jks \
  -alias veliora \
  -keyalg RSA -keysize 4096 -validity 10000
keytool -list -v -keystore veliora-release.jks -alias veliora | grep SHA256
```

Add the release certificate's SHA-256 to your own auth provider's Android app
registration (in Clerk, the reference adapter). If you use Play App Signing,
add the Play Console **app signing certificate** fingerprint too, because that
is the certificate installed on users' devices.

Set your auth values and release-signing values outside git (for example in
your shell or `~/.gradle/gradle.properties`), then build:

```bash
cd veliora
export VELIORA_RELEASE_STORE_FILE=/absolute/path/to/veliora-release.jks
export VELIORA_RELEASE_STORE_PASSWORD=...
export VELIORA_RELEASE_KEY_ALIAS=veliora
export VELIORA_RELEASE_KEY_PASSWORD=...
export VELIORA_CLERK_PUBLISHABLE_KEY=pk_live_your_key
export VELIORA_CLERK_ISSUER=https://clerk.your-domain.example
export VELIORA_CLERK_JWKS_URL=https://clerk.your-domain.example/.well-known/jwks.json
./gradlew :app:assembleRelease   # signed APK for device testing
./gradlew :app:bundleRelease     # AAB for Play
```

Install the signed APK with:

```bash
adb install -r veliora/app/build/outputs/apk/release/app-release.apk
```

If any `VELIORA_RELEASE_*` value is missing, Gradle still produces an
**unsigned** release APK that Android will not install. There is no debug-build
fallback in this workflow.

No backend host or `adb reverse` is required: the gateway, storage, and
pipeline run inside the APK on the device. Your auth provider and any other
network features use HTTPS directly.

## Repository map

| Path | Purpose |
|------|---------|
| `backend/sofuu/` | Shared JS gateway source; bundled into the app |
| `veliora/` | Native Android Compose application |
| `scripts/build_embedded_js.sh` | Rebuilds the app's committed gateway bundle |

Design documents and organization templates are kept in local `docs/` and
`templates/` folders and are not part of the published repository.

## Configuration

The Android app configures the embedded runtime through Gradle properties or
environment variables:

| Variable | Default | Purpose |
|---|---|---|
| `VELIORA_CLERK_PUBLISHABLE_KEY` | required | Your Clerk publishable key (reference adapter) |
| `VELIORA_CLERK_ISSUER` | required | Your Clerk Frontend API URL for JWT `iss` validation |
| `VELIORA_CLERK_JWKS_URL` | `<issuer>/.well-known/jwks.json` | Local RS256 verification |
| `VELIORA_CLERK_AUTHORIZED_PARTIES` | empty | Comma-separated `azp` allow-list |
| `VELIORA_EMBEDDED_MODE` | `scripted` | `scripted`, `sofuu`, or `agent` |
| `VELIORA_STEP_DELAY_MS` | `25` | Scripted pipeline pacing |
| `VELIORA_PROVIDER` / `VELIORA_MODEL` | empty | Optional real inference |
| `VELIORA_BASE_URL` | empty | Optional OpenAI-compatible or local model endpoint |

No maintainer Clerk host, key, or instance is defaulted. If you replace the
adapter, these `VELIORA_CLERK_*` settings are replaced by your provider's
configuration.

Inference settings can also be changed in the app's **Settings** tab. It supports:

- Scripted, Cloud, and Local inference modes.
- Saved model connections (name, provider, model, base URL, API key), with
  Android-Keystore-encrypted API keys.
- Web search engine selection (DuckDuckGo, Brave, Tavily) and API keys.
- HTTP MCP tool servers. Android cannot run stdio MCP processes, so only
  Streamable HTTP servers are supported.
- An experimental "use tools during investigations" mode that runs departments
  as Sofuu agents so they can use web and MCP tools.
- Pipeline step delay and scripted-fallback controls.

Connection/integration edits persist immediately. Runtime settings apply after
you close and reopen the app; web-search settings apply right away. Local mode
expects an OpenAI-compatible server (for example Ollama or llama.cpp);
on-device GGUF inference is not wired into the investigation loop yet.

The host-side reference service (`make run`) still reads
`backend/sofuu/veliora.json` and the `CLERK_*` / `VELIORA_*` environment
variables in `.env.example`. The Android app stores QTSQ data under its
private `filesDir/sofuu/veliora-store`; local inference is offline after the
model file is provisioned.

As a fallback, provider keys can also be provisioned into the app's private
storage rather than entered in the Settings tab:

```text
files/sofuu/provider-keys.json
{"openai":"sk-..."}   # or the provider name configured for Sofuu
```

`sofuu`/`agent` modes no longer fall back to scripted fixtures: a missing
provider, model, key, or reachable endpoint is surfaced as an error.

## License

MIT — see [LICENSE](LICENSE). Third-party dependencies and bundled assets are listed in [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
