package com.liguyano.mcaimod.client;

import com.google.gson.JsonObject;
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

    private KurumiEmbeddedPythonBridge() {}

    public static boolean busy() { return RUNNING.get(); }

    public static void cancel() {
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
        worker = new Thread(() -> execute(script), "Kurumi-GraalPy-Worker");
        worker.setDaemon(true);
        worker.start();
        return new BaritoneBridge.Result(true, "嵌入式 Python 脚本已启动（实验模式）");
    }

    private static Object onGameThread(Supplier<Object> operation) {
        Minecraft mc = Minecraft.getInstance();
        CompletableFuture<Object> done = new CompletableFuture<>();
        mc.execute(() -> {
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

    private static Object result(BaritoneBridge.Result r) {
        return ProxyObject.fromMap(Map.of(
                "success", r.success(),
                "completed", false,
                "message", r.message()));
    }

    private static void execute(String source) {
        long started = System.nanoTime();
        try (Context context = Context.newBuilder("python")
                .allowHostAccess(HostAccess.NONE)
                .allowHostClassLookup(name -> false)
                .allowIO(false)
                .allowCreateProcess(false)
                .allowCreateThread(false)
                .build()) {
            activeContext = context;
            Map<String, Object> toolFunctions = Map.of(
                    "get_position", (ProxyExecutable) args -> {
                        if (args.length != 0) throw new IllegalArgumentException("get_position()");
                        return onGameThread(() -> {
                            var player = Minecraft.getInstance().player;
                            if (player == null) throw new IllegalStateException("玩家尚未进入游戏");
                            return ProxyObject.fromMap(Map.of(
                                    "x", player.getBlockX(), "y", player.getBlockY(), "z", player.getBlockZ()));
                        });
                    },
                    "goto", (ProxyExecutable) args -> {
                        if (args.length != 3) throw new IllegalArgumentException("goto(x,y,z)");
                        int x = args[0].asInt(), y = args[1].asInt(), z = args[2].asInt();
                        return onGameThread(() -> result(BaritoneBridge.gotoCoordinates(x, y, z)));
                    },
                    "talk", (ProxyExecutable) args -> {
                        if (args.length != 1) throw new IllegalArgumentException("talk(message)");
                        String message = args[0].asString();
                        if (message.length() > 120) throw new IllegalArgumentException("消息过长");
                        return onGameThread(() -> {
                            var connection = Minecraft.getInstance().getConnection();
                            if (connection == null) throw new IllegalStateException("尚未连接服务器");
                            connection.sendChat(message);
                            return true;
                        });
                    }
            );
            context.getBindings("python").putMember("tools", ProxyObject.fromMap(toolFunctions));
            context.eval("python", source);
            KurumiWebMonitor.log("GraalPy 脚本执行结束，耗时 "
                    + TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started) + "ms");
        } catch (PolyglotException | IllegalStateException error) {
            KurumiWebMonitor.log("GraalPy 脚本失败：" + error.getMessage());
        } catch (Throwable error) {
            KurumiWebMonitor.log("GraalPy 运行时加载或执行失败：" + error);
        } finally {
            activeContext = null;
            worker = null;
            RUNNING.set(false);
        }
    }
}
