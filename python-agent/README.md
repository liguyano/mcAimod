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

## Current Java ↔ Python integration (experimental)

The NeoForge client now starts a local CPython process from the extracted
`config/mcaimod-agent/` sources when it joins a world. Agent sources are
packed inside the Mod JAR by Gradle. Configure CPython once on Windows:

```powershell
cd E:\code\mcAimod
git pull origin main
.\setup-agent.ps1
```

This installs third-party dependencies in the instance's
`config/mcaimod-agent/.venv/` directory; the Mod JAR does **not** bundle
CPython or pip wheels. When joining a world, Java launches the Python server
on loopback port 47833 and uses a random bearer token. A second authenticated
loopback channel on port 47832 forwards selected Python skill game-tool calls
to the Minecraft main thread. The owner SekaiSaionji's ordinary chat goes
to LangGraph if the Python process is alive; the old Java flow remains a
fallback. Java direct stop invalidates pending game calls and sends a
Python stop signal. Use `-Dmcaimod.agentEngine=legacy` to force the old
agent, or `-Dmcaimod.python=C:\path\to\python.exe` to override Python.

**Current known limits:** this has not been compiled or smoke-tested in
Minecraft; the process can be alive before its HTTP server is ready, causing
an initial chat failure. Python credentials are read directly from the
instance's `mcaimod-models.json` and not printed in logs. Only a small set
of game tools is connected. Action results generally report request accepted
rather than actual game completion; the Agent must re-observe. Emergency stop
does not yet guarantee interruption of an in-flight model HTTP call. This
release should be treated as a development build.

## Date-aware persistent memory

The CPython LangGraph agent uses `kurumi_memory.py` (SQLite, standard
library) and stores records at `config/mcaimod-agent/memory/conversations.db`.
Conversation turns include ISO timestamps and sender identifiers; explicit
long-term facts are stored separately. At each agent run the context contains
the local date/time, yesterday's date, recent exchanges and up to 20 of
yesterday's messages. `remember_fact` is a model-callable action that stores
one explicitly requested fact; it is **not** automatic extraction of user
preferences from all conversations. The default timezone is `Asia/Shanghai`,
overridable through the `KURUMI_TIMEZONE` process environment variable.
`tzdata` is a Python dependency so timezone lookup works on Windows.

The model should not invent memories when no recorded evidence exists.
The current database is not encrypted; protect the Minecraft instance's
config folder and back it up if needed. There is not yet a user-facing
memory deletion and review UI.

## Remaining integration work

1. Start and supervise the Python process from the Java client lifecycle (Windows and cross-platform support).
2. Implement authenticated loopback Java↔Python IPC for chat, model settings, game tools, errors and emergency stop.
3. Preserve existing owner authorization and fast chat/stop behavior in Java; enforce tool permissions at the Java boundary, too.
4. Move model HTTP requests, model profile selection, prompts, persistent memories, skill definitions and recovery policy into Python.
5. Cancel model network calls and pending world actions promptly when emergency stop is triggered.
6. Replace the old Java runAgent route only after end-to-end tests and successful NeoForge startup validation.

**This is an incremental 0.6.0 source implementation, not a ready-to-install working LangGraph Mod.**
