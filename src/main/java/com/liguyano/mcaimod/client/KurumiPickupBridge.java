package com.liguyano.mcaimod.client;

import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.item.ItemEntity;
import java.util.Comparator;

/** Find an actually loaded dropped item and navigate to it for vanilla pickup. */
public final class KurumiPickupBridge {
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
