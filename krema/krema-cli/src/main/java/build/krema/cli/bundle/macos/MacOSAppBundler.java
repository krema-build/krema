package build.krema.cli.bundle.macos;

import java.io.IOException;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import build.krema.cli.bundle.AppBundleConfig;
import build.krema.cli.bundle.AppBundler;

/**
 * macOS-specific application bundler.
 * Creates .app bundles with proper Info.plist for native feature support.
 */
public class MacOSAppBundler implements AppBundler {

    @Override
    public Path createDevBundle(AppBundleConfig config) throws IOException {
        Path appDir = config.outputDir().resolve(config.appName() + ".app");
        Path contentsDir = appDir.resolve("Contents");
        Path macOSDir = contentsDir.resolve("MacOS");

        Files.createDirectories(macOSDir);

        // Create Info.plist
        String infoPlist = createDevInfoPlist(config);
        Files.writeString(contentsDir.resolve("Info.plist"), infoPlist);

        // Create launcher script
        String launcher = createDevLauncher(config.appName());
        Path launcherPath = macOSDir.resolve(config.appName());
        Files.writeString(launcherPath, launcher);
        // Set executable for all users (not just owner)
        launcherPath.toFile().setExecutable(true, false);

        // Ad-hoc sign the bundle so macOS Launch Services accepts it
        adHocSign(appDir);

        return appDir;
    }

    @Override
    public Path createProductionBundle(AppBundleConfig config) throws IOException {
        Path appDir = config.outputDir().resolve(config.appName() + ".app");
        Path contentsDir = appDir.resolve("Contents");
        Path macOSDir = contentsDir.resolve("MacOS");
        Path resourcesDir = contentsDir.resolve("Resources");
        Path javaDir = resourcesDir.resolve("Java");

        Files.createDirectories(macOSDir);
        Files.createDirectories(resourcesDir);
        Files.createDirectories(javaDir);

        // Create Info.plist
        String infoPlist = createProductionInfoPlist(config);
        Files.writeString(contentsDir.resolve("Info.plist"), infoPlist);

        // Create launcher script
        String launcher = createProductionLauncher(config);
        Path launcherPath = macOSDir.resolve(config.appName());
        Files.writeString(launcherPath, launcher);
        launcherPath.toFile().setExecutable(true);

        // Copy icon if provided
        if (config.iconPath() != null && Files.exists(config.iconPath())) {
            Files.copy(config.iconPath(), resourcesDir.resolve("app.icns"),
                StandardCopyOption.REPLACE_EXISTING);
        }

        // Copy native library if lib path is provided
        if (config.libPath() != null && Files.isDirectory(config.libPath())) {
            copyNativeLibraries(config.libPath(), resourcesDir);
        }

        return appDir;
    }

    @Override
    public Process launch(Path bundlePath, Map<String, String> env) throws IOException {
        // Write environment variables to a file that the launcher script will read.
        Path envFile = bundlePath.resolve("Contents/MacOS/.krema-env");
        StringBuilder envContent = new StringBuilder();
        for (Map.Entry<String, String> entry : env.entrySet()) {
            envContent.append("export ")
                .append(entry.getKey())
                .append("='")
                .append(entry.getValue().replace("'", "'\\''"))
                .append("'\n");
        }
        Files.writeString(envFile, envContent.toString());

        // Use `open -a` to launch through Launch Services for proper app identity
        // Note: stdout/stderr from the app go to system console, not terminal
        // Check Console.app or use --no-bundle flag to see direct output
        ProcessBuilder pb = new ProcessBuilder(
            "open", "-a", bundlePath.toAbsolutePath().toString(),
            "--wait-apps"
        );
        pb.inheritIO();

        return pb.start();
    }

    @Override
    public boolean requiresBundleForNativeFeatures() {
        return true;
    }

    private String createDevInfoPlist(AppBundleConfig config) {
        StringBuilder sb = new StringBuilder();
        sb.append("""
            <?xml version="1.0" encoding="UTF-8"?>
            <!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
            <plist version="1.0">
            <dict>
                <key>CFBundleIdentifier</key>
                <string>%s</string>
                <key>CFBundleName</key>
                <string>%s</string>
                <key>CFBundleDisplayName</key>
                <string>%s</string>
                <key>CFBundleExecutable</key>
                <string>%s</string>
                <key>CFBundleVersion</key>
                <string>%s</string>
                <key>CFBundleShortVersionString</key>
                <string>%s</string>
                <key>CFBundlePackageType</key>
                <string>APPL</string>
                <key>LSUIElement</key>
                <false/>
                <key>NSHighResolutionCapable</key>
                <true/>
                <key>NSSupportsAutomaticGraphicsSwitching</key>
                <true/>
            """.formatted(
            config.identifier(),
            config.appName(),
            config.appName(),
            config.appName(),
            config.version(),
            config.version()
        ));
        sb.append(buildUrlTypesFragment(config));
        sb.append("""
            </dict>
            </plist>
            """);
        return sb.toString();
    }

    private String createProductionInfoPlist(AppBundleConfig config) {
        StringBuilder sb = new StringBuilder();
        sb.append("""
            <?xml version="1.0" encoding="UTF-8"?>
            <!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
            <plist version="1.0">
            <dict>
                <key>CFBundleName</key>
                <string>%s</string>
                <key>CFBundleDisplayName</key>
                <string>%s</string>
                <key>CFBundleIdentifier</key>
                <string>%s</string>
                <key>CFBundleVersion</key>
                <string>%s</string>
                <key>CFBundleShortVersionString</key>
                <string>%s</string>
                <key>CFBundleExecutable</key>
                <string>%s</string>
                <key>CFBundleIconFile</key>
                <string>app.icns</string>
                <key>CFBundlePackageType</key>
                <string>APPL</string>
                <key>LSMinimumSystemVersion</key>
                <string>10.15</string>
                <key>NSHighResolutionCapable</key>
                <true/>
                <key>NSSupportsAutomaticGraphicsSwitching</key>
                <true/>
            """.formatted(
            config.appName(),
            config.appName(),
            config.identifier(),
            config.version(),
            config.version(),
            config.appName()
        ));
        sb.append(buildUrlTypesFragment(config));
        sb.append("""
            </dict>
            </plist>
            """);
        return sb.toString();
    }

