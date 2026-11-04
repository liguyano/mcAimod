package com.liguyano.mcaimod.client;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class KurumiPlayerEntityMatcherTest {
    @Test void uuidWinsOverDisplayName() {
        UUID expected = UUID.randomUUID();
        var unrelated = new KurumiPlayerEntityMatcher.Candidate(UUID.randomUUID(), "PlayerABC", "PlayerABC");
        var target = new KurumiPlayerEntityMatcher.Candidate(expected, "OtherName", "OtherName");
        assertEquals(target, KurumiPlayerEntityMatcher.select("PlayerABC", expected, List.of(unrelated, target)));
    }

    @Test void usesExactCaseInsensitiveNameWhenUuidUnavailable() {
        var target = new KurumiPlayerEntityMatcher.Candidate(UUID.randomUUID(), "playerabc", "some display");
        assertEquals(target, KurumiPlayerEntityMatcher.select("PlayerABC", null, List.of(target)));
    }

    @Test void rejectsAmbiguousNameFallback() {
        var a = new KurumiPlayerEntityMatcher.Candidate(UUID.randomUUID(), "PlayerABC", "PlayerABC");
        var b = new KurumiPlayerEntityMatcher.Candidate(UUID.randomUUID(), "playerabc", "playerabc");
        assertNull(KurumiPlayerEntityMatcher.select("PlayerABC", null, List.of(a, b)));
    }
}
