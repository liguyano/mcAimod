package com.liguyano.mcaimod.context;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Immutable game data copied on the server thread; safe to pass to an HTTP worker. */
public record ActorSnapshot(UUID actorId, String actorType, Instant capturedAt, String dimension,
                            int x, int y, int z, String biome, String weather, long dayTime,
                            float health, float maxHealth, int food, String mainHand, String offHand,
                            List<String> armor, List<String> effects, List<NearbyEntity> nearby) {
    public ActorSnapshot {
        Objects.requireNonNull(actorId);
        Objects.requireNonNull(capturedAt);
        actorType = unknownIfBlank(actorType);
        dimension = unknownIfBlank(dimension);
        biome = unknownIfBlank(biome);
        weather = unknownIfBlank(weather);
        mainHand = unknownIfBlank(mainHand);
        offHand = unknownIfBlank(offHand);
        armor = List.copyOf(Objects.requireNonNull(armor));
        effects = List.copyOf(Objects.requireNonNull(effects));
        nearby = Objects.requireNonNull(nearby).stream()
                .sorted(Comparator.comparingDouble(NearbyEntity::distance)).limit(12).toList();
    }

    private static String unknownIfBlank(String value) {
        return value == null || value.isBlank() ? "unknown" : value;
    }
}
