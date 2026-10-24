package com.liguyano.mcaimod.client;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.AbstractFurnaceMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

/** Nearby block interaction and bounded item transfers into supported container menus. */
public final class KurumiInteractionBridge {
    private record Pending(String item, int count, String slot, long since) {}
    private static Pending pending;

    public static void install() { NeoForge.EVENT_BUS.addListener(KurumiInteractionBridge::tick); }
    public static boolean busy() { return pending != null; }
    public static void cancel() { pending = null; }

    public static BaritoneBridge.Result interact(String blockId) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.gameMode == null)
            return new BaritoneBridge.Result(false, "尚未进入游戏");
        if (!blockId.matches("[a-z0-9_.-]+:[a-z0-9_./-]{1,128}"))
            return new BaritoneBridge.Result(false, "方块 ID 无效");
        BlockPos nearest = null;
        double best = 4.5 * 4.5;
        BlockPos origin = mc.player.blockPosition();
        for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-4, -2, -4), origin.offset(4, 2, 4))) {
            if (!mc.level.hasChunkAt(pos)) continue;
            String id = BuiltInRegistries.BLOCK.getKey(mc.level.getBlockState(pos).getBlock()).toString();
            if (!blockId.equals(id)) continue;
            double dist = mc.player.getEyePosition().distanceToSqr(Vec3.atCenterOf(pos));
            if (dist < best) { best = dist; nearest = pos.immutable(); }
        }
        if (nearest == null) return new BaritoneBridge.Result(false, "交互距离内找不到 " + blockId);
        var result = mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(nearest), Direction.UP, nearest, false));
        return new BaritoneBridge.Result(result.consumesAction(),
                result.consumesAction() ? "已尝试交互 " + blockId + "（等待服务器确认）"
                        : "方块交互未被接受");
    }

    public static BaritoneBridge.Result putItem(String itemId, int quantity, String slot) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.gameMode == null)
            return new BaritoneBridge.Result(false, "尚未进入游戏");
        if (itemId == null || !itemId.matches("[a-z0-9_.-]+:[a-z0-9_./-]{1,128}") || quantity < 1 || quantity > 64)
            return new BaritoneBridge.Result(false, "物品 ID 或数量无效");
        if (slot == null || !java.util.Set.of("input", "fuel", "auto",
                "grid_1", "grid_2", "grid_3", "grid_4", "grid_5", "grid_6",
                "grid_7", "grid_8", "grid_9").contains(slot))
            return new BaritoneBridge.Result(false, "槽位只能为 input、fuel、auto 或 grid_1 至 grid_9");
        if (pending != null) return new BaritoneBridge.Result(false, "仍在等待上一次放入操作");
        if (mc.player.containerMenu instanceof AbstractFurnaceMenu menu) {
            if (slot.startsWith("grid_"))
                return new BaritoneBridge.Result(false, "熔炉不支持工作台 grid_1 至 grid_9 槽位");
            return transfer(menu, itemId, quantity, "auto".equals(slot) ? "input" : slot);
        }
        if (mc.player.containerMenu instanceof CraftingMenu menu) {
            if ("fuel".equals(slot))
                return new BaritoneBridge.Result(false, "工作台没有燃料槽 fuel，请使用 input、auto 或 grid_1 至 grid_9");
            int targetSlot = craftingSlotIndex(slot);
            return craftingTransfer(menu, itemId, quantity, targetSlot);
        }
        if (!"auto".equals(slot))
            return new BaritoneBridge.Result(false, "普通容器只支持 auto；grid_1 至 grid_9 仅用于工作台");
        if (mc.player.containerMenu != mc.player.inventoryMenu)
            return genericTransfer(itemId, quantity, true);
        return new BaritoneBridge.Result(false, "请先用 interact() 打开附近熔炉、箱子或工作台");
    }

    /** On a crafting table, input and auto both mean the first compatible crafting cell. */
    static int craftingSlotIndex(String slot) {
        if ("input".equals(slot) || "auto".equals(slot)) return -1;
        if (slot != null && slot.matches("grid_[1-9]")) return Integer.parseInt(slot.substring(5));
        throw new IllegalArgumentException("工作台槽位无效");
    }

    /** CraftingMenu: output=0, 3x3 input grid=1..9, followed by player inventory. */
    private static BaritoneBridge.Result craftingTransfer(CraftingMenu menu, String itemId,
                                                           int quantity, int targetSlot) {
        Minecraft mc = Minecraft.getInstance();
        if (!menu.getCarried().isEmpty())
            return new BaritoneBridge.Result(false, "鼠标光标上有物品，先清空光标");
        int source = -1;
        for (int i = 10; i < menu.slots.size(); i++) {
            Slot inventorySlot = menu.getSlot(i);
            if (inventorySlot.container != mc.player.getInventory() || !inventorySlot.hasItem()
                    || !inventorySlot.mayPickup(mc.player)) continue;
            ItemStack stack = inventorySlot.getItem();
            if (itemId.equals(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString())
                    && stack.getCount() >= quantity) {
                source = i;
                break;
            }
        }
        if (source < 0)
            return new BaritoneBridge.Result(false, "背包里没有单组数量足够的 " + itemId);
        ItemStack sourceStack = menu.getSlot(source).getItem();
        int destination = -1;
        for (int i = targetSlot > 0 ? targetSlot : 1;
             i <= (targetSlot > 0 ? targetSlot : 9); i++) {
            Slot cell = menu.getSlot(i);
            if (!cell.mayPlace(sourceStack)) continue;
            ItemStack existing = cell.getItem();
            if (!existing.isEmpty() && !ItemStack.isSameItemSameComponents(existing, sourceStack)) continue;
            int capacity = Math.min(sourceStack.getMaxStackSize(), cell.getMaxStackSize())
                    - existing.getCount();
            if (capacity >= quantity) {
                destination = i;
                break;
            }
        }
        if (destination < 0)
            return new BaritoneBridge.Result(false, "工作台目标格子已占用、容量不足或不接受该物品");
        int sourceCount = sourceStack.getCount();
        mc.gameMode.handleContainerInput(menu.containerId, source, 0, ContainerInput.PICKUP, mc.player);
        if (quantity == sourceCount) {
            mc.gameMode.handleContainerInput(menu.containerId, destination, 0, ContainerInput.PICKUP, mc.player);
        } else {
            for (int n = 0; n < quantity; n++)
                mc.gameMode.handleContainerInput(menu.containerId, destination, 1, ContainerInput.PICKUP, mc.player);
            mc.gameMode.handleContainerInput(menu.containerId, source, 0, ContainerInput.PICKUP, mc.player);
        }
        return new BaritoneBridge.Result(true, "已尝试向工作台第 " + destination
                + " 格放入 " + itemId + " × " + quantity + "（等待服务器同步确认；未自动领取产物）");
    }

    /** Take a matching result stack from the currently open furnace or crafting table. */
    public static BaritoneBridge.Result takeItem(String itemId, int maxCount) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.gameMode == null)
            return new BaritoneBridge.Result(false, "尚未进入游戏");
        if (itemId == null || !itemId.matches("[a-z0-9_.-]+:[a-z0-9_./-]{1,128}")
                || maxCount < 1 || maxCount > 64)
            return new BaritoneBridge.Result(false, "物品 ID 或数量无效");
        int output;
        if (mc.player.containerMenu instanceof AbstractFurnaceMenu) output = 2;
        else if (mc.player.containerMenu instanceof CraftingMenu) output = 0;
        else if (mc.player.containerMenu != mc.player.inventoryMenu)
            return genericTransfer(itemId, maxCount, false);
        else return new BaritoneBridge.Result(false, "请先打开容器");
        var menu = mc.player.containerMenu;
        var stack = menu.getSlot(output).getItem();
        if (stack.isEmpty() || !itemId.equals(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString()))
            return new BaritoneBridge.Result(false, "产物槽没有 " + itemId);
        if (stack.getCount() > maxCount)
            return new BaritoneBridge.Result(false, "产物槽有 " + stack.getCount()
                    + " 个，超过指定数量；请增大数量后再取（不会多拿）");
        int destination = -1;
        for (int i = menu.slots.size() - 36; i < menu.slots.size(); i++) {
            if (menu.getSlot(i).getItem().isEmpty()) { destination = i; break; }
        }
        if (destination < 0)
            return new BaritoneBridge.Result(false, "背包没有空槽，无法安全取出");
        int amount = stack.getCount();
        mc.gameMode.handleContainerInput(menu.containerId, output, 0, ContainerInput.PICKUP, mc.player);
        mc.gameMode.handleContainerInput(menu.containerId, destination, 0, ContainerInput.PICKUP, mc.player);
        return new BaritoneBridge.Result(true, "已尝试取出 " + itemId + " × " + amount
                + "（以服务器同步为准）");
    }

    /** Move an exact bounded quantity between the player inventory and a generic open menu. */
    private static BaritoneBridge.Result genericTransfer(String itemId, int quantity, boolean putting) {
        Minecraft mc = Minecraft.getInstance();
        AbstractContainerMenu menu = mc.player.containerMenu;
        if (!menu.getCarried().isEmpty())
            return new BaritoneBridge.Result(false, "鼠标光标上已有物品，请先清空光标");
        int source = -1;
        int destination = -1;
        for (int i = 0; i < menu.slots.size(); i++) {
            Slot slot = menu.getSlot(i);
            boolean playerSlot = slot.container == mc.player.getInventory();
            if (playerSlot == putting && source < 0 && slot.hasItem()
                    && itemId.equals(BuiltInRegistries.ITEM.getKey(slot.getItem().getItem()).toString())
                    && slot.mayPickup(mc.player) && slot.getItem().getCount() >= quantity)
                source = i;
        }
        if (source < 0)
            return new BaritoneBridge.Result(false, putting
                    ? "背包中没有单组数量足够的指定物品"
                    : "容器中没有单组数量足够的指定物品");
        ItemStack sourceStack = menu.getSlot(source).getItem();
        int sourceCount = sourceStack.getCount();
        for (int i = 0; i < menu.slots.size(); i++) {
            Slot slot = menu.getSlot(i);
            boolean playerSlot = slot.container == mc.player.getInventory();
            if (playerSlot != !putting || !slot.mayPlace(sourceStack)) continue;
            ItemStack existing = slot.getItem();
            if (!existing.isEmpty() && !ItemStack.isSameItemSameComponents(existing, sourceStack)) continue;
            int capacity = Math.min(sourceStack.getMaxStackSize(), slot.getMaxStackSize()) - existing.getCount();
            if (capacity >= quantity) { destination = i; break; }
        }
        if (destination < 0)
            return new BaritoneBridge.Result(false, "没有能够容纳指定数量的有效目标槽位");
        // One source and one destination; return the remainder to its original slot.
        mc.gameMode.handleContainerInput(menu.containerId, source, 0, ContainerInput.PICKUP, mc.player);
        if (quantity == sourceCount) {
            mc.gameMode.handleContainerInput(menu.containerId, destination, 0, ContainerInput.PICKUP, mc.player);
        } else {
            for (int i = 0; i < quantity; i++)
                mc.gameMode.handleContainerInput(menu.containerId, destination, 1, ContainerInput.PICKUP, mc.player);
            mc.gameMode.handleContainerInput(menu.containerId, source, 0, ContainerInput.PICKUP, mc.player);
        }
        return new BaritoneBridge.Result(true, "已尝试" + (putting ? "放入 " : "取出 ")
                + itemId + " × " + quantity + "（等待服务器背包同步确认）");
    }

    private static BaritoneBridge.Result transfer(AbstractFurnaceMenu menu, String itemId, int quantity, String slot) {
        Minecraft mc = Minecraft.getInstance();
        int dst = slot.equals("fuel") ? 1 : 0;
        int remaining = quantity;
        int sent = 0;
        for (int i = 3; i < menu.slots.size() && remaining > 0; i++) {
            var stack = menu.getSlot(i).getItem();
            if (stack.isEmpty() || !itemId.equals(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString())) continue;
            if (!menu.getSlot(dst).mayPlace(stack))
                return new BaritoneBridge.Result(false, "目标槽位不接受 " + itemId);
            var existing = menu.getSlot(dst).getItem();
            if (!existing.isEmpty() && !net.minecraft.world.item.ItemStack.isSameItemSameComponents(existing, stack))
                return new BaritoneBridge.Result(false, "目标槽位已有不同物品");
            int capacity = Math.min(stack.getMaxStackSize(), menu.getSlot(dst).getMaxStackSize())
                    - existing.getCount();
            if (capacity <= 0) return new BaritoneBridge.Result(false, "目标槽位已满");
            int amount = Math.min(Math.min(stack.getCount(), remaining), capacity);
            // Move onto the cursor, deposit a bounded number into the selected slot,
            // then return any leftover items to the original inventory slot.
            mc.gameMode.handleContainerInput(menu.containerId, i, 0, ContainerInput.PICKUP, mc.player);
            for (int n = 0; n < amount; n++)
                mc.gameMode.handleContainerInput(menu.containerId, dst, 1, ContainerInput.PICKUP, mc.player);
            mc.gameMode.handleContainerInput(menu.containerId, i, 0, ContainerInput.PICKUP, mc.player);
            sent += amount;
            remaining -= amount;
        }
        return sent > 0
                ? new BaritoneBridge.Result(true, "已尝试放入 " + itemId + " × " + sent
                        + " 至 " + slot + " 槽（以服务器同步为准）")
                : new BaritoneBridge.Result(false, "背包没有指定物品，或目标槽不可用");
    }

    private static void tick(ClientTickEvent.Post event) {
        // Reserved for future menu synchronization; no asynchronous transfer is queued.
    }
    private KurumiInteractionBridge() {}
}
