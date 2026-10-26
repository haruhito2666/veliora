const DEFAULTS = {
  port: 8080,
  host: "127.0.0.1",
  storePath: ".sofuu/veliora-store",
  mode: "scripted",
  provider: "",
  model: "",
  confidenceThreshold: 0.72,
  maxResearchCycles: 3,
  stepDelayMs: 25,
  fallbackToScripted: true,
  webSearch: false,
};

async function readConfigFile() {
  for (const path of ["backend/sofuu/veliora.json", "veliora.json"]) {
    try {
      const text = await sofuu.fs.readFile(path, "utf8");
      return JSON.parse(text);
    } catch (_) {
      // Try the next location.
    }
  }
  return {};
}

function number(value, fallback) {
  const parsed = Number(value);
  return Number.isFinite(parsed) ? parsed : fallback;
}

function bool(value, fallback) {
  if (value === undefined || value === null || value === "") return fallback;
  return value === true || value === "1" || value === "true";
}

export async function loadConfig() {
  const file = await readConfigFile();
  const env = process.env || {};
  const config = { ...DEFAULTS, ...file };

  if (env.VELIORA_PORT) config.port = number(env.VELIORA_PORT, config.port);
  if (env.VELIORA_HOST) config.host = env.VELIORA_HOST;
  if (env.VELIORA_STORE) config.storePath = env.VELIORA_STORE;
  if (env.VELIORA_MODE) config.mode = env.VELIORA_MODE;
  if (env.VELIORA_PROVIDER) config.provider = env.VELIORA_PROVIDER;
  if (env.VELIORA_MODEL) config.model = env.VELIORA_MODEL;
  if (env.SOFUU_PROVIDER && !config.provider) config.provider = env.SOFUU_PROVIDER;
  if (env.SOFUU_MODEL && !config.model) config.model = env.SOFUU_MODEL;
  if (env.VELIORA_CONFIDENCE_THRESHOLD) {
    config.confidenceThreshold = number(env.VELIORA_CONFIDENCE_THRESHOLD, config.confidenceThreshold);
  }
  if (env.VELIORA_MAX_CYCLES) {
    config.maxResearchCycles = number(env.VELIORA_MAX_CYCLES, config.maxResearchCycles);
  }
  if (env.VELIORA_STEP_DELAY_MS) {
    config.stepDelayMs = number(env.VELIORA_STEP_DELAY_MS, config.stepDelayMs);
  }
  if (env.VELIORA_FALLBACK_SCRIPTED) {
    config.fallbackToScripted = bool(env.VELIORA_FALLBACK_SCRIPTED, config.fallbackToScripted);
  }
  if (env.VELIORA_WEB_SEARCH) {
    config.webSearch = bool(env.VELIORA_WEB_SEARCH, config.webSearch);
  }

  config.port = Math.trunc(number(config.port, 8080));
  config.maxResearchCycles = Math.max(1, Math.trunc(number(config.maxResearchCycles, 3)));
  config.confidenceThreshold = number(config.confidenceThreshold, 0.72);
  config.stepDelayMs = Math.max(0, Math.trunc(number(config.stepDelayMs, 0)));
  return config;
}
