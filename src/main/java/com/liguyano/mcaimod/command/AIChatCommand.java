package com.liguyano.mcaimod.command;

import com.liguyano.mcaimod.chat.AIChatService;
import com.liguyano.mcaimod.context.GameContextCollector;
import com.liguyano.mcaimod.model.ModelException;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.util.UUID;
import java.util.concurrent.CompletionException;

/** The only Minecraft-facing part of the chat pipeline. */
public final class AIChatCommand {
    private final AIChatService service;

    public AIChatCommand(AIChatService service) {
        this.service = service;
    }

    public void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("ai")
                .requires(source -> source.getEntity() instanceof ServerPlayer)
                .then(Commands.literal("clear")
                        .executes(context -> {
                            ServerPlayer player = context.getSource().getPlayerOrException();
                            service.clear(player.getUUID());
                            reply(player, "已清空聊天历史；未完成的旧回复将被忽略。");
                            return 1;
                        }))
                .then(Commands.literal("status")
                        .executes(context -> {
                            ServerPlayer player = context.getSource().getPlayerOrException();
                            reply(player, service.status(player.getUUID()));
                            return 1;
                        }))
                .then(Commands.literal("model")
                        .then(Commands.literal("list")
                                .executes(context -> {
                                    reply(context.getSource().getPlayerOrException(),
                                            "可用模型：" + (service.availableProfiles().isEmpty() ? "无，请检查服务端配置"
                                                    : String.join(", ", service.availableProfiles())));
                                    return 1;
                                }))
                        .then(Commands.literal("use")
                                .then(Commands.argument("profile", StringArgumentType.word())
                                        .suggests((context, builder) -> {
                                            service.availableProfiles().forEach(builder::suggest);
                                            return builder.buildFuture();
                                        })
                                        .executes(context -> {
                                            ServerPlayer player = context.getSource().getPlayerOrException();
                                            String name = StringArgumentType.getString(context, "profile");
                                            try {
                                                service.selectProfile(player.getUUID(), name);
                                                reply(player, "已切换到模型：" + name);
                                            } catch (IllegalArgumentException invalid) {
                                                reply(player, invalid.getMessage());
                                                return 0;
                                            }
                                            return 1;
                                        }))))
                .then(Commands.argument("message", StringArgumentType.greedyString())
                        .executes(context -> ask(context.getSource().getPlayerOrException(),
                                StringArgumentType.getString(context, "message")))));
    }

    public void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) service.clear(player.getUUID());
    }

    private int ask(ServerPlayer player, String message) {
        UUID uuid = player.getUUID();
        var response = service.ask(uuid, GameContextCollector.capture(player), message);
        reply(player, "正在请求 AI...");
        MinecraftServer server = ((ServerLevel) player.level()).getServer();
        response.whenComplete((answer, error) -> {
            if (server == null) return;
            server.execute(() -> {
                // A player may have disconnected or reconnected while HTTP was in flight.
                ServerPlayer online = server.getPlayerList().getPlayer(uuid);
                if (online != player) return;
                if (error != null) {
                    reply(online, friendlyError(error));
                } else {
                    String limited = answer.length() > 1800 ? answer.substring(0, 1800) + "…" : answer;
                    reply(online, limited);
                }
            });
        });
        return 1;
    }

    private static String friendlyError(Throwable error) {
        Throwable root = error;
        while (root instanceof CompletionException && root.getCause() != null) root = root.getCause();
        if (root instanceof ModelException || root instanceof IllegalStateException
                || root instanceof IllegalArgumentException) {
            return "请求失败：" + root.getMessage();
        }
        return "请求失败：模型暂时不可用，请稍后重试。";
    }

    private static void reply(ServerPlayer player, String message) {
        player.sendSystemMessage(Component.literal("[AI Companion] " + message));
    }
}
