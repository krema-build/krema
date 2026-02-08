package build.krema.cli.bundle.linux;

import java.io.IOException;
import java.nio.file.*;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import build.krema.cli.bundle.AppBundleConfig;
import build.krema.cli.bundle.AppBundler;

/**
 * Linux-specific application bundler.
 *
 * <p>Dev bundle: creates a launcher script + .krema-env for environment variables.</p>
 * <p>Production bundle: creates an AppDir structure suitable for AppImage, .deb, or .rpm packaging.</p>
 */
public class LinuxAppBundler implements AppBundler {

    @Override
    public Path createDevBundle(AppBundleConfig config) throws IOException {
        Path bundleDir = config.outputDir().resolve(config.appName() + "-dev");
        Files.createDirectories(bundleDir);

        String launcher = createDevLauncher(config.appName());
        Path launcherPath = bundleDir.resolve(config.appName());
        Files.writeString(launcherPath, launcher);
        launcherPath.toFile().setExecutable(true, false);

        return bundleDir;
    }

    @Override
    public Path createProductionBundle(AppBundleConfig config) throws IOException {
        Path appDir = config.outputDir().resolve(config.appName() + ".AppDir");
        Path usrDir = appDir.resolve("usr");
        Path binDir = usrDir.resolve("bin");
        Path libDir = usrDir.resolve("lib");
        Path shareDir = usrDir.resolve("share").resolve(config.appName()).resolve("java");

        Files.createDirectories(binDir);
        Files.createDirectories(libDir);
        Files.createDirectories(shareDir);

        // Create AppRun launcher (entry point for AppImage)
        String appRun = createAppRunLauncher(config);
        Path appRunPath = appDir.resolve("AppRun");
        Files.writeString(appRunPath, appRun);
        appRunPath.toFile().setExecutable(true, false);

        // Create usr/bin launcher
        String binLauncher = createProductionLauncher(config);
        Path binLauncherPath = binDir.resolve(config.appName());
        Files.writeString(binLauncherPath, binLauncher);
        binLauncherPath.toFile().setExecutable(true, false);

        // Create .desktop file
        String desktop = createDesktopFile(config);
        Files.writeString(appDir.resolve(config.appName().toLowerCase() + ".desktop"), desktop);

        // Copy icon if provided
        if (config.iconPath() != null && Files.exists(config.iconPath())) {
            Files.copy(config.iconPath(),
                appDir.resolve(config.appName().toLowerCase() + ".png"),
                StandardCopyOption.REPLACE_EXISTING);
        }

        // Copy native libraries
        if (config.libPath() != null && Files.isDirectory(config.libPath())) {
            copyNativeLibraries(config.libPath(), libDir);
        }

        System.out.println("AppDir created at: " + appDir);
        System.out.println();
        System.out.println("To create distributable packages:");
        System.out.println();
        System.out.println("  # AppImage (requires appimagetool):");
        System.out.println("  appimagetool " + appDir.toAbsolutePath());
        System.out.println();
        System.out.println("  # .deb package:");
        System.out.println("  jpackage --type deb --input " + shareDir.toAbsolutePath() +
            " --main-jar " + config.appName() + ".jar" +
            " --main-class " + config.mainClass() +
            " --name " + config.appName() +
            " --app-version " + config.version());
        System.out.println();
        System.out.println("  # .rpm package:");
        System.out.println("  jpackage --type rpm --input " + shareDir.toAbsolutePath() +
            " --main-jar " + config.appName() + ".jar" +
            " --main-class " + config.mainClass() +
            " --name " + config.appName() +
            " --app-version " + config.version());

        return appDir;
    }

    @Override
    public Process launch(Path bundlePath, Map<String, String> env) throws IOException {
        // Write environment variables to .krema-env
        Path envFile = bundlePath.resolve(".krema-env");
        StringBuilder envContent = new StringBuilder();
        for (Map.Entry<String, String> entry : env.entrySet()) {
            envContent.append("export ")
                .append(entry.getKey())
                .append("='")
                .append(entry.getValue().replace("'", "'\\''"))
                .append("'\n");
        }
        Files.writeString(envFile, envContent.toString());

        // Find the launcher script
        Path launcher;
        if (Files.exists(bundlePath.resolve("AppRun"))) {
            launcher = bundlePath.resolve("AppRun");
        } else {
            // Dev bundle: launcher is named after the app
            try (var stream = Files.list(bundlePath)) {
                launcher = stream
                    .filter(p -> Files.isExecutable(p) && !p.getFileName().toString().startsWith("."))
                    .findFirst()
                    .orElseThrow(() -> new IOException("No executable launcher found in " + bundlePath));
            }
        }

        ProcessBuilder pb = new ProcessBuilder(launcher.toAbsolutePath().toString());
        pb.inheritIO();

        return pb.start();
    }

