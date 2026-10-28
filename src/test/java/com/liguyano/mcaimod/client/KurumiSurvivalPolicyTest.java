package com.liguyano.mcaimod.client;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class KurumiSurvivalPolicyTest {
    @Test void startsEatingOnlyWhenHungryAndAble() {
        assertTrue(KurumiSurvivalPolicy.needsFood(14, true, false));
        assertFalse(KurumiSurvivalPolicy.needsFood(15, true, false));
        assertFalse(KurumiSurvivalPolicy.needsFood(10, false, false));
        assertFalse(KurumiSurvivalPolicy.needsFood(10, true, true));
    }

    @Test void fleesAwayFromAttackerWithoutDividingByZero() {
        assertArrayEquals(new int[] {20, 0}, KurumiSurvivalPolicy.fleeTarget(0, 0, -1, 0));
        assertArrayEquals(new int[] {20, 0}, KurumiSurvivalPolicy.fleeTarget(0, 0, 0, 0));
    }
}
