# Veliora

**The first self-evolving autonomous organization — an open-source research project with local-first inference.**

Veliora turns a goal into an investigation run by an organization of specialized departments. Departments gather evidence, challenge claims, debate, vote, and expose the resulting state and decisions through a native Android dashboard.

> **Alpha:** Clerk sign-in is mandatory. The Android app has no anonymous mode: it launches into Clerk's native sign-in/sign-up flow and composes the app only after a session exists. The embedded gateway verifies the Clerk session token against the Clerk JWKS with `sofuu.crypto.verifyJWT` before owner-scoping local records. It needs a Clerk publishable key at build time and the Native API enabled in the Clerk Dashboard.

## Runtime: Sofuu, on-device

Veliora's application logic is the JavaScript gateway in `backend/sofuu/`. On Android it is bundled into the APK and executed inside the Sofuu SDK (`com.sofuu:sofuu-android:0.2.0`), in the app process. There is no Veliora HTTP server, no API base URL, and no `sofuu` executable in the APK.

| Concern | Sofuu primitive |
|---|---|
| Gateway routes | In-process `sofuu.veliora.request` over the same JS gateway |
| Department workers | `sofuu.agent` (`define`, `run`, `runMany`) |
| Durable organization, investigation, task, evidence, vote, and report records | `sofuu.storage` strict-schema tables in QTSQ segments |
| Institutional memory and semantic recall | `sofuu.memory` (CMA v2 / QTSQ) |
| Local or cloud inference | `sofuu.ai` (`streamLocal` for local models, `stream` / `complete` for providers) |
| Web research | `sofuu.web` |
| Session auth | `sofuu.crypto.verifyJWT` against the Clerk JWKS |

`backend/sofuu/` uses only the public Sofuu SDK surface; it does not import Sofuu's internal source files. The same source can still run as a host-side HTTP service for CLI tests (`sofuu run backend/sofuu/veliora.js`), but the Android app does not use that path. See [backend/sofuu/README.md](backend/sofuu/README.md).

## Current status

- The embedded gateway runs the full investigation pipeline end to end in deterministic `scripted` mode: research, cross-department challenge, two-round debate, synthesis, trust-weighted vote, confidence loop, and board report.
- Clerk authentication is mandatory in the app. The embedded gateway verifies RS256 session tokens locally against the Clerk JWKS, keeps a minimal local account projection, and owner-scopes records. The account record stores only the Clerk subject, first/last seen timestamps, app version, and platform — no email, name, or content.
- Real `sofuu.ai` and `sofuu.agent` modes are wired behind the same adapter but have not yet been validated against a provider or local model.
- The app polls the gateway's organization and investigation routes to refresh the UI. It does not need Server-Sent Events or a listening HTTP server.
- The Android app is gated behind Clerk's native sign-in/sign-up UI; there is no anonymous route. The signed-in identity and sign-out live on the Instance screen.
- The Android alpha contains no mock/design package and no placeholder-only screens. Screens use real on-device data with loading, error, and empty states.
- The Instance screen reports the loaded Sofuu ABI, capability bits, a SHA-256 vector, and the local embedding dimension, so the SDK integration can be checked without a backend.

## Quick start

### Requirements

- Android Studio, Android SDK 36, and JDK 17 for the Android app
- A Clerk application with the Frontend API URL and JWKS endpoint
- A local GGUF or SafeTensors model for offline inference, or a provider API key for cloud models (optional; the default `scripted` mode needs neither)
- For host-side gateway tests only: a Sofuu `0.2.0` build with `sofuu.storage` and `sofuu.crypto`

### Android

The Android app uses the official Clerk Android SDK (`com.clerk:clerk-android-ui`)
and embeds `com.sofuu:sofuu-android:0.2.0` from the public GitHub Pages Maven
repo. In the Clerk Dashboard:

1. Copy the application's **Publishable Key** (`pk_test_...` or `pk_live_...`).
2. Open **Native applications** and enable the **Native API**.
3. Configure the sign-in methods you want (email/password, email code, Google, etc.).
4. If your Clerk tokens include an `azp` claim, put the expected values in
   `VELIORA_CLERK_AUTHORIZED_PARTIES` (comma-separated).

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

