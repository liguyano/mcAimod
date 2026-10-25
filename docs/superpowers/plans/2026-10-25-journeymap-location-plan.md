# JourneyMap Location Memory Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Replace named location persistence with JourneyMap API v2 while keeping Kurumi's chat commands stable.

**Architecture:** JourneyMap plugin receives the API. Bridge delegates read/write for waypoints, and the old location command router consumes immutable snapshots. No migration or fallback JSON reads.

**Tech Stack:** Java, NeoForge 26.2, JourneyMap API 2.0.0 (compile-only), JUnit.

**Spec:** `docs/superpowers/specs/2026-10-25-journeymap-location-design.md`

## Global Constraints

- No Gradle build in this environment (user builds locally).
- No migration or modification of `mcaimod-locations.json`.
- All new behavior documented in `CHANGELOG.md`.

## Review Focus

- JourneyMap absent/not initialized: fail explicitly.
- Duplicate names across dimensions: select current dimension for navigation or refuse ambiguities.
- Deleting waypoint not owned by Kurumi: reject.
- Wrong dimension: never initiate direct Baritone navigation.
- Owner player not loaded: do not save invented position.

---

### Task 1: API integration

**Files:** `build.gradle`, `src/main/java/com/liguyano/mcaimod/client/KurumiJourneyMapPlugin.java`, `src/main/java/com/liguyano/mcaimod/client/KurumiJourneyMapBridge.java`

- [ ] Add compile-only JourneyMap API 26.2-2.0.0 dependency and repository.
- [ ] Implement API v2 plugin registration and safely capture/clear the API lifecycle.
- [ ] Implement immutable waypoint read/save/delete bridge, with explicit unavailable errors.
- [ ] Inspect API signatures and static diff; user will run Gradle locally.

### Task 2: Command migration

**Files:** `src/main/java/com/liguyano/mcaimod/client/KurumiLocationMemory.java`, `src/main/java/com/liguyano/mcaimod/client/KurumiClientController.java`, targeted tests.

- [ ] Add test cases for duplicate name and deletion protection in pure location-selection logic.
- [ ] Replace old JSON calls with bridge; retain chat and planner semantics.
- [ ] Expose all JourneyMap waypoints to the model; keep old file untouched.
- [ ] Update changelog; check diff and commit/sync main.
