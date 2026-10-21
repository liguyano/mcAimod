package com.liguyano.mcaimod.model;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public interface ModelProvider {
    CompletableFuture<String> chat(ModelProfile profile, List<ChatMessage> messages);
}
