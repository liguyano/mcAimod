package com.liguyano.mcaimod.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.neoforged.fml.loading.FMLPaths;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.ArrayBlockingQueue;

/** Connects to an independently running loopback Irodori-TTS or AivisSpeech server. */
public final class KurumiVoiceManager {
    private static final ArrayBlockingQueue<String> QUEUE = new ArrayBlockingQueue<>(5);
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3)).build();
    private static volatile boolean started;
    private static volatile long epoch;
    private static volatile Clip playback;

    private static JsonObject settings() {
        Path config = FMLPaths.CONFIGDIR.get().resolve("mcaimod-voice.json");
        try {
            if (Files.isRegularFile(config))
                return JsonParser.parseString(Files.readString(config, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (Exception ex) {
            KurumiWebMonitor.log("语音配置读取失败：" + ex.getMessage());
        }
        return new JsonObject();
    }

    private static String value(JsonObject json, String key, String fallback) {
        try { if (json.has(key) && !json.get(key).isJsonNull()) return json.get(key).getAsString(); }
        catch (RuntimeException ignored) { }
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
        } catch (Exception ex) {
            KurumiWebMonitor.log("语音设置保存失败：" + ex.getMessage());
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
        String text = message.strip();
        if (text.length() > 200) text = text.substring(0, 200);
        if (!QUEUE.offer(text)) { QUEUE.poll(); QUEUE.offer(text); }
    }

    public static void test() {
        speak("ご主人様、くるみの声が聞こえますか？ えへへっ♪");
    }

    /** Queue a Web-panel test using exactly the same synthesis and playback path as Kurumi dialogue. */
    public static void preview(String text) {
        if (text == null || text.isBlank() || text.length() > 200)
            throw new IllegalArgumentException("请输入 1 至 200 字符的试听文本");
        speak(text);
    }

    public static synchronized void stop() {
        epoch++;
        QUEUE.clear();
        Clip current = playback;
        if (current != null) { current.stop(); current.close(); playback = null; }
    }

    private static URI endpoint(JsonObject config, String path, String params) {
        String provider = value(config, "provider", "irodori");
        String base = value(config, "server_url", provider.equals("aivis")
                ? "http://127.0.0.1:10101" : "https://kurumivoice.cpolar.top");
        return KurumiVoiceEndpoint.resolve(base, path, params);
    }

    private static byte[] synthesizeAivis(String text, JsonObject config) throws Exception {
        String speaker = value(config, "speaker_id", "0");
        if (!speaker.matches("[0-9]{1,10}")) throw new IllegalArgumentException("speaker_id 必须是声线样式数字 ID");
        String params = "?speaker=" + speaker;
        URI queryUri = endpoint(config, "/audio_query", params + "&text="
                + URLEncoder.encode(text, StandardCharsets.UTF_8));
        HttpRequest query = HttpRequest.newBuilder(queryUri).timeout(Duration.ofSeconds(15))
                .POST(HttpRequest.BodyPublishers.noBody()).build();
        HttpResponse<String> queryResult = HTTP.send(query, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (queryResult.statusCode() != 200) throw new IOException("语音查询 HTTP " + queryResult.statusCode());
        JsonObject audioQuery = JsonParser.parseString(queryResult.body()).getAsJsonObject();
        float speed = Float.parseFloat(value(config, "speed", "1.0"));
        if (!Float.isFinite(speed) || speed < 0.5f || speed > 2.0f) throw new IllegalArgumentException("语速须在 0.5 至 2.0 之间");
        audioQuery.addProperty("speedScale", speed);
        HttpRequest synthesis = HttpRequest.newBuilder(endpoint(config, "/synthesis", params))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(45))
                .POST(HttpRequest.BodyPublishers.ofString(audioQuery.toString(), StandardCharsets.UTF_8)).build();
        HttpResponse<byte[]> response = HTTP.send(synthesis, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() != 200) throw new IOException("语音合成 HTTP " + response.statusCode());
        return validateWav(response.body());
    }

    private static byte[] synthesizeIrodori(String text, JsonObject config) throws Exception {
        float speed = Float.parseFloat(value(config, "speed", "1.0"));
        JsonObject requestBody = IrodoriSpeechRequest.build(text,
                value(config, "model", "irodori-tts"), value(config, "voice", "none"), speed,
                value(config, "caption", "明るく可愛らしい日本のアニメ少女の声。親しみやすく自然な話し方。"));
        HttpRequest request = HttpRequest.newBuilder(endpoint(config, "/v1/audio/speech", ""))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(300))
                .POST(HttpRequest.BodyPublishers.ofString(requestBody.toString(), StandardCharsets.UTF_8)).build();
        HttpResponse<byte[]> response = HTTP.send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() != 200) throw new IOException("Irodori 合成 HTTP " + response.statusCode()
                + "：" + new String(response.body(), 0, Math.min(240, response.body().length), StandardCharsets.UTF_8));
        return validateWav(response.body());
    }

    private static byte[] synthesize(String text, JsonObject config) throws Exception {
        return switch (value(config, "provider", "irodori")) {
            case "irodori" -> synthesizeIrodori(text, config);
            case "aivis" -> synthesizeAivis(text, config);
            default -> throw new IllegalArgumentException("未知语音后端，支持 irodori 或 aivis");
        };
    }

    private static byte[] validateWav(byte[] bytes) throws IOException {
        if (bytes.length < 44 || bytes.length > 8_000_000 || bytes[0] != 'R' || bytes[1] != 'I'
                || bytes[2] != 'F' || bytes[3] != 'F') throw new IOException("语音合成响应不是有效 WAV 或超过8MB");
        return bytes;
    }

    private static void playWav(byte[] wav, long generation) {
        try (AudioInputStream stream = AudioSystem.getAudioInputStream(new ByteArrayInputStream(wav))) {
            Clip clip = AudioSystem.getClip();
            try {
                playback = clip;
                clip.open(stream);
                if (generation == epoch) {
                    clip.start();
                    while (clip.isRunning() && generation == epoch) Thread.sleep(40);
                }
            } finally {
                clip.stop();
                clip.close();
            }
        } catch (Exception ex) {
            KurumiWebMonitor.log("本地播放不可用，仍可在 Web 面板播放：" + ex.getMessage());
        } finally { playback = null; }
    }

    private static void work() {
        while (!Thread.currentThread().isInterrupted()) {
            String text = null;
            try {
                text = QUEUE.take();
                long generation = epoch;
                JsonObject config = settings();
                byte[] wav = synthesize(text, config);
                if (generation != epoch) continue;
                KurumiWebMonitor.publishAudio(text, wav, "audio/wav");
                if (enabled() && Boolean.parseBoolean(value(config, "local_playback", "true"))) playWav(wav, generation);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception ex) {
                KurumiWebMonitor.log("本地语音失败：" + ex.getMessage());
                if (text != null) KurumiWebMonitor.publishVoiceError(text, ex.getMessage());
            }
        }
    }

    private KurumiVoiceManager() {}
}
