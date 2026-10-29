package com.liguyano.mcaimod.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import net.neoforged.fml.loading.FMLPaths;

/** Local-only nonblocking Edge TTS. Python + edge-tts + pygame are user-installed. */
public final class KurumiVoiceManager {
    private static final ArrayBlockingQueue<String> QUEUE = new ArrayBlockingQueue<>(5);
    private static volatile Process playback;
    private static volatile boolean started;
    private static volatile long epoch;
    private static volatile Path pythonScript;

    private static JsonObject settings() {
        Path config = FMLPaths.CONFIGDIR.get().resolve("mcaimod-voice.json");
        try {
            if (Files.isRegularFile(config))
                return JsonParser.parseString(Files.readString(config, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (Exception ex) {
            System.err.println("Kurumi voice config: " + ex.getMessage());
        }
        return new JsonObject();
    }

    private static String value(JsonObject json, String key, String fallback) {
        try {
            if (json.has(key) && !json.get(key).isJsonNull()) return json.get(key).getAsString();
        } catch (Exception ignored) { }
        return fallback;
    }

    public static boolean enabled() {
        JsonObject config = settings();
        return !config.has("enabled") || config.get("enabled").getAsBoolean();
    }

    public static void setEnabled(boolean enabled) {
        Path file = FMLPaths.CONFIGDIR.get().resolve("mcaimod-voice.json");
        try {
            JsonObject config = settings();
            config.addProperty("enabled", enabled);
            Files.createDirectories(file.getParent());
            Files.writeString(file, config.toString(), StandardCharsets.UTF_8);
            // Disabling local speakers does not stop remote MP3 delivery.
        } catch (Exception ex) {
            System.err.println("Kurumi voice save: " + ex.getMessage());
        }
    }

    public static synchronized void install() {
        if (started) return;
        started = true;
        Thread worker = new Thread(KurumiVoiceManager::work, "Kurumi-Local-TTS");
        worker.setDaemon(true);
        worker.start();
    }

    public static void speak(String message) {
        if (message == null || message.isBlank() || message.startsWith("/")) return;
        install();
        String trimmed = message.strip();
        if (trimmed.length() > 200) trimmed = trimmed.substring(0, 200);
        // Never block game execution; discard an older pending line if the queue is full.
        if (!QUEUE.offer(trimmed)) {
            QUEUE.poll();
            QUEUE.offer(trimmed);
        }
    }

    public static void test() {
        install();
        if (!QUEUE.offer("ご主人さま〜！ くるみの声、聞こえますか？ えへへっ♪")) {
            QUEUE.poll();
            QUEUE.offer("ご主人さま〜！ くるみの声、聞こえますか？ えへへっ♪");
        }
    }

    public static synchronized void stop() {
        epoch++;
        QUEUE.clear();
        Process p = playback;
        if (p != null && p.isAlive()) p.destroyForcibly();
    }

    private static Path scriptPath() throws IOException {
        if (pythonScript != null && Files.isRegularFile(pythonScript)) return pythonScript;
        try (InputStream in = KurumiVoiceManager.class.getResourceAsStream("/kurumi_tts.py")) {
            if (in == null) throw new IOException("Mod 缺少内置 kurumi_tts.py");
            pythonScript = Files.createTempFile("kurumi-voice-", ".py");
            Files.copy(in, pythonScript, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            pythonScript.toFile().deleteOnExit();
            return pythonScript;
        }
    }

    private static void work() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                String message = QUEUE.take();
                long generation = epoch;
                JsonObject config = settings();
                String voice = message.matches(".*[\\u3040-\\u30ff\\u31f0-\\u31ff].*")
                        ? value(config, "voice_ja", "ja-JP-NanamiNeural")
                        : value(config, "voice_zh", "zh-CN-XiaoyiNeural");
                Path agentPython = FMLPaths.CONFIGDIR.get().resolve("mcaimod-agent")
                        .resolve(".venv").resolve("Scripts").resolve("python.exe");
                String defaultPython = Files.isRegularFile(agentPython)
                        ? agentPython.toAbsolutePath().toString() : "py";
                String python = value(config, "python_command", defaultPython);
                Path audio = Files.createTempFile("kurumi-audio-", ".mp3");
                ProcessBuilder builder = new ProcessBuilder(python, scriptPath().toString(), message,
                        voice, value(config, "rate", "+12%"), value(config, "pitch", "+35Hz"),
                        value(config, "volume", "0.8"), audio.toString(), enabled() ? "1" : "0");
                builder.redirectErrorStream(true);
                Path output = Files.createTempFile("kurumi-tts-log-", ".txt");
                builder.redirectOutput(output.toFile());
                Process process;
                try { process = builder.start(); }
                catch (IOException error) { Files.deleteIfExists(audio); throw error; }
                playback = process;
                if (!process.waitFor(45, TimeUnit.SECONDS)) {
                    process.destroyForcibly();
                    System.err.println("Kurumi voice timeout");
                } else if (process.exitValue() == 0 && generation == epoch) {
                    try {
                        if (Files.size(audio) <= 2_000_000)
                            KurumiWebMonitor.publishAudio(message, Files.readAllBytes(audio));
                    } catch (IOException e) { System.err.println("Kurumi MP3 publish failed: " + e.getMessage()); }
                } else if (process.exitValue() != 0 && generation == epoch) {
                    String log = Files.readString(output, StandardCharsets.UTF_8);
                    System.err.println("Kurumi voice error: " + log.substring(0, Math.min(log.length(), 1000)));
                }
                if (playback == process) playback = null;
                Files.deleteIfExists(output);
                Files.deleteIfExists(audio);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception ex) {
                System.err.println("Kurumi voice unavailable: " + ex.getMessage());
            }
        }
    }

    private KurumiVoiceManager() {}
}
