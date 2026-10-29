"""Kurumi 0.6 Python agent core.

Python owns conversation state, model/tool loop and task policy.
Java must provide only a narrow transport (model_request, tool_call,
chat_send, stop_check); Minecraft operations stay on the game thread.
This module is deliberately transport-agnostic and importable by GraalPy.
"""
import json
import time


class AgentStopped(Exception):
    pass


class AgentBudgetExceeded(Exception):
    pass


class KurumiAgent:
    """A resumable model/tool agent with bounded execution and explicit cancellation."""

    def __init__(self, model_request, tool_call, chat_send, stop_check,
                 *, max_steps=64, max_seconds=1200, max_history=20):
        self.model_request = model_request
        self.tool_call = tool_call
        self.chat_send = chat_send
        self.stop_check = stop_check
        self.max_steps = max_steps
        self.max_seconds = max_seconds
        self.max_history = max_history
        self.history = []
        self.stopped = False

    def stop(self):
        self.stopped = True

    def check_stop(self):
        if self.stopped or self.stop_check():
            raise AgentStopped("Kurumi was stopped")

    def invoke_tool(self, name, *args):
        self.check_stop()
        if not isinstance(name, str) or not name or name.startswith("_"):
            raise ValueError("Invalid tool name")
        response = self.tool_call(name, *args)
        self.check_stop()
        return response

    def _request(self, messages):
        self.check_stop()
        raw = self.model_request(json.dumps(messages, ensure_ascii=False))
        self.check_stop()
        if isinstance(raw, str):
            return json.loads(raw)
        if isinstance(raw, dict):
            return raw
        raise ValueError("Model callback must return a JSON object")

    def respond(self, sender, message, system_prompt):
        """Execute an agent request. Java may schedule this on a worker, never render thread.

        Model protocol:
          {"type":"tool", "name":"get_inventory", "args":[]}
          {"type":"say", "message":"..."}
          {"type":"finish", "message":"..."}
        """
        if not isinstance(message, str) or not message.strip():
            raise ValueError("Empty user message")
        deadline = time.monotonic() + self.max_seconds
        transcript = ([{"role": "system", "content": system_prompt}]
                      + self.history[-self.max_history:]
                      + [{"role": "user", "content": str(sender) + ": " + message}])
        for _ in range(self.max_steps):
            self.check_stop()
            if time.monotonic() >= deadline:
                raise AgentBudgetExceeded("Agent execution deadline reached")
            action = self._request(transcript)
            if not isinstance(action, dict):
                raise ValueError("Model response must be an object")
            kind = action.get("type")
            if kind == "tool":
                name = action.get("name")
                args = action.get("args", [])
                if not isinstance(args, list) or len(args) > 16:
                    raise ValueError("Tool args must be a list with at most 16 items")
                result = self.invoke_tool(name, *args)
                transcript.append({"role": "assistant", "content": json.dumps(action, ensure_ascii=False)})
                transcript.append({"role": "tool", "content": json.dumps(result, ensure_ascii=False, default=str)})
            elif kind in ("say", "finish"):
                reply = action.get("message", "")
                if not isinstance(reply, str):
                    raise ValueError("Chat text must be a string")
                self.check_stop()
                if reply:
                    self.chat_send(reply)
                if kind == "finish":
                    self.history = transcript[-self.max_history:]
                    return {"success": True, "message": reply}
                transcript.append({"role": "assistant", "content": reply})
            else:
                raise ValueError("Unsupported model action type: " + str(kind))
        raise AgentBudgetExceeded("Agent step budget reached")
