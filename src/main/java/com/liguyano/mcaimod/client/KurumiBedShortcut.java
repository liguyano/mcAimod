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
    private static boolean lastInteractionAccepted;
    private static boolean awaitingSleepConfirmation;
    private static long interactionStarted;
    private static long lastConfirmedAt;
    private static final long TIMEOUT_MS = 75_000;
    private static final long CONFIRM_MS = 10_000;

    public static void install() { NeoForge.EVENT_BUS.addListener(KurumiBedShortcut::tick); }
    public static boolean busy() { return bed != null || awaitingSleepConfirmation; }
    /** True only after the client has observed the player sleeping. */
    public static boolean lastInteractionAccepted() { return lastInteractionAccepted; }
    public static boolean sleptRecently() {
        return KurumiSleepCompletionRules.recentlyConfirmed(System.currentTimeMillis(), lastConfirmedAt);
    }

    public static void cancel() {
        if (busy()) BaritoneBridge.stop();
        bed = null;
        approach = null;
        awaitingSleepConfirmation = false;
    }

    public static void resetWorld() {
        cancel();
        lastInteractionAccepted = false;
        lastConfirmedAt = 0;
    }

    public static BaritoneBridge.Result start() {
        Minecraft mc = Minecraft.getInstance();
        if (busy()) return new BaritoneBridge.Result(false, "正在前往床");
        if (mc.player == null || mc.level == null || mc.gameMode == null)
            return new BaritoneBridge.Result(false, "没有进入游戏世界");
        if (mc.player.isSleeping() || sleptRecently()) {
            lastInteractionAccepted = true;
            return new BaritoneBridge.Result(true, "已经确认睡过，无需再次与床交互");
        }
        lastInteractionAccepted = false;
        BlockPos origin = mc.player.blockPosition();
        BlockPos bestBed = null, bestApproach = null, directlyReachableBed = null;
        double closest = Double.POSITIVE_INFINITY, closestDirect = Double.POSITIVE_INFINITY;
        int detectedBeds = 0;
        for (BlockPos search : BlockPos.betweenClosed(
                origin.offset(-24,-6,-24), origin.offset(24,6,24))) {
            if (!mc.level.hasChunkAt(search)) continue;
            BlockState state = mc.level.getBlockState(search);
            if (!(state.getBlock() instanceof BedBlock)) continue;
            detectedBeds++;
            double directDistance = mc.player.getEyePosition().distanceToSqr(Vec3.atCenterOf(search));
            if (directDistance <= 4.5 * 4.5 && directDistance < closestDirect) {
                closestDirect = directDistance;
                directlyReachableBed = search.immutable();
            }
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
        // A bed already within interaction reach does not need a navigable adjacent floor.
        // This also handles beds against walls or with decorative blocks surrounding them.
        if (directlyReachableBed != null)
            return interactDirectly(mc, directlyReachableBed);
        if (bestBed == null) return new BaritoneBridge.Result(false,
                detectedBeds == 0 ? "附近24格已加载区域没有发现床"
                        : "发现 " + detectedBeds + " 个床方块，但没有可接近的落脚点");
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
        if (awaitingSleepConfirmation) {
            if (mc.player.isSleeping()) {
                awaitingSleepConfirmation = false;
                lastInteractionAccepted = true;
                lastConfirmedAt = System.currentTimeMillis();
                report("已确认玩家进入睡眠状态；本次睡觉已完成，不要再次右键床");
            } else if (System.currentTimeMillis() - interactionStarted > CONFIRM_MS) {
                awaitingSleepConfirmation = false;
                lastInteractionAccepted = false;
                report("右键床后10秒内没有观察到入睡；请检查游戏时间、床是否被占用及服务器提示");
            }
            return;
        }
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
        BaritoneBridge.stop();
        bed = null;
        approach = null;
        interactDirectly(mc, target);
    }

    private static BaritoneBridge.Result interactDirectly(Minecraft mc, BlockPos target) {
        var result = mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(target), Direction.UP, target, false));
        lastInteractionAccepted = false;
        if (result.consumesAction()) {
            awaitingSleepConfirmation = true;
            interactionStarted = System.currentTimeMillis();
        }
        String message = result.consumesAction()
                ? "已发送床交互请求，等待玩家实际入睡确认（最多10秒）"
                : "床互动未被接受（可能不是夜晚、床被占用或距离不足）";
        report(message);
        return new BaritoneBridge.Result(result.consumesAction(), message);
    }

    private static void report(String value) {
        KurumiWebMonitor.log("固定睡觉指令：" + value);
        Minecraft mc=Minecraft.getInstance();
        if (mc.player != null && mc.getConnection()!=null) mc.getConnection().sendChat(value);
    }

    private KurumiBedShortcut() {}
}
