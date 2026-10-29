package com.liguyano.mcaimod.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.Gson;
import net.minecraft.client.Minecraft;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.proxy.ProxyExecutable;
import org.graalvm.polyglot.proxy.ProxyObject;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

/**
 * Experimental embedded Python executor. Never evaluate guest Python on the
 * Minecraft render thread. Java owns all Minecraft and Baritone operations.
 *
 * NOT enabled as an AI-script entrypoint until authorization, task lifetime,
 * observation forwarding and dependency packaging are validated.
 */
public final class KurumiEmbeddedPythonBridge {
    private static final AtomicBoolean RUNNING = new AtomicBoolean();
    private static volatile Thread worker;
    private static volatile Context activeContext;
    private static volatile long runSequence;
    private static volatile long finishedSequence;
    private static volatile boolean finishedSuccess;
    private static volatile boolean finishedObserved;
    private static volatile String finishedReport = "";
    private static volatile String pendingObservation;
    private static volatile boolean cancelled;
    private static volatile long finishedVisionId;

    public static long activeRunId() { return runSequence; }
    public static long finishedRunId() { return finishedSequence; }
    public static boolean finishedRunSucceeded() { return finishedSuccess; }
    public static boolean finishedRunObserved() { return finishedObserved; }
    public static String finishedRunReport() { return finishedReport; }
    public static long finishedRunVisionId() { return finishedVisionId; }

    private KurumiEmbeddedPythonBridge() {}

    public static boolean busy() { return RUNNING.get(); }

    public static void cancel() {
        cancelled = true;
        Thread task = worker;
        if (task != null) task.interrupt();
        // Context.close(true) is only safe off-thread after the guest execution
        // is terminated. Avoid closing a context on the Minecraft tick thread.
    }

    public static BaritoneBridge.Result start(String script) {
        if (script == null || script.isBlank() || script.length() > 16_000)
            return new BaritoneBridge.Result(false, "Python 脚本为空或超出16KB限制");
        if (!RUNNING.compareAndSet(false, true))
            return new BaritoneBridge.Result(false, "已有 Python 脚本正在运行");
        long run = ++runSequence;
        finishedSuccess = false;
        finishedObserved = false;
        finishedReport = "";
        pendingObservation = null;
        cancelled = false;
        finishedVisionId = 0;
        worker = new Thread(() -> execute(script, run), "Kurumi-GraalPy-Worker");
        worker.setDaemon(true);
        worker.start();
        return new BaritoneBridge.Result(true, "嵌入式 Python 脚本已启动（实验模式）");
    }

    private static Object onGameThread(Supplier<Object> operation) {
        Minecraft mc = Minecraft.getInstance();
        CompletableFuture<Object> done = new CompletableFuture<>();
        mc.execute(() -> {
            if (cancelled || Thread.currentThread().isInterrupted()) {
                done.completeExceptionally(new IllegalStateException("Python 任务已取消"));
                return;
            }
            try { done.complete(operation.get()); }
            catch (Throwable error) { done.completeExceptionally(error); }
        });
        try {
            return done.get(15, TimeUnit.SECONDS);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Python 任务已取消", error);
        } catch (ExecutionException | TimeoutException error) {
            throw new IllegalStateException("游戏线程工具执行失败或超时", error);
        }
    }

    private static String result(BaritoneBridge.Result r) {
        JsonObject result = new JsonObject();
        result.addProperty("success", r.success());
        result.addProperty("completed", false);
        result.addProperty("message", r.message());
        return result.toString();
    }

    // Every value crossing into Python is a JSON encoded scalar, dict or list.
    // Python's json.loads converts them to native types; no Java proxies leak.
    private static String asJson(Object value) {
        if (value == null) return "null";
        if (value instanceof com.google.gson.JsonElement json) return json.toString();
        if (value instanceof String str) {
            try {
                var parsed = JsonParser.parseString(str);
                if (parsed.isJsonArray() || parsed.isJsonObject()) return parsed.toString();
            } catch (RuntimeException ignored) { }
            return new Gson().toJson(str);
        }
        return new Gson().toJson(value);
    }

