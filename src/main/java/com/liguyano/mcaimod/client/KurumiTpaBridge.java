package com.liguyano.mcaimod.client;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.event.ClientChatReceivedEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import java.util.regex.Pattern;

/** Sends only fixed trigger commands supported by the uploaded TPA datapack. */
public final class KurumiTpaBridge {
    private static final Pattern SAFE_COMMAND = Pattern.compile("/trigger tpa\\.want_tp_to set ([1-9][0-9]{0,8})");
    private static String pendingTarget;
    private static long pendingUntil;

    public static void install() {
        NeoForge.EVENT_BUS.addListener(KurumiTpaBridge::onSystemMessage);
        NeoForge.EVENT_BUS.addListener(KurumiTpaBridge::onTick);
        NeoForge.EVENT_BUS.addListener(KurumiTpaBridge::onLogout);
    }
    private static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) { pendingTarget = null; }
    private static void onTick(ClientTickEvent.Post event) {
        if (pendingTarget != null && System.currentTimeMillis() > pendingUntil) {
            KurumiWebMonitor.log("TPA: 菜单等待超时，未发送申请：" + pendingTarget);
            pendingTarget = null;
        }
    }
    private static void onSystemMessage(ClientChatReceivedEvent.System event) {
        if (pendingTarget == null || System.currentTimeMillis() > pendingUntil) return;
        inspectMenu(event.getMessage());
    }
    private static void inspectMenu(Component root) {
        if (root == null || pendingTarget == null) return;
        String target = pendingTarget;
        java.util.ArrayDeque<Component> queue = new java.util.ArrayDeque<>();
        queue.add(root);
        while (!queue.isEmpty()) {
            Component part = queue.removeFirst();
            var click = part.getStyle().getClickEvent();
            if (click != null && part.getString().strip().equals("[" + target + "]")) {
                try {
                    Object value = click.getClass().getMethod("command").invoke(click);
                    if (value instanceof String command && SAFE_COMMAND.matcher(command).matches()) {
                        var mc = Minecraft.getInstance();
                        if (mc.getConnection() == null) { pendingTarget = null; return; }
                        pendingTarget = null;
                        mc.getConnection().sendCommand(command.substring(1));
                        KurumiWebMonitor.log("TPA: 菜单匹配 " + target + "，已发送申请命令，等待服务器确认");
                        return;
                    }
                } catch (ReflectiveOperationException ignored) {
                    // Unknown click-event representation: fail closed.
                }
            }
            queue.addAll(part.getSiblings());
        }
    }

    public static BaritoneBridge.Result request() {
        return trigger("trigger TPA", "已打开 TPA 玩家选择菜单，请点击要申请传送的玩家");
    }

    /** Resolve the datapack's per-player SID from the client scoreboard. */
    public static BaritoneBridge.Result requestTo(String playerName) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.getConnection() == null)
            return new BaritoneBridge.Result(false, "尚未进入游戏");
        var match = KurumiPlayerResolver.resolve(playerName, KurumiPlayerDirectory.onlineNames());
        if (!match.found()) return new BaritoneBridge.Result(false, match.failure(playerName));
        playerName = match.name();
        if (playerName.equals(mc.player.getName().getString()))
            return new BaritoneBridge.Result(false, "不能向自己申请传送");
        pendingTarget = playerName;
        pendingUntil = System.currentTimeMillis() + 10000;
        mc.getConnection().sendCommand("trigger TPA");
        KurumiWebMonitor.log("TPA: 等待菜单匹配 " + playerName);
        return new BaritoneBridge.Result(true, "已请求 TPA 玩家菜单；尚未发送传送申请");

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
