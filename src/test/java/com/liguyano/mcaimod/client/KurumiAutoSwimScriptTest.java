package com.liguyano.mcaimod.client;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class KurumiAutoSwimScriptTest {
    @Test void letsAgentToggleAutonomousSwimming() {
        assertTrue(KurumiScriptParser.parse("auto_swim_off()\nauto_swim_status()\nauto_swim_on()").isPresent());
    }
}
