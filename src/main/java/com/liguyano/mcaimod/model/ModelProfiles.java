package com.liguyano.mcaimod.model;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Reload at server startup; only enabled, explicitly configured profiles are selectable. */
public final class ModelProfiles {
    private final Map<String, ModelProfile> profiles;
    private final boolean shareExactCoordinates;

    public ModelProfiles(Map<String, ModelProfile> profiles, boolean shareExactCoordinates) {
        this.profiles = Collections.unmodifiableMap(new LinkedHashMap<>(profiles));
        this.shareExactCoordinates = shareExactCoordinates;
    }

    public static ModelProfiles load(Path configFile) throws IOException {
        JsonObject root = JsonParser.parseString(Files.readString(configFile, StandardCharsets.UTF_8)).getAsJsonObject();
        JsonArray entries = root.getAsJsonArray("profiles");
        if (entries == null) throw new IllegalArgumentException("Missing profiles array");
        Map<String, ModelProfile> profiles = new LinkedHashMap<>();
        for (JsonElement element : entries) {
            JsonObject item = element.getAsJsonObject();
            if (item.has("apiKey")) {
                throw new IllegalArgumentException("Use api_key (snake_case) in model profiles");
            }
            if (!bool(item, "enabled", true)) continue;
            ModelProfile profile = new ModelProfile(required(item, "name"),
                    URI.create(required(item, "base_url")), required(item, "model_id"),
                    string(item, "api_key_env", ""), Duration.ofSeconds(number(item, "request_timeout", 30)),
                    number(item, "max_output_tokens", 512), string(item, "api_key", ""));
            if (profiles.putIfAbsent(profile.name(), profile) != null) {
                throw new IllegalArgumentException("Duplicate model profile " + profile.name());
            }
        }
        return new ModelProfiles(profiles, bool(root, "share_exact_coordinates", false));
    }

    public Optional<ModelProfile> get(String name) {
        return Optional.ofNullable(profiles.get(name));
    }

    public Set<String> names() {
        return profiles.keySet();
    }

    public boolean shareExactCoordinates() {
        return shareExactCoordinates;
    }

    public Optional<ModelProfile> first() {
        return profiles.values().stream().findFirst();
    }

    private static String required(JsonObject object, String key) {
        if (!object.has(key)) throw new IllegalArgumentException("Missing profile field " + key);
        return object.get(key).getAsString();
    }

    private static String string(JsonObject object, String key, String fallback) {
        return object.has(key) ? object.get(key).getAsString() : fallback;
    }

    private static boolean bool(JsonObject object, String key, boolean fallback) {
        return object.has(key) ? object.get(key).getAsBoolean() : fallback;
    }

    private static int number(JsonObject object, String key, int fallback) {
        return object.has(key) ? object.get(key).getAsInt() : fallback;
    }
}
