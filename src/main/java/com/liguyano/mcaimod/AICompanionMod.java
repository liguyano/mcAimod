package com.liguyano.mcaimod;

import com.liguyano.mcaimod.chat.AIChatService;
import com.liguyano.mcaimod.chat.ConversationManager;
import com.liguyano.mcaimod.command.AIChatCommand;
import com.liguyano.mcaimod.model.ModelProfiles;
import com.liguyano.mcaimod.model.OpenAiCompatibleProvider;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/** Player-first AI assistant. Commands are registered on the server event bus. */
@Mod(AICompanionMod.MOD_ID)
public final class AICompanionMod {
    public static final String MOD_ID = "mcaimod";

    public AICompanionMod() {
        if (FMLLoader.getCurrent().getDist() == Dist.CLIENT) {
            com.liguyano.mcaimod.client.KurumiClientController.install();
        }
        Path configFile = FMLPaths.CONFIGDIR.get().resolve("mcaimod-models.json");
        installDefaultConfig(configFile);
        ModelProfiles profiles;
        try {
            profiles = ModelProfiles.load(configFile);
        } catch (IOException | RuntimeException error) {
            // Keep the server bootable, so administrators can inspect and repair config.
            org.slf4j.LoggerFactory.getLogger(AICompanionMod.class)
                    .warn("AI Companion model configuration unavailable: {}", error.getClass().getSimpleName());
            profiles = new ModelProfiles(Map.of(), false);
        }
        AIChatCommand commands = new AIChatCommand(new AIChatService(
                new ConversationManager(), profiles, new OpenAiCompatibleProvider()));
        NeoForge.EVENT_BUS.addListener(commands::register);
        NeoForge.EVENT_BUS.addListener(commands::onLogout);
    }

    private static void installDefaultConfig(Path target) {
        if (Files.exists(target)) return;
        try (var example = AICompanionMod.class.getResourceAsStream("/mcaimod-models.example.json")) {
            if (example != null) {
                Files.createDirectories(target.getParent());
                Files.copy(example, target);
            }
        } catch (IOException error) {
            org.slf4j.LoggerFactory.getLogger(AICompanionMod.class)
                    .warn("Could not create AI Companion example configuration");
        }
    }
}
