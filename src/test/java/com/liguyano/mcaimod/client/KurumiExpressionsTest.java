package com.liguyano.mcaimod.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import org.junit.jupiter.api.Test;
import java.util.HashMap;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class KurumiExpressionsTest {
    @Test void dictionaryFieldsAndArguments() {
        Map<String, JsonElement> vars = new HashMap<>();
        JsonObject target = new JsonObject();
        target.addProperty("found", true);
        target.addProperty("x", 120);
        target.addProperty("y", 64);
        target.addProperty("z", -30);
        vars.put("target", target);
        assertTrue(KurumiExpressions.truth(KurumiExpressions.evaluate("target.found", vars)));
        var args=KurumiExpressions.arguments("target.x, target.y, target.z", vars);
        assertEquals(120, KurumiExpressions.integer(args.get(0)));
        assertEquals(64, KurumiExpressions.integer(args.get(1)));
        assertEquals(-30, KurumiExpressions.integer(args.get(2)));
    }

    @Test void arithmeticAndReassignments() {
        Map<String, JsonElement> vars=new HashMap<>();
        vars.put("count", new JsonPrimitive(1));
        vars.put("count", KurumiExpressions.evaluate("count + 1", vars));
        assertEquals(2, KurumiExpressions.integer(KurumiExpressions.evaluate("count", vars)));
        assertTrue(KurumiExpressions.truth(KurumiExpressions.evaluate("count < 4", vars)));
        assertFalse(KurumiExpressions.truth(KurumiExpressions.evaluate("not (count < 4)", vars)));
    }

    @Test void rejectsUnexpectedCallsAndFields() {
        Map<String, JsonElement> vars=new HashMap<>();
        vars.put("target", new JsonObject());
        assertThrows(IllegalArgumentException.class,
                () -> KurumiExpressions.evaluate("target.x", vars));
        assertThrows(IllegalArgumentException.class,
                () -> KurumiExpressions.evaluate("System.exit(0)", vars));
        assertThrows(IllegalArgumentException.class,
                () -> KurumiExpressions.evaluate("unknown(1)", vars));
    }

    @Test void parserAcceptsToolDictionaryAndGoto() {
        String script = """
                target = find_nearest_block("minecraft:crafting_table")
                if target.found:
                    goto(target.x, target.y, target.z)
                else:
                    talk("没有找到工作台")
                """;
        assertTrue(KurumiScriptParser.parse(script).isPresent());
    }

    @Test void parserAcceptsRuntimeCounterLoop() {
        String script = """
                count = 1
                while count < 4:
                    count = count + 1
                talk(count)
                """;
        assertTrue(KurumiScriptParser.parse(script).isPresent());
    }

    @Test void attackAliasParsesIntoCorrectTargets() {
        var mob = KurumiScriptParser.parse("attack(\"minecraft:zombie\")").orElseThrow();
        assertEquals("ATTACK_MOB", mob.statements().get(0).step().action());
        assertEquals("minecraft:zombie", mob.statements().get(0).step().target());
        var player = KurumiScriptParser.parse("attack(\"OtherPlayer\")").orElseThrow();
        assertEquals("ATTACK_PLAYER", player.statements().get(0).step().action());
        assertEquals("OtherPlayer", player.statements().get(0).step().target());
        assertTrue(KurumiScriptParser.parse("attack()").isEmpty());
        assertTrue(KurumiScriptParser.parse("attack(\"SekaiSaionji\")").isEmpty());
    }

    @Test void numericAndStringArgs() {
        Map<String, JsonElement> vars=Map.of();
        var args=KurumiExpressions.arguments("\"minecraft:oak_log\", 4",vars);
        assertEquals("minecraft:oak_log",KurumiExpressions.text(args.get(0)));
        assertEquals(4,KurumiExpressions.integer(args.get(1)));
    }
}
