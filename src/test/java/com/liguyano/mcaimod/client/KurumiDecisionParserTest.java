package com.liguyano.mcaimod.client;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class KurumiDecisionParserTest {
    @Test void acceptsFarmFromModel() {
        var decision = KurumiDecisionParser.parse("{\"type\":\"action\",\"reply\":\"好的，我去种田\",\"action\":\"FARM\",\"radius\":16}").orElseThrow();
        assertEquals(KurumiActionValidator.Type.FARM, decision.action().orElseThrow().type());
    }

    @Test void acceptsStopFromModel() {
        var decision = KurumiDecisionParser.parse("{\"type\":\"action\",\"reply\":\"好的，停止\",\"action\":\"STOP\",\"radius\":0}").orElseThrow();
        assertEquals(KurumiActionValidator.Type.STOP, decision.action().orElseThrow().type());
    }

    @Test void acceptsNormalChat() {
        var decision = KurumiDecisionParser.parse("{\"type\":\"chat\",\"reply\":\"你好呀\"}").orElseThrow();
        assertTrue(decision.action().isEmpty());
    }

    @Test void rejectsUnsupportedOrMalformedActions() {
        assertTrue(KurumiDecisionParser.parse("{\"type\":\"action\",\"reply\":\"执行\",\"action\":\"DROP\",\"item_id\":\"minecraft:diamond\",\"quantity\":64}").isEmpty());
        assertTrue(KurumiDecisionParser.parse("{\"type\":\"action\",\"reply\":\"执行\",\"action\":\"FARM\",\"radius\":32}").isEmpty());
        assertTrue(KurumiDecisionParser.parse("{\"reply\":\"你好\"}").isEmpty());
        assertTrue(KurumiDecisionParser.parse("not json").isEmpty());
    }
}
