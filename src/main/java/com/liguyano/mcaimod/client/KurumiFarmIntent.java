package com.liguyano.mcaimod.client;

import java.util.Locale;
import java.util.Optional;

/** Conservative intent recognition for farming and emergency stop.
 * Unrecognized requests go to normal AI chat; they never become commands.
 */
public final class KurumiFarmIntent {
    public enum Kind { FARM, STOP }
    public record Intent(Kind kind, int radius) {}

    public static Optional<Intent> parse(String message) {
        if (!KurumiWakeRouter.mentionsKurumi(message)) return Optional.empty();
        String text = message.toLowerCase(Locale.ROOT)
                .replaceAll("(?i)kurumi", "")
                .replaceAll("[\\s，。！!？?,、:：]+", "");
        if (text.length() > 100) return Optional.empty();
        if (text.matches(".*(停止|停下|别动|不要动|取消任务|stop|cancel).*")) {
            return Optional.of(new Intent(Kind.STOP, 0));
        }
        if (text.matches(".*(种田|收割|收庄稼|收农作物|farm|harvest).*")) {
            // Start with a small, fixed radius. Never trust a radius extracted from chat.
            return Optional.of(new Intent(Kind.FARM, 16));
        }
        return Optional.empty();
    }

    private KurumiFarmIntent() {}
}
