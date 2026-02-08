package build.krema.core.ports;

import java.util.List;

import build.krema.core.screen.CursorPosition;
import build.krema.core.screen.ScreenInfo;

/**
 * Port interface for screen/display information.
 * Implementations provide platform-specific screen detection.
 *
 * @see build.krema.core.screen.ScreenEngine
 */
public interface ScreenPort {

    /**
     * Gets information about all connected displays.
     *
     * @return list of screen information
     */
    List<ScreenInfo> getAllScreens();

    /**
     * Gets information about the primary display.
     *
     * @return primary screen information
     */
    ScreenInfo getPrimaryScreen();

    /**
     * Gets the current cursor position.
     *
     * @return cursor position in screen coordinates
     */
    CursorPosition getCursorPosition();

    /**
     * Gets the screen containing the specified point.
     *
     * @param x screen x coordinate
     * @param y screen y coordinate
     * @return screen information, or primary screen if not found
     */
    default ScreenInfo getScreenAt(int x, int y) {
        for (ScreenInfo screen : getAllScreens()) {
            if (screen.frame().contains(x, y)) {
                return screen;
            }
        }
        return getPrimaryScreen();
    }
}
