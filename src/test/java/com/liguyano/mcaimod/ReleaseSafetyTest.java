package com.liguyano.mcaimod;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class ReleaseSafetyTest {
    @Test
    void packagedExampleUsesOnlyEnvironmentReferences() throws Exception {
        try (var input = getClass().getResourceAsStream("/mcaimod-models.example.json")) {
            assertNotNull(input);
            String example = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            assertFalse(example.contains("\"api_key\":"));
            assertTrue(example.contains("DEEPSEEK_API_KEY"));
            assertTrue(example.contains("OPENAI_API_KEY"));
            assertTrue(example.contains("\"share_exact_coordinates\": false"));
        }
        String ignore = Files.readString(Path.of(".gitignore"));
        assertTrue(ignore.contains("config/mcaimod-models.json"));
    }
}
