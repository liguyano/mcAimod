package com.liguyano.mcaimod.client;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class KurumiObservationGuidanceTest {
    @Test void unknownChestContentsRequireObservationBeforeSelectingAnItem() {
        String guide = KurumiObservationGuidance.text();
        assertTrue(guide.contains("observe(inspect_container())"));
        assertTrue(guide.contains("不要预先枚举"));
        assertTrue(guide.contains("take_item"));
        assertTrue(guide.contains("重新观察"));
    }
}
