# Unified Tool Results Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Every supported Kurumi game action can be assigned to a result variable.

**Architecture:** Share a declared action-name allowlist between the parser and planner. Dispatch assigned calls using the same validated bridge operations as legacy calls; wrap each outcome with `success`, `completed`, and `message`. Keep read-only query values unchanged.

**Tech Stack:** Java, Gson, Minecraft 26.2 client, NeoForge, JUnit 5.

**Spec:** `docs/superpowers/specs/2026-10-25-unified-tool-results-design.md`

## Global Constraints

User runs Gradle locally; don't execute a build here. Preserve owner security and cancellation behavior. Log modifications in `CHANGELOG.md`. Do not depend on legacy unassigned calls, but don't delete them without need.

## Review Focus

- Zero-argument `tpa_accept` assignment must parse and execute.
- Missing/invalid arguments must return a failure, not silently succeed.
- `completed=false` for server-unsynchronized or asynchronous operations.
- Async operation results must not allow a second script step to race a still-busy bridge.
- Observe and read-only expressions must retain existing semantics.

### Task 1: Unified assigned-action parsing

**Files:** `KurumiScriptParser.java`, `KurumiTaskPlanner.java`, JUnit tests.

- [ ] Add test cases for assignment across zero/one/two-argument and dynamic actions (including `accepted = tpa_accept()`), unknown tool rejection, and ordinary query assignment.
- [ ] Verify baseline failure using available test tools or document local-build limitation.
- [ ] Consolidate assigned-action name validation; preserve exact parameter parsing through `KurumiExpressions.arguments`.
- [ ] Run targeted tests where available, and commit.

### Task 2: Unified action dispatch/results

**Files:** `KurumiPseudoExecutor.java`, action result helper, JUnit tests.

- [ ] Add contract tests for accepted vs verified return states, failures and cancellation.
- [ ] Route recognized assigned action calls to existing validated bridges; use `assignActionResult` consistently.
- [ ] Maintain asynchronous wait behavior; avoid false `completed=true`.
- [ ] Run relevant tests where available, and commit.

### Task 3: Prompt, documentation and handoff

**Files:** `KurumiClientController.java`, `KurumiWebActions.java`, `CHANGELOG.md`, tests.

- [ ] Document result contract and examples for tools including `tpa_accept`, `put_item`, movement and containers.
- [ ] Inspect remaining tool registrations for assignment gaps.
- [ ] Verify diff and tests where available; document that Windows Gradle build is pending.
- [ ] Commit without pushing to GitHub (prior GitHub tool write was rejected).
