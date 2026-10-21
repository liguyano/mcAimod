package com.liguyano.mcaimod.chat;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ConversationManagerTest {
    @Test
    void historiesAndSelectionsAreIsolatedAndBounded() {
        ConversationManager conversations = new ConversationManager();
        UUID alice = UUID.randomUUID();
        UUID bob = UUID.randomUUID();
        for (int i = 0; i < 13; i++) {
            conversations.append(alice, new ChatTurn("question " + i, "answer " + i));
        }
        assertEquals(10, conversations.history(alice).size());
        assertEquals("question 3", conversations.history(alice).getFirst().user());
        assertTrue(conversations.history(bob).isEmpty());
        conversations.selectProfile(alice, "deepseek");
        conversations.selectProfile(bob, "ollama");
        assertEquals("deepseek", conversations.selectedProfile(alice));
        assertEquals("ollama", conversations.selectedProfile(bob));
        assertThrows(UnsupportedOperationException.class, () -> conversations.history(alice).clear());
    }

    @Test
    void allowsOneInflightPerPlayerAndInvalidatesOnClear() {
        ConversationManager conversations = new ConversationManager();
        UUID alice = UUID.randomUUID();
        UUID bob = UUID.randomUUID();
        assertTrue(conversations.tryBegin(alice));
        assertFalse(conversations.tryBegin(alice));
        assertTrue(conversations.tryBegin(bob));
        conversations.clear(alice);
        assertFalse(conversations.isActive(alice));
        assertTrue(conversations.tryBegin(alice));
        conversations.finish(alice);
        assertFalse(conversations.isActive(alice));
    }
}
