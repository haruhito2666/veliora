const DEFAULTS = {
  port: 8080,
  host: "127.0.0.1",
  storePath: ".sofuu/veliora-store",
  mode: "scripted",
  provider: "",
  model: "",
  baseUrl: "",
  confidenceThreshold: 0.72,
  maxResearchCycles: 3,
  stepDelayMs: 25,
  fallbackToScripted: true,
  // Authentication is fail-closed: Clerk is the default. `dev` is an
  // explicit local-test mode and still requires a bearer token.
  authMode: "clerk",
  appOrigins: [
    "https://veliora-app.rikuhana.com",
    "http://localhost:3000",
    "http://127.0.0.1:3000",
  ],
  devUser: "user_dev_local",
  clerk: {
    issuer: "https://clerk.veliora-app.rikuhana.com",
    jwksUrl: "https://clerk.veliora-app.rikuhana.com/.well-known/jwks.json",
    authorizedParties: [
      "https://veliora-app.rikuhana.com",
      "http://localhost",
      "http://localhost:3000",
      "http://127.0.0.1:3000",
    ],
    audience: "",
    clockSkewSeconds: 5,
    jwksCacheMs: 60 * 60 * 1000,
    jwksTimeoutMs: 10000,
  },
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

function list(value, fallback) {
  if (value === undefined || value === null || value === "") return fallback;
  if (Array.isArray(value)) return value.map(String).map((item) => item.trim()).filter(Boolean);
  return String(value)
    .split(",")
    .map((item) => item.trim())
    .filter(Boolean);
}

export async function loadConfig() {
  const file = await readConfigFile();
  const env = (typeof process !== "undefined" && process.env) || {};
  const config = { ...DEFAULTS, ...file };
  config.clerk = { ...DEFAULTS.clerk, ...(file.clerk || {}) };
  config.baseUrl = String(file.baseUrl || file.base_url || config.baseUrl || "");
  config.appOrigins = list(file.appOrigins || config.appOrigins, DEFAULTS.appOrigins);
  config.clerk.authorizedParties = list(
    config.clerk.authorizedParties,
    DEFAULTS.clerk.authorizedParties,
  );

  if (env.VELIORA_PORT) config.port = number(env.VELIORA_PORT, config.port);
  if (env.VELIORA_HOST) config.host = env.VELIORA_HOST;
  if (env.VELIORA_STORE) config.storePath = env.VELIORA_STORE;
  if (env.VELIORA_MODE) config.mode = env.VELIORA_MODE;
  if (env.VELIORA_PROVIDER) config.provider = env.VELIORA_PROVIDER;
  if (env.VELIORA_MODEL) config.model = env.VELIORA_MODEL;
  if (env.VELIORA_BASE_URL) config.baseUrl = env.VELIORA_BASE_URL;
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

  if (env.VELIORA_AUTH_MODE) config.authMode = String(env.VELIORA_AUTH_MODE).toLowerCase();
  if (env.VELIORA_APP_ORIGINS) config.appOrigins = list(env.VELIORA_APP_ORIGINS, config.appOrigins);
  if (env.VELIORA_DEV_USER) config.devUser = String(env.VELIORA_DEV_USER);
  if (env.CLERK_ISSUER) config.clerk.issuer = String(env.CLERK_ISSUER);
  if (env.CLERK_JWKS_URL) config.clerk.jwksUrl = String(env.CLERK_JWKS_URL);
  if (env.CLERK_AUTHORIZED_PARTIES) {
    config.clerk.authorizedParties = list(
      env.CLERK_AUTHORIZED_PARTIES,
      config.clerk.authorizedParties,
    );
  }
  if (env.CLERK_AUDIENCE) config.clerk.audience = String(env.CLERK_AUDIENCE);
  if (env.CLERK_CLOCK_SKEW_SECONDS) {
    config.clerk.clockSkewSeconds = number(
      env.CLERK_CLOCK_SKEW_SECONDS,
      config.clerk.clockSkewSeconds,
    );
  }
  if (env.CLERK_JWKS_CACHE_MS) {
    config.clerk.jwksCacheMs = number(env.CLERK_JWKS_CACHE_MS, config.clerk.jwksCacheMs);
  }
  if (env.CLERK_JWKS_TIMEOUT_MS) {
    config.clerk.jwksTimeoutMs = number(env.CLERK_JWKS_TIMEOUT_MS, config.clerk.jwksTimeoutMs);
  }

  config.port = Math.trunc(number(config.port, 8080));
  config.maxResearchCycles = Math.max(1, Math.trunc(number(config.maxResearchCycles, 3)));
  config.confidenceThreshold = number(config.confidenceThreshold, 0.72);
  config.stepDelayMs = Math.max(0, Math.trunc(number(config.stepDelayMs, 0)));
  config.authMode = String(config.authMode || "clerk").toLowerCase();
  if (!["clerk", "dev"].includes(config.authMode)) {
    throw new Error(`VELIORA_AUTH_MODE must be "clerk" or "dev", got ${config.authMode}`);
  }
  config.clerk.clockSkewSeconds = Math.max(0, Math.trunc(number(config.clerk.clockSkewSeconds, 5)));
  config.clerk.jwksCacheMs = Math.max(1, Math.trunc(number(config.clerk.jwksCacheMs, 3600000)));
  config.clerk.jwksTimeoutMs = Math.max(1000, Math.trunc(number(config.clerk.jwksTimeoutMs, 10000)));
  if (config.authMode === "clerk" && !config.clerk.jwksUrl) {
    throw new Error("CLERK_JWKS_URL is required when VELIORA_AUTH_MODE=clerk");
  }
  return config;
}
