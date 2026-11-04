package com.liguyano.mcaimod.client;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class KurumiWaterSurvivalPolicyTest {
    @Test void surfacesWhenEnabledAndSubmerged() {
        assertTrue(KurumiWaterSurvivalPolicy.shouldAscend(true, true, true));
        assertFalse(KurumiWaterSurvivalPolicy.shouldAscend(true, true, false));
    }

    @Test void neverForcesAscentWhenSwitchedOff() {
        assertFalse(KurumiWaterSurvivalPolicy.shouldAscend(false, true, true));
        assertFalse(KurumiWaterSurvivalPolicy.shouldAscend(false, true, false));
    }

    @Test void noAscentOnLand() {
        assertFalse(KurumiWaterSurvivalPolicy.shouldAscend(true, false, true));
    }
}
