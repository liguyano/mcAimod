package com.liguyano.mcaimod.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class KurumiVoiceEndpointTest {
    @Test void acceptsCpolarHttpsAndLocalHttp() {
        assertEquals("https://kurumivoice.cpolar.top/v1/audio/speech",
                KurumiVoiceEndpoint.resolve("https://kurumivoice.cpolar.top", "/v1/audio/speech", "").toString());
        assertEquals("http://127.0.0.1:8088/health",
                KurumiVoiceEndpoint.resolve("http://127.0.0.1:8088/", "/health", "").toString());
    }

    @Test void rejectsUnexpectedRemoteHostsAndCredentials() {
        assertThrows(IllegalArgumentException.class,
                () -> KurumiVoiceEndpoint.resolve("http://kurumivoice.cpolar.top", "/health", ""));
        assertThrows(IllegalArgumentException.class,
                () -> KurumiVoiceEndpoint.resolve("https://evil.example", "/health", ""));
        assertThrows(IllegalArgumentException.class,
                () -> KurumiVoiceEndpoint.resolve("https://user:secret@kurumivoice.cpolar.top", "/health", ""));
        assertThrows(IllegalArgumentException.class,
                () -> KurumiVoiceEndpoint.resolve("https://kurumivoice.cpolar.top.evil.example", "/health", ""));
    }
}
