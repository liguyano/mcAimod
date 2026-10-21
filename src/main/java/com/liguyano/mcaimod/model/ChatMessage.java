package com.liguyano.mcaimod.model;

import java.util.Objects;

public record ChatMessage(String role, String content) {
    public ChatMessage {
        if (!"system".equals(role) && !"user".equals(role) && !"assistant".equals(role)) {
            throw new IllegalArgumentException("Unsupported chat role");
        }
        Objects.requireNonNull(content);
    }
}
