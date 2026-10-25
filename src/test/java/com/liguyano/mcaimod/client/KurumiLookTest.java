package com.liguyano.mcaimod.client;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class KurumiLookTest {
    @Test void acceptsLookScriptsWithCoordinatesIdsAndVariables() {
        assertTrue(KurumiScriptParser.parse("look(90,30)").isPresent());
        assertTrue(KurumiScriptParser.parse("look_at(-68,65,75)").isPresent());
        assertTrue(KurumiScriptParser.parse("look_at(\"minecraft:chest\")").isPresent());
        assertTrue(KurumiScriptParser.parse("look_at(\"minecraft:zombie\")").isPresent());
        assertTrue(KurumiScriptParser.parse("target = find_nearest_block(\"minecraft:chest\")\nlook_at(target.x,target.y,target.z)").isPresent());
        assertTrue(KurumiScriptParser.parse("aimed = look_at(1,65,2)").isPresent());
    }

    @Test void computesExpectedYawPitchAndRejectsInvalidAngles() {
        var south = KurumiLookMath.towards(0, 1.6, 0, 0, 1.6, 5);
        assertEquals(0, south.yaw(), 0.001);
        assertEquals(0, south.pitch(), 0.001);
        var north = KurumiLookMath.towards(0, 1.6, 0, 0, 1.6, -5);
        assertEquals(-180, north.yaw(), 0.001);
        var above = KurumiLookMath.towards(0, 1.6, 0, 0, 6.6, 5);
        assertEquals(-45, above.pitch(), 0.001);
        assertThrows(IllegalArgumentException.class, () -> KurumiLookMath.angles(90, 100));
        assertThrows(IllegalArgumentException.class, () -> KurumiLookMath.towards(1,1,1,1,1,1));
    }
}
