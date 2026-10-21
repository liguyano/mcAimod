package com.liguyano.mcaimod.chat;

import com.liguyano.mcaimod.context.ActorSnapshot;
import com.liguyano.mcaimod.model.ModelProfile;
import com.liguyano.mcaimod.model.ModelProfiles;
import com.liguyano.mcaimod.model.ModelProvider;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/** Pure-Java coordinator; no Minecraft world object crosses the HTTP boundary. */
public final class AIChatService {
    private static final Duration COOLDOWN = Duration.ofSeconds(4);
    private static final int MAX_PROMPT_LENGTH = 1000;
    private final ConversationManager conversations;
    private final ModelProfiles profiles;
    private final ModelProvider provider;
    private final Clock clock;
    private final Map<UUID, Instant> lastRequests = new HashMap<>();

    public AIChatService(ConversationManager conversations, ModelProfiles profiles, ModelProvider provider) {
        this(conversations, profiles, provider, Clock.systemUTC());
    }

    public AIChatService(ConversationManager conversations, ModelProfiles profiles, ModelProvider provider,
                         Clock clock) {
        this.conversations = Objects.requireNonNull(conversations);
        this.profiles = Objects.requireNonNull(profiles);
        this.provider = Objects.requireNonNull(provider);
        this.clock = Objects.requireNonNull(clock);
    }

    public synchronized CompletionStage<String> ask(UUID actorId, ActorSnapshot snapshot, String text) {
        if (text == null || text.isBlank() || text.length() > MAX_PROMPT_LENGTH) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("消息长度必须为 1–1000 个字符"));
        }
        if (!snapshot.actorId().equals(actorId)) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("玩家状态不匹配"));
        }
        ModelProfile model = selectedModel(actorId);
        if (model == null) {
            return CompletableFuture.failedFuture(new IllegalStateException("没有可用模型，请先配置服务端 config/mcaimod-models.json"));
        }
        if (conversations.isActive(actorId)) {
            return CompletableFuture.failedFuture(new IllegalStateException("上一个 AI 请求仍在进行中"));
        }
        Instant now = clock.instant();
        Instant last = lastRequests.get(actorId);
        if (last != null && now.isBefore(last.plus(COOLDOWN))) {
            return CompletableFuture.failedFuture(new IllegalStateException("请求过于频繁，请等待 4 秒"));
        }
        if (!conversations.tryBegin(actorId)) {
            return CompletableFuture.failedFuture(new IllegalStateException("AI 正在回复"));
        }
        lastRequests.put(actorId, now);
        long generation = conversations.generation(actorId);
        try {
            var messages = PromptBuilder.build(snapshot, conversations.history(actorId), text,
                    profiles.shareExactCoordinates());
            return provider.chat(model, messages).thenApply(reply -> {
                if (reply == null || reply.isBlank()) throw new IllegalStateException("模型返回空内容");
                if (!conversations.appendIfCurrent(actorId, generation, new ChatTurn(text, reply))) {
                    throw new IllegalStateException("玩家会话已经结束，本次旧回复已丢弃");
                }
                return reply;
            }).whenComplete((reply, error) -> conversations.finishIfCurrent(actorId, generation));
        } catch (RuntimeException error) {
            conversations.finishIfCurrent(actorId, generation);
            return CompletableFuture.failedFuture(new IllegalStateException("模型请求初始化失败"));
        }
    }

    public synchronized void clear(UUID actorId) {
        conversations.clear(actorId);
        lastRequests.remove(actorId);
    }

    public synchronized void disconnect(UUID actorId) {
        conversations.disconnect(actorId);
        lastRequests.remove(actorId);
    }

    public List<ChatTurn> history(UUID actorId) {
        return conversations.history(actorId);
    }

    public void selectProfile(UUID actorId, String name) {
        if (profiles.get(name).isEmpty()) throw new IllegalArgumentException("未启用的模型配置：" + name);
        conversations.selectProfile(actorId, name);
    }

    public Set<String> availableProfiles() {
        return profiles.names();
    }

    public String status(UUID actorId) {
        ModelProfile model = selectedModel(actorId);
        return "模型：" + (model == null ? "未配置" : model.name())
                + "；记忆轮数：" + conversations.history(actorId).size()
                + "；请求中：" + conversations.isActive(actorId)
                + "；精确坐标共享：" + (profiles.shareExactCoordinates() ? "开启" : "关闭");
    }

    private ModelProfile selectedModel(UUID actorId) {
        String selected = conversations.selectedProfile(actorId);
        return selected == null ? profiles.first().orElse(null) : profiles.get(selected).orElse(null);
    }
}
