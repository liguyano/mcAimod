package com.liguyano.mcaimod.model;

import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversation;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversationParam;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversationResult;
import com.alibaba.dashscope.common.MultiModalMessage;
import com.alibaba.dashscope.utils.Constants;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;

/**
 * Qwen multimodal conversations using the official Alibaba DashScope Java SDK.
 * The current Kurumi application sends text-only messages; SDK also supports images.
 */
public final class DashscopeMultimodalProvider implements ModelProvider {
    private static final String QWEN_ENDPOINT = "https://cn-beijing.maas.qianwenaiapi.com/api/v1";

    @Override
    public CompletableFuture<String> chat(ModelProfile profile, List<ChatMessage> messages) {
        String apiKey = profile.apiKey();
        if (apiKey.isBlank() && !profile.apiKeyEnv().isBlank()) {
            apiKey = System.getenv(profile.apiKeyEnv());
        }
        if (apiKey == null || apiKey.isBlank())
            return CompletableFuture.failedFuture(new ModelException("DashScope API key not configured"));

        final String credential = apiKey;
        return CompletableFuture.supplyAsync(() -> {
            try {
                // The user's Qwen endpoint is set explicitly for the SDK. Do not change
                // endpoints during normal chat processing for other providers.
                Constants.baseHttpApiUrl = QWEN_ENDPOINT;
                List<MultiModalMessage> input = new ArrayList<>();
                for (ChatMessage message : List.copyOf(messages)) {
                    input.add(MultiModalMessage.builder()
                            .role(message.role())
                            .content(List.of(Collections.singletonMap("text", message.content())))
                            .build());
                }
                MultiModalConversationParam param = MultiModalConversationParam.builder()
                        .apiKey(credential)
                        .model(profile.modelId())
                        .messages(input)
                        .build();
                MultiModalConversationResult result = new MultiModalConversation().call(param);
                var content = result.getOutput().getChoices().get(0).getMessage().getContent();
                StringBuilder reply = new StringBuilder();
                for (Map<String, Object> block : content) {
                    Object text = block.get("text");
                    if (text != null) reply.append(text);
                }
                if (reply.toString().isBlank()) throw new ModelException("Empty DashScope model response");
                return reply.toString().strip();
            } catch (Exception ex) {
                // Do not log SDK exception messages: upstream errors may contain credentials.
                throw new CompletionException(new ModelException(
                        "DashScope SDK request failed (" + ex.getClass().getSimpleName() + ")"));
            }
        }).orTimeout(profile.timeout().toMillis(), TimeUnit.MILLISECONDS);
    }

    public static boolean supports(ModelProfile profile) {
        return "cn-beijing.maas.qianwenaiapi.com".equalsIgnoreCase(profile.baseUrl().getHost());
    }
}
