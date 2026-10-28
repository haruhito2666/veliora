import { STARTUP_TEMPLATE } from "../core/templates.js";

// Optional Sofuu agent definitions. The default `scripted` mode does not
// define agents, so the backend runs without a provider or model.
export function defineAgents(config = {}) {
  for (const department of STARTUP_TEMPLATE.departments) {
    try {
      const definition = {
        name: `veliora_${department.id}`,
        system: [
          `You are the ${department.role} in the ${department.name}.`,
          department.charter,
          "Return structured JSON when the task asks for it. Preserve dissent.",
        ].join("\n"),
        tools: ["web"],
        memory: "agent",
        budget: { maxSteps: 8, maxWallMs: 120000 },
      };
      // The agent runtime needs an explicit provider/model when the embedder
      // selected agent mode; per-call options do not reach this path.
      if (config.provider) definition.provider = config.provider;
      if (config.model) definition.model = config.model;
      if (config.baseUrl) definition.base_url = config.baseUrl;
      sofuu.agent.define(definition);
    } catch (error) {
      console.warn(`could not define Sofuu agent veliora_${department.id}: ${error.message}`);
    }
  }
}
