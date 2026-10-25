package com.liguyano.mcaimod.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class KurumiUnifiedAssignedActionsTest {
    @Test void supportsZeroArgumentToolsAsAssignedCalls() {
        for (String method : new String[] {"tpa_accept", "tpa_request", "stop", "sleep_in_bed",
                "craft_open", "chest_open", "chest_put_all", "chest_take_all", "take_all_items",
                "drop_all", "pickup_all", "fish", "stop_fishing"}) {
            assertTrue(KurumiScriptParser.parse("outcome = " + method + "()").isPresent(), method);
        }
    }

    @Test void supportsOneAndTwoArgumentToolsAsAssignedCalls() {
        for (String call : new String[] {
                "tpa_request_to(\"SekaiSaionji\")", "drop_item_all(\"minecraft:stone\")",
                "pickup_item_all(\"minecraft:stone\")", "chest_put_item_all(\"minecraft:stone\")",
                "equip_armor(\"minecraft:iron_chestplate\")",
                "drop(\"minecraft:stone\",1)", "pickup(\"minecraft:stone\",1)",
                "chest_put(\"minecraft:stone\",1)", "chest_take(\"minecraft:stone\",1)",
                "collect_and_store(\"minecraft:stone\",1)", "farm(4)",
                "chain_on()", "chain_off()", "take_item(\"minecraft:bread\",1)",
                "put_item(\"minecraft:bread\",1,\"auto\")"}) {
            assertTrue(KurumiScriptParser.parse("outcome = " + call).isPresent(), call);
        }
    }

    @Test void rejectsUnknownActionName() {
        assertTrue(KurumiScriptParser.parse("outcome = unknown_world_action()").isEmpty());
    }

    @Test void queryAssignmentRemainsStructured() {
        assertTrue(KurumiScriptParser.parse("inv = get_inventory()\nobserve(inv)").isPresent());
    }
}
