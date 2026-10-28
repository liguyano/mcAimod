package com.liguyano.mcaimod.client;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class KurumiScreenshotScriptTest {
    @Test void screenshotCanBeAssignedAndObserved() {
        assertTrue(KurumiScriptParser.parse("image = screenshot()\nobserve(image)").isPresent(),
                KurumiScriptParser.lastError());
        assertTrue(KurumiScriptParser.parse("observe(screenshot())").isPresent(),
                KurumiScriptParser.lastError());
    }
}
