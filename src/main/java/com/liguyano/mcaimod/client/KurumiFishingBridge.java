package com.liguyano.mcaimod.client;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

/** Client-only continuous fishing; uses vanilla rod interaction and actual hook feedback. */
public final class KurumiFishingBridge {
    private enum Phase { EQUIP, NAVIGATE, AIM, WAIT_HOOK, WAIT_BITE, REEL_COOLDOWN }
    private record Task(KurumiFishingLogic.Spot spot, long startedAt) {}
    private static Task task;
    private static Phase phase;
    private static long phaseAt;
    private static int phaseTicks;
    private static int casts, reels, verifiedInventoryGains, failedCasts;
    private static int beforeReelItems;
    private static double previousHookVelocity;
    private static String lastReport = "尚未钓鱼";
    private static final long NAVIGATION_TIMEOUT_MS = 90_000L;
    private static final long BITE_TIMEOUT_MS = 120_000L;
    private static final int MAX_FAILED_CASTS = 3;

    public static void install() { NeoForge.EVENT_BUS.addListener(KurumiFishingBridge::tick); }
    public static boolean busy() { return task != null; }
    public static String status() {
        if (task == null) return lastReport;
        return "自动钓鱼中：" + phase + "；抛竿 " + casts + " 次；收竿 " + reels
                + " 次；背包已确认新增 " + verifiedInventoryGains + " 件；水域 "
                + task.spot().waterCenter();
    }

