package com.liguyano.mcaimod.client;

import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

/** Sequential plan runner with chest preflight and server-menu postflight checks. */
public final class KurumiPlanExecutor {
    private enum Phase { START, PRECHECK, ACT, VERIFY, POSTCHECK }
    private record Run(KurumiTaskPlanner.Plan plan, int index, long started, long stepStarted,
                       int inventoryBefore, int chestBefore, Phase phase, int retries, int round) {}
    private static Run active;
    private static int ticks;
    private static final long TOTAL_LIMIT = 300_000L;
    private static final long STEP_LIMIT = 75_000L;
    private static final int MAX_RETRIES = 3;

    public static void install() { NeoForge.EVENT_BUS.addListener(KurumiPlanExecutor::tick); }
    public static boolean busy() { return active != null; }
    public static void cancel() {
        active = null;
        KurumiCombatBridge.cancel();
        KurumiChestInspector.cancel();
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
                + "（" + r.phase() + "），循环 " + r.round() + "/" + r.plan().loopCount() + "，重试 " + r.retries() + "/" + MAX_RETRIES;
    }
    public static BaritoneBridge.Result start(KurumiTaskPlanner.Plan plan) {
        if (busy() || KurumiLongTaskBridge.busy() || KurumiChestCraftTask.busy())
            return new BaritoneBridge.Result(false, "已有长任务正在执行");
        if (plan == null || plan.steps().isEmpty() || plan.steps().size() > 8 || plan.loopCount() < 1 || plan.loopCount() > 20)
            return new BaritoneBridge.Result(false, "任务计划无效");
        long now = System.currentTimeMillis();
        active = new Run(plan, 0, now, now, 0, -1, Phase.START, 0, 1);
        ticks = 0;
        return new BaritoneBridge.Result(true, "已接受 " + plan.steps().size() + " 步计划，循环 " + plan.loopCount() + " 轮，箱子操作会进行前后核验");
    }
    private static void say(String message) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() != null) mc.getConnection().sendChat(message);
    }
    private static boolean isChest(String action) {
        return action.equals("CHEST_TAKE") || action.equals("CHEST_PUT")
                || action.equals("CHEST_PUT_ITEM_ALL");
    }
    private static boolean verified(KurumiTaskPlanner.Step s, int before) {
        int current = KurumiTaskChecks.inventoryCount(s.itemId());
        return switch (s.action()) {
            case "ATTACK_MOB", "ATTACK_PLAYER" -> !KurumiCombatBridge.busy();
            case "GOTO" -> KurumiTaskChecks.within(s.x(), s.y(), s.z(), 3);
            case "EQUIP_ARMOR" -> KurumiEquipBridge.wearing(s.itemId());
            case "EQUIP_MAIN_HAND" -> {
                Minecraft mc = Minecraft.getInstance();
                yield mc.player != null && s.itemId().equals(BuiltInRegistries.ITEM.getKey(
                        mc.player.getMainHandItem().getItem()).toString());
            }
            case "CHEST_TAKE", "CRAFT_ITEM", "PICKUP_ITEM_ALL", "MINE" -> current > before;
            case "CHEST_PUT", "CHEST_PUT_ITEM_ALL" -> current < before;
            default -> false;
        };
    }
    private static BaritoneBridge.Result dispatch(KurumiTaskPlanner.Step s) {
        return switch (s.action()) {
            case "ATTACK_MOB" -> KurumiCombatBridge.attackMob(s.target());
            case "ATTACK_PLAYER" -> KurumiCombatBridge.attackPlayer(s.target());
            case "GOTO" -> BaritoneBridge.gotoCoordinates(s.x(), s.y(), s.z());
            case "EQUIP_MAIN_HAND" -> KurumiEquipBridge.mainHand(s.itemId());
            case "EQUIP_ARMOR" -> KurumiEquipBridge.armor(s.itemId());
            case "CHEST_TAKE" -> KurumiChestBridge.takeItem(s.itemId(), s.quantity());
            case "CHEST_PUT" -> KurumiChestBridge.put(s.itemId(), s.quantity());
            case "CHEST_PUT_ITEM_ALL" -> KurumiChestBridge.putItemAll(s.itemId());
            case "CRAFT_ITEM" -> KurumiAutoCraftBridge.craft(s.itemId(), s.quantity());
            case "PICKUP_ITEM_ALL" -> KurumiPickupBridge.pickupAll(s.itemId());
            case "MINE" -> BaritoneBridge.mineBlock(s.itemId());
            default -> new BaritoneBridge.Result(false, "不支持的步骤");
        };
    }
    private static void set(Run r, Phase phase, int inventory, int chest) {
        active = new Run(r.plan(), r.index(), r.started(), r.stepStarted(),
                inventory, chest, phase, r.retries(), r.round());
    }
    private static void fail(Run r, String reason) {
        if (r.retries() >= MAX_RETRIES || System.currentTimeMillis() - r.started() > TOTAL_LIMIT) {
            say("自由任务已停止：步骤 " + (r.index() + 1) + " 检查失败，达到重试或时间上限：" + reason);
            cancel();
            return;
        }
        say("步骤 " + (r.index() + 1) + " 检查失败：" + reason
                + "；重新执行检查并重试（" + (r.retries() + 1) + "/" + MAX_RETRIES + "）");
        KurumiChestInspector.cancel();
        KurumiChestBridge.cancel();
        KurumiAutoCraftBridge.cancel();
        KurumiPickupBridge.cancel();
        BaritoneBridge.stop();
        long now = System.currentTimeMillis();
        active = new Run(r.plan(), r.index(), r.started(), now, 0, -1, Phase.START, r.retries() + 1, r.round());
    }
    private static String sAction(Run r) { return r.plan().steps().get(r.index()).action(); }
    private static void next(Run r) {
        long elapsed = (System.currentTimeMillis() - r.stepStarted()) / 1000;
        say("步骤 " + (r.index() + 1) + (sAction(r).startsWith("ATTACK_")
                ? " 攻击尝试已结束（不代表目标被击败）" : " 已检查通过") + "，耗时 " + elapsed + " 秒");
        KurumiTaskPlanner.Step s = r.plan().steps().get(r.index());
        if (s.action().equals("MINE") || s.action().equals("GOTO")) BaritoneBridge.stop();
        long now = System.currentTimeMillis();
        if (r.index() + 1 == r.plan().steps().size()) {
            if (r.round() >= r.plan().loopCount()) {
                active = null;
                say("自由组合任务完成 " + r.round() + "/" + r.plan().loopCount() + " 轮");
            } else if (now - r.started() >= TOTAL_LIMIT) {
                say("循环任务达到总时限，已停止");
                cancel();
            } else {
                say("第 " + r.round() + " 轮结束，开始第 " + (r.round() + 1) + " 轮");
                active = new Run(r.plan(), 0, r.started(), now, 0, -1, Phase.START, 0, r.round() + 1);
            }
        } else {
            active = new Run(r.plan(), r.index() + 1, r.started(), now, 0, -1, Phase.START, 0, r.round());
        }
    }
    private static void tick(ClientTickEvent.Post event) {
        Run r = active;
        if (r == null || ++ticks % 10 != 0) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.getConnection() == null) { cancel(); return; }
        long now = System.currentTimeMillis();
        if (now - r.started() > TOTAL_LIMIT) { say("自由任务超过5分钟，已停止"); cancel(); return; }
        KurumiTaskPlanner.Step s = r.plan().steps().get(r.index());
        if (now - r.stepStarted() > STEP_LIMIT) { fail(r, "75秒内没有通过检查"); return; }
        switch (r.phase()) {
            case START -> {
                if (isChest(s.action())) {
                    String result = KurumiChestInspector.check(s.itemId());
                    if (result.contains("error")) { fail(r, "无法检查附近箱子：" + result); return; }
                    set(r, Phase.PRECHECK, 0, -1);
                    say("步骤 " + (r.index() + 1) + "：先检查附近箱子里的 " + s.itemId());
                } else set(r, Phase.ACT, 0, -1);
            }
            case PRECHECK -> {
                if (KurumiChestInspector.busy()) return;
                int count = KurumiChestInspector.observedCount(s.itemId());
                if (count < 0) { fail(r, "没有获取到服务器箱子物品数量"); return; }
                if (s.action().equals("CHEST_TAKE") && count < s.quantity()) {
                    fail(r, "箱子材料不足：需要 " + s.quantity() + "，实际 " + count);
                    return;
                }
                if (!s.action().equals("CHEST_TAKE")
                        && KurumiTaskChecks.inventoryCount(s.itemId()) <= 0) {
                    fail(r, "背包中没有待存物品");
                    return;
                }
                say("箱子检查通过：" + s.itemId() + " × " + count);
                set(r, Phase.ACT, 0, count);
            }
            case ACT -> {
                if (KurumiChestInspector.busy()) return;
                int before = KurumiTaskChecks.inventoryCount(s.itemId());
                BaritoneBridge.Result result = dispatch(s);
                if (!result.success()) { fail(r, result.message()); return; }
                set(r, Phase.VERIFY, before, r.chestBefore());
                say("开始步骤 " + (r.index() + 1) + "/" + r.plan().steps().size() + "：" + s.action());
            }
            case VERIFY -> {
                if (s.action().startsWith("ATTACK_") && now - r.stepStarted() < 700) return;
                if (!verified(s, r.inventoryBefore())) return;
                if ((isChest(s.action()) && KurumiChestBridge.busy())
                        || (s.action().equals("CRAFT_ITEM") && KurumiAutoCraftBridge.busy())
                        || (s.action().equals("PICKUP_ITEM_ALL") && KurumiPickupBridge.busy())) return;
                if (isChest(s.action())) {
                    String result = KurumiChestInspector.check(s.itemId());
                    if (result.contains("error")) { fail(r, "存取后箱子检查失败：" + result); return; }
                    set(r, Phase.POSTCHECK, r.inventoryBefore(), r.chestBefore());
                } else next(r);
            }
            case POSTCHECK -> {
                if (KurumiChestInspector.busy()) return;
                int count = KurumiChestInspector.observedCount(s.itemId());
                if (count < 0) { fail(r, "没有取得存取后的箱子数量"); return; }
                int diff = count - r.chestBefore();
                int inventoryDiff = KurumiTaskChecks.inventoryCount(s.itemId()) - r.inventoryBefore();
                boolean success = s.action().equals("CHEST_TAKE")
                        ? diff < 0 && inventoryDiff > 0
                        : diff > 0 && inventoryDiff < 0;
                if (!success) {
                    fail(r, "箱子前后数量没有符合预期变化：之前 " + r.chestBefore()
                            + "，现在 " + count + "，背包变化 " + inventoryDiff);
                    return;
                }
                say("箱子核验成功：" + s.itemId() + "，数量 " + r.chestBefore() + " → " + count);
                next(r);
            }
        }
    }
    private KurumiPlanExecutor() {}
}
