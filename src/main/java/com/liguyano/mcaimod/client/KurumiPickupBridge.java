package com.liguyano.mcaimod.client;

import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.item.ItemEntity;
import java.util.Comparator;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

/** Find an actually loaded dropped item and navigate to it for vanilla pickup. */
public final class KurumiPickupBridge {
    private static String activeFilter;
    private static long startedAt;
    private static int ticks;
    private static int lastTargetId = -1;

    public static void install() {
        NeoForge.EVENT_BUS.addListener(KurumiPickupBridge::onTick);
    }

    public static boolean busy() { return activeFilter != null; }

    public static void cancel() {
        activeFilter = null;
        lastTargetId = -1;
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
        return new BaritoneBridge.Result(true, "开始连续拾取附近掉落物，直到范围内没有目标或超时");
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

    private static void onTick(ClientTickEvent.Post event) {
        if (activeFilter == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.getConnection() == null) {
            cancel();
            return;
        }
        if (++ticks % 20 != 1) return;
        if (System.currentTimeMillis() - startedAt > 120000) {
            cancel();
            BaritoneBridge.stop();
            mc.getConnection().sendChat("连续拾取已超时，已停止。");
            return;
        }
        var targets = nearby(mc, activeFilter);
        if (targets.isEmpty()) {
            cancel();
            BaritoneBridge.stop();
            mc.getConnection().sendChat("附近已没有符合条件的可见掉落物，拾取任务结束。");
            return;
        }
        ItemEntity target = targets.get(0);
        if (target.getId() != lastTargetId || ticks % 100 == 1) {
            var result = BaritoneBridge.gotoCoordinates(target.getBlockX(), target.getBlockY(), target.getBlockZ());
            if (!result.success()) {
                cancel();
                mc.getConnection().sendChat("拾取导航失败：" + result.message());
                return;
            }
            lastTargetId = target.getId();
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
