package build.krema.core.screen;

import java.util.List;

import build.krema.core.ports.ScreenPort;

/**
 * Platform-agnostic interface for screen/display information.
 * Implementations provide platform-specific screen functionality.
 *
 * @deprecated Use {@link ScreenPort} instead. This interface is maintained
 *             for backward compatibility and will be removed in a future version.
 * @see ScreenPort
 */
@Deprecated(since = "2.0", forRemoval = true)
public interface ScreenEngine {

    /**
     * Returns information about all connected screens.
     *
     * @return list of screen information
     */
    List<ScreenInfo> getAllScreens();

    /**
     * Returns information about the primary screen.
     *
     * @return primary screen information
     */
    ScreenInfo getPrimaryScreen();

    /**
     * Returns the current cursor position in screen coordinates.
     *
     * @return cursor position
     */
    CursorPosition getCursorPosition();

    /**
     * Returns the screen containing the specified point.
     *
     * @param x x coordinate
     * @param y y coordinate
     * @return screen info, or null if no screen contains the point
     */
    ScreenInfo getScreenAtPoint(double x, double y);
}
