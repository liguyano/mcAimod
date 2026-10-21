package com.liguyano.mcaimod.context;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Only call from the Minecraft server thread. Does not access or load distant chunks. */
public final class GameContextCollector {
    private static final double RADIUS = 16;

    private GameContextCollector() {}

    public static ActorSnapshot capture(ServerPlayer player) {
        ServerLevel level = (ServerLevel) player.level();
        BlockPos pos = player.blockPosition();
        String dimension = level.dimension().identifier().toString();
        String biome = level.getBiome(pos).getRegisteredName();
        String weather = !dimension.equals("minecraft:overworld") ? "not_applicable"
                : level.isThundering() ? "thunder"
                : level.isRaining() ? "rain" : "clear";

        List<String> armor = new ArrayList<>();
        for (EquipmentSlot slot : List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST,
                EquipmentSlot.LEGS, EquipmentSlot.FEET)) {
            armor.add(itemId(player.getItemBySlot(slot)));
        }

        List<NearbyEntity> nearby = level.getEntities(player,
                player.getBoundingBox().inflate(RADIUS),
                entity -> entity.isAlive() && entity.distanceToSqr(player) <= RADIUS * RADIUS)
                .stream()
                .map(entity -> new NearbyEntity(BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString(),
                        Math.sqrt(entity.distanceToSqr(player))))
                .toList();

        return new ActorSnapshot(player.getUUID(), "player", Instant.now(), dimension,
                pos.getX(), pos.getY(), pos.getZ(), biome, weather, level.getOverworldClockTime(),
                player.getHealth(), player.getMaxHealth(), player.getFoodData().getFoodLevel(),
                itemId(player.getMainHandItem()), itemId(player.getOffhandItem()), armor,
                player.getActiveEffects().stream().map(effect -> effect.getEffect().getRegisteredName()).toList(),
                nearby);
    }

    private static String itemId(net.minecraft.world.item.ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    }
}
