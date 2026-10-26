package com.liguyano.mcaimod.client;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class KurumiRaiseExceptionTest {
    @Test void acceptsExplicitAndDynamicMessagesInIfAndLoops() {
        var program = KurumiScriptParser.parse("for i in range(3):\n"
                + "    result = pickup_item_all(\"minecraft:wheat\")\n"
                + "    if not result.success:\n"
                + "        raise Exception(result.message)");
        assertTrue(program.isPresent(), KurumiScriptParser.lastError());
        var raise = program.get().statements().get(0).body().get(1).body().get(0);
        assertEquals("RAISE", raise.op());
        assertEquals("result.message", raise.step().itemId());
        assertEquals("4", raise.step().target());
    }

    @Test void acceptsSafeLiteralAndRejectsUnsupportedExceptionForms() {
        assertTrue(KurumiScriptParser.parse("raise Exception(\"找不到箱子\")").isPresent());
        assertTrue(KurumiScriptParser.parse("raise Exception()").isEmpty());
        assertTrue(KurumiScriptParser.parse("raise ValueError(\"错误\")").isEmpty());
        assertTrue(KurumiScriptParser.parse("raise Exception(\"a\", \"b\")").isEmpty());
        assertTrue(KurumiScriptParser.parse("raise Exception(__import__(\"os\"))").isEmpty());
    }
}
