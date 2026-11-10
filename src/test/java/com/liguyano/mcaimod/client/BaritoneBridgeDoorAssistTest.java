package com.liguyano.mcaimod.client;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BaritoneBridgeDoorAssistTest {
    @Test void onlyAssistAfterNavigationHasStalled() {
        assertFalse(BaritoneBridge.shouldAssistDoor(false, 1_000, 4_000, 0));
        assertFalse(BaritoneBridge.shouldAssistDoor(true, 1_000, 1_900, 0));
        assertTrue(BaritoneBridge.shouldAssistDoor(true, 1_000, 2_100, 0));
    }

    @Test void avoidRepeatedClicksWhileWaitingForDoorSync() {
        assertFalse(BaritoneBridge.shouldAssistDoor(true, 1_000, 6_000, 4_000));
        assertTrue(BaritoneBridge.shouldAssistDoor(true, 1_000, 7_100, 4_000));
    }
}
