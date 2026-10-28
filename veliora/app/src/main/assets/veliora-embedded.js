(() => {
  // backend/sofuu/core/config.js
  var DEFAULTS = {
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
    // Authentication is fail-closed. The reference adapter verifies Clerk
    // session tokens; a fork can replace auth/clerk.js with another provider.
    // `dev` is an explicit local-test mode and still requires a bearer token.
    // No Clerk instance, issuer, or JWKS is baked into these defaults: every
    // deployment supplies its own via veliora.json or environment variables.
    authMode: "clerk",
    appOrigins: ["http://localhost:3000", "http://127.0.0.1:3000"],
    devUser: "user_dev_local",
    clerk: {
      issuer: "",
      jwksUrl: "",
      authorizedParties: [
        "http://localhost",
        "http://localhost:3000",
        "http://127.0.0.1:3000"
      ],
      audience: "",
      clockSkewSeconds: 5,
      jwksCacheMs: 60 * 60 * 1e3,
      jwksTimeoutMs: 1e4
    }
  };
  async function readConfigFile() {
    for (const path of ["backend/sofuu/veliora.json", "veliora.json"]) {
      try {
        const text = await sofuu.fs.readFile(path, "utf8");
        return JSON.parse(text);
      } catch (_) {
      }
    }
    return {};
  }
  function number(value, fallback) {
    const parsed = Number(value);
    return Number.isFinite(parsed) ? parsed : fallback;
  }
  function bool(value, fallback) {
    if (value === void 0 || value === null || value === "") return fallback;
    return value === true || value === "1" || value === "true";
  }
  function list(value, fallback) {
    if (value === void 0 || value === null || value === "") return fallback;
    if (Array.isArray(value)) return value.map(String).map((item) => item.trim()).filter(Boolean);
    return String(value).split(",").map((item) => item.trim()).filter(Boolean);
  }
  async function loadConfig(overrides = {}) {
    const file = await readConfigFile();
    const env = typeof process !== "undefined" && process.env || {};
    const config = { ...DEFAULTS, ...file };
    config.clerk = { ...DEFAULTS.clerk, ...file.clerk || {} };
    config.baseUrl = String(file.baseUrl || file.base_url || config.baseUrl || "");
    config.appOrigins = list(file.appOrigins || config.appOrigins, DEFAULTS.appOrigins);
    config.clerk.authorizedParties = list(
      config.clerk.authorizedParties,
      DEFAULTS.clerk.authorizedParties
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
        config.clerk.authorizedParties
      );
    }
    if (env.CLERK_AUDIENCE) config.clerk.audience = String(env.CLERK_AUDIENCE);
    if (env.CLERK_CLOCK_SKEW_SECONDS) {
      config.clerk.clockSkewSeconds = number(
        env.CLERK_CLOCK_SKEW_SECONDS,
        config.clerk.clockSkewSeconds
      );
    }
    if (env.CLERK_JWKS_CACHE_MS) {
      config.clerk.jwksCacheMs = number(env.CLERK_JWKS_CACHE_MS, config.clerk.jwksCacheMs);
    }
    if (env.CLERK_JWKS_TIMEOUT_MS) {
      config.clerk.jwksTimeoutMs = number(env.CLERK_JWKS_TIMEOUT_MS, config.clerk.jwksTimeoutMs);
    }
    if (overrides && typeof overrides === "object") {
      const baseClerk = config.clerk;
      Object.assign(config, overrides);
      config.clerk = { ...baseClerk, ...overrides.clerk || {} };
      config.appOrigins = list(config.appOrigins, DEFAULTS.appOrigins);
      config.clerk.authorizedParties = list(
        config.clerk.authorizedParties,
        DEFAULTS.clerk.authorizedParties
      );
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
    config.clerk.jwksCacheMs = Math.max(1, Math.trunc(number(config.clerk.jwksCacheMs, 36e5)));
    config.clerk.jwksTimeoutMs = Math.max(1e3, Math.trunc(number(config.clerk.jwksTimeoutMs, 1e4)));
    if (config.authMode === "clerk" && !config.clerk.jwksUrl) {
      throw new Error(
        "CLERK_JWKS_URL is required when VELIORA_AUTH_MODE=clerk; set your auth provider's JWKS URL or use VELIORA_AUTH_MODE=dev for local tests"
      );
    }
    return config;
  }

  // backend/sofuu/core/ids.js
  var counter = 0;
  function newId(prefix) {
    counter += 1;
    const time = Date.now().toString(36);
    const count = counter.toString(36);
    const noise = Math.random().toString(36).slice(2, 8);
    return `${prefix}_${time}${count}${noise}`;
  }
  function nowISO() {
    return (/* @__PURE__ */ new Date()).toISOString();
  }
  function sleep(ms) {
    if (!ms) return Promise.resolve();
    return new Promise((resolve) => setTimeout(resolve, ms));
  }

  // backend/sofuu/core/events.js
  var EventBus = class {
    constructor(store, options = {}) {
      this.store = store;
      this.clients = /* @__PURE__ */ new Set();
      this.sequences = /* @__PURE__ */ new Map();
      this.heartbeat = options.heartbeat === false ? null : setInterval(() => this._heartbeat(), 15e3);
    }
    subscribe(orgID, invID, res, cors = {}, expiresAt = 0) {
      res.writeHead(200, {
        "Content-Type": "text/event-stream",
        "Cache-Control": "no-cache",
        "Connection": "keep-alive",
        "X-Accel-Buffering": "no",
        ...cors
      });
      res.write("retry: 3000\n\n");
      res.write(`: connected ${nowISO()}

`);
      const client = { orgID, invID: invID || null, res, expiryTimer: null };
      const delay = Number.isFinite(expiresAt) && expiresAt > 0 ? expiresAt - Date.now() : 0;
      if (delay > 0 && delay <= 2147483647) {
        client.expiryTimer = setTimeout(() => {
          try {
            res.end();
          } catch (_) {
          }
          this.unsubscribe(client);
        }, delay);
      }
      this.clients.add(client);
      return client;
    }
    unsubscribe(client) {
      if (client && client.expiryTimer) clearTimeout(client.expiryTimer);
      this.clients.delete(client);
    }
    publish(orgID, eventType, payload, options = {}) {
      const stream = options.stream || "organization";
      const streamID = options.streamID || orgID;
      const seq = (this.sequences.get(orgID) || 0) + 1;
      this.sequences.set(orgID, seq);
      const envelope = {
        event_id: newId("evt"),
        organization_id: orgID,
        event_type: eventType,
        stream,
        stream_id: streamID,
        seq,
        ts: nowISO(),
        payload: payload || {}
      };
      this.store.put("events", envelope.event_id, envelope, orgID);
      const frame = {
        stream,
        stream_id: streamID,
        seq,
        event: eventType,
        ts: envelope.ts,
        payload: envelope.payload
      };
      const wire = `data: ${JSON.stringify(frame)}

`;
      for (const client of Array.from(this.clients)) {
        if (client.orgID !== orgID) continue;
        if (client.invID && payload && payload.investigation_id && payload.investigation_id !== client.invID) {
          continue;
        }
        try {
          client.res.write(wire);
        } catch (_) {
          this.unsubscribe(client);
        }
      }
      return envelope;
    }
    _heartbeat() {
      for (const client of Array.from(this.clients)) {
        try {
          client.res.write(`: heartbeat ${nowISO()}

`);
        } catch (_) {
          this.unsubscribe(client);
        }
      }
    }
    close() {
      if (this.heartbeat) clearInterval(this.heartbeat);
      this.heartbeat = null;
      for (const client of Array.from(this.clients)) {
        if (client.expiryTimer) clearTimeout(client.expiryTimer);
        try {
          client.res.end();
        } catch (_) {
        }
      }
      this.clients.clear();
    }
  };

  // backend/sofuu/storage/store.js
  var Store = class {
    constructor(path) {
      this.path = path;
      this.db = sofuu.storage.open(path);
      this.closed = false;
    }
    _insert(collection, key, value, ref) {
      this.db.insert(collection, {
        key: String(key),
        ref: String(ref || ""),
        payload: JSON.stringify(value),
        created_at: Date.now()
      });
      try {
        this.db.flush();
      } catch (_) {
      }
    }
    put(collection, key, value, ref = "") {
      this._insert(collection, key, value, ref);
      return value;
    }
    append(collection, value, ref = "", key = null) {
      const id = key || value.id || value.event_id || newId("row");
      this._insert(collection, id, value, ref);
      return value;
    }
    get(collection, key) {
      try {
        const rows = this.db.query(collection, {
          filterCol: "key",
          filterVal: String(key),
          limit: 1,
          orderDesc: true
        });
        if (!rows || rows.length === 0) return null;
        return JSON.parse(rows[0].payload);
      } catch (_) {
        return null;
      }
    }
    list(collection, options = {}) {
      const { ref = null, limit = 1e4 } = options;
      try {
        const query = { limit, orderDesc: true };
        if (ref !== null) {
          query.filterCol = "ref";
          query.filterVal = String(ref);
        }
        const rows = this.db.query(collection, query) || [];
        const seen = /* @__PURE__ */ new Set();
        const out = [];
        for (const row of rows) {
          if (seen.has(row.key)) continue;
          seen.add(row.key);
          try {
            out.push(JSON.parse(row.payload));
          } catch (_) {
          }
        }
        return out;
      } catch (_) {
        return [];
      }
    }
    count(collection, options = {}) {
      return this.list(collection, options).length;
    }
    close() {
      if (this.closed) return;
      try {
        this.db.flush();
        this.db.close();
      } catch (_) {
      }
      this.closed = true;
    }
  };

  // backend/sofuu/core/templates.js
  var STARTUP_TEMPLATE = {
    id: "startup",
    name: "Startup Society",
    description: "Decides whether to build a venture \u2014 market, feasibility, viability, and compliance.",
    departments: [
      {
        id: "ceo_office",
        name: "CEO Office",
        role: "CEO",
        charter: "Coordinate the investigation and synthesize the board recommendation.",
        roles: [{ role: "CEO", count: 1 }]
      },
      {
        id: "research",
        name: "Research Division",
        role: "RESEARCHER",
        charter: "Establish empirical truth about the market, competitors, and evidence base.",
        roles: [{ role: "RESEARCHER", count: 2 }]
      },
      {
        id: "engineering",
        name: "Engineering",
        role: "ENGINEER",
        charter: "Assess technical feasibility, build effort, and architectural risk.",
        roles: [{ role: "ENGINEER", count: 2 }]
      },
      {
        id: "finance",
        name: "Finance",
        role: "FINANCE_ANALYST",
        charter: "Model costs, revenue, runway, and return; keep the org solvent.",
        roles: [{ role: "FINANCE_ANALYST", count: 1 }]
      },
      {
        id: "marketing",
        name: "Marketing",
        role: "MARKETER",
        charter: "Establish demand, willingness to pay, and go-to-market viability.",
        roles: [{ role: "MARKETER", count: 1 }]
      },
      {
        id: "legal",
        name: "Legal",
        role: "LEGAL_ADVISOR",
        charter: "Keep the organization compliant; flag regulatory and legal risk.",
        roles: [{ role: "LEGAL_ADVISOR", count: 1 }]
      }
    ],
    dna: {
      innovation: 0.7,
      risk: 0.6,
      hierarchy: 0.5,
      accuracy: 0.8,
      speed: 0.6,
      transparency: 0.75,
      creativity: 0.65,
      long_term_thinking: 0.6,
      security: 0.55
    },
    constitution: {
      mission: "Investigate and decide on startup viability with evidence, dissent, and a board-level recommendation.",
      principles: [
        "Evidence before opinion.",
        "Dissent is recorded, never erased.",
        "Every claim survives peer challenge before it shapes a recommendation."
      ],
      values: ["accuracy", "transparency", "pragmatism"],
      ethics: ["No fabrication of evidence.", "Cite sources.", "Flag uncertainty explicitly."],
      decision_rules: {
        voting_quorum: 0.6,
        trust_weighted: true,
        amendment_threshold: 0.75,
        ceo_veto: false,
        confidence_threshold: 0.72,
        max_cycles: 3
      }
    }
  };
  var RESEARCH_DEPARTMENTS = STARTUP_TEMPLATE.departments.filter(
    (department) => department.id !== "ceo_office"
  );
  function deliverablesFor(departmentName) {
    switch (departmentName) {
      case "Research Division":
        return "market size + competitor brief with sources";
      case "Engineering":
        return "feasibility verdict + effort estimate";
      case "Finance":
        return "24-month cost/revenue model";
      case "Marketing":
        return "demand signal + willingness to pay";
      case "Legal":
        return "compliance risk register";
      default:
        return "brief";
    }
  }

  // backend/sofuu/agents/departments.js
  var AGENT_IDS = {
    "CEO Office": "ceo_office",
    "Research Division": "research",
    Engineering: "engineering",
    Finance: "finance",
    Marketing: "marketing",
    Legal: "legal"
  };
  function agentNameFor(department) {
    const id = department && (department.id || AGENT_IDS[department.name]);
    return `veliora_${id || "org"}`;
  }
  function buildSystemPrompt(ctx) {
    const org = ctx.org;
    const constitution = org.constitution || {};
    const department = ctx.department || {};
    return [
      `You are ${ctx.role || department.role || "a worker"} in the ${department.name || "organization"}.`,
      `Organization: ${org.name} (${org.template}).`,
      `Mission: ${constitution.mission || "Investigate and decide with evidence."}`,
      `Principles: ${(constitution.principles || []).join("; ")}`,
      `Ethics: ${(constitution.ethics || []).join("; ")}`,
      `Department charter: ${department.charter || ""}`,
      `Organizational DNA: ${JSON.stringify(org.dna || STARTUP_TEMPLATE.dna)}`,
      `Investigation goal: ${ctx.inv ? ctx.inv.prompt : ""}`,
      "Be concrete, cite evidence when you have it, and preserve dissent."
    ].join("\n");
  }
  function evidenceFixture(departmentName, prompt) {
    switch (departmentName) {
      case "Research Division":
        return {
          claim: "The AI healthcare market is growing quickly, but hospital procurement cycles remain slow.",
          confidence: 0.78,
          source_type: "web",
          summary: "Market scans and competitor briefs point to strong demand in outpatient triage, with long sales cycles.",
          sources: [{ title: "Market scan", url: "https://example.com/market", snippet: "AI triage adoption is rising." }]
        };
      case "Engineering":
        return {
          claim: "A HIPAA-ready triage MVP is feasible in roughly 12 weeks with a four-person team.",
          confidence: 0.72,
          source_type: "internal_analysis",
          summary: "A narrow, non-diagnostic workflow avoids the hardest clinical validation work in the first release.",
          sources: [{ title: "Architecture review", url: "", snippet: "Four services, one clinician-facing workflow." }]
        };
      case "Finance":
        return {
          claim: "Break-even requires roughly 14,000 paying clinics at $99/month.",
          confidence: 0.69,
          source_type: "internal_analysis",
          summary: "The 24-month model is sensitive to sales-cycle length and clinician onboarding cost.",
          sources: [{ title: "Financial model", url: "", snippet: "Base case: 14k clinics at $99." }]
        };
      case "Marketing":
        return {
          claim: "A survey of 38 clinics found that 62% would trial an AI triage product.",
          confidence: 0.64,
          source_type: "survey",
          summary: "Demand signal is positive but the sample is small, so the estimate carries wide uncertainty.",
          sources: [{ title: "Clinic survey", url: "", snippet: "38 respondents, 62% trial intent." }]
        };
      case "Legal":
        return {
          claim: "Diagnostic claims may require FDA clearance; a non-diagnostic triage assistant has a lower regulatory burden.",
          confidence: 0.81,
          source_type: "web",
          summary: "Regulatory review should start before any clinical claim is made in marketing or product copy.",
          sources: [{ title: "Regulatory overview", url: "https://example.com/fda", snippet: "Clinical claims trigger device review." }]
        };
      default:
        return {
          claim: `The organization should evaluate ${prompt} with a bounded pilot.`,
          confidence: 0.6,
          source_type: "internal_analysis",
          summary: "A bounded pilot limits downside while producing real evidence.",
          sources: [{ title: "Department brief", url: "", snippet: "Pilot first." }]
        };
    }
  }
  function challengeFixture(departmentName) {
    return {
      challenge: departmentName === "Marketing" ? "The survey covers only 38 clinics; the sample is too small and may overstate trial intent." : "The claim needs a recency check and a clearer source trail before it shapes the recommendation.",
      severity: "medium"
    };
  }
  function debateFixture(ctx) {
    const department = ctx.department ? ctx.department.name : "Department";
    if (ctx.round > 1) {
      return {
        text: `Round 2 (${department}): I still see execution risk, but the evidence supports a bounded, non-diagnostic pilot rather than a full launch.`,
        stance: "qualified"
      };
    }
    return {
      text: `Round 1 (${department}): The evidence supports proceeding, provided the organization validates demand and regulatory scope first.`,
      stance: "support"
    };
  }
  function synthesisFixture() {
    return {
      recommendation: "Proceed with a 12-week, HIPAA-first pilot limited to non-diagnostic triage.",
      confidence: 0.78,
      executive_summary: "The organization should run a bounded pilot before committing to a full product launch.",
      dissent: ["Legal: do not make diagnostic claims before FDA review."],
      risks: ["Long clinic sales cycles", "Regulatory scope creep", "Small initial demand sample"],
      opportunities: ["Outpatient triage demand", "A narrow workflow can launch before clinical validation"],
      next_steps: ["Recruit 5 pilot clinics", "Complete a HIPAA review", "Freeze non-diagnostic product copy"]
    };
  }
  function reportFixture(ctx) {
    const evidence = (ctx.evidence || []).map((item) => item.summary || item.claim || "");
    const dissent = (ctx.dissent || []).slice();
    return {
      executive_summary: "The organization recommends a bounded pilot, not a full launch.",
      recommendation: "Proceed with a 12-week, HIPAA-first pilot limited to non-diagnostic triage.",
      confidence: ctx.confidence || 0.78,
      dissenting_opinions: dissent,
      supporting_evidence: evidence.length ? evidence : ["Accepted evidence is recorded in the investigation transcript."],
      risks: ["Long clinic sales cycles", "Regulatory scope creep", "Small initial demand sample"],
      opportunities: ["Outpatient triage demand", "A narrow workflow can launch before clinical validation"],
      next_steps: ["Recruit 5 pilot clinics", "Complete a HIPAA review", "Freeze non-diagnostic product copy"],
      full_discussion_summary: ctx.transcript || "The board debated the recommendation over two rounds."
    };
  }

  // backend/sofuu/agents/llm.js
  function extractJSON(value) {
    if (value && typeof value === "object") return value;
    let text = String(value || "").trim();
    const fenced = text.match(/```(?:json)?\s*([\s\S]*?)```/i);
    if (fenced) text = fenced[1].trim();
    const start = text.indexOf("{");
    const end = text.lastIndexOf("}");
    if (start >= 0 && end > start) text = text.slice(start, end + 1);
    return JSON.parse(text);
  }
  var LLM = class {
    constructor(config) {
      this.config = config;
    }
    _externalAvailable() {
      return this.config.mode !== "scripted";
    }
    async _completeJSON(ctx, instruction, fallback) {
      if (!this._externalAvailable()) {
        return fallback();
      }
      try {
        const prompt = [
          buildSystemPrompt(ctx),
          instruction,
          "Return only a JSON object. Do not wrap it in markdown."
        ].join("\n\n");
        if (this.config.mode === "agent") {
          const name = agentNameFor(ctx.department);
          const run = await sofuu.agent.run(name, prompt);
          return extractJSON(run.answer || run.result || {});
        }
        if (!this.config.provider || !this.config.model) {
          throw new Error("provider and model are required for non-scripted mode");
        }
        const options = {
          provider: this.config.provider,
          model: this.config.model,
          temperature: 0.2
        };
        if (this.config.baseUrl) options.base_url = this.config.baseUrl;
        const response = await sofuu.ai.complete(prompt, options);
        return extractJSON(response && response.text !== void 0 ? response.text : response);
      } catch (error) {
        if (!this.config.fallbackToScripted) throw error;
        console.warn(`LLM call failed, using scripted fixture: ${error.message}`);
        return fallback();
      }
    }
    async evidence(ctx) {
      return this._completeJSON(
        ctx,
        [
          `Collect one evidence record for the ${ctx.department.name}.`,
          `The task is: ${ctx.task.description}.`,
          `Deliverable: ${ctx.task.deliverables}.`,
          "Return JSON with keys: claim, confidence (0-1), source_type (paper|survey|external_comm|internal_analysis|web), summary, sources (array of {title,url,snippet})."
        ].join("\n"),
        () => evidenceFixture(ctx.department.name, ctx.inv.prompt)
      );
    }
    async challenge(ctx) {
      return this._completeJSON(
        ctx,
        [
          `Review this claim from a peer department: "${ctx.evidence.claim}".`,
          "If it is weak, explain specifically (sample size, bias, relevance, recency).",
          'If it is strong, return {"challenge": null}.',
          "Return JSON with keys: challenge, severity (low|medium|high)."
        ].join("\n"),
        () => challengeFixture(ctx.evidence.department)
      );
    }
    async debate(ctx) {
      const otherTurns = ctx.otherTurns ? `Prior turns: ${ctx.otherTurns}` : "You are opening the debate.";
      return this._completeJSON(
        ctx,
        [
          `Debate round ${ctx.round}. Your assigned stance is ${ctx.stance}.`,
          otherTurns,
          "State your department's position, cite the evidence, and disagree openly when a claim is unsupported.",
          "Keep it to 2-3 sentences.",
          "Return JSON with keys: text, stance (support|oppose|qualified)."
        ].join("\n"),
        () => debateFixture(ctx)
      );
    }
    async synthesize(ctx) {
      return this._completeJSON(
        ctx,
        [
          "Synthesize the board recommendation from the debate and accepted evidence.",
          `Accepted evidence: ${JSON.stringify(ctx.evidence.map((item) => item.claim || item.summary))}`,
          `Debate transcript: ${ctx.transcript}`,
          `Known dissent: ${ctx.dissent.join(" | ")}`,
          "Preserve every dissent under `dissent`.",
          "Return JSON with keys: recommendation, confidence (0-1), executive_summary, dissent (array), risks (array), opportunities (array), next_steps (array)."
        ].join("\n"),
        () => synthesisFixture()
      );
    }
    async vote(ctx) {
      return this._completeJSON(
        ctx,
        [
          `Draft recommendation: ${ctx.recommendation}`,
          `Your department's position: ${ctx.position || "not recorded"}`,
          "Cast one vote with one line of reasoning.",
          "Return JSON with keys: choice (yes|no|abstain), reasoning."
        ].join("\n"),
        () => ({ choice: "yes", reasoning: "The bounded-pilot evidence supports the recommendation." })
      );
    }
    async report(ctx) {
      return this._completeJSON(
        ctx,
        [
          `Final recommendation: ${ctx.recommendation}`,
          `Accepted evidence: ${JSON.stringify(ctx.evidence.map((item) => item.claim || item.summary))}`,
          `Dissenting opinions: ${ctx.dissent.join(" | ")}`,
          `Transcript: ${ctx.transcript}`,
          "Assemble the final board report.",
          "Return JSON with keys: executive_summary, recommendation, confidence, dissenting_opinions (array), supporting_evidence (array), risks (array), opportunities (array), next_steps (array), full_discussion_summary."
        ].join("\n"),
        () => reportFixture(ctx)
      );
    }
  };

  // backend/sofuu/agents/definitions.js
  function defineAgents(config = {}) {
    for (const department of STARTUP_TEMPLATE.departments) {
      try {
        const definition = {
          name: `veliora_${department.id}`,
          system: [
            `You are the ${department.role} in the ${department.name}.`,
            department.charter,
            "Return structured JSON when the task asks for it. Preserve dissent."
          ].join("\n"),
          tools: ["web"],
          memory: "agent",
          budget: { maxSteps: 8, maxWallMs: 12e4 }
        };
        if (config.provider) definition.provider = config.provider;
        if (config.model) definition.model = config.model;
        if (config.baseUrl) definition.base_url = config.baseUrl;
        sofuu.agent.define(definition);
      } catch (error) {
        console.warn(`could not define Sofuu agent veliora_${department.id}: ${error.message}`);
      }
    }
  }

  // backend/sofuu/orchestration/investigation.js
  function terminal(state2) {
    return state2 === "completed" || state2 === "cancelled" || state2 === "failed" || state2 === "archived";
  }
  function firstWorker(org, departmentName) {
    const department = org.departments.find((item) => item.name === departmentName);
    if (!department) return null;
    const worker = org.workers.find(
      (item) => item.department_id === department.department_id && item.state === "active"
    );
    if (!worker) return null;
    return { department, worker };
  }
  function challengerFor(org, evidenceDepartment) {
    for (const department of org.departments) {
      if (department.name === evidenceDepartment) continue;
      const worker = org.workers.find(
        (item) => item.department_id === department.department_id && item.state === "active"
      );
      if (worker) return { department, worker };
    }
    return null;
  }
  function uniqueSpeakers(evidence) {
    const seen = /* @__PURE__ */ new Set();
    const speakers = [];
    for (const item of evidence) {
      if (seen.has(item.department)) continue;
      seen.add(item.department);
      speakers.push({
        worker_id: item.submitted_by,
        department_id: item.department_id,
        department: item.department,
        role: item.role,
        claim: item.claim
      });
    }
    return speakers;
  }
  function sortByTime(items, field) {
    return items.slice().sort((a, b) => String(a[field] || "").localeCompare(String(b[field] || "")));
  }
  var InvestigationOrchestrator = class {
    constructor({ store, bus, llm, config }) {
      this.store = store;
      this.bus = bus;
      this.llm = llm;
      this.config = config;
    }
    // ── reads ────────────────────────────────────────────────────────────
    getRecord(invID) {
      return this.store.get("investigations", invID);
    }
    tasks(invID) {
      return sortByTime(this.store.list("tasks", { ref: invID }), "created_at");
    }
    evidence(invID) {
      return sortByTime(this.store.list("evidence", { ref: invID }), "submitted_at");
    }
    challenges(invID) {
      return sortByTime(this.store.list("challenges", { ref: invID }), "resolved_at");
    }
    arguments(invID) {
      return sortByTime(this.store.list("arguments", { ref: invID }), "at");
    }
    votes(invID) {
      return this.store.list("votes", { ref: invID }).sort((a, b) => b.cycle - a.cycle || String(a.created_at).localeCompare(String(b.created_at)));
    }
    meetings(orgID) {
      return sortByTime(this.store.list("meetings", { ref: orgID }), "called_at");
    }
    reportRecord(invID) {
      return this.store.get("reports", invID);
    }
    dto(inv) {
      if (!inv) return null;
      const cycleTasks = this.tasks(inv.investigation_id).filter(
        (task) => task.task_type === "collect_evidence" && task.cycle === inv.cycle_count
      );
      const planTasks = cycleTasks.length ? cycleTasks.map((task) => ({
        task_id: task.task_id,
        department: task.department,
        description: task.description,
        deliverables: task.deliverables,
        status: task.status
      })) : inv.plan && inv.plan.tasks || [];
      return {
        investigation_id: inv.investigation_id,
        organization_id: inv.organization_id,
        title: inv.title,
        prompt: inv.prompt,
        state: inv.state,
        current_phase: inv.current_phase,
        charter: inv.charter,
        plan: { tasks: planTasks },
        cycle_count: inv.cycle_count,
        confidence: inv.confidence || 0,
        created_at: inv.created_at,
        completed_at: inv.completed_at || null
      };
    }
    get(orgID, invID) {
      const inv = this.getRecord(invID);
      if (!inv || inv.organization_id !== orgID) return null;
      return this.dto(inv);
    }
    list(orgID) {
      return this.store.list("investigations", { ref: orgID }).sort((a, b) => String(b.created_at).localeCompare(String(a.created_at))).map((inv) => this.dto(inv));
    }
    getDebate(orgID, invID) {
      const debates = this.store.list("debates", { ref: invID });
      if (!debates.length) return null;
      const debate = debates[0];
      const transcript = this.arguments(invID).filter((argument) => argument.debate_id === debate.debate_id).map((argument) => ({
        argument_id: argument.argument_id,
        worker_id: argument.worker_id,
        role: argument.role,
        department: argument.department,
        round: argument.round,
        stance: argument.stance,
        text: argument.text,
        at: argument.at
      }));
      const participants = [];
      for (const item of transcript) {
        if (!participants.includes(item.worker_id)) participants.push(item.worker_id);
      }
      return {
        debate_id: debate.debate_id,
        organization_id: orgID,
        investigation_id: invID,
        meeting_id: debate.meeting_id,
        topic: debate.topic,
        debate_type: debate.debate_type,
        state: debate.state,
        phase: debate.phase,
        participants,
        transcript,
        opened_at: debate.opened_at,
        concluded_at: debate.concluded_at || null
      };
    }
    getMeeting(orgID, meetingID) {
      return this.store.get("meetings", meetingID);
    }
    getReport(orgID, invID) {
      const inv = this.getRecord(invID);
      if (!inv || inv.organization_id !== orgID) return { error: "not_found" };
      if (inv.state !== "completed" && inv.state !== "archived") return { error: "not_complete" };
      const report = this.reportRecord(invID);
      if (!report) return { error: "not_complete" };
      return { report };
    }
    // ── mutations ────────────────────────────────────────────────────────
    create(org, prompt, depth = "standard") {
      if (!org) throw new Error("organization not found");
      if (!prompt || !prompt.trim()) throw new Error("prompt required");
      const invID = newId("inv");
      const title = prompt.length > 60 ? `${prompt.slice(0, 60)}\u2026` : prompt;
      const inv = {
        investigation_id: invID,
        organization_id: org.organization_id,
        title,
        prompt,
        state: "planning",
        current_phase: "scoping",
        charter: {
          goal: prompt,
          departments: STARTUP_TEMPLATE.departments.map((department) => department.name),
          success_criteria: ["Evidence survives peer challenge", "Dissent is preserved"],
          confidence_threshold: this.config.confidenceThreshold,
          priority: "normal",
          depth
        },
        plan: { tasks: [] },
        cycle_count: 1,
        confidence: 0,
        final_recommendation: null,
        created_at: nowISO(),
        completed_at: null
      };
      this.store.put("investigations", invID, inv, org.organization_id);
      this.bus.publish(org.organization_id, "investigation.started", {
        investigation_id: invID,
        title,
        prompt,
        depth
      });
      this.bus.publish(org.organization_id, "investigation.charter_signed", {
        investigation_id: invID,
        charter: inv.charter
      });
      const tasks = this._createResearchTasks(org, inv, 1);
      inv.plan.tasks = tasks.map((task) => ({
        task_id: task.task_id,
        department: task.department,
        description: task.description,
        deliverables: task.deliverables,
        status: task.status
      }));
      this.store.put("investigations", invID, inv, org.organization_id);
      this._transition(inv, "researching", "research");
      Promise.resolve().then(() => this.run(invID)).catch((error) => this._fail(invID, error));
      return this.dto(this.getRecord(invID));
    }
    cancel(orgID, invID) {
      const inv = this.getRecord(invID);
      if (!inv || inv.organization_id !== orgID) return false;
      if (terminal(inv.state)) return false;
      inv.state = "cancelled";
      inv.current_phase = "cancelled";
      this.store.put("investigations", invID, inv, orgID);
      this.bus.publish(orgID, "investigation.cancelled", { investigation_id: invID });
      return true;
    }
    // First-pass recovery: resume investigations that were interrupted during
    // research. Later phases have their own idempotency work to do before they
    // can be resumed safely, so they are left visible rather than silently
    // restarted into a duplicate debate.
    resumeActive() {
      for (const inv of this.store.list("investigations", {})) {
        if (terminal(inv.state)) continue;
        if (inv.state === "planning") {
          this._transition(inv, "researching", "research");
        }
        if (inv.state === "researching" || inv.state === "planning") {
          Promise.resolve().then(() => this.run(inv.investigation_id)).catch((error) => this._fail(inv.investigation_id, error));
        } else {
          console.warn(
            `not resuming ${inv.investigation_id} from state ${inv.state} in this pass`
          );
        }
      }
    }
    async run(invID) {
      let inv = this.getRecord(invID);
      if (!inv) return;
      while (!terminal(inv.state)) {
        const cycle = inv.cycle_count;
        await this._research(inv, cycle);
        inv = this.getRecord(invID);
        if (!inv || terminal(inv.state)) return;
        await this._challenge(inv, cycle);
        inv = this.getRecord(invID);
        if (!inv || terminal(inv.state)) return;
        await this._debate(inv, cycle);
        inv = this.getRecord(invID);
        if (!inv || terminal(inv.state)) return;
        await this._synthesis(inv, cycle);
        inv = this.getRecord(invID);
        if (!inv || terminal(inv.state)) return;
        await this._vote(inv, cycle);
        inv = this.getRecord(invID);
        if (!inv || terminal(inv.state)) return;
        if (inv.state === "researching") continue;
        if (inv.state === "reporting") {
          await this._report(inv);
          return;
        }
        return;
      }
    }
    // ── pipeline phases ──────────────────────────────────────────────────
    async _research(inv, cycle) {
      const org = this.store.get("orgs", inv.organization_id);
      if (!org) throw new Error("organization disappeared during research");
      const tasks = this.tasks(inv.investigation_id).filter(
        (task) => task.task_type === "collect_evidence" && task.cycle === cycle
      );
      for (const task of tasks) {
        if (task.status === "done") continue;
        task.status = "in_progress";
        task.started_at = nowISO();
        this.store.put("tasks", task.task_id, task, inv.investigation_id);
        this.bus.publish(inv.organization_id, "task.started", {
          task_id: task.task_id,
          investigation_id: inv.investigation_id,
          task_type: task.task_type
        });
        const department = STARTUP_TEMPLATE.departments.find(
          (item) => item.name === task.department
        );
        const result = await this.llm.evidence({
          org,
          inv,
          department,
          role: task.role,
          task,
          cycle
        });
        await sleep(this.config.stepDelayMs);
        const claim = String(result.claim || result.summary || "Evidence collected.").trim();
        const summary = String(result.summary || claim).trim();
        const confidence = Math.max(0, Math.min(1, Number(result.confidence) || 0.5));
        const evidenceID = newId("evd");
        const evidence = {
          evidence_id: evidenceID,
          investigation_id: inv.investigation_id,
          task_id: task.task_id,
          submitted_by: task.worker_id,
          department_id: task.department_id,
          department: task.department,
          role: task.role,
          source_type: result.source_type || "internal_analysis",
          summary,
          claim,
          confidence,
          status: "submitted",
          challenge_count: 0,
          sources: result.sources || [],
          cycle,
          submitted_at: nowISO()
        };
        this.store.put("evidence", evidenceID, evidence, inv.investigation_id);
        task.status = "done";
        task.completed_at = nowISO();
        this.store.put("tasks", task.task_id, task, inv.investigation_id);
        this.bus.publish(inv.organization_id, "evidence.submitted", {
          investigation_id: inv.investigation_id,
          evidence_id: evidenceID,
          claim,
          confidence,
          department: task.department
        });
        this.bus.publish(inv.organization_id, "task.completed", {
          task_id: task.task_id,
          investigation_id: inv.investigation_id,
          task_type: task.task_type
        });
      }
      this._transition(inv, "analyzing", "analysis");
    }
    async _challenge(inv, cycle) {
      const org = this.store.get("orgs", inv.organization_id);
      const cycleEvidence = this.evidence(inv.investigation_id).filter(
        (item) => item.cycle === cycle
      );
      if (!cycleEvidence.length) {
        throw new Error("research completed without evidence to challenge");
      }
      const evidence = cycleEvidence.filter((item) => item.status === "submitted");
      if (!evidence.length) {
        this._transition(inv, "debating", "debate");
        return;
      }
      for (const item of evidence) {
        const challenger = challengerFor(org, item.department);
        if (!challenger) throw new Error(`no independent challenger for ${item.department}`);
        const verdict = await this.llm.challenge({
          org,
          inv,
          department: challenger.department,
          role: challenger.worker.role,
          evidence: item,
          cycle
        });
        await sleep(this.config.stepDelayMs);
        const text = verdict && verdict.challenge ? String(verdict.challenge).trim() : "";
        item.status = "accepted";
        if (text) item.challenge_count = (item.challenge_count || 0) + 1;
        this.store.put("evidence", item.evidence_id, item, inv.investigation_id);
        if (text) {
          const challenge = {
            challenge_id: newId("chl"),
            investigation_id: inv.investigation_id,
            evidence_id: item.evidence_id,
            department: item.department,
            challenged_by_worker_id: challenger.worker.worker_id,
            challenge_text: text,
            status: "resolved_qualified",
            cycle,
            resolved_at: nowISO()
          };
          this.store.put("challenges", challenge.challenge_id, challenge, inv.investigation_id);
          this.bus.publish(inv.organization_id, "evidence.challenged", {
            investigation_id: inv.investigation_id,
            evidence_id: item.evidence_id,
            challenge_id: challenge.challenge_id,
            challenge_text: text
          });
        }
        this.bus.publish(inv.organization_id, "evidence.upheld", {
          investigation_id: inv.investigation_id,
          evidence_id: item.evidence_id,
          qualified: Boolean(text)
        });
      }
      this._transition(inv, "debating", "debate");
    }
    async _debate(inv, cycle) {
      const org = this.store.get("orgs", inv.organization_id);
      const accepted = this.evidence(inv.investigation_id).filter(
        (item) => item.cycle === cycle && item.status === "accepted"
      );
      const speakers = uniqueSpeakers(accepted);
      if (speakers.length < 2) throw new Error("debate needs at least two departments with accepted evidence");
      const debateID = newId("dbt");
      const meetingID = newId("mtg");
      const participants = speakers.map((speaker) => speaker.worker_id);
      const debate = {
        debate_id: debateID,
        investigation_id: inv.investigation_id,
        organization_id: inv.organization_id,
        meeting_id: meetingID,
        topic: `Should the organization proceed with: ${inv.title}`,
        debate_type: "strategic",
        state: "open",
        phase: "opening",
        participants,
        cycle,
        opened_at: nowISO(),
        concluded_at: null
      };
      const meeting = {
        meeting_id: meetingID,
        organization_id: inv.organization_id,
        investigation_id: inv.investigation_id,
        title: `Investigation debate: ${inv.title}`,
        state: "conducting",
        agenda: [
          { id: "positions", title: "Cross-department evidence positions", type: "debate_ref", time_budget_min: 15 },
          { id: "rebuttal", title: "Rebuttal round", type: "decision", time_budget_min: 15 }
        ],
        participants,
        chair: "",
        called_at: nowISO(),
        started_at: nowISO(),
        ended_at: null
      };
      this.store.put("debates", debateID, debate, inv.investigation_id);
      this.store.put("meetings", meetingID, meeting, inv.organization_id);
      this.bus.publish(inv.organization_id, "debate.opened", {
        debate_id: debateID,
        investigation_id: inv.investigation_id,
        topic: debate.topic,
        participants
      });
      this.bus.publish(inv.organization_id, "meeting.called", {
        meeting_id: meetingID,
        investigation_id: inv.investigation_id,
        title: meeting.title,
        debate_id: debateID,
        participants
      });
      this.bus.publish(inv.organization_id, "meeting.started", {
        meeting_id: meetingID,
        debate_id: debateID,
        quorum_met: true
      });
      for (let round = 1; round <= 2; round += 1) {
        const priorTurns = this._transcriptText(debateID, inv.investigation_id);
        for (const speaker of speakers) {
          const department = STARTUP_TEMPLATE.departments.find(
            (item) => item.name === speaker.department
          );
          const turn = await this.llm.debate({
            org,
            inv,
            department,
            role: speaker.role,
            round,
            stance: round === 1 ? "support" : "oppose",
            otherTurns: priorTurns,
            cycle
          });
          await sleep(this.config.stepDelayMs);
          const text = String(turn && (turn.text || turn.response) || "").trim();
          if (!text) throw new Error("debate worker returned an empty turn");
          const stance = ["support", "oppose", "qualified"].includes(turn.stance) ? turn.stance : round === 1 ? "support" : "oppose";
          const argument = {
            argument_id: newId("arg"),
            debate_id: debateID,
            investigation_id: inv.investigation_id,
            worker_id: speaker.worker_id,
            department: speaker.department,
            role: speaker.role,
            round,
            stance,
            text,
            cycle,
            at: nowISO()
          };
          this.store.put("arguments", argument.argument_id, argument, inv.investigation_id);
          this.bus.publish(
            inv.organization_id,
            stance === "support" ? "debate.position" : round > 1 ? "debate.rebuttal" : "debate.challenged",
            {
              debate_id: debateID,
              investigation_id: inv.investigation_id,
              worker_id: speaker.worker_id,
              department: speaker.department,
              stance,
              round,
              text
            }
          );
        }
      }
      debate.state = "concluded";
      debate.phase = "closed";
      debate.concluded_at = nowISO();
      this.store.put("debates", debateID, debate, inv.investigation_id);
      meeting.state = "ended";
      meeting.ended_at = nowISO();
      this.store.put("meetings", meetingID, meeting, inv.organization_id);
      this.bus.publish(inv.organization_id, "debate.concluded", {
        debate_id: debateID,
        investigation_id: inv.investigation_id,
        verdict: "converged",
        rounds: 2
      });
      this.bus.publish(inv.organization_id, "meeting.concluded", {
        meeting_id: meetingID,
        investigation_id: inv.investigation_id,
        outcome: "transcript frozen for synthesis"
      });
      this._transition(inv, "synthesizing", "synthesis");
    }
    async _synthesis(inv, cycle) {
      const org = this.store.get("orgs", inv.organization_id);
      const ceo = firstWorker(org, "CEO Office");
      if (!ceo) throw new Error("no CEO worker available for synthesis");
      const accepted = this.evidence(inv.investigation_id).filter(
        (item) => item.cycle === cycle && item.status === "accepted"
      );
      const dissent = this._dissent(inv.investigation_id, cycle);
      const transcript = this._transcriptText(
        this._latestDebateID(inv.investigation_id),
        inv.investigation_id
      );
      const result = await this.llm.synthesize({
        org,
        inv,
        department: ceo.department,
        role: ceo.worker.role,
        evidence: accepted,
        dissent,
        transcript,
        cycle
      });
      await sleep(this.config.stepDelayMs);
      const recommendation = String(result.recommendation || "").trim();
      if (!recommendation) throw new Error("synthesis returned no recommendation");
      inv.final_recommendation = {
        ...result,
        recommendation,
        confidence: Math.max(0, Math.min(1, Number(result.confidence) || 0.72))
      };
      this.store.put("investigations", inv.investigation_id, inv, inv.organization_id);
      this.bus.publish(inv.organization_id, "debate.synthesis", {
        investigation_id: inv.investigation_id,
        recommendation,
        confidence: inv.final_recommendation.confidence
      });
      this._transition(inv, "vote", "synthesis");
    }
    async _vote(inv, cycle) {
      const org = this.store.get("orgs", inv.organization_id);
      const debateID = this._latestDebateID(inv.investigation_id);
      const args = this.arguments(inv.investigation_id).filter(
        (argument) => argument.debate_id === debateID
      );
      const speakers = [];
      const seen = /* @__PURE__ */ new Set();
      for (const argument of args) {
        if (seen.has(argument.worker_id)) continue;
        seen.add(argument.worker_id);
        speakers.push(argument);
      }
      if (!speakers.length) throw new Error("no debate speakers available to vote");
      const recommendation = this._recommendationText(inv);
      for (const speaker of speakers) {
        const worker = org.workers.find((item) => item.worker_id === speaker.worker_id);
        const result = await this.llm.vote({
          org,
          inv,
          department: STARTUP_TEMPLATE.departments.find((item) => item.name === speaker.department),
          role: speaker.role,
          recommendation,
          position: speaker.text,
          cycle
        });
        await sleep(this.config.stepDelayMs);
        let choice = String(result.choice || "").toLowerCase().trim();
        if (!["yes", "no", "abstain"].includes(choice)) choice = "abstain";
        const vote = {
          vote_id: newId("vot"),
          investigation_id: inv.investigation_id,
          voter_worker_id: speaker.worker_id,
          department: speaker.department,
          choice,
          weight: worker ? worker.trust_score : 0.5,
          reasoning: String(result.reasoning || ""),
          cycle,
          created_at: nowISO()
        };
        this.store.put("votes", vote.vote_id, vote, inv.investigation_id);
        this.bus.publish(inv.organization_id, "debate.vote", {
          investigation_id: inv.investigation_id,
          vote_id: vote.vote_id,
          choice,
          weight: vote.weight,
          department: vote.department
        });
      }
      const tally = { yes: 0, no: 0, abstain: 0 };
      for (const vote of this.votes(inv.investigation_id).filter((item) => item.cycle === cycle)) {
        tally[vote.choice] = (tally[vote.choice] || 0) + vote.weight;
      }
      const decided = tally.yes + tally.no;
      const confidence = decided > 0 ? tally.yes / decided : 0;
      const verdict = decided === 0 ? "no_consensus" : tally.yes >= tally.no ? "yes" : "no";
      inv.confidence = confidence;
      inv.state = "confidence_chk";
      inv.current_phase = "synthesis";
      inv.final_recommendation = {
        ...inv.final_recommendation || {},
        vote_verdict: verdict,
        vote_tally: tally
      };
      this.store.put("investigations", inv.investigation_id, inv, inv.organization_id);
      this.bus.publish(inv.organization_id, "investigation.confidence", {
        investigation_id: inv.investigation_id,
        confidence,
        threshold: this.config.confidenceThreshold,
        cycle
      });
      if (confidence < this.config.confidenceThreshold && cycle < this.config.maxResearchCycles) {
        this._concludeDebate(inv, debateID);
        const next = cycle + 1;
        const tasks = this._createResearchTasks(org, inv, next);
        inv.cycle_count = next;
        inv.plan.tasks = tasks.map((task) => ({
          task_id: task.task_id,
          department: task.department,
          description: task.description,
          deliverables: task.deliverables,
          status: task.status
        }));
        this.store.put("investigations", inv.investigation_id, inv, inv.organization_id);
        this._transition(inv, "researching", "research");
        return;
      }
      this._transition(inv, "reporting", "report");
    }
    async _report(inv) {
      const org = this.store.get("orgs", inv.organization_id);
      const ceo = firstWorker(org, "CEO Office");
      const accepted = this.evidence(inv.investigation_id).filter(
        (item) => item.status === "accepted"
      );
      const debateID = this._latestDebateID(inv.investigation_id);
      const transcript = this._transcriptText(debateID, inv.investigation_id);
      const dissent = this._dissent(inv.investigation_id, inv.cycle_count);
      const result = await this.llm.report({
        org,
        inv,
        department: ceo ? ceo.department : null,
        role: ceo ? ceo.worker.role : "CEO",
        recommendation: this._recommendationText(inv),
        evidence: accepted,
        dissent,
        transcript,
        confidence: inv.confidence,
        cycle: inv.cycle_count
      });
      await sleep(this.config.stepDelayMs);
      const report = {
        report_id: `rep_${inv.investigation_id.slice(-8)}`,
        investigation_id: inv.investigation_id,
        organization_id: inv.organization_id,
        executive_summary: String(result.executive_summary || result.summary || ""),
        recommendation: String(result.recommendation || ""),
        confidence: inv.confidence || Number(result.confidence) || 0,
        dissenting_opinions: result.dissenting_opinions || result.dissent || dissent,
        supporting_evidence: result.supporting_evidence || accepted.map((item) => item.summary),
        risks: result.risks || [],
        opportunities: result.opportunities || [],
        next_steps: result.next_steps || [],
        full_discussion: result.full_discussion_summary || result.full_discussion || transcript,
        created_at: nowISO()
      };
      if (!report.recommendation) throw new Error("board report returned no recommendation");
      this.store.put("reports", inv.investigation_id, report, inv.organization_id);
      if (ceo) {
        const taskID = newId("tsk");
        const task = {
          task_id: taskID,
          investigation_id: inv.investigation_id,
          department_id: ceo.department.department_id,
          department: ceo.department.name,
          worker_id: ceo.worker.worker_id,
          role: ceo.worker.role,
          task_type: "board_report",
          description: "Generate final board report",
          deliverables: "board report",
          status: "done",
          cycle: inv.cycle_count,
          created_at: nowISO(),
          completed_at: nowISO()
        };
        this.store.put("tasks", taskID, task, inv.investigation_id);
        this.bus.publish(inv.organization_id, "task.completed", {
          task_id: taskID,
          investigation_id: inv.investigation_id,
          task_type: "board_report"
        });
      }
      inv.state = "completed";
      inv.current_phase = "report";
      inv.completed_at = nowISO();
      this.store.put("investigations", inv.investigation_id, inv, inv.organization_id);
      this.bus.publish(inv.organization_id, "investigation.completed", {
        investigation_id: inv.investigation_id,
        report_id: report.report_id
      });
      this.bus.publish(inv.organization_id, "investigation.report_ready", {
        investigation_id: inv.investigation_id,
        report_id: report.report_id,
        formats: ["json"]
      });
    }
    // ── helpers ──────────────────────────────────────────────────────────
    _createResearchTasks(org, inv, cycle) {
      const tasks = [];
      for (const departmentTemplate of RESEARCH_DEPARTMENTS) {
        const found = firstWorker(org, departmentTemplate.name);
        if (!found) throw new Error(`department ${departmentTemplate.name} has no active worker`);
        const task = {
          task_id: newId("tsk"),
          investigation_id: inv.investigation_id,
          department_id: found.department.department_id,
          department: found.department.name,
          worker_id: found.worker.worker_id,
          role: found.worker.role,
          task_type: "collect_evidence",
          description: `Cycle ${cycle} research for ${found.department.name}: gather evidence for "${inv.prompt}"`,
          deliverables: deliverablesFor(found.department.name),
          status: "pending",
          cycle,
          created_at: nowISO()
        };
        this.store.put("tasks", task.task_id, task, inv.investigation_id);
        this.bus.publish(inv.organization_id, "task.assigned", {
          task_id: task.task_id,
          investigation_id: inv.investigation_id,
          task_type: task.task_type,
          department: task.department,
          cycle
        });
        tasks.push(task);
      }
      return tasks;
    }
    _transition(inv, state2, phase) {
      if (inv.state === state2 && inv.current_phase === phase) return;
      const from = inv.state;
      inv.state = state2;
      inv.current_phase = phase;
      this.store.put("investigations", inv.investigation_id, inv, inv.organization_id);
      this.bus.publish(inv.organization_id, "investigation.phase_changed", {
        investigation_id: inv.investigation_id,
        from_phase: from,
        to_phase: state2,
        state: state2,
        phase
      });
    }
    _concludeDebate(inv, debateID) {
      const debate = this.store.get("debates", debateID);
      if (debate) {
        debate.state = "concluded";
        debate.phase = "closed";
        debate.concluded_at = nowISO();
        this.store.put("debates", debateID, debate, inv.investigation_id);
      }
      for (const meeting of this.store.list("meetings", { ref: inv.organization_id })) {
        if (meeting.investigation_id === inv.investigation_id && meeting.state !== "ended") {
          meeting.state = "ended";
          meeting.ended_at = nowISO();
          this.store.put("meetings", meeting.meeting_id, meeting, inv.organization_id);
        }
      }
    }
    _fail(invID, error) {
      const inv = this.getRecord(invID);
      if (!inv || terminal(inv.state)) return;
      inv.state = "failed";
      inv.current_phase = "failed";
      inv.error = error && error.message ? error.message : String(error);
      this.store.put("investigations", invID, inv, inv.organization_id);
      this.bus.publish(inv.organization_id, "investigation.failed", {
        investigation_id: invID,
        error: inv.error
      });
      console.error(`investigation ${invID} failed: ${inv.error}`);
    }
    _latestDebateID(invID) {
      const debates = this.store.list("debates", { ref: invID });
      return debates.length ? debates[0].debate_id : "";
    }
    _transcriptText(debateID, invID) {
      if (!debateID || !invID) return "";
      return this.arguments(invID).filter((argument) => argument.debate_id === debateID).map((argument) => `${argument.role} (${argument.department}, ${argument.stance}): ${argument.text}`).join(" | ");
    }
    _dissent(invID, cycle) {
      const items = [];
      for (const argument of this.arguments(invID)) {
        if (argument.cycle !== cycle) continue;
        if (argument.stance === "oppose" || argument.stance === "qualified") {
          items.push(`${argument.department}: ${argument.text}`);
        }
      }
      for (const challenge of this.challenges(invID)) {
        if (challenge.cycle !== cycle) continue;
        items.push(`qualified evidence (${challenge.department}): ${challenge.challenge_text}`);
      }
      return items;
    }
    _recommendationText(inv) {
      const rec = inv.final_recommendation || {};
      const parts = [];
      if (rec.recommendation) parts.push(`Recommendation: ${rec.recommendation}`);
      if (rec.executive_summary) parts.push(`Executive summary: ${rec.executive_summary}`);
      if (Array.isArray(rec.dissent) && rec.dissent.length) {
        parts.push(`Dissent: ${rec.dissent.join("; ")}`);
      }
      return parts.join("\n");
    }
  };

  // backend/sofuu/auth/accounts.js
  var TOUCH_INTERVAL_MS = 15 * 60 * 1e3;
  var MAX_VERSION_LENGTH = 64;
  var PLATFORMS = /* @__PURE__ */ new Set(["android", "ios", "web", "desktop"]);
  function normalizeVersion(raw) {
    const value = String(raw || "").trim();
    return /^[0-9A-Za-z][0-9A-Za-z._+-]*$/.test(value) ? value.slice(0, MAX_VERSION_LENGTH) : "";
  }
  function normalizePlatform(raw) {
    const value = String(raw || "").trim().toLowerCase();
    return PLATFORMS.has(value) ? value : "unknown";
  }
  function present(record) {
    if (!record) return null;
    return {
      user_id: record.user_id,
      first_seen_at: record.first_seen_at,
      last_seen_at: record.last_seen_at,
      app_version: record.app_version || "",
      platform: record.platform || "unknown"
    };
  }
  var AccountRegistry = class {
    constructor(store) {
      this.store = store;
      this.lastTouch = /* @__PURE__ */ new Map();
    }
    touch(userID, meta = {}) {
      const user_id = String(userID || "").trim();
      if (!user_id) return null;
      const app_version = normalizeVersion(meta.app_version);
      const platform = normalizePlatform(meta.platform);
      const now = Date.now();
      const previous = this.lastTouch.get(user_id);
      if (previous && now - previous.at < TOUCH_INTERVAL_MS && previous.app_version === app_version && previous.platform === platform) {
        return present(previous.record);
      }
      const existing = this.store.get("accounts", user_id);
      const record = existing || {
        user_id,
        first_seen_at: nowISO()
      };
      record.last_seen_at = nowISO();
      if (app_version) record.app_version = app_version;
      if (platform !== "unknown") record.platform = platform;
      this.store.put("accounts", user_id, record, user_id);
      this.lastTouch.set(user_id, { at: now, app_version, platform, record });
      return present(record);
    }
    get(userID) {
      return present(this.store.get("accounts", String(userID || "")));
    }
    count() {
      return this.store.count("accounts");
    }
  };

  // backend/sofuu/server/gateway.js
  var BASE_CORS = {
    "Access-Control-Allow-Headers": "Authorization, Content-Type, X-Veliora-App-Version, X-Veliora-Platform",
    "Access-Control-Allow-Methods": "GET,POST,OPTIONS",
    "Access-Control-Max-Age": "600",
    Vary: "Origin"
  };
  var corsByResponse = /* @__PURE__ */ new WeakMap();
  function buildCors(req, config) {
    const allowed = config.appOrigins || [];
    const origin = req.headers && req.headers.origin ? String(req.headers.origin) : "";
    const cors = { ...BASE_CORS };
    if (allowed.includes("*")) {
      cors["Access-Control-Allow-Origin"] = "*";
    } else if (origin && allowed.includes(origin)) {
      cors["Access-Control-Allow-Origin"] = origin;
    } else if (!origin && allowed.length > 0) {
      cors["Access-Control-Allow-Origin"] = allowed[0];
    }
    return cors;
  }
  function sendJSON(res, status, value) {
    const cors = corsByResponse.get(res) || BASE_CORS;
    res.writeHead(status, { "Content-Type": "application/json", ...cors });
    res.end(JSON.stringify(value));
  }
  function sendError(res, status, code, message) {
    sendJSON(res, status, { error: { code, message } });
  }
  function splitUrl(raw) {
    const value = raw || "/";
    const qIndex = value.indexOf("?");
    const pathname = qIndex >= 0 ? value.slice(0, qIndex) : value;
    const query = {};
    if (qIndex >= 0) {
      for (const pair of value.slice(qIndex + 1).split("&")) {
        if (!pair) continue;
        const equals = pair.indexOf("=");
        const key = equals >= 0 ? pair.slice(0, equals) : pair;
        const val = equals >= 0 ? pair.slice(equals + 1) : "";
        query[decodeURIComponent(key)] = decodeURIComponent(val);
      }
    }
    return {
      pathname: pathname.startsWith("/api/v1") ? pathname.slice(4) : pathname,
      query
    };
  }
  function matchRoute(pattern, parts) {
    if (pattern.length !== parts.length) return null;
    const params = {};
    for (let i = 0; i < pattern.length; i += 1) {
      const expected = pattern[i];
      if (expected.startsWith(":")) {
        params[expected.slice(1)] = parts[i];
      } else if (expected !== parts[i]) {
        return null;
      }
    }
    return params;
  }
  function bodyOf(req, res) {
    if (!req.body) return {};
    try {
      return JSON.parse(req.body);
    } catch (error) {
      sendError(res, 400, "invalid_request", `invalid JSON body: ${error.message}`);
      return null;
    }
  }
  function clientMeta(req) {
    const headers = req && req.headers || {};
    return {
      app_version: String(headers["x-veliora-app-version"] || ""),
      platform: String(headers["x-veliora-platform"] || "")
    };
  }
  function createGateway({ store, bus, orchestrator, config, auth }) {
    const routes = [];
    const accounts = new AccountRegistry(store);
    function add(method, pattern, handler) {
      routes.push({ method, pattern: pattern.split("/").filter(Boolean), handler });
    }
    function ownedOrg(user, orgID) {
      const org = store.get("orgs", orgID);
      if (!org || org.owner_user_id !== user.user_id) return null;
      return org;
    }
    async function handle(req, res) {
      const cors = buildCors(req, config);
      corsByResponse.set(res, cors);
      try {
        if (req.method === "OPTIONS") {
          res.writeHead(204, cors);
          res.end();
          return;
        }
        const { pathname, query } = splitUrl(req.url);
        const parts = pathname.split("/").filter(Boolean).map(decodeURIComponent);
        const publicRoute = req.method === "GET" && (pathname === "/health" || pathname === "/v1/health") || pathname.startsWith("/ws/");
        let user = null;
        if (!publicRoute) {
          try {
            user = await auth.authenticate(req, {
              // EventSource cannot send Authorization. Only the SSE routes
              // accept a short-lived token in the query string.
              allowQueryToken: req.method === "GET" && pathname.endsWith("/events"),
              query
            });
          } catch (error) {
            const status = error.status || 401;
            sendError(res, status, error.code || "unauthorized", error.message || "unauthorized");
            return;
          }
        }
        let account = null;
        if (user) {
          try {
            account = accounts.touch(user.user_id, clientMeta(req));
          } catch (error) {
            console.warn(`account registry: touch failed: ${error.message || error}`);
          }
        }
        for (const route of routes) {
          if (route.method !== req.method) continue;
          const params = matchRoute(route.pattern, parts);
          if (!params) continue;
          await route.handler({ req, res, params, query, user, account, cors });
          return;
        }
        sendError(res, 404, "not_found", "route not found");
      } catch (error) {
        console.error("request failed:", error);
        try {
          sendError(res, 500, "internal_error", error.message || String(error));
        } catch (_) {
        }
      }
    }
    add("GET", "/health", ({ res }) => {
      sendJSON(res, 200, {
        status: "ok",
        backend: "sofuu",
        version: "0.1.0",
        auth: config.authMode
      });
    });
    add("GET", "/v1/health", ({ res }) => {
      sendJSON(res, 200, {
        status: "ok",
        backend: "sofuu",
        version: "0.1.0",
        auth: config.authMode
      });
    });
    add("GET", "/v1/me", ({ res, user, account }) => {
      sendJSON(res, 200, {
        user_id: user.user_id,
        session_id: user.session_id,
        account,
        claims: user.claims
      });
    });
    add("GET", "/v1/organizations/templates", ({ res }) => {
      sendJSON(res, 200, {
        templates: [
          {
            id: STARTUP_TEMPLATE.id,
            name: STARTUP_TEMPLATE.name,
            description: STARTUP_TEMPLATE.description,
            departments: STARTUP_TEMPLATE.departments.map((department) => department.name)
          }
        ]
      });
    });
    add("GET", "/v1/organizations", ({ res, user }) => {
      const data = store.list("orgs", {}).filter((org) => org.owner_user_id === user.user_id).map((org) => ({
        organization_id: org.organization_id,
        name: org.name,
        template: org.template,
        state: org.state,
        health: org.health
      }));
      sendJSON(res, 200, { data });
    });
    add("POST", "/v1/organizations", ({ req, res, user }) => {
      const body = bodyOf(req, res);
      if (!body) return;
      const name = String(body.name || "").trim();
      if (!name) return sendError(res, 400, "invalid_request", "name is required");
      const template = String(body.template || "startup");
      if (template !== "startup") {
        return sendError(res, 400, "invalid_request", `unknown template: ${template}`);
      }
      const orgID = newId("org");
      const org = {
        organization_id: orgID,
        owner_user_id: user.user_id,
        name,
        state: "active",
        template,
        health: 100,
        dna: { ...STARTUP_TEMPLATE.dna, ...body.dna || {} },
        constitution: {
          ...STARTUP_TEMPLATE.constitution,
          ...body.constitution || {}
        },
        departments: [],
        workers: [],
        created_at: nowISO()
      };
      if (body.constitution && body.constitution.mission) {
        org.constitution.mission = String(body.constitution.mission);
      }
      for (const departmentTemplate of STARTUP_TEMPLATE.departments) {
        const departmentID = newId("dep");
        const workerIDs = [];
        for (const role of departmentTemplate.roles) {
          for (let index = 0; index < role.count; index += 1) {
            const workerID = newId("wrk");
            org.workers.push({
              worker_id: workerID,
              organization_id: orgID,
              department_id: departmentID,
              name: `${role.role} ${index + 1}`,
              role: role.role,
              state: "active",
              trust_score: 0.5,
              current_task_id: "",
              promotions: 0,
              joined_at: nowISO()
            });
            workerIDs.push(workerID);
          }
        }
        org.departments.push({
          department_id: departmentID,
          organization_id: orgID,
          name: departmentTemplate.name,
          role: "department",
          charter: departmentTemplate.charter,
          workers: workerIDs,
          influence: 0.5,
          load: 0
        });
      }
      store.put("orgs", orgID, org, orgID);
      bus.publish(orgID, "organization.created", {
        organization_id: orgID,
        name,
        template,
        departments: org.departments.map((department) => department.name),
        worker_count: org.workers.length
      });
      sendJSON(res, 201, {
        organization_id: orgID,
        name,
        state: "active",
        template,
        constitution_id: `const_${orgID.slice(-8)}`,
        dna_id: `dna_${orgID.slice(-8)}`,
        departments: org.departments,
        created_at: org.created_at
      });
    });
    add("GET", "/v1/organizations/:orgID", ({ res, params, user }) => {
      const org = ownedOrg(user, params.orgID);
      if (!org) return sendError(res, 404, "organization_not_found", "organization not found");
      sendJSON(res, 200, org);
    });
    add("GET", "/v1/organizations/:orgID/status", ({ res, params, user }) => {
      const org = ownedOrg(user, params.orgID);
      if (!org) return sendError(res, 404, "organization_not_found", "organization not found");
      const terminal2 = /* @__PURE__ */ new Set(["completed", "cancelled", "failed", "archived"]);
      const investigations = store.list("investigations", { ref: params.orgID }).sort((a, b) => String(b.created_at).localeCompare(String(a.created_at)));
      const meetings = store.list("meetings", { ref: params.orgID });
      const activeInvestigations = investigations.filter((inv) => !terminal2.has(inv.state));
      const active = activeInvestigations[0] || null;
      const tasks = active ? orchestrator.tasks(active.investigation_id) : [];
      const evidence = active ? orchestrator.evidence(active.investigation_id) : [];
      const challenges = active ? orchestrator.challenges(active.investigation_id) : [];
      const currentTasks = tasks.filter(
        (task) => task.cycle === active?.cycle_count && task.task_type === "collect_evidence"
      );
      const departments = org.departments.map((department) => {
        const workers = org.workers.filter(
          (worker) => worker.department_id === department.department_id
        );
        const trust = workers.length ? workers.reduce((sum, worker) => sum + Number(worker.trust_score || 0), 0) / workers.length : 0.5;
        const task = currentTasks.find((item) => item.department === department.name) || null;
        const departmentEvidence = evidence.filter((item) => item.department === department.name);
        const departmentChallenges = challenges.filter(
          (item) => item.department === department.name && item.status === "open"
        );
        let workerState = "idle";
        let activity = "Idle";
        let currentTask = "";
        if (active) {
          currentTask = task?.task_id || "";
          if (task?.status === "in_progress") {
            workerState = "working";
            activity = task.description;
          } else if (task?.status === "pending") {
            workerState = "queued";
            activity = `Queued: ${task.description}`;
          } else if (task?.status === "done") {
            activity = "Research complete";
          } else if (active.state === "planning") {
            workerState = "waiting";
            activity = "Preparing investigation";
          } else if (active.state === "analyzing") {
            workerState = departmentEvidence.length ? "coordinating" : "waiting";
            activity = departmentEvidence.length ? "Reviewing evidence" : "Awaiting analysis";
          } else if (active.state === "debating") {
            workerState = "coordinating";
            activity = departmentEvidence.length ? "Debating evidence" : "Observing debate";
          } else if (["synthesizing", "vote", "voting", "confidence_chk"].includes(active.state)) {
            workerState = department.name === "CEO Office" ? "coordinating" : "waiting";
            activity = department.name === "CEO Office" ? "Synthesizing board recommendation" : "Awaiting vote";
          } else if (active.state === "reporting") {
            workerState = department.name === "CEO Office" ? "implementing" : "waiting";
            activity = department.name === "CEO Office" ? "Writing board report" : "Awaiting report";
          } else if (active.state === "researching") {
            workerState = "waiting";
            activity = "Preparing research";
          }
        }
        return {
          name: department.name,
          worker_state: workerState,
          activity,
          current_task: currentTask,
          artifacts: departmentEvidence.length,
          open_challenges: departmentChallenges.length,
          caspian_open: 0,
          trust
        };
      });
      sendJSON(res, 200, {
        organization_id: org.organization_id,
        state: org.state,
        health: org.health,
        departments,
        counts: {
          investigations_active: activeInvestigations.length,
          meetings: meetings.filter((meeting) => meeting.state !== "ended" && meeting.state !== "cancelled").length,
          messages: 0
        }
      });
    });
    add("GET", "/v1/organizations/:orgID/constitution", ({ res, params, user }) => {
      const org = ownedOrg(user, params.orgID);
      if (!org) return sendError(res, 404, "organization_not_found", "organization not found");
      sendJSON(res, 200, org.constitution);
    });
    add("GET", "/v1/organizations/:orgID/workers", ({ res, params, user }) => {
      const org = ownedOrg(user, params.orgID);
      if (!org) return sendError(res, 404, "organization_not_found", "organization not found");
      sendJSON(res, 200, { data: org.workers });
    });
    add("GET", "/v1/organizations/:orgID/communications", ({ res, params, user }) => {
      const org = ownedOrg(user, params.orgID);
      if (!org) return sendError(res, 404, "organization_not_found", "organization not found");
      sendJSON(res, 200, store.list("communications", { ref: params.orgID }));
    });
    add("GET", "/v1/organizations/:orgID/evolution/events", ({ res, params, user }) => {
      const org = ownedOrg(user, params.orgID);
      if (!org) return sendError(res, 404, "organization_not_found", "organization not found");
      sendJSON(res, 200, store.list("evolution", { ref: params.orgID }));
    });
    add("GET", "/v1/organizations/:orgID/investigations", ({ res, params, user }) => {
      const org = ownedOrg(user, params.orgID);
      if (!org) return sendError(res, 404, "organization_not_found", "organization not found");
      sendJSON(res, 200, orchestrator.list(params.orgID));
    });
    add("POST", "/v1/organizations/:orgID/investigations", ({ req, res, params, user }) => {
      const org = ownedOrg(user, params.orgID);
      if (!org) return sendError(res, 404, "organization_not_found", "organization not found");
      const body = bodyOf(req, res);
      if (!body) return;
      const prompt = String(body.prompt || body.goal || "").trim();
      if (!prompt) return sendError(res, 400, "invalid_request", "prompt is required");
      const investigation = orchestrator.create(org, prompt, body.depth || "standard");
      sendJSON(res, 202, investigation);
    });
    add("GET", "/v1/organizations/:orgID/investigations/:invID", ({ res, params, user }) => {
      const org = ownedOrg(user, params.orgID);
      if (!org) return sendError(res, 404, "organization_not_found", "organization not found");
      const investigation = orchestrator.get(params.orgID, params.invID);
      if (!investigation) return sendError(res, 404, "investigation_not_found", "investigation not found");
      sendJSON(res, 200, investigation);
    });
    add("POST", "/v1/organizations/:orgID/investigations/:invID/cancel", ({ res, params, user }) => {
      const org = ownedOrg(user, params.orgID);
      if (!org) return sendError(res, 404, "organization_not_found", "organization not found");
      const cancelled = orchestrator.cancel(params.orgID, params.invID);
      if (!cancelled) return sendError(res, 409, "investigation_invalid_transition", "cannot cancel investigation");
      sendJSON(res, 200, { cancelled: true });
    });
    add("GET", "/v1/organizations/:orgID/investigations/:invID/evidence", ({ res, params, user }) => {
      const org = ownedOrg(user, params.orgID);
      if (!org) return sendError(res, 404, "organization_not_found", "organization not found");
      const investigation = orchestrator.getRecord(params.invID);
      if (!investigation || investigation.organization_id !== params.orgID) {
        return sendError(res, 404, "investigation_not_found", "investigation not found");
      }
      const challenges = orchestrator.challenges(params.invID);
      const data = orchestrator.evidence(params.invID).map((item) => ({
        evidence_id: item.evidence_id,
        investigation_id: item.investigation_id,
        task_id: item.task_id,
        submitted_by: item.submitted_by,
        source_type: item.source_type,
        summary: item.summary,
        confidence: item.confidence,
        status: item.status,
        challenge_count: item.challenge_count,
        submitted_at: item.submitted_at,
        challenges: challenges.filter((challenge) => challenge.evidence_id === item.evidence_id).map((challenge) => ({
          challenge_id: challenge.challenge_id,
          by: challenge.challenged_by_worker_id,
          critique: challenge.challenge_text,
          resolution: challenge.status
        }))
      }));
      sendJSON(res, 200, data);
    });
    add("GET", "/v1/organizations/:orgID/investigations/:invID/challenges", ({ res, params, user }) => {
      const org = ownedOrg(user, params.orgID);
      if (!org) return sendError(res, 404, "organization_not_found", "organization not found");
      const investigation = orchestrator.getRecord(params.invID);
      if (!investigation || investigation.organization_id !== params.orgID) {
        return sendError(res, 404, "investigation_not_found", "investigation not found");
      }
      const data = orchestrator.challenges(params.invID).map((challenge) => ({
        challenge_id: challenge.challenge_id,
        evidence_id: challenge.evidence_id,
        department: challenge.department,
        challenged_by_worker_id: challenge.challenged_by_worker_id,
        challenge_text: challenge.challenge_text,
        status: challenge.status,
        resolved_at: challenge.resolved_at
      }));
      sendJSON(res, 200, data);
    });
    add("GET", "/v1/organizations/:orgID/investigations/:invID/debate", ({ res, params, user }) => {
      const org = ownedOrg(user, params.orgID);
      if (!org) return sendError(res, 404, "organization_not_found", "organization not found");
      const investigation = orchestrator.getRecord(params.invID);
      if (!investigation || investigation.organization_id !== params.orgID) {
        return sendError(res, 404, "investigation_not_found", "investigation not found");
      }
      const debate = orchestrator.getDebate(params.orgID, params.invID);
      if (!debate) return sendError(res, 404, "debate_not_found", "debate not found");
      sendJSON(res, 200, debate);
    });
    add("GET", "/v1/organizations/:orgID/investigations/:invID/votes", ({ res, params, user }) => {
      const org = ownedOrg(user, params.orgID);
      if (!org) return sendError(res, 404, "organization_not_found", "organization not found");
      const investigation = orchestrator.getRecord(params.invID);
      if (!investigation || investigation.organization_id !== params.orgID) {
        return sendError(res, 404, "investigation_not_found", "investigation not found");
      }
      const data = orchestrator.votes(params.invID).map((vote) => ({
        vote_id: vote.vote_id,
        investigation_id: vote.investigation_id,
        voter_worker_id: vote.voter_worker_id,
        department: vote.department,
        choice: vote.choice,
        weight: vote.weight,
        reasoning: vote.reasoning,
        cycle: vote.cycle,
        created_at: vote.created_at
      }));
      sendJSON(res, 200, data);
    });
    add("GET", "/v1/organizations/:orgID/investigations/:invID/report", ({ res, params, user }) => {
      const org = ownedOrg(user, params.orgID);
      if (!org) return sendError(res, 404, "organization_not_found", "organization not found");
      const result = orchestrator.getReport(params.orgID, params.invID);
      if (result.error === "not_found") {
        return sendError(res, 404, "investigation_not_found", "investigation not found");
      }
      if (result.error === "not_complete") {
        return sendError(res, 409, "investigation_not_complete", "investigation is not completed");
      }
      sendJSON(res, 200, result.report);
    });
    add("GET", "/v1/organizations/:orgID/meetings", ({ res, params, user }) => {
      const org = ownedOrg(user, params.orgID);
      if (!org) return sendError(res, 404, "organization_not_found", "organization not found");
      sendJSON(res, 200, orchestrator.meetings(params.orgID));
    });
    add("GET", "/v1/organizations/:orgID/meetings/:mtgID", ({ res, params, user }) => {
      const org = ownedOrg(user, params.orgID);
      if (!org) return sendError(res, 404, "organization_not_found", "organization not found");
      const meeting = orchestrator.getMeeting(params.orgID, params.mtgID);
      if (!meeting || meeting.organization_id !== params.orgID) {
        return sendError(res, 404, "meeting_not_found", "meeting not found");
      }
      sendJSON(res, 200, meeting);
    });
    add("GET", "/v1/organizations/:orgID/events", ({ res, params, user, cors }) => {
      const org = ownedOrg(user, params.orgID);
      if (!org) return sendError(res, 404, "organization_not_found", "organization not found");
      bus.subscribe(params.orgID, null, res, cors, user.expires_at);
    });
    add("GET", "/v1/organizations/:orgID/investigations/:invID/events", ({ res, params, user, cors }) => {
      const org = ownedOrg(user, params.orgID);
      if (!org) return sendError(res, 404, "organization_not_found", "organization not found");
      const investigation = orchestrator.getRecord(params.invID);
      if (!investigation || investigation.organization_id !== params.orgID) {
        return sendError(res, 404, "investigation_not_found", "investigation not found");
      }
      bus.subscribe(params.orgID, params.invID, res, cors, user.expires_at);
    });
    add("GET", "/ws/org/:orgID", ({ res }) => {
      sendError(res, 426, "websocket_not_supported", "use GET /v1/organizations/{orgID}/events (SSE)");
    });
    add("GET", "/ws/investigations/:orgID/:invID", ({ res }) => {
      sendError(res, 426, "websocket_not_supported", "use the investigation events SSE route");
    });
    return { handle };
  }

  // backend/sofuu/auth/clerk.js
  var DEV_PREFIX = "dev:";
  var AuthError = class extends Error {
    constructor(status, code, message) {
      super(message);
      this.name = "AuthError";
      this.status = status;
      this.code = code;
    }
  };
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
  var Authenticator = class {
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
          "veliora auth: DEV MODE \u2014 accepting 'Authorization: Bearer dev:<user_id>'. Never run this mode on a public deployment."
        );
        return;
      }
      if (!sofuu.crypto || typeof sofuu.crypto.verifyJWT !== "function") {
        throw new Error(
          "VELIORA_AUTH_MODE=clerk requires a Sofuu build with sofuu.crypto (see backend/sofuu/SOFUU-GAPS.md)"
        );
      }
      if (!nonEmpty(this.config.clerk.issuer)) {
        console.warn("veliora auth: CLERK_ISSUER is empty; the JWT `iss` claim will not be checked");
      }
      if (this.config.clerk.authorizedParties.length === 0) {
        console.warn(
          "veliora auth: CLERK_AUTHORIZED_PARTIES is empty; a token that includes an `azp` claim will be rejected"
        );
      }
      try {
        await this._ensureJWKS();
        console.log(
          `veliora auth: clerk mode ready (issuer=${this.config.clerk.issuer || "unchecked"}, jwks=${this.config.clerk.jwksUrl}, keys=${(this.jwks.keys || []).length})`
        );
      } catch (error) {
        console.warn(
          `veliora auth: JWKS not loaded at startup: ${error.message}. Check CLERK_JWKS_URL and that the Clerk Frontend API domain resolves.`
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
        try {
          await this._ensureJWKS(true);
          result = this._verify(token);
        } catch (_) {
        }
      }
      if (!result.ok) {
        const code = result.error && result.error.code || "invalid_token";
        const message = result.error && result.error.message || "token verification failed";
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
        expires_at: Number.isFinite(exp) ? exp * 1e3 : 0,
        claims
      };
    }
    _verify(token) {
      const clerk = this.config.clerk;
      return sofuu.crypto.verifyJWT(token, this.jwks, {
        issuer: clerk.issuer || void 0,
        authorizedParties: clerk.authorizedParties,
        audience: clerk.audience || void 0,
        clockSkewSeconds: clerk.clockSkewSeconds
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
          "dev auth expects 'Authorization: Bearer dev:<user_id>'"
        );
      }
      return {
        user_id: userId,
        session_id: "",
        expires_at: (Math.floor(Date.now() / 1e3) + 3600) * 1e3,
        claims: {
          sub: userId,
          iss: "veliora-dev",
          azp: "veliora-dev",
          exp: Math.floor(Date.now() / 1e3) + 3600
        }
      };
    }
    async _ensureJWKS(force = false) {
      const now = Date.now();
      const fresh = this.jwks && now - this.jwksAt < this.config.clerk.jwksCacheMs;
      if (!force && fresh) return this.jwks;
      if (force && this.jwks && now - this.lastRefreshAt < 3e4) return this.jwks;
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
          this.config.clerk.jwksTimeoutMs
        );
      });
      try {
        const response = await Promise.race([
          fetch(url, { headers: { Accept: "application/json" } }),
          timeout
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
            `veliora auth: JWKS refresh failed (${error.message}); using the cached key set`
          );
          this.jwksAt = Date.now() - Math.floor(this.config.clerk.jwksCacheMs / 2);
          return this.jwks;
        }
        throw new AuthError(
          503,
          "auth_unavailable",
          `cannot load Clerk JWKS: ${error.message}`
        );
      } finally {
        if (timer !== null) clearTimeout(timer);
      }
    }
  };

  // backend/sofuu/embedded.js
  var state = null;
  function lowerHeaders(value) {
    const out = {};
    if (!value || typeof value !== "object") return out;
    for (const key of Object.keys(value)) {
      out[String(key).toLowerCase()] = String(value[key]);
    }
    return out;
  }
  async function init(options) {
    if (state) return { ready: true, reused: true };
    const config = await loadConfig(options);
    config.host = "127.0.0.1";
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
      store: config.storePath
    };
  }
  async function request(input) {
    if (!state) throw new Error("veliora embedded runtime is not initialized");
    const options = input || {};
    const req = {
      method: String(options.method || "GET").toUpperCase(),
      url: String(options.url || "/"),
      headers: lowerHeaders(options.headers),
      body: options.body === void 0 || options.body === null ? "" : String(options.body)
    };
    let status = 200;
    let responseHeaders = {};
    const chunks = [];
    const res = {
      writeHead(code, headers) {
        status = Number(code) || 200;
        responseHeaders = { ...headers || {} };
        return this;
      },
      setHeader(name, value) {
        responseHeaders[String(name)] = String(value);
        return this;
      },
      write(chunk) {
        if (chunk !== void 0 && chunk !== null) chunks.push(String(chunk));
        return true;
      },
      end(chunk) {
        if (chunk !== void 0 && chunk !== null) chunks.push(String(chunk));
        return this;
      },
      send(chunk) {
        return this.end(chunk);
      }
    };
    await state.gateway.handle(req, res);
    return { status, headers: responseHeaders, body: chunks.join("") };
  }
  function tick(input) {
    const options = input || {};
    const ms = Math.max(0, Math.min(2e3, Number(options.ms === void 0 ? 50 : options.ms) || 0));
    return new Promise((resolve) => setTimeout(() => resolve({ ticked: ms }), ms));
  }
  function events(input) {
    if (!state) throw new Error("veliora embedded runtime is not initialized");
    const options = input || {};
    const orgId = String(options.org_id || "");
    const userId = String(options.user_id || "");
    const since = Number(options.since_seq || 0) || 0;
    const org = state.store.get("orgs", orgId);
    if (!org || org.owner_user_id !== userId) return [];
    return state.store.list("events", { ref: orgId }).filter((event) => Number(event.seq || 0) > since).sort((a, b) => Number(a.seq || 0) - Number(b.seq || 0)).map((event) => ({
      stream: event.stream || "organization",
      stream_id: event.stream_id || orgId,
      seq: Number(event.seq || 0),
      event: event.event_type || "",
      ts: event.ts || "",
      payload: event.payload || {}
    }));
  }
  function close() {
    if (!state) return false;
    try {
      state.bus.close();
    } catch (_) {
    }
    try {
      state.store.close();
    } catch (_) {
    }
    state = null;
    return true;
  }
  if (!sofuu.veliora) {
    sofuu.veliora = { init, request, events, tick, close };
  }
})();
