package com.liguyano.mcaimod.client;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class KurumiLocationShortcutTest {
    @Test void onlySpacedWhereQueriesAreLocalShortcuts() {
        assertTrue(KurumiLocationMemory.isWhereShortcut("仓库 在哪里"));
        assertTrue(KurumiLocationMemory.isWhereShortcut("家 在哪"));
        assertFalse(KurumiLocationMemory.isWhereShortcut("仓库在哪里"));
        assertFalse(KurumiLocationMemory.isWhereShortcut("家在哪儿"));
    }

    @Test void onlySpacedGoRequestsUseTheLocationShortcut() {
        assertTrue(KurumiLocationMemory.isGoShortcut("去 仓库"));
        assertFalse(KurumiLocationMemory.isGoShortcut("去仓库"));
    }

    @Test void whereShortcutDoesNotCaptureGoCommands() {
        assertFalse(KurumiLocationMemory.isWhereShortcut("去 仓库"));
        assertFalse(KurumiLocationMemory.isWhereShortcut("去 100 64 200"));
    }
}
