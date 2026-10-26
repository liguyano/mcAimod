package com.liguyano.mcaimod.client;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import java.util.function.BooleanSupplier;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
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

    private static Boolean previousAllowBreak;
    private static Object previousBlocksToAvoid;
    private static Runnable waterFallback;
    private static BooleanSupplier waterGoalReached;
    private static long lastWaterProgress;
    private static double lastWaterX, lastWaterZ;

    /** Water is initially impassable to Baritone, then allowed if the land route stalls. */
    private static boolean setWaterAvoidance(boolean enabled) {
        try {
            Class<?> api = Class.forName("baritone.api.BaritoneAPI");
            Object settings = api.getMethod("getSettings").invoke(null);
            Object setting = settings.getClass().getField("blocksToAvoid").get(settings);
            java.lang.reflect.Field value = setting.getClass().getField("value");
            if (enabled) {
                if (previousBlocksToAvoid == null) previousBlocksToAvoid = value.get(setting);
                @SuppressWarnings("unchecked")
                List<Object> blocks = new ArrayList<>((List<Object>) previousBlocksToAvoid);
                if (!blocks.contains(Blocks.WATER)) blocks.add(Blocks.WATER);
                value.set(setting, blocks);
            } else if (previousBlocksToAvoid != null) {
                value.set(setting, previousBlocksToAvoid);
                previousBlocksToAvoid = null;
            }
            return true;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ex) {
            return false;
        }
    }

    private static Result preferLand(Supplier<Result> start, Runnable fallback, BooleanSupplier reached) {
        waterFallback = null;
        waterGoalReached = null;
        setWaterAvoidance(false);
        boolean avoidsWater = setWaterAvoidance(true);
        Result result = start.get();
        if (!result.success()) {
            setWaterAvoidance(false);
            return result;
        }
        if (avoidsWater) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                lastWaterX = mc.player.getX();
                lastWaterZ = mc.player.getZ();
                lastWaterProgress = System.currentTimeMillis();
                waterFallback = fallback;
                waterGoalReached = reached;
            }
        }
        return new Result(true, result.message() + (avoidsWater
                ? "；优先陆路，若长时间无法前进则允许涉水" : "；避水选项不可用，使用普通寻路"));
    }

    /** Called on client ticks even when the agent is waiting for a script result. */
    public static void tickWaterAvoidance() {
        Runnable fallback = waterFallback;
        if (fallback == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            waterFallback = null;
            waterGoalReached = null;
            setWaterAvoidance(false);
            return;
        }
        if (waterGoalReached != null && waterGoalReached.getAsBoolean()) {
            waterFallback = null;
            waterGoalReached = null;
            setWaterAvoidance(false);
            return;
        }
        double dx = mc.player.getX() - lastWaterX;
        double dz = mc.player.getZ() - lastWaterZ;
        if (dx * dx + dz * dz >= 1.0) {
            lastWaterX = mc.player.getX();
            lastWaterZ = mc.player.getZ();
            lastWaterProgress = System.currentTimeMillis();
        } else if (shouldFallbackToWater(lastWaterProgress, System.currentTimeMillis())) {
            waterFallback = null;
            waterGoalReached = null;
            setWaterAvoidance(false);
            fallback.run();
            KurumiWebMonitor.log("Baritone 陆路寻路停滞，已恢复普通寻路（允许涉水）");
        }
    }

    static boolean shouldFallbackToWater(long lastProgress, long now) {
        return now - lastProgress >= 8_000L;
    }

    /** Scope no-breaking navigation until another explicit action or stop. */
    private static Result setBreakPolicy(boolean navigation) {
        try {
            Class<?> api = Class.forName("baritone.api.BaritoneAPI");
            Object settings = api.getMethod("getSettings").invoke(null);
            Object setting = settings.getClass().getField("allowBreak").get(settings);
            java.lang.reflect.Field value = setting.getClass().getField("value");
            if (navigation) {
                if (previousAllowBreak == null) previousAllowBreak = (Boolean) value.get(setting);
                value.set(setting, false);
            } else if (previousAllowBreak != null) {
                value.set(setting, previousAllowBreak);
                previousAllowBreak = null;
            }
            return new Result(true, navigation ? "已禁止寻路破坏方块" : "已恢复原先的破坏方块设置");
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ex) {
            return new Result(false, "无法设置 Baritone 禁止破坏方块；为安全起见未开始寻路");
        }
    }

    public static Result farm(int radius) {
        if (radius < 4 || radius > 32) {
            return new Result(false, "农作半径必须在 4–32 格之间");
        }
        waterFallback = null;
        waterGoalReached = null;
        setWaterAvoidance(false);
        // Baritone's #farm command uses its own optional arguments. Start with
        // the standard command rather than assuming an unsupported radius syntax.
        Result policy = setBreakPolicy(false);
        if (!policy.success()) return policy;
        return executeLocal("farm", "已向 Baritone 发送 #farm");
    }

    public static Result gotoCoordinates(int x, int y, int z) {
        Result policy = setBreakPolicy(true);
        if (!policy.success()) return policy;
        String command = "goto " + x + " " + y + " " + z;
        String message = "已向 Baritone 发送无破坏寻路到坐标 " + x + " " + y + " " + z;
        return preferLand(() -> executeLocal(command, message), () -> executeLocal(command, message), () -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) return false;
            double dx = mc.player.getX() - (x + 0.5);
            double dz = mc.player.getZ() - (z + 0.5);
            return dx * dx + dz * dz <= 2.25 && Math.abs(mc.player.getY() - y) <= 2;
        });
    }

    /** Relative movement uses the player's actual yaw, not fixed world axes. */
    public static Result moveRelative(String direction, int blocks) {
        if (!java.util.Set.of("forward", "back", "left", "right").contains(direction)
                || blocks < 1 || blocks > 16)
            return new Result(false, "相对移动只支持前后左右，距离1至16格");
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null)
            return new Result(false, "当前不在游戏世界");
        double yaw = Math.toRadians(mc.player.getYRot());
        double forwardX = -Math.sin(yaw);
        double forwardZ = Math.cos(yaw);
        double dx = switch (direction) {
            case "back" -> -forwardX;
            case "left" -> forwardZ;
            case "right" -> -forwardZ;
            default -> forwardX;
        };
        double dz = switch (direction) {
            case "back" -> -forwardZ;
            case "left" -> -forwardX;
            case "right" -> forwardX;
            default -> forwardZ;
        };
        int x = (int) Math.round(mc.player.getX() + dx * blocks);
        int z = (int) Math.round(mc.player.getZ() + dz * blocks);
        return gotoCoordinates(x, mc.player.getBlockY(), z);
    }

    /** One-shot approach: navigate to the player's current position, without enabling follow. */
    public static Result comeToPlayer(String name) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null)
            return new Result(false, "当前不在游戏世界");
        var match = KurumiPlayerResolver.resolve(name, KurumiPlayerDirectory.onlineNames());
        if (!match.found()) return new Result(false, match.failure(name));
        String canonical = match.name();
        Player target = null;
        for (Player player : mc.level.players()) {
            if (canonical.equals(player.getName().getString())) {
                target = player;
                break;
            }
        }
        if (target == null)
            return new Result(false, "玩家 " + canonical + " 在线但不在客户端可见范围内");
        // Stop any persistent follow first, then issue a finite coordinate goal.
        Result stopped = stop();
        if (!stopped.success()) return stopped;
        return gotoCoordinates(target.getBlockX(), target.getBlockY(), target.getBlockZ());
    }

    public static Result followPlayer(String name) {
        var match = KurumiPlayerResolver.resolve(name, KurumiPlayerDirectory.onlineNames());
        if (!match.found()) return new Result(false, match.failure(name));
        String canonical = match.name();
        Result policy = setBreakPolicy(true);
        if (!policy.success()) return policy;
        return preferLand(() -> startFollow(canonical), () -> startFollow(canonical), () -> false);
    }

    private static Result startFollow(String canonical) {
        try {
            Object baritone = primaryBaritone();
            if (baritone == null) return unavailable();
            Object process = invoke(baritone, "getFollowProcess");
            if (process == null) return unavailable();
            Predicate<Object> playerPredicate = entity -> {
                try {
                    Object component = entity.getClass().getMethod("getName").invoke(entity);
                    Object text = component.getClass().getMethod("getString").invoke(component);
                    return canonical.equals(String.valueOf(text));
                } catch (ReflectiveOperationException | RuntimeException ex) {
                    return false;
                }
            };
            process.getClass().getMethod("follow", Predicate.class).invoke(process, playerPredicate);
            return new Result(true, "已请求 Baritone 跟随玩家 " + canonical);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ex) {
            return new Result(false, "Baritone 跟随接口不可用，未开始跟随");
        }
    }

    /** Bounded resource collection; only known block identifiers can be passed. */
    public static Result mineBlock(String blockId) {
        waterFallback = null;
        waterGoalReached = null;
        setWaterAvoidance(false);
        if (blockId == null || !blockId.matches("minecraft:[a-z0-9_]{1,64}"))
            return new Result(false, "无效的方块名称");
        Result policy = setBreakPolicy(false);
        if (!policy.success()) return policy;
        return executeLocal("mine " + blockId, "已向 Baritone 发送采集 " + blockId);
    }

    public static Result stop() {
        waterFallback = null;
        waterGoalReached = null;
        setWaterAvoidance(false);
        Result stopped = executeLocal("stop", "已向 Baritone 发送 #stop");
        Result restored = setBreakPolicy(false);
        return !restored.success() ? restored : stopped;
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
