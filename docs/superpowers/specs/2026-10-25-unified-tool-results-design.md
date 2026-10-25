# Unified Kurumi Tool Results — Design

## Intent

Kurumi may assign the outcome of every supported game action (`accepted = tpa_accept()`, `stored = put_item(...)`, etc.) and inspect `success`, `completed`, and `message`, without a different assignment rule for each function. Read-only queries continue returning their existing structured data.

## Approach and scope

Chosen: unify action parsing, dispatch, and result wrapping rather than individually special-casing each new action. Legacy unassigned syntax is not a compatibility requirement, but may remain if removing it risks unrelated behavior. `talk()` and `observe()` retain their existing speaking/agent-handoff semantics. `sleep()` remains a script wait rather than a game action.

Use the existing `KurumiPseudoExecutor.assignActionResult` object shape for all actions; keep the restricted allowlist and parameter validation. Tool errors return structured failures when safely possible. Actions only report `completed=true` when verified; accepted, asynchronous or server-unsynchronized commands return `completed=false`. Do not claim successful movement, teleport, inventory transfer, sleeping, or collection based solely on dispatch.

## Constraints

- Minecraft 26.2, NeoForge 26.2.0.93 / 26.2.0.88 compatibility.
- Keep owner/attack permission checks, script execution budgets, cancellation and async wait semantics.
- Update AI system prompt and Web script instructions so the model knows which returns are tentative.
- Log all changes in `CHANGELOG.md`; user handles Gradle build and game testing locally.
