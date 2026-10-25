package com.liguyano.mcaimod.client;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/** Turns the player's in-game view without clicking or modifying the world. */
public final class KurumiLookBridge {
    private KurumiLookBridge() {}

    public static BaritoneBridge.Result look(double yaw, double pitch) {
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return new BaritoneBridge.Result(false, "尚未进入世界");
        try {
            apply(mc, KurumiLookMath.angles(yaw, pitch));
            return new BaritoneBridge.Result(true, "视角已调整至 yaw=" + yaw + ", pitch=" + pitch);
        } catch (IllegalArgumentException e) {
            return new BaritoneBridge.Result(false, e.getMessage());
        }
    }

    public static BaritoneBridge.Result lookAt(double x, double y, double z) {
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return new BaritoneBridge.Result(false, "尚未进入世界");
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)
                || Math.abs(x) > 30_000_000 || Math.abs(z) > 30_000_000
                || y < mc.level.getMinY() || y > mc.level.getMaxY())
            return new BaritoneBridge.Result(false, "视角目标坐标超出世界范围");
        try {
            Vec3 eye = mc.player.getEyePosition();
            apply(mc, KurumiLookMath.towards(eye.x, eye.y, eye.z, x, y, z));
            return new BaritoneBridge.Result(true, "已看向 (" + x + ", " + y + ", " + z + ")；未进行交互");
        } catch (IllegalArgumentException e) {
            return new BaritoneBridge.Result(false, e.getMessage());
        }
    }

    public static BaritoneBridge.Result lookAtId(String id) {
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return new BaritoneBridge.Result(false, "尚未进入世界");
        if (id == null || !id.matches("[a-z0-9_.-]+:[a-z0-9_./-]{1,128}"))
            return new BaritoneBridge.Result(false, "视角目标ID无效");
        Vec3 eye = mc.player.getEyePosition();
        Vec3 closest = null;
        double distance = Double.POSITIVE_INFINITY;
        BlockPos origin = mc.player.blockPosition();
        for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-12, -6, -12), origin.offset(12, 6, 12))) {
            if (!mc.level.hasChunkAt(pos)) continue;
            String blockId = BuiltInRegistries.BLOCK.getKey(mc.level.getBlockState(pos).getBlock()).toString();
            if (!id.equals(blockId)) continue;
            Vec3 center = Vec3.atCenterOf(pos);
            double d = eye.distanceToSqr(center);
            if (d < distance) { distance = d; closest = center; }
        }
        for (Entity entity : mc.level.getEntities(mc.player,
                mc.player.getBoundingBox().inflate(16.0), Entity::isAlive)) {
            String entityId = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();
            if (!KurumiEntityMatcher.matches(id, entityId)) continue;
            Vec3 center = entity.getBoundingBox().getCenter();
            double d = eye.distanceToSqr(center);
            if (d < distance) { distance = d; closest = center; }
        }
        if (closest == null) return new BaritoneBridge.Result(false, "附近没有找到可看向的 " + id);
        return lookAt(closest.x, closest.y, closest.z);
    }

    private static void apply(Minecraft mc, KurumiLookMath.Angles angles) {
        mc.player.setYRot(angles.yaw());
        mc.player.setXRot(angles.pitch());
    }
}
