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
    private static boolean timedHold;
    private static long holdUntil;

    private KurumiItemUseBridge() {}
    public static void install() { NeoForge.EVENT_BUS.addListener(KurumiItemUseBridge::tick); }
    public static boolean busy() { return busy; }
    public static boolean lastSucceeded() { return succeeded; }

    public static BaritoneBridge.Result use(String id) {
        return begin(id, 0);
    }

    /** Hold the player's use key for a bounded duration in seconds. */
    public static BaritoneBridge.Result hold(String id, int seconds) {
        if (seconds < 1 || seconds > 30)
            return new BaritoneBridge.Result(false, "持续使用时间必须为1至30秒");
        return begin(id, seconds);
    }

    private static BaritoneBridge.Result begin(String id, int seconds) {
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
        // Minecraft's normal tick logic releases a use action when the use key is
        // no longer down. Keep the actual key mapping held for the entire action.
        mc.options.keyUse.setDown(true);
        busy = true;
        succeeded = false;
        sawUsing = mc.player.isUsingItem();
        started = System.currentTimeMillis();
        timedHold = seconds > 0;
        holdUntil = started + seconds * 1000L;
        itemId = id;
        initialCount = held.getCount();
        return new BaritoneBridge.Result(true, seconds > 0
                ? "开始持续按住使用键 " + seconds + " 秒：" + id
                : "开始持续使用 " + id + "，等待物品使用完成确认");
    }

    private static void tick(ClientTickEvent.Post event) {
        if (!busy) return;
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.gameMode == null) { cancel(); return; }
        // Minecraft 26.2 does not expose a public mc.screen field in this mapping.
        // Container changes are the relevant blocker for an in-progress item use.
        if (mc.player.containerMenu != mc.player.inventoryMenu) { cancel(); return; }
        if (mc.player.isUsingItem()) sawUsing = true;
        var current = mc.player.getMainHandItem();
        boolean changed = current.isEmpty()
                || !itemId.equals(BuiltInRegistries.ITEM.getKey(current.getItem()).toString())
                || current.getCount() < initialCount;
        if (sawUsing && changed) {
            release();
            succeeded = true;
            return;
        }
        if (timedHold && System.currentTimeMillis() >= holdUntil) {
            release();
            // A duration is a key hold, not proof of successful consumption.
            succeeded = sawUsing;
            return;
        }
        if (System.currentTimeMillis() - started > MAX_USE_MS && !timedHold) cancel();
    }

    public static void cancel() {
        release();
        succeeded = false;
    }

    private static void release() {
        var mc = Minecraft.getInstance();
        mc.options.keyUse.setDown(false);
        if (busy && mc.player != null && mc.gameMode != null && mc.player.isUsingItem())
            mc.gameMode.releaseUsingItem(mc.player);
        busy = false;
    }
}
