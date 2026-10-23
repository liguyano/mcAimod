package com.liguyano.mcaimod.client;

import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.item.ItemEntity;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

/** Find an actually loaded dropped item and navigate to it for vanilla pickup. */
public final class KurumiPickupBridge {
    private static String activeFilter;
    private static long startedAt;
    private static int ticks;
    private static final long PICKUP_LIMIT_MS = 15_000L;
    private static final long STUCK_LIMIT_MS = 5_000L;
    private static final double MIN_PROGRESS_SQUARED = 0.25;
    private static final Set<Integer> skippedTargets = new HashSet<>();
    private static int lastTargetId = -1;
    private static long lastProgressAt;
    private static double bestDistanceSquared;

    public static void install() {
        NeoForge.EVENT_BUS.addListener(KurumiPickupBridge::onTick);
    }

    public static boolean busy() { return activeFilter != null; }

    public static void cancel() {
        activeFilter = null;
        lastTargetId = -1;
        skippedTargets.clear();
        lastProgressAt = 0;
        bestDistanceSquared = Double.POSITIVE_INFINITY;
    }

    public static BaritoneBridge.Result pickupAll(String itemId) {
        if (itemId != null && !itemId.matches("[a-z0-9_.-]+:[a-z0-9_./-]{1,128}"))
            return new BaritoneBridge.Result(false, "物品ID不合法");
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null)
            return new BaritoneBridge.Result(false, "尚未进入游戏");
        if (activeFilter != null) return new BaritoneBridge.Result(false, "上一项拾取任务仍在执行");
        if (nearby(mc, itemId).isEmpty())
            return new BaritoneBridge.Result(false, "附近48格内没有符合条件的掉落物");
        activeFilter = itemId == null ? "*" : itemId;
        startedAt = System.currentTimeMillis();
        ticks = 0;
        lastTargetId = -1;
        skippedTargets.clear();
        lastProgressAt = startedAt;
        bestDistanceSquared = Double.POSITIVE_INFINITY;
        return new BaritoneBridge.Result(true, "开始拾取附近掉落物，本轮最多15秒，卡住的目标会跳过");
    }

    public static boolean hasNearby(String itemId) {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && mc.level != null && !nearby(mc, itemId).isEmpty();
    }

    private static java.util.List<ItemEntity> nearby(Minecraft mc, String filter) {
        return mc.level.getEntities(mc.player, mc.player.getBoundingBox().inflate(48),
                entity -> entity instanceof ItemEntity).stream()
                .map(entity -> (ItemEntity) entity)
                .filter(entity -> !entity.getItem().isEmpty()
                        && (filter == null || "*".equals(filter)
                        || filter.equals(BuiltInRegistries.ITEM.getKey(entity.getItem().getItem()).toString())))
                .sorted(Comparator.comparingDouble(entity -> entity.distanceToSqr(mc.player)))
                .toList();
    }

    private static void finish(Minecraft mc, String message) {
        cancel();
        BaritoneBridge.stop();
        KurumiWebMonitor.log("pickup_item_all: " + message);
        mc.getConnection().sendChat(message);
    }

    private static void onTick(ClientTickEvent.Post event) {
        if (activeFilter == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.getConnection() == null) {
            cancel();
            return;
        }
        if (++ticks % 20 != 1) return;
        long now = System.currentTimeMillis();
        if (now - startedAt >= PICKUP_LIMIT_MS) {
            finish(mc, "本轮拾取已达到15秒上限，继续后续任务。");
            return;
        }
        var targets = nearby(mc, activeFilter).stream()
                .filter(target -> !skippedTargets.contains(target.getId()))
                .toList();
        if (targets.isEmpty()) {
            finish(mc, skippedTargets.isEmpty()
                    ? "附近没有剩余符合条件的可见掉落物，本轮拾取结束。"
                    : "可见掉落物已拾取或无法接近的目标已跳过，本轮拾取结束。");
            return;
        }
        ItemEntity target = targets.get(0);
        int targetId = target.getId();
        double distance = target.distanceToSqr(mc.player);
        boolean newTarget = targetId != lastTargetId;
        if (newTarget) {
            lastTargetId = targetId;
            bestDistanceSquared = distance;
            lastProgressAt = now;
        } else if (distance + MIN_PROGRESS_SQUARED < bestDistanceSquared) {
            bestDistanceSquared = distance;
            lastProgressAt = now;
        } else if (now - lastProgressAt >= STUCK_LIMIT_MS) {
            skippedTargets.add(targetId);
            lastTargetId = -1;
            KurumiWebMonitor.log("pickup_item_all: 5秒没有接近掉落物，跳过实体 " + targetId);
            BaritoneBridge.stop();
            return;
        }
        if (newTarget || ticks % 100 == 1) {
            var result = BaritoneBridge.gotoCoordinates(target.getBlockX(), target.getBlockY(), target.getBlockZ());
            if (!result.success()) {
                skippedTargets.add(targetId);
                lastTargetId = -1;
                KurumiWebMonitor.log("pickup_item_all: 寻路失败，跳过实体 " + targetId + ": " + result.message());
            }
        }
    }

    public static BaritoneBridge.Result pickup(String itemId, int quantity) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null)
            return new BaritoneBridge.Result(false, "尚未进入游戏");
        if (itemId == null || !itemId.matches("[a-z0-9_.-]+:[a-z0-9_./-]{1,128}")
                || quantity < 1 || quantity > 64)
            return new BaritoneBridge.Result(false, "拾取物品名称或数量无效");
        var matches = mc.level.getEntities(mc.player, mc.player.getBoundingBox().inflate(48),
                entity -> entity instanceof ItemEntity).stream()
                .map(entity -> (ItemEntity) entity)
                .filter(entity -> !entity.getItem().isEmpty()
                        && itemId.equals(BuiltInRegistries.ITEM.getKey(entity.getItem().getItem()).toString()))
                .sorted(Comparator.comparingDouble(entity -> entity.distanceToSqr(mc.player)))
                .toList();
        if (matches.isEmpty())
            return new BaritoneBridge.Result(false, "附近48格内没有发现掉落的 " + itemId);
        int visible = matches.stream().mapToInt(entity -> entity.getItem().getCount()).sum();
        if (visible < quantity)
            return new BaritoneBridge.Result(false, "附近只看到 " + visible + " 个 " + itemId + "，少于请求的 " + quantity + " 个");
        ItemEntity target = matches.get(0);
        BaritoneBridge.Result result = BaritoneBridge.gotoCoordinates(target.getBlockX(),
                target.getBlockY(), target.getBlockZ());
        if (!result.success()) return result;
        return new BaritoneBridge.Result(true, "正在前往最近的 " + itemId
                + " 掉落位置（" + target.getBlockX() + ", " + target.getBlockY() + ", "
                + target.getBlockZ() + "）；尚未确认拾取完成");
    }
    private KurumiPickupBridge() {}
}
