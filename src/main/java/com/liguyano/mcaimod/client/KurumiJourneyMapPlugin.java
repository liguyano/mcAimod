package com.liguyano.mcaimod.client;

import journeymap.api.v2.client.IClientAPI;
import journeymap.api.v2.client.IClientPlugin;
import journeymap.api.v2.common.JourneyMapPlugin;
import journeymap.api.v2.common.waypoint.Waypoint;
import journeymap.api.v2.common.waypoint.WaypointFactory;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.List;

/** Discovered by JourneyMap only when it is installed; never referenced directly by core mcAimod. */
@JourneyMapPlugin(apiVersion = "2.0.0")
public final class KurumiJourneyMapPlugin implements IClientPlugin {
    private static final String MOD_ID = "mcaimod";

    @Override
    public String getModId() {
        return MOD_ID;
    }

    @Override
    public void initialize(IClientAPI api) {
        KurumiJourneyMapBridge.connect(new KurumiJourneyMapBridge.Adapter() {
            @Override
            public List<KurumiJourneyMapBridge.Point> list() {
                requireWorld();
                List<KurumiJourneyMapBridge.Point> result = new ArrayList<>();
                for (Waypoint waypoint : api.getAllWaypoints()) {
                    if (waypoint.getName() == null || waypoint.getName().isBlank()) continue;
                    String dimension = waypoint.getPrimaryDimension();
                    if (dimension == null || dimension.isBlank()) continue;
                    result.add(new KurumiJourneyMapBridge.Point(waypoint.getName(), dimension,
                            waypoint.getX(), waypoint.getY(), waypoint.getZ(),
                            MOD_ID.equals(waypoint.getModId())));
                }
                return List.copyOf(result);
            }

            @Override
            public void save(String name, String dimension, int x, int y, int z) {
                var world = requireWorld();
                if (!dimension.equals(world.dimension().identifier().toString()))
                    throw new IllegalStateException("不能把路径点保存到未进入的维度");
                List<? extends Waypoint> old = api.getAllWaypoints().stream()
                        .filter(w -> name.equalsIgnoreCase(w.getName())
                                && dimension.equals(w.getPrimaryDimension())).toList();
                if (old.stream().anyMatch(w -> !MOD_ID.equals(w.getModId())))
                    throw new IllegalStateException("同名路径点已由 JourneyMap 或其他 Mod 保存，请使用其他名称");
                // Replacing an existing Kurumi marker must not duplicate names on the map.
                for (Waypoint waypoint : old) api.removeWaypoint(MOD_ID, waypoint);
                Waypoint waypoint = WaypointFactory.createWaypoint(MOD_ID,
                        new BlockPos(x, y, z), name, world.dimension(), true);
                api.addWaypoint(MOD_ID, waypoint);
            }

            @Override
            public boolean deleteOwned(String name, String dimension) {
                requireWorld();
                List<? extends Waypoint> found = api.getAllWaypoints().stream()
                        .filter(w -> MOD_ID.equals(w.getModId()) && name.equals(w.getName())
                                && dimension.equals(w.getPrimaryDimension())).toList();
                if (found.size() != 1) {
                    if (found.size() > 1) throw new IllegalStateException("同名地点有多个，请在 JourneyMap 中确认后删除");
                    return false;
                }
                api.removeWaypoint(MOD_ID, found.get(0));
                return true;
            }
        });
    }

    private static net.minecraft.client.multiplayer.ClientLevel requireWorld() {
        var world = Minecraft.getInstance().level;
        if (world == null) throw new IllegalStateException("当前未进入世界，JourneyMap 地点不可用");
        return world;
    }
}
