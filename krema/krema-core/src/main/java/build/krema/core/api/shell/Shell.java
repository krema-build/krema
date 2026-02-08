package build.krema.core.api.shell;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import build.krema.core.KremaCommand;
import build.krema.core.platform.Platform;

/**
 * Shell command execution utilities.
 * Uses platform-specific commands (open, xdg-open, cmd) instead of AWT Desktop.
 */
public class Shell {

    @KremaCommand("shell:execute")
    public CommandResult execute(String command, Map<String, Object> options) throws IOException, InterruptedException {
        List<String> args = parseCommand(command);
        return executeInternal(args, options);
    }

    @KremaCommand("shell:open")
    public boolean open(String path) {
        try {
            if (path.startsWith("http://") || path.startsWith("https://")) {
                return openUrl(path);
            } else {
                return openFile(Path.of(path));
            }
        } catch (Exception e) {
            return false;
        }
    }

    @KremaCommand("shell:openUrl")
    public boolean openUrl(String url) {
        try {
            return switch (Platform.current()) {
                case MACOS -> {
                    Runtime.getRuntime().exec(new String[]{"open", url});
                    yield true;
                }
                case WINDOWS -> {
                    Runtime.getRuntime().exec(new String[]{"cmd", "/c", "start", url});
                    yield true;
                }
                case LINUX -> {
                    Runtime.getRuntime().exec(new String[]{"xdg-open", url});
                    yield true;
                }
                case UNKNOWN -> false;
            };
        } catch (IOException e) {
            return false;
        }
    }

    @KremaCommand("shell:openFile")
    public boolean openFile(Path path) {
        try {
            return switch (Platform.current()) {
                case MACOS -> {
                    Runtime.getRuntime().exec(new String[]{"open", path.toString()});
                    yield true;
                }
                case WINDOWS -> {
                    Runtime.getRuntime().exec(new String[]{"cmd", "/c", "start", "", path.toString()});
                    yield true;
                }
                case LINUX -> {
                    Runtime.getRuntime().exec(new String[]{"xdg-open", path.toString()});
                    yield true;
                }
                case UNKNOWN -> false;
            };
        } catch (IOException e) {
            return false;
        }
    }

    @KremaCommand("shell:revealInFinder")
    public boolean revealInFinder(String path) {
        return showItemInFolder(path);
    }

    @KremaCommand("shell:showItemInFolder")
    public boolean showItemInFolder(String path) {
        if (path == null || path.isEmpty()) {
            return false;
        }
        try {
            return switch (Platform.current()) {
                case MACOS -> {
                    Runtime.getRuntime().exec(new String[]{"open", "-R", path});
                    yield true;
                }
                case WINDOWS -> {
                    Runtime.getRuntime().exec(new String[]{"explorer", "/select,", path});
                    yield true;
                }
                case LINUX -> {
                    // Linux doesn't have a standard "reveal" command, open the directory
                    Path dir = Path.of(path).getParent();
                    Runtime.getRuntime().exec(new String[]{"xdg-open", dir.toString()});
                    yield true;
                }
                case UNKNOWN -> false;
            };
        } catch (IOException e) {
            return false;
        }
    }

    @KremaCommand("shell:openWith")
    public boolean openWith(String path, String app) {
        try {
            return switch (Platform.current()) {
                case MACOS -> {
                    Runtime.getRuntime().exec(new String[]{"open", "-a", app, path});
                    yield true;
                }
                case WINDOWS -> {
                    Runtime.getRuntime().exec(new String[]{"cmd", "/c", "start", "", app, path});
                    yield true;
                }
                case LINUX -> {
                    Runtime.getRuntime().exec(new String[]{app, path});
                    yield true;
                }
                case UNKNOWN -> false;
            };
        } catch (IOException e) {
            return false;
        }
    }

    private CommandResult executeInternal(List<String> args, Map<String, Object> options) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(args);

        // Set working directory
        if (options != null && options.containsKey("cwd")) {
            pb.directory(new File(options.get("cwd").toString()));
        }

        // Set environment variables
        if (options != null && options.containsKey("env")) {
            @SuppressWarnings("unchecked")
            Map<String, String> env = (Map<String, String>) options.get("env");
            pb.environment().putAll(env);
        }

        // Merge stderr into stdout
        pb.redirectErrorStream(true);

        Process process = pb.start();

        // Read output
        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
        }

        // Wait for completion with optional timeout
        long timeout = 30000; // Default 30 seconds
        if (options != null && options.containsKey("timeout")) {
            timeout = ((Number) options.get("timeout")).longValue();
        }

        boolean completed = process.waitFor(timeout, TimeUnit.MILLISECONDS);
        if (!completed) {
            process.destroyForcibly();
            return new CommandResult(-1, "", "Command timed out");
        }

        int exitCode = process.exitValue();
        String stdout = output.toString().trim();

        return new CommandResult(exitCode, stdout, exitCode == 0 ? "" : stdout);
    }

    private List<String> parseCommand(String command) {
        // Simple command parsing - splits on spaces but respects quotes
        List<String> args = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        char quoteChar = 0;

        for (char c : command.toCharArray()) {
            if ((c == '"' || c == '\'') && !inQuotes) {
                inQuotes = true;
                quoteChar = c;
            } else if (c == quoteChar && inQuotes) {
                inQuotes = false;
                quoteChar = 0;
            } else if (c == ' ' && !inQuotes) {
                if (!current.isEmpty()) {
                    args.add(current.toString());
                    current = new StringBuilder();
                }
            } else {
                current.append(c);
            }
        }

        if (!current.isEmpty()) {
            args.add(current.toString());
        }

        return args;
    }

    public record CommandResult(int code, String stdout, String stderr) {
        public boolean success() {
            return code == 0;
        }
    }
}
