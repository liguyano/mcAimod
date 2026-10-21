package com.liguyano.mcaimod.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;

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
                out.addProperty("dimension", mc.level.dimension().location().toString());
                out.addProperty("day_time", mc.level.getDayTime());
                out.addProperty("raining", mc.level.isRaining());
                out.addProperty("biome", mc.level.getBiome(mc.player.blockPosition()).unwrapKey()
                        .map(k -> k.location().toString()).orElse("unknown"));
            }
            case "scan_nearby_entities" -> {
                JsonArray found = new JsonArray();
                mc.level.getEntities(mc.player, mc.player.getBoundingBox().inflate(24),
                        e -> true).stream()
                        .sorted(Comparator.comparingDouble(e -> e.distanceToSqr(mc.player)))
                        .limit(24).forEach(e -> {
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
            case "scan_nearby_blocks" -> {
                JsonArray found = new JsonArray();
                BlockPos origin = mc.player.blockPosition();
                int count = 0;
                for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-8,-4,-8), origin.offset(8,4,8))) {
                    if (count >= 48) break;
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
                for (var stack : mc.player.getInventory().getNonEquipmentItems()) {
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
