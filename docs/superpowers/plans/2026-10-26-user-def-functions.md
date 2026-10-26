# Restricted user-defined functions implementation plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Let Kurumi's restricted script language define and call functions with parameters, local variables, and values returned to callers, without recursion.

**Architecture:** Store named definitions as bounded AST blocks, run function bodies on the existing tick-driven frame stack, and restore caller variables after returning. Validate function calls against declared names, prevent direct and indirect recursion, and preserve all game-action allowlists and resource budgets.

**Tech Stack:** Java 25, NeoForge, Gson AST, JUnit 5.

**Spec:** Approved conversation scope: `def`, arguments, local variables, `return`, nested calls between different functions, tool actions that await completion, no recursion; use loops for repetitions.

## Global Constraints

- Do not execute general Python, imports, or external code.
- Preserve existing 200-action and 20-minute task limits.
- Do not overwrite unrelated local modifications.
- Runtime compilation requires JDK 25, which is unavailable in the current sandbox.

## Review Focus

- Reject direct and indirect recursion, including hidden calls in conditional blocks.
- Reject duplicate function names, undefined functions, or invalid argument counts.
- Keep caller variables isolated when nested function calls return.
- Support action calls and deferred completion inside functions.
- Handle `return` from nested loops and branches correctly.

---

### Task 1: Parse functions and calls

**Files:** `KurumiScriptParser.java`, `KurumiTaskPlanner.java`, `KurumiDefFunctionsTest.java`.

- [ ] Add failing tests for definitions, calls, invalid definitions and unknown calls.
- [ ] Observe test failure (or document JDK blocker).
- [ ] Parse `def`, `CALL_FUNCTION`, `RETURN` expressions with bounded validation.
- [ ] Verify tests.

### Task 2: Function execution and local scopes

**Files:** `KurumiPseudoExecutor.java`, `KurumiDefFunctionsTest.java`.

- [ ] Add tests for execution data flow and recursion validation.
- [ ] Observe test failure (or document JDK blocker).
- [ ] Run function bodies with stack frames and saved caller scopes, restoring and assigning return values.
- [ ] Reject recursion and report precise failure.
- [ ] Verify tests.

### Task 3: Guidance and release notes

**Files:** `KurumiClientController.java`, `KurumiWebActions.java`, `README.md`, `CHANGELOG.md`.

- [ ] Describe `def`, returns, local scope, no recursion, and `for`/`while` guidance.
- [ ] Check diff and run whole Gradle build where JDK 25 is available.
