# Restricted Script Exceptions Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Support explicitly requested `raise Exception(message)` as a safe failure handoff.

**Architecture:** Parse one restricted argument into a `RAISE` statement carrying the line number, validate in `KurumiTaskPlanner`, and end the interpreter stage with a failed report. Existing awaited-stage AI continuation receives the report.

**Tech Stack:** Java 25, Gson, NeoForge, JUnit Jupiter.

**Spec:** `docs/superpowers/specs/2026-10-26-script-raise-design.md`

## Global Constraints

- No unrestricted Python execution or arbitrary exception classes.
- Do not change `observe()` during this feature.
- Preserve current limits and no automatic retry.

## Review Focus

- `raise Exception()` / wrong class / multiple args must be rejected.
- Nested `if` / loop can raise and report line number.
- An exception cannot be reported as script success.
- Expression errors are failures, not silent success.
- Other existing statements keep parsing normally.

### Task 1: Restricted parse and validation

- [ ] Add JUnit parser tests for literal, `result.message`, nested condition and rejected forms.
- [ ] Implement `RAISE` parser branch with exact source line and restricted expression; validate AST entry.
- [ ] Run `./gradlew test --tests '*KurumiRaiseExceptionTest'` on JDK 25.

### Task 2: Failed-stage handoff

- [ ] Add interpreter handling that evaluates the message, fails the run and includes line and reason in report.
- [ ] Check next-stage controller already forwards `finishedRunReport()` to AI without auto-retry.
- [ ] Document syntax and run full `./gradlew build` on JDK 25.
