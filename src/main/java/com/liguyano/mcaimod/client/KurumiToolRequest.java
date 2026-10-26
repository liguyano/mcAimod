package com.liguyano.mcaimod.client;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.Set;
public final class KurumiToolRequest {
    private static final Set<String> ALLOWED = Set.of("get_player_status", "get_environment",
            "scan_nearby_entities", "scan_nearby_blocks", "scan_dropped_items", "get_inventory",
            "inspect_container");
    public static String parse(String raw) {
        if (raw == null || raw.length() > 8192) return null;
        try {
            JsonObject object = JsonParser.parseString(raw.strip()).getAsJsonObject();
            if (!object.has("type") || !"tool".equals(object.get("type").getAsString())) return null;
            String tool = object.get("tool").getAsString();
            if ("inspect_slot".equals(tool)) {
                if (!object.has("index")) return null;
                int index = object.get("index").getAsInt();
                return index >= 0 && index <= 255 ? "inspect_slot:" + index : null;
            }
            if ("get_distance".equals(tool)) {
                String kind = object.has("kind") ? object.get("kind").getAsString() : "";
                String target = object.has("target") ? object.get("target").getAsString() : "";
                if (!Set.of("block", "entity", "player", "item").contains(kind)
                        || target.length() > 128 || target.isBlank()) return null;
                if ("player".equals(kind))
                    return target.matches("[A-Za-z0-9_]{1,16}") ? "get_distance:player:" + target : null;
                return target.matches("[a-z0-9_.-]+:[a-z0-9_./-]{1,128}")
                        ? "get_distance:" + kind + ":" + target : null;
            }
            if ("find_nearest_block".equals(tool)) {
                String block = object.has("block_id") ? object.get("block_id").getAsString() : "";
                return block.matches("[a-z0-9_.-]+:[a-z0-9_./-]{1,128}")
                        ? "find_nearest_block:" + block : null;
            }
            if ("get_crafting_recipe".equals(tool)) {
                String item = object.has("item_id") ? object.get("item_id").getAsString() : "";
                return item.matches("[a-z0-9_.-]+:[a-z0-9_./-]{1,128}")
                        ? "get_crafting_recipe:" + item : null;
            }
            if ("check_nearby_chest".equals(tool)) {
                String item = object.has("item_id") ? object.get("item_id").getAsString() : "";
                return item.matches("[a-z0-9_.-]+:[a-z0-9_./-]{1,128}")
                        ? "check_nearby_chest:" + item : null;
            }
            if ("get_nearby_chest_result".equals(tool)) {
                String item = object.has("item_id") ? object.get("item_id").getAsString() : "";
                return item.matches("[a-z0-9_.-]+:[a-z0-9_./-]{1,128}")
                        ? "get_nearby_chest_result:" + item : null;
            }
            if ("check_inventory_item".equals(tool) || "check_inventory_count".equals(tool)) {
                String item = object.has("item_id") ? object.get("item_id").getAsString() : "";
                return item.matches("[a-z0-9_.-]+:[a-z0-9_./-]{1,128}")
                        ? tool + ":" + item : null;
            }
            if ("check_position".equals(tool)) {
                int x = object.get("x").getAsInt(), y = object.get("y").getAsInt(), z = object.get("z").getAsInt();
                int radius = object.has("radius") ? object.get("radius").getAsInt() : 3;
                if (radius < 1 || radius > 32 || y < -64 || y > 320 || Math.abs((long)x) > 30000000 || Math.abs((long)z) > 30000000) return null;
                return "check_position:" + x + ":" + y + ":" + z + ":" + radius;
            }
            return ALLOWED.contains(tool) ? tool : null;
        } catch (RuntimeException ex) { return null; }
    }
    private KurumiToolRequest() {}
}
