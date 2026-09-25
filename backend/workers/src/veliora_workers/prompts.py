"""5-layer prompt composer (doc 17 §2–§5)."""
from __future__ import annotations

from dataclasses import dataclass, field
from typing import Any


@dataclass
class PromptContext:
    """Everything needed to assemble a worker prompt (L1–L5)."""

    org_name: str
    org_type: str
    mission: str
    constitution: dict[str, Any] = field(default_factory=dict)
    dna: dict[str, float] = field(default_factory=dict)
    org_state: str = ""
    role: str = ""
    department: str = ""
    department_charter: str = ""
    scope: str = ""
    peers: str = ""
    investigation_goal: str = ""
    plan_summary: str = ""
    task: str = ""
    deliverables: str = ""
    memory: str = ""
    constraints: str = ""

    def layer1(self) -> str:
        return f"""You are a worker in {self.org_name}, a {self.org_type} organization.
MISSION: {self.mission}
CONSTITUTION: {self.constitution}
ORGANIZATIONAL DNA: {self.dna}
ORGANIZATIONAL STATE: {self.org_state}"""

    def layer2(self) -> str:
        return f"""YOUR ROLE:
Department: {self.department}
Title: {self.role}
Charter: {self.department_charter}
SCOPE: {self.scope}
PEERS: {self.peers}"""

    def layer3(self) -> str:
        return f"""INVESTIGATION CONTEXT:
Goal: {self.investigation_goal}
Plan summary: {self.plan_summary}
RELEVANT INSTITUTIONAL MEMORY:
{self.memory}"""

    def layer4(self) -> str:
        return f"""TASK:
{self.task}
DELIVERABLES: {self.deliverables}"""

    def layer5(self) -> str:
        return f"""CONSTRAINTS:
{self.constraints}"""

    def compose(self, budget: int = 8000) -> list[dict[str, str]]:
        """Assemble the five layers in order (doc 17 §2.2). Truncates the
        context layer first when over budget."""
        layers = [
            ("system", self.layer1()),
            ("role", self.layer2()),
            ("context", self.layer3()),
            ("task", self.layer4()),
            ("constraints", self.layer5()),
        ]
        total = sum(len(t) for _, t in layers)
        # Soft budget: drop the context layer to a summary when over.
        if total > budget:
            layers = [
                (r, t) for r, t in layers if r != "context"
            ] + [("context", "CONTEXT TRUNCATED (budget exceeded).")]
        messages: list[dict[str, str]] = [{"role": "system", "content": layers[0][1]}]
        for role, text in layers[1:]:
            messages.append({"role": "user", "content": f"[{role.upper()}]\n{text}"})
        return messages
