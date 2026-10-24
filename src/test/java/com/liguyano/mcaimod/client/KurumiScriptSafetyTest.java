package com.liguyano.mcaimod.client;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class KurumiScriptSafetyTest {
    @Test void observingInventoryDoesNotRequireWorldActionPermission() {
        var program = KurumiScriptParser.parse("items = get_inventory()\nobserve(items)").orElseThrow();
        assertTrue(KurumiScriptSafety.isReadOnly(program.statements()));
    }

    @Test void movementStillRequiresPermission() {
        var program = KurumiScriptParser.parse("goto(1,64,3)").orElseThrow();
        assertFalse(KurumiScriptSafety.isReadOnly(program.statements()));
    }
}
