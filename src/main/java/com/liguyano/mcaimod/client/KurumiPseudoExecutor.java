package com.liguyano.mcaimod.client;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

/** Tick-driven interpreter for a bounded, declarative pseudocode AST. */
public final class KurumiPseudoExecutor {
    private static final long TIMEOUT = 300_000L;
    private static final int MAX_ITERATIONS = 20;
    private static final int MAX_ACTIONS = 160;
    private static final class Frame {
        final List<KurumiTaskPlanner.Statement> statements;
        final KurumiTaskPlanner.Statement loop;
        int index;
        int iterations;
        Frame(List<KurumiTaskPlanner.Statement> statements, KurumiTaskPlanner.Statement loop) {
            this.statements = statements;
            this.loop = loop;
        }
    }
    private static final Deque<Frame> frames = new ArrayDeque<>();
    private static long started;
    private static boolean running;
    private static boolean awaitingAction;
    private static boolean awaitingChest;
    private static boolean awaitingCome;
    private static int comeX, comeY, comeZ;
    private static long comeStarted;
    private static KurumiTaskPlanner.Condition pendingCondition;
    private static KurumiTaskPlanner.Statement pendingStatement;
    private static int actions;
    private static int publicMessages;
    private static long lastPublicMessageAt;
    private static boolean lastResultSuccess;
    private static boolean assignedResult;

