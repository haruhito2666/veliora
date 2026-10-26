#!/bin/bash
# Smoke-test the Sofuu backend end to end.
# Requires: bash, curl, python3, and a Sofuu build with sofuu.storage.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../../.." && pwd)"
cd "$ROOT"

SOFUU_BIN="${SOFUU_BIN:-sofuu}"
PORT="${VELIORA_TEST_PORT:-18080}"
STORE="$(mktemp -d)"
LOG="$(mktemp)"
BASE="http://127.0.0.1:$PORT"

cleanup() {
  if [ -n "${PID:-}" ]; then
    kill "$PID" 2>/dev/null || true
    wait "$PID" 2>/dev/null || true
  fi
  rm -rf "$STORE" "$LOG"
}
trap cleanup EXIT

echo "== start backend (sofuu=$SOFUU_BIN, port=$PORT) =="
VELIORA_PORT="$PORT" VELIORA_STORE="$STORE" VELIORA_STEP_DELAY_MS=0 \
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
echo "ok: $HEALTH"

echo "== create organization =="
ORG="$(curl -fsS -X POST "$BASE/v1/organizations" \
  -H 'Content-Type: application/json' \
  -d '{"name":"Sofuu E2E","template":"startup","constitution":{"mission":"Validate the Sofuu backend."}}' \
  | python3 -c 'import sys,json; print(json.load(sys.stdin)["organization_id"])')"
echo "ok: org $ORG"

WORKERS="$(curl -fsS "$BASE/v1/organizations/$ORG" \
  | python3 -c 'import sys,json; print(len(json.load(sys.stdin)["workers"]))')"
[ "$WORKERS" = "8" ] || { echo "FAIL: expected 8 workers, got $WORKERS"; exit 1; }
echo "ok: 8 workers"

MISSION="$(curl -fsS "$BASE/v1/organizations/$ORG/constitution" \
  | python3 -c 'import sys,json; print(json.load(sys.stdin)["mission"])')"
[ "$MISSION" = "Validate the Sofuu backend." ] || { echo "FAIL: mission = $MISSION"; exit 1; }
echo "ok: mission override persisted"

echo "== create investigation =="
INV="$(curl -fsS -X POST "$BASE/v1/organizations/$ORG/investigations" \
  -H 'Content-Type: application/json' \
  -d '{"prompt":"Should I build an AI healthcare startup?","depth":"standard"}' \
  | python3 -c 'import sys,json; print(json.load(sys.stdin)["investigation_id"])')"
echo "ok: investigation $INV"

echo "== wait for completion =="
STATE=""
for _ in $(seq 1 150); do
  STATE="$(curl -fsS "$BASE/v1/organizations/$ORG/investigations/$INV" \
    | python3 -c 'import sys,json; d=json.load(sys.stdin); print(d["state"], d["cycle_count"], round(float(d.get("confidence") or 0), 2))')"
  case "$STATE" in
    completed\ *) break ;;
  esac
  sleep 0.2
done
echo "$STATE" | grep -q '^completed 1 ' || { echo "FAIL: state = $STATE"; tail -40 "$LOG"; exit 1; }
echo "ok: $STATE"

EVIDENCE="$(curl -fsS "$BASE/v1/organizations/$ORG/investigations/$INV/evidence" \
  | python3 -c 'import sys,json; print(len(json.load(sys.stdin)))')"
[ "$EVIDENCE" = "5" ] || { echo "FAIL: evidence = $EVIDENCE"; exit 1; }
echo "ok: 5 evidence records"

CHALLENGES="$(curl -fsS "$BASE/v1/organizations/$ORG/investigations/$INV/challenges" \
  | python3 -c 'import sys,json; print(len(json.load(sys.stdin)))')"
[ "$CHALLENGES" = "5" ] || { echo "FAIL: challenges = $CHALLENGES"; exit 1; }
echo "ok: 5 peer challenges"

TURNS="$(curl -fsS "$BASE/v1/organizations/$ORG/investigations/$INV/debate" \
  | python3 -c 'import sys,json; d=json.load(sys.stdin); print(len(d["transcript"]))')"
[ "$TURNS" = "10" ] || { echo "FAIL: debate turns = $TURNS"; exit 1; }
echo "ok: 10 debate turns"

VOTES="$(curl -fsS "$BASE/v1/organizations/$ORG/investigations/$INV/votes" \
  | python3 -c 'import sys,json; print(len(json.load(sys.stdin)))')"
[ "$VOTES" = "5" ] || { echo "FAIL: votes = $VOTES"; exit 1; }
echo "ok: 5 ballots"

RECOMMENDATION="$(curl -fsS "$BASE/v1/organizations/$ORG/investigations/$INV/report" \
  | python3 -c 'import sys,json; print(json.load(sys.stdin)["recommendation"])')"
[ -n "$RECOMMENDATION" ] || { echo "FAIL: no report recommendation"; exit 1; }
echo "ok: report recommendation = $RECOMMENDATION"

echo ""
echo "SMOKE PASSED — Sofuu backend reached completed."
