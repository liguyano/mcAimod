package com.liguyano.mcaimod.client;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class KurumiDefFunctionsTest {
    @Test void parsesDefinitionWithParametersReturnAndCall() {
        var program = KurumiScriptParser.parse("def move_twice(distance):\n"
                + "    for i in range(2):\n"
                + "        result = move(\"forward\", distance)\n"
                + "    return result\n"
                + "outcome = move_twice(3)\nobserve(outcome)");
        assertTrue(program.isPresent(), KurumiScriptParser.lastError());
        assertEquals("DEF", program.get().statements().get(0).op());
        assertEquals("CALL_FUNCTION", program.get().statements().get(1).op());
    }

    @Test void supportsFunctionCallingAnotherFunctionWithoutRecursion() {
        var program = KurumiScriptParser.parse("def inner(x):\n    return x + 1\n"
                + "def outer(value):\n    answer = inner(value)\n    return answer\n"
                + "final_value = outer(4)\nobserve(final_value)");
        assertTrue(program.isPresent(), KurumiScriptParser.lastError());
    }

    @Test void allowsParameterControlledLoop() {
        var program = KurumiScriptParser.parse("def collect(count):\n"
                + "    for i in range(count):\n"
                + "        result = pickup(\"minecraft:wheat\", 1)\n"
                + "    return result\n"
                + "status = collect(3)\nobserve(status)");
        assertTrue(program.isPresent(), KurumiScriptParser.lastError());
        assertEquals("FOR_EXPR", program.get().statements().get(0).body().get(0).op());
    }

    @Test void rejectsRecursionUnknownCallsAndDuplicateDefinitions() {
        assertTrue(KurumiScriptParser.parse("def a():\n    a()\na()").isEmpty());
        assertTrue(KurumiScriptParser.parse("def a():\n    b()\ndef b():\n    a()\na()").isEmpty());
        assertTrue(KurumiScriptParser.parse("x = unknown_func(1)").isEmpty());
        assertTrue(KurumiScriptParser.parse("def a():\n    return 1\ndef a():\n    return 2\na()").isEmpty());
    }
}
