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
        final HttpRequest request;
        try {
            String key = profile.apiKeyEnv().isBlank() ? "" : environment.apply(profile.apiKeyEnv());
            if (!profile.apiKeyEnv().isBlank() && (key == null || key.isBlank())) {
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
                    if (error != null) throw new CompletionException(new ModelException("Model network error or timeout"));
                    int code = response.statusCode();
                    if (code == 401 || code == 403) throw new CompletionException(new ModelException("Model authorization failed (HTTP " + code + ")"));
                    if (code == 429) throw new CompletionException(new ModelException("Model rate limit (HTTP 429)"));
                    if (code < 200 || code >= 300) throw new CompletionException(new ModelException("Model HTTP " + code));
                    if (response.body().length > MAX_REPLY_BODY_BYTES) throw new CompletionException(new ModelException("Model response too large"));
                    try {
                        JsonObject root = JsonParser.parseString(new String(response.body(), StandardCharsets.UTF_8)).getAsJsonObject();
                        JsonElement content = root.getAsJsonArray("choices").get(0).getAsJsonObject()
                                .getAsJsonObject("message").get("content");
                        String reply = content.getAsString().strip();
                        if (reply.isEmpty()) throw new IllegalArgumentException("empty");
                        return reply;
                    } catch (RuntimeException malformed) {
                        throw new CompletionException(new ModelException("Invalid or empty model response"));
                    }
                });
    }
}
