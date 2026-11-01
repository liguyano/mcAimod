# Script Nesting Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Accept 16 nested blocks, reject a 17th block safely.

**Architecture:** Define the depth cap once in the script planner and use it in both parsers. Preserve the statement budget and existing executor.

**Tech Stack:** Java, JUnit 5, Gradle.

**Spec:** `docs/superpowers/specs/2026-11-01-script-nesting-design.md`

## Global Constraints

- Maximum nesting depth is 16, counted from root depth 0.
- Keep the 200-statement budget.
- No Python or LangGraph changes.

## Review Focus

- Depth boundaries 5, 6, 16, 17; both source and JSON parsing.
- Nested else blocks and total statement budget remain valid.

### Task 1: Shared maximum nesting

**Files:** `KurumiScriptParser.java`, `KurumiTaskPlanner.java`, `KurumiNestingDepthTest.java`.

- [ ] Write JUnit tests accepting depths 6 and 16 and rejecting 17 in both parsers.
- [ ] Run focused tests and observe the depth-6 failure.
- [ ] Introduce a single shared 16-depth constant and update both guards.
- [ ] Run focused tests, then full test suite.
