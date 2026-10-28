// Embedded (on-device) entrypoint for the Veliora Android app.
//
// The same gateway/storage/orchestrator as `veliora.js`, but without
// `sofuu.http.createServer`: an Android app has no background libuv pump to
// serve a listening socket while the UI is idle. Instead the host invokes
// `sofuu.veliora.request(...)` through the C-ABI funnel; the funnel drains
// the runtime loop until that request's promise settles.
//
// `sofuu.veliora.events` gives the UI a polling read of the durable event
// store, replacing the SSE stream. `init` is idempotent so an Activity
// recreation or a second screen does not open the storage twice.

import { loadConfig } from "./core/config.js";
import { EventBus } from "./core/events.js";
import { Store } from "./storage/store.js";
import { LLM } from "./agents/llm.js";
import { defineAgents } from "./agents/definitions.js";
import { InvestigationOrchestrator } from "./orchestration/investigation.js";
import { createGateway } from "./server/gateway.js";
import { Authenticator } from "./auth/clerk.js";

let state = null;

function lowerHeaders(value) {
  const out = {};
  if (!value || typeof value !== "object") return out;
  for (const key of Object.keys(value)) {
    out[String(key).toLowerCase()] = String(value[key]);
  }
  return out;
}

function mergeConfig(config, injected) {
  const options = injected || {};
  // Capture the resolved Clerk defaults before Object.assign replaces the
  // whole nested object with the injected subset.
  const baseClerk = config.clerk || {};
  Object.assign(config, options);
  config.clerk = { ...baseClerk, ...(options.clerk || {}) };
  // No listener, no CORS surface, and no heartbeat in embedded mode.
  config.host = "127.0.0.1";
  return config;
}

async function init(options) {
  if (state) return { ready: true, reused: true };

  const config = mergeConfig(await loadConfig(), options);
  const auth = new Authenticator(config);
  await auth.initialize();

  const store = new Store(config.storePath);
  const bus = new EventBus(store, { heartbeat: false });
  if (config.mode === "agent") defineAgents(config);

  const llm = new LLM(config);
  const orchestrator = new InvestigationOrchestrator({ store, bus, llm, config });
  const gateway = createGateway({ store, bus, orchestrator, config, auth });
  orchestrator.resumeActive();

  state = { config, store, bus, auth, orchestrator, gateway };
  globalThis.__veliora = state;

  return {
    ready: true,
    mode: config.mode,
    auth: config.authMode,
    store: config.storePath,
  };
}

// Execute one gateway request without a socket. The gateway's response
// methods are captured into a small JSON envelope the Kotlin layer can
// decode with the same status/body contract Retrofit used.
async function request(input) {
  if (!state) throw new Error("veliora embedded runtime is not initialized");

  const options = input || {};
  const req = {
    method: String(options.method || "GET").toUpperCase(),
    url: String(options.url || "/"),
    headers: lowerHeaders(options.headers),
    body: options.body === undefined || options.body === null ? "" : String(options.body),
  };

  let status = 200;
  let responseHeaders = {};
  const chunks = [];
  const res = {
    writeHead(code, headers) {
      status = Number(code) || 200;
      responseHeaders = { ...(headers || {}) };
      return this;
    },
    setHeader(name, value) {
      responseHeaders[String(name)] = String(value);
      return this;
    },
    write(chunk) {
      if (chunk !== undefined && chunk !== null) chunks.push(String(chunk));
      return true;
    },
    end(chunk) {
      if (chunk !== undefined && chunk !== null) chunks.push(String(chunk));
      return this;
    },
    send(chunk) {
      return this.end(chunk);
    },
  };

  await state.gateway.handle(req, res);
  return { status, headers: responseHeaders, body: chunks.join("") };
}


// Advance the embedded libuv loop for a bounded slice. Android has no
// background pump, so the UI calls this while a pipeline is active; the C
// ABI drains the loop until this promise settles and any timers it wakes.
function tick(input) {
  const options = input || {};
  const ms = Math.max(0, Math.min(2000, Number(options.ms === undefined ? 50 : options.ms) || 0));
  return new Promise((resolve) => setTimeout(() => resolve({ ticked: ms }), ms));
}

// Durable event projection for UI polling. The caller supplies the
// authenticated user id so one local account cannot read another account's
// organization stream by guessing an id.
function events(input) {
  if (!state) throw new Error("veliora embedded runtime is not initialized");
  const options = input || {};
  const orgId = String(options.org_id || "");
  const userId = String(options.user_id || "");
  const since = Number(options.since_seq || 0) || 0;

  const org = state.store.get("orgs", orgId);
  if (!org || org.owner_user_id !== userId) return [];

  return state.store
    .list("events", { ref: orgId })
    .filter((event) => Number(event.seq || 0) > since)
    .sort((a, b) => Number(a.seq || 0) - Number(b.seq || 0))
    .map((event) => ({
      stream: event.stream || "organization",
      stream_id: event.stream_id || orgId,
      seq: Number(event.seq || 0),
      event: event.event_type || "",
      ts: event.ts || "",
      payload: event.payload || {},
    }));
}

function close() {
  if (!state) return false;
  try { state.bus.close(); } catch (_) {}
  try { state.store.close(); } catch (_) {}
  state = null;
  return true;
}

if (!sofuu.veliora) {
  sofuu.veliora = { init, request, events, tick, close };
}
