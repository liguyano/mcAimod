package com.liguyano.mcaimod.client;

/** Automatic surface swimming is optional, so scripted dives remain possible. */
final class KurumiWaterSurvivalPolicy {
    static boolean shouldAscend(boolean enabled, boolean inWater, boolean headUnderWater) {
        return enabled && inWater && headUnderWater;
    }

    private KurumiWaterSurvivalPolicy() {}
}
