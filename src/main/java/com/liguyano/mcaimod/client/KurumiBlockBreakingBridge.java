package com.liguyano.mcaimod.client;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

/** One nearby block breaking action, using vanilla client mining packets and server confirmation. */
public final class KurumiBlockBreakingBridge {
    private static BlockPos target;
    private static String initialBlock;
    private static long started;
    private static boolean lastSucceeded;
    private static final long TIMEOUT_MS = 15_000L;

    public static void install() { NeoForge.EVENT_BUS.addListener(KurumiBlockBreakingBridge::tick); }
    public static boolean busy() { return target != null; }
    public static boolean lastSucceeded() { return lastSucceeded; }

    public static BaritoneBridge.Result start(String blockId, Integer x, Integer y, Integer z) {
        Minecraft mc = Minecraft.getInstance();
        if (busy()) return new BaritoneBridge.Result(false, "正在破坏上一块方块");
        if (mc.player == null || mc.level == null || mc.gameMode == null)
            return new BaritoneBridge.Result(false, "尚未进入世界");
        if (mc.player.containerMenu != mc.player.inventoryMenu)
            return new BaritoneBridge.Result(false, "请先关闭容器界面");
        if (x == null && (blockId == null || !blockId.matches("[a-z0-9_.-]+:[a-z0-9_./-]{1,128}")))
            return new BaritoneBridge.Result(false, "方块ID无效");
        BlockPos best = null;
        if (x != null && y != null && z != null) {
            best = new BlockPos(x,y,z);
        } else {
            double closest = 4.5 * 4.5;
            BlockPos center = mc.player.blockPosition();
            for (BlockPos pos : BlockPos.betweenClosed(center.offset(-4,-4,-4),center.offset(4,4,4))) {
                if (!mc.level.hasChunkAt(pos)) continue;
                String id = BuiltInRegistries.BLOCK.getKey(mc.level.getBlockState(pos).getBlock()).toString();
                if (!blockId.equals(id)) continue;
                double distance = mc.player.getEyePosition().distanceToSqr(
                        net.minecraft.world.phys.Vec3.atCenterOf(pos));
                if (distance < closest) {closest = distance;best = pos.immutable();}
            }
        }
        if (best == null) return new BaritoneBridge.Result(false, "附近4.5格内找不到目标方块");
        if (best.getY() < mc.level.getMinY() || best.getY() >= mc.level.getMaxY()
                || !mc.level.getWorldBorder().isWithinBounds(best) || !mc.level.hasChunkAt(best))
            return new BaritoneBridge.Result(false, "坐标超出世界或方块未加载");
        if (mc.player.getEyePosition().distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(best)) > 4.5 * 4.5)
            return new BaritoneBridge.Result(false, "破坏距离超过4.5格，请先靠近");
        var state = mc.level.getBlockState(best);
        if (state.isAir() || state.getDestroySpeed(mc.level,best) < 0
                || state.is(Blocks.BEDROCK))
            return new BaritoneBridge.Result(false, "目标方块无法破坏");
        String id = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
        if (blockId != null && !blockId.equals(id))
            return new BaritoneBridge.Result(false, "目标位置不是指定方块：" + blockId);
        lastSucceeded=false;
        target=best.immutable();
        initialBlock=id;
        started=System.currentTimeMillis();
        mc.gameMode.startDestroyBlock(best,Direction.UP);
        return new BaritoneBridge.Result(true,"已开始破坏 "+id+" ("+best.toShortString()+")，等待服务器方块状态变化");
    }

    private static void tick(ClientTickEvent.Post event) {
        if (!busy()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.gameMode == null || !mc.level.hasChunkAt(target)) {
            cancel();return;
        }
        if (!initialBlock.equals(BuiltInRegistries.BLOCK.getKey(
                mc.level.getBlockState(target).getBlock()).toString())) {
            lastSucceeded=true;
            target=null;
            initialBlock=null;
            KurumiWebMonitor.log("破坏方块成功：已观察到服务器方块状态改变");
            return;
        }
        if (System.currentTimeMillis()-started > TIMEOUT_MS
                || mc.player.getEyePosition().distanceToSqr(
                net.minecraft.world.phys.Vec3.atCenterOf(target)) > 4.5*4.5) {
            KurumiWebMonitor.log("破坏方块失败：超时或距离过远");
            cancel();
            return;
        }
        mc.gameMode.continueDestroyBlock(target,Direction.UP);
    }

    public static void cancel() {
        Minecraft mc = Minecraft.getInstance();
        if (target != null && mc.gameMode != null) mc.gameMode.stopDestroyBlock();
        target = null;
        initialBlock = null;
        lastSucceeded = false;
    }

    private KurumiBlockBreakingBridge() {}
}
