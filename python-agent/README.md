# Kurumi 0.6.0 — LangGraph

**Decision: independent CPython process with LangGraph.** GraalPy remains in the repo as a compatibility experiment; the intended 0.6.0 architecture is a CPython service, not an embedded third-party runtime inside NeoForge.

The first executable agent graph is `kurumi_langgraph.py`. It has `think → act → observe → think` edges and a final-answer route. The model returns a JSON object (`{"type":"tool","name":"get_position","args":[]}` or `{"type":"finish","message":"..."}`). Java must supply the model and tool transport callbacks; no Minecraft client integration is wired up yet.

## Developer setup

```powershell
cd E:\code\mcAimod\python-agent
py -3 -m venv .venv
.\.venv\Scripts\python.exe -m pip install -r requirements.txt
.\.venv\Scripts\python.exe -m unittest discover -s . -p "test_*.py"
```

This tests only the Python core. It **does not** compile/install the Mod.

## Skills

`kurumi_skills.py` provides a registry of capabilities backed by permitted Java game tools.
The LangGraph agent can request `{"type":"tool","name":"list_skills","args":[]}`
to discover available skills, then call a skill by name:

```json
{"type":"tool","name":"navigate","args":[-21,64,27]}
```

Current example skills (registered only if the Java tool permission set allows
their primitives): `status`, `navigate`, `harvest`, `collect_drops`,
`inspect_chest`, `store_item`, `retrieve_item`, `equip`, `fight`,
`rest`, `start_fishing`, `stop_fishing`, and `examine_area`.
Existing low-level Java tools remain available for granular operation; the
agent may compose skills in its LangGraph reasoning loop. A successful Java
tool dispatch is **not** proof an in-game action has completed.

These are currently Python functions, not independently installable SKILL.md
packages. Future migration may add skill manifests, persistence, and user
provided skills with explicit permission review. Java IPC and in-game
integration are **still not implemented**.

## Remaining integration work

1. Start and supervise the Python process from the Java client lifecycle (Windows and cross-platform support).
2. Implement authenticated loopback Java↔Python IPC for chat, model settings, game tools, errors and emergency stop.
3. Preserve existing owner authorization and fast chat/stop behavior in Java; enforce tool permissions at the Java boundary, too.
4. Move model HTTP requests, model profile selection, prompts, persistent memories, skill definitions and recovery policy into Python.
5. Cancel model network calls and pending world actions promptly when emergency stop is triggered.
6. Replace the old Java runAgent route only after end-to-end tests and successful NeoForge startup validation.

**This is an incremental 0.6.0 source implementation, not a ready-to-install working LangGraph Mod.**
