package com.liguyano.mcaimod.client;

import java.util.List;

/** API-free boundary: only the optional JourneyMap plugin imports JourneyMap classes. */
final class KurumiJourneyMapBridge {
    record Point(String name, String dimension, int x, int y, int z, boolean owned) {}

    interface Adapter {
        List<Point> list();
        void save(String name, String dimension, int x, int y, int z);
        boolean deleteOwned(String name, String dimension);
    }

    private static volatile Adapter adapter;

    static void connect(Adapter value) {
        adapter = value;
    }

    static List<Point> list() {
        return require().list();
    }

    static void save(String name, String dimension, int x, int y, int z) {
        require().save(name, dimension, x, y, z);
    }

    static boolean deleteOwned(String name, String dimension) {
        return require().deleteOwned(name, dimension);
    }

    private static Adapter require() {
        Adapter active = adapter;
        if (active == null) throw new IllegalStateException("JourneyMap 尚未安装或尚未初始化，无法使用地点记忆");
        return active;
    }

    private KurumiJourneyMapBridge() {}
}
