package com.liguyano.mcaimod.client;

import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Bounded collect -> pickup -> deposit workflow.
 * Progress is checked against actual inventory changes, never assumed from a dispatched command.
 */
public final class KurumiLongTaskBridge {
    private enum Phase { MINING, PICKING_UP, DEPOSITING }
    private record Task(String itemId, int goal, int startCount, long startTime, long phaseTime,
                        Phase phase, int depositCount, int rounds, int lastCount) {}
    private static Task active;
    private static int ticks;
    private static final long MAX_DURATION_MS = 300_000;
    private static final long MINE_ROUND_MS = 20_000;
    private static final int MAX_ROUNDS = 12;
    private static final long PICKUP_LIMIT_MS = 45_000;
    private static final long DEPOSIT_LIMIT_MS = 15_000;

    public static void install() { NeoForge.EVENT_BUS.addListener(KurumiLongTaskBridge::tick); }
    public static boolean busy() { return active != null; }
    public static void cancel() {
        active = null;
        KurumiPickupBridge.cancel();
        BaritoneBridge.stop();
    }
    public static String status() {
        Task t = active;
        return t == null ? "没有正在执行的长任务"
                : "正在执行收集并存箱：" + t.itemId() + "；阶段：" + t.phase()
                  + "；目标新增数量：" + t.goal() + "；当前背包数量：" + count(t.itemId()) + "；轮次：" + t.rounds();
    }
    public static BaritoneBridge.Result collectAndStore(String itemId, int quantity) {
        if (active != null) return new BaritoneBridge.Result(false, "已有长任务在运行，请先停止");
        if (!"minecraft:cherry_log".equals(itemId) && !"minecraft:oak_log".equals(itemId)
                && !"minecraft:spruce_log".equals(itemId) && !"minecraft:birch_log".equals(itemId)
                && !"minecraft:jungle_log".equals(itemId) && !"minecraft:acacia_log".equals(itemId)
                && !"minecraft:dark_oak_log".equals(itemId) && !"minecraft:mangrove_log".equals(itemId)
                && !"minecraft:pale_oak_log".equals(itemId))
            return new BaritoneBridge.Result(false, "目前长任务仅支持原版树木原木");
        if (quantity < 1 || quantity > 64) return new BaritoneBridge.Result(false, "目标数量必须为1至64");
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return new BaritoneBridge.Result(false, "尚未进入世界");
        BaritoneBridge.Result start = BaritoneBridge.mineBlock(itemId);
        if (!start.success()) return start;
        long now = System.currentTimeMillis();
        active = new Task(itemId, quantity, count(itemId), now, now, Phase.MINING, 0, 1, count(itemId));
        ticks = 0;
        return new BaritoneBridge.Result(true, "已开始长任务：采集 " + quantity + " 个 " + itemId()
                + "，随后拾取并尝试存入附近箱子");
    }
    private static String itemId() { return active == null ? "原木" : active.itemId(); }
    private static int count(String itemId) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return 0;
        int n = 0;
        for (int i = 0; i < 36; i++) {
            var stack = mc.player.getInventory().getItem(i);
            if (!stack.isEmpty() && itemId.equals(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString()))
                n += stack.getCount();
        }
        return n;
    }
    private static void say(String text) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() != null) mc.getConnection().sendChat(text);
    }
    private static void fail(String reason) {
        Task failed = active;
        cancel();
        if (failed != null) {
            say("检查未通过：" + reason + "；阶段：" + failed.phase()
                    + "；耗时：" + (System.currentTimeMillis() - failed.phaseTime()) / 1000 + "秒");
            KurumiTaskRecovery.failed("采集并存箱 " + failed.itemId(), reason, failed.itemId());
        }
    }
    private static void tick(ClientTickEvent.Post event) {
        Task t = active;
        if (t == null || ++ticks % 20 != 0) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.getConnection() == null) { cancel(); return; }
        long now = System.currentTimeMillis();
        if (now - t.startTime() > MAX_DURATION_MS) { fail("超过5分钟上限"); return; }
        int current = count(t.itemId());
        if (t.phase() == Phase.MINING) {
            if (current - t.startCount() < t.goal() && now - t.phaseTime() < MINE_ROUND_MS) return;
            BaritoneBridge.stop();
            say("采集阶段耗时 " + (now - t.phaseTime()) / 1000 + " 秒；检查背包新增 "
                    + Math.max(0, current - t.startCount()) + " 个 " + t.itemId());
            active = new Task(t.itemId(), t.goal(), t.startCount(), t.startTime(), now, Phase.PICKING_UP, 0, t.rounds(), current);
            say("采集阶段结束，开始拾取 " + t.itemId());
            BaritoneBridge.Result pickup = KurumiPickupBridge.pickupAll(t.itemId());
            if (!pickup.success()) {
                // No visible drops can be normal if they were already picked up.
                checkCollectedAndContinue();
            }
        } else if (t.phase() == Phase.PICKING_UP) {
            if (KurumiPickupBridge.busy() && now - t.phaseTime() < PICKUP_LIMIT_MS) return;
            KurumiPickupBridge.cancel();
            BaritoneBridge.stop();
            say("拾取阶段耗时 " + (now - t.phaseTime()) / 1000 + " 秒；检查背包当前 "
                    + count(t.itemId()) + " 个 " + t.itemId());
            checkCollectedAndContinue();
        } else if (t.phase() == Phase.DEPOSITING) {
            if (KurumiChestBridge.busy() && now - t.phaseTime() < DEPOSIT_LIMIT_MS) return;
            if (KurumiChestBridge.busy()) { fail("箱子操作超时"); return; }
            int remaining = count(t.itemId());
            active = null;
            say("存箱阶段耗时 " + (now - t.phaseTime()) / 1000 + " 秒；检查背包减少 "
                    + Math.max(0, t.depositCount() - remaining) + " 个 " + t.itemId());
            if (remaining >= t.depositCount()) {
                say("存箱未通过验证，重新规划。");
                KurumiTaskRecovery.failed("采集并存箱 " + t.itemId(), "背包数量没有减少，附近箱子可能不可用", t.itemId());
                return;
            }
            int gathered = Math.max(0, t.depositCount() - t.startCount());
            if (gathered < t.goal()) {
                say("存箱已确认，但收集量 " + gathered + " 未达到目标 " + t.goal() + "，准备重新规划。");
                KurumiTaskRecovery.failed("采集并存箱 " + t.itemId(),
                        "已存箱但采集数量不足，还缺 " + (t.goal() - gathered), t.itemId());
                return;
            }
            say("长任务完成：已验证存箱，背包剩余 " + remaining + " 个 " + t.itemId());
        }
    }
    /** Check actual collected inventory after each mine/pickup round, not mined blocks. */
    private static void checkCollectedAndContinue() {
        Task t = active;
        if (t == null) return;
        int collected = Math.max(0, count(t.itemId()) - t.startCount());
        say("第 " + t.rounds() + " 轮检查：实际新增 " + collected
                + "/" + t.goal() + " 个 " + t.itemId());
        if (collected >= t.goal()) {
            advanceToDeposit();
            return;
        }
        if (t.rounds() >= MAX_ROUNDS
                || System.currentTimeMillis() - t.startTime() >= MAX_DURATION_MS - MINE_ROUND_MS) {
            fail("采集未达到目标，已拾取 " + collected + "/" + t.goal() + " 个");
            return;
        }
        BaritoneBridge.Result start = BaritoneBridge.mineBlock(t.itemId());
        if (!start.success()) {
            fail("继续砍树失败：" + start.message());
            return;
        }
        long now = System.currentTimeMillis();
        active = new Task(t.itemId(), t.goal(), t.startCount(), t.startTime(),
                now, Phase.MINING, 0, t.rounds() + 1, count(t.itemId()));
        say("数量不足，继续第 " + (t.rounds() + 1) + " 轮砍树");
    }

    private static void advanceToDeposit() {
        Task t = active;
        if (t == null) return;
        int n = count(t.itemId());
        if (n <= 0) { fail("没有收集到可存放的原木"); return; }
        long now = System.currentTimeMillis();
        active = new Task(t.itemId(), t.goal(), t.startCount(), t.startTime(), now, Phase.DEPOSITING, n, t.rounds(), n);
        say("拾取阶段结束，正在寻找身边的箱子存放原木");
        BaritoneBridge.Result result = KurumiChestBridge.putItemAll(t.itemId());
        if (!result.success()) fail("存箱失败：" + result.message() + "。请把箱子放在4格内再试");
    }
    private KurumiLongTaskBridge() {}
}
