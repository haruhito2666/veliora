import { newId, nowISO } from "../core/ids.js";
import { STARTUP_TEMPLATE } from "../core/templates.js";

const BASE_CORS = {
  "Access-Control-Allow-Headers": "Authorization, Content-Type",
  "Access-Control-Allow-Methods": "GET,POST,OPTIONS",
  "Access-Control-Max-Age": "600",
  Vary: "Origin",
};

// The response object is a native Sofuu handle. Keep its CORS headers in a
// WeakMap rather than adding properties to it; handlers continue to call
// sendJSON/sendError without threading a request context through every route.
const corsByResponse = new WeakMap();

function buildCors(req, config) {
  const allowed = config.appOrigins || [];
  const origin = req.headers && req.headers.origin ? String(req.headers.origin) : "";
  const cors = { ...BASE_CORS };
  if (allowed.includes("*")) {
    cors["Access-Control-Allow-Origin"] = "*";
  } else if (origin && allowed.includes(origin)) {
    cors["Access-Control-Allow-Origin"] = origin;
  } else if (!origin && allowed.length > 0) {
    // curl / server-to-server: no browser enforcement, but keep the header
    // shape consistent for the smoke test.
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
    query,
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

export function createGateway({ store, bus, orchestrator, config, auth }) {
  const routes = [];

  function add(method, pattern, handler) {
    routes.push({ method, pattern: pattern.split("/").filter(Boolean), handler });
  }

  function ownedOrg(user, orgID) {
    const org = store.get("orgs", orgID);
    // Deliberately 404 for another owner's organization: an authenticated
    // user must not be able to enumerate organization ids.
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

      const publicRoute =
        (req.method === "GET" && (pathname === "/health" || pathname === "/v1/health")) ||
        pathname.startsWith("/ws/");
      let user = null;
      if (!publicRoute) {
        try {
          user = await auth.authenticate(req, {
            // EventSource cannot send Authorization. Only the SSE routes
            // accept a short-lived token in the query string.
            allowQueryToken: req.method === "GET" && pathname.endsWith("/events"),
            query,
          });
        } catch (error) {
          const status = error.status || 401;
          sendError(res, status, error.code || "unauthorized", error.message || "unauthorized");
          return;
        }
      }

      for (const route of routes) {
        if (route.method !== req.method) continue;
        const params = matchRoute(route.pattern, parts);
        if (!params) continue;
        await route.handler({ req, res, params, query, user, cors });
        return;
      }
      sendError(res, 404, "not_found", "route not found");
    } catch (error) {
      console.error("request failed:", error);
      try {
        sendError(res, 500, "internal_error", error.message || String(error));
      } catch (_) {
        // Response already started (for example an SSE stream).
      }
    }
  }

  // ── health and identity ──────────────────────────────────────────────

  add("GET", "/health", ({ res }) => {
    sendJSON(res, 200, {
      status: "ok",
      backend: "sofuu",
      version: "0.1.0",
      auth: config.authMode,
    });
  });

  add("GET", "/v1/health", ({ res }) => {
    sendJSON(res, 200, {
      status: "ok",
      backend: "sofuu",
      version: "0.1.0",
      auth: config.authMode,
    });
  });

  add("GET", "/v1/me", ({ res, user }) => {
    sendJSON(res, 200, {
      user_id: user.user_id,
      session_id: user.session_id,
      claims: user.claims,
    });
  });

  add("GET", "/v1/organizations/templates", ({ res }) => {
    sendJSON(res, 200, {
      templates: [
        {
          id: STARTUP_TEMPLATE.id,
          name: STARTUP_TEMPLATE.name,
          description: STARTUP_TEMPLATE.description,
          departments: STARTUP_TEMPLATE.departments.map((department) => department.name),
        },
      ],
    });
  });

  // ── organizations ────────────────────────────────────────────────────

  add("GET", "/v1/organizations", ({ res, user }) => {
    const data = store
      .list("orgs", {})
      .filter((org) => org.owner_user_id === user.user_id)
      .map((org) => ({
        organization_id: org.organization_id,
        name: org.name,
        template: org.template,
        state: org.state,
        health: org.health,
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
      dna: { ...STARTUP_TEMPLATE.dna, ...(body.dna || {}) },
      constitution: {
        ...STARTUP_TEMPLATE.constitution,
        ...(body.constitution || {}),
      },
      departments: [],
      workers: [],
      created_at: nowISO(),
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
            joined_at: nowISO(),
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
        load: 0,
      });
    }

    store.put("orgs", orgID, org, orgID);
    bus.publish(orgID, "organization.created", {
      organization_id: orgID,
      name,
      template,
      departments: org.departments.map((department) => department.name),
      worker_count: org.workers.length,
    });

    sendJSON(res, 201, {
      organization_id: orgID,
      name,
      state: "active",
      template,
      constitution_id: `const_${orgID.slice(-8)}`,
      dna_id: `dna_${orgID.slice(-8)}`,
      departments: org.departments,
      created_at: org.created_at,
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
    const investigations = store.list("investigations", { ref: params.orgID });
    const meetings = store.list("meetings", { ref: params.orgID });
    const activeInvestigations = investigations.filter(
      (inv) => !["completed", "cancelled", "failed", "archived"].includes(inv.state),
    );
    sendJSON(res, 200, {
      organization_id: org.organization_id,
      state: org.state,
      health: org.health,
      departments: org.departments.map((department) => ({
        name: department.name,
        worker_state: "idle",
        activity: "",
        current_task: "",
        artifacts: 0,
        open_challenges: 0,
        caspian_open: 0,
        trust: 0.5,
      })),
      counts: {
        investigations_active: activeInvestigations.length,
        meetings: meetings.filter((meeting) => meeting.state !== "ended" && meeting.state !== "cancelled").length,
        messages: 0,
      },
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
    sendJSON(res, 200, []);
  });

  add("GET", "/v1/organizations/:orgID/evolution/events", ({ res, params, user }) => {
    const org = ownedOrg(user, params.orgID);
    if (!org) return sendError(res, 404, "organization_not_found", "organization not found");
    sendJSON(res, 200, []);
  });

  // ── investigations ───────────────────────────────────────────────────

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
      challenges: challenges
        .filter((challenge) => challenge.evidence_id === item.evidence_id)
        .map((challenge) => ({
          challenge_id: challenge.challenge_id,
          by: challenge.challenged_by_worker_id,
          critique: challenge.challenge_text,
          resolution: challenge.status,
        })),
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
      resolved_at: challenge.resolved_at,
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
      created_at: vote.created_at,
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

  // ── meetings ─────────────────────────────────────────────────────────

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

  // ── live streams (SSE; Sofuu has no WebSocket server) ───────────────

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

  // A compatibility landing point for clients still pointing at the old WS
  // routes. They get an explicit error instead of a stalled connection.
  add("GET", "/ws/org/:orgID", ({ res }) => {
    sendError(res, 426, "websocket_not_supported", "use GET /v1/organizations/{orgID}/events (SSE)");
  });
  add("GET", "/ws/investigations/:orgID/:invID", ({ res }) => {
    sendError(res, 426, "websocket_not_supported", "use the investigation events SSE route");
  });

  return { handle };
}
