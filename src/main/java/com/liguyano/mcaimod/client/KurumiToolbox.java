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
        JsonObject out = new JsonObject();
        out.addProperty("tool", tool);
        switch (tool) {
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
