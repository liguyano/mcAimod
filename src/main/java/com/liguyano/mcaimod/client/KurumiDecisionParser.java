package com.liguyano.mcaimod.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.Optional;

/** Parse a model response as data, never as an executable command. */
public final class KurumiDecisionParser {
    public record Decision(String reply, Optional<KurumiActionValidator.Action> action, String target, Integer x, Integer y, Integer z, String blockId, Optional<KurumiTaskPlanner.Plan> plan, Optional<KurumiTaskPlanner.Program> program) {}

    public static Optional<Decision> parse(String raw) {
        if (raw == null || raw.length() > 8192) return Optional.empty();
        try {
            JsonObject object = JsonParser.parseString(raw.strip()).getAsJsonObject();
            if (!object.has("reply") || !object.get("reply").isJsonPrimitive()) return Optional.empty();
            String reply = object.get("reply").getAsString().strip().replaceAll("[\\r\\n]+", " ");
            if (reply.isEmpty() || reply.length() > 160 || reply.startsWith("/")) return Optional.empty();
            if (!object.has("type") || !object.get("type").isJsonPrimitive()) return Optional.empty();
            String type = object.get("type").getAsString();
            if (!"action".equals(type)) return Optional.empty();
            if (!object.has("action") || !object.get("action").isJsonPrimitive()) return Optional.empty();
            String name = object.get("action").getAsString();
            if (!"EXECUTE_SCRIPT".equals(name)) return Optional.empty();
            if ("EXECUTE_SCRIPT".equals(name)) {
                if (!object.has("script") || !object.get("script").isJsonPrimitive()) return Optional.empty();
                return KurumiScriptParser.parse(object.get("script").getAsString()).map(program ->
                        new Decision(reply, Optional.empty(), "", null, null, null, "", Optional.empty(), Optional.of(program)));
            }
        } catch (RuntimeException error) {
            return Optional.empty();
        }
    }

    private KurumiDecisionParser() {}
}
