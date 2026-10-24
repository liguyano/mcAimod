package com.liguyano.mcaimod.client;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class KurumiFishingScriptTest {
    @Test void fishingStartAndStopAreValidatedScriptCalls() {
        assertTrue(KurumiScriptParser.parse("fish()").isPresent());
        assertTrue(KurumiScriptParser.parse("stop_fishing()").isPresent());
        assertTrue(KurumiScriptParser.parse("observe(get_fishing_status())").isPresent());
        assertTrue(KurumiScriptParser.parse("fish(999)").isPresent()); // Runtime rejects nonzero args.
    }
}
