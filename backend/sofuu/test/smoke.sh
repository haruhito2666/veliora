#!/bin/bash
# Smoke-test the Sofuu backend end to end.
# Requires: bash, curl, python3, and a Sofuu build with sofuu.storage +
# sofuu.crypto (the auth path is exercised in dev mode here; real Clerk
# verification is covered by Sofuu's crypto tests and can be run against a
# Clerk JWKS with VELIORA_AUTH_MODE=clerk).
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../../.." && pwd)"
cd "$ROOT"

SOFUU_BIN="${SOFUU_BIN:-sofuu}"
PORT="${VELIORA_TEST_PORT:-18080}"
STORE_ROOT="$(mktemp -d)"
STORE="$STORE_ROOT/veliora-store"
mkdir -p "$STORE"
LOG="$(mktemp)"
BASE="http://127.0.0.1:$PORT"
AUTH=(-H "Authorization: Bearer dev:user_smoke")
OTHER=(-H "Authorization: Bearer dev:user_other")

cleanup() {
  if [ -n "${PID:-}" ]; then
    kill "$PID" 2>/dev/null || true
    wait "$PID" 2>/dev/null || true
  fi
  rm -rf "$STORE_ROOT" "$LOG"
}
trap cleanup EXIT

echo "== start backend (sofuu=$SOFUU_BIN, port=$PORT) =="
VELIORA_PORT="$PORT" VELIORA_STORE="$STORE" VELIORA_STEP_DELAY_MS=0 \
  VELIORA_AUTH_MODE=dev \
  "$SOFUU_BIN" run backend/sofuu/veliora.js >"$LOG" 2>&1 &
PID=$!

echo "== wait for health =="
HEALTH=""
for _ in $(seq 1 100); do
  HEALTH="$(curl -fsS "$BASE/health" 2>/dev/null || true)"
  if [ -n "$HEALTH" ]; then break; fi
  sleep 0.2
done
if [ -z "$HEALTH" ]; then
  echo "FAIL: backend did not become healthy"
  cat "$LOG"
  exit 1
fi
echo "$HEALTH" | grep -q '"backend":"sofuu"' || { echo "FAIL: $HEALTH"; exit 1; }
echo "$HEALTH" | grep -q '"auth":"dev"' || { echo "FAIL: auth mode missing: $HEALTH"; exit 1; }
echo "ok: $HEALTH"

echo "== auth is required and dev identity works =="
CODE="$(curl -sS -o /dev/null -w '%{http_code}' "$BASE/v1/me")"
[ "$CODE" = "401" ] || { echo "FAIL: unauthenticated /v1/me returned $CODE"; exit 1; }
ME="$(curl -fsS "${AUTH[@]}" \
  -H 'X-Veliora-Platform: android' \
  -H 'X-Veliora-App-Version: 1.0.0' \
  "$BASE/v1/me")"
ME_USER="$(printf '%s' "$ME" | python3 -c 'import sys,json; print(json.load(sys.stdin)["user_id"])')"
[ "$ME_USER" = "user_smoke" ] || { echo "FAIL: /v1/me user = $ME_USER"; exit 1; }
printf '%s' "$ME" | python3 -c '
import sys, json
a = json.load(sys.stdin)["account"]
assert a["user_id"] == "user_smoke", a
assert a["first_seen_at"], a
assert a["last_seen_at"], a
assert a["app_version"] == "1.0.0", a
assert a["platform"] == "android", a
assert set(a) == {"user_id", "first_seen_at", "last_seen_at", "app_version", "platform"}, a
' || { echo "FAIL: minimal account record is wrong: $ME"; exit 1; }
FIRST_SEEN="$(printf '%s' "$ME" | python3 -c 'import sys,json; print(json.load(sys.stdin)["account"]["first_seen_at"])')"
ME2="$(curl -fsS "${AUTH[@]}" \
  -H 'X-Veliora-Platform: android' \
  -H 'X-Veliora-App-Version: 1.0.1' \
  "$BASE/v1/me")"
printf '%s' "$ME2" | FIRST_SEEN="$FIRST_SEEN" python3 -c '
import os, json, sys
a = json.load(sys.stdin)["account"]
assert a["first_seen_at"] == os.environ["FIRST_SEEN"], a
assert a["app_version"] == "1.0.1", a
' || { echo "FAIL: account touch did not update version: $ME2"; exit 1; }
echo "ok: /v1/me -> $ME_USER; minimal account record persisted"

echo "== create organization =="
ORG="$(curl -fsS "${AUTH[@]}" -X POST "$BASE/v1/organizations" \
  -H 'Content-Type: application/json' \
  -d '{"name":"Sofuu E2E","template":"startup","constitution":{"mission":"Validate the Sofuu backend."}}' \
  | python3 -c 'import sys,json; print(json.load(sys.stdin)["organization_id"])')"
echo "ok: org $ORG"

WORKERS="$(curl -fsS "${AUTH[@]}" "$BASE/v1/organizations/$ORG" \
  | python3 -c 'import sys,json; print(len(json.load(sys.stdin)["workers"]))')"
[ "$WORKERS" = "8" ] || { echo "FAIL: expected 8 workers, got $WORKERS"; exit 1; }
echo "ok: 8 workers"

