# mcAimod — Kurumi, an Autonomous Minecraft AI Companion

**Experimental v0.5.0 · Minecraft Java 26.2 · NeoForge**

Kurumi is an in-game AI companion that can do more than answer chat messages. With automation explicitly enabled, it can **observe its surroundings, choose actions, operate Minecraft tools, check results, and decide what to do next**. It uses a language model for reasoning and Java/NeoForge integrations for actual game operations.

> **Key feature: autonomous in-game behavior.** Kurumi can react to changing game conditions instead of waiting for a separate command for every individual movement or interaction. Autonomy is limited by its enabled settings, available tools, model reliability, game conditions, and safety controls. It is not guaranteed to finish every task.

This repository also contains a separate `/ai` game-aware chat interface. The client-side Kurumi Agent is the experimental autonomous-action component.

## What Kurumi Can Do on Its Own

### Observe → plan → act → verify → adapt

1. **Observe:** Read game state, inventory, nearby entities and blocks, containers, dropped items, and other supported observations.
2. **Plan:** Ask the configured language model to choose a task strategy and generate a constrained action script.
3. **Act:** Execute supported operations through Java game bridges, including Baritone movement, mining, interaction, inventory transfers, and combat attempts.
4. **Verify:** Check returned tool feedback and available game observations. A successful command submission does **not** necessarily mean the goal was achieved.
5. **Adapt:** On a tool failure or an explicit `observe(value)` handoff, forward the actual feedback to the Java Agent for another model decision. Normal script completion does not automatically trigger another planning round.

**Examples of autonomous workflows:**
- Navigate toward resources, mine them, pick up drops, and attempt to store items in a chest.
- Locate nearby entities or a corpse, interact with the target, and recover items from an accessible container.
- Inspect a chest or crafting interface before choosing what to take or put in each slot.
- Start and monitor automatic fishing, stopping on certain safety or resource conditions.
- React to hunger and damage with experimental survival behavior, including interrupting a task and attempting to flee.

These are **capabilities and intended workflows**, not promises of reliable end-to-end completion on every world or server.

### Autonomous survival reactions (experimental)

When enabled, Kurumi can monitor hunger and react to damage. The current Java implementation can interrupt an ongoing task, attempt to eat, and use bounded Baritone movement to move away from danger or toward a nearby player. It can then ask the model whether the interrupted task should resume. Fleeing does not guarantee safety, and autonomous combat or survival decisions should be tested in a disposable world first.

### Multi-stage reasoning with `observe()`

The existing **Java pseudocode executor** supports explicit handoffs to the model:

```python
entities = scan_nearby_entities()
observe(entities)  # Ends this stage and sends actual observations back to the Agent
```

Kurumi can use the returned information to generate a subsequent script rather than guessing what happened. This syntax is **Python-like pseudocode**, not CPython.

### Tool-driven autonomy

Current Java-backed features include, depending on context and server support:

| Area | Examples |
| --- | --- |
| Movement | Baritone navigation, following and approaching players, looking around |
| World interaction | Interact with blocks and entities, mining, placing or breaking blocks |
| Inventory | Inspect inventory, equip items, pick up or drop items |
| Containers | Open, inspect, take and put items, search chests |
| Activities | Farming, fishing, item use, sleeping, bounded combat attempts |
| Observations | Player status, environment, nearby blocks/entities/items, screenshots |
| Memory and skills | Remember locations, inspect saved skills, run approved shortcuts |
| Safety | Stop commands, budgets, interruption and failure reports |

Some actions are asynchronous. The script must distinguish **request accepted** from **action completed**, and verify observable outcomes before claiming success.

## v0.5.0: Embedded Python Work in Progress

The long-term plan is **Java Agent + embedded GraalPy script execution + Java Minecraft tools**, *not* a Python rewrite of the model API or the entire Mod.

The repository now contains:
- `python-prototype/`: a separate GraalPy proof of concept.
- `KurumiEmbeddedPythonBridge.java`: experimental Java-to-GraalPy tool dispatch, native Python-compatible return value conversion, and an initial `observe()` stage-report mechanism.
- GraalPy dependencies declared in the main Gradle build for integration work.

**Important: GraalPy is not yet the live Kurumi script interpreter.** The AI still executes scripts through the existing Java pseudocode executor. The new Python bridge is not enabled for model-generated scripts. Full NeoForge runtime packaging, complete tool parity and permission checks, asynchronous completion and robust cancellation, Java Agent stage routing, compilation, and in-game validation are **not complete**. Do not mistake the existence of bridge methods for tested functionality. Native Python libraries such as OpenCV are not bundled or guaranteed to work.

## Requirements

- Minecraft Java Edition **26.2**
- A compatible **NeoForge 26.2** installation
- **JDK 25** to build the project
- A configured and reachable LLM API or a local Ollama instance
- Baritone and other relevant integrations where required by a particular feature

## Build

Windows PowerShell:

```powershell
git clone https://github.com/liguyano/mcAimod.git
cd mcAimod
java -version
.\gradlew.bat clean test build --no-daemon
```

Linux/macOS:

```bash
./gradlew clean test build --no-daemon
```

On a successful build, check `build/libs/` for the Mod JAR. Put the appropriate JAR in the `mods/` folder of a compatible NeoForge installation. **A successful Gradle build is not proof of successful in-game behavior or embedded Python runtime packaging.**

For the separate GraalPy prototype, see [python-prototype/README.md](python-prototype/README.md).

## Model Configuration

Model profiles are stored in the instance's `config/mcaimod-models.json`. For structure and available fields, see [config/mcaimod-models.example.json](config/mcaimod-models.example.json).

Supported setups include local Ollama and configurable Chat Completions-compatible services such as Qwen, DeepSeek, and OpenAI, subject to provider API compatibility.

Keep API keys out of Git. Prefer environment variables, protect configuration files containing plaintext keys, and review the information sent to external model providers.

## Commands

| Command | Description |
| --- | --- |
| `/ai <message>` | Ask the separate game-aware chat assistant |
| `/ai status` | Inspect chat assistant status |
| `/ai model list` | List configured model options |
| `/ai model use <name>` | Select a model |
| `/ai clear` | Clear the current chat context |
| `/kurumi on` / `/kurumi off` | Enable or disable Kurumi chat listening |
| `/kurumi auto on` / `/kurumi auto off` | Enable or disable automatic actions |
| `/kurumi status` | Inspect Kurumi status |
| `/kurumi stop` | Stop the current automated activity |
| `/kurumi farm` | Attempt to start farming |

Kurumi also exposes a local experimental task monitor at `http://127.0.0.1:8765/` when available. It is intended for local use; do not expose the unauthenticated monitor to the internet.

## Safety and Limitations

- **Automation must be explicitly enabled** before Kurumi performs world-changing tasks.
- Model-generated instructions and tool results are not guarantees of success.
- Some sensitive actions have owner-specific restrictions in the current Java executor.
- Long-running tasks, server latency, unloaded chunks, inventory state, and mod compatibility can cause failures.
- Some experimental features may interrupt player control or interact with inventory and containers.
- Back up important worlds and test autonomous actions in a safe environment.
- The embedded Python bridge is a development component and **must not be exposed to untrusted scripts without completed authorization and resource controls**.

## Development

See [CHANGELOG.md](CHANGELOG.md) for implementation history. The project is under active development; details in older changelog entries may describe earlier versions rather than the current runtime.

**Project focus:** a Minecraft companion that can **take initiative, act on real observations, and recover from mistakes**, not just generate conversational replies.
