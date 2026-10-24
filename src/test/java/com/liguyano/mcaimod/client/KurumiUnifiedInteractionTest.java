package com.liguyano.mcaimod.client;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class KurumiUnifiedInteractionTest {
    @Test void boatItemIdMatchesItsBoatEntity() {
        assertTrue(KurumiEntityMatcher.matches("minecraft:oak_boat", "minecraft:boat"));
        assertTrue(KurumiEntityMatcher.matches("minecraft:cherry_chest_boat", "minecraft:chest_boat"));
        assertFalse(KurumiEntityMatcher.matches("minecraft:furnace", "minecraft:boat"));
        assertTrue(KurumiEntityMatcher.matches("minecraft:villager", "minecraft:villager"));
    }

    @Test void scriptAcceptsUseItemWithLiteralOrVariable() {
        assertTrue(KurumiScriptParser.parse("use_item(\"minecraft:apple\")").isPresent());
        assertTrue(KurumiScriptParser.parse("food = \"minecraft:apple\"\nuse_item(food)").isPresent());
        assertTrue(KurumiScriptParser.parse("result = use_item(\"minecraft:apple\")").isPresent());
    }
}
