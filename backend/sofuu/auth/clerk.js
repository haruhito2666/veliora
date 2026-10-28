// Reference auth adapter: Clerk session-token verification for the Veliora API.
//
// Clerk is the reference provider, not a project requirement. The gateway
// contract is deliberately small: verify the bearer token and return a stable
// `sub` plus the token claims. A fork can replace this module with another
// provider and keep the routes, ownership model, and SSE token rules unchanged.
//
// Clerk's frontend sends an RS256 JWT in `Authorization: Bearer <token>`.
// The token is verified locally against the project's public JWKS using the
// runtime primitive `sofuu.crypto.verifyJWT`; no Clerk secret key is needed
// and no network round-trip happens per request.
//
// Modes:
//   clerk (default) — verify against the configured issuer/JWKS; every request
//                     needs a token. The mode name is the reference adapter's,
//                     not a product-wide auth requirement.
//   dev             — local/tests only. Accepts `Bearer dev:<user_id>`.
//
// Dev mode is deliberately explicit: it is never enabled by simply omitting
// auth configuration, and it logs a warning at startup.

const DEV_PREFIX = "dev:";

export class AuthError extends Error {
  constructor(status, code, message) {
    super(message);
    this.name = "AuthError";
    this.status = status;
    this.code = code;
  }
}

function header(req, name) {
  const headers = req && req.headers;
  if (!headers) return "";
  return String(headers[name.toLowerCase()] || "");
}

function bearerToken(req) {
  const value = header(req, "authorization");
  const match = /^Bearer\s+(.+)$/i.exec(value.trim());
  return match ? match[1].trim() : "";
}

function queryToken(query) {
  if (!query || !query.token) return "";
  return String(query.token).trim();
}

function nonEmpty(value) {
  return typeof value === "string" && value.trim() !== "";
}

export class Authenticator {
  constructor(config) {
    this.config = config;
    this.mode = config.authMode;
    this.jwks = null;
    this.jwksAt = 0;
    this.lastRefreshAt = 0;
    this.loading = null;
  }

  async initialize() {
    if (this.mode === "dev") {
      console.warn(
        "veliora auth: DEV MODE — accepting 'Authorization: Bearer dev:<user_id>'. " +
          "Never run this mode on a public deployment.",
      );
      return;
    }
    if (!sofuu.crypto || typeof sofuu.crypto.verifyJWT !== "function") {
      throw new Error(
        "VELIORA_AUTH_MODE=clerk requires a Sofuu build with sofuu.crypto " +
          "(see backend/sofuu/SOFUU-GAPS.md)",
      );
    }
    if (!nonEmpty(this.config.clerk.issuer)) {
      console.warn("veliora auth: CLERK_ISSUER is empty; the JWT `iss` claim will not be checked");
    }
    if (this.config.clerk.authorizedParties.length === 0) {
      console.warn(
        "veliora auth: CLERK_AUTHORIZED_PARTIES is empty; a token that includes an `azp` claim will be rejected",
      );
    }
    try {
      await this._ensureJWKS();
      console.log(
        `veliora auth: clerk mode ready (issuer=${this.config.clerk.issuer || "unchecked"}, ` +
          `jwks=${this.config.clerk.jwksUrl}, keys=${(this.jwks.keys || []).length})`,
      );
    } catch (error) {
      // A temporary JWKS outage must not make the process unstartable. The
      // first authenticated request retries; until then it gets 503.
      console.warn(
        `veliora auth: JWKS not loaded at startup: ${error.message}. ` +
          "Check CLERK_JWKS_URL and that the Clerk Frontend API domain resolves.",
      );
    }
  }

