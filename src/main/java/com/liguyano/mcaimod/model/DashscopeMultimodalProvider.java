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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;

/**
 * Qwen multimodal conversations using the official Alibaba DashScope Java SDK.
 * The current Kurumi application sends text-only messages; SDK also supports images.
 */
public final class DashscopeMultimodalProvider implements ModelProvider {
    private static final String QWEN_ENDPOINT = "https://cn-beijing.maas.qianwenaiapi.com/api/v1";
    private static final Pattern HTTP_STATUS = Pattern.compile("(?<!\\d)(400|401|403|404|408|429|500|502|503|504)(?!\\d)");
    private static final Pattern API_CODE = Pattern.compile(
            "(?i)(?:error[_ -]?code|code)\\s*[:=]\\s*[\\\"']?([A-Za-z][A-Za-z0-9_.-]{2,55})");

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
                    List<Map<String, Object>> parts = new ArrayList<>();
                    parts.add(Collections.singletonMap("text", message.content()));
                    if (message.imageDataUri() != null)
                        parts.add(Collections.singletonMap("image", message.imageDataUri()));
                    input.add(MultiModalMessage.builder()
                            .role(message.role())
                            .content(parts)
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
            } catch (VirtualMachineError fatal) {
                throw fatal;
            } catch (Throwable ex) {
                // Show safe error classification and status/code, never credentials or raw bodies.
                String detail = diagnostic(ex, credential);
                System.err.println("[mcaimod/DashScope] " + detail);
                throw new CompletionException(new ModelException(detail));
            }
        }).orTimeout(profile.timeout().toMillis(), TimeUnit.MILLISECONDS)
                .exceptionally(error -> {
                    Throwable root = error;
                    for (int i = 0; i < 8 && root.getCause() != null && root.getCause() != root; i++)
                        root = root.getCause();
                    if (root instanceof java.util.concurrent.TimeoutException)
                        throw new CompletionException(new ModelException(
                                "DashScope SDK request timed out after " + profile.timeout().toSeconds()
                                + "s; complex generation may exceed the configured timeout"));
                    throw new CompletionException(root);
                });
    }

    /**
     * A bounded diagnostic: error class, HTTP status and API error code only.
     * Deliberately excludes raw upstream response bodies and secret-bearing URLs.
     */
    static String diagnostic(Throwable error, String credential) {
        Throwable root = error;
        for (int i = 0; i < 8 && root.getCause() != null && root.getCause() != root; i++)
            root = root.getCause();
        String type = root.getClass().getSimpleName();
        if (type.isBlank()) type = root.getClass().getName();
        String message = root.getMessage();
        if (message == null) message = "";
        if (credential != null && !credential.isBlank())
            message = message.replace(credential, "[REDACTED]");
        Matcher status = HTTP_STATUS.matcher(message);
        Matcher code = API_CODE.matcher(message);
        StringBuilder info = new StringBuilder("DashScope SDK request failed (").append(type).append(")");
        if (status.find()) info.append(" HTTP ").append(status.group(1));
        if (code.find()) info.append(" code=").append(code.group(1));
        if (root instanceof NoClassDefFoundError || root instanceof ClassNotFoundException)
            info.append("; SDK runtime dependency missing: ")
                    .append(safeClassName(message));
        if (root instanceof java.net.UnknownHostException) info.append("; DNS lookup failed");
        if (root instanceof java.net.ConnectException) info.append("; connection failed");
        if (root instanceof javax.net.ssl.SSLException) info.append("; TLS/SSL failed");
        if (root instanceof java.net.SocketTimeoutException) info.append("; network timeout");
        return info.toString();
    }

    private static String safeClassName(String detail) {
        String value = detail.replace('.', '/').trim();
        return value.matches("[A-Za-z_$][A-Za-z0-9_$/]{1,160}") ? value : "(hidden)";
    }

    public static boolean supports(ModelProfile profile) {
        return "cn-beijing.maas.qianwenaiapi.com".equalsIgnoreCase(profile.baseUrl().getHost());
    }
}
