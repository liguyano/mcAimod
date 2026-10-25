package com.liguyano.mcaimod.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class KurumiHoldUseScriptTest {
    @Test void acceptsHoldUseWithItemAndDuration() {
        assertTrue(KurumiScriptParser.parse("hold_use_item(\"minecraft:carrot\",3)").isPresent());
        assertTrue(KurumiScriptParser.parse("hold_use_item(\"minecraft:bow\",5)\nobserve(get_player_status())").isPresent());
        assertTrue(KurumiScriptParser.parse("used = hold_use_item(\"minecraft:carrot\",3)").isPresent());
    }

    @Test void rejectsMalformedHoldUse() {
        assertTrue(KurumiScriptParser.parse("hold_use_item()").isEmpty());
    }
}
