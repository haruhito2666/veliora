import {
  agentNameFor,
  buildSystemPrompt,
  challengeFixture,
  debateFixture,
  evidenceFixture,
  reportFixture,
  synthesisFixture,
} from "./departments.js";

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

export class LLM {
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
        "Return only a JSON object. Do not wrap it in markdown.",
      ].join("\n\n");

      if (this.config.mode === "agent") {
        const name = agentNameFor(ctx.department);
        const run = await sofuu.agent.run(name, prompt);
        return extractJSON(run.answer || run.result || {});
      }

      if (!this.config.provider || !this.config.model) {
        throw new Error("provider and model are required for non-scripted mode");
      }
      const response = await sofuu.ai.complete(prompt, {
        provider: this.config.provider,
        model: this.config.model,
        temperature: 0.2,
      });
      return extractJSON(response && response.text !== undefined ? response.text : response);
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
        "Return JSON with keys: claim, confidence (0-1), source_type (paper|survey|external_comm|internal_analysis|web), summary, sources (array of {title,url,snippet}).",
      ].join("\n"),
      () => evidenceFixture(ctx.department.name, ctx.inv.prompt),
    );
  }

  async challenge(ctx) {
    return this._completeJSON(
      ctx,
      [
        `Review this claim from a peer department: "${ctx.evidence.claim}".`,
        "If it is weak, explain specifically (sample size, bias, relevance, recency).",
        "If it is strong, return {\"challenge\": null}.",
        "Return JSON with keys: challenge, severity (low|medium|high).",
      ].join("\n"),
      () => challengeFixture(ctx.evidence.department),
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
        "Return JSON with keys: text, stance (support|oppose|qualified).",
      ].join("\n"),
      () => debateFixture(ctx),
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
        "Return JSON with keys: recommendation, confidence (0-1), executive_summary, dissent (array), risks (array), opportunities (array), next_steps (array).",
      ].join("\n"),
      () => synthesisFixture(),
    );
  }

  async vote(ctx) {
    return this._completeJSON(
      ctx,
      [
        `Draft recommendation: ${ctx.recommendation}`,
        `Your department's position: ${ctx.position || "not recorded"}`,
        "Cast one vote with one line of reasoning.",
        "Return JSON with keys: choice (yes|no|abstain), reasoning.",
      ].join("\n"),
      () => ({ choice: "yes", reasoning: "The bounded-pilot evidence supports the recommendation." }),
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
        "Return JSON with keys: executive_summary, recommendation, confidence, dissenting_opinions (array), supporting_evidence (array), risks (array), opportunities (array), next_steps (array), full_discussion_summary.",
      ].join("\n"),
      () => reportFixture(ctx),
    );
  }
}
