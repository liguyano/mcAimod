package com.liguyano.mcaimod.client;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.Set;
public final class KurumiToolRequest {
    private static final Set<String> ALLOWED = Set.of("get_player_status", "get_environment",
            "scan_nearby_entities", "scan_nearby_blocks", "scan_dropped_items", "get_inventory");
    public static String parse(String raw) {
        if (raw == null || raw.length() > 8192) return null;
        try {
            JsonObject object = JsonParser.parseString(raw.strip()).getAsJsonObject();
            if (!object.has("type") || !"tool".equals(object.get("type").getAsString())) return null;
            String tool = object.get("tool").getAsString();
            if ("get_crafting_recipe".equals(tool)) {
                String item = object.has("item_id") ? object.get("item_id").getAsString() : "";
                return item.matches("[a-z0-9_.-]+:[a-z0-9_./-]{1,128}")
                        ? "get_crafting_recipe:" + item : null;
            }
            return ALLOWED.contains(tool) ? tool : null;
        } catch (RuntimeException ex) { return null; }
    }
    private KurumiToolRequest() {}
}
