package com.liguyano.mcaimod.client;

import net.minecraft.client.Minecraft;

/** Sends only fixed trigger commands supported by the uploaded TPA datapack. */
public final class KurumiTpaBridge {
    public static BaritoneBridge.Result request() {
        return trigger("trigger TPA", "已打开 TPA 玩家选择菜单，请点击要申请传送的玩家");
    }

    /** Resolve the datapack's per-player SID from the client scoreboard. */
    public static BaritoneBridge.Result requestTo(String playerName) {
        if (playerName == null || !playerName.matches("[A-Za-z0-9_]{3,16}"))
            return new BaritoneBridge.Result(false, "玩家名称不合法");
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.getConnection() == null)
            return new BaritoneBridge.Result(false, "尚未进入游戏");
        boolean online = mc.getConnection().getOnlinePlayers().stream()
                .anyMatch(info -> playerName.equals(info.getProfile().name()));
        if (!online) return new BaritoneBridge.Result(false, "该玩家当前不在线：" + playerName);
        if (playerName.equals(mc.player.getName().getString()))
            return new BaritoneBridge.Result(false, "不能向自己申请传送");
        try {
            Object scoreboard = mc.level.getScoreboard();
            Object objective = scoreboard.getClass().getMethod("getObjective", String.class)
                    .invoke(scoreboard, "tpa.sid");
            if (objective == null)
                return new BaritoneBridge.Result(false, "客户端未同步 TPA 玩家编号，无法安全指定玩家");
            Class<?> holderClass = Class.forName("net.minecraft.world.scores.ScoreHolder");
            Object holder = holderClass.getMethod("forNameOnly", String.class).invoke(null, playerName);
            java.lang.reflect.Method lookup = null;
            for (var method : scoreboard.getClass().getMethods())
                if (method.getName().equals("getPlayerScoreInfo") && method.getParameterCount() == 2) {
                    lookup = method;
                    break;
                }
            if (lookup == null)
                return new BaritoneBridge.Result(false, "当前版本无法读取 TPA 玩家编号");
            Object score = lookup.invoke(scoreboard, holder, objective);
            if (score == null)
                return new BaritoneBridge.Result(false, "未找到 " + playerName + " 的 TPA 编号；请先使用传送菜单");
            int sid = ((Number) score.getClass().getMethod("value").invoke(score)).intValue();
            if (sid <= 0)
                return new BaritoneBridge.Result(false, "TPA 玩家编号无效");
            mc.getConnection().sendCommand("trigger tpa.want_tp_to set " + sid);
            return new BaritoneBridge.Result(true, "已向 " + playerName + " 发送 TPA 申请指令（是否成功由服务器确认）");
        } catch (ReflectiveOperationException | RuntimeException error) {
            return new BaritoneBridge.Result(false, "无法读取 TPA 玩家编号，未发送请求");
        }
    }

    public static BaritoneBridge.Result accept() {
        return trigger("trigger tpa.accept_tp", "已发送接受当前 TPA 传送请求的指令");
    }

    private static BaritoneBridge.Result trigger(String command, String message) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.getConnection() == null)
            return new BaritoneBridge.Result(false, "当前不在多人游戏中");
        mc.getConnection().sendCommand(command);
        return new BaritoneBridge.Result(true, message + "（是否成功由服务器决定）");
    }

    private KurumiTpaBridge() {}
}
