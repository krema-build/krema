package build.krema.cli;

import build.krema.cli.bundle.AppBundleConfig;
import build.krema.cli.bundle.AppBundler;
import build.krema.cli.bundle.AppBundlerFactory;
import build.krema.core.platform.Platform;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.File;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static java.nio.file.StandardWatchEventKinds.*;

/**
 * Starts development mode - compiles and runs the app's Main class with the frontend dev server.
 * Supports hot reload: watches for Java file changes and restarts the app.
 */
@Command(
    name = "dev",
    description = "Start development mode with hot reload"
)
public class DevCommand implements Callable<Integer> {

    @Option(names = {"-c", "--config"}, description = "Config file path", defaultValue = "krema.toml")
    private String configPath;

    @Option(names = {"--no-frontend"}, description = "Don't start the frontend dev server")
    private boolean noFrontend;

    @Option(names = {"--no-compile"}, description = "Skip initial Java compilation")
    private boolean noCompile;

    @Option(names = {"--no-watch"}, description = "Disable Java hot reload (file watching)")
    private boolean noWatch;

    @Option(names = {"--no-bundle"}, description = "Don't create dev bundle (native features may not work)")
    private boolean noBundle;

    @Option(names = {"--env"}, description = "Environment profile", defaultValue = "development")
    private String envProfile;

    @Option(names = {"--port"}, description = "Override frontend dev server port")
    private Integer port;

    private final AtomicBoolean running = new AtomicBoolean(true);
    private final AtomicReference<Process> appProcess = new AtomicReference<>();
    private Path devBundlePath;
    private Map<String, String> dotEnvVars = Map.of();

