package com.liguyano.mcaimod.client;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class KurumiFarmIntentTest {
    @Test void recognizesFarming() {
        var intent = KurumiFarmIntent.parse("Kurumi，去种田");
        assertTrue(intent.isPresent());
        assertEquals(KurumiFarmIntent.Kind.FARM, intent.get().kind());
        assertEquals(16, intent.get().radius());
    }

    @Test void recognizesStop() {
        assertEquals(KurumiFarmIntent.Kind.STOP,
                KurumiFarmIntent.parse("kurumi 停止").orElseThrow().kind());
    }

    @Test void unrelatedMessagesNeverTriggerActions() {
        assertTrue(KurumiFarmIntent.parse("大家去种田").isEmpty());
        assertTrue(KurumiFarmIntent.parse("kurumi 你好").isEmpty());
        assertTrue(KurumiFarmIntent.parse("kurumix farm").isEmpty());
    }
}
