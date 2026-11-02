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
        assertTrue(KurumiScriptParser.parse("raise Exception('没有找到床')").isPresent());
        assertEquals("没有找到床", KurumiExpressions.text(
                KurumiExpressions.evaluate("'没有找到床'", java.util.Map.of())));
        assertEquals(2, KurumiExpressions.arguments("'minecraft:bed', 2", java.util.Map.of()).size());
        assertTrue(KurumiScriptParser.parse("raise Exception()").isEmpty());
        assertTrue(KurumiScriptParser.parse("raise ValueError(\"错误\")").isEmpty());
        assertTrue(KurumiScriptParser.parse("raise Exception(\"a\", \"b\")").isEmpty());
        assertTrue(KurumiScriptParser.parse("raise Exception(__import__(\"os\"))").isEmpty());
    }

    @Test void supportsConcatenatedActionErrorsWithoutAllowingArbitraryCalls() {
        assertTrue(KurumiScriptParser.parse("""
                result = goto(-110,70,62)
                if not result.success:
                    raise Exception("回家失败：" + result.message)
                """).isPresent(), KurumiScriptParser.lastError());
        assertTrue(KurumiScriptParser.parse("raise Exception(\"x\" + __import__(\"os\"))").isEmpty());
        assertTrue(KurumiScriptParser.parse("raise Exception('x' + __import__('os'))").isEmpty());
    }

    @Test void acceptsSingleQuotedSleepRecoveryScript() {
        assertTrue(KurumiScriptParser.parse("""
                talk('主人~，我去睡觉啦~')
                home = goto(-110, 70, 62)
                if not home.success:
                    raise Exception('回家寻路失败')
                slept = sleep_in_bed()
                if not slept.success:
                    raise Exception(slept.message)
                """).isPresent(), KurumiScriptParser.lastError());
    }
}
