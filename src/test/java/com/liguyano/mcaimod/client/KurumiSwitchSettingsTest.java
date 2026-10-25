package com.liguyano.mcaimod.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class KurumiSwitchSettingsTest {
    @Test void missingOptionsDefaultToOff() {
        assertEquals(new KurumiSwitchSettings(false, false), KurumiSwitchSettings.parse("{}"));
    }

    @Test void explicitlyEnabledOptionsSurviveRoundTrip() {
        var state = new KurumiSwitchSettings(true, true);
        assertEquals(state, KurumiSwitchSettings.parse(state.toJson()));
    }

    @Test void rejectsInvalidBooleanInsteadOfAccidentallyEnablingAutomation() {
        assertThrows(IllegalArgumentException.class,
                () -> KurumiSwitchSettings.parse("{\"auto_enabled\": \"true\"}"));
    }
}
