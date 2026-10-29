# Kurumi 0.6.0 Python-first migration

## Intended division of responsibilities

- **Java/NeoForge:** receive player chat; fast wake/stop triggers; Minecraft and Baritone tool adapters; marshal game operations to the Minecraft client thread; provide a safe transport into GraalPy; lifecycle cleanup.
- **Python/GraalPy:** model request orchestration, conversation history, prompts, agent state, decision loop, tool sequencing, memory, and Python modules.
- **Emergency stop:** Java sets a cancellation signal immediately and cancels active Python calls. The Python loop checks the signal before and after every callback.

## What is implemented on the 0.6.0 branch/main so far

- Version bumped to 0.6.0.
- `python-agent/kurumi_agent.py`: a model/tool callback-driven Python agent loop with bounded steps, deadlines, chat sending, history and stop checks.
- Existing Java/GraalPy script bridge and legacy executor are retained.

## Not yet implemented / must not be claimed as complete

1. Connect the Python agent model callback to the model provider and migrate the current model configuration semantics.
2. Route all recognized in-game chats from Java straight into a persistent Python agent session, rather than the existing `KurumiClientController.runAgent`.
3. Move prompts, skills, memory and task planning from Java into Python; verify behavior parity.
4. Expose only explicitly authorized game tools via Java callbacks; enforce per-player permissions at the Java boundary.
5. Guarantee emergency-stop responsiveness during in-flight model HTTP requests, pending game-thread actions, and GraalPy execution.
6. Package the Python modules and the full GraalPy runtime into the production Mod JAR; prove class loading and runtime operation in NeoForge.
7. Add tests and run `gradlew build` and an in-game smoke test.

Until steps 1–7 are finished, this is a **0.6.0 development foundation**, not an end-to-end Python-owned agent.

## API sketch

```python
from kurumi_agent import KurumiAgent

agent = KurumiAgent(
    model_request=java_model_request,   # JSON string in / JSON string out
    tool_call=java_tool_call,           # name, *args -> serializable result
    chat_send=java_chat_send,           # send public chat
    stop_check=java_stop_requested,     # no network dependency
)
agent.respond(sender="owner", message="collect wood", system_prompt=prompt)
```

The in-game chat, model transport, tool execution and safety decisions still need to be wired by Java. The Python agent module alone is not a working Mod.
