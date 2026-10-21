package com.liguyano.mcaimod.client;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;

/** Pure Java, client-side wake-word gate. No server-side mod is required. */
public final class KurumiWakeRouter {
    private final Clock clock;
    private final Duration cooldown;
    private Instant lastAccepted;
    private boolean busy;

    public KurumiWakeRouter(Clock clock, Duration cooldown) {
        this.clock = Objects.requireNonNull(clock);
        this.cooldown = Objects.requireNonNull(cooldown);
        if (cooldown.isNegative()) throw new IllegalArgumentException("Negative cooldown");
    }

    public static boolean mentionsKurumi(String text) {
        if (text == null || text.isBlank() || text.length() > 500) return false;
        String normalized = text.toLowerCase(Locale.ROOT);
        int from = 0;
        while ((from = normalized.indexOf("kurumi", from)) >= 0) {
            int end = from + 6;
            boolean left = from == 0 || !isWordPart(normalized.charAt(from - 1));
            boolean right = end == normalized.length() || !isWordPart(normalized.charAt(end));
            if (left && right) return true;
            from = end;
        }
        return false;
    }

    private static boolean isWordPart(char c) {
        return c >= 'a' && c <= 'z' || c >= '0' && c <= '9' || c == '_';
    }

    public synchronized boolean tryAccept(String text) {
        if (!mentionsKurumi(text) || busy) return false;
        Instant now = clock.instant();
        if (lastAccepted != null && now.isBefore(lastAccepted.plus(cooldown))) return false;
        busy = true;
        lastAccepted = now;
        return true;
    }

    public synchronized void finish() {
        busy = false;
    }

    public synchronized void reset() {
        busy = false;
        lastAccepted = null;
    }
}
