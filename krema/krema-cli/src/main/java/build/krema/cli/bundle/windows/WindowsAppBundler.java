package build.krema.cli.bundle.windows;

import static build.krema.cli.bundle.windows.WindowsRegistryHelper.*;

import java.io.IOException;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import build.krema.cli.bundle.AppBundleConfig;
import build.krema.cli.bundle.AppBundler;

/**
 * Windows-specific application bundler using jpackage.
 * Creates .exe and .msi installers for distribution.
 */
public class WindowsAppBundler implements AppBundler {

    @Override
    public Path createDevBundle(AppBundleConfig config) throws IOException {
        // Windows doesn't require bundling for dev mode.
        // The app runs as a regular Java process; native features work without
        // a formal bundle structure.
        return null;
    }

    @Override
    public Path createProductionBundle(AppBundleConfig config) throws IOException {
        String jpackagePath = findJpackage();
        if (jpackagePath == null) {
            throw new IOException("jpackage not found. Requires JDK 16+ with jpackage available.");
        }

        Path outputDir = config.outputDir().resolve("bundle").resolve("windows");
        Files.createDirectories(outputDir);

        // Use outputDir as input directory (where the jar and libs are)
        Path inputDir = config.outputDir();

        List<String> command = new ArrayList<>();
        command.add(jpackagePath);
        command.add("--type");
        command.add("exe");
        command.add("--input");
        command.add(inputDir.toAbsolutePath().toString());
        command.add("--main-jar");
        command.add(config.appName() + ".jar");
        command.add("--main-class");
        command.add(config.mainClass());
        command.add("--name");
        command.add(config.appName());
        command.add("--app-version");
        command.add(config.version());
        command.add("--dest");
        command.add(outputDir.toAbsolutePath().toString());

        if (config.iconPath() != null && Files.exists(config.iconPath())) {
            command.add("--icon");
            command.add(config.iconPath().toAbsolutePath().toString());
        }

        // Copy native libraries into input directory so jpackage includes them
        if (config.libPath() != null && Files.isDirectory(config.libPath())) {
            try (var files = Files.list(config.libPath())) {
                files.filter(p -> p.toString().endsWith(".dll"))
                     .forEach(dll -> {
                         try {
                             Files.copy(dll, inputDir.resolve(dll.getFileName()),
                                 StandardCopyOption.REPLACE_EXISTING);
                         } catch (IOException e) {
                             System.err.println("[WindowsAppBundler] Warning: failed to copy " + dll);
                         }
                     });
            }
        }

        // Windows-specific options
        command.add("--win-dir-chooser");
        command.add("--win-shortcut");
        command.add("--win-menu");

        System.out.println("[WindowsAppBundler] Running jpackage...");

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.inheritIO();
        Process process = pb.start();

        try {
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new IOException("jpackage failed with exit code " + exitCode);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("jpackage interrupted", e);
        }

        System.out.println("[WindowsAppBundler] Bundle created at: " + outputDir);

        // Generate URL protocol registration scripts if deep link schemes are configured
        List<String> schemes = config.deepLinkSchemes();
        if (schemes != null && !schemes.isEmpty()) {
            generateProtocolRegistrationScripts(config, outputDir);
        }

        return outputDir;
    }

    /**
     * Generates URL protocol registration files for deep link support.
     * Creates .reg file, PowerShell registration script, and unregistration script.
     */
    private void generateProtocolRegistrationScripts(AppBundleConfig config, Path outputDir) {
        List<String> schemes = config.deepLinkSchemes();
        String appName = config.appName();

        // Determine the installed exe path (standard jpackage location)
        // Users should update this path if they customize the install location
        String defaultInstallPath = "C:\\Program Files\\" + appName + "\\" + appName + ".exe";

        try {
            // Generate .reg file for manual import
            Path regFile = outputDir.resolve(appName + "-protocols.reg");
            generateRegFile(schemes, defaultInstallPath, appName, regFile);
            System.out.println("[WindowsAppBundler] Generated protocol registry file: " + regFile);

            // Generate PowerShell registration script
            Path registerScript = outputDir.resolve("register-protocols.ps1");
            generatePowerShellScript(schemes, defaultInstallPath, appName, registerScript);
            System.out.println("[WindowsAppBundler] Generated registration script: " + registerScript);

            // Generate PowerShell unregistration script
            Path unregisterScript = outputDir.resolve("unregister-protocols.ps1");
            generateUnregisterScript(schemes, unregisterScript);
            System.out.println("[WindowsAppBundler] Generated unregistration script: " + unregisterScript);

            // Print instructions
            System.out.println();
            System.out.println("[WindowsAppBundler] Deep link protocols configured: " + String.join(", ", schemes));
            System.out.println("[WindowsAppBundler] To enable deep links, choose one of:");
            System.out.println("  1. Double-click " + regFile.getFileName() + " to import registry entries");
            System.out.println("  2. Run: powershell -ExecutionPolicy Bypass -File " + registerScript.getFileName());
            System.out.println("  Note: Update the exe path in scripts if using a custom install location");

        } catch (IOException e) {
            System.err.println("[WindowsAppBundler] Warning: Failed to generate protocol scripts: " + e.getMessage());
        }
    }

    @Override
    public Process launch(Path bundlePath, Map<String, String> env) throws IOException {
        // Find the .exe in the bundle directory
        Path exe = null;
        try (var stream = Files.list(bundlePath)) {
            exe = stream
                .filter(p -> p.toString().endsWith(".exe"))
                .findFirst()
                .orElse(null);
        }
        if (exe == null) {
            throw new IOException("No .exe found in bundle: " + bundlePath);
        }

        ProcessBuilder pb = new ProcessBuilder(exe.toAbsolutePath().toString());
        if (env != null) {
            pb.environment().putAll(env);
        }
        pb.inheritIO();
        return pb.start();
    }

    @Override
    public boolean requiresBundleForNativeFeatures() {
        return false;
    }

    private static String findJpackage() {
        // Check JAVA_HOME first
        String javaHome = System.getenv("JAVA_HOME");
        if (javaHome != null) {
            Path path = Path.of(javaHome, "bin", "jpackage.exe");
            if (Files.isExecutable(path)) return path.toString();
        }

        // Check current JDK
        String jdkHome = System.getProperty("java.home");
        if (jdkHome != null) {
            Path path = Path.of(jdkHome, "bin", "jpackage.exe");
            if (Files.isExecutable(path)) return path.toString();
        }

        // Check PATH
        try {
            Process p = new ProcessBuilder("where", "jpackage").start();
            String output = new String(p.getInputStream().readAllBytes()).strip();
            if (p.waitFor() == 0 && !output.isEmpty()) {
                return output.lines().findFirst().orElse(null);
            }
        } catch (Exception ignored) {
        }

        return null;
    }
}
