package com.liguyano.mcaimod.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ModelProfilesTest {
    @TempDir Path tempDir;

    @Test
    void loadsOnlyEnabledProfilesAndRejectsUnsafeEndpoint() throws Exception {
        Path file = tempDir.resolve("models.json");
        Files.writeString(file, """
                {"profiles":[
                  {"name":"local","base_url":"http://127.0.0.1:11434/v1","model_id":"llama3.2","api_key_env":"","enabled":true},
                  {"name":"disabled","base_url":"https://api.example.com/v1","model_id":"test","enabled":false}
                ]}
                """);
        ModelProfiles profiles = ModelProfiles.load(file);
        assertEquals(1, profiles.names().size());
        assertTrue(profiles.get("local").isPresent());
        assertTrue(profiles.get("disabled").isEmpty());

        Files.writeString(file, """
                {"profiles":[{"name":"bad","base_url":"http://external.example/v1","model_id":"test","enabled":true}]}
                """);
        assertThrows(IllegalArgumentException.class, () -> ModelProfiles.load(file));

    }

    @Test
    void acceptsInlineApiKeyWithoutExposingItInProfileLogs() throws Exception {
        Path file = tempDir.resolve("models.json");
        Files.writeString(file, """
                {"profiles":[{"name":"aliyun","base_url":"https://api.example.com/compatible-mode/v1",
                  "model_id":"qwen3.8-max","api_key":"unit-test-inline-secret","api_key_env":"TEST_KEY"}]}
                """);
        ModelProfile profile = ModelProfiles.load(file).get("aliyun").orElseThrow();
        assertEquals("qwen3.8-max", profile.modelId());
        assertFalse(profile.toString().contains("unit-test-inline-secret"));
    }

    @Test
    void stillRejectsUnknownCamelCaseApiKeyField() throws Exception {
        Path file = tempDir.resolve("models.json");
        Files.writeString(file, """
                {"profiles":[{"name":"bad","base_url":"https://api.example.com/v1",
                  "model_id":"test","apiKey":"unit-test-inline-secret"}]}
                """);
        assertThrows(IllegalArgumentException.class, () -> ModelProfiles.load(file));
    }
}
