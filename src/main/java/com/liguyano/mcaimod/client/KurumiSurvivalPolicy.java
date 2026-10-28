package com.liguyano.mcaimod.client;

/** Pure policy decisions for the local, model-free survival interrupt. */
final class KurumiSurvivalPolicy {
    static final int EAT_AT_FOOD_LEVEL = 14;
    static final int FLEE_DISTANCE = 20;
    static final double MAX_ALLY_DISTANCE_SQUARED = 64 * 64;

    record NearbyPlayer(String name, double x, double z, boolean attacker) {}

    static NearbyPlayer selectAlly(double x, double z, java.util.List<NearbyPlayer> players) {
        NearbyPlayer nearest = null;
        double bestDistance = MAX_ALLY_DISTANCE_SQUARED;
        for (NearbyPlayer player : players) {
            if (player.attacker()) continue;
            double distance = Math.pow(x - player.x(), 2) + Math.pow(z - player.z(), 2);
            if (distance > MAX_ALLY_DISTANCE_SQUARED) continue;
            if ("SekaiSaionji".equals(player.name())) return player;
            if (nearest == null || distance < bestDistance) {
                nearest = player;
                bestDistance = distance;
            }
        }
        return nearest;
    }

    static boolean needsFood(int foodLevel, boolean canEat, boolean alreadyEating) {
        return foodLevel <= EAT_AT_FOOD_LEVEL && canEat && !alreadyEating;
    }

    static int[] fleeTarget(double x, double z, double attackerX, double attackerZ) {
        double dx = x - attackerX;
        double dz = z - attackerZ;
        double distance = Math.hypot(dx, dz);
        if (distance < 0.001) { dx = 1; dz = 0; distance = 1; }
        return new int[] {(int) Math.floor(x + FLEE_DISTANCE * dx / distance),
                (int) Math.floor(z + FLEE_DISTANCE * dz / distance)};
    }

    static int[] towardAlly(double x, double z, NearbyPlayer ally) {
        double dx = ally.x() - x;
        double dz = ally.z() - z;
        double distance = Math.hypot(dx, dz);
        if (distance < 0.001) return new int[] {(int) Math.floor(x), (int) Math.floor(z)};
        double advance = Math.min(FLEE_DISTANCE, Math.max(0, distance - 2));
        return new int[] {(int) Math.floor(x + dx / distance * advance),
                (int) Math.floor(z + dz / distance * advance)};
    }

    private KurumiSurvivalPolicy() {}
}
