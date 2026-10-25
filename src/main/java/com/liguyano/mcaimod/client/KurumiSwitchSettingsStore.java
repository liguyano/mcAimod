package com.liguyano.mcaimod.client;

import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** Config lives next to mcaimod-models.json, and is never a world-scoped task flag. */
final class KurumiSwitchSettingsStore {
    static KurumiSwitchSettings load() {
        Path file = path();
        try {
            if (Files.notExists(file)) {
                KurumiSwitchSettings defaults = new KurumiSwitchSettings(false, false);
                save(defaults);
                return defaults;
            }
            return KurumiSwitchSettings.parse(Files.readString(file, StandardCharsets.UTF_8));
        } catch (IOException | RuntimeException ex) {
            KurumiWebMonitor.log("Kurumi 开关配置读取失败，安全起见两项均关闭：" + ex.getMessage());
            return new KurumiSwitchSettings(false, false);
        }
    }

    static boolean save(KurumiSwitchSettings value) {
        Path file = path();
        try {
            Files.createDirectories(file.getParent());
            Path pending = file.resolveSibling(file.getFileName() + ".tmp");
            Files.writeString(pending, value.toJson(), StandardCharsets.UTF_8);
            try {
                Files.move(pending, file, StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException ex) {
                Files.move(pending, file, StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } catch (IOException | RuntimeException ex) {
            KurumiWebMonitor.log("Kurumi 开关配置保存失败：" + ex.getMessage());
            return false;
        }
    }

    private static Path path() {
        return FMLPaths.CONFIGDIR.get().resolve("mcaimod-settings.json");
    }

    private KurumiSwitchSettingsStore() {}
}
