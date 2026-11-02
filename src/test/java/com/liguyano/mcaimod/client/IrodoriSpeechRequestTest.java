package com.liguyano.mcaimod.client;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class IrodoriSpeechRequestTest {
    @Test void producesOpenAiCompatibleWaveRequest() {
        JsonObject request = IrodoriSpeechRequest.build("こんにちは、ご主人様", "irodori-tts", "none", 1.0f, "可愛らしい少女の声");
        assertEquals("こんにちは、ご主人様", request.get("input").getAsString());
        assertEquals("irodori-tts", request.get("model").getAsString());
        assertEquals("none", request.get("voice").getAsString());
        assertEquals("wav", request.get("response_format").getAsString());
        assertEquals("可愛らしい少女の声", request.getAsJsonObject("irodori").get("caption").getAsString());
    }

    @Test void rejectsInvalidSpeedAndEmptyText() {
        assertThrows(IllegalArgumentException.class, () -> IrodoriSpeechRequest.build("hi", "irodori-tts", "none", Float.NaN, ""));
        assertThrows(IllegalArgumentException.class, () -> IrodoriSpeechRequest.build(" ", "irodori-tts", "none", 1, ""));
    }
}
