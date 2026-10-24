package com.liguyano.mcaimod.client;

import org.junit.jupiter.api.Test;
import com.liguyano.mcaimod.model.ChatMessage;
import java.util.ArrayList;
import static org.junit.jupiter.api.Assertions.*;

class KurumiAgentSessionTest {
    @Test void queuesMessagesInOrderAndDoesNotLoseNewMessagesOnFinish() {
        var session = new KurumiAgentSession();
        session.enqueue("SekaiSaionji", "顺便找铁");
        session.enqueue("SekaiSaionji", "先回来");
        assertFalse(session.finishIfIdle());
        assertEquals(2, session.drainMessages().size());
        assertTrue(session.finishIfIdle());
    }

    @Test void keepsRecentObservationsAndIndexesOldResults() {
        var session = new KurumiAgentSession();
        for (int i = 0; i < 30; i++) session.recordObservation("inventory", "item-" + i);
        String context = session.buildModelContext();
        assertTrue(context.contains("item-29"));
        assertTrue(context.contains("item-0"));
        assertTrue(context.length() < 15000);
        assertEquals("item-0", session.observation(0));
        assertEquals("item-29", session.observation(29));
    }

    @Test void finishResponseIsExplicitAndRejectsScriptFields() {
        assertTrue(KurumiDecisionParser.isFinish("{\"type\":\"finish\",\"reason\":\"已回复\"}"));
        assertFalse(KurumiDecisionParser.isFinish("{\"type\":\"finish\",\"script\":\"stop()\"}"));
        assertFalse(KurumiDecisionParser.isFinish("{\"type\":\"action\"}"));
    }

    @Test void observeExpressionParsesWithoutTalk() {
        assertTrue(KurumiScriptParser.parse("items = get_inventory()\nobserve(items)").isPresent());
    }

    @Test void compactsLongConversationWithoutForgettingOriginalGoalOrLatestTurn() {
        var history = new ArrayList<ChatMessage>();
        history.add(new ChatMessage("system", "只能输出白名单脚本"));
        for (int i = 0; i < 45; i++) history.add(new ChatMessage("user", "观察结果" + i + "-" + "x".repeat(900)));
        var result = KurumiAgentContext.compact(history, "收集钻石", "已完成箱子检查");
        assertTrue(result.stream().anyMatch(m -> m.content().contains("收集钻石")));
        assertTrue(result.stream().anyMatch(m -> m.content().contains("观察结果44")));
        assertTrue(result.stream().mapToInt(m -> m.content().length()).sum() <= 48000);
    }
}
