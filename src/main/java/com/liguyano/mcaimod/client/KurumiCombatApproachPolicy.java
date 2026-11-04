package com.liguyano.mcaimod.client;

/** Pure safety and distance policy for explicit player pursuit. */
final class KurumiCombatApproachPolicy {
    static final long APPROACH_TIMEOUT_MS = 30_000L;

    static boolean inAttackRange(double distanceSquared) {
        return distanceSquared <= 9.0;
    }

    static boolean canContinue(long started, long now, float health, float maxHealth) {
        return now - started < APPROACH_TIMEOUT_MS
                && maxHealth > 0 && health / maxHealth >= 0.30f;
    }

    private KurumiCombatApproachPolicy() {}
}
