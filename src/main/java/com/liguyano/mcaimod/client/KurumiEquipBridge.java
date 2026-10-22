package com.liguyano.mcaimod.client;

import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.inventory.ContainerInput;

/** Equip any explicitly requested inventory item in the main hand via a vanilla inventory swap. */
public final class KurumiEquipBridge {
    public static BaritoneBridge.Result mainHand(String itemId) {
        if (itemId == null || !itemId.matches("[a-z0-9_.-]+:[a-z0-9_./-]{1,128}"))
            return new BaritoneBridge.Result(false, "物品 ID 不合法");
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.gameMode == null)
            return new BaritoneBridge.Result(false, "尚未进入游戏");
        if (mc.player.containerMenu != mc.player.inventoryMenu)
            return new BaritoneBridge.Result(false, "请先关闭其他容器界面");
        var inv = mc.player.getInventory();
        int selected = inv.getSelectedSlot();
        int found = -1;
        for (int slot = 0; slot < 36; slot++) {
            var stack = inv.getItem(slot);
            if (!stack.isEmpty() && itemId.equals(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString())) {
                found = slot;
                if (slot == selected) break;
            }
        }
        if (found < 0) return new BaritoneBridge.Result(false, "背包中没有 " + itemId);
        if (found < 9) {
            inv.setSelectedSlot(found);
            return new BaritoneBridge.Result(true, "已将 " + itemId + " 切换到主手");
        }
        // Vanilla SWAP moves inventory menu slot 9..35 into the selected hotbar slot.
        mc.gameMode.handleContainerInput(mc.player.inventoryMenu.containerId,
                found, selected, ContainerInput.SWAP, mc.player);
        return new BaritoneBridge.Result(true, "已请求将 " + itemId + " 从背包交换到主手，等待服务器同步");
    }

    private KurumiEquipBridge() {}
}
