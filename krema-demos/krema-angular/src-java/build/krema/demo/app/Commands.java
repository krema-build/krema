package build.krema.demo.app;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import build.krema.core.KremaCommand;
import build.krema.core.event.EventEmitter;
import build.krema.core.notification.NotificationEngine;
import build.krema.core.notification.NotificationEngineFactory;
import build.krema.core.platform.Platform;

/**
 * Backend commands exposed to the Angular frontend via Krema IPC.
 */
public class Commands {

    private final AtomicReference<EventEmitter> emitterRef;
    private ScheduledExecutorService timerService;
    private int tickCount = 0;

    public Commands(AtomicReference<EventEmitter> emitterRef) {
        this.emitterRef = emitterRef;
    }

    // ==================== Basic IPC ====================

    @KremaCommand
    public String greet(String name) {
        return "Hello, " + name + "! Welcome to Krema Angular.";
    }

    @KremaCommand
    public double calculate(double a, double b, String operation) {
        return switch (operation) {
            case "add" -> a + b;
            case "subtract" -> a - b;
            case "multiply" -> a * b;
            case "divide" -> {
                if (b == 0) {
                    throw new ArithmeticException("Division by zero");
                }
                yield a / b;
            }
            default -> throw new IllegalArgumentException("Unknown operation: " + operation);
        };
    }

    // ==================== System Info ====================

    @KremaCommand
    public SystemInfo systemInfo() {
        return new SystemInfo(
            System.getProperty("os.name"),
            System.getProperty("os.version"),
            System.getProperty("os.arch"),
            System.getProperty("java.version"),
            System.getProperty("java.vendor"),
            Runtime.getRuntime().availableProcessors(),
            Runtime.getRuntime().maxMemory() / 1024 / 1024,
            Runtime.getRuntime().totalMemory() / 1024 / 1024,
            Runtime.getRuntime().freeMemory() / 1024 / 1024
        );
    }

    @KremaCommand
    public EnvironmentInfo environmentInfo() {
        return new EnvironmentInfo(
            System.getProperty("user.name"),
            System.getProperty("user.home"),
            System.getProperty("user.dir"),
            System.getenv("PATH"),
            Platform.current().name()
        );
    }

    // ==================== Timer (Backend Events) ====================

    @KremaCommand
    public String startTimer() {
        if (timerService != null && !timerService.isShutdown()) {
            return "Timer already running";
        }

        tickCount = 0;
        timerService = Executors.newSingleThreadScheduledExecutor();
        timerService.scheduleAtFixedRate(() -> {
            EventEmitter emitter = emitterRef.get();
            if (emitter != null) {
                tickCount++;
                emitter.emit("timer-tick", Map.of(
                    "count", tickCount,
                    "timestamp", System.currentTimeMillis()
                ));
            }
        }, 0, 1, TimeUnit.SECONDS);

        return "Timer started";
    }

    @KremaCommand
    public String stopTimer() {
        if (timerService != null) {
            timerService.shutdown();
            EventEmitter emitter = emitterRef.get();
            if (emitter != null) {
                emitter.emit("timer-stopped", Map.of("finalCount", tickCount));
            }
        }
        return "Timer stopped";
    }

    // ==================== Clipboard ====================

    @KremaCommand
    public String clipboardRead() {
        try {
            return switch (Platform.current()) {
                case MACOS -> execCommand("pbpaste");
                case LINUX -> execCommand("xclip", "-selection", "clipboard", "-o");
                case WINDOWS -> execCommand("powershell", "-NoProfile", "-c", "Get-Clipboard");
                case UNKNOWN -> "";
            };
        } catch (Exception e) {
            throw new RuntimeException("Failed to read clipboard: " + e.getMessage());
        }
    }

    @KremaCommand
    public boolean clipboardWrite(String text) {
        try {
            return switch (Platform.current()) {
                case MACOS -> pipeToCommand(text, "pbcopy");
                case LINUX -> pipeToCommand(text, "xclip", "-selection", "clipboard");
                case WINDOWS -> pipeToCommand(text, "powershell", "-NoProfile", "-c", "$input | Set-Clipboard");
                case UNKNOWN -> false;
            };
        } catch (Exception e) {
            throw new RuntimeException("Failed to write to clipboard: " + e.getMessage());
        }
    }

    // ==================== Shell / OS Integration ====================

    @KremaCommand
    public boolean openUrl(String url) {
        try {
            return switch (Platform.current()) {
                case MACOS -> new ProcessBuilder("open", url).start().waitFor() == 0;
                case LINUX -> new ProcessBuilder("xdg-open", url).start().waitFor() == 0;
                case WINDOWS -> new ProcessBuilder("rundll32", "url.dll,FileProtocolHandler", url).start().waitFor() == 0;
                case UNKNOWN -> false;
            };
        } catch (Exception e) {
            throw new RuntimeException("Failed to open URL: " + e.getMessage());
        }
    }

