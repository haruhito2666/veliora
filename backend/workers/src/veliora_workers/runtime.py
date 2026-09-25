"""Worker runtime entry point: consumes task events from NATS JetStream
and executes them against the LLM (doc 05 §6, doc 12 Phase 12).

Run: uv run python -m veliora_workers.runtime
"""
from __future__ import annotations

import asyncio
import json
import logging
import os
from typing import Any

import nats

from .llm import LLM, LLMError, OpenAILLM, ScriptedLLM
from .prompts import PromptContext
from .tasks import (
    board_report,
    challenge_evidence,
    collect_evidence,
    debate_turn,
    plan_task,
    synthesize,
    vote_choice,
)
from .tools import WebSearch

log = logging.getLogger("veliora-workers")

SUBJECT_PREFIX = "org"


def make_llm() -> LLM:
    if os.environ.get("VELIORA_SCRIPTED_LLM") == "1":
        return ScriptedLLM()
    return OpenAILLM()


class TaskMessage:
    def __init__(self, raw: dict[str, Any]) -> None:
        self.task_id: str = raw.get("task_id", "")
        self.org_id: str = raw.get("organization_id", "")
        self.investigation_id: str = raw.get("investigation_id", "")
        self.worker_id: str = raw.get("worker_id", "")
        self.role: str = raw.get("role", "RESEARCHER")
        self.department: str = raw.get("department", "Research Division")
        self.task_type: str = raw.get("task_type", "collect_evidence")
        self.prompt: str = raw.get("prompt", "")
        self.payload: dict[str, Any] = raw.get("payload", {})
        self.plan: list[dict[str, Any]] = raw.get("plan", [])


class Runtime:
    def __init__(self, llm: LLM, nats_url: str = "nats://localhost:4222") -> None:
        self.llm = llm
        self.nats_url = nats_url
        self.web = WebSearch()
        self.nc: nats.aio.client.Client | None = None

    async def connect(self) -> None:
        self.nc = await nats.connect(self.nats_url, max_reconnect_attempts=-1)

    async def run(self) -> None:
        await self.connect()
        assert self.nc is not None
        # Queue group per (org, task_type) for fair distribution.
        await self.nc.subscribe(
            f"{SUBJECT_PREFIX}.*.task.*",
            cb=self._on_task,
            queue="workers",
        )
        log.info("worker runtime listening on org.*.task.*")
        await asyncio.Event().wait()

    async def _on_task(self, msg: nats.aio.msg.Msg) -> None:
        try:
            raw = json.loads(msg.data)
            task = TaskMessage(raw)
            log.info("task %s (%s) for %s", task.task_id, task.task_type, task.department)
            result = await asyncio.to_thread(self._execute, task)
            # Publish the result event on the org stream.
            await self.nc.publish(
                f"{SUBJECT_PREFIX}.{task.org_id}.task.{task.task_type}",
                json.dumps({"task_id": task.task_id, "result": result, "ok": True}).encode(),
            )
            await msg.ack()
        except LLMError as e:
            log.error("llm error: %s", e)
            await msg.ack()
        except Exception as e:  # noqa: BLE001
            log.exception("task failed: %s", e)
            await msg.ack()

    def _execute(self, task: TaskMessage) -> dict[str, Any]:
        ctx = PromptContext(
            org_name="Veliora Organization",
            org_type="Startup Society",
            mission="Investigate and decide on startup viability.",
            constitution={"principles": ["Evidence before opinion"]},
            dna={"accuracy": 0.8, "speed": 0.6},
            role=task.role,
            department=task.department,
            investigation_goal=task.prompt,
            task=task.payload.get("description", ""),
        )
        switch: dict[str, Any] = {
            "plan": lambda: plan_task(self.llm, ctx),
            "collect_evidence": lambda: collect_evidence(self.llm, ctx, self.web),
            "challenge": lambda: challenge_evidence(
                self.llm, ctx, task.payload.get("other_claim", "")),
            "debate_turn": lambda: {"text": debate_turn(
                self.llm, ctx, task.payload.get("other_turns", ""), task.payload.get("round", 1))},
            "synthesize": lambda: synthesize(self.llm, ctx, task.payload.get("stances", "")),
            "report": lambda: board_report(
                self.llm, ctx, task.payload.get("evidence", ""), task.payload.get("dissent", "")),
            "vote": lambda: vote_choice(self.llm, ctx, task.payload.get("recommendation", "")),
        }
        handler = switch.get(task.task_type)
        if handler is None:
            raise ValueError(f"unknown task type: {task.task_type}")
        return handler()


async def main() -> None:
    logging.basicConfig(level=logging.INFO)
    llm = make_llm()
    runtime = Runtime(llm, os.environ.get("NATS_URL", "nats://localhost:4222"))
    await runtime.run()


if __name__ == "__main__":
    asyncio.run(main())
