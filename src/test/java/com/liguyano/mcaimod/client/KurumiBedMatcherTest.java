package com.liguyano.mcaimod.client;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class KurumiBedMatcherTest {
    @Test void genericBedMatchesEveryVanillaColor() {
        for (String color : new String[]{"orange", "red", "white", "light_blue", "black"}) {
            assertTrue(KurumiBedMatcher.matches("minecraft:bed", "minecraft:" + color + "_bed"));
        }
    }

    @Test void exactBedQueriesStayExactAndGenericDoesNotMatchOtherBlocks() {
        assertTrue(KurumiBedMatcher.matches("minecraft:orange_bed", "minecraft:orange_bed"));
        assertFalse(KurumiBedMatcher.matches("minecraft:orange_bed", "minecraft:red_bed"));
        assertFalse(KurumiBedMatcher.matches("minecraft:bed", "minecraft:bedrock"));
        assertFalse(KurumiBedMatcher.matches("minecraft:bed", "example:orange_bed"));
        assertFalse(KurumiBedMatcher.matches("minecraft:bed", "minecraft:unknown_bed"));
    }
}