    public static void install() { NeoForge.EVENT_BUS.addListener(KurumiPseudoExecutor::tick); }
    public static boolean busy() { return running; }
    public static void cancel() {
        if (running) KurumiWebMonitor.state("已取消");
        running = false;
        awaitingAction = false;
        awaitingChest = false;
        awaitingCome = false;
        frames.clear();
        KurumiChestInspector.cancel();
        KurumiPlanExecutor.cancel();
    }
    private static void say(String msg) {
        var mc = Minecraft.getInstance();
        if (mc.getConnection() != null) mc.getConnection().sendChat(msg);
    }
    public static String status() {
        return running ? "伪代码执行中：动作 " + actions + "/" + MAX_ACTIONS
                + "，嵌套层级 " + frames.size() : "没有伪代码任务";
    }
    public static BaritoneBridge.Result start(KurumiTaskPlanner.Program program) {
        if (running || KurumiPlanExecutor.busy() || KurumiLongTaskBridge.busy()
                || KurumiChestCraftTask.busy())
            return new BaritoneBridge.Result(false, "已有任务正在执行");
        if (program == null || program.statements().isEmpty())
            return new BaritoneBridge.Result(false, "伪代码为空");
        frames.clear();
        frames.push(new Frame(program.statements(), null));
        started = System.currentTimeMillis();
        actions = 0;
        publicMessages = 0;
        lastPublicMessageAt = 0;
        lastResultSuccess = false;
        assignedResult = false;
        awaitingAction = false;
        awaitingChest = false;
        awaitingCome = false;
        pendingCondition = null;
        pendingStatement = null;
        running = true;
        KurumiWebMonitor.begin(program);
        return new BaritoneBridge.Result(true, "已开始执行受控伪代码任务");
    }
    private static boolean matches(int actual, KurumiTaskPlanner.Condition c) {
        return switch (c.operator()) {
            case "LT" -> actual < c.count();
            case "LE" -> actual <= c.count();
            case "EQ" -> actual == c.count();
            case "GE" -> actual >= c.count();
            case "GT" -> actual > c.count();
            case "NE" -> actual != c.count();
            default -> false;
        };
    }
    private static void completeCondition(boolean result) {
        KurumiTaskPlanner.Statement stmt = pendingStatement;
        Frame frame = frames.peek();
        pendingStatement = null;
        pendingCondition = null;
        awaitingChest = false;
        awaitingCome = false;
        if (frame == null || stmt == null) { cancel(); return; }
        if ("IF".equals(stmt.op())) {
            frame.index++;
            List<KurumiTaskPlanner.Statement> branch = result ? stmt.body() : stmt.otherwise();
            if (!branch.isEmpty()) frames.push(new Frame(branch, null));
        } else if ("WHILE".equals(stmt.op())) {
            if (!result) { frame.index++; return; }
            if (++frame.iterations > MAX_ITERATIONS) {
                say("伪代码循环达到每个 WHILE 最多 " + MAX_ITERATIONS + " 次的限制");
                cancel();
                return;
            }
            frames.push(new Frame(stmt.body(), stmt));
        }
    }
    private static void tick(ClientTickEvent.Post event) {
        if (!running) return;
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.getConnection() == null) { cancel(); return; }
        if (System.currentTimeMillis() - started > TIMEOUT) {
            say("伪代码超过5分钟，已停止");
            cancel();
            return;
        }
        if (awaitingCome) {
            double dx = mc.player.getX() - (comeX + 0.5);
            double dy = mc.player.getY() - comeY;
            double dz = mc.player.getZ() - (comeZ + 0.5);
            if (dx * dx + dy * dy + dz * dz <= 9.0) {
                awaitingCome = false;
                lastResultSuccess = true;
                KurumiWebMonitor.log("come() check_position: 已抵达目标坐标附近");
            } else if (System.currentTimeMillis() - comeStarted > 90000L) {
                awaitingCome = false;
                lastResultSuccess = false;
                BaritoneBridge.stop();
                KurumiWebMonitor.log("come() check_position: 等待90秒超时");
                if (!assignedResult) { say("未抵达目标，已停止后续动作"); cancel(); }
            }
            return;
        }
        if (awaitingAction) {
            if (KurumiPlanExecutor.busy()) return;
            awaitingAction = false;
            lastResultSuccess = KurumiPlanExecutor.lastSucceeded();
            KurumiWebMonitor.log("动作结束，验证结果：" + lastResultSuccess);
            if (!lastResultSuccess && !assignedResult) {
                say("伪代码动作失败，已停止");
                cancel();
            }
            return;
        }
        if (awaitingChest) {
            if (KurumiChestInspector.busy()) return;
            int count = KurumiChestInspector.observedCount(pendingCondition.itemId());
            if (count < 0) {
                say("箱子条件检查失败，已停止");
                cancel();
                return;
            }
            KurumiWebMonitor.log("箱子数量检查：" + count);
            completeCondition(matches(count, pendingCondition));
            return;
        }
        if (frames.isEmpty()) {
            running = false;
            KurumiWebMonitor.state("已完成");
            say("伪代码任务执行完成");
            return;
        }
        Frame frame = frames.peek();
        if (frame.index >= frame.statements.size()) {
            frames.pop();
            return;
        }
        var stmt = frame.statements.get(frame.index);
        KurumiWebMonitor.step(stmt, actions, frame.iterations, lastResultSuccess);
        switch (stmt.op()) {
            case "SLEEP", "SLEEP_RESULT" -> {
                if (++actions > MAX_ACTIONS) { cancel(); return; }
                var result = KurumiSleepBridge.sleep();
                lastResultSuccess = result.success();
                assignedResult = "SLEEP_RESULT".equals(stmt.op());
                KurumiWebMonitor.log("sleep() 请求：" + result.message());
                frame.index++;
                if (!result.success() && !assignedResult) cancel();
            }
            case "FARM", "FARM_RESULT" -> {
                if (++actions > MAX_ACTIONS) { cancel(); return; }
                var result = BaritoneBridge.farm(stmt.step().quantity());
                KurumiWebMonitor.log("已请求启动 farm，命令接受：" + result.success());
                lastResultSuccess = result.success();
                assignedResult = "FARM_RESULT".equals(stmt.op());
                frame.index++;
                if (!result.success() && !assignedResult) {
                    say("无法启动种田：" + result.message());
                    cancel();
                }
                // farm is an ongoing Baritone command; success means accepted, not harvested.
            }
            case "TALK" -> {
                // Send ordinary player chat to the server, not a local notice or command.
                // Rate-limit scripted public messages to avoid flooding multiplayer chat.
                long now = System.currentTimeMillis();
                if (publicMessages >= 12) {
                    say("伪代码 talk 次数超过上限，任务已停止");
                    cancel();
                    return;
                }
                if (now - lastPublicMessageAt < 2000) return;
                String message = stmt.step().target();
                if (message == null || message.isBlank() || message.length() > 120
                        || message.startsWith("/") || message.indexOf('\n') >= 0) {
                    cancel();
                    return;
                }
                mc.getConnection().sendChat(message);
                KurumiWebMonitor.log("talk: " + message);
                publicMessages++;
                lastPublicMessageAt = now;
                frame.index++;
            }
            case "ACTION", "ACTION_RESULT" -> {
                if (java.util.Set.of("STOP", "TPA_REQUEST", "TPA_ACCEPT", "TPA_REQUEST_TO",
                        "CRAFT_OPEN", "CHEST_OPEN", "CHEST_PUT_ALL", "CHEST_TAKE_ALL", "DROP_ALL",
                        "DROP_ITEM_ALL", "DROP", "PICKUP", "PICKUP_ALL", "CRAFT_ITEM",
                        "CHEST_TAKE", "CHEST_PUT", "COLLECT_AND_STORE").contains(stmt.step().action())) {
                    if (++actions > MAX_ACTIONS) { cancel(); return; }
                    var step = stmt.step();
                    BaritoneBridge.Result result = switch (step.action()) {
                        case "STOP" -> BaritoneBridge.stop();
                        case "TPA_REQUEST" -> KurumiTpaBridge.request();
                        case "TPA_ACCEPT" -> KurumiTpaBridge.accept();
                        case "TPA_REQUEST_TO" -> KurumiTpaBridge.requestTo(step.target());
                        case "CRAFT_OPEN" -> KurumiCraftingBridge.open();
                        case "CHEST_OPEN" -> KurumiChestBridge.open();
                        case "CHEST_PUT_ALL" -> KurumiChestBridge.putAll();
                        case "CHEST_TAKE_ALL" -> KurumiChestBridge.takeAll();
                        case "DROP_ALL" -> KurumiDropBridge.dropAll();
                        case "DROP_ITEM_ALL" -> KurumiDropBridge.dropItemAll(step.itemId());
                        case "DROP" -> KurumiDropBridge.drop(step.itemId(), step.quantity());
                        case "PICKUP" -> KurumiPickupBridge.pickup(step.itemId(), step.quantity());
                        case "PICKUP_ALL" -> KurumiPickupBridge.pickupAll(null);
                        case "CRAFT_ITEM" -> KurumiAutoCraftBridge.craft(step.itemId(), step.quantity());
                        case "CHEST_TAKE" -> KurumiChestBridge.takeItem(step.itemId(), step.quantity());
                        case "CHEST_PUT" -> KurumiChestBridge.put(step.itemId(), step.quantity());
                        case "COLLECT_AND_STORE" -> KurumiLongTaskBridge.collectAndStore(step.itemId(), step.quantity());
                        default -> new BaritoneBridge.Result(false, "不支持的指令");
                    };
                    lastResultSuccess = result.success();
                    assignedResult = "ACTION_RESULT".equals(stmt.op());
                    KurumiWebMonitor.log(step.action() + ": " + result.message());
                    frame.index++;
                    if (!result.success() && !assignedResult) cancel();
                    return;
                }
                if ("CHAIN_ON".equals(stmt.step().action()) || "CHAIN_OFF".equals(stmt.step().action())) {
                    if (++actions > MAX_ACTIONS) { cancel(); return; }
                    var result = KurumiChainBridge.setEnabled("CHAIN_ON".equals(stmt.step().action()));
                    lastResultSuccess = result.success();
                    assignedResult = "ACTION_RESULT".equals(stmt.op());
                    KurumiWebMonitor.log(stmt.step().action() + ": " + result.message());
                    frame.index++;
                    if (!result.success() && !assignedResult) cancel();
                    return;
                }
                if ("COME".equals(stmt.step().action()) || "FOLLOW".equals(stmt.step().action())) {
                    if (++actions > MAX_ACTIONS) { cancel(); return; }
                    var result = "COME".equals(stmt.step().action())
                            ? BaritoneBridge.comeToPlayer(stmt.step().target())
                            : BaritoneBridge.followPlayer(stmt.step().target());
                    lastResultSuccess = result.success();
                    assignedResult = "ACTION_RESULT".equals(stmt.op());
                    KurumiWebMonitor.log(stmt.step().action() + " 命令：" + result.message());
                    frame.index++;
                    if (!result.success() && !assignedResult) { cancel(); return; }
                    if (result.success() && "COME".equals(stmt.step().action())) {
                        var target = mc.level.players().stream()
                                .filter(p -> stmt.step().target().equalsIgnoreCase(p.getName().getString()))
                                .findFirst();
                        if (target.isEmpty()) {
                            lastResultSuccess = false;
                            if (!assignedResult) cancel();
                            return;
                        }
                        comeX = target.get().getBlockX();
                        comeY = target.get().getBlockY();
                        comeZ = target.get().getBlockZ();
                        comeStarted = System.currentTimeMillis();
                        awaitingCome = true;
                        KurumiWebMonitor.log("come() check_position: 等待到达 " + comeX + "," + comeY + "," + comeZ);
                    }
                    return;
                }
                if (++actions > MAX_ACTIONS) {
                    say("伪代码动作数量达到安全上限");
                    cancel();
                    return;
                }
                var plan = new KurumiTaskPlanner.Plan(List.of(stmt.step()), 1, 1, 1, List.of());
                var result = KurumiPlanExecutor.start(plan);
                if (!result.success()) {
                    say("伪代码动作启动失败：" + result.message());
                    cancel();
                    return;
                }
                KurumiWebMonitor.log("启动动作：" + stmt.step().action());
                frame.index++;
                assignedResult = "ACTION_RESULT".equals(stmt.op());
                awaitingAction = true;
            }
            case "IF", "WHILE" -> {
                pendingStatement = stmt;
                pendingCondition = stmt.condition();
                if ("result".equals(pendingCondition.source())) {
                    completeCondition(matches(lastResultSuccess ? 1 : 0, pendingCondition));
                } else if ("inventory".equals(pendingCondition.source())) {
                    completeCondition(matches(KurumiTaskChecks.inventoryCount(pendingCondition.itemId()),
                            pendingCondition));
                } else {
                    String result = KurumiChestInspector.check(pendingCondition.itemId());
                    if (result.contains("error")) {
                        say("箱子条件检查无法启动：" + result);
                        cancel();
                    } else awaitingChest = true;
                }
            }
            case "RETURN" -> {
                running = false;
                frames.clear();
                KurumiWebMonitor.state("RETURN 结束");
                say("伪代码执行 RETURN，任务结束");
            }
            case "BREAK" -> {
                Frame target = null;
                for (Frame f : frames) {
                    if (f.loop != null) { target = f; break; }
                }
                if (target == null) {
                    say("BREAK 只能在 WHILE 内使用");
                    cancel();
                    return;
                }
                while (!frames.isEmpty()) {
                    Frame popped = frames.pop();
                    if (popped == target) break;
                }
                if (!frames.isEmpty()) frames.peek().index++;
            }
            default -> cancel();
        }
    }
    private KurumiPseudoExecutor() {}
}
