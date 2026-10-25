package com.liguyano.mcaimod.client;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class KurumiWaypointSelectionTest {
    private final List<KurumiJourneyMapBridge.Point> points = List.of(
            new KurumiJourneyMapBridge.Point("家", "minecraft:overworld", 1, 65, 2, true),
            new KurumiJourneyMapBridge.Point("矿洞", "minecraft:overworld", 20, 30, 20, false),
            new KurumiJourneyMapBridge.Point("家", "minecraft:the_nether", 3, 65, 4, false));

    @Test void navigationSelectsCurrentDimension() {
        var found = KurumiWaypointSelection.find(points, "家", "minecraft:overworld");
        assertEquals(1, found.size());
        assertEquals(1, found.get(0).x());
    }

    @Test void duplicateWaypointsInSameDimensionRemainAmbiguous() {
        var extras = new java.util.ArrayList<>(points);
        extras.add(new KurumiJourneyMapBridge.Point("矿洞", "minecraft:overworld", 30, 20, 30, true));
        assertEquals(2, KurumiWaypointSelection.find(extras, "矿洞", "minecraft:overworld").size());
    }

    @Test void removalOnlyTargetsKurumiOwnedWaypoints() {
        assertTrue(KurumiWaypointSelection.owned(points, "矿洞").isEmpty());
        assertEquals(1, KurumiWaypointSelection.owned(points, "家").size());
    }
}
