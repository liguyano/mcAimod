package com.liguyano.mcaimod.client;

import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

/** Confirm chest withdrawal by inventory delta before starting recipe-book crafting. */
public final class KurumiChestCraftTask {
    private record Job(String ingredient, int amount, String result, int resultAmount,
                       int initialIngredient, int initialResult, long started, int phase) {}
    private static Job job;
    private static int ticks;

    public static void install() { NeoForge.EVENT_BUS.addListener(KurumiChestCraftTask::tick); }
    public static boolean busy() { return job != null; }
    public static void cancel() { job = null; KurumiChestBridge.cancel(); KurumiAutoCraftBridge.cancel(); }
    public static String status() {
        Job j = job;
        return j == null ? "没有取物合成任务" : "取物合成任务：" + j.ingredient() + " → " + j.result()
                + "；阶段：" + (j.phase() == 0 ? "等待箱子取物" : "等待合成结果");
    }
    public static BaritoneBridge.Result start(String ingredient, int amount, String result, int resultAmount) {
        if (job != null || KurumiLongTaskBridge.busy())
            return new BaritoneBridge.Result(false, "已有长任务执行中");
        if (!valid(ingredient) || !valid(result) || amount < 1 || amount > 64
                || resultAmount < 1 || resultAmount > 64)
            return new BaritoneBridge.Result(false, "计划中的物品或数量无效");
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return new BaritoneBridge.Result(false, "尚未进入世界");
        if (KurumiAutoCraftBridge.lookup(result).contains("\"available\":false"))
            return new BaritoneBridge.Result(false, "客户端尚未找到目标物品的合成配方");
        int before = count(ingredient);
        int beforeResult = count(result);
        BaritoneBridge.Result taken = KurumiChestBridge.takeItem(ingredient, amount);
        if (!taken.success()) return taken;
        job = new Job(ingredient, amount, result, resultAmount, before, beforeResult,
                System.currentTimeMillis(), 0);
        ticks = 0;
        return new BaritoneBridge.Result(true, "已开始从箱子取材料，等待背包确认后再合成");
    }
    private static boolean valid(String id) {
        return id != null && id.matches("[a-z0-9_.-]+:[a-z0-9_./-]{1,128}");
    }
    private static int count(String id) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return 0;
        int n = 0;
        for (int i = 0; i < 36; i++) {
            var stack = mc.player.getInventory().getItem(i);
            if (!stack.isEmpty() && id.equals(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString()))
                n += stack.getCount();
        }
        return n;
    }
    private static void say(String text) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() != null) mc.getConnection().sendChat(text);
    }
    private static void tick(ClientTickEvent.Post event) {
        Job j = job;
        if (j == null || ++ticks % 10 != 0) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.getConnection() == null) { cancel(); return; }
        long elapsed = System.currentTimeMillis() - j.started();
        if (elapsed > 60_000) { cancel(); say("取物合成任务超时，已停止"); return; }
        if (j.phase() == 0) {
            if (KurumiChestBridge.busy()) return;
            if (mc.player.containerMenu != mc.player.inventoryMenu) return;
            int gained = count(j.ingredient()) - j.initialIngredient();
            if (gained < j.amount()) {
                if (elapsed > 8000) {
                    cancel();
                    say("箱子材料不足或服务器尚未确认取物，未开始合成");
                }
                return;
            }
            BaritoneBridge.Result crafted = KurumiAutoCraftBridge.craft(j.result(), j.resultAmount());
            if (!crafted.success()) { cancel(); say("无法开始合成：" + crafted.message()); return; }
            job = new Job(j.ingredient(), j.amount(), j.result(), j.resultAmount(),
                    j.initialIngredient(), j.initialResult(), j.started(), 1);
            say("已确认材料进入背包，开始合成 " + j.result());
        } else if (!KurumiAutoCraftBridge.busy()) {
            int made = count(j.result()) - j.initialResult();
            job = null;
            say(made >= j.resultAmount() ? "取物合成任务完成，新增 " + made + " 个 " + j.result()
                    : "合成未达到目标：新增 " + Math.max(0, made) + " 个 " + j.result());
        }
    }
    private KurumiChestCraftTask() {}
}