    @Override
    public Integer call() {
        try {
            long startTime = System.currentTimeMillis();

            KremaConfig config = KremaConfig.loadOrDefault(Path.of(configPath), envProfile);
            dotEnvVars = DotEnvLoader.load(Path.of("."), envProfile);
            System.out.println("[Krema Dev] Environment: " + envProfile);
            System.out.println("[Krema Dev] Config loaded in " + (System.currentTimeMillis() - startTime) + "ms");

            String mainClass = config.getBuild().getMainClass();
            if (mainClass == null || mainClass.isEmpty()) {
                System.err.println("[Krema Dev] Error: main_class not specified in krema.toml [build] section");
                System.err.println("[Krema Dev] Add: main_class = \"com.example.app.Main\" to your krema.toml");
                return 1;
            }

            // Compile Java code
            if (!noCompile) {
                System.out.println("[Krema Dev] Compiling Java code...");
                if (!runMavenCompile()) {
                    System.err.println("[Krema Dev] Compilation failed");
                    return 1;
                }
            }

            // Create dev bundle if required for native features
            AppBundler bundler = AppBundlerFactory.get();
            if (!noBundle && bundler.requiresBundleForNativeFeatures()) {
                System.out.println("[Krema Dev] Creating dev bundle for native feature support...");
                devBundlePath = createDevBundle(config, bundler);
                if (devBundlePath != null) {
                    System.out.println("[Krema Dev] Dev bundle created: " + devBundlePath);
                }
            }

            Process frontendProcess = null;
            String devUrl = config.getBuild().getFrontendDevUrl();

            if (port != null) {
                devUrl = devUrl.replaceAll(":\\d+", ":" + port);
            }

            if (!noFrontend) {
                long checkStart = System.currentTimeMillis();
                if (isServerRunning(devUrl)) {
                    System.out.println("[Krema Dev] Frontend server already running at " + devUrl + " (checked in " + (System.currentTimeMillis() - checkStart) + "ms)");
                } else {
                    frontendProcess = startFrontendServer(config);
                    if (frontendProcess != null) {
                        System.out.println("[Krema Dev] Frontend server starting...");
                        waitForServer(devUrl, frontendProcess);
                    }
                }
            }

            // Setup shutdown hook
            final Process finalFrontendProcess = frontendProcess;
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                running.set(false);
                stopApp();
                if (finalFrontendProcess != null) {
                    finalFrontendProcess.destroy();
                }
            }));

            // Start file watcher if enabled
            Thread watcherThread = null;
            if (!noWatch) {
                String javaSourceDir = config.getBuild().getJavaSourceDir();
                Path watchPath = Path.of(javaSourceDir);
                if (Files.isDirectory(watchPath)) {
                    final String finalDevUrl = devUrl;
                    watcherThread = new Thread(() -> watchForChanges(watchPath, mainClass, finalDevUrl));
                    watcherThread.setDaemon(true);
                    watcherThread.start();
                    System.out.println("[Krema Dev] Watching " + watchPath + " for changes (hot reload enabled)");
                }
            }

            try {
                System.out.println("[Krema Dev] Starting app: " + mainClass);

                // Start the app
                startApp(mainClass, devUrl);

                // Wait for the app process
                Process process = appProcess.get();
                if (process != null) {
                    return process.waitFor();
                }
                return 0;
            } finally {
                running.set(false);
                stopApp();
                if (frontendProcess != null) {
                    System.out.println("[Krema Dev] Stopping frontend server...");
                    killProcessTree(frontendProcess);
                }
            }
        } catch (Exception e) {
            System.err.println("[Krema Dev] Error: " + e.getMessage());
            e.printStackTrace();
            return 1;
        }
    }

    private void watchForChanges(Path sourceDir, String mainClass, String devUrl) {
        try (WatchService watcher = FileSystems.getDefault().newWatchService()) {
            // Register all directories recursively
            registerDirectoryTree(watcher, sourceDir);

            while (running.get()) {
                WatchKey key;
                try {
                    key = watcher.poll(500, TimeUnit.MILLISECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }

                if (key == null) {
                    continue;
                }

                boolean needsRestart = false;
                for (WatchEvent<?> event : key.pollEvents()) {
                    WatchEvent.Kind<?> kind = event.kind();

                    if (kind == OVERFLOW) {
                        continue;
                    }

                    @SuppressWarnings("unchecked")
                    WatchEvent<Path> ev = (WatchEvent<Path>) event;
                    Path filename = ev.context();

                    if (filename.toString().endsWith(".java")) {
                        System.out.println("[Krema Dev] Detected change: " + filename);
                        needsRestart = true;
                    }

                    // If a new directory is created, register it
                    if (kind == ENTRY_CREATE) {
                        Path dir = ((Path) key.watchable()).resolve(filename);
                        if (Files.isDirectory(dir)) {
                            registerDirectoryTree(watcher, dir);
                        }
                    }
                }

                boolean valid = key.reset();
                if (!valid) {
                    break;
                }

                if (needsRestart && running.get()) {
                    restartApp(mainClass, devUrl);
                }
            }
        } catch (IOException e) {
            System.err.println("[Krema Dev] File watcher error: " + e.getMessage());
        }
    }

    private void registerDirectoryTree(WatchService watcher, Path start) throws IOException {
        Files.walkFileTree(start, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, java.nio.file.attribute.BasicFileAttributes attrs) throws IOException {
                dir.register(watcher, ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private synchronized void restartApp(String mainClass, String devUrl) {
        System.out.println("[Krema Dev] Recompiling...");

        try {
            if (!runMavenCompile()) {
                System.err.println("[Krema Dev] Compilation failed, keeping current version");
                return;
            }

            System.out.println("[Krema Dev] Restarting app...");
            stopApp();

            // Small delay to ensure clean restart
            Thread.sleep(500);

            startApp(mainClass, devUrl);
            System.out.println("[Krema Dev] App restarted successfully");
        } catch (Exception e) {
            System.err.println("[Krema Dev] Restart failed: " + e.getMessage());
        }
    }

    private void startApp(String mainClass, String devUrl) throws IOException, InterruptedException {
        String classpath = buildClasspath();
        String kremaLibPath = findKremaLibPath();

        // If we have a dev bundle, launch through it
        if (devBundlePath != null) {
            System.out.println("[Krema Dev] Launching through bundle: " + devBundlePath.getFileName());
            System.out.println("[Krema Dev] App logs: ~/.cache/krema/dev.log");
            launchThroughBundle(mainClass, classpath, kremaLibPath, devUrl);
            return;
        }

        // Otherwise, launch Java directly (native features may not work on macOS)
        List<String> command = new ArrayList<>();
        command.add("java");

        // Platform-specific flags
        if (Platform.current() == Platform.MACOS) {
            command.add("-XstartOnFirstThread");
        }
        command.add("--enable-native-access=ALL-UNNAMED");

        // Find krema-core lib directory for native libraries
        if (kremaLibPath != null) {
            command.add("-Djava.library.path=" + kremaLibPath);
        }

        // Build classpath
        command.add("-cp");
        command.add(classpath);

        // Main class
        command.add(mainClass);

        // Pass dev flag
        command.add("--dev");

        System.out.println("[Krema Dev] Running: java " + mainClass + " --dev");

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.inheritIO();
        pb.environment().put("KREMA_DEV", "true");
        pb.environment().put("KREMA_DEV_URL", devUrl);
        pb.environment().put("KREMA_ENV", envProfile);
        pb.environment().putAll(dotEnvVars);
        pb.environment().put("KREMA_ENV_VARS", toJson(dotEnvVars));

        Process process = pb.start();
        appProcess.set(process);
    }

    private void launchThroughBundle(String mainClass, String classpath, String libPath, String devUrl)
            throws IOException {
        AppBundler bundler = AppBundlerFactory.get();

        Map<String, String> env = new HashMap<>();
        env.put("KREMA_DEV", "true");
        env.put("KREMA_DEV_URL", devUrl);
        env.put("KREMA_CLASSPATH", classpath);
        env.put("KREMA_MAIN_CLASS", mainClass);
        env.put("KREMA_ENV", envProfile);
        env.putAll(dotEnvVars);
        env.put("KREMA_ENV_VARS", toJson(dotEnvVars));
        if (libPath != null) {
            env.put("KREMA_LIB_PATH", libPath);
        }

        Process process = bundler.launch(devBundlePath, env);
        appProcess.set(process);
    }

    private Path createDevBundle(KremaConfig config, AppBundler bundler) throws IOException {
        String appName = config.getPackageConfig().getName();
        String identifier = config.getBundle().getIdentifier();
        if (identifier == null) {
            identifier = config.getPackageConfig().getIdentifier();
        }
        String version = config.getPackageConfig().getVersion();
        String mainClass = config.getBuild().getMainClass();

        Path devBundleDir = Path.of(".krema/dev-bundle");
        Files.createDirectories(devBundleDir);

        AppBundleConfig bundleConfig = AppBundleConfig.builder()
            .appName(appName)
            .identifier(identifier)
            .version(version)
            .mainClass(mainClass)
            .outputDir(devBundleDir)
            .deepLinkSchemes(config.getDeepLink().getSchemes())
            .build();

        return bundler.createDevBundle(bundleConfig);
    }

    private void stopApp() {
        Process process = appProcess.getAndSet(null);
        if (process != null && process.isAlive()) {
            process.destroy();
            try {
                if (!process.waitFor(3, TimeUnit.SECONDS)) {
                    process.destroyForcibly();
                }
            } catch (InterruptedException e) {
                process.destroyForcibly();
                Thread.currentThread().interrupt();
            }
        }
    }

    private void killProcessTree(Process process) {
        if (process == null) return;

        try {
            // Get the PID and kill the entire process group
            long pid = process.pid();

            if (Platform.current() == Platform.MACOS || Platform.current() == Platform.LINUX) {
                // Kill the process group (negative PID kills the group)
                // First try to get child processes and kill them
                process.descendants().forEach(ph -> {
                    try {
                        ph.destroyForcibly();
                    } catch (Exception ignored) {}
                });
            }

            // Kill the main process
            process.destroyForcibly();
            process.waitFor(2, TimeUnit.SECONDS);

        } catch (Exception e) {
            // Fallback: just force kill
            process.destroyForcibly();
        }
    }

    private boolean runMavenCompile() throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder("mvn", "compile", "-q");
        pb.inheritIO();
        Process process = pb.start();
        return process.waitFor() == 0;
    }

    private String findKremaLibPath() {
        // 1. Explicit override via KREMA_LIB_PATH
        String explicitPath = System.getenv("KREMA_LIB_PATH");
        if (explicitPath != null) {
            Path libPath = Path.of(explicitPath);
            if (containsWebviewLib(libPath)) {
                return libPath.toAbsolutePath().toString();
            }
        }

        // 2. Resolve from KREMA_HOME (set by bin/krema launcher)
        String kremaHome = System.getenv("KREMA_HOME");
        if (kremaHome != null) {
            Path libPath = Path.of(kremaHome, "krema-core", "lib");
            if (containsWebviewLib(libPath)) {
                return libPath.toAbsolutePath().toString();
            }
        }

        // 3. Resolve relative to the CLI JAR location
        try {
            Path jarPath = Path.of(DevCommand.class.getProtectionDomain()
                    .getCodeSource().getLocation().toURI());
            // JAR is at <krema-root>/krema-cli/target/krema-cli.jar
            Path kremaRoot = jarPath.getParent().getParent().getParent();
            Path libPath = kremaRoot.resolve("krema-core/lib");
            if (containsWebviewLib(libPath)) {
                return libPath.toAbsolutePath().toString();
            }
        } catch (Exception ignored) {}

        // 4. Check common relative locations (for development setups)
        String[] relativePaths = {
            "../krema/krema-core/lib",
            "../krema-core/lib",
            "../../krema/krema-core/lib",
        };

        for (String basePath : relativePaths) {
            Path libPath = Path.of(basePath);
            if (containsWebviewLib(libPath)) {
                return libPath.toAbsolutePath().toString();
            }
        }

        // 5. Check Maven local repository
        Path m2Base = Path.of(System.getProperty("user.home"),
                ".m2/repository/build/krema/krema-core");
        if (Files.isDirectory(m2Base)) {
            try (var versions = Files.list(m2Base)) {
                var latestVersion = versions
                    .filter(Files::isDirectory)
                    .max((a, b) -> a.getFileName().toString().compareTo(b.getFileName().toString()));
                if (latestVersion.isPresent()) {
                    Path libDir = latestVersion.get().resolve("lib");
                    if (containsWebviewLib(libDir)) {
                        return libDir.toAbsolutePath().toString();
                    }
                }
            } catch (IOException ignored) {}
        }

        return null;
    }

    private boolean containsWebviewLib(Path dir) {
        if (!Files.isDirectory(dir)) return false;
        try (var files = Files.list(dir)) {
            return files.anyMatch(p -> p.getFileName().toString().contains("webview"));
        } catch (IOException e) {
            return false;
        }
    }

    private String buildClasspath() throws IOException, InterruptedException {
        // Use Maven to get the classpath - write to temp file to avoid mixing with warnings
        Path tempFile = Files.createTempFile("krema-classpath", ".txt");
        try {
            ProcessBuilder pb = new ProcessBuilder(
                "mvn", "dependency:build-classpath",
                "-Dmdep.outputFile=" + tempFile.toAbsolutePath(), "-q"
            );
            pb.inheritIO();
            Process process = pb.start();
            process.waitFor();

            String mvnClasspath = Files.readString(tempFile).trim();

            // Add target/classes with absolute path (required for bundle launching)
            String targetClasses = Path.of("target/classes").toAbsolutePath().toString();

            if (mvnClasspath.isEmpty()) {
                return targetClasses;
            }

            return targetClasses + File.pathSeparator + mvnClasspath;
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    private Process startFrontendServer(KremaConfig config) throws IOException {
        String command = config.getBuild().getFrontendDevCommand();
        System.out.println("[Krema Dev] Starting frontend server: " + command);

        ProcessBuilder pb = new ProcessBuilder();
        String[] parts = command.split("\\s+");

        // Detect package manager
        String packageManager = parts[0];
        if (packageManager.equals("npm")) {
            if (Files.exists(Path.of("pnpm-lock.yaml"))) {
                packageManager = "pnpm";
            } else if (Files.exists(Path.of("yarn.lock"))) {
                packageManager = "yarn";
            } else if (Files.exists(Path.of("bun.lockb"))) {
                packageManager = "bun";
            }
        }
        parts[0] = packageManager;
        pb.command(parts);
        pb.inheritIO();
        pb.redirectErrorStream(true);

        return pb.start();
    }

    private boolean isServerRunning(String url) {
        // Angular/Vite dev servers on macOS often bind to ::1 (IPv6 only).
        // Java's HttpURLConnection tries 127.0.0.1 first and doesn't fall back,
        // so we explicitly try all variants.
        if (tryConnect(url)) return true;
        if (url.contains("localhost")) {
            if (tryConnect(url.replace("localhost", "[::1]"))) return true;
            if (tryConnect(url.replace("localhost", "127.0.0.1"))) return true;
        }
        return false;
    }

    private boolean tryConnect(String url) {
        try {
            HttpURLConnection conn = (HttpURLConnection) new URI(url).toURL().openConnection();
            conn.setConnectTimeout(1000);
            conn.setReadTimeout(1000);
            conn.setRequestMethod("GET");
            conn.setInstanceFollowRedirects(true);
            int responseCode = conn.getResponseCode();
            conn.disconnect();
            return responseCode >= 200 && responseCode < 400;
        } catch (Exception e) {
            return false;
        }
    }

    private String toJson(Map<String, String> map) {
        if (map.isEmpty()) return "{}";
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, String> e : map.entrySet()) {
            if (!first) sb.append(",");
            first = false;
            sb.append("\"").append(escapeJson(e.getKey())).append("\":\"")
              .append(escapeJson(e.getValue())).append("\"");
        }
        return sb.append("}").toString();
    }

    private String escapeJson(String s) {
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private void waitForServer(String url, Process serverProcess) throws InterruptedException {
        int maxAttempts = 60; // 60 seconds max
        for (int i = 1; i <= maxAttempts; i++) {
            if (!serverProcess.isAlive()) {
                System.err.println("[Krema Dev] Frontend server process exited unexpectedly");
                return;
            }
            if (isServerRunning(url)) {
                System.out.println("[Krema Dev] Frontend server ready at " + url + " (" + i + "s)");
                return;
            }
            if (i % 5 == 0) {
                System.out.println("[Krema Dev] Waiting for frontend server... (" + i + "s)");
            }
            Thread.sleep(1000);
        }
        System.err.println("[Krema Dev] Warning: frontend server not responding after " + maxAttempts + "s, starting app anyway");
    }
}
