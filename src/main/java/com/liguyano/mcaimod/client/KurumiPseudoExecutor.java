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
    private static KurumiTaskPlanner.Condition pendingCondition;
    private static KurumiTaskPlanner.Statement pendingStatement;
    private static int actions;
    private static boolean lastResultSuccess;
    private static boolean assignedResult;

    public static void install() { NeoForge.EVENT_BUS.addListener(KurumiPseudoExecutor::tick); }
    public static boolean busy() { return running; }
    public static void cancel() {
        running = false;
        awaitingAction = false;
        awaitingChest = false;
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
        lastResultSuccess = false;
        assignedResult = false;
        awaitingAction = false;
        awaitingChest = false;
        pendingCondition = null;
        pendingStatement = null;
        running = true;
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
        if (awaitingAction) {
            if (KurumiPlanExecutor.busy()) return;
            awaitingAction = false;
            lastResultSuccess = KurumiPlanExecutor.lastSucceeded();
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
            completeCondition(matches(count, pendingCondition));
            return;
        }
        if (frames.isEmpty()) {
            running = false;
            say("伪代码任务执行完成");
            return;
        }
        Frame frame = frames.peek();
        if (frame.index >= frame.statements.size()) {
            frames.pop();
            return;
        }
        var stmt = frame.statements.get(frame.index);
        switch (stmt.op()) {
            case "ACTION", "ACTION_RESULT" -> {
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
