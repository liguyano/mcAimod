package com.liguyano.mcaimod.client;

/** Maps conversational owner aliases to the same exact username used by the chat controller. */
final class KurumiLookPlayerTarget {
    private static final String OWNER = "SekaiSaionji";
    private KurumiLookPlayerTarget() {}

    static String playerName(String target) {
        if ("主人".equals(target) || "主人~".equals(target) || "ご主人様".equals(target)) return OWNER;
        return target != null && target.matches("[A-Za-z0-9_]{3,16}") ? target : null;
    }
}
