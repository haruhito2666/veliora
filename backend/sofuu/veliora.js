import { loadConfig } from "./core/config.js";
import { EventBus } from "./core/events.js";
import { Store } from "./storage/store.js";
import { LLM } from "./agents/llm.js";
import { defineAgents } from "./agents/definitions.js";
import { InvestigationOrchestrator } from "./orchestration/investigation.js";
import { createGateway } from "./server/gateway.js";
import { Authenticator } from "./auth/clerk.js";

async function main() {
  if (!sofuu || !sofuu.http || typeof sofuu.http.createServer !== "function") {
    throw new Error("this backend requires the Sofuu runtime (sofuu.http.createServer)");
  }
  if (!sofuu.storage || typeof sofuu.storage.open !== "function") {
    throw new Error("this backend requires a Sofuu build with sofuu.storage");
  }

  const config = await loadConfig();
  const auth = new Authenticator(config);
  await auth.initialize();

  const store = new Store(config.storePath);
  const bus = new EventBus(store);

  if (config.mode === "agent") {
    defineAgents();
  }

  const llm = new LLM(config);
  const orchestrator = new InvestigationOrchestrator({ store, bus, llm, config });
  const gateway = createGateway({ store, bus, orchestrator, config, auth });
  orchestrator.resumeActive();

  const server = sofuu.http.createServer((req, res) => gateway.handle(req, res));
  server.listen(config.port, config.host);

  // Keep the runtime objects reachable. QuickJS may collect a local server
  // handle once main() returns, which closes the listening socket.
  globalThis.__veliora = { config, store, bus, auth, server };

  console.log(
    `veliora-sofuu listening on http://${config.host}:${config.port} ` +
      `(mode=${config.mode}, auth=${config.authMode}, store=${config.storePath})`,
  );
}

main().catch((error) => {
  console.error("veliora-sofuu failed to start:", error.message || error);
  process.exit(1);
});
