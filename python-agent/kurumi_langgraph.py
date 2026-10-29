"""Kurumi 0.6.0 LangGraph orchestration core (requires CPython)."""
from __future__ import annotations

import json
import threading
import time
from typing import Any, Callable, TypedDict

from langgraph.graph import END, START, StateGraph


class AgentState(TypedDict):
    messages: list[dict[str, str]]
    decision: dict[str, Any]
    steps: int
    deadline: float
    result: dict[str, Any]


class Stopped(Exception):
    pass


class KurumiGraph:
    """The model chooses an allowlisted Minecraft tool or a final answer.

    All Minecraft tool execution is delegated to Java. Calls should return a
    structured result with completion status, not a fabricated success flag.
    """

    def __init__(
        self,
        model: Callable[[list[dict[str, str]]], dict[str, Any]],
        tool: Callable[[str, list[Any]], Any],
        stopped: Callable[[], bool],
        *,
        allowed_tools: set[str],
        max_steps: int = 64,
        deadline_seconds: int = 1200,
    ):
        self.model, self.tool, self.stopped = model, tool, stopped
        self.allowed_tools = frozenset(allowed_tools)
        self.max_steps, self.deadline_seconds = max_steps, deadline_seconds
        graph = StateGraph(AgentState)
        graph.add_node("think", self._think)
        graph.add_node("act", self._act)
        graph.add_edge(START, "think")
        graph.add_conditional_edges("think", self._route, {"act": "act", "end": END})
        graph.add_edge("act", "think")
        self.graph = graph.compile()
        self.lock = threading.RLock()
        self.history: dict[str, list[dict[str, str]]] = {}

    def _guard(self, state: AgentState):
        if self.stopped():
            raise Stopped("Emergency stop requested")
        if state["steps"] >= self.max_steps:
            raise RuntimeError("Agent step budget reached")
        if time.monotonic() > state["deadline"]:
            raise TimeoutError("Agent execution deadline reached")

    def _think(self, state: AgentState) -> dict:
        self._guard(state)
        decision = self.model(state["messages"])
        if not isinstance(decision, dict) or decision.get("type") not in {"tool", "finish"}:
            raise ValueError("Expected model JSON object with type=tool|finish")
        if self.stopped():
            raise Stopped("Emergency stop requested")
        return {"decision": decision, "steps": state["steps"] + 1}

    @staticmethod
    def _route(state: AgentState) -> str:
        return "act" if state["decision"]["type"] == "tool" else "end"

    def _act(self, state: AgentState) -> dict:
        self._guard(state)
        decision = state["decision"]
        name, args = decision.get("name"), decision.get("args", [])
        if name not in self.allowed_tools:
            raise PermissionError(f"Unapproved game tool: {name!r}")
        if not isinstance(args, list) or len(args) > 16:
            raise ValueError("Tool args must be a list of up to 16 values")
        outcome = self.tool(name, args)
        if self.stopped():
            raise Stopped("Emergency stop requested")
        return {"messages": state["messages"] + [
            {"role": "assistant", "content": json.dumps(decision, ensure_ascii=False)},
            {"role": "user", "content": "Tool result for " + name + ": " +
             json.dumps(outcome, ensure_ascii=False, default=str) +
             ". Check whether the action really completed before continuing."}
        ]}

    def run(self, sender: str, message: str, system_prompt: str) -> dict:
        """Serializes sessions for now; only trusted Java should call this."""
        with self.lock:
            history = self.history.get(sender, [])
            messages = [{"role": "system", "content": system_prompt}] + history[-20:] + [
                {"role": "user", "content": message}
            ]
            state: AgentState = {
                "messages": messages, "decision": {}, "steps": 0,
                "deadline": time.monotonic() + self.deadline_seconds, "result": {}
            }
            output = self.graph.invoke(state, config={"recursion_limit": self.max_steps * 3 + 5})
            decision = output["decision"]
            response = str(decision.get("message", ""))[:1200]
            self.history[sender] = (output["messages"] +
                                    [{"role": "assistant", "content": response}])[-20:]
            return {"success": True, "reply": response, "steps": output["steps"]}
