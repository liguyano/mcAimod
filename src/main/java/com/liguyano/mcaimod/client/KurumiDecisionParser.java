package com.liguyano.mcaimod.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.Optional;

/** Parse a model response as data, never as an executable command. */
public final class KurumiDecisionParser {
    public record Decision(String reply, Optional<KurumiActionValidator.Action> action, String target, Integer x, Integer y, Integer z, String blockId, Optional<KurumiTaskPlanner.Plan> plan, Optional<KurumiTaskPlanner.Program> program) {}

    /** Completion requires an explicit model decision, never merely end-of-script. */
    public static boolean isFinish(String raw) {
        if (raw == null || raw.length() > 8192) return false;
        try {
            JsonObject object = JsonParser.parseString(raw.strip()).getAsJsonObject();
            return object.has("type") && "finish".equals(object.get("type").getAsString())
                    && !object.has("script") && !object.has("action")
                    && object.has("reason") && object.get("reason").isJsonPrimitive()
                    && !object.get("reason").getAsString().isBlank();
        } catch (RuntimeException ex) { return false; }
    }

    /** A clarification is a conversational question, never an executable script. */
    public static Optional<String> clarification(String raw) {
        if (raw == null || raw.length() > 8192) return Optional.empty();
        try {
            JsonObject object = JsonParser.parseString(raw.strip()).getAsJsonObject();
            if (!object.has("type") || !"clarify".equals(object.get("type").getAsString())
                    || !object.has("question") || !object.get("question").isJsonPrimitive())
                return Optional.empty();
            String question = object.get("question").getAsString().strip();
            if (question.isBlank() || question.length() > 160 || question.contains("\\n")
                    || question.contains("\\r") || question.startsWith("/"))
                return Optional.empty();
            return Optional.of(question);
        } catch (RuntimeException exception) {
            return Optional.empty();
        }
    }

    /** A model may request another observation/planning round after this script finishes. */
    public static boolean wantsContinuation(String raw) {
        if (raw == null || raw.length() > 8192) return false;
        try {
            JsonObject object = JsonParser.parseString(raw.strip()).getAsJsonObject();
            return object.has("continue") && object.get("continue").isJsonPrimitive()
                    && object.get("continue").getAsJsonPrimitive().isBoolean()
                    && object.get("continue").getAsBoolean();
        } catch (RuntimeException ex) {
            return false;
        }
    }

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
                String script = object.get("script").getAsString();
                // Ordinary scripts must not initialize NeoForge config during standalone parser tests.
                if (script.lines().map(String::stripLeading).anyMatch(line ->
                        line.startsWith("from ") || line.startsWith("import ")
                                || line.startsWith("run_skill(")))
                    script = KurumiSkillMemory.configured().expandCalls(script);
                return KurumiScriptParser.parse(script).map(program ->
                        new Decision(reply, Optional.empty(), "", null, null, null, "", Optional.empty(), Optional.of(program)));
            }
            return Optional.empty();
        } catch (RuntimeException error) {
            return Optional.empty();
        }
    }

    private KurumiDecisionParser() {}
}
