# Batch Observe Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** `observe(a, b, c)` sends all observation values together to the next AI stage, while preserving single-argument behavior.

**Architecture:** Keep the existing `EXPR_CALL` parser route. Extract batch observation preparation to a small pure Java helper for testability, then use the existing executor's observation log and one screenshot handoff. Update tool registry/prompt instructions.

**Tech Stack:** Java, Gson, JUnit 5, existing Kurumi DSL and NeoForge integration.

**Spec:** `docs/superpowers/specs/2026-11-11-batch-observe-design.md`

## Global Constraints

- No Python runtime or new dependencies.
- One `observe(...)` suspends the current script once; legacy one-argument invocation unchanged.
- Accept ordinary values plus at most one screenshot; preserve complete data and argument labels.
- Failure does not silently return partial observations.

## Review Focus

- Mixed screenshot and object order should not drop the object.
- More than one screenshot should fail clearly.
- Missing screenshot should stop with a precise reason.
- A long structure should remain complete in AI feedback.
- Existing `observe(value)` must produce the same successful handoff.

---

### Task 1: Pure batch observation preparation

**Files:**
- Create: `src/main/java/com/liguyano/mcaimod/client/KurumiObserveBatch.java`
- Test: `src/test/java/com/liguyano/mcaimod/client/KurumiObserveBatchTest.java`

**Interfaces:** `KurumiObserveBatch.prepare(String argumentSource, Map<String,JsonElement> variables)` returns ordered labeled results, optional screenshot id; rejects zero parameters and multiple screenshots; uses `KurumiExpressions.arguments`.

- [ ] Write tests for single and multi-value, long objects, mixed screenshot order, invalid screenshot count and zero parameters.
- [ ] Run `./gradlew test --tests '*KurumiObserveBatchTest'` and observe expected failures.
- [ ] Implement minimum helper to satisfy tests.
- [ ] Rerun targeted tests.

### Task 2: Executor handoff

**Files:**
- Modify: `src/main/java/com/liguyano/mcaimod/client/KurumiPseudoExecutor.java`
- Test: `src/test/java/com/liguyano/mcaimod/client/KurumiObserveBatchTest.java`

**Interfaces:** Consume `KurumiObserveBatch.prepare`; log every prepared value before one `finishRun`, or wait for the existing screenshot completion path while retaining the other values.

- [ ] Write assertions for batch handoff log construction and no partial results on validation failure.
- [ ] Verify the test fails on existing behavior.
- [ ] Replace the one-argument `observe` handling and preserve existing screenshot flow.
- [ ] Verify the targeted tests.

### Task 3: Model-facing instructions and regression verification

**Files:**
- Modify: `src/main/java/com/liguyano/mcaimod/client/KurumiClientController.java`
- Modify: `src/main/java/com/liguyano/mcaimod/client/KurumiToolRegistry.java`
- Modify: `src/main/java/com/liguyano/mcaimod/client/KurumiObservationGuidance.java`
- Test: `src/test/java/com/liguyano/mcaimod/client/KurumiObservationGuidanceTest.java`

- [ ] Write a failing assertion that AI guidance describes multi-value `observe` and the one-screenshot limit.
- [ ] Run targeted test to establish red.
- [ ] Update guidance and registry signature.
- [ ] Run `./gradlew test --no-daemon` plus `git diff --check`; report environment failures exactly.
