package com.liguyano.mcaimod.client;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class KurumiChestTargetScriptTest {
    @Test void canCheckVerifiedChestOpenWithoutObservingWholeContainer() {
        assertTrue(KurumiScriptParser.parse("""
                chest_target(-21,64,-8)
                opened = chest_open()
                if not opened.completed:
                    raise Exception("箱子没有打开")
                chest_put_item_all("minecraft:cherry_log")
                """).isPresent());
    }

    @Test void canSelectExactChestAndCloseMenu() {
        assertTrue(KurumiScriptParser.parse("""
                chest_target(123,64,-50)
                goto(123,64,-50)
                chest_take("minecraft:cherry_log",32)
                close_container()
                chest_target(130,64,-50)
                chest_put_item_all("minecraft:stick")
                chest_target()
                """).isPresent());
    }

    @Test void canAssignTargetAndCloseResults() {
        assertTrue(KurumiScriptParser.parse("""
                a = chest_target(100,70,200)
                b = close_container()
                """).isPresent());
    }
}
