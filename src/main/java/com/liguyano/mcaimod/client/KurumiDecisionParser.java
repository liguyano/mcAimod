package com.liguyano.mcaimod.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.Optional;

/** Parse a model response as data, never as an executable command. */
public final class KurumiDecisionParser {
    public record Decision(String reply, Optional<KurumiActionValidator.Action> action) {}

    public static Optional<Decision> parse(String raw) {
        if (raw == null || raw.length() > 8192) return Optional.empty();
        try {
            JsonObject object = JsonParser.parseString(raw.strip()).getAsJsonObject();
            if (!object.has("reply") || !object.get("reply").isJsonPrimitive()) return Optional.empty();
            String reply = object.get("reply").getAsString().strip().replaceAll("[\\r\\n]+", " ");
            if (reply.isEmpty() || reply.length() > 160 || reply.startsWith("/")) return Optional.empty();
            String type = object.get("type").getAsString();
            if ("chat".equals(type)) return Optional.of(new Decision(reply, Optional.empty()));
            if (!"action".equals(type)) return Optional.empty();
            String name = object.get("action").getAsString();
            String item = object.has("item_id") ? object.get("item_id").getAsString() : "";
            int quantity = object.has("quantity") ? object.get("quantity").getAsInt() : 0;
            int radius = object.has("radius") ? object.get("radius").getAsInt() : 0;
            Optional<KurumiActionValidator.Action> action = KurumiActionValidator.validate(name, item, quantity, radius);
            return action.map(value -> new Decision(reply, Optional.of(value)));
        } catch (RuntimeException error) {
            return Optional.empty();
        }
    }

    private KurumiDecisionParser() {}
}
