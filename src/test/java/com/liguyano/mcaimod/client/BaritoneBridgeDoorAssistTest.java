package com.liguyano.mcaimod.client;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BaritoneBridgeDoorAssistTest {
    @Test void onlyAssistAfterNavigationHasStalled() {
        assertFalse(KurumiDoorAssistPolicy.shouldTry(false, true, 1_000, 4_000, 0, 0));
        assertFalse(KurumiDoorAssistPolicy.shouldTry(true, true, 1_000, 1_900, 0, 0));
        assertTrue(KurumiDoorAssistPolicy.shouldTry(true, true, 1_000, 2_100, 0, 0));
    }

    @Test void avoidRepeatedClicksWhileWaitingForDoorSync() {
        assertFalse(KurumiDoorAssistPolicy.shouldTry(true, true, 1_000, 6_000, 4_000, 0));
        assertTrue(KurumiDoorAssistPolicy.shouldTry(true, true, 1_000, 7_100, 4_000, 0));
    }
}
