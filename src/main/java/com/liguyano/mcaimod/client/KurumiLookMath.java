package com.liguyano.mcaimod.client;

/** Pure view-angle calculation, independent from Minecraft client state. */
final class KurumiLookMath {
    record Angles(float yaw, float pitch) {}
    private KurumiLookMath() {}

    static Angles angles(double yaw, double pitch) {
        if (!Double.isFinite(yaw) || !Double.isFinite(pitch)
                || Math.abs(yaw) > 360 || Math.abs(pitch) > 90)
            throw new IllegalArgumentException("视角角度无效：yaw须在±360、pitch须在±90以内");
        return new Angles((float) yaw, (float) pitch);
    }

    static Angles towards(double fromX, double fromY, double fromZ,
                          double toX, double toY, double toZ) {
        double dx = toX - fromX, dy = toY - fromY, dz = toZ - fromZ;
        double horizontal = Math.hypot(dx, dz);
        if (!Double.isFinite(horizontal) || !Double.isFinite(dy)
                || (horizontal < 1e-6 && Math.abs(dy) < 1e-6))
            throw new IllegalArgumentException("视角目标与眼睛重合或坐标无效");
        return angles(Math.toDegrees(Math.atan2(-dx, dz)),
                -Math.toDegrees(Math.atan2(dy, horizontal)));
    }
}
