package com.liguyano.mcaimod.client;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class KurumiDecisionParserTest {
    private static String request(String script) {
        return new com.google.gson.JsonObject() {{
            addProperty("type", "action");
            addProperty("action", "EXECUTE_SCRIPT");
            addProperty("reply", "主人~");
            addProperty("script", script);
        }}.toString();
    }

    @Test void acceptsScriptSpeech() {
        var decision = KurumiDecisionParser.parse(request("talk(\"你好，主人~\")")).orElseThrow();
        assertTrue(decision.action().isEmpty());
        assertTrue(decision.program().isPresent());
    }

    @Test void acceptsScriptObservations() {
        assertTrue(KurumiDecisionParser.parse(request("talk(get_environment())")).isPresent());
        assertTrue(KurumiDecisionParser.parse(request("talk(get_inventory())")).isPresent());
        assertTrue(KurumiDecisionParser.parse(request("talk(get_distance(\"block\",\"minecraft:oak_log\"))")).isPresent());
        assertTrue(KurumiDecisionParser.parse(request("talk(check_position(1, 64, -2, 8))")).isPresent());
    }

    @Test void acceptsScriptActions() {
        assertTrue(KurumiDecisionParser.parse(request("result = mine(\"minecraft:oak_log\")")).isPresent());
        assertTrue(KurumiDecisionParser.parse(request("stop()")).isPresent());
    }

    @Test void rejectsLegacyResponses() {
        assertTrue(KurumiDecisionParser.parse("{\"type\":\"chat\",\"reply\":\"你好\"}").isEmpty());
        assertTrue(KurumiDecisionParser.parse("{\"type\":\"tool\",\"tool\":\"get_environment\",\"reply\":\"查询\"}").isEmpty());
        assertTrue(KurumiDecisionParser.parse("{\"type\":\"action\",\"action\":\"FARM\",\"reply\":\"开始\",\"radius\":16}").isEmpty());
    }

    @Test void rejectsMalformedScripts() {
        assertTrue(KurumiDecisionParser.parse(request("java.lang.Runtime.getRuntime()")).isEmpty());
        assertTrue(KurumiDecisionParser.parse(request("talk(get_distance(\"bad\",\"minecraft:stone\"))")).isEmpty());
        assertTrue(KurumiDecisionParser.parse("not json").isEmpty());
    }
}
