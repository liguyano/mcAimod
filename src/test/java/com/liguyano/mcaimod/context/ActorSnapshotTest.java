package com.liguyano.mcaimod.context;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ActorSnapshotTest {
    @Test
    void snapshotCopiesInputsAndBoundsNearbyToTwelveNearest() {
        List<NearbyEntity> nearby = new ArrayList<>();
        for (int i = 20; i >= 0; i--) {
            nearby.add(new NearbyEntity("minecraft:zombie", i));
        }
        List<String> armor = new ArrayList<>(List.of("minecraft:iron_helmet"));
        ActorSnapshot snapshot = new ActorSnapshot(UUID.randomUUID(), "player", Instant.now(),
                "minecraft:overworld", 4, 70, -12, "minecraft:plains", "clear", 1400,
                16, 20, 14, "minecraft:stone_sword", "minecraft:air", armor,
                List.of("minecraft:speed"), nearby);

        armor.clear();
        nearby.clear();
        assertEquals(List.of("minecraft:iron_helmet"), snapshot.armor());
        assertEquals(12, snapshot.nearby().size());
        assertEquals(0, snapshot.nearby().getFirst().distance());
        assertEquals(11, snapshot.nearby().getLast().distance());
        assertThrows(UnsupportedOperationException.class, () -> snapshot.nearby().clear());
    }

    @Test
    void missingBiomeAndWeatherBecomeUnknown() {
        ActorSnapshot snapshot = new ActorSnapshot(UUID.randomUUID(), "player", Instant.now(),
                "minecraft:the_nether", 0, 60, 0, null, null, 0,
                20, 20, 20, "minecraft:air", "minecraft:air", List.of(), List.of(), List.of());
        assertEquals("unknown", snapshot.biome());
        assertEquals("unknown", snapshot.weather());
    }
}
