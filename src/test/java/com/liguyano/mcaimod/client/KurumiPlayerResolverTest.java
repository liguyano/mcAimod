package com.liguyano.mcaimod.client;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class KurumiPlayerResolverTest {
    private final List<String> online = List.of("SekaiSaionji", "Steve", "Stephanie", "Alex");

    @Test void canonicalizesCaseAndUniqueAbbreviations() {
        assertEquals("Steve", KurumiPlayerResolver.resolve("sTEve", online).name());
        assertEquals("SekaiSaionji", KurumiPlayerResolver.resolve("主人", online).name());
        assertEquals("SekaiSaionji", KurumiPlayerResolver.resolve("sekai", online).name());
        assertEquals("Alex", KurumiPlayerResolver.resolve("Aelx", online).name());
    }

    @Test void ambiguityReturnsCandidatesInsteadOfChoosingWrongPlayer() {
        var result = KurumiPlayerResolver.resolve("ste", online);
        assertNull(result.name());
        assertTrue(result.candidates().contains("Steve"));
        assertTrue(result.candidates().contains("Stephanie"));
    }

    @Test void neverInventsUnknownPlayer() {
        assertNull(KurumiPlayerResolver.resolve("UnknownDude", online).name());
        assertNull(KurumiPlayerResolver.resolve("", online).name());
    }
}
