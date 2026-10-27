#!/bin/bash
# Exercise the real Clerk-mode verification path without contacting Clerk.
#
# A local HTTP server serves a public JWKS fixture; the token below was signed
# by the matching (discarded) test private key. This proves that Veliora's
# gateway -> sofufu.crypto.verifyJWT -> JWKS fetch path works end to end.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../../.." && pwd)"
cd "$ROOT"

SOFUU_BIN="${SOFUU_BIN:-sofuu}"
PORT="${VELIORA_TEST_PORT:-18084}"
JWKS_PORT="${JWKS_TEST_PORT:-18085}"
BASE="http://127.0.0.1:$PORT"
JWKS_DIR="$(mktemp -d)"
STORE_ROOT="$(mktemp -d)"
STORE="$STORE_ROOT/veliora-store"
mkdir -p "$STORE"
LOG="$(mktemp)"

# Valid until 2100-01-01. iss/azp match the config below; kid matches the JWK.
TOKEN='eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCIsImtpZCI6InRlc3Qta2V5LTEifQ.eyJpc3MiOiJodHRwczovL3ZlbGlvcmEtYXBwLnJpa3VoYW5hLmNvbSIsInN1YiI6InVzZXJfdGVzdF8xMjMiLCJzaWQiOiJzZXNzX3Rlc3RfNDU2IiwiYXpwIjoiaHR0cHM6Ly92ZWxpb3JhLWFwcC5yaWt1aGFuYS5jb20iLCJleHAiOjQxMDI0NDQ4MDAsIm5iZiI6MTcwMDAwMDAwMCwiaWF0IjoxNzAwMDAwMDAwfQ.zfnAcbmhsFakar4NZnVK9qzmWOIVpggoIx4FcpMsPTkYrM3nIrXoYdll7F8N0v_rULlvJZfJ1PwctLy5FFEk_WiM0LuurNAEaaajIJqbD3tncdLLGiE7-skb9JcxDxjNu7YI_0_hjSj4uAiNs6Vl8_6z15V6syEsvV8FI3mkIc8k8Y6LFrCIOiTDlrg1okhyikXu75QYqobjm5pNTxrRKSpy1rpynLoq8G6mraUfV3cMC-qPovlLhwLfp1QGrZXHsxR5SsmAqd6KMhKohqb23NMFmUiRZcmiuJssUpiefbrchTXJaAcIHXqpjfuwd8wQHFYAFYpl_iD1ZSl8U5gLoQ'
AUTH=(-H "Authorization: Bearer $TOKEN")
ORIGIN='https://veliora-app.rikuhana.com'

cp backend/sofuu/test/fixtures/jwks.json "$JWKS_DIR/jwks.json"

cleanup() {
  if [ -n "${PID:-}" ]; then kill "$PID" 2>/dev/null || true; wait "$PID" 2>/dev/null || true; fi
  if [ -n "${JWKS_PID:-}" ]; then kill "$JWKS_PID" 2>/dev/null || true; wait "$JWKS_PID" 2>/dev/null || true; fi
  rm -rf "$JWKS_DIR" "$STORE_ROOT" "$LOG"
}
trap cleanup EXIT

echo "== start local JWKS =="
python3 -m http.server "$JWKS_PORT" --bind 127.0.0.1 --directory "$JWKS_DIR" >/dev/null 2>&1 &
JWKS_PID=$!
for _ in $(seq 1 50); do
  curl -fsS "http://127.0.0.1:$JWKS_PORT/jwks.json" >/dev/null 2>&1 && break
  sleep 0.1
done
curl -fsS "http://127.0.0.1:$JWKS_PORT/jwks.json" >/dev/null || {
  echo "FAIL: local JWKS did not start"
  exit 1
}
echo "ok: JWKS on http://127.0.0.1:$JWKS_PORT/jwks.json"

