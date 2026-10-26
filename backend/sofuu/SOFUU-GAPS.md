# Sofuu SDK gaps found while adding Clerk auth

- **Date:** 2026-10-26
- **Sofuu version tested:** `0.2.0-beta` (built from source at `/Users/priyanshuboruah/ai-native-js-runtime`)
- **Veliora backend:** `backend/sofuu/` (Sofuu SDK only; no internal Sofuu imports)

Veliora moved from an authless API to Clerk session tokens. Clerk issues an
RS256-signed JWT and publishes the corresponding public keys as a JWKS. The
backend must verify the signature and claims locally before trusting a
request. Sofuu 0.2.0-beta had no way to do that from JavaScript.

## Blocking gaps

### 1. No crypto surface at all

`sofuu` exposes BigInt, but none of these exist:

| Expected primitive | Status in Sofuu 0.2.0-beta |
| --- | --- |
| `crypto.subtle.verify` (WebCrypto) | missing |
| `crypto.subtle.digest` | missing |
| `crypto.getRandomValues` | missing |
| `atob` / `btoa` | missing |
| `TextEncoder` / `TextDecoder` | missing |
| `sofuu.crypto` | missing |
| `require("crypto")` | missing |
| `sofuu.jwt` | missing |

Clerk's session token is a compact JWS:

```
base64url(header) . base64url(payload) . base64url(RS256 signature)
```

Verifying it needs SHA-256, base64url, and RSA PKCS#1 v1.5 verification. None
of those were reachable from JS.

### 2. No JWT/JWKS helper

Even with raw crypto, every app would reimplement JWT splitting, JWKS key
selection by `kid`, `exp`/`nbf` checks, issuer checks, and Clerk's `azp`
(authorized party) check. That is security-sensitive glue that belongs in the
runtime, not copy-pasted into each application.

## Not blocking, but worth knowing

- **No cookie parser.** Veliora uses `Authorization: Bearer <token>` rather
  than Clerk cookies, so this did not block auth. A `sofuu.http` cookie helper
  would still be useful for browser-first deployments.
- **No built-in CORS handling on `sofuu.http.createServer`.** Veliora adds the
  headers manually and handles `OPTIONS` before routing.
- **`EventSource` cannot set an `Authorization` header.** The SSE endpoint
  accepts `?token=<jwt>` in addition to the header. This is an application
  decision, not a Sofuu defect.
- **`sofuu.storage` has no update/delete.** Veliora's store is append-only and
  reads the newest row per key. This is sufficient, but callers must remember
  that `id` is a reserved numeric auto-increment column and the application
  key belongs in `key`.
- **A storage path with a dot in its final component is treated as a file.**
  `sofuu.storage.open("/tmp/store.v1")` uses `/tmp` as the database directory,
  because the SDK interprets any final-component extension as a file and takes
  its parent. Use a directory name without an extension
  (`/tmp/store-v1/`) or pass a path whose parent is the intended directory.
  Veliora's test scripts create `<tmp>/veliora-store`, not `mktemp -d` directly.

## Resolution

The missing crypto/JWT primitives were added directly to Sofuu as
`sofuu.crypto`:

```js
sofuu.crypto.sha256("abc");                    // lowercase hex
sofuu.crypto.base64urlEncode("hello");         // "aGVsbG8"
sofuu.crypto.base64urlDecode("aGVsbG8");       // "hello"
sofuu.crypto.verifyJWT(token, jwks, options);  // {ok, header, payload} | {ok:false, error}
```

Implementation notes and the exact file list are in the Sofuu repository at
`SOFUU-CRYPTO-JWT.md` (repository root; Sofuu's own `docs/` folder is
gitignored).

Veliora now depends on that Sofuu build. A stock `0.2.0-beta` binary without
the module fails fast at startup with an explicit error instead of silently
accepting unauthenticated requests.
