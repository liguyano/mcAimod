package com.liguyano.mcaimod.client;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

/** Nearby vanilla chest interaction, followed by bounded item transfer after server opens the menu. */
public final class KurumiChestBridge {
    private record Pending(String itemId, int quantity, long startedAt) {}
    private static Pending pending;
    private static final String ALL_ITEMS = "*";

    public static void install() {
        NeoForge.EVENT_BUS.addListener(KurumiChestBridge::onTick);
    }

    public static void cancel() { pending = null; }

    public static BaritoneBridge.Result open() { return interact(null, 0); }

    public static BaritoneBridge.Result put(String itemId, int quantity) {
        if (itemId == null || !itemId.matches("[a-z0-9_.-]+:[a-z0-9_./-]{1,128}")
                || quantity < 1 || quantity > 64)
            return new BaritoneBridge.Result(false, "物品ID或数量不合法（每次1至64个）");
        return interact(itemId, quantity);
    }

    public static BaritoneBridge.Result putAll() { return interact(ALL_ITEMS, 0); }

    private static BaritoneBridge.Result interact(String itemId, int quantity) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.gameMode == null)
            return new BaritoneBridge.Result(false, "当前不在游戏世界");
        if (pending != null)
            return new BaritoneBridge.Result(false, "上一项箱子操作尚未完成");
        if (itemId != null && !ALL_ITEMS.equals(itemId)) {
            int count = 0;
            for (int i = 0; i < 36; i++) {
                var stack = mc.player.getInventory().getItem(i);
                if (!stack.isEmpty() && itemId.equals(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString()))
                    count += stack.getCount();
            }
            if (count < quantity)
                return new BaritoneBridge.Result(false, "背包中 " + itemId + " 数量不足，需要" + quantity + "个，现有" + count + "个");
        }
        if (mc.player.containerMenu instanceof ChestMenu) {
            if (itemId == null) return new BaritoneBridge.Result(true, "箱子界面已打开");
            return transfer(itemId, quantity);
        }
        if (mc.player.containerMenu != mc.player.inventoryMenu)
            return new BaritoneBridge.Result(false, "请先关闭其他容器界面");
        BlockPos nearest = null;
        double best = 4.5 * 4.5;
        BlockPos origin = mc.player.blockPosition();
        for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-4, -2, -4), origin.offset(4, 2, 4))) {
            if (!mc.level.hasChunkAt(pos) || !(mc.level.getBlockState(pos).getBlock() instanceof ChestBlock))
                continue;
            double distance = mc.player.getEyePosition().distanceToSqr(Vec3.atCenterOf(pos));
            if (distance < best) {
                best = distance;
                nearest = pos.immutable();
            }
        }
        if (nearest == null)
            return new BaritoneBridge.Result(false, "交互范围内找不到普通箱子，请先走近箱子");
        var result = mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(nearest), Direction.UP, nearest, false));
        if (!result.consumesAction())
            return new BaritoneBridge.Result(false, "无法打开箱子，可能被阻挡或服务器拒绝");
        if (itemId != null)
            pending = new Pending(itemId, quantity, System.currentTimeMillis());
        return new BaritoneBridge.Result(true, itemId == null ? "已发送打开箱子的请求" : "正在打开箱子，等待服务器同步后放入物品");
    }

    private static void onTick(ClientTickEvent.Post event) {
        Pending task = pending;
        if (task == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.getConnection() == null) { pending = null; return; }
        if (mc.player.containerMenu instanceof ChestMenu) {
            pending = null;
            BaritoneBridge.Result result = transfer(task.itemId(), task.quantity());
            mc.getConnection().sendChat("[Kurumi] " + (result.success() ? result.message() : "抱歉，操作失败：" + result.message()));
        } else if (System.currentTimeMillis() - task.startedAt() > 5000) {
            pending = null;
            mc.getConnection().sendChat("[Kurumi] 抱歉，操作失败：等待箱子打开超时");
        }
    }

    private static BaritoneBridge.Result transfer(String itemId, int quantity) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.gameMode == null || !(mc.player.containerMenu instanceof ChestMenu menu))
            return new BaritoneBridge.Result(false, "箱子界面尚未打开");
        int chestSlots = menu.getRowCount() * 9;
        if (ALL_ITEMS.equals(itemId)) {
            int stacks = 0;
            for (int slot = chestSlots; slot < chestSlots + 36; slot++) {
                var stack = menu.getSlot(slot).getItem();
                if (stack.isEmpty()) continue;
                mc.gameMode.handleContainerInput(menu.containerId, slot, 0,
                        ContainerInput.QUICK_MOVE, mc.player);
                stacks++;
            }
            return stacks == 0
                    ? new BaritoneBridge.Result(false, "背包主物品栏和快捷栏为空")
                    : new BaritoneBridge.Result(true, "已尝试将 " + stacks
                            + " 组物品快速放入箱子（不含盔甲、副手；以服务器同步为准）");
        }
        int remaining = quantity;
        int sent = 0;
        for (int slot = chestSlots; slot < chestSlots + 36 && remaining > 0; slot++) {
            var stack = menu.getSlot(slot).getItem();
            if (stack.isEmpty() || !itemId.equals(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString()))
                continue;
            int count = stack.getCount();
            if (count <= remaining) {
                // Shift-click performs vanilla stacking: existing matching stacks are filled first.
                // Never right-click every item into separately selected destination slots.
                mc.gameMode.handleContainerInput(menu.containerId, slot, 0, ContainerInput.QUICK_MOVE, mc.player);
                int left = menu.getSlot(slot).getItem().getCount();
                int moved = Math.max(0, count - left);
                remaining -= moved;
                sent += moved;
            } else {
                // Partial stack: reserve one empty chest slot and keep the destination fixed.
                // Using a different destination for each click caused fragmented 1-2 item stacks.
                int dst = -1;
                for (int i = 0; i < chestSlots; i++) {
                    if (menu.getSlot(i).getItem().isEmpty()) { dst = i; break; }
                }
                if (dst < 0) break;
                mc.gameMode.handleContainerInput(menu.containerId, slot, 0, ContainerInput.PICKUP, mc.player);
                for (int i = 0; i < remaining; i++)
                    mc.gameMode.handleContainerInput(menu.containerId, dst, 1, ContainerInput.PICKUP, mc.player);
                mc.gameMode.handleContainerInput(menu.containerId, slot, 0, ContainerInput.PICKUP, mc.player);
                sent += remaining;
                remaining = 0;
            }
        }
        return sent == 0 ? new BaritoneBridge.Result(false, "箱子空间不足，或物品无法转移")
                : new BaritoneBridge.Result(true, "已尝试向箱子放入 " + itemId + " × " + sent
                        + (remaining > 0 ? "；剩余" + remaining + "个未放入" : "")
                        + "（以服务器同步结果为准）");
    }

    private KurumiChestBridge() {}
}
