package com.liguyano.mcaimod.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class KurumiContainerActionResultTest {
    @Test void acceptsAssignedPutItemAndTakeItem() {
        assertTrue(KurumiScriptParser.parse("""
                first = put_item("minecraft:wheat",22,"grid_1")
                second = put_item("minecraft:wheat",21,"grid_2")
                third = put_item("minecraft:wheat",21,"grid_3")
                observe(third)
                """).isPresent());
        assertTrue(KurumiScriptParser.parse("result = take_item(\"minecraft:bread\",1)").isPresent());
    }

    @Test void rejectsMissingContainerTransferArguments() {
        assertTrue(KurumiScriptParser.parse("result = put_item()").isEmpty());
        assertTrue(KurumiScriptParser.parse("result = put_item(\"minecraft:bread\")").isEmpty());
        assertTrue(KurumiScriptParser.parse("result = take_item(\"minecraft:bread\")").isEmpty());
    }

    @Test void preservesUnassignedContainerTransfers() {
        assertTrue(KurumiScriptParser.parse("put_item(\"minecraft:bread\",1,\"auto\")").isPresent());
        assertTrue(KurumiScriptParser.parse("take_item(\"minecraft:bread\",1)").isPresent());
    }
}
