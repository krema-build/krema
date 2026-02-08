package build.krema.core.screen;

/**
 * Detailed information about a display screen.
 */
public record ScreenInfo(
    String name,
    ScreenBounds frame,
    ScreenBounds visibleFrame,
    double scaleFactor,
    double refreshRate,
    boolean isPrimary
) {
}
