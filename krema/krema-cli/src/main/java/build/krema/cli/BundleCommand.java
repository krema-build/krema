package build.krema.cli;

import build.krema.cli.bundle.AppBundleConfig;
import build.krema.cli.bundle.AppBundler;
import build.krema.cli.bundle.AppBundlerFactory;
import build.krema.cli.bundle.macos.MacOSAppBundler;
import build.krema.cli.bundle.macos.MacOSCodeSigner;
import build.krema.cli.bundle.windows.WindowsCodeSigner;
import build.krema.core.platform.Platform;
import build.krema.core.updater.UpdateSigner;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.IOException;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * Creates platform-specific application bundles.
 */
@Command(
    name = "bundle",
    description = "Create platform-specific application bundle"
)
public class BundleCommand implements Callable<Integer> {

    @Option(names = {"-c", "--config"}, description = "Config file path", defaultValue = "krema.toml")
    private String configPath;

    @Option(names = {"-t", "--type"}, description = "Bundle type: app, dmg, msi, deb, appimage")
    private String type;

    @Option(names = {"--skip-build"}, description = "Skip the build step")
    private boolean skipBuild;

    @Option(names = {"--sign"}, description = "Sign the bundle with configured certificate/identity")
    private boolean sign;

    @Option(names = {"--notarize"}, description = "Notarize the bundle with Apple (macOS only, implies --sign)")
    private boolean notarize;

    @Option(names = {"--env"}, description = "Environment profile", defaultValue = "production")
    private String envProfile;

    @Override
    public Integer call() {
        try {
            KremaConfig config = KremaConfig.loadOrDefault(Path.of(configPath), envProfile);
            Platform platform = Platform.current();

            System.out.println("[Krema Bundle] Creating bundle for " + platform.getDisplayName() + "...");

            // Run build first if not skipped
            if (!skipBuild) {
                BuildCommand buildCommand = new BuildCommand();
                buildCommand.setEnvProfile(envProfile);
                int buildResult = buildCommand.call();
                if (buildResult != 0) {
                    return buildResult;
                }
            }

            // --notarize is macOS only
            if (notarize && platform != Platform.MACOS) {
                System.err.println("[Krema Bundle] Error: --notarize is only supported on macOS");
                return 1;
            }

            // Create bundle based on platform
            return switch (platform) {
                case MACOS -> bundleMacOS(config);
                case WINDOWS -> bundleWindows(config);
                case LINUX -> bundleLinux(config);
                case UNKNOWN -> {
                    System.err.println("[Krema Bundle] Unknown platform, cannot create bundle");
                    yield 1;
                }
            };
        } catch (Exception e) {
            System.err.println("[Krema Bundle] Error: " + e.getMessage());
            e.printStackTrace();
            return 1;
        }
    }

