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
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.neoforged.fml.loading.FMLPaths;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.util.UUID;
import java.util.Optional;

/** Local-only multiplayer chat integration. Actions remain disabled until game-side adapters exist. */
public final class KurumiClientController {
    private static final KurumiWakeRouter GATE =
            new KurumiWakeRouter(Clock.systemUTC(), Duration.ofSeconds(5));
    private static final OpenAiCompatibleProvider PROVIDER = new OpenAiCompatibleProvider();
    private static final Map<UUID, List<ChatMessage>> HISTORY = new HashMap<>();
    private static final int MAX_TOOL_CALLS = 3;
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
        HISTORY.clear();
    }

    private static void registerCommands(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(LiteralArgumentBuilder.<CommandSourceStack>literal("kurumi")
                .then(LiteralArgumentBuilder.<CommandSourceStack>literal("on").executes(context -> {
                    enabled = true;
                    notice("已启用聊天监听（仅本机）。");
                    return 1;
                }))
                .then(LiteralArgumentBuilder.<CommandSourceStack>literal("off").executes(context -> {
                    enabled = false;
                    autoFarmEnabled = false;
                    generation++;
                    requestInFlight = false;
                    GATE.reset();
                    HISTORY.clear();
                    notice("已关闭聊天监听。");
                    return 1;
                }))
                .then(LiteralArgumentBuilder.<CommandSourceStack>literal("auto")
                        .then(LiteralArgumentBuilder.<CommandSourceStack>literal("on").executes(context -> {
                            autoFarmEnabled = true;
                            notice("已允许公共聊天触发 Baritone 农作（任何玩家均可触发）。");
                            return 1;
                        }))
                        .then(LiteralArgumentBuilder.<CommandSourceStack>literal("off").executes(context -> {
                            autoFarmEnabled = false;
                            notice("已禁止公共聊天触发农作。");
                            return 1;
                        })))
                .then(LiteralArgumentBuilder.<CommandSourceStack>literal("farm").executes(context -> {
                    BaritoneBridge.Result result = BaritoneBridge.farm(16);
                    notice(result.message());
                    return result.success() ? 1 : 0;
                }))
                .then(LiteralArgumentBuilder.<CommandSourceStack>literal("stop").executes(context -> {
                    generation++;
                    GATE.reset();
                    BaritoneBridge.Result result = BaritoneBridge.stop();
                    notice("已取消等待中的 Kurumi 请求。" + result.message());
                    return 1;
                }))
                .then(LiteralArgumentBuilder.<CommandSourceStack>literal("status").executes(context -> {
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
        UUID sender = event.getSender();
        List<ChatMessage> conversation = new ArrayList<>();
        conversation.add(new ChatMessage("system", "你是 Minecraft 中的 Kurumi。只输出 JSON 对象，不要 Markdown。type 为 chat 或 action，reply 为简短中文。动作：FARM radius=16、STOP radius=0、FOLLOW target=精确玩家名、GOTO x/y/z=完整整数坐标、MINE block_id=具体原木 minecraft:xxx_log 或菌柄 minecraft:xxx_stem。跟随我时用本条消息提供的发言者玩家名。缺少坐标或树种时询问，不能假装已执行。可以调用只读工具，输出 JSON 对象 type=tool、tool=工具名。工具名可选 get_player_status、get_environment、scan_nearby_entities、scan_nearby_blocks、get_inventory。收到工具结果后再决定下一步，最多3次调用。不要编造观察结果。不要输出其他动作。"));
        conversation.addAll(HISTORY.getOrDefault(sender, List.of()));
        String senderName = minecraft.getConnection().getPlayerInfo(sender) == null ? "未知" : minecraft.getConnection().getPlayerInfo(sender).getProfile().name();
        conversation.add(new ChatMessage("user", "发言者玩家名：" + senderName + "；消息：" + message));
        runAgent(profile.get(), conversation, sender, message, requestGeneration, 0);
    }

    private static void runAgent(ModelProfile profile, List<ChatMessage> conversation, UUID sender,
                                 String message, long requestGeneration, int toolCalls) {
        Minecraft minecraft = Minecraft.getInstance();
        PROVIDER.chat(profile, conversation)
                .whenComplete((reply, error) -> minecraft.execute(() -> {
                    if (generation != requestGeneration) return;
                    if (!enabled || generation != requestGeneration || minecraft.player == null ||
                            minecraft.getConnection() == null) return;
                    if (error != null) {
                        finishRequest();
                        notice("模型请求失败，请检查网络与模型配置。");
                        return;
                    }
                    String tool = KurumiToolRequest.parse(reply);
                    if (tool != null) {
                        if (toolCalls >= MAX_TOOL_CALLS) {
                            finishRequest();
                            notice("工具调用达到3次上限，任务停止。");
                            return;
                        }
                        List<ChatMessage> next = new ArrayList<>(conversation);
                        next.add(new ChatMessage("assistant", reply));
                        next.add(new ChatMessage("user", "工具 " + tool + " 的真实返回：" + KurumiToolbox.observe(tool)
                                + "。请据此继续判断，最后返回 chat 或 action JSON。"));
                        runAgent(profile, next, sender, message, requestGeneration, toolCalls + 1);
                        return;
                    }
                    finishRequest();
                    Optional<KurumiDecisionParser.Decision> parsed = KurumiDecisionParser.parse(reply);
                    if (parsed.isEmpty()) {
                        notice("模型没有返回有效的结构化回复，本次不执行操作。");
                        return;
                    }
                    KurumiDecisionParser.Decision decision = parsed.get();
                    if (decision.action().isPresent() || !decision.target().isEmpty() || decision.x() != null || !decision.blockId().isEmpty()) {
                        KurumiActionValidator.Action action = decision.action().orElse(null);
                        if (!autoFarmEnabled) {
                            notice("自动操作未开启，请先输入 /kurumi auto on。");
                            return;
                        }
                        BaritoneBridge.Result result = decision.action().isEmpty()
                                ? (!decision.blockId().isEmpty() ? BaritoneBridge.mineBlock(decision.blockId())
                                    : !decision.target().isEmpty() ? BaritoneBridge.followPlayer(decision.target())
                                    : BaritoneBridge.gotoCoordinates(decision.x(), decision.y(), decision.z()))
                                : switch (action.type()) {
                            case FARM -> BaritoneBridge.farm(action.radius());
                            case STOP -> BaritoneBridge.stop();
                            default -> new BaritoneBridge.Result(false, "暂不支持此操作");
                        };
                        if (!result.success()) {
                            notice(result.message());
                            return;
                        }
                    }
                    List<ChatMessage> updated = new ArrayList<>(HISTORY.getOrDefault(sender, List.of()));
                    updated.add(new ChatMessage("user", message));
                    updated.add(new ChatMessage("assistant", decision.reply()));
                    if (updated.size() > 12) updated = new ArrayList<>(updated.subList(updated.size() - 12, updated.size()));
                    HISTORY.put(sender, updated);
                    minecraft.getConnection().sendChat("[Kurumi] " + decision.reply());
                }));
    }

    private static void finishRequest() {
        requestInFlight = false;
        GATE.finish();
    }

    private static void notice(String text) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null) minecraft.player.sendSystemMessage(Component.literal("[Kurumi] " + text));
    }

    private KurumiClientController() {}
}
