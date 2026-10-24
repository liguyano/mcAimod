package com.liguyano.mcaimod.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Read-only loaded-world block scan; never opens containers or persists coordinates. */
public final class KurumiBlockScanner {
    public static JsonArray search(String id) {
        Minecraft mc=Minecraft.getInstance();
        if (mc.player==null || mc.level==null)
            throw new IllegalArgumentException("未进入游戏世界");
        BlockPos origin=mc.player.blockPosition();
        List<BlockPos> positions=new ArrayList<>();
        for (BlockPos pos : BlockPos.betweenClosed(
                origin.offset(-24,-8,-24),origin.offset(24,8,24))) {
            if (!mc.level.hasChunkAt(pos)) continue;
            if (!KurumiBedMatcher.matches(id, BuiltInRegistries.BLOCK.getKey(
                    mc.level.getBlockState(pos).getBlock()).toString())) continue;
            positions.add(pos.immutable());
        }
        positions.sort(Comparator.comparingDouble(pos -> pos.distSqr(origin)));
        JsonArray result=new JsonArray();
        // Bound to the current script variable limit (8192 serialized characters).
        for (BlockPos pos:positions) {
            if (result.size()>=64) break;
            JsonObject point=new JsonObject();
            point.addProperty("x",pos.getX());
            point.addProperty("y",pos.getY());
            point.addProperty("z",pos.getZ());
            point.addProperty("block_id", BuiltInRegistries.BLOCK.getKey(
                    mc.level.getBlockState(pos).getBlock()).toString());
            if (result.toString().length()+point.toString().length()+2>8000) break;
            result.add(point);
        }
        return result;
    }

    private KurumiBlockScanner() {}
}
