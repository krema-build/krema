package build.krema.cli;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import build.krema.core.platform.PlatformDetector;

import static build.krema.cli.JdkResolver.resolveOrWarn;

/**
 * Builds the application for production.
 */
@Command(
    name = "build",
    description = "Build the application for production"
)
public class BuildCommand implements Callable<Integer> {

    @Option(names = {"-c", "--config"}, description = "Config file path", defaultValue = "krema.toml")
    private String configPath;

    @Option(names = {"--skip-frontend"}, description = "Skip frontend build")
    private boolean skipFrontend;

    @Option(names = {"--skip-java"}, description = "Skip Java compilation")
    private boolean skipJava;

    @Option(names = {"--native", "-n"}, description = "Compile to native binary using GraalVM native-image")
    private boolean nativeBuild;

    @Option(names = {"--env"}, description = "Environment profile", defaultValue = "production")
    private String envProfile;

    private Path javaHome;

    @Override
    public Integer call() {
        try {
            KremaConfig config = KremaConfig.loadOrDefault(Path.of(configPath), envProfile);
            java.util.Map<String, String> dotEnvVars = DotEnvLoader.load(Path.of("."), envProfile);
            System.out.println("[Krema Build] Environment: " + envProfile);
            System.out.println("[Krema Build] Starting production build...");

            javaHome = resolveOrWarn();
            if (javaHome == null) {
                return 1;
            }

            // Build frontend
            if (!skipFrontend) {
                if (!buildFrontend(config, dotEnvVars)) {
                    return 1;
                }
            }

            // Build Java
            if (!skipJava) {
                if (!buildJava(config)) {
                    return 1;
                }
            }

            // Copy assets to target
            copyAssets(config);

            // Native image compilation
            if (nativeBuild) {
                if (!buildNativeImage(config)) {
                    return 1;
                }
            }

            System.out.println();
            System.out.println("[Krema Build] Build completed successfully!");
            System.out.println("[Krema Build] Output: target/");

            return 0;
        } catch (Exception e) {
            System.err.println("[Krema Build] Error: " + e.getMessage());
            e.printStackTrace();
            return 1;
        }
    }

    private boolean buildFrontend(KremaConfig config, java.util.Map<String, String> dotEnvVars)
            throws IOException, InterruptedException {
        String command = config.getBuild().getFrontendCommand();
        System.out.println("[Krema Build] Building frontend: " + command);

        ProcessBuilder pb = new ProcessBuilder();

        // Detect package manager
        if (Files.exists(Path.of("pnpm-lock.yaml"))) {
            pb.command("pnpm", "run", "build");
        } else if (Files.exists(Path.of("yarn.lock"))) {
            pb.command("yarn", "build");
        } else if (Files.exists(Path.of("bun.lockb"))) {
            pb.command("bun", "run", "build");
        } else {
            pb.command("npm", "run", "build");
        }

        pb.inheritIO();
        pb.environment().putAll(dotEnvVars);
        Process process = pb.start();
        int exitCode = process.waitFor();

        if (exitCode != 0) {
            System.err.println("[Krema Build] Frontend build failed with exit code: " + exitCode);
            return false;
        }

        System.out.println("[Krema Build] Frontend build completed");
        return true;
    }

    private boolean buildJava(KremaConfig config) throws IOException, InterruptedException {
        System.out.println("[Krema Build] Compiling Java sources...");

        Path javaSourceDir = Path.of(config.getBuild().getJavaSourceDir());
        if (!Files.exists(javaSourceDir)) {
            System.out.println("[Krema Build] No Java source directory found at: " + javaSourceDir);
            return true;
        }

        // Use Maven if pom.xml exists, otherwise use javac directly
        if (Files.exists(Path.of("pom.xml"))) {
            return buildWithMaven();
        } else {
            return buildWithJavac(config);
        }
    }

