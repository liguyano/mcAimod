package com.liguyano.mcaimod.client;

/** Decisions for opening a close door during stalled Baritone navigation. */
final class KurumiDoorAssistPolicy {
    private KurumiDoorAssistPolicy() {}

    static boolean shouldTry(boolean enabled, boolean navigating, long lastProgress,
                             long now, long lastInteraction, double distanceSquared) {
        return enabled && navigating
                && now - lastProgress >= 1_000L
                && now - lastInteraction >= 3_000L
                && distanceSquared <= 1.65 * 1.65;
    }
}
