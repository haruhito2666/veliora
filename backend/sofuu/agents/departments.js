import { STARTUP_TEMPLATE } from "../core/templates.js";

const AGENT_IDS = {
  "CEO Office": "ceo_office",
  "Research Division": "research",
  Engineering: "engineering",
  Finance: "finance",
  Marketing: "marketing",
  Legal: "legal",
};

export function agentNameFor(department) {
  const id = department && (department.id || AGENT_IDS[department.name]);
  return `veliora_${id || "org"}`;
}

export function buildSystemPrompt(ctx) {
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
    "Be concrete, cite evidence when you have it, and preserve dissent.",
  ].join("\n");
}

export function evidenceFixture(departmentName, prompt) {
  switch (departmentName) {
    case "Research Division":
      return {
        claim: "The AI healthcare market is growing quickly, but hospital procurement cycles remain slow.",
        confidence: 0.78,
        source_type: "web",
        summary: "Market scans and competitor briefs point to strong demand in outpatient triage, with long sales cycles.",
        sources: [{ title: "Market scan", url: "https://example.com/market", snippet: "AI triage adoption is rising." }],
      };
    case "Engineering":
      return {
        claim: "A HIPAA-ready triage MVP is feasible in roughly 12 weeks with a four-person team.",
        confidence: 0.72,
        source_type: "internal_analysis",
        summary: "A narrow, non-diagnostic workflow avoids the hardest clinical validation work in the first release.",
        sources: [{ title: "Architecture review", url: "", snippet: "Four services, one clinician-facing workflow." }],
      };
    case "Finance":
      return {
        claim: "Break-even requires roughly 14,000 paying clinics at $99/month.",
        confidence: 0.69,
        source_type: "internal_analysis",
        summary: "The 24-month model is sensitive to sales-cycle length and clinician onboarding cost.",
        sources: [{ title: "Financial model", url: "", snippet: "Base case: 14k clinics at $99." }],
      };
    case "Marketing":
      return {
        claim: "A survey of 38 clinics found that 62% would trial an AI triage product.",
        confidence: 0.64,
        source_type: "survey",
        summary: "Demand signal is positive but the sample is small, so the estimate carries wide uncertainty.",
        sources: [{ title: "Clinic survey", url: "", snippet: "38 respondents, 62% trial intent." }],
      };
    case "Legal":
      return {
        claim: "Diagnostic claims may require FDA clearance; a non-diagnostic triage assistant has a lower regulatory burden.",
        confidence: 0.81,
        source_type: "web",
        summary: "Regulatory review should start before any clinical claim is made in marketing or product copy.",
        sources: [{ title: "Regulatory overview", url: "https://example.com/fda", snippet: "Clinical claims trigger device review." }],
      };
    default:
      return {
        claim: `The organization should evaluate ${prompt} with a bounded pilot.`,
        confidence: 0.6,
        source_type: "internal_analysis",
        summary: "A bounded pilot limits downside while producing real evidence.",
        sources: [{ title: "Department brief", url: "", snippet: "Pilot first." }],
      };
  }
}

export function challengeFixture(departmentName) {
  return {
    challenge: departmentName === "Marketing"
      ? "The survey covers only 38 clinics; the sample is too small and may overstate trial intent."
      : "The claim needs a recency check and a clearer source trail before it shapes the recommendation.",
    severity: "medium",
  };
}

export function debateFixture(ctx) {
  const department = ctx.department ? ctx.department.name : "Department";
  if (ctx.round > 1) {
    return {
      text: `Round 2 (${department}): I still see execution risk, but the evidence supports a bounded, non-diagnostic pilot rather than a full launch.`,
      stance: "qualified",
    };
  }
  return {
    text: `Round 1 (${department}): The evidence supports proceeding, provided the organization validates demand and regulatory scope first.`,
    stance: "support",
  };
}

export function synthesisFixture() {
  return {
    recommendation: "Proceed with a 12-week, HIPAA-first pilot limited to non-diagnostic triage.",
    confidence: 0.78,
    executive_summary: "The organization should run a bounded pilot before committing to a full product launch.",
    dissent: ["Legal: do not make diagnostic claims before FDA review."],
    risks: ["Long clinic sales cycles", "Regulatory scope creep", "Small initial demand sample"],
    opportunities: ["Outpatient triage demand", "A narrow workflow can launch before clinical validation"],
    next_steps: ["Recruit 5 pilot clinics", "Complete a HIPAA review", "Freeze non-diagnostic product copy"],
  };
}

export function reportFixture(ctx) {
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
    full_discussion_summary: ctx.transcript || "The board debated the recommendation over two rounds.",
  };
}
