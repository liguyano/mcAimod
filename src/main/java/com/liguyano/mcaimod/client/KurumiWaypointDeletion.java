package com.liguyano.mcaimod.client;

import java.util.List;
import java.util.Locale;

/** Pure selection rules; actual deletion is handled by the JourneyMap adapter. */
final class KurumiWaypointDeletion {
    static boolean isDeath(KurumiJourneyMapBridge.Point point) {
        String name = point.name().toLowerCase(Locale.ROOT);
        return name.contains("death") || name.contains("死亡") || name.contains("死亡地点");
    }

    static List<KurumiJourneyMapBridge.Point> deathPoints(List<KurumiJourneyMapBridge.Point> points) {
        return points.stream().filter(KurumiWaypointDeletion::isDeath).toList();
    }

    static List<KurumiJourneyMapBridge.Point> exactName(List<KurumiJourneyMapBridge.Point> points,
                                                         String name, String dimension) {
        return points.stream().filter(point -> point.name().equalsIgnoreCase(name)
                && point.dimension().equals(dimension)).toList();
    }

    private KurumiWaypointDeletion() {}
}
