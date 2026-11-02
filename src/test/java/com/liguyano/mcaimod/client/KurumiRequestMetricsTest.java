package com.liguyano.mcaimod.client;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class KurumiRequestMetricsTest {
    @Test void reportsElapsedAndSizesWithoutMessageContents() {
        String summary = KurumiRequestMetrics.summary(1200, 8450, 140, 1, false);
        assertTrue(summary.contains("1200ms"));
        assertTrue(summary.contains("8450"));
        assertTrue(summary.contains("140"));
        assertTrue(summary.contains("重试次数=1"));
        assertFalse(summary.contains("Bearer"));
    }
}
