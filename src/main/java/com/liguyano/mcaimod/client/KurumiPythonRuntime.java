package com.liguyano.mcaimod.client;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Optional GraalPy boundary. Do not link Graal classes in the regular
 * Minecraft event handler: a missing optional runtime must not crash a world.
 */
public final class KurumiPythonRuntime {
    private static final String ENGINE = "com.liguyano.mcaimod.client.KurumiEmbeddedPythonBridge";
    private KurumiPythonRuntime() {}

    public static boolean available() {
        try {
            Class.forName("org.graalvm.polyglot.PolyglotException", false,
                    KurumiPythonRuntime.class.getClassLoader());
            Class.forName("org.graalvm.polyglot.Context", false,
                    KurumiPythonRuntime.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException | LinkageError error) {
            return false;
        }
    }

    private static Object invoke(String name, Class<?>[] signature, Object... arguments) {
        if (!available()) throw new IllegalStateException("GraalPy runtime is not packaged");
        try {
            Class<?> engine = Class.forName(ENGINE);
            Method method = engine.getMethod(name, signature);
            return method.invoke(null, arguments);
        } catch (InvocationTargetException error) {
            Throwable cause = error.getCause();
            throw new IllegalStateException("GraalPy " + name + " failed", cause);
        } catch (ReflectiveOperationException | LinkageError error) {
            throw new IllegalStateException("GraalPy bridge unavailable", error);
        }
    }

    private static boolean flag(String method) {
        return available() && Boolean.TRUE.equals(invoke(method, new Class<?>[0]));
    }
    private static long number(String method) {
        return available() ? ((Number) invoke(method, new Class<?>[0])).longValue() : 0;
    }
    public static boolean busy() { return flag("busy"); }
    public static void cancel() {
        if (available()) {
            try { invoke("cancel", new Class<?>[0]); }
            catch (RuntimeException error) {
                KurumiWebMonitor.log("Python cancellation failed: " + error.getMessage());
            }
        }
    }
    public static BaritoneBridge.Result start(String script) {
        if (!available()) return new BaritoneBridge.Result(false,
                "GraalPy runtime missing from Mod distribution; use legacy script engine");
        try {
            return (BaritoneBridge.Result) invoke("start", new Class<?>[]{String.class}, script);
        } catch (RuntimeException error) {
            return new BaritoneBridge.Result(false, error.getMessage());
        }
    }
    public static long activeRunId() { return number("activeRunId"); }
    public static long finishedRunId() { return number("finishedRunId"); }
    public static boolean finishedRunSucceeded() { return flag("finishedRunSucceeded"); }
    public static boolean finishedRunObserved() { return flag("finishedRunObserved"); }
    public static long finishedRunVisionId() { return number("finishedRunVisionId"); }
    public static String finishedRunReport() {
        return available() ? (String) invoke("finishedRunReport", new Class<?>[0])
                : "GraalPy runtime missing";
    }
}