    private boolean buildWithMaven() throws IOException, InterruptedException {
        System.out.println("[Krema Build] Building with Maven...");

        ProcessBuilder pb = new ProcessBuilder("mvn", "package", "-q", "-DskipTests");
        pb.environment().put("JAVA_HOME", javaHome.toString());
        pb.inheritIO();
        Process process = pb.start();
        int exitCode = process.waitFor();

        if (exitCode != 0) {
            System.err.println("[Krema Build] Maven build failed");
            return false;
        }

        return true;
    }

    private boolean buildWithJavac(KremaConfig config) throws IOException, InterruptedException {
        Path sourceDir = Path.of(config.getBuild().getJavaSourceDir());
        Path outputDir = Path.of("target/classes");

        Files.createDirectories(outputDir);

        // Find all Java files
        java.util.List<String> javaFiles = new java.util.ArrayList<>();
        Files.walkFileTree(sourceDir, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                if (file.toString().endsWith(".java")) {
                    javaFiles.add(file.toString());
                }
                return FileVisitResult.CONTINUE;
            }
        });

        if (javaFiles.isEmpty()) {
            System.out.println("[Krema Build] No Java files found");
            return true;
        }

        java.util.List<String> command = new java.util.ArrayList<>();
        command.add(javaHome.resolve("bin/javac").toString());
        command.add("--release");
        command.add("25");
        command.add("-d");
        command.add(outputDir.toString());
        command.addAll(javaFiles);

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.inheritIO();
        Process process = pb.start();
        int exitCode = process.waitFor();

        if (exitCode != 0) {
            System.err.println("[Krema Build] Java compilation failed");
            return false;
        }

        System.out.println("[Krema Build] Java compilation completed");
        return true;
    }

    private void copyAssets(KremaConfig config) throws IOException {
        Path outDir = Path.of(config.getBuild().getOutDir());
        Path targetResources = Path.of("target/classes/" + config.getBuild().getAssetsPath());

        if (!Files.exists(outDir)) {
            System.out.println("[Krema Build] No frontend output directory: " + outDir);
            return;
        }

        System.out.println("[Krema Build] Copying assets from " + outDir + " to " + targetResources);
        Files.createDirectories(targetResources);

        Files.walkFileTree(outDir, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                Path targetDir = targetResources.resolve(outDir.relativize(dir));
                Files.createDirectories(targetDir);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Path targetFile = targetResources.resolve(outDir.relativize(file));
                Files.copy(file, targetFile, StandardCopyOption.REPLACE_EXISTING);
                return FileVisitResult.CONTINUE;
            }
        });

        System.out.println("[Krema Build] Assets copied successfully");
    }

    private boolean buildNativeImage(KremaConfig config) throws IOException, InterruptedException {
        System.out.println("[Krema Build] Building native image...");

        // Find native-image tool
        Path nativeImageTool = findNativeImageTool();
        if (nativeImageTool == null) {
            System.err.println("[Krema Build] native-image tool not found.");
            System.err.println("[Krema Build] Set GRAALVM_HOME or JAVA_HOME to a GraalVM installation,");
            System.err.println("[Krema Build] or ensure native-image is on your PATH.");
            return false;
        }
        System.out.println("[Krema Build] Using native-image: " + nativeImageTool);

        // Resolve main class
        String mainClass = config.getBuild().getMainClass();
        if (mainClass == null || mainClass.isBlank()) {
            System.err.println("[Krema Build] main_class must be set in krema.toml [build] section for native builds");
            return false;
        }

        // Build classpath via Maven
        String classpath = resolveMavenClasspath();
        if (classpath == null) {
            System.err.println("[Krema Build] Failed to resolve Maven classpath");
            return false;
        }

        // Append target/classes
        classpath = Path.of("target/classes").toAbsolutePath() + System.getProperty("path.separator") + classpath;

        // Find native library directory
        Path libDir = NativeLibraryFinder.find();
        String libPathStr = libDir != null ? libDir.toAbsolutePath().toString() : "";

        // Generate resource config for frontend assets
        Path resourceConfig = generateResourceConfig(config);

        // Determine output name
        String appName = config.getPackageConfig().getName();

        // Assemble native-image command
        List<String> command = new ArrayList<>();
        command.add(nativeImageTool.toString());
        command.add("-cp");
        command.add(classpath);
        command.add("--enable-native-access=ALL-UNNAMED");
        command.add("-H:+UnlockExperimentalVMOptions");
        command.add("-H:-CheckToolchain");
        command.add("-Djava.awt.headless=true");

        // On Apple Silicon, force the C compiler to target arm64 — the macOS
        // universal binary for /usr/bin/cc can default to x86_64 under Rosetta,
        // causing struct definition mismatches (AArch64 vs x86_64 mcontext).
        if (PlatformDetector.isMacOS() && "aarch64".equals(PlatformDetector.getArch())) {
            command.add("-H:CCompilerOption=-arch");
            command.add("-H:CCompilerOption=arm64");
        }

        if (!libPathStr.isEmpty()) {
            command.add("-Djava.library.path=" + libPathStr);
        }

        if (resourceConfig != null) {
            command.add("-H:ResourceConfigurationFiles=" + resourceConfig.toAbsolutePath());
        }

        command.add("-o");
        command.add(Path.of("target", appName).toString());
        command.add(mainClass);

        System.out.println("[Krema Build] Running: " + String.join(" ", command));

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.inheritIO();
        Process process = pb.start();
        int exitCode = process.waitFor();

        if (exitCode != 0) {
            System.err.println("[Krema Build] native-image compilation failed with exit code: " + exitCode);
            return false;
        }

        // Clean up temp resource config
        if (resourceConfig != null) {
            Files.deleteIfExists(resourceConfig);
        }

        // Copy native library next to the binary so it can be found at runtime
        copyNativeLibraryToBuildOutput();

        // On Windows, copy VC runtime DLLs next to the binary so end users
        // don't need the Visual C++ Redistributable installed
        if (PlatformDetector.isWindows()) {
            copyVCRuntimeDlls();
        }

        System.out.println("[Krema Build] Native binary created: target/" + appName);
        return true;
    }

    private void copyNativeLibraryToBuildOutput() {
        Path library = NativeLibraryFinder.findLibrary();
        if (library != null) {
            Path target = Path.of("target").resolve(library.getFileName());
            try {
                Files.copy(library, target, StandardCopyOption.REPLACE_EXISTING);
                System.out.println("[Krema Build] Copied " + library.getFileName() + " to target/");
            } catch (IOException e) {
                System.err.println("[Krema Build] Warning: failed to copy native library: " + e.getMessage());
            }
        } else {
            System.err.println("[Krema Build] Warning: native library (libwebview) not found, binary may fail at runtime");
        }
    }

    private void copyVCRuntimeDlls() {
        Path targetDir = Path.of("target");
        String[] dlls = {"vcruntime140.dll", "vcruntime140_1.dll"};

        // Try VCToolsRedistDir (set by Visual Studio Developer environment)
        String redistDir = System.getenv("VCToolsRedistDir");
        if (redistDir != null) {
            Path crtDir = findVCRedistCrtDir(Path.of(redistDir, "x64"));
            if (crtDir != null) {
                copyDlls(crtDir, targetDir, dlls);
                return;
            }
        }

        // Try to find via vswhere
        try {
            var pb = new ProcessBuilder(
                Path.of(System.getenv("ProgramFiles(x86)"),
                    "Microsoft Visual Studio", "Installer", "vswhere.exe").toString(),
                "-latest", "-property", "installationPath");
            pb.redirectErrorStream(true);
            Process process = pb.start();
            String vsPath = new String(process.getInputStream().readAllBytes()).trim();
            if (process.waitFor() == 0 && !vsPath.isBlank()) {
                // Search in VC\Redist\MSVC\*\x64\Microsoft.VC*.CRT
                Path redistBase = Path.of(vsPath, "VC", "Redist", "MSVC");
                if (Files.isDirectory(redistBase)) {
                    try (var versions = Files.list(redistBase)) {
                        var latest = versions.filter(Files::isDirectory).max(Path::compareTo);
                        if (latest.isPresent()) {
                            Path crtDir = findVCRedistCrtDir(latest.get().resolve("x64"));
                            if (crtDir != null) {
                                copyDlls(crtDir, targetDir, dlls);
                                return;
                            }
                        }
                    }
                }
            }
        } catch (Exception ignored) {}

        // Fallback: copy from System32
        Path system32 = Path.of(System.getenv("SYSTEMROOT"), "System32");
        copyDlls(system32, targetDir, dlls);
    }

    private Path findVCRedistCrtDir(Path x64Dir) {
        if (!Files.isDirectory(x64Dir)) return null;
        try (var dirs = Files.list(x64Dir)) {
            return dirs.filter(p -> p.getFileName().toString().startsWith("Microsoft.VC"))
                       .filter(p -> p.getFileName().toString().endsWith(".CRT"))
                       .findFirst().orElse(null);
        } catch (IOException e) {
            return null;
        }
    }

    private void copyDlls(Path sourceDir, Path targetDir, String[] dlls) {
        for (String dll : dlls) {
            Path src = sourceDir.resolve(dll);
            if (Files.exists(src)) {
                try {
                    Files.copy(src, targetDir.resolve(dll), StandardCopyOption.REPLACE_EXISTING);
                    System.out.println("[Krema Build] Copied " + dll + " to target/");
                } catch (IOException e) {
                    System.err.println("[Krema Build] Warning: failed to copy " + dll + ": " + e.getMessage());
                }
            }
        }
    }

    private Path findNativeImageTool() {
        // Check GRAALVM_HOME (explicit GraalVM override)
        String graalHome = System.getenv("GRAALVM_HOME");
        if (graalHome != null && !graalHome.isBlank()) {
            Path tool = Path.of(graalHome, "bin", "native-image");
            if (Files.isExecutable(tool)) return tool;
        }

        // Check the resolved JDK (already discovered by JdkResolver)
        if (javaHome != null) {
            Path tool = javaHome.resolve("bin/native-image");
            if (Files.isExecutable(tool)) return tool;
        }

        // Fall back to PATH
        String whichCmd = PlatformDetector.isWindows() ? "where" : "which";
        try {
            var pb = new ProcessBuilder(whichCmd, "native-image");
            pb.redirectErrorStream(true);
            Process process = pb.start();
            try (var reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line = reader.readLine();
                if (process.waitFor() == 0 && line != null && !line.isBlank()) {
                    return Path.of(line.trim());
                }
            }
        } catch (IOException | InterruptedException ignored) {}

        return null;
    }

    private String resolveMavenClasspath() throws IOException, InterruptedException {
        Path cpFile = Files.createTempFile("krema-cp", ".txt");
        try {
            ProcessBuilder pb = new ProcessBuilder(
                "mvn", "dependency:build-classpath",
                "-Dmdep.outputFile=" + cpFile.toAbsolutePath(),
                "-q"
            );
            pb.environment().put("JAVA_HOME", javaHome.toString());
            pb.inheritIO();
            Process process = pb.start();
            int exitCode = process.waitFor();

            if (exitCode != 0) {
                return null;
            }

            return Files.readString(cpFile).trim();
        } finally {
            Files.deleteIfExists(cpFile);
        }
    }

    void setEnvProfile(String profile) {
        this.envProfile = profile;
    }

    private Path generateResourceConfig(KremaConfig kremaConfig) throws IOException {
        String assetsPath = kremaConfig.getBuild().getAssetsPath();
        String config = """
                {
                  "resources": {
                    "includes": [
                      { "pattern": "%s/.*" }
                    ]
                  }
                }
                """.formatted(assetsPath);

        Path configFile = Files.createTempFile("krema-resource-config", ".json");
        Files.writeString(configFile, config);
        return configFile;
    }
}
