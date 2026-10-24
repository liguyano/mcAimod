package com.liguyano.mcaimod.client;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/** Client-thread owned mailbox and bounded model context for one active agent session. */
public final class KurumiAgentSession {
    public record Message(String sender, String content) {}
    private static final int MAX_PENDING = 64;
    private static final int MAX_RECENT = 12;
    private static final int MAX_ARCHIVE = 200;
    private final Deque<Message> pending = new ArrayDeque<>();
    private final Deque<String> recent = new ArrayDeque<>();
    private final List<String> archive = new ArrayList<>();

    public void enqueue(String sender, String message) {
        if (pending.size() == MAX_PENDING) {
            // Do not silently lose requests: compact the oldest message into context.
            Message old = pending.removeFirst();
            recordObservation("排队消息（队列容量已满）", old.sender() + ": " + old.content());
        }
        pending.addLast(new Message(sender, message));
    }

    public List<Message> drainMessages() {
        List<Message> out = new ArrayList<>(pending);
        pending.clear();
        return out;
    }

    public boolean finishIfIdle() { return pending.isEmpty(); }
    public int queuedCount() { return pending.size(); }

    public String observation(int index) {
        if (index < 0 || index >= archive.size())
            throw new IllegalArgumentException("观察记录索引不存在");
        String entry = archive.get(index);
        int colon = entry.indexOf(": ");
        return colon < 0 ? entry : entry.substring(colon + 2);
    }

    public void recordObservation(String title, String result) {
        String entry = title + ": " + (result == null ? "null" : result);
        archive.add(entry);
        if (archive.size() > MAX_ARCHIVE) archive.remove(0);
        recent.addLast(entry.length() > 4000 ? entry.substring(0, 4000) + " [详情保存在工具记录]" : entry);
        while (recent.size() > MAX_RECENT) recent.removeFirst();
    }

    public String buildModelContext() {
        StringBuilder result = new StringBuilder("最近真实工具反馈（早期结果可按需再次查询）：\n");
        for (String s : recent) {
            if (result.length() + s.length() > 12000) break;
            result.append(s).append('\n');
        }
        if (archive.size() > recent.size()) {
            result.append("较旧反馈可调用 observe(get_agent_observation(编号)) 再次完整查看，索引：");
            for (int i = 0; i < archive.size() - recent.size(); i++) {
                String value = archive.get(i);
                result.append('[').append(i).append(':')
                        .append(value, 0, Math.min(45, value.length())).append("] ");
                if (result.length() > 14500) break;
            }
        }
        return result.toString();
    }
}
