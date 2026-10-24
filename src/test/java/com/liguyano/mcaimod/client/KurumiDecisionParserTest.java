package com.liguyano.mcaimod.client;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class KurumiDecisionParserTest {
    private static String request(String script) {
        String escaped = script.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r");
        return "{\"type\":\"action\",\"action\":\"EXECUTE_SCRIPT\","
                + "\"reply\":\"主人~\",\"script\":\"" + escaped + "\"}";
    }

    @Test void clarificationIsValidatedAndNotExecuted() {
        String question = "{\"type\":\"clarify\",\"question\":\"主人~，你要哪种原木？\"}";
        assertEquals("主人~，你要哪种原木？", KurumiDecisionParser.clarification(question).orElseThrow());
        assertTrue(KurumiDecisionParser.parse(question).isEmpty());
        assertTrue(KurumiDecisionParser.clarification(
                "{\"type\":\"clarify\",\"question\":\"/danger\"}").isEmpty());
        assertTrue(KurumiDecisionParser.clarification(
                "{\"type\":\"clarify\",\"question\":\"\"}").isEmpty());
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

    @Test void supportsBoundedSleep() {
        assertTrue(KurumiDecisionParser.parse(request("talk(\"稍等一下\")\nsleep(5)\ntalk(\"继续啦\")")).isPresent());
        assertTrue(KurumiDecisionParser.parse(request("sleep(0)")).isEmpty());
        assertTrue(KurumiDecisionParser.parse(request("sleep(121)")).isEmpty());
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
