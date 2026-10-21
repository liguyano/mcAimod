package com.liguyano.mcaimod.client;

import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.inventory.ContainerInput;

/** Drops items through the normal inventory click packets, not client-only stack edits. */
public final class KurumiDropBridge {
    public static BaritoneBridge.Result drop(String itemId, int quantity) {
        if (itemId == null || !itemId.matches("[a-z0-9_.-]+:[a-z0-9_./-]{1,128}")
                || quantity < 1 || quantity > 64)
            return new BaritoneBridge.Result(false, "物品名称或数量不合法（每次最多64个）");
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.gameMode == null)
            return new BaritoneBridge.Result(false, "当前无法操作背包");
        if (mc.player.containerMenu != mc.player.inventoryMenu)
            return new BaritoneBridge.Result(false, "请先关闭箱子等界面，再丢弃物品");
        int available = 0;
        for (int i = 0; i < 36; i++) {
            var stack = mc.player.getInventory().getItem(i);
            if (!stack.isEmpty() && itemId.equals(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString()))
                available += stack.getCount();
        }
        if (available < quantity)
            return new BaritoneBridge.Result(false, "背包物品不足：" + itemId + "，需要" + quantity + "个，只有" + available + "个");
        int remaining = quantity;
        for (int i = 0; i < 36 && remaining > 0; i++) {
            var stack = mc.player.getInventory().getItem(i);
            if (stack.isEmpty() || !itemId.equals(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString()))
                continue;
            int count = Math.min(remaining, stack.getCount());
            int menuSlot = i < 9 ? i + 36 : i;
            if (count == stack.getCount()) {
                mc.gameMode.handleContainerInput(mc.player.inventoryMenu.containerId,
                        menuSlot, 1, ContainerInput.THROW, mc.player);
            } else {
                for (int j = 0; j < count; j++) {
                    mc.gameMode.handleContainerInput(mc.player.inventoryMenu.containerId,
                            menuSlot, 0, ContainerInput.THROW, mc.player);
                }
            }
            remaining -= count;
        }
        return new BaritoneBridge.Result(true, "已发送丢弃请求：" + itemId + " × " + quantity + "（以服务器实际处理为准）");
    }
    /** Drop every stack from the 36 regular inventory slots, excluding armor and offhand. */
    public static BaritoneBridge.Result dropAll() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.gameMode == null)
            return new BaritoneBridge.Result(false, "当前无法操作背包");
        if (mc.player.containerMenu != mc.player.inventoryMenu)
            return new BaritoneBridge.Result(false, "请先关闭箱子等界面");
        int stacks = 0;
        for (int i = 0; i < 36; i++) {
            var stack = mc.player.getInventory().getItem(i);
            if (stack.isEmpty()) continue;
            int menuSlot = i < 9 ? i + 36 : i;
            mc.gameMode.handleContainerInput(mc.player.inventoryMenu.containerId,
                    menuSlot, 1, ContainerInput.THROW, mc.player);
            stacks++;
        }
        return stacks == 0
                ? new BaritoneBridge.Result(false, "背包主物品栏和快捷栏已经是空的")
                : new BaritoneBridge.Result(true, "已请求丢出 " + stacks
                        + " 组物品（不包括盔甲和副手，以服务器处理为准）");
    }

    private KurumiDropBridge() {}
}