    private int bundleMacOS(KremaConfig config) throws IOException, InterruptedException {
        System.out.println("[Krema Bundle] Creating macOS app bundle...");

        String appName = config.getPackageConfig().getName();
        String identifier = config.getBundle().getIdentifier();
        if (identifier == null) {
            identifier = config.getPackageConfig().getIdentifier();
        }
        String version = config.getPackageConfig().getVersion();
        String mainClass = config.getBuild().getMainClass();

        // Find native library path
        Path libPath = NativeLibraryFinder.find();

        // Find icon path
        String iconPathStr = config.getBundle().getIcon();
        Path iconPath = iconPathStr != null ? Path.of(iconPathStr) : null;

        Path bundleDir = Path.of("target/bundle/macos");
        Files.createDirectories(bundleDir);

        // Build bundle config
        AppBundleConfig bundleConfig = AppBundleConfig.builder()
            .appName(appName)
            .identifier(identifier)
            .version(version)
            .mainClass(mainClass != null ? mainClass : "com.example.app." + toPascalCase(appName) + "App")
            .outputDir(bundleDir)
            .libPath(libPath)
            .iconPath(iconPath)
            .deepLinkSchemes(config.getDeepLink().getSchemes())
            .build();

        // Create bundle using AppBundler
        AppBundler bundler = AppBundlerFactory.get(Platform.MACOS);
        Path appDir = bundler.createProductionBundle(bundleConfig);
        System.out.println("[Krema Bundle] Created bundle structure");

        // Copy JAR files to the Java resources directory
        Path javaDir = MacOSAppBundler.getJavaResourcesDir(appDir);
        copyJarFiles(javaDir);

        // Copy native libraries
        Path resourcesDir = MacOSAppBundler.getResourcesDir(appDir);
        copyNativeLibrary(resourcesDir);

        // --notarize implies --sign
        boolean shouldSign = sign || notarize;

        // Code signing
        MacOSCodeSigner codeSigner = null;
        if (shouldSign) {
            codeSigner = new MacOSCodeSigner(config.getBundle().getMacos());
            if (!codeSigner.isSigningConfigured()) {
                System.err.println("[Krema Bundle] Error: --sign requires [bundle.macos] signing_identity in krema.toml");
                return 1;
            }
            codeSigner.signApp(appDir);
        }

        System.out.println();
        System.out.println("[Krema Bundle] macOS app bundle created: " + appDir);

        // Create DMG if requested
        if ("dmg".equals(type)) {
            int dmgResult = createDMG(config, bundleDir, appDir);
            if (dmgResult != 0) {
                return dmgResult;
            }

            if (shouldSign) {
                Path dmgPath = bundleDir.resolve(config.getPackageConfig().getName() + ".dmg");
                codeSigner.signDMG(dmgPath);

                if (notarize) {
                    codeSigner.notarize(dmgPath);
                    codeSigner.staple(dmgPath);
                }

                signArtifactIfConfigured(dmgPath);
            } else {
                Path dmgPath = bundleDir.resolve(config.getPackageConfig().getName() + ".dmg");
                if (Files.exists(dmgPath)) {
                    signArtifactIfConfigured(dmgPath);
                }
            }
        } else {
            // Sign the .app bundle as a tar.gz if present
            // Auto-sign any distributable artifact
        }

        return 0;
    }

    private void copyJarFiles(Path targetDir) throws IOException {
        Path targetJars = Path.of("target");
        if (Files.exists(targetJars)) {
            try (var stream = Files.list(targetJars)) {
                stream.filter(p -> p.toString().endsWith(".jar"))
                    .forEach(jar -> {
                        try {
                            Files.copy(jar, targetDir.resolve(jar.getFileName()), StandardCopyOption.REPLACE_EXISTING);
                            System.out.println("[Krema Bundle] Copied " + jar.getFileName());
                        } catch (IOException e) {
                            System.err.println("[Krema Bundle] Failed to copy " + jar + ": " + e.getMessage());
                        }
                    });
            }
        }

        // Also copy dependencies
        Path depsDir = Path.of("target/dependency");
        if (Files.exists(depsDir)) {
            try (var stream = Files.list(depsDir)) {
                stream.filter(p -> p.toString().endsWith(".jar"))
                    .forEach(jar -> {
                        try {
                            Files.copy(jar, targetDir.resolve(jar.getFileName()), StandardCopyOption.REPLACE_EXISTING);
                        } catch (IOException e) {
                            // Ignore
                        }
                    });
            }
        }
    }

    private void copyNativeLibrary(Path targetDir) throws IOException {
        // Try to find libwebview.dylib
        String[] searchPaths = {
            "lib/libwebview.dylib",
            "../lib/libwebview.dylib",
            System.getProperty("java.library.path", "") + "/libwebview.dylib"
        };

        for (String searchPath : searchPaths) {
            Path libPath = Path.of(searchPath);
            if (Files.exists(libPath)) {
                Files.copy(libPath, targetDir.resolve("libwebview.dylib"), StandardCopyOption.REPLACE_EXISTING);
                System.out.println("[Krema Bundle] Copied libwebview.dylib");
                return;
            }
        }

        System.out.println("[Krema Bundle] Warning: libwebview.dylib not found");
    }

    private int createDMG(KremaConfig config, Path bundleDir, Path appDir) throws IOException, InterruptedException {
        String appName = config.getPackageConfig().getName();
        Path dmgPath = bundleDir.resolve(appName + ".dmg");

        System.out.println("[Krema Bundle] Creating DMG: " + dmgPath);

        List<String> command = new ArrayList<>();
        command.add("hdiutil");
        command.add("create");
        command.add("-volname");
        command.add(appName);
        command.add("-srcfolder");
        command.add(appDir.toString());
        command.add("-ov");
        command.add("-format");
        command.add("UDZO");
        command.add(dmgPath.toString());

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.inheritIO();
        Process process = pb.start();
        int exitCode = process.waitFor();

        if (exitCode != 0) {
            System.err.println("[Krema Bundle] Failed to create DMG");
            return 1;
        }

        System.out.println("[Krema Bundle] DMG created: " + dmgPath);
        return 0;
    }

