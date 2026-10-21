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

    @Test void acceptsSleep() {
        var decision = KurumiDecisionParser.parse("{\"type\":\"action\",\"reply\":\"尝试睡觉\",\"action\":\"SLEEP\",\"radius\":0}").orElseThrow();
        assertEquals(KurumiActionValidator.Type.SLEEP, decision.action().orElseThrow().type());
    }

    @Test void acceptsDropAndRejectsInvalidQuantity() {
        var decision = KurumiDecisionParser.parse("{\"type\":\"action\",\"reply\":\"丢出圆石\",\"action\":\"DROP\",\"item_id\":\"minecraft:cobblestone\",\"quantity\":10,\"radius\":0}").orElseThrow();
        assertEquals(KurumiActionValidator.Type.DROP, decision.action().orElseThrow().type());
        assertEquals(10, decision.action().orElseThrow().quantity());
        assertTrue(KurumiDecisionParser.parse("{\"type\":\"action\",\"reply\":\"丢出圆石\",\"action\":\"DROP\",\"item_id\":\"minecraft:cobblestone\",\"quantity\":65,\"radius\":0}").isEmpty());
    }

    @Test void acceptsChainMiningToggle() {
        var on = KurumiDecisionParser.parse("{\"type\":\"action\",\"reply\":\"开启连锁破坏\",\"action\":\"CHAIN_ON\",\"radius\":0}").orElseThrow();
        assertEquals(KurumiActionValidator.Type.CHAIN_ON, on.action().orElseThrow().type());
        var off = KurumiDecisionParser.parse("{\"type\":\"action\",\"reply\":\"关闭连锁破坏\",\"action\":\"CHAIN_OFF\",\"radius\":0}").orElseThrow();
        assertEquals(KurumiActionValidator.Type.CHAIN_OFF, off.action().orElseThrow().type());
    }

    @Test void acceptsNormalChat() {
        var decision = KurumiDecisionParser.parse("{\"type\":\"chat\",\"reply\":\"你好呀\"}").orElseThrow();
        assertTrue(decision.action().isEmpty());
    }

    @Test void acceptsFollowAndGoto() {
        var follow = KurumiDecisionParser.parse("{\"type\":\"action\",\"reply\":\"开始跟随\",\"action\":\"FOLLOW\",\"target\":\"SekaiSaionji\"}").orElseThrow();
        assertEquals("SekaiSaionji", follow.target());
        var gotoDecision = KurumiDecisionParser.parse("{\"type\":\"action\",\"reply\":\"前往坐标\",\"action\":\"GOTO\",\"x\":120,\"y\":64,\"z\":-30}").orElseThrow();
        assertEquals(120, gotoDecision.x());
        assertEquals(-30, gotoDecision.z());
        assertTrue(KurumiDecisionParser.parse("{\"type\":\"action\",\"reply\":\"走\",\"action\":\"GOTO\",\"x\":120,\"y\":64}").isEmpty());
    }

    @Test void validatesTreeHarvesting() {
        var mine = KurumiDecisionParser.parse("{\"type\":\"action\",\"reply\":\"开始砍树\",\"action\":\"MINE\",\"block_id\":\"minecraft:oak_log\"}").orElseThrow();
        assertEquals("minecraft:oak_log", mine.blockId());
        assertTrue(KurumiDecisionParser.parse("{\"type\":\"action\",\"reply\":\"执行\",\"action\":\"MINE\",\"block_id\":\"minecraft:command_block\"}").isEmpty());
    }

    @Test void rejectsUnsupportedOrMalformedActions() {
        assertTrue(KurumiDecisionParser.parse("{\"type\":\"action\",\"reply\":\"执行\",\"action\":\"DROP\",\"item_id\":\"minecraft:diamond\",\"quantity\":0}").isEmpty());
        assertTrue(KurumiDecisionParser.parse("{\"type\":\"action\",\"reply\":\"执行\",\"action\":\"FARM\",\"radius\":32}").isEmpty());
        assertTrue(KurumiDecisionParser.parse("{\"reply\":\"你好\"}").isEmpty());
        assertTrue(KurumiDecisionParser.parse("not json").isEmpty());
    }
}
