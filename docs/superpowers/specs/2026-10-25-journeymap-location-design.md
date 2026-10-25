# JourneyMap location memory design

## Intent
Use JourneyMap's saved waypoints as Kurumi's only named-location store. Preserve the existing owner-only conversational commands (`记住`, `去`, `在哪里`, `地点列表`, `忘记`) and the `gotoNamed` planner action. Read user-created JourneyMap waypoints as well as Kurumi-created ones.

## Approaches
- **Chosen:** Replace location JSON reads/writes with the JourneyMap 6 API. The user explicitly selected no migration of existing JSON locations.
- **Rejected:** Import the old JSON first; the user declined migration.
- **Rejected:** Keep dual stores in sync; this would make two sources of truth.

## Constraints
- Never read, migrate, overwrite or remove the existing `config/mcaimod-locations.json`; keep it untouched.
- JourneyMap is installed on the user's client; use API v2 (`info.journeymap:journeymap-api-neoforge:26.2-2.0.0`) compile-only, never bundle JourneyMap into mcAimod.
- Use official JourneyMap plugin registration. When JourneyMap is unavailable, return an informative failure rather than a false success or silent JSON fallback.
- Preserve exact owner checks. Do not delete user-created markers on a fuzzy match; deletion only for Kurumi's own markers and exact names.
- Never navigate directly across dimensions, or invent coordinates. World-specific waypoint scope comes from JourneyMap.
- The user builds locally; do not execute Gradle.

## Implementation outline
Introduce a JourneyMap client plugin that receives the API, and a small bridge that exposes immutable named-location snapshots and save/delete methods to `KurumiLocationMemory`. Rework only named-location persistence, not the separate remembered-block scripting tools. Update prompt text, changelog and targeted tests.