  async authenticate(req, options = {}) {
    let token = bearerToken(req);
    if (!token && options.allowQueryToken) token = queryToken(options.query);
    if (this.mode === "dev") return this._devUser(token);

    if (!token) {
      throw new AuthError(401, "unauthorized", "missing Authorization: Bearer token");
    }
    if (!sofuu.crypto || typeof sofuu.crypto.verifyJWT !== "function") {
      throw new AuthError(503, "auth_unavailable", "this Sofuu build lacks sofuu.crypto");
    }

    await this._ensureJWKS();
    let result = this._verify(token);
    if (!result.ok && result.error && result.error.code === "unknown_kid") {
      // Clerk rotates signing keys. Refresh once, but do not let a stream of
      // unknown kids hammer the JWKS endpoint.
      try {
        await this._ensureJWKS(true);
        result = this._verify(token);
      } catch (_) {
        // Keep the original unknown_kid error.
      }
    }
    if (!result.ok) {
      const code = (result.error && result.error.code) || "invalid_token";
      const message = (result.error && result.error.message) || "token verification failed";
      throw new AuthError(401, code, message);
    }

    const claims = result.payload || {};
    const userId = String(claims.sub || "");
    if (!userId) {
      throw new AuthError(401, "missing_sub", "token is missing a subject");
    }
    const exp = Number(claims.exp);
    return {
      user_id: userId,
      session_id: claims.sid ? String(claims.sid) : "",
      // Milliseconds; SSE connections use this to close when the token
      // expires instead of outliving the credential that opened them.
      expires_at: Number.isFinite(exp) ? exp * 1000 : 0,
      claims,
    };
  }

  _verify(token) {
    const clerk = this.config.clerk;
    return sofuu.crypto.verifyJWT(token, this.jwks, {
      issuer: clerk.issuer || undefined,
      authorizedParties: clerk.authorizedParties,
      audience: clerk.audience || undefined,
      clockSkewSeconds: clerk.clockSkewSeconds,
    });
  }

  _devUser(token) {
    if (!token) {
      throw new AuthError(401, "unauthorized", "missing Authorization: Bearer dev:<user_id>");
    }
    let userId = "";
    if (token === "dev") {
      userId = this.config.devUser;
    } else if (token.startsWith(DEV_PREFIX)) {
      userId = token.slice(DEV_PREFIX.length).trim();
    }
    if (!userId) {
      throw new AuthError(
        401,
        "unauthorized",
        "dev auth expects 'Authorization: Bearer dev:<user_id>'",
      );
    }
    return {
      user_id: userId,
      session_id: "",
      expires_at: (Math.floor(Date.now() / 1000) + 3600) * 1000,
      claims: {
        sub: userId,
        iss: "veliora-dev",
        azp: "veliora-dev",
        exp: Math.floor(Date.now() / 1000) + 3600,
      },
    };
  }

  async _ensureJWKS(force = false) {
    const now = Date.now();
    const fresh = this.jwks && now - this.jwksAt < this.config.clerk.jwksCacheMs;
    if (!force && fresh) return this.jwks;
    if (force && this.jwks && now - this.lastRefreshAt < 30000) return this.jwks;
    if (this.loading) return this.loading;

    this.loading = this._loadJWKS();
    try {
      return await this.loading;
    } finally {
      this.loading = null;
    }
  }

  async _loadJWKS() {
    const url = this.config.clerk.jwksUrl;
    if (!url) throw new AuthError(503, "auth_unavailable", "CLERK_JWKS_URL is not configured");

    let timer = null;
    const timeout = new Promise((_, reject) => {
      timer = setTimeout(
        () => reject(new Error(`JWKS fetch timed out after ${this.config.clerk.jwksTimeoutMs} ms`)),
        this.config.clerk.jwksTimeoutMs,
      );
    });

    try {
      const response = await Promise.race([
        fetch(url, { headers: { Accept: "application/json" } }),
        timeout,
      ]);
      if (!response || !response.ok) {
        throw new Error(`JWKS request failed with HTTP ${response ? response.status : "?"}`);
      }
      const data = await response.json();
      if (!data || !Array.isArray(data.keys) || data.keys.length === 0) {
        throw new Error("JWKS response contains no keys");
      }
      this.jwks = data;
      this.jwksAt = Date.now();
      this.lastRefreshAt = this.jwksAt;
      return data;
    } catch (error) {
      if (this.jwks) {
        console.warn(
          `veliora auth: JWKS refresh failed (${error.message}); using the cached key set`,
        );
        this.jwksAt = Date.now() - Math.floor(this.config.clerk.jwksCacheMs / 2);
        return this.jwks;
      }
      throw new AuthError(
        503,
        "auth_unavailable",
        `cannot load Clerk JWKS: ${error.message}`,
      );
    } finally {
      if (timer !== null) clearTimeout(timer);
    }
  }
}