    private String buildUrlTypesFragment(AppBundleConfig config) {
        List<String> schemes = config.deepLinkSchemes();
        if (schemes == null || schemes.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("        <key>CFBundleURLTypes</key>\n");
        sb.append("        <array>\n");
        for (String scheme : schemes) {
            sb.append("            <dict>\n");
            sb.append("                <key>CFBundleURLName</key>\n");
            sb.append("                <string>").append(config.identifier()).append(".").append(scheme).append("</string>\n");
            sb.append("                <key>CFBundleURLSchemes</key>\n");
            sb.append("                <array>\n");
            sb.append("                    <string>").append(scheme).append("</string>\n");
            sb.append("                </array>\n");
            sb.append("            </dict>\n");
        }
        sb.append("        </array>\n");
        return sb.toString();
    }

    private String createDevLauncher(String appName) {
        // Dev launcher reads environment from .krema-env file (written by DevCommand)
        return """
            #!/bin/bash
            # Dev launcher for %s

            DIR="$(cd "$(dirname "$0")" && pwd)"
            LOG_FILE="$HOME/.cache/krema/dev.log"
            mkdir -p "$(dirname "$LOG_FILE")"

            # Source environment variables
            if [ -f "$DIR/.krema-env" ]; then
                source "$DIR/.krema-env"
            fi

            # Find Java
            if [ -n "$JAVA_HOME" ]; then
                JAVA_CMD="$JAVA_HOME/bin/java"
            else
                JAVA_CMD="java"
            fi

            if [ -z "$KREMA_CLASSPATH" ] || [ -z "$KREMA_MAIN_CLASS" ]; then
                echo "Error: KREMA_CLASSPATH and KREMA_MAIN_CLASS must be set" >> "$LOG_FILE"
                exit 1
            fi

            JAVA_OPTS="-XstartOnFirstThread --enable-native-access=ALL-UNNAMED"

            if [ -n "$KREMA_LIB_PATH" ]; then
                JAVA_OPTS="$JAVA_OPTS -Djava.library.path=$KREMA_LIB_PATH"
            fi

            echo "=== Krema Dev Log - $(date) ===" >> "$LOG_FILE"
            echo "Running: $JAVA_CMD $JAVA_OPTS -cp [classpath] $KREMA_MAIN_CLASS" >> "$LOG_FILE"

            # Redirect output to log file (since open -a doesn't preserve stdout)
            exec "$JAVA_CMD" $JAVA_OPTS -cp "$KREMA_CLASSPATH" "$KREMA_MAIN_CLASS" "$@" >> "$LOG_FILE" 2>&1
            """.formatted(appName);
    }

    private String createProductionLauncher(AppBundleConfig config) {
        return """
            #!/bin/bash
            DIR="$(cd "$(dirname "$0")" && pwd)"
            RESOURCES="$DIR/../Resources"
            JAVA="$RESOURCES/Java"

            # Find Java
            if [ -n "$JAVA_HOME" ]; then
                JAVA_CMD="$JAVA_HOME/bin/java"
            else
                JAVA_CMD="java"
            fi

            # Run the application
            exec "$JAVA_CMD" \\
                -XstartOnFirstThread \\
                --enable-native-access=ALL-UNNAMED \\
                -Djava.library.path="$RESOURCES" \\
                -cp "$JAVA/*" \\
                %s "$@"
            """.formatted(config.mainClass());
    }

    private void copyNativeLibraries(Path sourceDir, Path targetDir) throws IOException {
        try (var stream = Files.list(sourceDir)) {
            stream.filter(p -> p.toString().endsWith(".dylib"))
                .forEach(lib -> {
                    try {
                        Files.copy(lib, targetDir.resolve(lib.getFileName()),
                            StandardCopyOption.REPLACE_EXISTING);
                    } catch (IOException e) {
                        System.err.println("[MacOSAppBundler] Failed to copy " + lib + ": " + e.getMessage());
                    }
                });
        }
    }

    /**
     * Returns the path to the Java resources directory inside the bundle.
     * Used by BundleCommand to copy JAR files.
     */
    public static Path getJavaResourcesDir(Path appDir) {
        return appDir.resolve("Contents/Resources/Java");
    }

    /**
     * Returns the path to the Resources directory inside the bundle.
     * Used by BundleCommand to copy native libraries.
     */
    public static Path getResourcesDir(Path appDir) {
        return appDir.resolve("Contents/Resources");
    }

    /**
     * Ad-hoc signs the app bundle so macOS Launch Services accepts it.
     * This is required for `open -a` to work with script-based executables.
     */
    private void adHocSign(Path appDir) throws IOException {
        try {
            ProcessBuilder pb = new ProcessBuilder(
                "codesign", "--force", "--deep", "--sign", "-",
                appDir.toAbsolutePath().toString()
            );
            pb.redirectErrorStream(true);
            Process process = pb.start();
            int exitCode = process.waitFor();

            if (exitCode != 0) {
                String output = new String(process.getInputStream().readAllBytes());
                System.err.println("[MacOSAppBundler] Ad-hoc signing failed: " + output);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while signing bundle", e);
        }
    }
}
