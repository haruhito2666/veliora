"""Provider-agnostic LLM client with a scripted test adapter."""
from __future__ import annotations

import json
import os
from abc import ABC, abstractmethod
from typing import Any

from openai import OpenAI


class LLMError(Exception):
    pass


class LLM(ABC):
    @abstractmethod
    def complete_json(self, messages: list[dict[str, str]], temperature: float = 0.2) -> dict[str, Any]:
        """Return a JSON object from the model."""
        ...

    @abstractmethod
    def complete_text(self, messages: list[dict[str, str]], temperature: float = 0.2) -> str:
        """Return free text from the model."""
        ...


class OpenAILLM(LLM):
    """Real OpenAI client (gpt-4o default, gpt-4o-mini for cheap turns)."""

    def __init__(self) -> None:
        key = os.environ.get("OPENAI_API_KEY")
        if not key:
            raise LLMError("OPENAI_API_KEY not set")
        self.client = OpenAI(api_key=key)
        self.model = os.environ.get("VELIORA_LLM_MODEL", "gpt-4o")
        self.cheap_model = os.environ.get("VELIORA_LLM_MODEL_CHEAP", "gpt-4o-mini")

    def _model_for(self, cheap: bool) -> str:
        return self.cheap_model if cheap else self.model

    def complete_json(self, messages: list[dict[str, str]], temperature: float = 0.2) -> dict[str, Any]:
        resp = self.client.chat.completions.create(
            model=self._model_for(cheap=False),
            messages=messages,
            temperature=temperature,
            response_format={"type": "json_object"},
        )
        text = resp.choices[0].message.content or "{}"
        try:
            return json.loads(text)
        except json.JSONDecodeError as e:
            raise LLMError(f"model returned invalid JSON: {text[:200]}") from e

    def complete_text(self, messages: list[dict[str, str]], temperature: float = 0.2) -> str:
        resp = self.client.chat.completions.create(
            model=self._model_for(cheap=True),
            messages=messages,
            temperature=temperature,
        )
        return resp.choices[0].message.content or ""


class ScriptedLLM(LLM):
    """Test-only deterministic adapter (never reachable from the app).

    Returns canned JSON keyed by a hint in the user message.
    """

    def __init__(self) -> None:
        self.calls: list[list[dict[str, str]]] = []

    def complete_json(self, messages: list[dict[str, str]], temperature: float = 0.2) -> dict[str, Any]:
        self.calls.append(messages)
        joined = " ".join(m.get("content", "") for m in messages).lower()
        # Match on the explicit task instruction, not fuzzy keywords.
        if "decompose this goal into one task per department" in joined:
            return {
                "tasks": [
                    {"department": "Research Division", "description": "size the market", "deliverables": "brief"},
                    {"department": "Engineering", "description": "assess feasibility", "deliverables": "verdict"},
                    {"department": "Finance", "description": "build forecast", "deliverables": "model"},
                    {"department": "Marketing", "description": "survey demand", "deliverables": "signal"},
                    {"department": "Legal", "description": "check compliance", "deliverables": "register"},
                ]
            }
        if "review this claim from a peer department" in joined:
            return {"challenge": "sample size too small", "severity": "medium"}
        if "assemble the final board report" in joined:
            return {
                "summary": "Executive summary",
                "recommendation": "proceed_with_conditions",
                "confidence": 0.78,
                "risks": ["regulatory uncertainty"],
                "opportunities": ["large market"],
                "next_steps": ["90-day validation"],
            }
        if "cast your vote" in joined:
            return {"choice": "yes", "reasoning": "evidence supports it"}
        if "produce a single synthesized recommendation" in joined:
            return {
                "recommendation": "proceed_with_conditions",
                "confidence": 0.74,
                "executive_summary": "Balanced view.",
                "risks": [], "opportunities": [], "next_steps": [],
            }
        # collect_evidence default.
        return {
            "claim": "The market is growing.",
            "confidence": 0.7,
            "source_type": "web",
            "summary": "Real web result summary.",
        }

    def complete_text(self, messages: list[dict[str, str]], temperature: float = 0.2) -> str:
        self.calls.append(messages)
        return "Scripted deterministic response."
