package com.liguyano.mcaimod.client;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.Set;
public final class KurumiToolRequest {
    private static final Set<String> ALLOWED = Set.of("get_player_status", "get_environment",
            "scan_nearby_entities", "scan_nearby_blocks", "get_inventory");
    public static String parse(String raw) {
        if (raw == null || raw.length() > 8192) return null;
        try {
            JsonObject object = JsonParser.parseString(raw.strip()).getAsJsonObject();
            if (!object.has("type") || !"tool".equals(object.get("type").getAsString())) return null;
            String tool = object.get("tool").getAsString();
            return ALLOWED.contains(tool) ? tool : null;
        } catch (RuntimeException ex) { return null; }
    }
    private KurumiToolRequest() {}
}
