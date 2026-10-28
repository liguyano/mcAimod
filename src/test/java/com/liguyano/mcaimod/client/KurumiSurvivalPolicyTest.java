package com.liguyano.mcaimod.client;

import org.junit.jupiter.api.Test;
import java.util.List;
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

    @Test void prefersMasterThenNearestOtherPlayerAndNeverAttacker() {
        var others = List.of(new KurumiSurvivalPolicy.NearbyPlayer("Alex", 8, 0, false),
                new KurumiSurvivalPolicy.NearbyPlayer("SekaiSaionji", 15, 0, false),
                new KurumiSurvivalPolicy.NearbyPlayer("Enemy", 2, 0, true));
        assertEquals("SekaiSaionji", KurumiSurvivalPolicy.selectAlly(0, 0, others).name());
        assertEquals("Alex", KurumiSurvivalPolicy.selectAlly(0, 0,
                List.of(others.get(0), others.get(2))).name());
        assertNull(KurumiSurvivalPolicy.selectAlly(0, 0, List.of(others.get(2))));
        assertArrayEquals(new int[] {13, 0}, KurumiSurvivalPolicy.towardAlly(0, 0, others.get(1)));
    }
}
