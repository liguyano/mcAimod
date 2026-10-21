package com.liguyano.mcaimod.client;

import net.minecraft.client.Minecraft;

/** Sends only fixed trigger commands supported by the uploaded TPA datapack. */
public final class KurumiTpaBridge {
    public static BaritoneBridge.Result request() {
        return trigger("trigger TPA", "已打开 TPA 玩家选择菜单，请点击要申请传送的玩家");
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
