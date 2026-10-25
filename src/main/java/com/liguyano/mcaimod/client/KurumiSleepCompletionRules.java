package com.liguyano.mcaimod.client;

/** Pure timing policy: suppress repeated sleep interactions after a confirmed sleep. */
public final class KurumiSleepCompletionRules {
    public static final long REPEAT_GUARD_MS = 90_000L;

    public static boolean recentlyConfirmed(long now, long confirmedAt) {
        return confirmedAt > 0 && now >= confirmedAt && now - confirmedAt < REPEAT_GUARD_MS;
    }

    private KurumiSleepCompletionRules() {}
}