    private static void require(Object[] args, int count) {
        if (args.length != count) throw new IllegalArgumentException("参数数量应为 " + count);
    }
    private static String str(Object[] args, int i) { return (String) args[i]; }
    private static int num(Object[] args, int i) { return ((Number) args[i]).intValue(); }
    private static final java.util.concurrent.atomic.AtomicInteger TOOL_CALLS = new java.util.concurrent.atomic.AtomicInteger();
    private static volatile long scriptStartedNanos;

    private static Object callGameTool(String name, Object[] a) {
        if (!RUNNING.get() || cancelled || worker == null || worker.isInterrupted())
            throw new IllegalStateException("Python 脚本已取消或不在有效执行上下文");
        if (System.nanoTime() - scriptStartedNanos > TimeUnit.MINUTES.toNanos(5))
            throw new IllegalStateException("Python 脚本超过5分钟");
        int count = TOOL_CALLS.incrementAndGet();
        if (count > 200) throw new IllegalStateException("Python 工具调用超过200次");
        if (java.util.Set.of("check_inventory","check_distance","get_remembered_block",
                "get_remembered_block_at","remember_block","check_nearby_chest",
                "get_nearby_chest_result","screenshot","get_agent_observation",
                "list_remembered_blocks","get_chest_search_result","get_fishing_status",
                "check_inventory_item","check_inventory_count","get_crafting_recipe",
                "find_nearest_block","search_all_block","get_tool_help","get_skill",
                "get_distance","inspect_slot").contains(name)) {
            StringBuilder expression = new StringBuilder(name).append('(');
            for (int i = 0; i < a.length; i++) {
                if (i != 0) expression.append(',');
                if (a[i] instanceof String) expression.append(new Gson().toJson(a[i]));
                else if (a[i] instanceof Number) expression.append(a[i].toString());
                else throw new IllegalArgumentException("工具仅支持字符串或数字参数");
            }
            expression.append(')');
            return KurumiExpressions.evaluate(expression.toString(), Map.of());
        }
        BaritoneBridge.Result action;
        switch (name) {
            case "look" -> { require(a, 2); action = KurumiLookBridge.look(((Number)a[0]).doubleValue(), ((Number)a[1]).doubleValue()); }
            case "look_at" -> {
                if (a.length == 1) action = KurumiLookBridge.lookAtId(str(a,0));
                else if (a.length == 3) action = KurumiLookBridge.lookAt(((Number)a[0]).doubleValue(),((Number)a[1]).doubleValue(),((Number)a[2]).doubleValue());
                else throw new IllegalArgumentException("look_at(id) 或 look_at(x,y,z)");
            }
            case "attack" -> {
                require(a, 1);
                String target = str(a,0);
                action = target.contains(":") ? KurumiCombatBridge.attackMob(target)
                        : KurumiCombatBridge.attackPlayer(target);
            }
            case "use_item" -> { require(a,1); action = KurumiItemUseBridge.use(str(a,0)); }
            case "hold_use_item" -> { require(a,2); action = KurumiItemUseBridge.hold(str(a,0),num(a,1)); }
            case "sleep_in_bed" -> { require(a,0); action = KurumiBedShortcut.start(); }
            case "sleep" -> throw new IllegalStateException("sleep 由 Python 工作线程处理");
            case "goto" -> { require(a, 3); action = BaritoneBridge.gotoCoordinates(num(a,0),num(a,1),num(a,2)); }
            case "move" -> { require(a, 2); action = BaritoneBridge.moveRelative(str(a,0), num(a,1)); }
            case "come" -> { require(a, 1); action = BaritoneBridge.comeToPlayer(str(a,0)); }
            case "follow" -> { require(a, 1); action = BaritoneBridge.followPlayer(str(a,0)); }
            case "stop" -> { require(a, 0); action = BaritoneBridge.stop(); }
            case "interact" -> {
                if (a.length < 1 || a.length > 2) throw new IllegalArgumentException("interact(id[,uuid])");
                action = KurumiInteractionBridge.interact(str(a,0), a.length == 2 ? str(a,1) : null);
            }
            case "put_item" -> {
                if (a.length < 2 || a.length > 3) throw new IllegalArgumentException("put_item(id,count[,slot])");
                action = KurumiInteractionBridge.putItem(str(a,0),num(a,1),a.length == 3 ? str(a,2) : "auto");
            }
            case "take_item" -> { require(a, 2); action = KurumiInteractionBridge.takeItem(str(a,0),num(a,1)); }
            case "take_all_items" -> { require(a, 0); action = KurumiTakeAllBridge.start(); }
            case "search_chests" -> { require(a,1); action = KurumiChestSearch.search(str(a,0)); }
            case "take_from_memory" -> { require(a,3); action = KurumiChestSearch.take(str(a,0),str(a,1),num(a,2)); }
            case "chest_target" -> {
                if (a.length == 0) action = KurumiChestBridge.clearTarget();
                else { require(a, 3); action = KurumiChestBridge.target(num(a,0),num(a,1),num(a,2)); }
            }
            case "chest_open" -> { require(a, 0); action = KurumiChestBridge.open(); }
            case "chest_put" -> { require(a, 2); action = KurumiChestBridge.put(str(a,0),num(a,1)); }
            case "chest_take" -> { require(a, 2); action = KurumiChestBridge.takeItem(str(a,0),num(a,1)); }
            case "chest_put_all" -> { require(a, 0); action = KurumiChestBridge.putAll(); }
            case "chest_take_all" -> { require(a, 0); action = KurumiChestBridge.takeAll(); }
            case "chest_put_item_all" -> { require(a, 1); action = KurumiChestBridge.putItemAll(str(a,0)); }
            case "close_container" -> {
                require(a, 0);
                var mc = Minecraft.getInstance();
                if (mc.player == null) throw new IllegalStateException("未进入世界");
                if (mc.player.containerMenu != mc.player.inventoryMenu) mc.player.closeContainer();
                action = new BaritoneBridge.Result(true,"已请求关闭容器");
            }
            case "craft_open" -> { require(a, 0); action = KurumiCraftingBridge.open(); }
            case "equip_main_hand" -> { require(a, 1); action = KurumiEquipBridge.mainHand(str(a,0)); }
            case "mine" -> { require(a, 1); action = BaritoneBridge.mineBlock(str(a,0)); }
            case "break_block" -> {
                if (a.length == 1) action = KurumiBlockBreakingBridge.start(str(a,0),null,null,null);
                else if (a.length == 3) action = KurumiBlockBreakingBridge.start(null,num(a,0),num(a,1),num(a,2));
                else if (a.length == 4) action = KurumiBlockBreakingBridge.start(str(a,0),num(a,1),num(a,2),num(a,3));
                else throw new IllegalArgumentException("break_block(id[,x,y,z]) 或 break_block(x,y,z)");
            }
            case "place_block" -> {
                if (a.length == 1) action = KurumiBlockPlacementBridge.place(str(a,0),null,null,null);
                else if (a.length == 4) action = KurumiBlockPlacementBridge.place(str(a,0),num(a,1),num(a,2),num(a,3));
                else throw new IllegalArgumentException("place_block(id[,x,y,z])");
            }
            case "pickup_all" -> { require(a, 0); action = KurumiPickupBridge.pickupAll(null); }
            case "pickup_item_all" -> { require(a, 1); action = KurumiPickupBridge.pickupAll(str(a,0)); }
            case "drop" -> { require(a, 2); action = KurumiDropBridge.drop(str(a,0),num(a,1)); }
            case "drop_all" -> { require(a, 0); action = KurumiDropBridge.dropAll(); }
            case "drop_item_all" -> { require(a, 1); action = KurumiDropBridge.dropItemAll(str(a,0)); }
            case "chain_on" -> { require(a, 0); action = KurumiChainBridge.setEnabled(true); }
            case "chain_off" -> { require(a, 0); action = KurumiChainBridge.setEnabled(false); }
            case "farm" -> { require(a, 1); action = BaritoneBridge.farm(num(a,0)); }
            case "fish" -> { require(a, 0); action = KurumiFishingBridge.start(); }
            case "stop_fishing" -> { require(a, 0); action = KurumiFishingBridge.stop(); }
            case "tpa_request" -> { require(a, 0); action = KurumiTpaBridge.request(); }
            case "tpa_request_to" -> { require(a, 1); action = KurumiTpaBridge.requestTo(str(a,0)); }
            case "tpa_accept" -> { require(a, 0); action = KurumiTpaBridge.accept(); }
            case "talk" -> {
                require(a, 1);
                String message = str(a,0);
                if (message.length() > 120) throw new IllegalArgumentException("聊天内容过长");
                var connection = Minecraft.getInstance().getConnection();
                if (connection == null) throw new IllegalStateException("未连接服务器");
                connection.sendChat(message);
                return true;
            }
            default -> {
                String tool = name;
                if (java.util.Set.of("search_all_block","find_nearest_block","check_inventory_item",
                        "check_inventory_count","get_crafting_recipe","get_skill","get_tool_help").contains(name)) {
                    require(a, 1);
                    if ("get_tool_help".equals(name)) return KurumiToolRegistry.help(str(a,0)).toString();
                    if ("get_skill".equals(name)) return KurumiSkillMemory.configured().describe(str(a,0)).toString();
                    if ("search_all_block".equals(name)) return KurumiBlockScanner.search(str(a,0)).toString();
                    tool += ":" + str(a,0);
                } else if ("get_distance".equals(name)) {
                    require(a, 2);
                    tool += ":" + str(a,0) + ":" + str(a,1);
                } else if ("inspect_slot".equals(name)) {
                    require(a, 1);
                    tool += ":" + num(a,0);
                } else require(a, 0);
                if (!tool.matches("[a-z_]+(?::[A-Za-z0-9_./-]+){0,3}"))
                    throw new IllegalArgumentException("无效的观察参数");
                return KurumiToolbox.observe(tool);
            }
        }
        return result(action);
    }

