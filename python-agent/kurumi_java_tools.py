"""Authenticated local Java game-tool client for LangGraph skills."""
import json
import socket


class JavaToolClient:
    def __init__(self, token: str, port: int = 47832, timeout: float = 20.0):
        if not token or len(token) != 64:
            raise ValueError("Expected a 64-character session token from Java")
        self.token = token
        self.port = port
        self.timeout = timeout

    def __call__(self, name: str, args: list):
        if not isinstance(name, str) or not isinstance(args, list):
            raise TypeError("Java tool call requires name and argument list")
        message = json.dumps({"token": self.token, "tool": name, "args": args},
                             ensure_ascii=False, separators=(",", ":")) + "\n"
        if len(message.encode("utf-8")) > 12000:
            raise ValueError("Tool request exceeds 12KB")
        # Serialized by the LangGraph session; no persistent unauthenticated port.
        with socket.create_connection(("127.0.0.1", self.port), timeout=self.timeout) as sock:
            sock.settimeout(self.timeout)
            sock.sendall(message.encode("utf-8"))
            with sock.makefile("rb") as stream:
                response = stream.readline(64000)
        if not response or len(response) >= 64000:
            raise OSError("Java game-tool response missing or too large")
        data = json.loads(response)
        if not isinstance(data, dict):
            raise ValueError("Unexpected Java game-tool response")
        return data
