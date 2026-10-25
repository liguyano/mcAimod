package com.liguyano.mcaimod.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.entity.item.ItemEntity;

import java.util.Comparator;

/** Read-only game observations. Must be invoked on the Minecraft client thread. */
public final class KurumiToolbox {
    public static String observe(String tool) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return "{\"error\":\"not_in_world\"}";
        if ("inspect_container".equals(tool) || (tool != null && tool.startsWith("inspect_slot:"))) {
            var menu = mc.player.containerMenu;
            JsonObject out = new JsonObject();
            if (menu == mc.player.inventoryMenu) return "{\"error\":\"no_open_container\"}";
            out.addProperty("menu_class", menu.getClass().getName());
            out.addProperty("container_id", menu.containerId);
            out.addProperty("slot_count", menu.slots.size());
            int selected = -1;
            if (tool.startsWith("inspect_slot:")) {
                try { selected = Integer.parseInt(tool.substring("inspect_slot:".length())); }
                catch (NumberFormatException ex) { return "{\"error\":\"invalid_slot\"}"; }
                if (selected < 0 || selected >= menu.slots.size()) return "{\"error\":\"slot_out_of_range\"}";
            }
            JsonArray slots = new JsonArray();
            for (int i = 0; i < menu.slots.size(); i++) {
                if (selected >= 0 && i != selected) continue;
                var slot = menu.getSlot(i);
                var stack = slot.getItem();
                JsonObject info = new JsonObject();
                info.addProperty("index", i);
                info.addProperty("x", slot.x);
                info.addProperty("y", slot.y);
                info.addProperty("player_inventory", slot.container == mc.player.getInventory());
                info.addProperty("has_item", !stack.isEmpty());
                info.addProperty("may_pickup", slot.mayPickup(mc.player));
                info.addProperty("max_stack_size", slot.getMaxStackSize());
                if (!stack.isEmpty()) {
                    info.addProperty("item_id", BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
                    info.addProperty("count", stack.getCount());
                    info.addProperty("may_place_same_item", slot.mayPlace(stack));
                }
                slots.add(info);
            }
            out.add("slots", slots);
            out.addProperty("note", "槽位用途未预设；空槽可放物品类型需用具体物品验证");
            return out.toString();
        }
        if (tool != null && tool.startsWith("get_distance:")) {
            String[] parts = tool.split(":", 3);
            JsonObject out = new JsonObject();
            if (parts.length != 3) return "{\"error\":\"invalid_target\"}";
            String kind = parts[1], target = parts[2];
            out.addProperty("kind", kind);
            out.addProperty("target", target);
            out.addProperty("unit", "blocks");
            out.addProperty("search_radius", 48);
            if ("player".equals(kind)) {
                var match = KurumiPlayerResolver.resolve(target, KurumiPlayerDirectory.onlineNames());
                if (!match.found()) {
                    out.addProperty("found", false);
                    out.addProperty("note", match.failure(target));
                    return out.toString();
                }
                target = match.name();
                out.addProperty("resolved_player", target);
            }
            net.minecraft.world.phys.Vec3 eye = mc.player.position();
            net.minecraft.world.phys.Vec3 nearest = null;
            if ("block".equals(kind)) {
                BlockPos origin = mc.player.blockPosition();
                double best = 48.0 * 48.0;
                for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-48, -16, -48),
                        origin.offset(48, 16, 48))) {
                    if (!mc.level.hasChunkAt(pos)) continue;
                    if (!target.equals(BuiltInRegistries.BLOCK.getKey(
                            mc.level.getBlockState(pos).getBlock()).toString())) continue;
                    var candidate = net.minecraft.world.phys.Vec3.atCenterOf(pos);
                    double d = eye.distanceToSqr(candidate);
                    if (d < best) { best = d; nearest = candidate; }
                }
            } else {
                java.util.List<? extends net.minecraft.world.entity.Entity> entities;
                if ("player".equals(kind)) {
                    entities = mc.level.players();
                } else {
                    entities = mc.level.getEntities(mc.player,
                            mc.player.getBoundingBox().inflate(48), e -> true);
                }
                double best = 48.0 * 48.0;
                for (var entity : entities) {
                    if (entity == mc.player) continue;
                    boolean match = switch (kind) {
                        case "player" -> target.equalsIgnoreCase(entity.getName().getString());
                        case "entity" -> target.equals(BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString());
                        case "item" -> entity instanceof ItemEntity drop
                                && target.equals(BuiltInRegistries.ITEM.getKey(drop.getItem().getItem()).toString());
                        default -> false;
                    };
                    if (!match) continue;
                    double d = eye.distanceToSqr(entity.position());
                    if (d < best) { best = d; nearest = entity.position(); }
                }
            }
            out.addProperty("found", nearest != null);
            if (nearest != null) {
                out.addProperty("distance", Math.round(eye.distanceTo(nearest) * 100.0) / 100.0);
                out.addProperty("x", nearest.x);
                out.addProperty("y", nearest.y);
                out.addProperty("z", nearest.z);
            } else {
                out.addProperty("note", "未在已加载的搜索范围内发现目标，不代表目标不存在");
            }
            return out.toString();
        }
        if (tool != null && tool.startsWith("find_nearest_block:")) {
            String blockId = tool.substring("find_nearest_block:".length());
            JsonObject result = new JsonObject();
            result.addProperty("block_id", blockId);
            BlockPos origin = mc.player.blockPosition();
            BlockPos nearest = null;
            String actualBlockId = "";
            double best = Double.POSITIVE_INFINITY;
            for (BlockPos pos : BlockPos.betweenClosed(
                    origin.offset(-24, -8, -24), origin.offset(24, 8, 24))) {
                if (!mc.level.hasChunkAt(pos)) continue;
                String id = BuiltInRegistries.BLOCK.getKey(
                        mc.level.getBlockState(pos).getBlock()).toString();
                if (!KurumiBedMatcher.matches(blockId, id)) continue;
                double distance = pos.distSqr(origin);
                if (distance < best) { best = distance; nearest = pos.immutable(); actualBlockId = id; }
            }
            result.addProperty("found", nearest != null);
            if (nearest != null) {
                result.addProperty("block_id", actualBlockId);
                result.addProperty("x", nearest.getX());
                result.addProperty("y", nearest.getY());
                result.addProperty("z", nearest.getZ());
                result.addProperty("distance", Math.sqrt(best));
                result.addProperty("dimension", mc.level.dimension().toString());
            }
            return result.toString();
        }
        if (tool != null && tool.startsWith("check_nearby_chest:"))
            return KurumiChestInspector.check(tool.substring("check_nearby_chest:".length()));
        if (tool != null && tool.startsWith("get_nearby_chest_result:"))
            return KurumiChestInspector.status(tool.substring("get_nearby_chest_result:".length()));
        if (tool != null && tool.startsWith("check_inventory_count:")) {
            String id = tool.substring("check_inventory_count:".length());
            JsonObject check = new JsonObject();
            check.addProperty("item_id", id);
            check.addProperty("count", KurumiTaskChecks.inventoryCount(id));
            return check.toString();
        }
        if (tool != null && tool.startsWith("check_position:")) {
            String[] parts = tool.substring("check_position:".length()).split(":");
            try {
                int x = Integer.parseInt(parts[0]), y = Integer.parseInt(parts[1]);
                int z = Integer.parseInt(parts[2]), radius = Integer.parseInt(parts[3]);
                return KurumiTaskChecks.observe("", x, y, z, radius);
            } catch (RuntimeException ex) { return "{\"error\":\"invalid_coordinates\"}"; }
        }
        if (tool != null && tool.startsWith("get_crafting_recipe:"))
            return KurumiAutoCraftBridge.lookup(tool.substring("get_crafting_recipe:".length()));
        JsonObject out = new JsonObject();
        out.addProperty("tool", tool);
        switch (tool) {
            case "get_position" -> {
                out.addProperty("x", mc.player.getBlockX());
                out.addProperty("y", mc.player.getBlockY());
                out.addProperty("z", mc.player.getBlockZ());
            }
            case "get_player_status" -> {
                out.addProperty("x", mc.player.getBlockX());
                out.addProperty("y", mc.player.getBlockY());
                out.addProperty("z", mc.player.getBlockZ());
                out.addProperty("health", mc.player.getHealth());
                out.addProperty("food", mc.player.getFoodData().getFoodLevel());
            }
            case "get_environment" -> {
                out.addProperty("dimension", mc.level.dimension().toString());
                out.addProperty("day_time", mc.level.getGameTime());
                out.addProperty("raining", mc.level.isRaining());
                out.addProperty("biome", mc.level.getBiome(mc.player.blockPosition()).unwrapKey()
                        .map(Object::toString).orElse("unknown"));
            }
            case "scan_nearby_entities" -> {
                JsonArray found = new JsonArray();
                mc.level.getEntities(mc.player, mc.player.getBoundingBox().inflate(48),
                        e -> true).stream()
                        .sorted(Comparator.comparingDouble(e -> e.distanceToSqr(mc.player)))
                        .limit(64).forEach(e -> {
                            JsonObject item = new JsonObject();
                            item.addProperty("type", BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).toString());
                            item.addProperty("name", e.getName().getString());
                            item.addProperty("x", e.getBlockX());
                            item.addProperty("y", e.getBlockY());
                            item.addProperty("z", e.getBlockZ());
                            found.add(item);
                        });
                out.add("entities", found);
            }
            case "scan_dropped_items" -> {
                JsonArray found = new JsonArray();
                mc.level.getEntities(mc.player, mc.player.getBoundingBox().inflate(48),
                        entity -> entity instanceof ItemEntity).stream()
                        .map(entity -> (ItemEntity) entity)
                        .sorted(Comparator.comparingDouble(entity -> entity.distanceToSqr(mc.player)))
                        .limit(64).forEach(entity -> {
                            JsonObject item = new JsonObject();
                            item.addProperty("item_id", BuiltInRegistries.ITEM.getKey(entity.getItem().getItem()).toString());
                            item.addProperty("count", entity.getItem().getCount());
                            item.addProperty("x", entity.getBlockX());
                            item.addProperty("y", entity.getBlockY());
                            item.addProperty("z", entity.getBlockZ());
                            item.addProperty("distance_squared", entity.distanceToSqr(mc.player));
                            found.add(item);
                        });
                out.add("items", found);
            }
            case "scan_nearby_blocks" -> {
                JsonArray found = new JsonArray();
                BlockPos origin = mc.player.blockPosition();
                int count = 0;
                for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-16,-6,-16), origin.offset(16,6,16))) {
                    if (count >= 96) break;
                    if (!mc.level.hasChunkAt(pos)) continue;
                    BlockState state = mc.level.getBlockState(pos);
                    String id = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
                    if (!(id.endsWith("_log") || id.endsWith("_stem") || id.endsWith("_ore"))) continue;
                    JsonObject item = new JsonObject();
                    item.addProperty("block_id", id);
                    item.addProperty("x", pos.getX());
                    item.addProperty("y", pos.getY());
                    item.addProperty("z", pos.getZ());
                    found.add(item);
                    count++;
                }
                out.add("blocks", found);
            }
            case "get_inventory" -> {
                JsonArray items = new JsonArray();
                for (int i = 0; i < mc.player.getInventory().getContainerSize(); i++) {
                    var stack = mc.player.getInventory().getItem(i);
                    if (stack.isEmpty()) continue;
                    JsonObject item = new JsonObject();
                    item.addProperty("item", BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
                    item.addProperty("count", stack.getCount());
                    items.add(item);
                }
                out.add("items", items);
            }
            default -> out.addProperty("error", "unsupported_tool");
        }
        return out.toString();
    }
    private KurumiToolbox() {}
}
