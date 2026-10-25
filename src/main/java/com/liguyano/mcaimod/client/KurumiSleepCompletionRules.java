package com.liguyano.mcaimod.client;

/** Pure timing policy: suppress repeated attempts after an accepted bed interaction. */
public final class KurumiSleepCompletionRules {
    public static final long REPEAT_GUARD_MS = 90_000L;

    public static boolean recentlyInteracted(long now, long interactedAt) {
        return interactedAt > 0 && now >= interactedAt && now - interactedAt < REPEAT_GUARD_MS;
    }

    private KurumiSleepCompletionRules() {}
}
