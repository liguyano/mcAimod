package com.liguyano.mcaimod.client;

import net.neoforged.fml.loading.FMLPaths;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

/**
 * Only local administrator-provided Python modules may bypass the legacy
 * pseudocode parser. This is not a general-purpose Python import permission.
 */
public final class KurumiPythonModulePolicy {
    private static final Pattern IMPORT = Pattern.compile(
            "^(?:import ([A-Za-z_][A-Za-z_0-9]{0,47})|from ([A-Za-z_][A-Za-z_0-9]{0,47}) import [A-Za-z_][A-Za-z_0-9]*(?:\\s*,\\s*[A-Za-z_][A-Za-z_0-9]*)*)$");
    private KurumiPythonModulePolicy() {}

    public static boolean containsImport(String script) {
        return script != null && script.lines().anyMatch(line -> {
            String value = line.stripLeading();
            return value.startsWith("import ") || value.startsWith("from ");
        });
    }

    public static boolean isApprovedImportScript(String script) {
        if (script == null || script.isBlank() || script.length() > 16000
                || !containsImport(script)) return false;
        Path root = FMLPaths.CONFIGDIR.get().resolve("mcaimod-python").toAbsolutePath().normalize();
        int imports = 0;
        for (String line : script.lines().toList()) {
            String trimmed = line.strip();
            if (!trimmed.startsWith("import ") && !trimmed.startsWith("from ")) continue;
            // Module declarations must be at top-level, with no path traversal,
            // aliases, wildcard imports or import chains.
            if (!line.equals(line.stripLeading())) return false;
            var match = IMPORT.matcher(trimmed);
            if (!match.matches()) return false;
            String name = match.group(1) != null ? match.group(1) : match.group(2);
            Path file = root.resolve(name + ".py").normalize();
            if (!file.getParent().equals(root) || !Files.isRegularFile(file)
                    || Files.isSymbolicLink(file)) return false;
            if (++imports > 16) return false;
        }
        return imports > 0;
    }
}
