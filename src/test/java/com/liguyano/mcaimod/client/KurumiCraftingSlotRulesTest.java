package com.liguyano.mcaimod.client;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class KurumiCraftingSlotRulesTest {
    @Test void translatesInputAndAutoToFirstAvailableGrid() {
        assertEquals(-1, KurumiCraftingSlotRules.index("input"));
        assertEquals(-1, KurumiCraftingSlotRules.index("auto"));
        assertEquals(1, KurumiCraftingSlotRules.index("grid_1"));
        assertEquals(9, KurumiCraftingSlotRules.index("grid_9"));
    }

    @Test void doesNotMapFuelOrUnknownSlotsToCraftingGrid() {
        assertThrows(IllegalArgumentException.class, () -> KurumiCraftingSlotRules.index("fuel"));
        assertThrows(IllegalArgumentException.class, () -> KurumiCraftingSlotRules.index("grid_0"));
    }

    @Test void parserAllowsPutItemToCraftingInput() {
        assertTrue(KurumiScriptParser.parse(
            "interact(\"minecraft:crafting_table\")\nput_item(\"minecraft:cherry_log\",1,\"input\")"
        ).isPresent());
    }
}
