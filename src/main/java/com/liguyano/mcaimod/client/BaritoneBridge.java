package com.liguyano.mcaimod.client;

import java.lang.reflect.Method;

/**
 * Optional runtime-only bridge to the Baritone API.
 * No compile-time dependency: if the installed Baritone is absent or its API differs,
 * the operation fails closed rather than issuing arbitrary chat commands.
 */
public final class BaritoneBridge {
    public record Result(boolean success, String message) {}

    public static boolean available() {
        try {
            return primaryBaritone() != null;
        } catch (ReflectiveOperationException | LinkageError ex) {
            return false;
        }
    }

    /** Start Baritone's built-in farming process with a locally bounded radius. */
    public static Result farm(int radius) {
        if (radius < 4 || radius > 32) {
            return new Result(false, "农作半径必须在 4–32 格之间");
        }
        try {
            Object baritone = primaryBaritone();
            if (baritone == null) return unavailable();
            Object process = invoke(baritone, "getFarmProcess");
            if (process == null) return unavailable();
            // Baritone API commonly exposes farm(int range). Verify the method at runtime.
            Method farm = process.getClass().getMethod("farm", int.class);
            farm.invoke(process, radius);
            return new Result(true, "已向 Baritone 提交农作任务，半径 " + radius + " 格");
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ex) {
            return new Result(false, "Baritone 农作接口不可用或与当前版本不兼容");
        }
    }

    /** Emergency stop; does not depend on an LLM response. */
    public static Result stop() {
        try {
            Object baritone = primaryBaritone();
            if (baritone == null) return unavailable();
            Object pathing = invoke(baritone, "getPathingBehavior");
            Method cancel = pathing.getClass().getMethod("cancelEverything");
            cancel.invoke(pathing);
            return new Result(true, "已要求 Baritone 停止当前寻路任务");
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ex) {
            return new Result(false, "无法调用 Baritone 停止接口；请在游戏中手动停止");
        }
    }

    private static Object primaryBaritone() throws ReflectiveOperationException {
        Class<?> api = Class.forName("baritone.api.BaritoneAPI");
        return api.getMethod("getProvider").invoke(null) == null ? null
                : invoke(api.getMethod("getProvider").invoke(null), "getPrimaryBaritone");
    }

    private static Object invoke(Object target, String method) throws ReflectiveOperationException {
        return target.getClass().getMethod(method).invoke(target);
    }

    private static Result unavailable() {
        return new Result(false, "没有检测到可用的 Baritone，请确认安装了兼容当前 Minecraft 版本的客户端");
    }

    private BaritoneBridge() {}
}