    private int bundleWindows(KremaConfig config) throws IOException {
        String appName = config.getPackageConfig().getName();
        String identifier = config.getBundle().getIdentifier();
        if (identifier == null) {
            identifier = config.getPackageConfig().getIdentifier();
        }
        String version = config.getPackageConfig().getVersion();
        String mainClass = config.getBuild().getMainClass();

        AppBundleConfig bundleConfig = AppBundleConfig.builder()
            .appName(appName)
            .identifier(identifier)
            .version(version)
            .mainClass(mainClass != null ? mainClass : "com.example.app.Main")
            .outputDir(Path.of("target/bundle/windows"))
            .deepLinkSchemes(config.getDeepLink().getSchemes())
            .build();

        AppBundler bundler = AppBundlerFactory.get(Platform.WINDOWS);
        try {
            Path outputDir = bundler.createProductionBundle(bundleConfig);

            // Code signing
            if (sign) {
                WindowsCodeSigner codeSigner = new WindowsCodeSigner(config.getBundle().getWindows());
                if (!codeSigner.isSigningConfigured()) {
                    System.err.println("[Krema Bundle] Error: --sign requires [bundle.windows] signing_certificate in krema.toml");
                    return 1;
                }
                // Find and sign all .exe files in the output directory
                try (var stream = Files.list(outputDir)) {
                    stream.filter(p -> p.toString().endsWith(".exe"))
                        .forEach(exe -> {
                            try {
                                codeSigner.sign(exe);
                            } catch (IOException e) {
                                throw new RuntimeException(e);
                            }
                        });
                }
            }

            // Auto-sign artifacts for update distribution
            try (var stream = Files.list(outputDir)) {
                stream.filter(p -> {
                    String name = p.toString().toLowerCase();
                    return name.endsWith(".exe") || name.endsWith(".msi");
                }).forEach(this::signArtifactIfConfigured);
            }

            return 0;
        } catch (UnsupportedOperationException e) {
            return 1;
        } catch (RuntimeException e) {
            if (e.getCause() instanceof IOException ioe) {
                throw ioe;
            }
            throw e;
        }
    }

    private int bundleLinux(KremaConfig config) throws IOException {
        String appName = config.getPackageConfig().getName();
        String identifier = config.getBundle().getIdentifier();
        if (identifier == null) {
            identifier = config.getPackageConfig().getIdentifier();
        }
        String version = config.getPackageConfig().getVersion();
        String mainClass = config.getBuild().getMainClass();

        AppBundleConfig bundleConfig = AppBundleConfig.builder()
            .appName(appName)
            .identifier(identifier)
            .version(version)
            .mainClass(mainClass != null ? mainClass : "com.example.app.Main")
            .outputDir(Path.of("target/bundle/linux"))
            .deepLinkSchemes(config.getDeepLink().getSchemes())
            .build();

        AppBundler bundler = AppBundlerFactory.get(Platform.LINUX);
        try {
            bundler.createProductionBundle(bundleConfig);
            return 0;
        } catch (UnsupportedOperationException e) {
            return 1;
        }
    }

    private void signArtifactIfConfigured(Path artifact) {
        String privateKey = System.getenv("KREMA_SIGNING_PRIVATE_KEY");
        if (privateKey == null || privateKey.isBlank()) {
            return;
        }

        try {
            Path sigFile = UpdateSigner.writeSignatureFile(artifact, privateKey.trim());
            System.out.println("[Krema Bundle] Signed artifact: " + sigFile);
        } catch (Exception e) {
            System.err.println("[Krema Bundle] Warning: Failed to sign artifact: " + e.getMessage());
        }
    }

    private String toPascalCase(String input) {
        StringBuilder result = new StringBuilder();
        boolean capitalizeNext = true;

        for (char c : input.toCharArray()) {
            if (Character.isLetterOrDigit(c)) {
                result.append(capitalizeNext ? Character.toUpperCase(c) : c);
                capitalizeNext = false;
            } else {
                capitalizeNext = true;
            }
        }

        return result.toString();
    }
}
