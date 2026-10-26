package com.liguyano.mcaimod.client;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class KurumiToolRequestTest {
    @Test void acceptsReadOnlyTools() {
        assertEquals("scan_nearby_entities", KurumiToolRequest.parse("{\"type\":\"tool\",\"tool\":\"scan_nearby_entities\"}"));
        assertEquals("get_inventory", KurumiToolRequest.parse("{\"type\":\"tool\",\"tool\":\"get_inventory\"}"));
    }
    @Test void acceptsRecipeLookupAndRejectsMalformedItemIds() {
        assertEquals("get_crafting_recipe:minecraft:chest", KurumiToolRequest.parse(
                "{\"type\":\"tool\",\"tool\":\"get_crafting_recipe\",\"item_id\":\"minecraft:chest\"}"));
        assertNull(KurumiToolRequest.parse(
                "{\"type\":\"tool\",\"tool\":\"get_crafting_recipe\",\"item_id\":\"not-an-id\"}"));
    }
    @Test void acceptsGenericInventoryItemCheckAndRejectsMalformedIds() {
        assertEquals("check_inventory_item:minecraft:diamond", KurumiToolRequest.parse(
                "{\"type\":\"tool\",\"tool\":\"check_inventory_item\",\"item_id\":\"minecraft:diamond\"}"));
        assertEquals("check_inventory_item:some_mod:materials/ingot", KurumiToolRequest.parse(
                "{\"type\":\"tool\",\"tool\":\"check_inventory_item\",\"item_id\":\"some_mod:materials/ingot\"}"));
        assertNull(KurumiToolRequest.parse(
                "{\"type\":\"tool\",\"tool\":\"check_inventory_item\",\"item_id\":\"bad id\"}"));
    }
    @Test void rejectsUnknownTools() {
        assertNull(KurumiToolRequest.parse("{\"type\":\"tool\",\"tool\":\"execute_command\"}"));
        assertNull(KurumiToolRequest.parse("not json"));
    }
}
