package com.liguyano.mcaimod.client;

import net.minecraft.core.BlockPos;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class KurumiChestTargetScriptTest {
    @Test void correctsOneNearbyChestButRefusesAmbiguousTargets() {
        BlockPos chest = new BlockPos(-21, 64, -8);
        assertEquals(chest, KurumiChestBridge.uniqueCandidate(List.of(chest)));
        assertNull(KurumiChestBridge.uniqueCandidate(List.of()));
        assertNull(KurumiChestBridge.uniqueCandidate(List.of(chest, new BlockPos(-20, 64, -7))));
    }

    @Test void canSelectExactChestAndCloseMenu() {
        assertTrue(KurumiScriptParser.parse("""
                chest_target(123,64,-50)
                goto(123,64,-50)
                chest_take("minecraft:cherry_log",32)
                close_container()
                chest_target(130,64,-50)
                chest_put_item_all("minecraft:stick")
                chest_target()
                """).isPresent());
    }

    @Test void canAssignTargetAndCloseResults() {
        assertTrue(KurumiScriptParser.parse("""
                a = chest_target(100,70,200)
                b = close_container()
                """).isPresent());
    }
}