    private static void execute(String source, long run) {
        long started = System.nanoTime();
        scriptStartedNanos = started;
        TOOL_CALLS.set(0);
        try (Context context = Context.newBuilder("python")
                .allowHostAccess(HostAccess.NONE)
                .allowHostClassLookup(name -> false)
                .allowIO(false)
                .allowCreateProcess(false)
                .allowCreateThread(false)
                .build()) {
            activeContext = context;
            Map<String, Object> toolFunctions = new java.util.LinkedHashMap<>();
            // Every game operation is marshalled to the Minecraft thread.
            for (String name : java.util.List.of("get_position", "get_player_status",
                    "get_environment", "get_inventory", "scan_nearby_entities",
                    "scan_nearby_blocks", "scan_dropped_items", "inspect_container",
                    "get_fishing_status", "list_remembered_blocks", "get_chest_search_result",
                    "search_all_block", "find_nearest_block", "check_inventory_item",
                    "check_inventory_count", "get_crafting_recipe", "inspect_slot",
                    "get_distance", "get_tool_help", "get_skill", "check_inventory", "check_distance", "get_remembered_block", "get_remembered_block_at", "remember_block", "check_nearby_chest", "get_nearby_chest_result", "screenshot", "get_agent_observation",
                    "goto", "move", "come", "follow", "stop", "look", "look_at", "attack", "use_item", "hold_use_item", "sleep_in_bed", "sleep",
                    "interact", "put_item", "take_item", "take_all_items",
                    "chest_target", "chest_open", "chest_put", "chest_take",
                    "chest_put_all", "chest_take_all", "chest_put_item_all",
                    "close_container", "craft_open", "equip_main_hand", "search_chests", "take_from_memory",
                    "mine", "break_block", "place_block", "pickup_all",
                    "pickup_item_all", "drop", "drop_all", "drop_item_all",
                    "chain_on", "chain_off", "farm", "fish", "stop_fishing",
                    "tpa_request", "tpa_request_to", "tpa_accept", "talk")) {
                toolFunctions.put(name, (ProxyExecutable) args -> {
                    // Freeze guest values on the GraalPy worker; never move
                    // polyglot Value handles across to Minecraft's thread.
                    Object[] frozen = new Object[args.length];
                    for (int i = 0; i < args.length; i++) {
                        if (args[i].isString()) frozen[i] = args[i].asString();
                        else if (args[i].fitsInInt()) frozen[i] = args[i].asInt();
                        else if (args[i].isNumber()) frozen[i] = args[i].asDouble();
                        else if (args[i].isBoolean()) frozen[i] = args[i].asBoolean();
                        else throw new IllegalArgumentException("工具参数只允许基本类型");
                    }
                    if ("sleep".equals(name)) {
                        if (frozen.length != 1 || !(frozen[0] instanceof Number))
                            throw new IllegalArgumentException("sleep(seconds) 需要数字");
                        double seconds = ((Number) frozen[0]).doubleValue();
                        if (!Double.isFinite(seconds) || seconds < 0 || seconds > 60)
                            throw new IllegalArgumentException("sleep 范围为0到60秒");
                        try { Thread.sleep((long)(seconds * 1000)); }
                        catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            throw new IllegalStateException("Python 等待已取消", e);
                        }
                        return "null";
                    }
                    return asJson(onGameThread(() -> callGameTool(name, frozen)));
                });
            }
            context.getBindings("python").putMember("_handoff_observation",
                    (ProxyExecutable) args -> {
                        if (args.length != 1 || !args[0].isString())
                            throw new IllegalArgumentException("observe(value) 参数不合法");
                        String payload = args[0].asString();
                        if (payload.length() > 48_000)
                            throw new IllegalArgumentException("observe() 返回数据超过48KB");
                        pendingObservation = payload;
                        return true;
                    });
            context.getBindings("python").putMember("_java_tools", ProxyObject.fromMap(toolFunctions));
            context.eval("python", """
                    import json
                    class _KurumiTools:
                        def __getattr__(self, name):
                            callback = _java_tools[name]
                            def invoke(*args):
                                return json.loads(callback(*args))
                            return invoke
                    tools = _KurumiTools()
                    for _tool_name in (
                        "get_position", "get_player_status", "get_environment", "get_inventory",
                        "scan_nearby_entities", "scan_nearby_blocks", "scan_dropped_items",
                        "inspect_container", "inspect_slot", "get_distance", "search_all_block",
                        "find_nearest_block", "check_inventory_item", "check_inventory_count",
                        "check_inventory", "check_distance", "get_crafting_recipe",
                        "get_skill", "get_tool_help", "get_fishing_status", "get_agent_observation",
                        "remember_block", "get_remembered_block", "get_remembered_block_at",
                        "list_remembered_blocks", "get_chest_search_result", "check_nearby_chest",
                        "get_nearby_chest_result", "screenshot", "goto", "move", "come",
                        "follow", "stop", "look", "look_at", "attack", "use_item",
                        "hold_use_item", "sleep_in_bed", "sleep", "interact", "put_item",
                        "take_item", "take_all_items", "chest_target", "chest_open",
                        "chest_put", "chest_take", "chest_put_all", "chest_take_all",
                        "chest_put_item_all", "close_container", "craft_open",
                        "equip_main_hand", "search_chests", "take_from_memory", "mine",
                        "break_block", "place_block", "pickup_all", "pickup_item_all",
                        "drop", "drop_all", "drop_item_all", "chain_on", "chain_off",
                        "farm", "fish", "stop_fishing", "tpa_request", "tpa_request_to",
                        "tpa_accept", "talk"
                    ):
                        globals()[_tool_name] = getattr(tools, _tool_name)
                    """);
            context.eval("python", """
                    class _KurumiObserve(Exception):
                        pass

                    def observe(value):
                        _handoff_observation(json.dumps(value, ensure_ascii=False, default=str))
                        raise _KurumiObserve()
                    """);
            try {
                context.eval("python", source);
                finishedSuccess = true;
                finishedReport = "Python 脚本已执行完毕";
            } catch (PolyglotException error) {
                if (pendingObservation == null || !error.isGuestException()
                        || !"_KurumiObserve".equals(error.getGuestObject().getMetaObject().getMetaSimpleName()))
                    throw error;
                finishedObserved = true;
                finishedSuccess = true;
                finishedReport = "AI观察：" + pendingObservation;
                KurumiWebMonitor.log("Python observe() 交还控制权：" + finishedReport);
            }
            KurumiWebMonitor.log("GraalPy 脚本执行结束，耗时 "
                    + TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started) + "ms");
        } catch (PolyglotException | IllegalStateException error) {
            finishedSuccess = false;
            finishedReport = "GraalPy 脚本失败：" + error.getMessage();
            KurumiWebMonitor.log(finishedReport);
        } catch (Throwable error) {
            finishedSuccess = false;
            finishedReport = "GraalPy 运行时加载或执行失败：" + error;
            KurumiWebMonitor.log(finishedReport);
        } finally {
            activeContext = null;
            TOOL_CALLS.set(0);
            worker = null;
            if (cancelled) {
                finishedSuccess = false;
                finishedObserved = false;
                finishedReport = "Python 任务已取消";
            }
            RUNNING.set(false);
            finishedSequence = run;
        }
    }
}
