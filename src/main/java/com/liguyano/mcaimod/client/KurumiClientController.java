package com.liguyano.mcaimod.client;

import com.liguyano.mcaimod.model.ChatMessage;
import com.liguyano.mcaimod.model.ModelProfile;
import com.liguyano.mcaimod.model.ModelProfiles;
import com.liguyano.mcaimod.model.OpenAiCompatibleProvider;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.event.ClientChatReceivedEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.minecraft.commands.Commands;
import net.neoforged.fml.loading.FMLPaths;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

/** Local-only multiplayer chat integration. Actions remain disabled until game-side adapters exist. */
public final class KurumiClientController {
    private static final KurumiWakeRouter GATE =
            new KurumiWakeRouter(Clock.systemUTC(), Duration.ofSeconds(30));
    private static final OpenAiCompatibleProvider PROVIDER = new OpenAiCompatibleProvider();
    private static boolean enabled = false;
    private static long generation = 0;

    public static void install() {
        NeoForge.EVENT_BUS.addListener(KurumiClientController::onPlayerChat);
        NeoForge.EVENT_BUS.addListener(KurumiClientController::registerCommands);
        NeoForge.EVENT_BUS.addListener(KurumiClientController::onLogout);
    }

    private static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        generation++;
        GATE.reset();
    }

    private static void registerCommands(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("kurumi")
                .then(Commands.literal("on").executes(context -> {
                    enabled = true;
                    notice("已启用聊天监听（仅本机）。");
                    return 1;
                }))
                .then(Commands.literal("off").executes(context -> {
                    enabled = false;
                    generation++;
                    GATE.reset();
                    notice("已关闭聊天监听。");
                    return 1;
                }))
                .then(Commands.literal("stop").executes(context -> {
                    generation++;
                    GATE.reset();
                    notice("已取消等待中的 Kurumi 请求。");
                    return 1;
                }))
                .then(Commands.literal("status").executes(context -> {
                    notice("聊天监听：" + (enabled ? "开启" : "关闭") + "；自动操作：尚未实现");
                    return 1;
                })));
    }

    private static void onPlayerChat(ClientChatReceivedEvent.Player event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!enabled || minecraft.player == null || minecraft.getConnection() == null) return;
        if (minecraft.player.getUUID().equals(event.getSender())) return;
        String message = event.getPlayerChatMessage().signedContent();
        if (!GATE.tryAccept(message)) return;
        long requestGeneration = generation;
        // Acknowledgement is public and uses the player's normal chat account.

        Optional<ModelProfile> profile;
        try {
            profile = ModelProfiles.load(FMLPaths.CONFIGDIR.get().resolve("mcaimod-models.json")).first();
        } catch (Exception error) {
            GATE.finish();
            notice("模型配置读取失败，请检查 config/mcaimod-models.json");
            return;
        }
        if (profile.isEmpty()) {
            GATE.finish();
            notice("没有启用的模型配置。");
            return;
        }
        minecraft.getConnection().sendChat("[Kurumi] 好的，我听到了。");
        PROVIDER.chat(profile.get(), List.of(
                new ChatMessage("system", "你是 Minecraft 中的 Kurumi。只回答简短的中文聊天内容，不要声称已经执行任何游戏动作。不要输出指令。"),
                new ChatMessage("user", message)))
                .whenComplete((reply, error) -> minecraft.execute(() -> {
                    GATE.finish();
                    if (!enabled || generation != requestGeneration || minecraft.player == null ||
                            minecraft.getConnection() == null) return;
                    if (error != null) {
                        notice("模型请求失败，请检查网络与模型配置。");
                        return;
                    }
                    String safe = reply == null ? "" : reply.strip().replaceAll("[\\r\\n]+", " ");
                    if (safe.length() > 160) safe = safe.substring(0, 160);
                    if (safe.isEmpty() || safe.startsWith("/")) return;
                    minecraft.getConnection().sendChat("[Kurumi] " + safe);
                }));
    }

    private static void notice(String text) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null) minecraft.player.displayClientMessage(Component.literal("[Kurumi] " + text), false);
    }

    private KurumiClientController() {}
}