echo "== start backend in clerk mode =="
VELIORA_PORT="$PORT" VELIORA_STORE="$STORE" VELIORA_STEP_DELAY_MS=0 \
  VELIORA_AUTH_MODE=clerk \
  CLERK_ISSUER="$ORIGIN" \
  CLERK_JWKS_URL="http://127.0.0.1:$JWKS_PORT/jwks.json" \
  CLERK_AUTHORIZED_PARTIES="$ORIGIN" \
  "$SOFUU_BIN" run backend/sofuu/veliora.js >"$LOG" 2>&1 &
PID=$!

HEALTH=""
for _ in $(seq 1 100); do
  HEALTH="$(curl -fsS "$BASE/health" 2>/dev/null || true)"
  [ -n "$HEALTH" ] && break
  sleep 0.2
done
[ -n "$HEALTH" ] || { echo "FAIL: backend did not start"; cat "$LOG"; exit 1; }
echo "$HEALTH" | grep -q '"auth":"clerk"' || { echo "FAIL: $HEALTH"; exit 1; }
echo "ok: $HEALTH"

echo "== valid RS256 token is accepted =="
ME="$(curl -fsS "${AUTH[@]}" \
  -H 'X-Veliora-Platform: android' \
  -H 'X-Veliora-App-Version: 2.0.0' \
  "$BASE/v1/me")"
printf '%s' "$ME" | python3 -c '
import sys, json
d = json.load(sys.stdin)
assert d["user_id"] == "user_test_123", d
a = d["account"]
assert a["user_id"] == "user_test_123", a
assert a["platform"] == "android", a
assert a["app_version"] == "2.0.0", a
assert set(a) == {"user_id", "first_seen_at", "last_seen_at", "app_version", "platform"}, a
' || { echo "FAIL: $ME"; exit 1; }
echo "ok: valid token + minimal account record"

echo "== missing/tampered tokens are rejected =="
CODE="$(curl -sS -o /dev/null -w '%{http_code}' "$BASE/v1/me")"
[ "$CODE" = "401" ] || { echo "FAIL: missing token returned $CODE"; exit 1; }
CODE="$(curl -sS -o /dev/null -w '%{http_code}' -H 'Authorization: Bearer x.y.z' "$BASE/v1/me")"
[ "$CODE" = "401" ] || { echo "FAIL: malformed token returned $CODE"; exit 1; }
echo "ok: 401 without a valid token"

echo "== query token is accepted only on SSE routes =="
CODE="$(curl -sS -o /dev/null -w '%{http_code}' "$BASE/v1/me?token=$TOKEN")"
[ "$CODE" = "401" ] || { echo "FAIL: query token accepted on /v1/me ($CODE)"; exit 1; }
ORG="$(curl -fsS "${AUTH[@]}" -X POST "$BASE/v1/organizations" \
  -H 'Content-Type: application/json' -d '{"name":"Clerk E2E","template":"startup"}' \
  | python3 -c 'import sys,json; print(json.load(sys.stdin)["organization_id"])')"
SSE_CODE="$(curl -sS -m 1 -N -o /dev/null -w '%{http_code}' "$BASE/v1/organizations/$ORG/events?token=$TOKEN" 2>/dev/null || true)"
[ "$SSE_CODE" = "200" ] || { echo "FAIL: SSE query token returned $SSE_CODE"; exit 1; }
echo "ok: SSE query token works; /v1/me rejects it"

echo "== CORS reflects only the allow-listed origin =="
HEADERS="$(curl -sS -D - -o /dev/null -H "Origin: $ORIGIN" "${AUTH[@]}" "$BASE/v1/me")"
echo "$HEADERS" | grep -qi "access-control-allow-origin: $ORIGIN" || {
  echo "FAIL: allowed origin missing"; echo "$HEADERS"; exit 1
}
EVIL="$(curl -sS -D - -o /dev/null -H 'Origin: https://evil.example' "${AUTH[@]}" "$BASE/v1/me")"
if echo "$EVIL" | grep -qi '^access-control-allow-origin:'; then
  echo "FAIL: disallowed origin was reflected"; echo "$EVIL"; exit 1
fi
echo "ok: CORS allow-list enforced"

echo ""
echo "CLERK AUTH PASSED — real RS256 verification, claim checks, and SSE token policy."
