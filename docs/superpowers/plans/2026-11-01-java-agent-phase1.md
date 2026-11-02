# Java Agent Context Phase 1 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Reduce irrelevant prompt content and expose model request latency while preserving existing Java agent execution.

**Architecture:** Task-scoped tool selection uses the existing registry; context compaction retains the original task and system messages with a smaller recent-turn budget; controller logs request metrics around the asynchronous provider call.

**Tech Stack:** Java 25, JUnit 5, NeoForge Gradle.

**Spec:** `docs/superpowers/specs/2026-11-01-java-agent-phase1-design.md`

## Global Constraints

- No change to autonomous action permissions or script engine.
- Retain full tool catalog fallback for unknown requests.
- Never log credentials or prompt bodies as performance telemetry.

## Review Focus

- Multi-intent requests should include all relevant categories.
- Corpse-related tasks need both entity and container tools.
- No-match requests should retain full discovery.
- Latest user message and system rules must survive compaction.
- Async failures and formatting retries must emit elapsed time without credentials.

### Task 1: Task-scoped tool catalog

- [ ] Add failing category filtering tests for corpse, movement, and unknown requests.
- [ ] Add registry selection and apply it in the controller's existing task guide.
- [ ] Verify tests.

### Task 2: Budgeted context

- [ ] Add failing tests for preserved goal and latest message under a smaller budget.
- [ ] Change compaction to favor recent turns and preserve task state.
- [ ] Verify tests.

### Task 3: Model request instrumentation

- [ ] Add a deterministic metric formatting test.
- [ ] Log per-attempt elapsed time and input/output character counts around model calls.
- [ ] Verify focused and full test suites.
