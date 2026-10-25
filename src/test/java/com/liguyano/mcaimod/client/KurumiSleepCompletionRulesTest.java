package com.liguyano.mcaimod.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class KurumiSleepCompletionRulesTest {
    @Test void guardsRecentlyAcceptedBedInteraction() {
        assertFalse(KurumiSleepCompletionRules.recentlyInteracted(100_000, 0));
        assertTrue(KurumiSleepCompletionRules.recentlyInteracted(100_000, 99_000));
        assertFalse(KurumiSleepCompletionRules.recentlyInteracted(100_000, 9_000));
        assertFalse(KurumiSleepCompletionRules.recentlyInteracted(100_000, 101_000));
    }
}
