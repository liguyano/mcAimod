package com.liguyano.mcaimod.client;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** Attempts normal player interaction with a reachable bed; no server commands. */
public final class KurumiSleepBridge {
    public static BaritoneBridge.Result sleep() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.gameMode == null)
            return new BaritoneBridge.Result(false, "当前不在游戏世界");
        BlockPos origin = mc.player.blockPosition();
        BlockPos nearest = null;
        double best = 4.5 * 4.5;
        for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-4, -2, -4),
                origin.offset(4, 2, 4))) {
            if (!mc.level.hasChunkAt(pos) || !(mc.level.getBlockState(pos).getBlock() instanceof BedBlock))
                continue;
            double distance = mc.player.getEyePosition().distanceToSqr(Vec3.atCenterOf(pos));
            if (distance < best) {
                best = distance;
                nearest = pos.immutable();
            }
        }
        if (nearest == null)
            return new BaritoneBridge.Result(false, "附近可交互距离内没有床，请先走到床旁边");
        var result = mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(nearest), Direction.UP, nearest, false));
        return result.consumesAction()
                ? new BaritoneBridge.Result(true, "已尝试与床交互，是否入睡由服务器决定")
                : new BaritoneBridge.Result(false, "床交互未被接受，可能是白天或床不可用");
    }
    private KurumiSleepBridge() {}
}