    @KremaCommand
    public boolean openPath(String path) {
        try {
            return switch (Platform.current()) {
                case MACOS -> new ProcessBuilder("open", path).start().waitFor() == 0;
                case LINUX -> new ProcessBuilder("xdg-open", path).start().waitFor() == 0;
                case WINDOWS -> new ProcessBuilder("explorer", path).start().waitFor() == 0;
                case UNKNOWN -> false;
            };
        } catch (Exception e) {
            throw new RuntimeException("Failed to open path: " + e.getMessage());
        }
    }

    @KremaCommand
    public boolean revealInFinder(String path) {
        try {
            if (Platform.current() == Platform.MACOS) {
                Runtime.getRuntime().exec(new String[]{"open", "-R", path});
                return true;
            } else if (Platform.current() == Platform.WINDOWS) {
                Runtime.getRuntime().exec(new String[]{"explorer", "/select,", path});
                return true;
            }
            return false;
        } catch (Exception e) {
            throw new RuntimeException("Failed to reveal in finder: " + e.getMessage());
        }
    }

    @KremaCommand
    public CommandResult runCommand(String command) {
        try {
            ProcessBuilder pb;
            if (Platform.current() == Platform.WINDOWS) {
                pb = new ProcessBuilder("cmd", "/c", command);
            } else {
                pb = new ProcessBuilder("sh", "-c", command);
            }
            pb.redirectErrorStream(true);

            Process process = pb.start();
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }

            boolean completed = process.waitFor(10, TimeUnit.SECONDS);
            if (!completed) {
                process.destroyForcibly();
                return new CommandResult(-1, "", "Command timed out");
            }

            return new CommandResult(
                process.exitValue(),
                output.toString().trim(),
                ""
            );
        } catch (Exception e) {
            return new CommandResult(-1, "", e.getMessage());
        }
    }

    // ==================== Notifications ====================

    @KremaCommand
    public boolean showNotification(String title, String body) {
        NotificationEngine engine = NotificationEngineFactory.get();
        if (engine != null) {
            return engine.show(title, body, Map.of("sound", "default"));
        }
        return false;
    }

    // ==================== File System ====================

    @KremaCommand
    public PathInfo getPaths() {
        String home = System.getProperty("user.home");
        return new PathInfo(
            home,
            Path.of(home, "Desktop").toString(),
            Path.of(home, "Documents").toString(),
            Path.of(home, "Downloads").toString(),
            System.getProperty("java.io.tmpdir"),
            System.getProperty("user.dir")
        );
    }

    @KremaCommand
    public FileInfo[] listDirectory(String path) {
        try {
            File dir = new File(path);
            if (!dir.isDirectory()) {
                throw new IllegalArgumentException("Not a directory: " + path);
            }
            File[] files = dir.listFiles();
            if (files == null) return new FileInfo[0];

            return java.util.Arrays.stream(files)
                .map(f -> new FileInfo(
                    f.getName(),
                    f.getAbsolutePath(),
                    f.isDirectory(),
                    f.length(),
                    f.lastModified()
                ))
                .toArray(FileInfo[]::new);
        } catch (Exception e) {
            throw new RuntimeException("Failed to list directory: " + e.getMessage());
        }
    }

    @KremaCommand
    public String readTextFile(String path) {
        try {
            return Files.readString(Path.of(path));
        } catch (Exception e) {
            throw new RuntimeException("Failed to read file: " + e.getMessage());
        }
    }

    @KremaCommand
    public boolean writeTextFile(String path, String content) {
        try {
            Files.writeString(Path.of(path), content);
            return true;
        } catch (Exception e) {
            throw new RuntimeException("Failed to write file: " + e.getMessage());
        }
    }

    // ==================== Helpers ====================

    private String execCommand(String... command) throws Exception {
        Process p = new ProcessBuilder(command).redirectErrorStream(true).start();
        String output;
        try (var is = p.getInputStream()) {
            output = new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
        return p.waitFor() == 0 ? output : "";
    }

    private boolean pipeToCommand(String input, String... command) throws Exception {
        Process p = new ProcessBuilder(command).start();
        try (OutputStream os = p.getOutputStream()) {
            os.write(input.getBytes(StandardCharsets.UTF_8));
        }
        return p.waitFor() == 0;
    }

    // ==================== Records ====================

    public record SystemInfo(
        String osName,
        String osVersion,
        String osArch,
        String javaVersion,
        String javaVendor,
        int processors,
        long maxMemoryMb,
        long totalMemoryMb,
        long freeMemoryMb
    ) {}

    public record EnvironmentInfo(
        String username,
        String homeDir,
        String workingDir,
        String path,
        String platform
    ) {}

    public record CommandResult(int code, String stdout, String stderr) {}

    public record PathInfo(
        String home,
        String desktop,
        String documents,
        String downloads,
        String temp,
        String current
    ) {}

    public record FileInfo(
        String name,
        String path,
        boolean isDirectory,
        long size,
        long lastModified
    ) {}
}
