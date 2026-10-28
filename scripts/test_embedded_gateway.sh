#!/usr/bin/env bash
# Exercise the committed Android gateway bundle through a Sofuu host runtime.
#
# This is the SDK-path test: it evaluates the same veliora-embedded.js that
# ships in the APK and drives the in-process gateway (init -> org -> pipeline
# -> report -> events) without starting an HTTP server.
#
# Usage:
#   SOFUU_BIN=/path/to/sofuu bash scripts/test_embedded_gateway.sh
#
# The binary must expose sofuu.storage and sofuu.crypto (a 0.2.0 build).
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

SOFUU_BIN="${SOFUU_BIN:-sofuu}"
if ! command -v "$SOFUU_BIN" >/dev/null 2>&1; then
    echo "✗ Sofuu binary not found: $SOFUU_BIN" >&2
    exit 1
fi

CAPS="$("$SOFUU_BIN" eval 'console.log(typeof sofuu.storage, typeof sofuu.crypto?.verifyJWT)' 2>/dev/null || true)"
case "$CAPS" in
    "object function") ;;
    *)
        echo "✗ this Sofuu build lacks sofuu.storage/sofuu.crypto: $CAPS" >&2
        echo "  build Sofuu 0.2.0 or newer and set SOFUU_BIN" >&2
        exit 1
        ;;
esac

TMP="$(mktemp -d "${TMPDIR:-/tmp}/veliora-embedded-XXXXXX")"
trap 'rm -rf "$TMP"' EXIT
STORE="$TMP/store"

cat > "$TMP/run.js" <<JS
globalThis.__VELIORA_TEST_STORE__ = "$STORE";
JS
cat >> "$TMP/run.js" <<'JS'
const bundle = await sofuu.fs.readFile(
  "veliora/app/src/main/assets/veliora-embedded.js", "utf8");
eval(bundle);

const init = await sofuu.veliora.init({
  storePath: globalThis.__VELIORA_TEST_STORE__,
  mode: "scripted",
  authMode: "dev",
  stepDelayMs: 1,
  appOrigins: [],
});

const headers = {
  authorization: "Bearer dev:user_test",
  "x-veliora-platform": "android",
  "x-veliora-app-version": "1.0",
  "content-type": "application/json",
};

async function request(method, url, body) {
  return await sofuu.veliora.request({
    method,
    url,
    headers,
    body: body === undefined ? "" : JSON.stringify(body),
  });
}

const created = JSON.parse((await request("POST", "/v1/organizations", {
  name: "Embedded Test",
  template: "startup",
  constitution: { mission: "Prove the SDK runs the gateway in-process." },
})).body);
const orgId = created.organization_id;

const investigation = JSON.parse((await request(
  "POST",
  `/v1/organizations/${orgId}/investigations`,
  { prompt: "Validate the embedded runtime.", depth: "standard" },
)).body);

const liveStatus = JSON.parse((await request(
  "GET",
  `/v1/organizations/${orgId}/status`,
)).body);
if (liveStatus.counts.investigations_active !== 1) {
  throw new Error("status did not report the active investigation");
}
if (!liveStatus.departments.some((department) => department.worker_state !== "idle")) {
  throw new Error("status did not report any working or queued department");
}

let state = "";
for (let i = 0; i < 80; i += 1) {
  await sofuu.veliora.tick({ ms: 100 });
  const current = JSON.parse((await request(
    "GET",
    `/v1/organizations/${orgId}/investigations/${investigation.investigation_id}`,
  )).body);
  state = current.state;
  if (state === "completed") break;
}

const report = await request(
  "GET",
  `/v1/organizations/${orgId}/investigations/${investigation.investigation_id}/report`,
);
if (report.status !== 200) throw new Error("report status " + report.status);

const endpoints = [
  `/v1/organizations/templates`,
  `/v1/organizations/${orgId}`,
  `/v1/organizations/${orgId}/status`,
  `/v1/organizations/${orgId}/constitution`,
  `/v1/organizations/${orgId}/workers`,
  `/v1/organizations/${orgId}/communications`,
  `/v1/organizations/${orgId}/evolution/events`,
  `/v1/organizations/${orgId}/meetings`,
  `/v1/organizations/${orgId}/investigations`,
  `/v1/organizations/${orgId}/investigations/${investigation.investigation_id}`,
  `/v1/organizations/${orgId}/investigations/${investigation.investigation_id}/evidence`,
];
for (const path of endpoints) {
  const response = await request("GET", path);
  if (response.status !== 200) {
    throw new Error("GET " + path + " returned " + response.status + ": " + response.body);
  }
}

const events = sofuu.veliora.events({
  org_id: orgId,
  user_id: "user_test",
  since_seq: 0,
});

console.log("EMBEDDED_RESULT " + JSON.stringify({
  init: init.ready === true,
  mode: init.mode,
  state,
  reportStatus: report.status,
  eventCount: events.length,
  lastEvent: events.length ? events[events.length - 1].event : "",
}));
JS

OUT="$(mktemp "${TMPDIR:-/tmp}/veliora-embedded-out.XXXXXX")"
trap 'rm -rf "$TMP" "$OUT"' EXIT

"$SOFUU_BIN" run "$TMP/run.js" | tee "$OUT"
grep -q '"init":true' "$OUT"
grep -q '"state":"completed"' "$OUT"
grep -q '"reportStatus":200' "$OUT"
grep -q '"lastEvent":"investigation.report_ready"' "$OUT"

echo "✓ embedded gateway completed the scripted pipeline"
