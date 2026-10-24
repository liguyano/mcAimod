package com.liguyano.mcaimod.model;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Function;

/** OpenAI-compatible Chat Completions for OpenAI, DeepSeek and local Ollama. */
public final class OpenAiCompatibleProvider implements ModelProvider {
    private static final int MAX_REPLY_BODY_BYTES = 1024 * 1024;
    private final DashscopeMultimodalProvider dashscope = new DashscopeMultimodalProvider();
    private final HttpClient client;
    private final Function<String, String> environment;

    public OpenAiCompatibleProvider() {
        this(HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build(), System::getenv);
    }

    public OpenAiCompatibleProvider(HttpClient client, Function<String, String> environment) {
        this.client = Objects.requireNonNull(client);
        this.environment = Objects.requireNonNull(environment);
    }

    @Override
    public CompletableFuture<String> chat(ModelProfile profile, List<ChatMessage> messages) {
        if (DashscopeMultimodalProvider.supports(profile)) return dashscope.chat(profile, messages);
        final HttpRequest request;
        try {
            String key = profile.apiKey();
            if (key.isBlank() && !profile.apiKeyEnv().isBlank()) {
                key = environment.apply(profile.apiKeyEnv());
            }
            if ((key == null || key.isBlank()) && !profile.apiKeyEnv().isBlank()) {
                throw new ModelException("Missing server environment key " + profile.apiKeyEnv());
            }
            JsonObject payload = new JsonObject();
            payload.addProperty("model", profile.modelId());
            payload.addProperty("stream", false);
            payload.addProperty("max_tokens", profile.maxOutputTokens());
            JsonArray array = new JsonArray();
            for (ChatMessage message : List.copyOf(messages)) {
                JsonObject entry = new JsonObject();
                entry.addProperty("role", message.role());
                entry.addProperty("content", message.content());
                array.add(entry);
            }
            payload.add("messages", array);
            HttpRequest.Builder builder = HttpRequest.newBuilder(profile.chatCompletionsUri())
                    .timeout(profile.timeout())
                    .header("Content-Type", "application/json; charset=utf-8")
                    .POST(HttpRequest.BodyPublishers.ofString(payload.toString(), StandardCharsets.UTF_8));
            if (key != null && !key.isBlank()) builder.header("Authorization", "Bearer " + key);
            request = builder.build();
        } catch (RuntimeException error) {
            return CompletableFuture.failedFuture(error instanceof ModelException ? error : new ModelException("Invalid model request"));
        }

        return client.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray())
                .orTimeout(profile.timeout().toMillis() + 500L, java.util.concurrent.TimeUnit.MILLISECONDS)
                .handle((response, error) -> {
                    if (error != null)
                        throw new CompletionException(new ModelException(networkDiagnostic(error)));
                    int code = response.statusCode();
                    if (code == 401 || code == 403) throw new CompletionException(new ModelException("Model authorization failed (HTTP " + code + ")"));
                    if (code == 429) throw new CompletionException(new ModelException("Model rate limit (HTTP 429)"));
                    if (code < 200 || code >= 300) throw new CompletionException(new ModelException("Model HTTP " + code));
                    if (response.body().length > MAX_REPLY_BODY_BYTES) throw new CompletionException(new ModelException("Model response too large"));
                    try {
                        JsonObject root = JsonParser.parseString(new String(response.body(), StandardCharsets.UTF_8)).getAsJsonObject();
                        JsonObject choice = root.getAsJsonArray("choices").get(0).getAsJsonObject();
                        JsonElement finish = choice.get("finish_reason");
                        String finishReason = finish == null || finish.isJsonNull() ? "" : finish.getAsString();
                        JsonElement content = choice.getAsJsonObject("message").get("content");
                        String reply = content == null || content.isJsonNull() ? "" : content.getAsString().strip();
                        if ("length".equalsIgnoreCase(finishReason))
                            throw new ModelException("Model output truncated (finish_reason=length); increase max_output_tokens or shorten the task");
                        if (reply.isEmpty())
                            throw new ModelException("Model returned empty content (finish_reason=" + safeFinishReason(finishReason) + ")");
                        return reply;
                    } catch (ModelException known) {
                        throw new CompletionException(known);
                    } catch (RuntimeException malformed) {
                        throw new CompletionException(new ModelException("Invalid model response JSON or missing choices/message/content"));
                    }
                });
    }

    /** Safe classification only: never pass network exception messages or response bodies to the UI. */
    static String networkDiagnostic(Throwable error) {
        Throwable root = error;
        for (int i = 0; i < 12 && root.getCause() != null && root.getCause() != root; i++)
            root = root.getCause();
        String kind = root.getClass().getSimpleName();
        if (root instanceof java.net.http.HttpTimeoutException
                || root instanceof java.net.SocketTimeoutException
                || root instanceof java.util.concurrent.TimeoutException)
            return "Model request timed out (" + kind + ")";
        if (root instanceof java.net.UnknownHostException)
            return "Model DNS lookup failed (" + kind + ")";
        if (root instanceof java.net.ConnectException)
            return "Model connection failed (" + kind + ")";
        if (root instanceof javax.net.ssl.SSLException)
            return "Model TLS/SSL failed (" + kind + ")";
        if (root instanceof java.io.EOFException)
            return "Model connection closed early (" + kind + ")";
        if (root instanceof java.io.IOException)
            return "Model network I/O failed (" + kind + ")";
        if (root instanceof java.util.concurrent.CancellationException)
            return "Model request cancelled (" + kind + ")";
        return "Model request failed (" + (kind.isBlank() ? "unknown" : kind) + ")";
    }

    private static String safeFinishReason(String value) {
        return value.matches("[a-zA-Z0-9_-]{1,48}") ? value : "unknown";
    }
}
