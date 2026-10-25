package com.liguyano.mcaimod.client;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/** Persistent client-local startup defaults, independent of the current connection. */
record KurumiSwitchSettings(boolean kurumiEnabled, boolean autoEnabled) {
    static KurumiSwitchSettings parse(String jsonText) {
        JsonObject root = JsonParser.parseString(jsonText).getAsJsonObject();
        return new KurumiSwitchSettings(readBoolean(root, "kurumi_enabled"),
                readBoolean(root, "auto_enabled"));
    }

    private static boolean readBoolean(JsonObject root, String key) {
        if (!root.has(key)) return false;
        var value = root.get(key);
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean())
            throw new IllegalArgumentException(key + " 必须为 true 或 false");
        return value.getAsBoolean();
    }

    String toJson() {
        JsonObject root = new JsonObject();
        root.addProperty("kurumi_enabled", kurumiEnabled);
        root.addProperty("auto_enabled", autoEnabled);
        return new GsonBuilder().setPrettyPrinting().create().toJson(root);
    }
}
