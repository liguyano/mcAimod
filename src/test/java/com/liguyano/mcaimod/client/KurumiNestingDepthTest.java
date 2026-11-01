package com.liguyano.mcaimod.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class KurumiNestingDepthTest {
    private static String nestedSource(int levels) {
        StringBuilder source = new StringBuilder();
        for (int level = 0; level < levels; level++) {
            source.append("    ".repeat(level)).append("if 1 == 1:\n");
        }
        source.append("    ".repeat(levels)).append("talk(\"ok\")");
        return source.toString();
    }

    private static JsonObject nestedProgram(int levels) {
        JsonArray root = new JsonArray();
        JsonArray current = root;
        for (int level = 0; level < levels; level++) {
            JsonObject statement = new JsonObject();
            statement.addProperty("op", "EXPR_IF");
            statement.addProperty("expression", "1 == 1");
            JsonArray body = new JsonArray();
            statement.add("body", body);
            current.add(statement);
            current = body;
        }
        JsonObject leaf = new JsonObject();
        leaf.addProperty("op", "EXPR_CALL");
        leaf.addProperty("method", "talk");
        leaf.addProperty("expression", "\"ok\"");
        current.add(leaf);
        JsonObject program = new JsonObject();
        program.add("program", root);
        return program;
    }

    @Test void acceptsSixAndSixteenLevelsInBothParsers() {
        for (int depth : new int[]{6, 16}) {
            assertTrue(KurumiScriptParser.parse(nestedSource(depth)).isPresent(), KurumiScriptParser.lastError());
            assertTrue(KurumiTaskPlanner.parseProgram(nestedProgram(depth)).isPresent());
        }
    }

    @Test void rejectsSeventeenLevelsInBothParsers() {
        assertTrue(KurumiScriptParser.parse(nestedSource(17)).isEmpty());
        assertTrue(KurumiTaskPlanner.parseProgram(nestedProgram(17)).isEmpty());
    }
}
