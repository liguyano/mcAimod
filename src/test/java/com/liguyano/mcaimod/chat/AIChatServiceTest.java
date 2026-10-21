package com.liguyano.mcaimod.chat;

import com.liguyano.mcaimod.context.ActorSnapshot;
import com.liguyano.mcaimod.model.ChatMessage;
import com.liguyano.mcaimod.model.ModelProfile;
import com.liguyano.mcaimod.model.ModelProfiles;
import com.liguyano.mcaimod.model.ModelProvider;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.*;

class AIChatServiceTest {
    private final Instant now = Instant.parse("2026-10-21T08:00:00Z");
    private final Clock clock = Clock.fixed(now, ZoneOffset.UTC);

    private AIChatService service(ModelProvider provider) {
        ModelProfile model = new ModelProfile("local", URI.create("http://127.0.0.1:11434/v1"),
                "test", "", Duration.ofSeconds(2), 100);
        return new AIChatService(new ConversationManager(),
                new ModelProfiles(Map.of("local", model), false), provider, clock);
    }

    private ActorSnapshot snapshot(UUID actor) {
        return new ActorSnapshot(actor, "player", now, "minecraft:overworld", 0, 60, 0,
                "minecraft:plains", "clear", 100, 20, 20, 20,
                "minecraft:air", "minecraft:air", List.of(), List.of(), List.of());
    }

    @Test
    void rejectsBadInputBeforeNetwork() {
        AIChatService chat = service((profile, messages) -> fail("Network must not be called"));
        UUID actor = UUID.randomUUID();
        for (String input : List.of("", "  ", "x".repeat(1001))) {
            assertThrows(CompletionException.class, () -> chat.ask(actor, snapshot(actor), input).toCompletableFuture().join());
        }
    }

    @Test
    void keepsBusyAndCancelledRepliesOutOfHistory() {
        CompletableFuture<String> response = new CompletableFuture<>();
        AIChatService chat = service((profile, messages) -> response);
        UUID actor = UUID.randomUUID();
        CompletableFuture<String> first = chat.ask(actor, snapshot(actor), "hi").toCompletableFuture();
        assertThrows(CompletionException.class,
                () -> chat.ask(actor, snapshot(actor), "again").toCompletableFuture().join());
        chat.clear(actor);
        response.complete("stale reply");
        assertThrows(CompletionException.class, first::join);
        assertEquals(0, chat.history(actor).size());
    }

    @Test
    void successfulReplyPersistsAndRateLimitsSameActor() {
        AIChatService chat = service((profile, messages) -> CompletableFuture.completedFuture("hello"));
        UUID actor = UUID.randomUUID();
        assertEquals("hello", chat.ask(actor, snapshot(actor), "hi").toCompletableFuture().join());
        assertEquals(1, chat.history(actor).size());
        assertThrows(CompletionException.class,
                () -> chat.ask(actor, snapshot(actor), "two").toCompletableFuture().join());
        UUID other = UUID.randomUUID();
        assertEquals("hello", chat.ask(other, snapshot(other), "other").toCompletableFuture().join());
    }

    @Test
    void modelSwitchingIsExplicitlyValidated() {
        AIChatService chat = service((profile, messages) -> CompletableFuture.completedFuture("ok"));
        UUID actor = UUID.randomUUID();
        assertThrows(IllegalArgumentException.class, () -> chat.selectProfile(actor, "not-allowed"));
        chat.selectProfile(actor, "local");
        assertTrue(chat.status(actor).contains("local"));
    }
}
