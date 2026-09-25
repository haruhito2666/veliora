"""Worker runtime tests using the ScriptedLLM test adapter (never in prod)."""
from __future__ import annotations

from veliora_workers.llm import ScriptedLLM
from veliora_workers.prompts import PromptContext
from veliora_workers.tasks import (
    board_report,
    challenge_evidence,
    collect_evidence,
    debate_turn,
    plan_task,
    synthesize,
    vote_choice,
)


def test_prompt_composer_layers() -> None:
    ctx = PromptContext(
        org_name="Startup Society",
        org_type="startup",
        mission="test",
        role="RESEARCHER",
        department="Research Division",
        investigation_goal="Should we build X?",
        task="size the market",
    )
    messages = ctx.compose(budget=100000)
    roles = [m["role"] for m in messages]
    assert roles[0] == "system"
    # All five layers present.
    assert any("[ROLE]" in m["content"] for m in messages)
    assert any("[TASK]" in m["content"] for m in messages)
    assert any("[CONTEXT]" in m["content"] for m in messages)


def test_prompt_composer_truncates_over_budget() -> None:
    ctx = PromptContext(
        org_name="X", org_type="y", mission="m", role="r", department="d",
        investigation_goal="g" * 5000,
    )
    messages = ctx.compose(budget=1000)
    total = sum(len(m["content"]) for m in messages)
    assert total < 5000, "over-budget prompt should be truncated"
    assert any("TRUNCATED" in m["content"] for m in messages)


def test_plan_task_scripted() -> None:
    llm = ScriptedLLM()
    ctx = PromptContext(org_name="S", org_type="startup", mission="m", role="CEO",
                        department="CEO Office", investigation_goal="build an app")
    out = plan_task(llm, ctx)
    assert "tasks" in out
    assert len(out["tasks"]) == 5


def test_collect_evidence_scripted() -> None:
    llm = ScriptedLLM()
    ctx = PromptContext(org_name="S", org_type="startup", mission="m", role="RESEARCHER",
                        department="Research Division", investigation_goal="market size?")
    out = collect_evidence(llm, ctx, web_tool=None)
    assert out["claim"] == "The market is growing."
    assert out["confidence"] == 0.7


def test_challenge_evidence_scripted() -> None:
    llm = ScriptedLLM()
    ctx = PromptContext(org_name="S", org_type="startup", mission="m", role="ENGINEER",
                        department="Engineering", investigation_goal="feasible?")
    out = challenge_evidence(llm, ctx, "the market is $45B")
    assert out["challenge"] == "sample size too small"


def test_debate_turn_scripted() -> None:
    llm = ScriptedLLM()
    ctx = PromptContext(org_name="S", org_type="startup", mission="m", role="MARKETER",
                        department="Marketing", investigation_goal="demand?")
    text = debate_turn(llm, ctx, "Finance says no", round_no=1)
    assert "Scripted" in text


def test_board_report_scripted() -> None:
    llm = ScriptedLLM()
    ctx = PromptContext(org_name="S", org_type="startup", mission="m", role="CEO",
                        department="CEO Office", investigation_goal="go?")
    out = board_report(llm, ctx, "evidence", "dissent")
    assert out["recommendation"] == "proceed_with_conditions"
    assert out["confidence"] == 0.78


def test_vote_choice_scripted() -> None:
    llm = ScriptedLLM()
    ctx = PromptContext(org_name="S", org_type="startup", mission="m", role="FINANCE_ANALYST",
                        department="Finance", investigation_goal="go?")
    out = vote_choice(llm, ctx, "GO")
    assert out["choice"] == "yes"


def test_synthesize_scripted() -> None:
    llm = ScriptedLLM()
    ctx = PromptContext(org_name="S", org_type="startup", mission="m", role="CEO",
                        department="CEO Office", investigation_goal="go?")
    out = synthesize(llm, ctx, "GO vs HOLD")
    assert out["recommendation"] == "proceed_with_conditions"
    assert out["confidence"] == 0.74
