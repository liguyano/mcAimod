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
    private static boolean awaitingBlockBreak;
    private static boolean awaitingChestSearch;
    private static boolean awaitingChest;
    private static boolean awaitingCome;
    private static boolean awaitingExternal;
    private static boolean awaitingCraft;
    private static boolean awaitingMenu;
    private static long menuStarted;
    private static long externalStarted;
    private static String comeTarget;
    private static boolean comeFollowing;
    private static long comeStarted;
    private static long sleepUntil;
    private static KurumiTaskPlanner.Condition pendingCondition;
    private static KurumiTaskPlanner.Statement pendingStatement;
    private static int actions;
    private static int publicMessages;
    private static long lastPublicMessageAt;
    private static boolean lastResultSuccess;
    private static boolean assignedResult;
    private static final java.util.Map<String, com.google.gson.JsonElement> variables = new java.util.HashMap<>();

    public static void install() { NeoForge.EVENT_BUS.addListener(KurumiPseudoExecutor::tick); }
    public static boolean busy() { return running; }
    public static void cancel() {
        if (comeFollowing) BaritoneBridge.stop();
        comeFollowing = false;
        comeTarget = null;
        if (running) KurumiWebMonitor.state("已取消");
        running = false;
        awaitingAction = false;
        awaitingBlockBreak = false;
        awaitingChest = false;
        awaitingCome = false;
        sleepUntil = 0;
        awaitingExternal = false;
        awaitingCraft = false;
        awaitingMenu = false;
        frames.clear();
        KurumiBlockBreakingBridge.cancel();
        KurumiChestSearch.cancel();
        awaitingChestSearch = false;
        awaitingBlockBreak = false;
        variables.clear();
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
        variables.clear();
        awaitingAction = false;
        awaitingChest = false;
        awaitingCome = false;
        awaitingExternal = false;
        awaitingCraft = false;
        awaitingMenu = false;
        awaitingChestSearch = false;
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
        if ("IF".equals(stmt.op()) || "EXPR_IF".equals(stmt.op())) {
            frame.index++;
            List<KurumiTaskPlanner.Statement> branch = result ? stmt.body() : stmt.otherwise();
            if (!branch.isEmpty()) frames.push(new Frame(branch, null));
        } else if ("WHILE".equals(stmt.op()) || "EXPR_WHILE".equals(stmt.op())) {
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
        if (sleepUntil > 0) {
            if (System.currentTimeMillis() < sleepUntil) return;
            sleepUntil = 0;
            KurumiWebMonitor.log("sleep(): 等待结束，继续执行");
        }
        if (awaitingMenu) {
            if (mc.player.containerMenu != mc.player.inventoryMenu) {
                awaitingMenu = false;
                KurumiWebMonitor.log("容器界面已打开，可以继续放入物品");
            } else if (System.currentTimeMillis() - menuStarted > 5000L) {
                awaitingMenu = false;
                KurumiWebMonitor.log("等待容器界面超时");
                lastResultSuccess = false;
                if (!assignedResult) cancel();
            }
            return;
        }
        if (awaitingExternal) {
            if (KurumiLongTaskBridge.busy() || KurumiAutoCraftBridge.busy()
                    || KurumiChestBridge.busy() || KurumiPickupBridge.busy()) {
                if (System.currentTimeMillis() - externalStarted > (KurumiLongTaskBridge.busy() ? 300000L : 90000L)) {
                    KurumiWebMonitor.log("异步操作等待超时，停止脚本");
                    cancel();
                }
                return;
            }
            awaitingExternal = false;
            if (awaitingCraft) {
                awaitingCraft = false;
                lastResultSuccess = KurumiAutoCraftBridge.lastSucceeded();
                KurumiWebMonitor.log("合成完成验证：" + lastResultSuccess);
                if (!lastResultSuccess && !assignedResult) {
                    say("合成失败，停止后续取物步骤");
                    cancel();
                }
            } else {
                KurumiWebMonitor.log("异步操作已结束；结果可能仍需服务器确认");
            }
            return;
        }
        if (awaitingCome) {
            var target = mc.level == null ? java.util.Optional.<net.minecraft.world.entity.player.Player>empty()
                    : mc.level.players().stream()
                    .filter(p -> comeTarget != null && comeTarget.equalsIgnoreCase(p.getName().getString()))
                    .findFirst();
            if (target.isPresent() && mc.player.distanceToSqr(target.get()) <= 9.0) {
                BaritoneBridge.stop();
                comeFollowing = false;
                awaitingCome = false;
                lastResultSuccess = true;
                KurumiWebMonitor.log("come(): 已靠近 " + comeTarget + "，自动取消 follow");
                say("主人~ 我已经过来啦！");
                KurumiWebMonitor.log("talk: 主人~ 我已经过来啦！");
            } else if (System.currentTimeMillis() - comeStarted > 90000L) {
                BaritoneBridge.stop();
                comeFollowing = false;
                awaitingCome = false;
                lastResultSuccess = false;
                KurumiWebMonitor.log("come(): 跟随90秒未能抵达，已停止");
                if (!assignedResult) { say("未能靠近目标玩家，已停止后续动作"); cancel(); }
            }
            return;
        }
        if (awaitingChestSearch) {
            if (KurumiChestSearch.busy()) return;
            awaitingChestSearch = false;
            lastResultSuccess = KurumiChestSearch.lastSucceeded();
            KurumiWebMonitor.log("箱子任务结果：" + KurumiChestSearch.status());
            if (!lastResultSuccess) {
                say("箱子搜索或按记忆取物失败，请查看 Web 日志");
                cancel();
            }
            return;
        }
        if (awaitingBlockBreak) {
            if (KurumiBlockBreakingBridge.busy()) return;
            awaitingBlockBreak = false;
            lastResultSuccess = KurumiBlockBreakingBridge.lastSucceeded();
            KurumiWebMonitor.log("破坏方块验证：" + lastResultSuccess);
            if (!lastResultSuccess) {
                say("破坏方块失败，已停止");
                cancel();
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
            case "EXPR_ASSIGN" -> {
                try {
                    String name = stmt.step().target();
                    if (variables.size() >= 16 && !variables.containsKey(name))
                        throw new IllegalArgumentException("最多保存16个变量");
                    var value = KurumiExpressions.evaluate(stmt.step().itemId(), variables);
                    if (value.toString().length() > 8192)
                        throw new IllegalArgumentException("变量内容超过8192字符");
                    variables.put(name, value.deepCopy());
                    KurumiWebMonitor.log("赋值 " + name + " = " + value);
                    frame.index++;
                } catch (RuntimeException ex) {
                    KurumiWebMonitor.log("变量赋值失败：" + ex.getMessage());
                    say("伪代码变量错误，任务停止");
                    cancel();
                }
            }
            case "EXPR_IF", "EXPR_WHILE" -> {
                try {
                    boolean yes = KurumiExpressions.truth(
                            KurumiExpressions.evaluate(stmt.step().itemId(), variables));
                    pendingStatement = stmt;
                    completeCondition(yes);
                } catch (RuntimeException ex) {
                    KurumiWebMonitor.log("条件表达式失败：" + ex.getMessage());
                    cancel();
                }
            }
            case "EXPR_CALL" -> {
                try {
                    String name = stmt.step().action();
                    var args = KurumiExpressions.arguments(stmt.step().itemId(), variables);
                    if ("talk".equals(name)) {
                        if (args.size()!=1) throw new IllegalArgumentException("talk 需要一个参数");
                        long now = System.currentTimeMillis();
                        if (publicMessages >= 12) throw new IllegalArgumentException("talk 次数超限");
                        if (now - lastPublicMessageAt < 2000) return;
                        String msg = KurumiExpressions.text(args.get(0));
                        if (msg.isBlank() || msg.startsWith("/") || msg.contains("\n"))
                            throw new IllegalArgumentException("消息内容不合法");
                        if (msg.length() > 120) msg=msg.substring(0,108)+"...(已截断)";
                        mc.getConnection().sendChat(msg);
                        KurumiWebMonitor.log("talk(表达式): " + msg);
                        publicMessages++;
                        lastPublicMessageAt = now;
                        frame.index++;
                        return;
                    }
                    if ("sleep".equals(name)) {
                        if (args.size()!=1) throw new IllegalArgumentException("sleep 需要一个参数");
                        int secs = KurumiExpressions.integer(args.get(0));
                        if (secs < 1 || secs > 120) throw new IllegalArgumentException("sleep 仅支持1到120秒");
                        sleepUntil = System.currentTimeMillis() + secs * 1000L;
                        frame.index++;
                        return;
                    }
                    if (++actions > MAX_ACTIONS) throw new IllegalArgumentException("动作次数达到上限");
                    BaritoneBridge.Result outcome;
                    if ("goto".equals(name)) {
                        if (args.size()!=3) throw new IllegalArgumentException("goto 需要3个坐标");
                        int x=KurumiExpressions.integer(args.get(0));
                        int y=KurumiExpressions.integer(args.get(1));
                        int z=KurumiExpressions.integer(args.get(2));
                        if (y < -64 || y > 320 || Math.abs((long)x)>30000000 || Math.abs((long)z)>30000000)
                            throw new IllegalArgumentException("坐标超出世界范围");
                        var destination = new net.minecraft.core.BlockPos(x,y,z);
                        // If the coordinate is an occupied block, walk beside it instead of into it.
                        if (mc.level != null && mc.level.hasChunkAt(destination)
                                && !mc.level.getBlockState(destination)
                                        .getCollisionShape(mc.level,destination).isEmpty()) {
                            net.minecraft.core.BlockPos nearest=null;
                            double best=Double.POSITIVE_INFINITY;
                            for (var direction : net.minecraft.core.Direction.Plane.HORIZONTAL) {
                                var pos=destination.relative(direction);
                                if (!mc.level.hasChunkAt(pos) || !mc.level.hasChunkAt(pos.above())
                                        || !mc.level.hasChunkAt(pos.below())) continue;
                                if (!mc.level.getBlockState(pos).getCollisionShape(mc.level,pos).isEmpty()
                                        || !mc.level.getBlockState(pos.above()).getCollisionShape(mc.level,pos.above()).isEmpty()
                                        || mc.level.getBlockState(pos.below()).getCollisionShape(mc.level,pos.below()).isEmpty())
                                    continue;
                                double distance=pos.distSqr(mc.player.blockPosition());
                                if (distance<best) {best=distance;nearest=pos;}
                            }
                            if (nearest==null) throw new IllegalArgumentException("目标周围没有可站立位置");
                            destination=nearest;
                        }
                        var step=new KurumiTaskPlanner.Step("GOTO","",1,destination.getX(),
                                destination.getY(),destination.getZ(),"");
                        var plan=new KurumiTaskPlanner.Plan(List.of(step),1,1,1,List.of());
                        outcome=KurumiPlanExecutor.start(plan);
                        if (outcome.success()) awaitingAction=true;
                    } else if ("move".equals(name)) {
                        if (args.size()!=2) throw new IllegalArgumentException("move 需要两个参数");
                        String direction=KurumiExpressions.text(args.get(0));
                        int blocks=KurumiExpressions.integer(args.get(1));
                        if (!java.util.Set.of("forward","back","left","right").contains(direction)
                                || blocks < 1 || blocks > 16) throw new IllegalArgumentException("move 参数无效");
                        outcome=BaritoneBridge.moveRelative(direction,blocks);
                    } else if ("interact".equals(name) || "equip_main_hand".equals(name)) {
                        if (args.size()!=1) throw new IllegalArgumentException("指令需要一个物品ID");
                        String id=KurumiExpressions.text(args.get(0));
                        if (!id.matches("[a-z0-9_.-]+:[a-z0-9_./-]{1,128}"))
                            throw new IllegalArgumentException("方块或物品ID无效");
                        outcome="interact".equals(name) ? KurumiInteractionBridge.interact(id)
                                : KurumiEquipBridge.mainHand(id);
                    } else if ("search_chests".equals(name)) {
                        if (args.size()!=1) throw new IllegalArgumentException("search_chests 需要一个物品ID");
                        outcome=KurumiChestSearch.search(KurumiExpressions.text(args.get(0)));
                        if(outcome.success()) awaitingChestSearch=true;
                    } else if ("take_from_memory".equals(name)) {
                        if(args.size()!=3) throw new IllegalArgumentException("take_from_memory 需要记忆名称、物品ID、数量");
                        outcome=KurumiChestSearch.take(KurumiExpressions.text(args.get(0)),
                                KurumiExpressions.text(args.get(1)),KurumiExpressions.integer(args.get(2)));
                        if(outcome.success()) awaitingChestSearch=true;
                    } else if ("break_block".equals(name)) {
                        if (args.size()!=1 && args.size()!=3 && args.size()!=4)
                            throw new IllegalArgumentException("break_block 支持方块ID、坐标或ID加坐标");
                        String id = (args.size()==1 || args.size()==4)
                                ? KurumiExpressions.text(args.get(0)) : null;
                        if (id != null && !id.matches("[a-z0-9_.-]+:[a-z0-9_./-]{1,128}"))
                            throw new IllegalArgumentException("方块ID无效");
                        int offset = args.size()==4 ? 1 : 0;
                        Integer bx = args.size()==1 ? null : KurumiExpressions.integer(args.get(offset));
                        Integer by = args.size()==1 ? null : KurumiExpressions.integer(args.get(offset+1));
                        Integer bz = args.size()==1 ? null : KurumiExpressions.integer(args.get(offset+2));
                        outcome=KurumiBlockBreakingBridge.start(id,bx,by,bz);
                        if (outcome.success()) awaitingBlockBreak=true;
                    } else if ("place_block".equals(name)) {
                        if (args.size()!=1 && args.size()!=4)
                            throw new IllegalArgumentException("place_block 需要1或4个参数");
                        String id=KurumiExpressions.text(args.get(0));
                        if (!id.matches("[a-z0-9_.-]+:[a-z0-9_./-]{1,128}"))
                            throw new IllegalArgumentException("物品ID无效");
                        outcome=KurumiBlockPlacementBridge.place(id,
                                args.size()==4 ? KurumiExpressions.integer(args.get(1)) : null,
                                args.size()==4 ? KurumiExpressions.integer(args.get(2)) : null,
                                args.size()==4 ? KurumiExpressions.integer(args.get(3)) : null);
                    } else throw new IllegalArgumentException("函数不在动作白名单");
                    KurumiWebMonitor.log(name + "(变量): " + outcome.message());
                    lastResultSuccess=outcome.success();
                    if (!outcome.success()) { cancel(); return; }
                    frame.index++;
                } catch (RuntimeException ex) {
                    KurumiWebMonitor.log("变量参数执行失败：" + ex.getMessage());
                    say("变量或动作参数错误，任务已停止");
                    cancel();
                }
            }
            case "SLEEP" -> {
                sleepUntil = System.currentTimeMillis() + stmt.step().quantity() * 1000L;
                frame.index++;
                KurumiWebMonitor.log("sleep(): 等待 " + stmt.step().quantity() + " 秒");
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
            case "OBSERVE_TALK" -> {
                long now = System.currentTimeMillis();
                if (publicMessages >= 12) {
                    say("查询消息次数达到上限，任务已停止");
                    cancel();
                    return;
                }
                if (now - lastPublicMessageAt < 2000) return;
                String observation = KurumiToolbox.observe(stmt.step().target());
                String message = observation;
                if (message.length() > 120)
                    message = message.substring(0, 108) + "...(已截断)";
                if (message.isBlank() || message.startsWith("/")) {
                    cancel();
                    return;
                }
                mc.getConnection().sendChat(message);
                KurumiWebMonitor.log("talk(observe): " + observation);
                publicMessages++;
                lastPublicMessageAt = now;
                frame.index++;
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
                    if (!result.success() && !assignedResult) { cancel(); return; }
                    if (result.success() && "CRAFT_ITEM".equals(step.action())) awaitingCraft = true;
                    if (result.success() && "CRAFT_OPEN".equals(step.action())) {
                        awaitingMenu = true;
                        menuStarted = System.currentTimeMillis();
                    }
                    if (result.success() && (KurumiLongTaskBridge.busy() || KurumiAutoCraftBridge.busy()
                            || KurumiChestBridge.busy() || KurumiPickupBridge.busy())) {
                        awaitingExternal = true;
                        externalStarted = System.currentTimeMillis();
                    }
                    return;
                }
                if ("PLACE_BLOCK".equals(stmt.step().action()) || "INTERACT".equals(stmt.step().action()) || "PUT_ITEM".equals(stmt.step().action()) || "TAKE_ITEM".equals(stmt.step().action())) {
                    if (++actions > MAX_ACTIONS) { cancel(); return; }
                    var step = stmt.step();
                    var result = "PLACE_BLOCK".equals(step.action())
                            ? KurumiBlockPlacementBridge.place(step.itemId(), step.x(), step.y(), step.z())
                            : "INTERACT".equals(step.action())
                            ? KurumiInteractionBridge.interact(step.itemId())
                            : "TAKE_ITEM".equals(step.action())
                            ? KurumiInteractionBridge.takeItem(step.itemId(), step.quantity())
                            : KurumiInteractionBridge.putItem(step.itemId(), step.quantity(), step.target());
                    lastResultSuccess = result.success();
                    assignedResult = "ACTION_RESULT".equals(stmt.op());
                    KurumiWebMonitor.log(step.action() + ": " + result.message());
                    frame.index++;
                    if (!result.success() && !assignedResult) { cancel(); return; }
                    if (result.success() && "INTERACT".equals(step.action())
                            && (step.itemId().endsWith("_chest") || step.itemId().endsWith("_shulker_box")
                                    || step.itemId().equals("minecraft:chest")
                                    || step.itemId().equals("minecraft:trapped_chest")
                                    || step.itemId().equals("minecraft:crafting_table")
                                    || step.itemId().endsWith("_furnace")
                                    || step.itemId().equals("minecraft:smoker")
                                    || step.itemId().equals("minecraft:blast_furnace")
                                    || step.itemId().equals("minecraft:barrel")
                                    || step.itemId().equals("minecraft:hopper"))) {
                        awaitingMenu = true;
                        menuStarted = System.currentTimeMillis();
                    }
                    return;
                }
                if ("MOVE".equals(stmt.step().action())) {
                    if (++actions > MAX_ACTIONS) { cancel(); return; }
                    var step = stmt.step();
                    var result = BaritoneBridge.moveRelative(step.target(), step.quantity());
                    lastResultSuccess = result.success();
                    assignedResult = "ACTION_RESULT".equals(stmt.op());
                    KurumiWebMonitor.log("MOVE: " + result.message());
                    frame.index++;
                    if (!result.success() && !assignedResult) cancel();
                    return;
                }
                if ("GOTO_NAMED".equals(stmt.step().action())) {
                    if (++actions > MAX_ACTIONS) { cancel(); return; }
                    var result = KurumiLocationMemory.gotoNamed(stmt.step().target());
                    lastResultSuccess = result.success();
                    assignedResult = "ACTION_RESULT".equals(stmt.op());
                    KurumiWebMonitor.log("goto(地点)：" + result.message());
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
                    boolean oneShotCome = "COME".equals(stmt.step().action());
                    var result = BaritoneBridge.followPlayer(stmt.step().target());
                    lastResultSuccess = result.success();
                    assignedResult = "ACTION_RESULT".equals(stmt.op());
                    KurumiWebMonitor.log(stmt.step().action() + " 命令：" + result.message());
                    frame.index++;
                    if (!result.success() && !assignedResult) { cancel(); return; }
                    if (result.success() && oneShotCome) {
                        comeTarget = stmt.step().target();
                        comeStarted = System.currentTimeMillis();
                        comeFollowing = true;
                        awaitingCome = true;
                        KurumiWebMonitor.log("come(): follow " + comeTarget + "，靠近3格后自动停止");
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
                } else if ("distance".equals(pendingCondition.source())) {
                    try {
                        String observation = KurumiToolbox.observe(
                                "get_distance:" + pendingCondition.itemId());
                        var data = com.google.gson.JsonParser.parseString(observation).getAsJsonObject();
                        if (!data.has("found") || !data.get("found").getAsBoolean()
                                || !data.has("distance")) {
                            KurumiWebMonitor.log("距离条件检查失败或目标不在搜索范围：" + observation);
                            say("未找到目标，无法判断距离条件，脚本已停止");
                            cancel();
                            return;
                        }
                        double distance = data.get("distance").getAsDouble();
                        boolean satisfied = switch (pendingCondition.operator()) {
                            case "LT" -> distance < pendingCondition.count();
                            case "LE" -> distance <= pendingCondition.count();
                            case "EQ" -> distance == pendingCondition.count();
                            case "GE" -> distance >= pendingCondition.count();
                            case "GT" -> distance > pendingCondition.count();
                            case "NE" -> distance != pendingCondition.count();
                            default -> false;
                        };
                        KurumiWebMonitor.log("距离检查：" + pendingCondition.itemId()
                                + " = " + distance + " 格");
                        completeCondition(satisfied);
                    } catch (RuntimeException ex) {
                        KurumiWebMonitor.log("距离检查出错：" + ex.getMessage());
                        cancel();
                    }
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
