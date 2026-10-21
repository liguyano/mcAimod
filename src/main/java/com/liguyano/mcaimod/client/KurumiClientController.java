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
import net.neoforged.neoforge.client.command.ClientCommands;
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
    private static boolean autoFarmEnabled = false;
    private static long generation = 0;
    private static boolean requestInFlight = false;

    public static void install() {
        NeoForge.EVENT_BUS.addListener(KurumiClientController::onPlayerChat);
        NeoForge.EVENT_BUS.addListener(KurumiClientController::registerCommands);
        NeoForge.EVENT_BUS.addListener(KurumiClientController::onLogout);
    }

    private static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        generation++;
        requestInFlight = false;
        autoFarmEnabled = false;
        GATE.reset();
    }

    private static void registerCommands(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(ClientCommands.literal("kurumi")
                .then(ClientCommands.literal("on").executes(context -> {
                    enabled = true;
                    notice("已启用聊天监听（仅本机）。");
                    return 1;
                }))
                .then(ClientCommands.literal("off").executes(context -> {
                    enabled = false;
                    autoFarmEnabled = false;
                    generation++;
                    requestInFlight = false;
                    GATE.reset();
                    notice("已关闭聊天监听。");
                    return 1;
                }))
                .then(ClientCommands.literal("auto")
                        .then(ClientCommands.literal("on").executes(context -> {
                            autoFarmEnabled = true;
                            notice("已允许公共聊天触发 Baritone 农作（任何玩家均可触发）。");
                            return 1;
                        }))
                        .then(ClientCommands.literal("off").executes(context -> {
                            autoFarmEnabled = false;
                            notice("已禁止公共聊天触发农作。");
                            return 1;
                        })))
                .then(ClientCommands.literal("farm").executes(context -> {
                    BaritoneBridge.Result result = BaritoneBridge.farm(16);
                    notice(result.message());
                    return result.success() ? 1 : 0;
                }))
                .then(ClientCommands.literal("stop").executes(context -> {
                    generation++;
                    GATE.reset();
                    BaritoneBridge.Result result = BaritoneBridge.stop();
                    notice("已取消等待中的 Kurumi 请求。" + result.message());
                    return 1;
                }))
                .then(ClientCommands.literal("status").executes(context -> {
                    notice("聊天监听：" + (enabled ? "开启" : "关闭") + "；自动农作：" + (autoFarmEnabled ? "开启" : "关闭") + "；Baritone：" + (BaritoneBridge.available() ? "已检测到" : "未检测到") + "；动作识别：大模型");
                    return 1;
                })));
    }

    private static void onPlayerChat(ClientChatReceivedEvent.Player event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!enabled || minecraft.player == null || minecraft.getConnection() == null) return;
        if (minecraft.player.getUUID().equals(event.getSender())) return;
        String message = event.getPlayerChatMessage().signedContent();
        if (requestInFlight || !GATE.tryAccept(message)) return;
        requestInFlight = true;
        long requestGeneration = generation;
        // Acknowledgement is public and uses the player's normal chat account.

        Optional<ModelProfile> profile;
        try {
            profile = ModelProfiles.load(FMLPaths.CONFIGDIR.get().resolve("mcaimod-models.json")).first();
        } catch (Exception error) {
            requestInFlight = false;
            GATE.finish();
            notice("模型配置读取失败，请检查 config/mcaimod-models.json");
            return;
        }
        if (profile.isEmpty()) {
            requestInFlight = false;
            GATE.finish();
            notice("没有启用的模型配置。");
            return;
        }
        minecraft.getConnection().sendChat("[Kurumi] 好的，我听到了。");
        PROVIDER.chat(profile.get(), List.of(
                new ChatMessage("system", "你是 Minecraft 中的 Kurumi。只输出 JSON 对象，不要 Markdown。字段 type 为 chat 或 action，reply 为简短中文回复。仅在玩家明确要求种田或收割时，使用 type=action、action=FARM、radius=16；明确要求停止时使用 action=STOP、radius=0。其他请求用 type=chat。不要生成其他动作。"),
                new ChatMessage("user", message)))
                .whenComplete((reply, error) -> minecraft.execute(() -> {
                    if (generation != requestGeneration) return;
                    requestInFlight = false;
                    GATE.finish();
                    if (!enabled || generation != requestGeneration || minecraft.player == null ||
                            minecraft.getConnection() == null) return;
                    if (error != null) {
                        notice("模型请求失败，请检查网络与模型配置。");
                        return;
                    }
                    Optional<KurumiDecisionParser.Decision> parsed = KurumiDecisionParser.parse(reply);
                    if (parsed.isEmpty()) {
                        notice("模型没有返回有效的结构化回复，本次不执行操作。");
                        return;
                    }
                    KurumiDecisionParser.Decision decision = parsed.get();
                    if (decision.action().isPresent()) {
                        KurumiActionValidator.Action action = decision.action().get();
                        if (!autoFarmEnabled) {
                            notice("自动操作未开启，请先输入 /kurumi auto on。");
                            return;
                        }
                        BaritoneBridge.Result result = switch (action.type()) {
                            case FARM -> BaritoneBridge.farm(action.radius());
                            case STOP -> BaritoneBridge.stop();
                            default -> new BaritoneBridge.Result(false, "暂不支持此操作");
                        };
                        if (!result.success()) {
                            notice(result.message());
                            return;
                        }
                    }
                    minecraft.getConnection().sendChat("[Kurumi] " + decision.reply());
                }));
    }

    private static void notice(String text) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null) minecraft.player.displayClientMessage(Component.literal("[Kurumi] " + text), false);
    }

    private KurumiClientController() {}
}
