package com.liguyano.mcaimod.prototype;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyExecutable;
import org.graalvm.polyglot.proxy.ProxyObject;

import java.util.Map;

/**
 * Embedded message bridge proof of concept. No live Minecraft calls.
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

            // This Python function owns the message dispatch. Later it can be
            // replaced by a real model/agent loop without changing Java's API.
            context.eval("python", """
                    def handle_message(sender, message):
                        if message == "测试位置":
                            position = tools.get_position()
                            tools.talk(f"{sender}: 当前坐标 {position.x}, {position.y}, {position.z}")
                            return {"success": True, "handled_by": "python"}
                        if message == "向前一格":
                            position = tools.get_position()
                            result = tools.goto(position.x + 1, position.y, position.z)
                            tools.talk(f"{sender}: 寻路完成={result.completed}")
                            return {"success": result.success, "handled_by": "python"}
                        tools.talk(f"{sender}: 尚未接入模型，收到消息：{message}")
                        return {"success": True, "handled_by": "python"}
                    """);

            Value handler = context.getBindings("python").getMember("handle_message");
            // Same Java -> Python entrypoint that NeoForge will call in a later stage.
            Value first = handler.execute("SekaiSaionji", "测试位置");
            Value second = handler.execute("SekaiSaionji", "向前一格");
            if (!first.getHashValue("success").asBoolean()
                    || !second.getHashValue("success").asBoolean())
                throw new IllegalStateException("Python message bridge failed");
            System.out.println("Java -> embedded Python -> mock Minecraft tools: OK");
        } catch (PolyglotException error) {
            System.err.println("GraalPy runtime failed: " + error.getMessage());
            System.exit(1);
        }
    }
}
