package com.liguyano.mcaimod.client;

import com.liguyano.mcaimod.model.ChatMessage;
import java.util.ArrayList;
import java.util.List;

/** Preserve system rules and recent turns while bounding requests to the model. */
public final class KurumiAgentContext {
    private static final int MAX_CHARS = 48000;
    private static final int MAX_RECENT = 18;

    public static List<ChatMessage> compact(List<ChatMessage> messages, String originalRequest,
                                             String currentTaskContext) {
        List<ChatMessage> system = new ArrayList<>();
        List<ChatMessage> turns = new ArrayList<>();
        for (var entry : messages) {
            if ("system".equals(entry.role())) system.add(entry);
            else turns.add(entry);
        }
        List<ChatMessage> selected = new ArrayList<>(system);
        selected.add(new ChatMessage("system", "当前持续任务的原始目标：" + originalRequest
                + "。历史详细工具结果可能已压缩；不确定时重新 observe 查询。\n" + currentTaskContext));
        int budget = MAX_CHARS - selected.stream().mapToInt(m -> m.content().length()).sum();
        int from = Math.max(0, turns.size() - MAX_RECENT);
        List<ChatMessage> recent = new ArrayList<>();
        for (int i = turns.size() - 1; i >= from; i--) {
            var turn = turns.get(i);
            if (turn.content().length() <= budget) {
                recent.add(0, turn);
                budget -= turn.content().length();
            } else if (budget > 256 && "user".equals(turn.role())) {
                // Preserve the most recent tail where results and new instructions are stated.
                recent.add(0, new ChatMessage("user", "[过长记录，前文已省略] "
                        + turn.content().substring(turn.content().length() - Math.min(budget - 64, 4000))));
                break;
            }
        }
        selected.addAll(recent);
        return selected;
    }

    private KurumiAgentContext() {}
}
