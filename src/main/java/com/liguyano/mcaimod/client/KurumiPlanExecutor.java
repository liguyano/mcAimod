package com.liguyano.mcaimod.client;

import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Executes a validated plan one step at a time, verifying observable postconditions.
 * A failed step stops the plan; no unverified action is treated as completed.
 */
public final class KurumiPlanExecutor {
    private record Run(KurumiTaskPlanner.Plan plan, int index, long started, long stepStarted,
                       int initial, boolean dispatched, int attempts) {}
    private static Run active;
    private static int ticks;
    private static final long TOTAL_LIMIT = 300_000L;
    private static final long STEP_LIMIT = 75_000L;
    private static final int MAX_RETRIES = 3;

    public static void install() { NeoForge.EVENT_BUS.addListener(KurumiPlanExecutor::tick); }
    public static boolean busy() { return active != null; }
    public static void cancel() {
        active = null;
        KurumiChestBridge.cancel();
        KurumiAutoCraftBridge.cancel();
        KurumiPickupBridge.cancel();
        BaritoneBridge.stop();
    }
    public static String status() {
        Run r = active;
        return r == null ? "没有自由组合任务"
                : "自由任务 " + (r.index() + 1) + "/" + r.plan().steps().size()
                + "：" + r.plan().steps().get(r.index()).action()
                + "，失败重试 " + r.attempts() + "/" + MAX_RETRIES;
    }
    public static BaritoneBridge.Result start(KurumiTaskPlanner.Plan plan) {
        if (busy() || KurumiLongTaskBridge.busy() || KurumiChestCraftTask.busy())
            return new BaritoneBridge.Result(false, "已有长任务正在执行");
        if (plan == null || plan.steps().isEmpty() || plan.steps().size() > 8)
            return new BaritoneBridge.Result(false, "任务计划无效");
        long now = System.currentTimeMillis();
        active = new Run(plan, 0, now, now, 0, false, 0);
        ticks = 0;
        return new BaritoneBridge.Result(true, "已接受 " + plan.steps().size() + " 步自由组合任务，将逐步执行并检查");
    }
    private static void say(String message) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() != null) mc.getConnection().sendChat(message);
    }
    private static boolean verified(KurumiTaskPlanner.Step s, int before) {
        int current = KurumiTaskChecks.inventoryCount(s.itemId());
        return switch (s.action()) {
            case "GOTO" -> KurumiTaskChecks.within(s.x(), s.y(), s.z(), 3);
            case "EQUIP_MAIN_HAND" -> {
                Minecraft mc = Minecraft.getInstance();
                yield mc.player != null && s.itemId().equals(
                        net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(
                                mc.player.getMainHandItem().getItem()).toString());
            }
            case "CHEST_TAKE", "CRAFT_ITEM", "PICKUP_ITEM_ALL" -> current > before;
            case "CHEST_PUT", "CHEST_PUT_ITEM_ALL" -> current < before;
            case "MINE" -> current > before;
            default -> false;
        };
    }
    private static BaritoneBridge.Result dispatch(KurumiTaskPlanner.Step s) {
        return switch (s.action()) {
            case "GOTO" -> BaritoneBridge.gotoCoordinates(s.x(), s.y(), s.z());
            case "EQUIP_MAIN_HAND" -> KurumiEquipBridge.mainHand(s.itemId());
            case "CHEST_TAKE" -> KurumiChestBridge.takeItem(s.itemId(), s.quantity());
            case "CHEST_PUT" -> KurumiChestBridge.put(s.itemId(), s.quantity());
            case "CHEST_PUT_ITEM_ALL" -> KurumiChestBridge.putItemAll(s.itemId());
            case "CRAFT_ITEM" -> KurumiAutoCraftBridge.craft(s.itemId(), s.quantity());
            case "PICKUP_ITEM_ALL" -> KurumiPickupBridge.pickupAll(s.itemId());
            case "MINE" -> BaritoneBridge.mineBlock(s.itemId());
            default -> new BaritoneBridge.Result(false, "不支持的步骤");
        };
    }
    private static void fail(Run r, String reason) {
        if (r.attempts() >= MAX_RETRIES || System.currentTimeMillis() - r.started() > TOTAL_LIMIT) {
            say("自由任务已停止：第 " + (r.index() + 1) + " 步失败，达到重试/时间上限：" + reason);
            cancel();
            return;
        }
        say("第 " + (r.index() + 1) + " 步检查失败：" + reason
                + "，准备重试（" + (r.attempts() + 1) + "/" + MAX_RETRIES + "）");
        KurumiChestBridge.cancel();
        KurumiAutoCraftBridge.cancel();
        KurumiPickupBridge.cancel();
        BaritoneBridge.stop();
        long now = System.currentTimeMillis();
        active = new Run(r.plan(), r.index(), r.started(), now, 0, false, r.attempts() + 1);
    }
    private static void tick(ClientTickEvent.Post event) {
        Run r = active;
        if (r == null || ++ticks % 10 != 0) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.getConnection() == null) { cancel(); return; }
        long now = System.currentTimeMillis();
        if (now - r.started() > TOTAL_LIMIT) { say("自由任务超过5分钟，已停止"); cancel(); return; }
        KurumiTaskPlanner.Step s = r.plan().steps().get(r.index());
        if (!r.dispatched()) {
            int before = KurumiTaskChecks.inventoryCount(s.itemId());
            BaritoneBridge.Result result = dispatch(s);
            if (!result.success()) { fail(r, result.message()); return; }
            active = new Run(r.plan(), r.index(), r.started(), now, before, true, r.attempts());
            say("开始步骤 " + (r.index() + 1) + "/" + r.plan().steps().size() + "：" + s.action());
            return;
        }
        if (verified(s, r.initial())) {
            // Wait for asynchronous container and crafting operations to settle before continuing.
            if ((s.action().startsWith("CHEST_") && KurumiChestBridge.busy())
                    || (s.action().equals("CRAFT_ITEM") && KurumiAutoCraftBridge.busy())
                    || (s.action().equals("PICKUP_ITEM_ALL") && KurumiPickupBridge.busy())) return;
            say("第 " + (r.index() + 1) + " 步检查成功，耗时 "
                    + (now - r.stepStarted()) / 1000 + " 秒");
            if (s.action().equals("MINE") || s.action().equals("GOTO")) BaritoneBridge.stop();
            if (r.index() + 1 == r.plan().steps().size()) {
                active = null;
                say("自由组合任务已全部通过检查，执行完成");
            } else {
                active = new Run(r.plan(), r.index() + 1, r.started(), now, 0, false, 0);
            }
            return;
        }
        if (now - r.stepStarted() > STEP_LIMIT) fail(r, "75秒内没有观察到预期状态变化");
    }
    private KurumiPlanExecutor() {}
}
