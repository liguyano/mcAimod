package com.liguyano.mcaimod.client;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class KurumiForRangeTest {
    @Test void parsesOneTwoAndThreeArgumentRanges() {
        for (String header : new String[]{"for i in range(5):", "for i in range(1, 6):",
                "for i in range(9, 0, -2):"}) {
            var program = KurumiScriptParser.parse(header + "\n    talk(i)");
            assertTrue(program.isPresent(), header + ": " + KurumiScriptParser.lastError());
            assertEquals("FOR", program.get().statements().get(0).op());
        }
    }

    @Test void allowsNestedIfBreakAndContinue() {
        String script = "for i in range(10):\n    if i == 2:\n        continue\n"
                + "    if i == 7:\n        break\n    talk(i)";
        assertTrue(KurumiScriptParser.parse(script).isPresent(), KurumiScriptParser.lastError());
    }

    @Test void refusesUnboundedOrInvalidLoops() {
        assertTrue(KurumiScriptParser.parse("for i in range(101):\n    talk(i)").isEmpty());
        assertTrue(KurumiScriptParser.parse("for i in range(0, 4, 0):\n    talk(i)").isEmpty());
        assertTrue(KurumiScriptParser.parse("break").isEmpty());
        assertTrue(KurumiScriptParser.parse("continue").isEmpty());
    }

    @Test void forLoopRetainsBodyRatherThanExpandingIt() {
        var p = KurumiScriptParser.parse("for i in range(100):\n    talk(i)");
        assertTrue(p.isPresent(), KurumiScriptParser.lastError());
        assertEquals(1, p.get().statements().size());
        assertEquals(1, p.get().statements().get(0).body().size());
    }
}
