#!/bin/bash
# Veliora golden-path e2e: create org → investigation → verify state machine + WS.
# Real API, real DB, no mocks. Authless local mode; run only on a trusted instance.
set -euo pipefail

BASE="${BASE:-http://localhost:8080}"
echo "== 1. Health =="
HEALTH=$(curl -fsS "$BASE/health")
[ "$(printf '%s' "$HEALTH" | python3 -c "import sys,json; print(json.load(sys.stdin).get('mode',''))")" = "authless-local" ] || { echo "FAIL: unhealthy gateway: $HEALTH"; exit 1; }
echo "ok: gateway is healthy (authless-local)"

echo "== 2. Create organization =="
ORG=$(curl -s -X POST "$BASE/v1/organizations" \
  -H 'Content-Type: application/json' \
  -d '{"name":"E2E Startup","template":"startup","constitution":{"mission":"Validate the open-source local workflow."}}' \
  | python3 -c "import sys,json; print(json.load(sys.stdin).get('organization_id',''))")
[ -n "$ORG" ] || { echo "FAIL: no org"; exit 1; }
echo "ok: org $ORG"

echo "== 3. Org snapshot =="
WORKERS=$(curl -s "$BASE/v1/organizations/$ORG" \
  | python3 -c "import sys,json; print(len(json.load(sys.stdin).get('workers',[])))")
[ "$WORKERS" = "8" ] || { echo "FAIL: expected 8 workers, got $WORKERS"; exit 1; }
echo "ok: 8 workers materialized"

echo "== 3b. Constitution mission override =="
MISSION=$(curl -s "$BASE/v1/organizations/$ORG/constitution" \
  | python3 -c 'import sys,json; print(json.load(sys.stdin).get("mission",""))')
[ "$MISSION" = "Validate the open-source local workflow." ] || { echo "FAIL: mission = $MISSION"; exit 1; }
echo "ok: mission persisted"

echo "== 4. Create investigation =="
INV=$(curl -s -X POST "$BASE/v1/organizations/$ORG/investigations" \
  -H 'Content-Type: application/json' -d '{"prompt":"Should I build an AI healthcare startup?","depth":"standard"}' \
  | python3 -c "import sys,json; print(json.load(sys.stdin).get('investigation_id',''))")
[ -n "$INV" ] || { echo "FAIL: no investigation"; exit 1; }
echo "ok: investigation $INV"

echo "== 5. Investigation advanced to researching with 5-task plan =="
sleep 1
STATE=$(curl -s "$BASE/v1/organizations/$ORG/investigations/$INV" \
  | python3 -c "import sys,json; d=json.load(sys.stdin); print(d['state'], len(d['plan'].get('tasks') or []))")
echo "$STATE" | grep -q "^researching 5" || { echo "FAIL: state = $STATE"; exit 1; }
echo "ok: $STATE"

echo "== 6. Org status endpoint =="
STATE2=$(curl -s "$BASE/v1/organizations/$ORG/status" \
  | python3 -c "import sys,json; print(json.load(sys.stdin).get('state',''))")
[ "$STATE2" = "active" ] || { echo "FAIL: org state = $STATE2"; exit 1; }
echo "ok: org active"

echo "== 7. Events persisted (event store) =="
COUNT=$(psql -h 127.0.0.1 -U veliora -d veliora -tAc "SELECT COUNT(*) FROM events WHERE organization_id='$ORG'")
[ "$COUNT" -ge 4 ] || { echo "FAIL: only $COUNT events"; exit 1; }
echo "ok: $COUNT events in store"

echo "== 8. WebSocket event stream =="
# Start a WS listener that prints investigation events, then trigger one.
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
(cd "$ROOT/backend/go" && go run ./cmd/wscheck -org "$ORG" > /tmp/wscheck.log 2>&1 &)
sleep 2
curl -s -X POST "$BASE/v1/organizations/$ORG/investigations" \
  -H 'Content-Type: application/json' -d '{"prompt":"WS event check","depth":"quick"}' >/dev/null
sleep 4
pkill -f "wscheck" 2>/dev/null || true
grep -q "investigation.started" /tmp/wscheck.log || { echo "FAIL: no investigation.started in WS"; cat /tmp/wscheck.log; exit 1; }
echo "ok: investigation.started streamed over WS"
grep "investigation" /tmp/wscheck.log | head -4

echo ""
echo "E2E PASSED — full golden path verified against the real backend."
