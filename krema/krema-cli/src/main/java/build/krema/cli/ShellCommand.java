package build.krema.cli;

import build.krema.core.platform.PlatformDetector;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Builds commands that work across platforms.
 * <p>
 * On Windows, {@link ProcessBuilder} cannot resolve {@code .cmd} or {@code .bat}
 * wrappers (e.g. {@code mvn}, {@code npm}, {@code pnpm}). This class prepends
 * {@code cmd /c} on Windows so the shell resolves the command.
 */
public final class ShellCommand {

    private ShellCommand() {}

    /**
     * Wraps the given command for the current platform.
     * On Windows, prepends {@code cmd /c} so that .cmd/.bat wrappers are resolved.
     * On Unix, returns the command as-is.
     */
    public static List<String> of(String... command) {
        if (PlatformDetector.isWindows()) {
            List<String> wrapped = new ArrayList<>(command.length + 2);
            wrapped.add("cmd");
            wrapped.add("/c");
            wrapped.addAll(Arrays.asList(command));
            return wrapped;
        }
        return Arrays.asList(command);
    }
}
