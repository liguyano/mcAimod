package com.liguyano.mcaimod.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.Optional;

/** Parse a model response as data, never as an executable command. */
public final class KurumiDecisionParser {
    public record Decision(String reply, Optional<KurumiActionValidator.Action> action, String target, Integer x, Integer y, Integer z) {}

    public static Optional<Decision> parse(String raw) {
        if (raw == null || raw.length() > 8192) return Optional.empty();
        try {
            JsonObject object = JsonParser.parseString(raw.strip()).getAsJsonObject();
            if (!object.has("reply") || !object.get("reply").isJsonPrimitive()) return Optional.empty();
            String reply = object.get("reply").getAsString().strip().replaceAll("[\\r\\n]+", " ");
            if (reply.isEmpty() || reply.length() > 160 || reply.startsWith("/")) return Optional.empty();
            if (!object.has("type") || !object.get("type").isJsonPrimitive()) return Optional.empty();
            String type = object.get("type").getAsString();
            if ("chat".equals(type)) return Optional.of(new Decision(reply, Optional.empty(), "", null, null, null));
            if (!"action".equals(type)) return Optional.empty();
            if (!object.has("action") || !object.get("action").isJsonPrimitive()) return Optional.empty();
            String name = object.get("action").getAsString();
            if ("FOLLOW".equals(name)) {
                String target = object.has("target") ? object.get("target").getAsString() : "";
                if (!target.matches("[A-Za-z0-9_]{3,16}")) return Optional.empty();
                return Optional.of(new Decision(reply, Optional.empty(), target, null, null, null));
            }
            if ("GOTO".equals(name)) {
                if (!object.has("x") || !object.has("y") || !object.has("z")) return Optional.empty();
                int x = object.get("x").getAsInt(), y = object.get("y").getAsInt(), z = object.get("z").getAsInt();
                if (y < -64 || y > 320 || Math.abs((long)x) > 30000000 || Math.abs((long)z) > 30000000) return Optional.empty();
                return Optional.of(new Decision(reply, Optional.empty(), "", x, y, z));
            }
            if (!"FARM".equals(name) && !"STOP".equals(name)) return Optional.empty();
            String item = object.has("item_id") ? object.get("item_id").getAsString() : "";
            int quantity = object.has("quantity") ? object.get("quantity").getAsInt() : 0;
            int radius = object.has("radius") ? object.get("radius").getAsInt() : 0;
            if ("FARM".equals(name) && radius != 16 || "STOP".equals(name) && radius != 0) return Optional.empty();
            Optional<KurumiActionValidator.Action> action = KurumiActionValidator.validate(name, item, quantity, radius);
            return action.map(value -> new Decision(reply, Optional.of(value), "", null, null, null));
        } catch (RuntimeException error) {
            return Optional.empty();
        }
    }

    private KurumiDecisionParser() {}
}
