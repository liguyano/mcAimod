package com.liguyano.mcaimod.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class KurumiTakeAllScriptTest {
    @Test void acceptsTakeAllOnCurrentOpenContainer() {
        assertTrue(KurumiScriptParser.parse("take_all_items()").isPresent());
        assertTrue(KurumiScriptParser.parse("take_all_items()\nobserve(inspect_container())").isPresent());
    }

    @Test void rejectsUnexpectedTakeAllArguments() {
        assertTrue(KurumiScriptParser.parse("take_all_items(64)").isEmpty());
    }
}
