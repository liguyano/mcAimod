package com.liguyano.mcaimod.client;

import java.lang.reflect.Method;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import java.util.function.Predicate;

/**
 * Optional Baritone bridge. Commands are dispatched locally through Baritone's
 * command manager, never sent as public multiplayer chat.
 */
public final class BaritoneBridge {
    public record Result(boolean success, String message) {}

    public static boolean available() {
        try {
            return primaryBaritone() != null;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ex) {
            return false;
        }
    }

    public static Result farm(int radius) {
        if (radius < 4 || radius > 32) {
            return new Result(false, "农作半径必须在 4–32 格之间");
        }
        // Baritone's #farm command uses its own optional arguments. Start with
        // the standard command rather than assuming an unsupported radius syntax.
        return executeLocal("farm", "已向 Baritone 发送 #farm");
    }

    public static Result gotoCoordinates(int x, int y, int z) {
        return executeLocal("goto " + x + " " + y + " " + z,
                "已向 Baritone 发送前往坐标 " + x + " " + y + " " + z);
    }

    /** One-shot approach: navigate to the player's current position, without enabling follow. */
    public static Result comeToPlayer(String name) {
        if (name == null || !name.matches("[A-Za-z0-9_]{3,16}"))
            return new Result(false, "玩家名称不合法");
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null)
            return new Result(false, "当前不在游戏世界");
        Player target = null;
        for (Player player : mc.level.players()) {
            if (name.equalsIgnoreCase(player.getName().getString())) {
                target = player;
                break;
            }
        }
        if (target == null)
            return new Result(false, "目标玩家不在客户端可见范围内");
        // Stop any persistent follow first, then issue a finite coordinate goal.
        Result stopped = stop();
        if (!stopped.success()) return stopped;
        return gotoCoordinates(target.getBlockX(), target.getBlockY(), target.getBlockZ());
    }

    public static Result followPlayer(String name) {
        if (name == null || !name.matches("[A-Za-z0-9_]{3,16}"))
            return new Result(false, "玩家名称不合法");
        try {
            Object baritone = primaryBaritone();
            if (baritone == null) return unavailable();
            Object process = invoke(baritone, "getFollowProcess");
            if (process == null) return unavailable();
            Predicate<Object> match = entity -> {
                try {
                    Object component = entity.getClass().getMethod("getName").invoke(entity);
                    Object text = component.getClass().getMethod("getString").invoke(component);
                    return name.equalsIgnoreCase(String.valueOf(text));
                } catch (ReflectiveOperationException | RuntimeException ex) {
                    return false;
                }
            };
            process.getClass().getMethod("follow", Predicate.class).invoke(process, match);
            return new Result(true, "已请求 Baritone 跟随玩家 " + name);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ex) {
            return new Result(false, "Baritone 跟随接口不可用，未开始跟随");
        }
    }

    /** Bounded resource collection; only known block identifiers can be passed. */
    public static Result mineBlock(String blockId) {
        if (blockId == null || !blockId.matches("minecraft:[a-z0-9_]{1,64}"))
            return new Result(false, "无效的方块名称");
        return executeLocal("mine " + blockId, "已向 Baritone 发送采集 " + blockId);
    }

    public static Result stop() {
        return executeLocal("stop", "已向 Baritone 发送 #stop");
    }

    /**
     * Only commands explicitly mapped in this class are allowed. Do not accept
     * raw LLM output or arbitrary text from multiplayer chat.
     */
    private static Result executeLocal(String command, String successMessage) {
        try {
            Object baritone = primaryBaritone();
            if (baritone == null) return unavailable();
            Object manager = invoke(baritone, "getCommandManager");
            if (manager == null) return unavailable();
            Method execute = manager.getClass().getMethod("execute", String.class);
            execute.invoke(manager, command);
            return new Result(true, successMessage);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ex) {
            return new Result(false, "Baritone 本地指令接口不可用；请确认版本支持 #farm/#stop");
        }
    }

    private static Object primaryBaritone() throws ReflectiveOperationException {
        Class<?> api = Class.forName("baritone.api.BaritoneAPI");
        Object provider = api.getMethod("getProvider").invoke(null);
        return provider == null ? null : invoke(provider, "getPrimaryBaritone");
    }

    private static Object invoke(Object target, String method) throws ReflectiveOperationException {
        return target.getClass().getMethod(method).invoke(target);
    }

    private static Result unavailable() {
        return new Result(false, "未检测到可用的 Baritone");
    }

    private BaritoneBridge() {}
}
