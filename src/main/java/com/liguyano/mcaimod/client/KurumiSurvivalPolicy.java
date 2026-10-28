package com.liguyano.mcaimod.client;

/** Pure policy decisions for the local, model-free survival interrupt. */
final class KurumiSurvivalPolicy {
    static final int EAT_AT_FOOD_LEVEL = 14;
    static final int FLEE_DISTANCE = 20;

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

    private KurumiSurvivalPolicy() {}
}
