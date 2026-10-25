package com.liguyano.mcaimod.client;

import java.util.List;

/** Name matching is deliberately exact; ambiguous map markers must not silently redirect navigation. */
final class KurumiWaypointSelection {
    static List<KurumiJourneyMapBridge.Point> find(List<KurumiJourneyMapBridge.Point> points,
                                                   String name, String currentDimension) {
        List<KurumiJourneyMapBridge.Point> matching = points.stream()
                .filter(point -> point.name().equalsIgnoreCase(name)).toList();
        List<KurumiJourneyMapBridge.Point> local = matching.stream()
                .filter(point -> point.dimension().equals(currentDimension)).toList();
        return local.isEmpty() ? matching : local;
    }

    static List<KurumiJourneyMapBridge.Point> owned(List<KurumiJourneyMapBridge.Point> points,
                                                    String name) {
        return points.stream().filter(point -> point.owned() && point.name().equals(name)).toList();
    }

    private KurumiWaypointSelection() {}
}
