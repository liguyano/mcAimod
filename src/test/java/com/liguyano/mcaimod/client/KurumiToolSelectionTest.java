package com.liguyano.mcaimod.client;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class KurumiToolSelectionTest {
    @Test void corpseTaskRetainsEntityMovementAndContainerTools() {
        String catalog = KurumiToolRegistry.catalogForRequest("过来捡一下这个尸体");
        assertTrue(catalog.contains("scan_nearby_entities()"));
        assertTrue(catalog.contains("goto(x,y,z)"));
        assertTrue(catalog.contains("take_all_items()"));
        assertFalse(catalog.contains("get_crafting_recipe(item_id)"));
    }

    @Test void simpleFollowTaskDoesNotSendCraftingTools() {
        String catalog = KurumiToolRegistry.catalogForRequest("跟着主人走");
        assertTrue(catalog.contains("follow(player_name)"));
        assertFalse(catalog.contains("craft_open()"));
    }

    @Test void unknownTaskFallsBackToFullCatalog() {
        assertEquals(KurumiToolRegistry.catalog(), KurumiToolRegistry.catalogForRequest("你好呀"));
    }
}
