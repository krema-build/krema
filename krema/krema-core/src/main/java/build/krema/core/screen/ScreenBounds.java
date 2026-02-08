package build.krema.core.screen;

/**
 * Represents the bounds of a rectangular area (screen or region).
 */
public record ScreenBounds(double x, double y, double width, double height) {

    /**
     * Returns true if the given point is within these bounds.
     */
    public boolean contains(double px, double py) {
        return px >= x && px < x + width && py >= y && py < y + height;
    }
}
