package com.liguyano.mcaimod.model;

import java.net.URI;
import java.time.Duration;
import java.util.Objects;

/** Server-controlled model selection. Credentials stay server-side and are redacted from logs. */
public record ModelProfile(String name, URI baseUrl, String modelId, String apiKeyEnv,
                           Duration timeout, int maxOutputTokens, String apiKey) {
    public ModelProfile(String name, URI baseUrl, String modelId, String apiKeyEnv,
                        Duration timeout, int maxOutputTokens) {
        this(name, baseUrl, modelId, apiKeyEnv, timeout, maxOutputTokens, "");
    }

    public ModelProfile {
        Objects.requireNonNull(name);
        Objects.requireNonNull(baseUrl);
        Objects.requireNonNull(modelId);
        Objects.requireNonNull(timeout);
        apiKeyEnv = apiKeyEnv == null ? "" : apiKeyEnv;
        apiKey = apiKey == null ? "" : apiKey.strip();
        if (!name.matches("[a-zA-Z0-9_-]{1,48}") || modelId.isBlank()) {
            throw new IllegalArgumentException("Invalid model profile name or model ID");
        }
        if (!apiKeyEnv.isEmpty() && !apiKeyEnv.matches("[A-Za-z_][A-Za-z0-9_]*")) {
            throw new IllegalArgumentException("API key must be an environment variable name");
        }
        if (timeout.compareTo(Duration.ofSeconds(1)) < 0 || timeout.compareTo(Duration.ofMinutes(5)) > 0
                || maxOutputTokens < 1 || maxOutputTokens > 8192) {
            throw new IllegalArgumentException("Model timeout or output token limit out of range");
        }
        String scheme = baseUrl.getScheme();
        String host = baseUrl.getHost();
        if (host == null || baseUrl.getUserInfo() != null || baseUrl.getQuery() != null
                || baseUrl.getFragment() != null || baseUrl.getRawPath().contains("..")) {
            throw new IllegalArgumentException("Invalid model endpoint URL");
        }
        boolean local = host.equalsIgnoreCase("localhost") || host.equals("127.0.0.1")
                || host.equals("::1") || host.equals("[::1]");
        if (!"https".equalsIgnoreCase(scheme) && !("http".equalsIgnoreCase(scheme) && local)) {
            throw new IllegalArgumentException("Model endpoint requires HTTPS or local loopback HTTP");
        }
    }

    public URI chatCompletionsUri() {
        String path = baseUrl.toString().replaceAll("/+$", "");
        return URI.create(path + "/chat/completions");
    }

    @Override
    public String toString() {
        return "ModelProfile[name=" + name + ", baseUrl=" + baseUrl + ", modelId=" + modelId
                + ", apiKeyEnv=" + apiKeyEnv + ", timeout=" + timeout
                + ", maxOutputTokens=" + maxOutputTokens
                + ", apiKey=" + (apiKey.isEmpty() ? "<empty>" : "<redacted>") + "]";
    }
}
