package com.liguyano.mcaimod.client;

import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.EquipmentSlot;

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

    /** Swap an armor item from the player's inventory into its matching armor slot. */
    public static BaritoneBridge.Result armor(String itemId) {
        if (itemId == null || !itemId.matches("[a-z0-9_.-]+:[a-z0-9_./-]{1,128}"))
            return new BaritoneBridge.Result(false, "物品 ID 不合法");
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.gameMode == null)
            return new BaritoneBridge.Result(false, "尚未进入游戏");
        if (mc.player.containerMenu != mc.player.inventoryMenu)
            return new BaritoneBridge.Result(false, "请先关闭其他容器界面");
        var inv = mc.player.getInventory();
        int found = -1;
        EquipmentSlot armorSlot = null;
        for (int i = 0; i < 36; i++) {
            var stack = inv.getItem(i);
            if (stack.isEmpty() || !itemId.equals(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString()))
                continue;
            var equippable = stack.get(DataComponents.EQUIPPABLE);
            if (equippable == null)
                return new BaritoneBridge.Result(false, "指定物品不是可穿戴装备");
            armorSlot = equippable.slot();
            if (armorSlot != EquipmentSlot.HEAD && armorSlot != EquipmentSlot.CHEST
                    && armorSlot != EquipmentSlot.LEGS && armorSlot != EquipmentSlot.FEET)
                return new BaritoneBridge.Result(false, "指定物品不属于四个盔甲槽位");
            found = i;
            break;
        }
        if (found < 0) return new BaritoneBridge.Result(false, "背包中没有 " + itemId);
        int menuSlot = switch (armorSlot) {
            case HEAD -> 5;
            case CHEST -> 6;
            case LEGS -> 7;
            case FEET -> 8;
            default -> -1;
        };
        // SWAP with a hotbar slot or the inventory slot holding the item.
        // Prefer a vanilla pickup sequence to avoid relying on offhand/hotbar-only SWAP.
        int sourceMenuSlot = found < 9 ? found + 36 : found;
        if (!mc.player.containerMenu.getCarried().isEmpty())
            return new BaritoneBridge.Result(false, "请先清空鼠标指针上拿着的物品");
        mc.gameMode.handleContainerInput(mc.player.inventoryMenu.containerId,
                sourceMenuSlot, 0, ContainerInput.PICKUP, mc.player);
        mc.gameMode.handleContainerInput(mc.player.inventoryMenu.containerId,
                menuSlot, 0, ContainerInput.PICKUP, mc.player);
        mc.gameMode.handleContainerInput(mc.player.inventoryMenu.containerId,
                sourceMenuSlot, 0, ContainerInput.PICKUP, mc.player);
        return new BaritoneBridge.Result(true, "已请求穿戴 " + itemId + "，等待服务器同步装备栏");
    }

    public static boolean wearing(String itemId) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || itemId == null) return false;
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST,
                EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            var stack = mc.player.getItemBySlot(slot);
            if (!stack.isEmpty() && itemId.equals(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString()))
                return true;
        }
        return false;
    }

    private KurumiEquipBridge() {}
}
