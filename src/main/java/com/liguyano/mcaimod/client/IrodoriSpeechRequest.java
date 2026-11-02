package com.liguyano.mcaimod.client;

import com.google.gson.JsonObject;

/** OpenAI-compatible request body accepted by the Irodori TTS server. */
final class IrodoriSpeechRequest {
    static JsonObject build(String text, String model, String voice, float speed, String caption) {
        if (text == null || text.isBlank() || text.length() > 200)
            throw new IllegalArgumentException("语音文本必须为 1 至 200 字符");
        if (!Float.isFinite(speed) || speed < 0.5f || speed > 2.0f)
            throw new IllegalArgumentException("语速须在 0.5 至 2.0 之间");
        JsonObject request = new JsonObject();
        request.addProperty("model", model);
        request.addProperty("input", text);
        request.addProperty("voice", voice);
        request.addProperty("response_format", "wav");
        request.addProperty("speed", speed);
        if (caption != null && !caption.isBlank()) {
            JsonObject options = new JsonObject();
            options.addProperty("caption", caption);
            request.add("irodori", options);
        }
        return request;
    }
    private IrodoriSpeechRequest() {}
}
