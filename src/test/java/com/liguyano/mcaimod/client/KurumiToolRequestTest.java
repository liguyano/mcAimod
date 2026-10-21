package com.liguyano.mcaimod.client;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class KurumiToolRequestTest {
    @Test void acceptsReadOnlyTools() {
        assertEquals("scan_nearby_entities", KurumiToolRequest.parse("{\"type\":\"tool\",\"tool\":\"scan_nearby_entities\"}"));
        assertEquals("get_inventory", KurumiToolRequest.parse("{\"type\":\"tool\",\"tool\":\"get_inventory\"}"));
    }
    @Test void rejectsUnknownTools() {
        assertNull(KurumiToolRequest.parse("{\"type\":\"tool\",\"tool\":\"execute_command\"}"));
        assertNull(KurumiToolRequest.parse("not json"));
    }
}
