package com.liguyano.mcaimod.client;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class KurumiTreeHarvestExampleTest {
    @Test
    void treeHarvestExampleParsesWithCurrentInterpreter() throws Exception {
        String example = Files.readString(Path.of("excaplescript/tree_harvest_16.py"));
        assertTrue(KurumiScriptParser.parse(example).isPresent(), KurumiScriptParser.lastError());
    }
}
