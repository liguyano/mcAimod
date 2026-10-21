package com.liguyano.mcaimod;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SmokeTest {
    @Test
    void modIdIsStable() {
        assertEquals("mcaimod", AICompanionMod.MOD_ID);
    }
}
