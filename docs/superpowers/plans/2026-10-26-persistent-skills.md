# Persistent Kurumi Skills Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Add reusable function memory, owner-approved saving, title lookup, and AI-assisted fallback for exact-match quick commands.

**Architecture:** A bounded JSON store validates scripts and keeps versioned skill records; the controller owns owner-confirmation and quick-run state. Existing interpreter runs stored code, existing model loop receives failure reports.

**Tech Stack:** Java 25, NeoForge, Gson, JUnit 5.

**Spec:** `docs/superpowers/specs/2026-10-26-persistent-skills-design.md`

## Global Constraints

- No recursion, arbitrary Python, or bypassing action allowlists.
- Owner confirmation required for every save/update; no implied consent.
- Shortcut failures do not auto-retry; pass evidence to the AI.
- Do not alter unrelated local modifications.
- Native build uses JDK 25, unavailable in current sandbox.

## Review Focus

- Duplicate names and trigger collisions, including built-ins.
- Corrupt JSON and atomic persistence preserving existing skills.
- Never persist a merely accepted action as verified completed.
- Prevent non-owner saving/shortcut execution and partial-phrase triggers.
- Preserve failure context when handing quick-run failures to AI.

---

### Task 1: Bounded persistent skill library

**Files:** create `KurumiSkillMemory.java`, test `KurumiSkillMemoryTest.java`.

- [ ] Write tests for validation, save/load, exact lookup, index summaries, update versions and corrupt files.
- [ ] Run failing tests or record JDK blocker.
- [ ] Implement Path-injectable atomic JSON store and constrained API.
- [ ] Verify tests.

### Task 2: AI proposal and explicit owner consent

**Files:** `KurumiDecisionParser.java`, `KurumiClientController.java`, `KurumiExpressions.java`, `KurumiScriptParser.java`, tests.

- [ ] Test proposal validation, read-only `get_skill`, confirmation guard and injection.
- [ ] Implement validated `finish.suggest_skill`, pending approval, indexes in prompt, get_skill tool.
- [ ] Verify tests.

### Task 3: Quick-run and AI fallback

**Files:** `KurumiClientController.java`, tests.

- [ ] Test exact matching, owner-only access, and failed direct run handoff.
- [ ] Launch stored skill without model, inspect returned result, continue with AI on failure.
- [ ] Verify tests.

### Task 4: Docs and handoff

**Files:** `README.md`, `CHANGELOG.md`.

- [ ] Explain opt-in approval, title-only index, shortcuts, failure fallback.
- [ ] Verify diff and run Windows JDK 25 build/test when available.
