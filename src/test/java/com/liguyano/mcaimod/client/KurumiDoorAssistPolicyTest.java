package com.liguyano.mcaimod.client;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class KurumiDoorAssistPolicyTest {
    @Test void firstClickDoesNotWaitForRetryCooldown() {
        assertTrue(KurumiDoorAssistPolicy.shouldTry(true, true, 1_000, 2_100, 0, 0));
        assertFalse(KurumiDoorAssistPolicy.shouldTry(true, true, 1_000, 1_900, 0, 0));
    }

    @Test void opensCloseDoorAfterStallWithoutDependingOnLookDirection() {
        assertTrue(KurumiDoorAssistPolicy.shouldTry(true, true, 1_000, 2_050, -10_000, 1.4));
    }

    @Test void neverOpensWhenMovingNotNavigatingOrDoorIsFar() {
        assertFalse(KurumiDoorAssistPolicy.shouldTry(true, true, 1_000, 1_500, -10_000, 1.4));
        assertFalse(KurumiDoorAssistPolicy.shouldTry(true, false, 1_000, 2_050, -10_000, 1.4));
        assertFalse(KurumiDoorAssistPolicy.shouldTry(false, true, 1_000, 2_050, -10_000, 1.4));
        assertFalse(KurumiDoorAssistPolicy.shouldTry(true, true, 1_000, 2_050, -10_000, 3.0));
    }

    @Test void doesNotRepeatedlyToggleTheSameDoor() {
        assertFalse(KurumiDoorAssistPolicy.shouldTry(true, true, 1_000, 2_050, 1_500, 1.4));
        assertTrue(KurumiDoorAssistPolicy.shouldTry(true, true, 1_000, 4_600, 1_500, 1.4));
    }
}
