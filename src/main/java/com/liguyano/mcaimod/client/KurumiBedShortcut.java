package com.liguyano.mcaimod.client;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

/** Fixed owner-chat shortcut: find a nearby bed, approach it, then right-click on arrival. */
public final class KurumiBedShortcut {
    private static BlockPos bed;
    private static BlockPos approach;
    private static long since;
    private static final long TIMEOUT_MS = 75_000;

    public static void install() { NeoForge.EVENT_BUS.addListener(KurumiBedShortcut::tick); }
    public static boolean busy() { return bed != null; }

    public static void cancel() {
        if (busy()) BaritoneBridge.stop();
        bed = null;
        approach = null;
    }

    public static BaritoneBridge.Result start() {
        Minecraft mc = Minecraft.getInstance();
        if (busy()) return new BaritoneBridge.Result(false, "正在前往床");
        if (mc.player == null || mc.level == null || mc.gameMode == null)
            return new BaritoneBridge.Result(false, "没有进入游戏世界");
        BlockPos origin = mc.player.blockPosition();
        BlockPos bestBed = null, bestApproach = null;
        double closest = Double.POSITIVE_INFINITY;
        for (BlockPos search : BlockPos.betweenClosed(
                origin.offset(-24,-6,-24), origin.offset(24,6,24))) {
            if (!mc.level.hasChunkAt(search)) continue;
            BlockState state = mc.level.getBlockState(search);
            if (!(state.getBlock() instanceof BedBlock)) continue;
            for (Direction facing : Direction.Plane.HORIZONTAL) {
                BlockPos next = search.relative(facing);
                if (!mc.level.hasChunkAt(next) || !mc.level.hasChunkAt(next.above())
                        || !mc.level.hasChunkAt(next.below())) continue;
                if (!mc.level.getBlockState(next).getCollisionShape(mc.level,next).isEmpty()
                        || !mc.level.getBlockState(next.above()).getCollisionShape(mc.level,next.above()).isEmpty()
                        || mc.level.getBlockState(next.below()).getCollisionShape(mc.level,next.below()).isEmpty())
                    continue;
                double d=next.distSqr(origin);
                if (d<closest) {closest=d;bestBed=search.immutable();bestApproach=next.immutable();}
            }
        }
        if (bestBed == null) return new BaritoneBridge.Result(false, "附近24格已加载区域没有可接近的床");
        var navigation = BaritoneBridge.gotoCoordinates(
                bestApproach.getX(), bestApproach.getY(), bestApproach.getZ());
        if (!navigation.success()) return navigation;
        bed = bestBed;
        approach = bestApproach;
        since = System.currentTimeMillis();
        return new BaritoneBridge.Result(true, "已找到床，正在接近 " + bestBed.toShortString());
    }

    private static void tick(ClientTickEvent.Post event) {
        if (!busy()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.gameMode == null) { cancel(); return; }
        if (System.currentTimeMillis()-since > TIMEOUT_MS) {
            report("前往床超时，已停止");
            cancel();
            return;
        }
        if (!mc.level.hasChunkAt(bed) || !(mc.level.getBlockState(bed).getBlock() instanceof BedBlock)) {
            report("床已经消失或未加载");
            cancel();
            return;
        }
        if (mc.player.distanceToSqr(Vec3.atCenterOf(approach)) > 5.5) return;
        // Also check reach to the actual bed, not just proximity to the path goal.
        if (mc.player.getEyePosition().distanceToSqr(Vec3.atCenterOf(bed)) > 4.5 * 4.5) return;
        BlockPos target = bed;
        // Release our navigation before attempting the interaction.
        cancel();
        var result=mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(target), Direction.UP, target, false));
        report(result.consumesAction() ? "已尝试与床互动，是否能睡觉由服务器判断"
                : "床互动未被接受（可能不是夜晚、床被占用或距离不足）");
    }

    private static void report(String value) {
        KurumiWebMonitor.log("固定睡觉指令：" + value);
        Minecraft mc=Minecraft.getInstance();
        if (mc.player != null && mc.getConnection()!=null) mc.getConnection().sendChat(value);
    }

    private KurumiBedShortcut() {}
}
