package com.liguyano.mcaimod.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class KurumiStepwisePolicyTest {
    @Test void successfulNormalStageReturnsToPlanner() {
        assertTrue(KurumiStepwisePolicy.shouldReplan(true, false));
    }

    @Test void explicitObservationAndFailureStillReturnToPlanner() {
        assertTrue(KurumiStepwisePolicy.shouldReplan(true, true));
        assertTrue(KurumiStepwisePolicy.shouldReplan(false, false));
    }

    @Test void instructionsBoundEachStageAndRequireExplicitFinish() {
        String guide = KurumiStepwisePolicy.guidance();
        assertTrue(guide.contains("一步"));
        assertTrue(guide.contains("type=finish"));
        assertTrue(guide.contains("success"));
    }
}
