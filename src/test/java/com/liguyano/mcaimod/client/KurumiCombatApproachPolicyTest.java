package com.liguyano.mcaimod.client;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class KurumiCombatApproachPolicyTest {
    @Test void attacksOnlyWhenInReach() {
        assertTrue(KurumiCombatApproachPolicy.inAttackRange(2.8 * 2.8));
        assertFalse(KurumiCombatApproachPolicy.inAttackRange(3.2 * 3.2));
    }

    @Test void approachExpiresAndStopsAtCriticalHealth() {
        assertTrue(KurumiCombatApproachPolicy.canContinue(1000, 29000, 12, 20));
        assertFalse(KurumiCombatApproachPolicy.canContinue(1000, 31000, 12, 20));
        assertFalse(KurumiCombatApproachPolicy.canContinue(1000, 2000, 5, 20));
    }
}
