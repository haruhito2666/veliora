"""Worker task handlers (doc 19 phases). Each returns structured JSON."""
from __future__ import annotations

from typing import Any

from .llm import LLM
from .prompts import PromptContext


def plan_task(llm: LLM, ctx: PromptContext) -> dict[str, Any]:
    """Planner: decompose the goal into a per-department task graph (doc 18)."""
    messages = ctx.compose()
    messages.append({
        "role": "user",
        "content": "Decompose this goal into one task per department (Research Division, Engineering, "
                   "Finance, Marketing, Legal). Return JSON: {\"tasks\": [{\"department\": ..., "
                   "\"description\": ..., \"deliverables\": ...}]}",
    })
    return llm.complete_json(messages)


def collect_evidence(llm: LLM, ctx: PromptContext, web_tool: Any = None) -> dict[str, Any]:
    """Research/collect phase: produce evidence records (doc 19 §4.2)."""
    sources: list[dict[str, str]] = []
    if web_tool is not None:
        try:
            sources = web_tool.search(ctx.investigation_goal, limit=3)
        except Exception:
            sources = []
    messages = ctx.compose()
    messages.append({
        "role": "user",
        "content": f"Produce a single evidence record (claim, confidence 0-1, source type, summary) "
                   f"backed by these web results: {sources}. Return JSON with keys: "
                   "claim, confidence, source_type, summary.",
    })
    return llm.complete_json(messages)


def challenge_evidence(llm: LLM, ctx: PromptContext, other_claim: str) -> dict[str, Any]:
    """Analysis phase: critique another department's evidence (doc 10 §4.1)."""
    messages = ctx.compose()
    messages.append({
        "role": "user",
        "content": f"Review this claim from a peer department: \"{other_claim}\". "
                   "If it is weak, explain specifically (sample size, bias, relevance, recency). "
                   "If strong, return {\"challenge\": null}. Return JSON with keys: challenge, severity.",
    })
    return llm.complete_json(messages)


def debate_turn(llm: LLM, ctx: PromptContext, other_turns: str, round_no: int) -> str:
    """Debate phase: one Society-of-Thought turn (doc 10 §6)."""
    messages = ctx.compose()
    messages.append({
        "role": "user",
        "content": f"Round {round_no}. Other statements this round: {other_turns}. "
                   "State your position. Cite evidence. Disagree openly if another claim is unsupported. "
                   "Be specific and short (2-3 sentences).",
    })
    return llm.complete_text(messages)


def synthesize(llm: LLM, ctx: PromptContext, stances: str) -> dict[str, Any]:
    """Synthesis: CEO drafts the recommendation (doc 19 §4.5)."""
    messages = ctx.compose()
    messages.append({
        "role": "user",
        "content": f"Departmental stances: {stances}. Produce a single synthesized recommendation. "
                   "Return JSON with keys: recommendation, confidence, executive_summary, risks, "
                   "opportunities, next_steps.",
    })
    return llm.complete_json(messages)


def board_report(llm: LLM, ctx: PromptContext, evidence: str, dissent: str) -> dict[str, Any]:
    """Report phase: assemble the 9-section board report (doc 19 §10)."""
    messages = ctx.compose()
    messages.append({
        "role": "user",
        "content": f"Upheld evidence: {evidence}. Dissenting opinions: {dissent}. "
                   "Assemble the final board report. Return JSON with keys: executive_summary, "
                   "recommendation, confidence, dissenting_opinions, supporting_evidence, risks, "
                   "opportunities, next_steps, full_discussion_summary.",
    })
    return llm.complete_json(messages)


def vote_choice(llm: LLM, ctx: PromptContext, recommendation: str) -> dict[str, Any]:
    """Vote phase: a department casts a weighted vote (doc 10 §8.4)."""
    messages = ctx.compose()
    messages.append({
        "role": "user",
        "content": f"Draft recommendation: {recommendation}. Cast your vote: yes / no / abstain, "
                   "with one-line reasoning. Return JSON with keys: choice, reasoning.",
    })
    return llm.complete_json(messages)
