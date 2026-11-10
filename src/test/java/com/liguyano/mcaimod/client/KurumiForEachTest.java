package com.liguyano.mcaimod.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class KurumiForEachTest {
    @Test void acceptsEntityIterationWithBreak() {
        var program = KurumiScriptParser.parse("""
                scan = scan_nearby_entities()
                found = False
                for e in scan.entities:
                    if e.type == "corpse:corpse":
                        found = True
                        break
                """);
        assertTrue(program.isPresent(), KurumiScriptParser.lastError());
        assertEquals("FOR_EACH", program.orElseThrow().statements().get(2).op());
    }

    @Test void acceptsContinueInsideListIteration() {
        assertTrue(KurumiScriptParser.parse("""
                scan = scan_nearby_entities()
                for e in scan.entities:
                    if e.type != "corpse:corpse":
                        continue
                    talk("found")
                """).isPresent(), KurumiScriptParser.lastError());
    }

    @Test void rejectsUnsupportedIterationExpressions() {
        assertTrue(KurumiScriptParser.parse("""
                for e in scan.entities():
                    talk("unsafe")
                """).isEmpty());
    }
}
