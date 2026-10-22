package com.liguyano.mcaimod.client;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.AbstractFurnaceMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

/** Nearby block right-click and item insertion into vanilla furnace-like menus. */
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
        if (!itemId.matches("[a-z0-9_.-]+:[a-z0-9_./-]{1,128}") || quantity < 1 || quantity > 64)
            return new BaritoneBridge.Result(false, "物品 ID 或数量无效");
        if (!slot.equals("input") && !slot.equals("fuel"))
            return new BaritoneBridge.Result(false, "熔炉槽位只能为 input 或 fuel");
        if (pending != null) return new BaritoneBridge.Result(false, "仍在等待上一次放入操作");
        if (mc.player.containerMenu instanceof AbstractFurnaceMenu menu)
            return transfer(menu, itemId, quantity, slot);
        if (mc.player.containerMenu instanceof ChestMenu)
            return KurumiChestBridge.put(itemId, quantity);
        if (mc.player.containerMenu != mc.player.inventoryMenu)
            return new BaritoneBridge.Result(false, "当前容器不是支持的熔炉或箱子");
        return new BaritoneBridge.Result(false, "请先用 interact() 打开附近熔炉或箱子");
    }

    private static BaritoneBridge.Result transfer(AbstractFurnaceMenu menu, String itemId, int quantity, String slot) {
        Minecraft mc = Minecraft.getInstance();
        int dst = slot.equals("fuel") ? 1 : 0;
        int remaining = quantity;
        int sent = 0;
        for (int i = 3; i < menu.slots.size() && remaining > 0; i++) {
            var stack = menu.getSlot(i).getItem();
            if (stack.isEmpty() || !itemId.equals(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString())) continue;
            int amount = Math.min(stack.getCount(), remaining);
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
