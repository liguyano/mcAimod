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

    @Test void parserAcceptsGetPositionAndCoordinates() {
        String script = """
                pos = get_position()
                x = 0 - pos.x
                y = pos.y
                z = 0 - pos.z
                goto(x,y,z)
                return "完成"
                """;
        assertTrue(KurumiScriptParser.parse(script).isPresent());
        assertTrue(KurumiScriptParser.parse("talk(get_position())").isPresent());
    }

    @Test void parserAllowsDirectBlockBreak() {
        assertTrue(KurumiScriptParser.parse("break_block(\"minecraft:stone\")").isPresent());
        assertTrue(KurumiScriptParser.parse("break_block(100,64,200)").isPresent());
        assertTrue(KurumiScriptParser.parse("""
                target = find_nearest_block("minecraft:stone")
                if target.found:
                    break_block(target.x,target.y,target.z)
                """).isPresent());
        assertTrue(KurumiScriptParser.parse("break_block(\"minecraft:stone\",100,64,200)").isPresent());
        assertTrue(KurumiScriptParser.parse("break_block()").isPresent()); // runtime rejects invalid arity
    }

    @Test void attackAliasParsesAsMobOrPlayer() {
        assertTrue(KurumiScriptParser.parse("attack(\"minecraft:skeleton\")").isPresent());
        assertTrue(KurumiScriptParser.parse("attack(\"OtherPlayer\")").isPresent());
        assertTrue(KurumiScriptParser.parse("attack()").isEmpty());
    }

    @Test void chestMemoryScriptSyntax() {
        assertTrue(KurumiScriptParser.parse("""
                search_chests("minecraft:diamond")
                report = get_chest_search_result()
                talk(report)
                """).isPresent());
        assertTrue(KurumiScriptParser.parse("""
                saved = remember_block("矿场箱子",100,64,200)
                point = get_remembered_block("矿场箱子")
                if point.found:
                    goto(point.x,point.y,point.z)
                take_from_memory("矿场箱子","minecraft:diamond",4)
                """).isPresent());
    }

    @Test void genericBlockMemoryListIsIterable() {
        assertTrue(KurumiScriptParser.parse("""
                memory = list_remembered_blocks()
                i = 0
                while i < memory.count:
                    block = get_remembered_block_at(i)
                    if block.found:
                        talk(block.name)
                    i = i + 1
                """).isPresent());
    }

    @Test void expressionIndexesCoordinateLists() {
        var values=new java.util.HashMap<String,com.google.gson.JsonElement>();
        var list=new com.google.gson.JsonArray();
        var first=new com.google.gson.JsonObject();
        first.addProperty("x",100);
        first.addProperty("y",64);
        first.addProperty("z",200);
        list.add(first);
        values.put("blocks",list);
        values.put("i",new com.google.gson.JsonPrimitive(0));
        assertEquals(1, KurumiExpressions.integer(KurumiExpressions.evaluate("len(blocks)",values)));
        assertEquals(100, KurumiExpressions.integer(KurumiExpressions.evaluate("blocks[i].x",values)));
        assertEquals(200, KurumiExpressions.integer(KurumiExpressions.evaluate("blocks[0].z",values)));
        assertThrows(IllegalArgumentException.class,
                ()->KurumiExpressions.evaluate("blocks[1].x",values));
        assertTrue(KurumiScriptParser.parse("""
                blocks = search_all_block("minecraft:chest")
                i = 0
                while i < len(blocks):
                    point = blocks[i]
                    talk(point.x)
                    i = i + 1
                """).isPresent());
    }

    @Test void numericAndStringArgs() {
        Map<String, JsonElement> vars=Map.of();
        var args=KurumiExpressions.arguments("\"minecraft:oak_log\", 4",vars);
        assertEquals("minecraft:oak_log",KurumiExpressions.text(args.get(0)));
        assertEquals(4,KurumiExpressions.integer(args.get(1)));
    }
}