MISSION="$(curl -fsS "${AUTH[@]}" "$BASE/v1/organizations/$ORG/constitution" \
  | python3 -c 'import sys,json; print(json.load(sys.stdin)["mission"])')"
[ "$MISSION" = "Validate the Sofuu backend." ] || { echo "FAIL: mission = $MISSION"; exit 1; }
echo "ok: mission override persisted"

echo "== organizations are scoped to their owner =="
LISTED="$(curl -fsS "${AUTH[@]}" "$BASE/v1/organizations" \
  | python3 -c 'import sys,json; print(len(json.load(sys.stdin)["data"]))')"
[ "$LISTED" = "1" ] || { echo "FAIL: owner sees $LISTED organizations"; exit 1; }
OTHER_ORG="$(curl -fsS "${OTHER[@]}" -X POST "$BASE/v1/organizations" \
  -H 'Content-Type: application/json' \
  -d '{"name":"Other User Org","template":"startup"}' \
  | python3 -c 'import sys,json; print(json.load(sys.stdin)["organization_id"])')"
CODE="$(curl -sS -o /dev/null -w '%{http_code}' "${OTHER[@]}" "$BASE/v1/organizations/$ORG")"
[ "$CODE" = "404" ] || { echo "FAIL: cross-owner read returned $CODE"; exit 1; }
CODE="$(curl -sS -o /dev/null -w '%{http_code}' "${OTHER[@]}" "$BASE/v1/organizations/$ORG/investigations")"
[ "$CODE" = "404" ] || { echo "FAIL: cross-owner investigations returned $CODE"; exit 1; }
echo "ok: other user cannot read $ORG (own org $OTHER_ORG)"

echo "== SSE requires auth =="
CODE="$(curl -sS -o /dev/null -w '%{http_code}' "$BASE/v1/organizations/$ORG/events")"
[ "$CODE" = "401" ] || { echo "FAIL: unauthenticated SSE returned $CODE"; exit 1; }
echo "ok: SSE 401 without token"

echo "== create investigation =="
INV="$(curl -fsS "${AUTH[@]}" -X POST "$BASE/v1/organizations/$ORG/investigations" \
  -H 'Content-Type: application/json' \
  -d '{"prompt":"Should I build an AI healthcare startup?","depth":"standard"}' \
  | python3 -c 'import sys,json; print(json.load(sys.stdin)["investigation_id"])')"
echo "ok: investigation $INV"

echo "== wait for completion =="
STATE=""
for _ in $(seq 1 150); do
  STATE="$(curl -fsS "${AUTH[@]}" "$BASE/v1/organizations/$ORG/investigations/$INV" \
    | python3 -c 'import sys,json; d=json.load(sys.stdin); print(d["state"], d["cycle_count"], round(float(d.get("confidence") or 0), 2))')"
  case "$STATE" in
    completed\ *) break ;;
  esac
  sleep 0.2
done
echo "$STATE" | grep -q '^completed 1 ' || { echo "FAIL: state = $STATE"; tail -40 "$LOG"; exit 1; }
echo "ok: $STATE"

EVIDENCE="$(curl -fsS "${AUTH[@]}" "$BASE/v1/organizations/$ORG/investigations/$INV/evidence" \
  | python3 -c 'import sys,json; print(len(json.load(sys.stdin)))')"
[ "$EVIDENCE" = "5" ] || { echo "FAIL: evidence = $EVIDENCE"; exit 1; }
echo "ok: 5 evidence records"

CHALLENGES="$(curl -fsS "${AUTH[@]}" "$BASE/v1/organizations/$ORG/investigations/$INV/challenges" \
  | python3 -c 'import sys,json; print(len(json.load(sys.stdin)))')"
[ "$CHALLENGES" = "5" ] || { echo "FAIL: challenges = $CHALLENGES"; exit 1; }
echo "ok: 5 peer challenges"

TURNS="$(curl -fsS "${AUTH[@]}" "$BASE/v1/organizations/$ORG/investigations/$INV/debate" \
  | python3 -c 'import sys,json; d=json.load(sys.stdin); print(len(d["transcript"]))')"
[ "$TURNS" = "10" ] || { echo "FAIL: debate turns = $TURNS"; exit 1; }
echo "ok: 10 debate turns"

VOTES="$(curl -fsS "${AUTH[@]}" "$BASE/v1/organizations/$ORG/investigations/$INV/votes" \
  | python3 -c 'import sys,json; print(len(json.load(sys.stdin)))')"
[ "$VOTES" = "5" ] || { echo "FAIL: votes = $VOTES"; exit 1; }
echo "ok: 5 ballots"

RECOMMENDATION="$(curl -fsS "${AUTH[@]}" "$BASE/v1/organizations/$ORG/investigations/$INV/report" \
  | python3 -c 'import sys,json; print(json.load(sys.stdin)["recommendation"])')"
[ -n "$RECOMMENDATION" ] || { echo "FAIL: no report recommendation"; exit 1; }
echo "ok: report recommendation = $RECOMMENDATION"

echo ""
echo "SMOKE PASSED — Sofuu backend reached completed with authenticated, owner-scoped API."
