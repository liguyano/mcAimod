package com.liguyano.mcaimod.client;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class KurumiTreePlantingExampleTest {
    @Test
    void treePlantingExampleUsesSupportedPseudoSyntax() throws Exception {
        String code = Files.readString(Path.of("excaplescript/tree_planting.py"));
        assertTrue(KurumiScriptParser.parse(code).isPresent(), KurumiScriptParser.lastError());
    }
}
