package com.liguyano.mcaimod.client;

import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.InteractionHand;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

/** Starts one held-item use and waits for the client to observe its completion. */
public final class KurumiItemUseBridge {
    private static final long MAX_USE_MS = 12_000;
    private static boolean busy, succeeded;
    private static long started;
    private static String itemId;
    private static int initialCount;
    private static boolean sawUsing;

    private KurumiItemUseBridge() {}
    public static void install() { NeoForge.EVENT_BUS.addListener(KurumiItemUseBridge::tick); }
    public static boolean busy() { return busy; }
    public static boolean lastSucceeded() { return succeeded; }

    public static BaritoneBridge.Result use(String id) {
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.gameMode == null) return new BaritoneBridge.Result(false, "尚未进入游戏");
        if (busy) return new BaritoneBridge.Result(false, "仍在使用上一件物品");
        if (!mc.player.containerMenu.equals(mc.player.inventoryMenu))
            return new BaritoneBridge.Result(false, "请先关闭容器界面");
        var held = mc.player.getMainHandItem();
        if (held.isEmpty() || !id.equals(BuiltInRegistries.ITEM.getKey(held.getItem()).toString()))
            return new BaritoneBridge.Result(false, "请先 equip_main_hand(\"" + id + "\")，等待装备成功");
        if (held.getUseDuration(mc.player) <= 0)
            return new BaritoneBridge.Result(false, "该物品不支持持续使用");
        if (mc.player.isUsingItem()) return new BaritoneBridge.Result(false, "玩家正在使用其他物品");
        var response = mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
        if (!response.consumesAction()) return new BaritoneBridge.Result(false, "服务器未接受物品使用请求");
        busy = true;
        succeeded = false;
        sawUsing = mc.player.isUsingItem();
        started = System.currentTimeMillis();
        itemId = id;
        initialCount = held.getCount();
        return new BaritoneBridge.Result(true, "开始持续使用 " + id + "，等待完成确认");
    }

    private static void tick(ClientTickEvent.Post event) {
        if (!busy) return;
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.gameMode == null) { cancel(); return; }
        if (mc.player.isUsingItem()) { sawUsing = true; return; }
        var current = mc.player.getMainHandItem();
        boolean changed = current.isEmpty()
                || !itemId.equals(BuiltInRegistries.ITEM.getKey(current.getItem()).toString())
                || current.getCount() < initialCount;
        if (sawUsing && changed) { busy = false; succeeded = true; return; }
        if (System.currentTimeMillis() - started > MAX_USE_MS) cancel();
    }

    public static void cancel() {
        var mc = Minecraft.getInstance();
        if (busy && mc.player != null && mc.gameMode != null && mc.player.isUsingItem())
            mc.gameMode.releaseUsingItem(mc.player);
        busy = false;
        succeeded = false;
    }
}