    @Override
    public boolean requiresBundleForNativeFeatures() {
        return false;
    }

    private String createDevLauncher(String appName) {
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
                echo "Error: KREMA_CLASSPATH and KREMA_MAIN_CLASS must be set" >&2
                exit 1
            fi

            JAVA_OPTS="--enable-native-access=ALL-UNNAMED"

            if [ -n "$KREMA_LIB_PATH" ]; then
                JAVA_OPTS="$JAVA_OPTS -Djava.library.path=$KREMA_LIB_PATH"
            fi

            exec "$JAVA_CMD" $JAVA_OPTS -cp "$KREMA_CLASSPATH" "$KREMA_MAIN_CLASS" "$@"
            """.formatted(appName);
    }

    private String createAppRunLauncher(AppBundleConfig config) {
        return """
            #!/bin/bash
            # AppRun launcher for %s

            DIR="$(cd "$(dirname "$0")" && pwd)"
            exec "$DIR/usr/bin/%s" "$@"
            """.formatted(config.appName(), config.appName());
    }

    private String createProductionLauncher(AppBundleConfig config) {
        return """
            #!/bin/bash
            DIR="$(cd "$(dirname "$0")" && pwd)"
            USR="$DIR/.."
            LIB="$USR/lib"
            JAVA_DIR="$USR/share/%s/java"

            # Source environment variables if present
            APPDIR="$USR/.."
            if [ -f "$APPDIR/.krema-env" ]; then
                source "$APPDIR/.krema-env"
            fi

            # Find Java
            if [ -n "$JAVA_HOME" ]; then
                JAVA_CMD="$JAVA_HOME/bin/java"
            else
                JAVA_CMD="java"
            fi

            exec "$JAVA_CMD" \\
                --enable-native-access=ALL-UNNAMED \\
                -Djava.library.path="$LIB" \\
                -cp "$JAVA_DIR/*" \\
                %s "$@"
            """.formatted(config.appName(), config.mainClass());
    }

    private String createDesktopFile(AppBundleConfig config) {
        String icon = config.appName().toLowerCase();
        List<String> schemes = config.deepLinkSchemes();
        boolean hasSchemes = schemes != null && !schemes.isEmpty();

        StringBuilder sb = new StringBuilder();
        sb.append("[Desktop Entry]\n");
        sb.append("Name=").append(config.appName()).append("\n");
        sb.append("Exec=").append(config.appName()).append(hasSchemes ? " %u" : "").append("\n");
        sb.append("Icon=").append(icon).append("\n");
        sb.append("Terminal=false\n");
        sb.append("Type=Application\n");
        sb.append("Categories=Utility;\n");

        if (hasSchemes) {
            String mimeTypes = schemes.stream()
                .map(s -> "x-scheme-handler/" + s)
                .collect(Collectors.joining(";"));
            sb.append("MimeType=").append(mimeTypes).append(";\n");
        }

        return sb.toString();
    }

    private void copyNativeLibraries(Path sourceDir, Path targetDir) throws IOException {
        try (var stream = Files.list(sourceDir)) {
            stream.filter(p -> p.toString().endsWith(".so") || p.toString().contains(".so."))
                .forEach(lib -> {
                    try {
                        Files.copy(lib, targetDir.resolve(lib.getFileName()),
                            StandardCopyOption.REPLACE_EXISTING);
                    } catch (IOException e) {
                        System.err.println("[LinuxAppBundler] Failed to copy " + lib + ": " + e.getMessage());
                    }
                });
        }
    }

    /**
     * Returns the path to the Java share directory inside the AppDir bundle.
     */
    public static Path getJavaShareDir(Path appDir) {
        String appName = appDir.getFileName().toString().replace(".AppDir", "");
        return appDir.resolve("usr/share/" + appName + "/java");
    }

    /**
     * Returns the path to the lib directory inside the AppDir bundle.
     */
    public static Path getLibDir(Path appDir) {
        return appDir.resolve("usr/lib");
    }
}