    public static BaritoneBridge.Result start() {
        if (task != null) return new BaritoneBridge.Result(true, "已经在自动钓鱼：" + status());
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.gameMode == null)
            return new BaritoneBridge.Result(false, "尚未进入游戏世界");
        if (mc.player.containerMenu != mc.player.inventoryMenu)
            return new BaritoneBridge.Result(false, "请先关闭其他容器");
        if (KurumiPlanExecutor.busy() || KurumiLongTaskBridge.busy()
                || KurumiChestCraftTask.busy() || KurumiBedShortcut.busy())
            return new BaritoneBridge.Result(false, "另一个世界任务正在执行，不能同时开始钓鱼");
        if (hasRod(mc) == false)
            return new BaritoneBridge.Result(false, "背包没有鱼竿，需要 minecraft:fishing_rod");
        Optional<KurumiFishingLogic.Spot> spot = findSpot(mc);
        if (spot.isEmpty()) return new BaritoneBridge.Result(false,
                "附近已加载区域找不到至少5×5开放水面和安全岸边，请靠近湖泊或河流后重试");
        task = new Task(spot.get(), System.currentTimeMillis());
        casts = reels = verifiedInventoryGains = failedCasts = 0;
        transition(Phase.EQUIP);
        KurumiWebMonitor.log("开始自动钓鱼：岸边 " + task.spot().stand()
                + "；水面 " + task.spot().waterCenter());
        return new BaritoneBridge.Result(true,
                "已开始自动钓鱼，先装备鱼竿并前往附近安全岸边；将持续到停止或安全条件触发");
    }

    public static BaritoneBridge.Result stop() {
        if (task == null) return new BaritoneBridge.Result(true, "当前没有自动钓鱼任务");
        finish("已由主人或 Agent 停止钓鱼");
        return new BaritoneBridge.Result(true, lastReport);
    }
    public static void cancel() { if (task != null) finish("钓鱼任务已取消"); }

    private static void finish(String reason) {
        Minecraft mc = Minecraft.getInstance();
        // If this task cast a hook, reel it back before yielding control.
        if (mc.player != null && mc.gameMode != null && mc.player.fishing != null && rodInHand(mc))
            mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
        BaritoneBridge.stop();
        lastReport = reason + "；抛竿=" + casts + "，收竿=" + reels
                + "，确认背包新增=" + verifiedInventoryGains;
        KurumiWebMonitor.log(lastReport);
        task = null;
        phase = null;
    }

    private static void fail(String reason) {
        finish("钓鱼中断：" + reason);
        KurumiWebMonitor.state(lastReport);
    }

    private static void transition(Phase next) {
        phase = next;
        phaseAt = System.currentTimeMillis();
        phaseTicks = 0;
    }
    private static boolean hasRod(Minecraft mc) {
        for (int i=0; i<36; i++) if (mc.player.getInventory().getItem(i).is(Items.FISHING_ROD)) return true;
        return false;
    }
    private static boolean rodInHand(Minecraft mc) {
        return mc.player.getMainHandItem().is(Items.FISHING_ROD);
    }
    private static int inventoryItems(Minecraft mc) {
        int count=0;
        for (int i=0;i<36;i++) count+=mc.player.getInventory().getItem(i).getCount();
        return count;
    }
    private static boolean inventoryFull(Minecraft mc) {
        for (int i=0;i<36;i++) {
            var item=mc.player.getInventory().getItem(i);
            if (item.isEmpty() || item.getCount()<item.getMaxStackSize()) return false;
        }
        return true;
    }
    private static boolean rodUnsafe(Minecraft mc) {
        var rod=mc.player.getMainHandItem();
        return rod.isDamageableItem() && rod.getMaxDamage()-rod.getDamageValue()<=2;
    }
    private static KurumiFishingLogic.Point point(BlockPos pos) {
        return new KurumiFishingLogic.Point(pos.getX(), pos.getY(), pos.getZ());
    }
    private static BlockPos block(KurumiFishingLogic.Point pos) {
        return new BlockPos(pos.x(),pos.y(),pos.z());
    }
    private static boolean safeStand(Minecraft mc, BlockPos feet) {
        if (!mc.level.hasChunkAt(feet) || !mc.level.hasChunkAt(feet.below())
                || !mc.level.getWorldBorder().isWithinBounds(feet)) return false;
        if (!mc.level.getBlockState(feet).getCollisionShape(mc.level,feet).isEmpty()
                || !mc.level.getBlockState(feet.above()).getCollisionShape(mc.level,feet.above()).isEmpty()
                || !mc.level.getBlockState(feet.below()).isFaceSturdy(mc.level,feet.below(),Direction.UP))
            return false;
        if (!mc.level.getFluidState(feet).isEmpty() || !mc.level.getFluidState(feet.above()).isEmpty()) return false;
        for (Direction d:Direction.Plane.HORIZONTAL)
            if (mc.level.getFluidState(feet.relative(d)).is(net.minecraft.tags.FluidTags.LAVA)) return false;
        return true;
    }
    private static Optional<KurumiFishingLogic.Spot> findSpot(Minecraft mc) {
        BlockPos origin=mc.player.blockPosition();
        Set<KurumiFishingLogic.Point> water=new HashSet<>();
        for (BlockPos pos:BlockPos.betweenClosed(origin.offset(-24,-8,-24),origin.offset(24,8,24))) {
            if (!mc.level.hasChunkAt(pos) || !mc.level.getFluidState(pos).is(FluidTags.WATER)
                    || !mc.level.getFluidState(pos).isSource()
                    || !mc.level.getBlockState(pos.above()).isAir()) continue;
            water.add(point(pos));
        }
        return KurumiFishingLogic.choose(water,point(origin),p->safeStand(mc,block(p)));
    }
    private static boolean nearStand(Minecraft mc) {
        var stand=task.spot().stand();
        return Math.abs(mc.player.getY()-stand.y())<2.2
                && mc.player.position().distanceToSqr(new Vec3(stand.x()+0.5,mc.player.getY(),stand.z()+0.5))<6.25;
    }
    private static void tick(ClientTickEvent.Post event) {
        if (task == null) return;
        Minecraft mc=Minecraft.getInstance();
        if (mc.player==null || mc.level==null || mc.gameMode==null || mc.getConnection()==null) {
            fail("玩家已离开游戏"); return;
        }
        phaseTicks++;
        long elapsed=System.currentTimeMillis()-phaseAt;
        if (phase == Phase.EQUIP) {
            if (!rodInHand(mc) && phaseTicks == 1) {
                var equip=KurumiEquipBridge.mainHand("minecraft:fishing_rod");
                if (!equip.success()) { fail(equip.message()); return; }
            }
            if (!rodInHand(mc)) {
                if (elapsed>4000) fail("装备鱼竿后服务器未同步到主手");
                return;
            }
            if (rodUnsafe(mc)) { fail("鱼竿耐久即将耗尽"); return; }
            if (!nearStand(mc)) {
                var stand=task.spot().stand();
                var route=BaritoneBridge.gotoCoordinates(stand.x(),stand.y(),stand.z());
                if (!route.success()) { fail(route.message()); return; }
                transition(Phase.NAVIGATE);
            } else transition(Phase.AIM);
            return;
        }
        if (phase == Phase.NAVIGATE) {
            if (nearStand(mc)) { BaritoneBridge.stop(); transition(Phase.AIM); }
            else if (elapsed>NAVIGATION_TIMEOUT_MS) fail("90秒内未能走到水边");
            return;
        }
        if (!rodInHand(mc)) { fail("鱼竿不再位于主手"); return; }
        if (rodUnsafe(mc)) { fail("鱼竿耐久即将耗尽"); return; }
        if (inventoryFull(mc)) { fail("背包已满"); return; }
        if (phase == Phase.AIM) {
            var center=task.spot().waterCenter();
            double dx=center.x()+0.5-mc.player.getX(), dz=center.z()+0.5-mc.player.getZ();
            mc.player.setYRot((float)Math.toDegrees(Math.atan2(-dx,dz)));
            mc.player.setXRot(12.0F);
            if (phaseTicks < 5) return; // Let vanilla synchronize rotation before using the rod.
            if (mc.player.fishing != null) { transition(Phase.WAIT_BITE); return; }
            if (!mc.gameMode.useItem(mc.player,InteractionHand.MAIN_HAND).consumesAction()) {
                fail("服务器没有接受抛竿"); return;
            }
            casts++;
            transition(Phase.WAIT_HOOK);
            return;
        }
        FishingHook hook=mc.player.fishing;
        if (phase == Phase.WAIT_HOOK) {
            if (hook != null) { previousHookVelocity=hook.getDeltaMovement().y; transition(Phase.WAIT_BITE); }
            else if (elapsed>5000) {
                if (++failedCasts>=MAX_FAILED_CASTS) fail("连续抛竿未出现鱼漂");
                else transition(Phase.AIM);
            }
            return;
        }
        if (phase == Phase.WAIT_BITE) {
            if (hook == null || hook.isRemoved()) {
                if (++failedCasts>=MAX_FAILED_CASTS) fail("鱼漂反复消失，无法稳定钓鱼");
                else transition(Phase.AIM);
                return;
            }
            double v=hook.getDeltaMovement().y;
            boolean inWater=hook.isInWater() || mc.level.getFluidState(hook.blockPosition()).is(FluidTags.WATER);
            boolean bite=KurumiFishingLogic.bite(hook.tickCount,inWater,v,previousHookVelocity);
            previousHookVelocity=v;
            if (bite) {
                beforeReelItems=inventoryItems(mc);
                if (!mc.gameMode.useItem(mc.player,InteractionHand.MAIN_HAND).consumesAction()) {
                    fail("服务器未接受收竿"); return;
                }
                reels++;
                failedCasts=0;
                transition(Phase.REEL_COOLDOWN);
            } else if (elapsed>BITE_TIMEOUT_MS) {
                // Reel and re-cast only a bounded number of times; never spam useItem.
                beforeReelItems=inventoryItems(mc);
                mc.gameMode.useItem(mc.player,InteractionHand.MAIN_HAND);
                if (++failedCasts>=MAX_FAILED_CASTS) fail("多次超过两分钟没有有效咬钩");
                else transition(Phase.REEL_COOLDOWN);
            }
            return;
        }
        if (phase == Phase.REEL_COOLDOWN && phaseTicks >= 50) {
            verifiedInventoryGains += Math.max(0,inventoryItems(mc)-beforeReelItems);
            KurumiWebMonitor.log("钓鱼收竿完成，累计" + reels + "次；" + status());
            if (mc.player.fishing != null) { fail("鱼漂未能收回"); return; }
            transition(Phase.AIM);
        }
    }
    private KurumiFishingBridge() {}
}
