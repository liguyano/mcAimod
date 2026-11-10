package com.liguyano.mcaimod.client;

/** Pure timeout decision, safe to test without launching Minecraft. */
final class KurumiWaterAvoidancePolicy {
    private KurumiWaterAvoidancePolicy() {}

    static boolean shouldFallback(long lastProgress, long now) {
        return now - lastProgress >= 8_000L;
    }
}
