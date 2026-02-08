package build.krema.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Loads environment variables from .env files.
 * Supports a base .env file and profile-specific .env.{profile} files.
 */
public final class DotEnvLoader {

    private DotEnvLoader() {}

    /**
     * Loads environment variables from .env and .env.{profile} files.
     * Profile-specific values override base values.
     *
     * @param projectDir the directory containing the .env files
     * @param profile    the environment profile name (e.g., "development", "staging")
     * @return merged map of environment variables
     */
    public static Map<String, String> load(Path projectDir, String profile) {
        Map<String, String> vars = new LinkedHashMap<>();

        // Load base .env file
        Path baseEnv = projectDir.resolve(".env");
        if (Files.isRegularFile(baseEnv)) {
            parseInto(baseEnv, vars);
        }

        // Load profile-specific .env.{profile} file (overrides base)
        if (profile != null && !profile.isEmpty()) {
            Path profileEnv = projectDir.resolve(".env." + profile);
            if (Files.isRegularFile(profileEnv)) {
                parseInto(profileEnv, vars);
            }
        }

        return vars;
    }

    private static void parseInto(Path file, Map<String, String> vars) {
        try {
            for (String line : Files.readAllLines(file)) {
                String trimmed = line.trim();

                // Skip blank lines and comments
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }

                // Split on first '=' only
                int eqIndex = trimmed.indexOf('=');
                if (eqIndex < 0) {
                    continue;
                }

                String key = trimmed.substring(0, eqIndex).trim();
                String value = trimmed.substring(eqIndex + 1).trim();

                // Strip surrounding quotes
                if (value.length() >= 2) {
                    char first = value.charAt(0);
                    char last = value.charAt(value.length() - 1);
                    if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
                        value = value.substring(1, value.length() - 1);
                    }
                }

                vars.put(key, value);
            }
        } catch (IOException e) {
            // Silently skip unreadable files
        }
    }
}
