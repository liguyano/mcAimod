# Unified Interaction Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let Kurumi interact with nearby boats/entities and continuously use equipped consumables, while preserving block/container behavior and recording modifications in a changelog.

**Architecture:** Route `interact(id)` to registered nearby block or entity targets. A separate tick-driven held-item action invokes multiplayer use and waits for a server-reflected completion condition; existing script executor awaits that action. Keep explicit stop and no autonomous attacks.

**Tech Stack:** Java 25, NeoForge 26.2.0.88, Minecraft 26.2, Gradle/JUnit 5.

**Spec:** `docs/superpowers/specs/2026-10-25-unified-interaction-design.md`

## Global Constraints

- No regressions to nearby block interaction and container transfers.
- Acknowledging a click is not proof of a server-side outcome.
- `/kurumi stop` must stop any held use.
- Record this and subsequent changes in `CHANGELOG.md`.

## Review Focus

- Boat item ID vs nearby boat entity: do not accidentally use the held boat item.
- Target out of reach: report honestly, do not claim mounted.
- Consumable not equipped or non-consumable: fail clearly.
- Cancelling during use: release use state and stop waiting.
- Furnace interaction: continue to await container opening.

---

### Task 1: Entity interaction routing

**Files:** `KurumiInteractionBridge.java`, `KurumiInteractionTarget.java` (if useful), `KurumiInteractionTargetTest.java`.

- [x] Add failing test for boat entity ID normalization and accepted parser calls. (Nearest selection remains game-side.)
- [x] Run target test and observe expected missing-method failure.
- [x] Implement block-first, entity-second `interact(id)` with reach checks and multiplayer gameMode `interact`.
- [x] Run target test and full suite.
- [ ] Commit task.

### Task 2: Held item continuous use

**Files:** `KurumiItemUseBridge.java`, `KurumiPseudoExecutor.java`, `KurumiScriptParser.java`, `KurumiTaskPlanner.java`, `KurumiItemUseLogicTest.java`.

- [x] Add failing test for `use_item` parser; tick lifecycle requires Minecraft runtime verification.
- [x] Run target tests before implementing parser support.
- [x] Implement tick-based held use and await in script interpreter; integrate cancellation.
- [x] Run target tests and full suite.
- [ ] Commit task.

### Task 3: Model guidance, logging, and end-to-end verification

**Files:** `KurumiClientController.java`, `CHANGELOG.md`, tests.

- [x] Add guidance for new interactions and add changelog entry. (Prompt regression test not practical in isolation.)
- [x] Run offline Gradle `test build` and review diff.
- [ ] Commit.
