package com.liguyano.mcaimod.client;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class KurumiNoAutoCraftTest {
    @Test void rejectsRemovedCraftItemScript() {
        assertTrue(KurumiScriptParser.parse("craft_item(\"minecraft:stick\",4)").isEmpty());
    }

    @Test void rejectsRemovedCraftItemActions() {
        assertTrue(KurumiActionValidator.validate("CRAFT_ITEM", "minecraft:stick", 4, 0).isEmpty());
        JsonObject plan = new JsonObject();
        var steps = new com.google.gson.JsonArray();
        JsonObject step = new JsonObject();
        step.addProperty("action", "CRAFT_ITEM");
        step.addProperty("item_id", "minecraft:stick");
        step.addProperty("quantity", 4);
        steps.add(step);
        plan.add("steps", steps);
        assertTrue(KurumiTaskPlanner.parse(plan).isEmpty());
    }

    @Test void keepsManualCraftingViaSlots() {
        assertTrue(KurumiScriptParser.parse("put_item(\"minecraft:cherry_log\",1,\"grid_1\")").isPresent());
        assertTrue(KurumiScriptParser.parse("take_item(\"minecraft:cherry_planks\",4)").isPresent());
    }
}
