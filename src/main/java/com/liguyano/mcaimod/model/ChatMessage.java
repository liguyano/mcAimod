package com.liguyano.mcaimod.model;

import java.util.Objects;

public record ChatMessage(String role, String content, String imageDataUri) {
    public ChatMessage(String role, String content) {
        this(role, content, null);
    }
    public ChatMessage {
        if (!"system".equals(role) && !"user".equals(role) && !"assistant".equals(role)) {
            throw new IllegalArgumentException("Unsupported chat role");
        }
        Objects.requireNonNull(content);
        if (imageDataUri != null && (!"user".equals(role)
                || !imageDataUri.startsWith("data:image/jpeg;base64,")
                || imageDataUri.length() > 3_000_000))
            throw new IllegalArgumentException("Invalid image attachment");
    }
}
