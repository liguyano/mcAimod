package com.liguyano.mcaimod.chat;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** One short, transient session per actor, isolated by UUID. */
public final class ConversationManager {
    private static final int MAX_TURNS = 10;
    private final Map<UUID, Session> sessions = new HashMap<>();
    private final Map<UUID, Long> epochs = new HashMap<>();

    public synchronized List<ChatTurn> history(UUID actorId) {
        return List.copyOf(session(actorId).turns);
    }

    public synchronized void append(UUID actorId, ChatTurn turn) {
        Session session = session(actorId);
        session.turns.addLast(turn);
        while (session.turns.size() > MAX_TURNS) session.turns.removeFirst();
    }

    public synchronized void clear(UUID actorId) {
        epochs.merge(actorId, 1L, Long::sum);
        Session session = session(actorId);
        session.turns.clear();
        session.active = false;
    }

    public synchronized void disconnect(UUID actorId) {
        epochs.merge(actorId, 1L, Long::sum);
        sessions.remove(actorId);
    }

    public synchronized boolean tryBegin(UUID actorId) {
        Session session = session(actorId);
        if (session.active) return false;
        session.active = true;
        return true;
    }

    public synchronized void finish(UUID actorId) {
        session(actorId).active = false;
    }

    public synchronized boolean isActive(UUID actorId) {
        return session(actorId).active;
    }

    public synchronized long generation(UUID actorId) {
        return epochs.getOrDefault(actorId, 0L);
    }

    public synchronized boolean appendIfCurrent(UUID actorId, long generation, ChatTurn turn) {
        if (generation(actorId) != generation || !session(actorId).active) return false;
        append(actorId, turn);
        session(actorId).active = false;
        return true;
    }

    public synchronized void finishIfCurrent(UUID actorId, long generation) {
        if (generation(actorId) == generation) finish(actorId);
    }

    public synchronized String selectedProfile(UUID actorId) {
        return session(actorId).profile;
    }

    public synchronized void selectProfile(UUID actorId, String profile) {
        session(actorId).profile = profile;
    }

    private Session session(UUID actorId) {
        return sessions.computeIfAbsent(actorId, ignored -> new Session());
    }

    private static final class Session {
        private final ArrayDeque<ChatTurn> turns = new ArrayDeque<>();
        private boolean active;
        private String profile;
    }
}
