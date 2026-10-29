package com.liguyano.mcaimod.prototype;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyExecutable;
import org.graalvm.polyglot.proxy.ProxyObject;

import java.util.Map;

/**
 * Opt-in standalone prototype, NOT linked to the live NeoForge agent.
 * Only explicit, mock tool functions are exposed. No Java host classes.
 * Python guest code is controlled and bounded by the test harness;
 * untrusted model output must not be used until isolation/timeouts are implemented.
 */
public final class KurumiPythonPrototype {
    private KurumiPythonPrototype() {}

    public static void main(String[] args) {
        // No host class access, no IO, no new processes, and no networking.
        try (Context context = Context.newBuilder("python")
                .allowHostAccess(HostAccess.NONE)
                .allowHostClassLookup(name -> false)
                .allowIO(false)
                .allowCreateProcess(false)
                .allowCreateThread(false)
                .build()) {
            ProxyObject tools = ProxyObject.fromMap(Map.of(
                    "get_position", (ProxyExecutable) arguments ->
                            ProxyObject.fromMap(Map.of("x", 10, "y", 64, "z", 20)),
                    "goto", (ProxyExecutable) arguments -> {
                        if (arguments.length != 3) throw new IllegalArgumentException("goto requires x,y,z");
                        System.out.println("[mock] goto(" + arguments[0] + ", "
                                + arguments[1] + ", " + arguments[2] + ")");
                        return ProxyObject.fromMap(Map.of(
                                "success", true, "completed", true, "message", "mock destination reached"));
                    }
            ));
            context.getBindings("python").putMember("tools", tools);
            Value result = context.eval("python", """
                    # Normal Python 3 syntax (not Kurumi's custom parser)
                    point = tools.get_position()
                    reached = tools.goto(point.x + 1, point.y, point.z)
                    values = [1, 2, 3]
                    assert sum(values) == 6
                    assert reached.success and reached.completed
                    "Python interpreter works"
                    """);
            System.out.println(result.asString());
        } catch (PolyglotException error) {
            System.err.println("GraalPy runtime failed: " + error.getMessage());
            System.exit(1);
        }
    }
}
