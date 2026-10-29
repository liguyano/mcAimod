package com.liguyano.mcaimod.prototype;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyExecutable;
import org.graalvm.polyglot.proxy.ProxyObject;

import java.util.Map;

/**
 * Embedded script executor proof of concept. No live Minecraft calls.
 * Real-game integration requires a bounded game-thread dispatch bridge.
 */
public final class KurumiPythonPrototype {
    private KurumiPythonPrototype() {}

    public static void main(String[] args) {
        try (Context context = Context.newBuilder("python")
                .allowHostAccess(HostAccess.NONE)
                .allowHostClassLookup(name -> false)
                .allowIO(false)
                .allowCreateProcess(false)
                .allowCreateThread(false)
                .build()) {
            // Java exposes only whitelisted tools, not a Java object or class loader.
            ProxyObject tools = ProxyObject.fromMap(Map.of(
                    "get_position", (ProxyExecutable) arguments ->
                            ProxyObject.fromMap(Map.of("x", 10, "y", 64, "z", 20)),
                    "goto", (ProxyExecutable) arguments -> {
                        if (arguments.length != 3)
                            throw new IllegalArgumentException("goto requires x, y, z");
                        System.out.println("[mock Minecraft] goto("
                                + arguments[0] + ", " + arguments[1] + ", " + arguments[2] + ")");
                        return ProxyObject.fromMap(Map.of("success", true,
                                "completed", true, "message", "mock destination reached"));
                    },
                    "talk", (ProxyExecutable) arguments -> {
                        if (arguments.length != 1)
                            throw new IllegalArgumentException("talk requires message");
                        System.out.println("[mock Minecraft chat] " + arguments[0].asString());
                        return true;
                    }
            ));
            context.getBindings("python").putMember("tools", tools);

            // Java keeps the LLM, chat handling and agent orchestration.
            // Only scripts are delegated into the embedded Python interpreter.
            String script = """
                    point = tools.get_position()
                    result = tools.goto(point.x + 1, point.y, point.z)
                    if result.success and result.completed:
                        tools.talk("脚本执行成功")
                    else:
                        tools.talk("脚本执行失败")
                    """;
            context.eval("python", script);
            System.out.println("Java -> embedded Python script -> mock tools: OK");
        } catch (PolyglotException error) {
            System.err.println("GraalPy runtime failed: " + error.getMessage());
            System.exit(1);
        }
    }
}
