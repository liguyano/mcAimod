package com.liguyano.mcaimod.client;

import org.junit.jupiter.api.Test;
import java.util.HashSet;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class KurumiFishingLogicTest {
    @Test void picksShoreNearOpenFiveByFiveWaterAndRejectsPuddle() {
        Set<KurumiFishingLogic.Point> water = new HashSet<>();
        for (int x=0;x<7;x++) for(int z=0;z<7;z++) water.add(new KurumiFishingLogic.Point(x,63,z));
        var origin = new KurumiFishingLogic.Point(-3,64,2);
        var spot = KurumiFishingLogic.choose(water, origin,
                pos -> pos.equals(new KurumiFishingLogic.Point(-1,64,3)));
        assertTrue(spot.isPresent());
        assertEquals(new KurumiFishingLogic.Point(-1,64,3), spot.get().stand());
        assertTrue(KurumiFishingLogic.choose(Set.of(new KurumiFishingLogic.Point(0,63,0)), origin,
                pos -> true).isEmpty());
    }

    @Test void ignoresFallingBobberAndDetectsSuddenDownwardBite() {
        assertFalse(KurumiFishingLogic.bite(10, true, -0.32, 0));
        assertFalse(KurumiFishingLogic.bite(50, false, -0.32, 0));
        assertFalse(KurumiFishingLogic.bite(60, true, -0.04, -0.02));
        assertTrue(KurumiFishingLogic.bite(60, true, -0.27, 0.01));
        assertFalse(KurumiFishingLogic.bite(61, true, -0.26, -0.27));
    }
}
