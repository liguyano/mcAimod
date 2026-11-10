package com.liguyano.mcaimod.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class KurumiWaterAvoidanceTimeoutTest {
    @Test void startsWithLandPreferenceBeforeEightSeconds() {
        assertFalse(KurumiWaterAvoidancePolicy.shouldFallback(1_000L, 8_999L));
    }

    @Test void allowsWaterAfterEightSecondsWithoutProgress() {
        assertTrue(KurumiWaterAvoidancePolicy.shouldFallback(1_000L, 9_000L));
    }
}
