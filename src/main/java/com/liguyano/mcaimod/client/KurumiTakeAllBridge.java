package com.liguyano.mcaimod.client;

import net.minecraft.client.Minecraft;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

/** Shift-click every accessible non-player slot in the currently open menu. */
public final class KurumiTakeAllBridge {
    private static AbstractContainerMenu active;
    private static int nextSlot;
    private static int ticks;
    private static int attempted;
    private static long started;
    private static boolean lastSucceeded;

    public static void install() { NeoForge.EVENT_BUS.addListener(KurumiTakeAllBridge::tick); }
    public static boolean busy() { return active != null; }
    public static boolean lastSucceeded() { return lastSucceeded; }
    public static void cancel() { active = null; }

    public static BaritoneBridge.Result start() {
        Minecraft mc = Minecraft.getInstance();
        if (busy()) return new BaritoneBridge.Result(false, "仍在取出上一批物品");
        if (mc.player == null || mc.gameMode == null || mc.player.containerMenu == mc.player.inventoryMenu)
            return new BaritoneBridge.Result(false, "请先交互打开有槽位的容器");
        AbstractContainerMenu menu = mc.player.containerMenu;
        if (!menu.getCarried().isEmpty())
            return new BaritoneBridge.Result(false, "鼠标光标上还有物品，请先清空光标");
        boolean hasSource = false;
        for (Slot slot : menu.slots) {
            if (slot.container != mc.player.getInventory() && slot.hasItem() && slot.mayPickup(mc.player)) {
                hasSource = true;
                break;
            }
        }
        if (!hasSource) return new BaritoneBridge.Result(true, "当前容器没有可取物品");
        active = menu;
        nextSlot = 0;
        ticks = 0;
        attempted = 0;
        started = System.currentTimeMillis();
        lastSucceeded = false;
        KurumiWebMonitor.log("take_all_items(): 开始从当前容器逐槽取物，不切换页面");
        return new BaritoneBridge.Result(true, "已开始取出当前容器的全部可取物品");
    }

    private static void finish(String reason) {
        Minecraft mc = Minecraft.getInstance();
        int remaining = 0;
        if (mc.player != null && active == mc.player.containerMenu)
            for (Slot slot : active.slots)
                if (slot.container != mc.player.getInventory() && slot.hasItem() && slot.mayPickup(mc.player))
                    remaining++;
        lastSucceeded = attempted > 0 && remaining == 0;
        KurumiWebMonitor.log("take_all_items(): " + reason + "；尝试快速取物 " + attempted
                + " 组；当前界面剩余 " + remaining + " 组；" + (remaining > 0 ? "可能背包已满或槽位拒绝快速移动" : "已扫描全部槽位"));
        active = null;
    }

    private static void tick(ClientTickEvent.Post event) {
        if (active == null || ++ticks % 3 != 0) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.gameMode == null || mc.player.containerMenu != active) {
            finish("容器已关闭或切换，操作中止");
            return;
        }
        if (System.currentTimeMillis() - started > 30_000L) {
            finish("达到30秒安全时限");
            return;
        }
        while (nextSlot < active.slots.size()) {
            int index = nextSlot++;
            Slot slot = active.getSlot(index);
            if (slot.container == mc.player.getInventory() || !slot.hasItem()
                    || !slot.mayPickup(mc.player)) continue;
            ItemStack stack = slot.getItem();
            if (!hasSpace(mc, stack)) {
                finish("背包没有可容纳该组物品的空间");
                return;
            }
            mc.gameMode.handleContainerInput(active.containerId, index, 0,
                    ContainerInput.QUICK_MOVE, mc.player);
            attempted++;
            return; // One server-synced transfer per few ticks, not a burst of clicks.
        }
        finish("当前页面全部槽位遍历完毕");
    }

    private static boolean hasSpace(Minecraft mc, ItemStack stack) {
        for (Slot slot : active.slots) {
            if (slot.container != mc.player.getInventory() || !slot.mayPlace(stack)) continue;
            ItemStack existing = slot.getItem();
            if (existing.isEmpty()) return true;
            if (ItemStack.isSameItemSameComponents(existing, stack)
                    && existing.getCount() < Math.min(existing.getMaxStackSize(), slot.getMaxStackSize()))
                return true;
        }
        return false;
    }

    private KurumiTakeAllBridge() {}
}
