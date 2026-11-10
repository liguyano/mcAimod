package com.liguyano.mcaimod.client;

import java.time.LocalTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/** Recent diagnostic events, independent of task lifecycle. */
final class KurumiRecentLog {
    private final int capacity;
    private final Deque<String> values = new ArrayDeque<>();

    KurumiRecentLog(int capacity) {
        if (capacity < 1) throw new IllegalArgumentException("capacity must be positive");
        this.capacity = capacity;
    }

    synchronized void add(String message) {
        if (message == null || message.isBlank()) return;
        values.addLast(LocalTime.now().withNano(0) + "  " + message);
        while (values.size() > capacity) values.removeFirst();
    }

    synchronized List<String> entries() {
        return new ArrayList<>(values);
    }
}
