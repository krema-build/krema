package build.krema.core.window;

/**
 * Represents the current state of a window.
 */
public record WindowState(
    int x,
    int y,
    int width,
    int height,
    boolean minimized,
    boolean maximized,
    boolean fullscreen,
    boolean focused,
    boolean visible
) {
    /**
     * Creates a basic window state with position and size.
     */
    public static WindowState of(int x, int y, int width, int height) {
        return new WindowState(x, y, width, height, false, false, false, true, true);
    }
}
