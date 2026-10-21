package com.liguyano.mcaimod.client;

import org.junit.jupiter.api.Test;
import java.time.Clock;
import java.time.Duration;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;

class KurumiClientLogicTest {
    @Test void wakeWordIsNaturalAndBounded() {
        assertTrue(KurumiWakeRouter.mentionsKurumi("Kurumi，来一下"));
        assertTrue(KurumiWakeRouter.mentionsKurumi("嘿kurumi帮我"));
        assertFalse(KurumiWakeRouter.mentionsKurumi("kurumix"));
        assertFalse(KurumiWakeRouter.mentionsKurumi("xxkurumi"));
        assertFalse(KurumiWakeRouter.mentionsKurumi("没叫她"));
    }

    @Test void gatePreventsConcurrentRequests() {
        var gate = new KurumiWakeRouter(Clock.systemUTC(), Duration.ofSeconds(30));
        assertTrue(gate.tryAccept("kurumi"));
        assertFalse(gate.tryAccept("kurumi"));
        gate.finish();
        assertFalse(gate.tryAccept("kurumi"));
        gate.reset();
        assertTrue(gate.tryAccept("kurumi"));
    }

    @Test void rejectsUnboundedOrUnlistedActions() {
        assertEquals(Optional.empty(), KurumiActionValidator.validate("run_command", "", 0, 0));
        assertEquals(Optional.empty(), KurumiActionValidator.validate("farm", "", 0, 10000));
        assertEquals(Optional.empty(), KurumiActionValidator.validate("chest_take", "minecraft:diamond", 999, 0));
        assertEquals(Optional.empty(), KurumiActionValidator.validate("chest_take", "../secret", 1, 0));
        assertTrue(KurumiActionValidator.validate("farm", "", 0, 16).isPresent());
        assertTrue(KurumiActionValidator.validate("chest_take", "minecraft:iron_ingot", 10, 0).isPresent());
    }
}
