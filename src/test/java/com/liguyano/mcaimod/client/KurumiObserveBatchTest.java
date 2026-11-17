package com.liguyano.mcaimod.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class KurumiObserveBatchTest {
    @Test void parserAcceptsBatchObserveAndLegacyObserve() {
        assertTrue(KurumiScriptParser.parse("observe(scan, inventory, env)").isPresent(),
                KurumiScriptParser.lastError());
        assertTrue(KurumiScriptParser.parse("observe(scan)").isPresent(),
                KurumiScriptParser.lastError());
    }

    @Test void combinesLabeledValuesInOneHandoff() {
        JsonObject scan = new JsonObject();
        scan.addProperty("success", true);
        scan.addProperty("tool", "scan_nearby_entities");
        var batch = KurumiObserveBatch.prepare("scan, inventory, env", Map.of(
                "scan", scan, "inventory", new JsonPrimitive("inventory"), "env", new JsonPrimitive("clear")));
        assertEquals(3, batch.values().size());
        assertEquals("scan", batch.values().get(0).label());
        assertTrue(batch.values().get(0).value().contains("\"success\":true"));
        assertEquals(0, batch.screenshotId());
    }

    @Test void supportsSingleValueAndPreservesLongObservations() {
        String large = "x".repeat(6000);
        var batch = KurumiObserveBatch.prepare("result", Map.of("result", new JsonPrimitive(large)));
        assertEquals(1, batch.values().size());
        assertTrue(batch.values().get(0).value().contains(large));
    }

    @Test void screenshotMayAppearAlongsideStructuredValues() {
        JsonObject screenshot = new JsonObject();
        screenshot.addProperty("kind", "screenshot");
        screenshot.addProperty("id", 42);
        var batch = KurumiObserveBatch.prepare("scan, image", Map.of(
                "image", screenshot, "scan", new JsonPrimitive("found")));
        assertEquals(42, batch.screenshotId());
        assertEquals(1, batch.values().size());
    }

    @Test void rejectsEmptyAndMultipleScreenshotInputs() {
        assertThrows(IllegalArgumentException.class,
                () -> KurumiObserveBatch.prepare("", Map.of()));
        JsonObject screenshot = new JsonObject();
        screenshot.addProperty("kind", "screenshot");
        screenshot.addProperty("id", 42);
        assertThrows(IllegalArgumentException.class,
                () -> KurumiObserveBatch.prepare("a, b", Map.of("a", screenshot, "b", screenshot)));
    }
}