If the Clerk key is missing, the app launches to an explicit "Clerk is not
configured" screen instead of crashing or sending unauthenticated requests.

### User count and account metadata

Clerk is the source of truth for accounts and monthly active users; its
dashboard shows those numbers without a custom analytics pipeline. The
embedded gateway also keeps a deliberately small local account projection per
Clerk subject (`user_id`, `first_seen_at`, `last_seen_at`, `app_version`,
`platform`), so app-version checks do not require storing email addresses,
names, or application content. The Android client supplies its version and
platform to the in-process gateway; the `/v1/me` route remains available
inside the embedded gateway.

### Production setup

The production Clerk Frontend API for this deployment is:

```text
https://clerk.veliora-app.rikuhana.com
https://clerk.veliora-app.rikuhana.com/.well-known/jwks.json
```

The app defaults to those URLs. Override them with
`VELIORA_CLERK_ISSUER` / `VELIORA_CLERK_JWKS_URL` for a test instance. In the
Clerk Dashboard, with the **Production** instance selected:

1. Open **Configure → Developers → Native applications** and keep **Native
   API** enabled.
2. Click **Add Android app** and enter:
   - **Namespace:** `veliora-production`
   - **Package name:** `com.veliora` (must match `applicationId`)
   - **SHA-256 fingerprints:** add the debug certificate now, and the release
     certificate before shipping.
3. If you use Google or another OAuth provider, add the mobile SSO redirect
   URL shown by Clerk. Email/password and email-code sign-in do not need one.
4. Open **API keys** and confirm the **Publishable key** matches the value in
   `.env` (`VELIORA_CLERK_PUBLISHABLE_KEY`). Do not copy the secret key into
   the app.

The debug certificate for this workspace is:

```text
FE:91:3C:9B:83:A6:59:12:9B:4C:6B:E0:01:00:09:1B:A7:9A:E9:E0:1C:95:5B:B0:A8:41:28:23:10:D5:72:73
```

Create a release keystore and read its fingerprint:

```bash
keytool -genkeypair -v \
  -keystore veliora-release.jks \
  -alias veliora \
  -keyalg RSA -keysize 4096 -validity 10000
keytool -list -v -keystore veliora-release.jks -alias veliora | grep SHA256
```

Add the release fingerprint to the same Clerk Android app. If you use Play
App Signing, add the Play Console **app signing certificate** fingerprint too,
because that is the certificate installed on users' devices.

Set the release-signing values outside git (for example in your shell or
`~/.gradle/gradle.properties`), then build:

```bash
cd veliora
export VELIORA_RELEASE_STORE_FILE=/absolute/path/to/veliora-release.jks
export VELIORA_RELEASE_STORE_PASSWORD=...
export VELIORA_RELEASE_KEY_ALIAS=veliora
export VELIORA_RELEASE_KEY_PASSWORD=...
export VELIORA_CLERK_PUBLISHABLE_KEY=pk_live_...
./gradlew :app:bundleRelease
```

No backend host or `adb reverse` is required: the gateway, storage, and
pipeline run inside the APK on the device. Clerk and any provider/web features
use HTTPS directly.

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
| `VELIORA_CLERK_PUBLISHABLE_KEY` | required | Clerk publishable key |
| `VELIORA_CLERK_ISSUER` | production Frontend API | JWT `iss` validation |
| `VELIORA_CLERK_JWKS_URL` | `<issuer>/.well-known/jwks.json` | Local RS256 verification |
| `VELIORA_CLERK_AUTHORIZED_PARTIES` | empty | Comma-separated `azp` allow-list |
| `VELIORA_EMBEDDED_MODE` | `scripted` | `scripted`, `sofuu`, or `agent` |
| `VELIORA_STEP_DELAY_MS` | `25` | Scripted pipeline pacing |
| `VELIORA_PROVIDER` / `VELIORA_MODEL` | empty | Optional real inference |

The host-side reference service (`make run`) still reads
`backend/sofuu/veliora.json` and the `CLERK_*` / `VELIORA_*` environment
variables in `.env.example`. The Android app stores QTSQ data under its
private `filesDir/sofuu/veliora-store`; local inference is offline after the
model file is provisioned.

## License

MIT — see [LICENSE](LICENSE). Third-party dependencies and bundled assets are listed in [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
