package com.liguyano.mcaimod.client;

/** Pure target-ID matcher, shared with client entity interaction. */
final class KurumiEntityMatcher {
    private KurumiEntityMatcher() {}

    static boolean matches(String targetId, String entityId) {
        if (targetId.equals(entityId)) return true;
        return targetId.startsWith("minecraft:")
                && ((targetId.endsWith("_chest_boat") && entityId.equals("minecraft:chest_boat"))
                    || (targetId.endsWith("_boat") && !targetId.endsWith("_chest_boat")
                        && entityId.equals("minecraft:boat")));
    }
}
