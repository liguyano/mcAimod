package com.liguyano.mcaimod.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;

/** Uniform tool-result envelope without losing the original observation fields. */
final class KurumiToolResults {
    private KurumiToolResults() {}

    static JsonObject normalize(JsonElement raw) {
        JsonObject result;
        if (raw != null && raw.isJsonObject()) {
            result = raw.getAsJsonObject().deepCopy();
        } else {
            result = new JsonObject();
            JsonElement value = raw == null ? JsonNull.INSTANCE : raw.deepCopy();
            result.add("value", value);
            if (value.isJsonArray()) result.add("items", value.deepCopy());
            if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber())
                result.add("count", value.deepCopy());
        }
        // `found=false` is a valid observation, not a tool failure.
        if (!result.has("success")) result.addProperty("success", !result.has("error"));
        return result;
    }

    static JsonElement scalar(JsonElement element) {
        if (element != null && element.isJsonObject()) {
            JsonObject object = element.getAsJsonObject();
            if (object.has("value")) return object.get("value");
            if (object.has("count")) return object.get("count");
        }
        return element;
    }

    static JsonArray array(JsonElement element) {
        if (element != null && element.isJsonArray()) return element.getAsJsonArray();
        if (element != null && element.isJsonObject()) {
            JsonObject object = element.getAsJsonObject();
            if (object.has("items") && object.get("items").isJsonArray())
                return object.getAsJsonArray("items");
            if (object.has("value") && object.get("value").isJsonArray())
                return object.getAsJsonArray("value");
        }
        return null;
    }
}
