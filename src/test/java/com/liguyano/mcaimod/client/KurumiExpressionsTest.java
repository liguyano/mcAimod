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

    @Test void parserAcceptsObservationResultAssignments() {
        String script = """
                status = get_player_status()
                env = get_environment()
                inventory = get_inventory()
                entities = scan_nearby_entities()
                blocks = scan_nearby_blocks()
                dropped = scan_dropped_items()
                target = find_nearest_block("minecraft:furnace")
                distance = get_distance("block","minecraft:furnace")
                slots = inspect_container()
                slot = inspect_slot(0)
                talk(slot)
                """;
        assertTrue(KurumiScriptParser.parse(script).isPresent());
    }

    @Test void numericAndStringArgs() {
        Map<String, JsonElement> vars=Map.of();
        var args=KurumiExpressions.arguments("\"minecraft:oak_log\", 4",vars);
        assertEquals("minecraft:oak_log",KurumiExpressions.text(args.get(0)));
        assertEquals(4,KurumiExpressions.integer(args.get(1)));
    }
}
