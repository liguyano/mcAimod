package com.liguyano.mcaimod.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class KurumiToolResultsTest {
    @Test void addsSuccessWithoutDiscardingObservationFields() {
        JsonObject raw = new JsonObject();
        raw.addProperty("tool", "scan_nearby_entities");
        JsonArray entities = new JsonArray();
        JsonObject corpse = new JsonObject();
        corpse.addProperty("type", "corpse:corpse");
        entities.add(corpse);
        raw.add("entities", entities);

        JsonObject normalized = KurumiToolResults.normalize(raw);
        assertTrue(normalized.get("success").getAsBoolean());
        assertEquals("corpse:corpse", normalized.getAsJsonArray("entities")
                .get(0).getAsJsonObject().get("type").getAsString());
        assertFalse(raw.has("success"), "normalization must not mutate shared tool data");
        assertTrue(KurumiExpressions.evaluate("scan.success", Map.of("scan", normalized)).getAsBoolean());
        assertEquals("corpse:corpse", KurumiExpressions.evaluate("scan.entities[0].type",
                Map.of("scan", normalized)).getAsString());
    }

    @Test void preservesExplicitFailureAndMarksErrorsAsFailure() {
        JsonObject failed = new JsonObject();
        failed.addProperty("success", false);
        failed.addProperty("message", "cannot open");
        assertFalse(KurumiToolResults.normalize(failed).get("success").getAsBoolean());

        JsonObject error = new JsonObject();
        error.addProperty("error", "unavailable");
        assertFalse(KurumiToolResults.normalize(error).get("success").getAsBoolean());
        JsonObject notFound = new JsonObject();
        notFound.addProperty("found", false);
        assertTrue(KurumiToolResults.normalize(notFound).get("success").getAsBoolean());
    }

    @Test void numericToolsRetainLegacyArithmeticAndComparison() {
        JsonObject count = KurumiToolResults.normalize(new JsonPrimitive(4));
        assertEquals(4, count.get("count").getAsInt());
        assertEquals(4, count.get("value").getAsInt());
        assertTrue(KurumiExpressions.evaluate("count.success", Map.of("count", count)).getAsBoolean());
        assertTrue(KurumiExpressions.evaluate("count > 0", Map.of("count", count)).getAsBoolean());
        assertTrue(KurumiExpressions.evaluate("count == 4", Map.of("count", count)).getAsBoolean());
        assertEquals(5, KurumiExpressions.evaluate("count + 1", Map.of("count", count)).getAsInt());
    }

    @Test void listToolsRetainLegacyLengthAndArrayIterationAccess() {
        JsonArray items = new JsonArray();
        items.add("bread");
        items.add("apple");
        JsonObject result = KurumiToolResults.normalize(items);
        assertTrue(result.get("success").getAsBoolean());
        assertEquals(2, KurumiExpressions.evaluate("len(result)", Map.of("result", result)).getAsInt());
        assertEquals("bread", KurumiExpressions.evaluate("result.items[0]", Map.of("result", result)).getAsString());
        assertEquals(2, KurumiToolResults.array(result).size());
    }

    @Test void stringToolsRetainLegacyConcatenation() {
        JsonObject result = KurumiToolResults.normalize(new JsonPrimitive("ready"));
        assertEquals("ready!", KurumiExpressions.evaluate("status + '!'",
                Map.of("status", result)).getAsString());
    }
}
