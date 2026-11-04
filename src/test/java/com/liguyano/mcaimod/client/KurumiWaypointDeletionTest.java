package com.liguyano.mcaimod.client;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class KurumiWaypointDeletionTest {
    private static final String WORLD = "minecraft:overworld";

    @Test void findsDeathWaypointsWithoutSelectingOrdinaryMarkers() {
        var death = new KurumiJourneyMapBridge.Point("Death 12", WORLD, 3, 64, 3, false);
        var chinese = new KurumiJourneyMapBridge.Point("死亡点", WORLD, 4, 64, 4, false);
        var home = new KurumiJourneyMapBridge.Point("家", WORLD, 5, 64, 5, true);
        assertEquals(List.of(death, chinese), KurumiWaypointDeletion.deathPoints(List.of(death, chinese, home)));
    }

    @Test void nameSelectionRespectsDimensionAndDoesNotPickAnAmbiguousPoint() {
        var one = new KurumiJourneyMapBridge.Point("家", WORLD, 1, 64, 2, true);
        var two = new KurumiJourneyMapBridge.Point("家", WORLD, 3, 64, 4, false);
        var nether = new KurumiJourneyMapBridge.Point("家", "minecraft:the_nether", 5, 64, 6, false);
        assertEquals(List.of(one, two), KurumiWaypointDeletion.exactName(List.of(one, two, nether), "家", WORLD));
        assertTrue(KurumiWaypointDeletion.exactName(List.of(nether), "家", WORLD).isEmpty());
    }

    @Test void deletionToolsAreValidScriptFunctions() {
        assertTrue(KurumiScriptParser.parse("forget_block(\"樱花木箱子\")").isPresent());
        assertTrue(KurumiScriptParser.parse("delete_waypoint(\"家\")").isPresent());
        assertTrue(KurumiScriptParser.parse("request_delete_death_waypoints()").isPresent());
        assertFalse(KurumiExpressions.isExpressionFunction("confirm_delete_death_waypoints"));
    }
}
