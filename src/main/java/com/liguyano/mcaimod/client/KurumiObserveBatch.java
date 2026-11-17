package com.liguyano.mcaimod.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Prepares one observe(...) handoff without mutating any source observation. */
final class KurumiObserveBatch {
    record Entry(String label, String value) {}
    record Batch(List<Entry> values, long screenshotId) {}

    private KurumiObserveBatch() {}

    static Batch prepare(String source, Map<String, JsonElement> variables) {
        if (source == null || source.isBlank()) throw new IllegalArgumentException("observe 至少需要一个参数");
        List<String> expressions = split(source);
        List<JsonElement> results = KurumiExpressions.arguments(source, variables);
        if (expressions.size() != results.size()) throw new IllegalArgumentException("observe 参数解析不一致");
        List<Entry> values = new ArrayList<>();
        long screenshotId = 0;
        for (int i = 0; i < results.size(); i++) {
            JsonElement element = results.get(i);
            if (element != null && element.isJsonObject()) {
                JsonObject object = element.getAsJsonObject();
                if (object.has("kind") && "screenshot".equals(object.get("kind").getAsString())) {
                    if (screenshotId != 0) throw new IllegalArgumentException("observe 每次最多返回一张截图");
                    if (!object.has("id") || !object.get("id").isJsonPrimitive())
                        throw new IllegalArgumentException("截图 ID 无效");
                    screenshotId = object.get("id").getAsLong();
                    if (screenshotId <= 0) throw new IllegalArgumentException("截图 ID 无效");
                    continue;
                }
            }
            String expression = expressions.get(i).trim();
            String label = expression.matches("[a-z][a-zA-Z0-9_]*") ? expression : "参数" + (i + 1);
            String value = element != null && (element.isJsonObject() || element.isJsonArray())
                    ? element.toString() : KurumiExpressions.text(element);
            values.add(new Entry(label, value));
        }
        return new Batch(List.copyOf(values), screenshotId);
    }

    private static List<String> split(String source) {
        List<String> segments = new ArrayList<>();
        int depth = 0, start = 0;
        char quote = 0;
        for (int i = 0; i < source.length(); i++) {
            char c = source.charAt(i);
            if (quote != 0) {
                if (c == quote) quote = 0;
            } else if (c == '\'' || c == '"') {
                quote = c;
            } else if (c == '(' || c == '[' || c == '{') {
                depth++;
            } else if (c == ')' || c == ']' || c == '}') {
                depth--;
            } else if (c == ',' && depth == 0) {
                segments.add(source.substring(start, i));
                start = i + 1;
            }
        }
        segments.add(source.substring(start));
        return segments;
    }
}
