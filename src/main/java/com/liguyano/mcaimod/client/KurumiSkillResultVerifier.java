package com.liguyano.mcaimod.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/** No shortcut may declare victory based solely on finishing a script. */
final class KurumiSkillResultVerifier {
    static boolean confirmed(String snapshot) {
        try {
            JsonObject variables = JsonParser.parseString(snapshot).getAsJsonObject();
            JsonElement value = variables.get("result");
            if (value == null || !value.isJsonObject()) return false;
            JsonObject result = value.getAsJsonObject();
            return result.has("success") && result.get("success").getAsBoolean()
                    && result.has("completed") && result.get("completed").getAsBoolean();
        } catch (RuntimeException ex) { return false; }
    }

    static boolean hasVerifiedCompletion(String report) {
        return report != null && report.contains("\"completed\":true")
                && report.contains("\"success\":true");
    }

    private KurumiSkillResultVerifier() {}
}
