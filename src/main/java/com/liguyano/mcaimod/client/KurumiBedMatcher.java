package com.liguyano.mcaimod.client;

/** Generic search alias for all vanilla bed colors; exact IDs retain exact-match behavior. */
final class KurumiBedMatcher {
    private static final java.util.Set<String> COLORS = java.util.Set.of(
            "white", "orange", "magenta", "light_blue", "yellow", "lime",
            "pink", "gray", "light_gray", "cyan", "purple", "blue",
            "brown", "green", "red", "black");

    static boolean matches(String query, String actual) {
        if (query.equals(actual)) return true;
        if (!"minecraft:bed".equals(query) || !actual.startsWith("minecraft:")
                || !actual.endsWith("_bed")) return false;
        String color = actual.substring("minecraft:".length(), actual.length() - "_bed".length());
        return COLORS.contains(color);
    }

    private KurumiBedMatcher() {}
}
