package com.liguyano.mcaimod.model;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ChatMessageImageTest {
    @Test void optionalImageDoesNotBreakTextMessages() {
        assertNull(new ChatMessage("user", "hello").imageDataUri());
        assertNotNull(new ChatMessage("user", "describe", "data:image/jpeg;base64,YQ==").imageDataUri());
        assertThrows(IllegalArgumentException.class,
                () -> new ChatMessage("assistant", "reply", "data:image/jpeg;base64,YQ=="));
    }
}
