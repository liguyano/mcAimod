package com.liguyano.mcaimod.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.neoforged.fml.loading.FMLPaths;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/** Supervised local CPython LangGraph process. No network listener outside loopback. */
public final class KurumiAgentProcess {
    private static final String TOKEN = newToken();
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    private static volatile Process process;

    private KurumiAgentProcess() {}

    private static String newToken() {
        byte[] data = new byte[32];
        new SecureRandom().nextBytes(data);
        return HexFormat.of().formatHex(data);
    }

    public static boolean available() {
        Process p = process;
        return p != null && p.isAlive();
    }

    public static synchronized void launch() {
        if (available()) return;
        try {
            // The Python package directory can be overridden for source checkouts.
            Path folder = Path.of(System.getProperty("mcaimod.agentDir", "python-agent")).toAbsolutePath();
            Path script = folder.resolve("kurumi_server.py");
            if (!java.nio.file.Files.isRegularFile(script)) {
                KurumiWebMonitor.log("LangGraph Agent 尚未安装: " + script);
                return;
            }
            String interpreter = System.getProperty("mcaimod.python",
                    folder.resolve(".venv").resolve("Scripts").resolve("python.exe").toString());
            ProcessBuilder builder = new ProcessBuilder(interpreter, "-u", script.toString());
            builder.directory(folder.toFile());
            Map<String, String> environment = builder.environment();
            environment.put("KURUMI_AGENT_TOKEN", TOKEN);
            environment.put("KURUMI_GAME_TOKEN", KurumiAgentToolServer.token());
            environment.put("KURUMI_MODEL_CONFIG", FMLPaths.CONFIGDIR.get()
                    .resolve("mcaimod-models.json").toAbsolutePath().toString());
            environment.put("KURUMI_AGENT_PORT", "47833");
            builder.redirectErrorStream(true);
            Process started = builder.start();
            process = started;
            Thread logger = new Thread(() -> {
                try (var lines = new java.io.BufferedReader(new java.io.InputStreamReader(
                        started.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = lines.readLine()) != null)
                        KurumiWebMonitor.log("LangGraph: " + line.replaceAll("(?i)(sk-[A-Za-z0-9_-]{12,})", "[REDACTED]"));
                } catch (Exception ignored) {}
            }, "Kurumi-LangGraph-Log");
            logger.setDaemon(true);
            logger.start();
            KurumiWebMonitor.log("LangGraph 进程已启动（初始化中）");
        } catch (Exception e) {
            KurumiWebMonitor.log("LangGraph 启动失败: " + e.getMessage());
        }
    }

    public static CompletableFuture<JsonObject> chat(String sender, String message) {
        JsonObject payload = new JsonObject();
        payload.addProperty("sender", sender);
        payload.addProperty("message", message);
        return request("/chat", payload);
    }

    public static void stop() {
        // Notify the Python agent before forcibly stopping it. No game-thread wait.
        request("/stop", new JsonObject()).exceptionally(error -> null);
    }

    public static synchronized void shutdown() {
        Process p = process;
        process = null;
        if (p != null) p.destroyForcibly();
    }

    private static CompletableFuture<JsonObject> request(String path, JsonObject payload) {
        HttpRequest req = HttpRequest.newBuilder(URI.create("http://127.0.0.1:47833" + path))
                .timeout(Duration.ofSeconds(130))
                .header("Authorization", "Bearer " + TOKEN)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload.toString(), StandardCharsets.UTF_8))
                .build();
        return HTTP.sendAsync(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
                .thenApply(reply -> {
                    if (reply.statusCode() != 200) throw new IllegalStateException("Agent HTTP " + reply.statusCode());
                    return JsonParser.parseString(reply.body()).getAsJsonObject();
                });
    }
}
