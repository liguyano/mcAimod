package com.liguyano.mcaimod.chat;

import java.util.Objects;

public record ChatTurn(String user, String assistant) {
    public ChatTurn {
        Objects.requireNonNull(user);
        Objects.requireNonNull(assistant);
    }
}
