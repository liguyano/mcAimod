package com.liguyano.mcaimod.model;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class OpenAiCompatibleProviderTest {
    private HttpServer server;
    @TempDir Path tempDir;

    @AfterEach
    void stop() {
        if (server != null) server.stop(0);
    }

    private ModelProfile profile(Duration timeout) {
        return new ModelProfile("test", URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/v1"),
                "fake-model", "TEST_KEY", timeout, 200);
    }

    @Test
    void postsValidChatCompletionAndReadsText() throws Exception {
        AtomicReference<String> request = new AtomicReference<>();
        AtomicReference<String> bearer = new AtomicReference<>();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            request.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            bearer.set(exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] answer = "{\"choices\":[{\"message\":{\"content\":\"你好！\"}}]}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, answer.length);
            exchange.getResponseBody().write(answer);
            exchange.close();
        });
        server.start();
        ModelProvider provider = new OpenAiCompatibleProvider(HttpClient.newHttpClient(), key -> "secret-test-token");

        String answer = provider.chat(profile(Duration.ofSeconds(3)),
                List.of(new ChatMessage("user", "你在哪？"))).get(4, TimeUnit.SECONDS);
        assertEquals("你好！", answer);
        assertTrue(request.get().contains("\"model\":\"fake-model\""));
        assertTrue(request.get().contains("\"role\":\"user\""));
        assertEquals("Bearer secret-test-token", bearer.get());
    }

    @Test
    void usesInlineApiKeyFromJsonInPreferenceToEnvironment() throws Exception {
        AtomicReference<String> bearer = new AtomicReference<>();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            bearer.set(exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] answer = "{\"choices\":[{\"message\":{\"content\":\"OK\"}}]}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, answer.length);
            exchange.getResponseBody().write(answer);
            exchange.close();
        });
        server.start();
        Path config = tempDir.resolve("models.json");
        Files.writeString(config, """
                {"profiles":[{"name":"aliyun","base_url":"http://127.0.0.1:%d/v1",
                  "model_id":"qwen3.8-max","api_key":"unit-test-inline-token","api_key_env":"TEST_KEY"}]}
                """.formatted(server.getAddress().getPort()));
        ModelProfile configured = ModelProfiles.load(config).get("aliyun").orElseThrow();
        ModelProvider provider = new OpenAiCompatibleProvider(HttpClient.newHttpClient(), env -> "environment-token");
        assertEquals("OK", provider.chat(configured, List.of(new ChatMessage("user", "hello")))
                .get(4, TimeUnit.SECONDS));
        assertEquals("Bearer unit-test-inline-token", bearer.get());
    }

    @Test
    void mapsUnauthorizedAndRateLimitToSafeFailures() throws Exception {
        AtomicInteger code = new AtomicInteger(401);
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            byte[] answer = "do not echo secret-test-token".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(code.get(), answer.length);
            exchange.getResponseBody().write(answer);
            exchange.close();
        });
        server.start();
        ModelProvider provider = new OpenAiCompatibleProvider(HttpClient.newHttpClient(), key -> "secret-test-token");
        for (int status : List.of(401, 429)) {
            code.set(status);
            CompletionException error = assertThrows(CompletionException.class,
                    () -> provider.chat(profile(Duration.ofSeconds(3)), List.of(new ChatMessage("user", "hi"))).join());
            assertInstanceOf(ModelException.class, error.getCause());
            assertTrue(error.getCause().getMessage().contains(Integer.toString(status)));
            assertFalse(error.getCause().getMessage().contains("secret-test-token"));
        }
    }

    @Test
    void rejectsNonLoopbackPlainHttp() {
        assertThrows(IllegalArgumentException.class,
                () -> new ModelProfile("unsafe", URI.create("http://example.org/v1"), "model", "KEY",
                        Duration.ofSeconds(3), 200));
    }

    @Test
    void rejectsMalformedResponseAndMissingSecret() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            byte[] answer = "{broken json".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, answer.length);
            exchange.getResponseBody().write(answer);
            exchange.close();
        });
        server.start();
        ModelProvider withSecret = new OpenAiCompatibleProvider(HttpClient.newHttpClient(), key -> "fake");
        Throwable error = assertThrows(CompletionException.class,
                () -> withSecret.chat(profile(Duration.ofSeconds(2)), List.of(new ChatMessage("user", "hi"))).join());
        assertTrue(error.getCause().getMessage().contains("Invalid model response JSON"));

        ModelProvider missingSecret = new OpenAiCompatibleProvider(HttpClient.newHttpClient(), key -> null);
        error = assertThrows(CompletionException.class,
                () -> missingSecret.chat(profile(Duration.ofSeconds(2)), List.of(new ChatMessage("user", "hi"))).join());
        assertTrue(error.getCause().getMessage().contains("Missing server environment key"));
    }

    @Test
    void enforcesHttpTimeout() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            try {
                Thread.sleep(2300);
                exchange.sendResponseHeaders(204, -1);
            } catch (Exception ignored) {
                // Client may time out before the handler responds.
            } finally {
                exchange.close();
            }
        });
        server.start();
        ModelProvider provider = new OpenAiCompatibleProvider(HttpClient.newHttpClient(), key -> "fake");
        Throwable error = assertThrows(CompletionException.class,
                () -> provider.chat(profile(Duration.ofSeconds(1)), List.of(new ChatMessage("user", "hi"))).join());
        assertTrue(error.getCause().getMessage().contains("timed out"));
    }
}
