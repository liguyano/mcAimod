# Unified Minecraft interaction design

## Intent

Kurumi should understand Minecraft actions as context-dependent interactions, and
`interact("minecraft:oak_boat")` should let her board a nearby boat instead of
failing because the current bridge only searches blocks.

## Scope

- Keep existing block interactions and furnace/container item transfers intact.
- Extend `interact(id)` to nearby entities (boats, villagers, animals) via
  client multiplayer entity interaction. Only report attempted interaction until
  a subsequent observation verifies the effect.
- Provide a separate continuous held-item use action for food and drink. Observe
  actual use state/completion and allow `/kurumi stop` to release/cancel it.
- Update agent guidance so it chooses block, entity or hand-held item interaction
  appropriately; neither all Minecraft actions nor all entities support right-click.
- Add parser/executor tests for accepted syntax, routing decisions, and safety,
  plus relevant integration/build checks. Preserve compatibility with 26.2/NeoForge
  26.2.0.88.

## Approaches considered

1. **One giant `interact` routine for blocks, entities and held items.** Simple
   for the model, but ambiguous between a boat entity and a boat item and hard to
   manage multi-tick item use safely.
2. **Unified target interaction, distinct `use_item` for held items (recommended).**
   `interact(id)` routes known nearby targets to block/entity adapters;
   `use_item(id)` holds use until completion or cancellation. Explicit action
   semantics enable reliable follow-up observations.
3. **Separate `interact_block`, `interact_entity`, `use_item`.** Easiest to implement
   and debug, but unnecessarily expands the model-facing command vocabulary.

## Constraints and acceptance

- Do not change the separate long-lived agent/session architecture in this patch.
- Never silently claim a server-side interaction completed based on a client-side
  click return value.
- Never automatically approach faraway entities; report out-of-reach targets.
- No unsolicited item use or attacks. Immediate stop remains effective.
- The normal furnace `interact` and `put_item` flow continues working.

## Open decisions

User approved unified `interact` with distinct `use_item` and requested a
per-change project changelog. These choices apply to this patch.
