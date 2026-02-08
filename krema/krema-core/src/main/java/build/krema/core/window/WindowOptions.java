package build.krema.core.window;

/**
 * Configuration options for window creation.
 */
public record WindowOptions(
    String title,
    int width,
    int height,
    int minWidth,
    int minHeight,
    int maxWidth,
    int maxHeight,
    boolean resizable,
    boolean fullscreen,
    boolean alwaysOnTop,
    boolean transparent,
    boolean decorations,
    boolean center,
    Integer x,
    Integer y,
    boolean debug,
    // Frameless window options
    TitleBarStyle titleBarStyle,
    Integer trafficLightX,
    Integer trafficLightY,
    boolean titlebarAppearsTransparent
) {
    /**
     * Title bar style options for window chrome customization.
     */
    public enum TitleBarStyle {
        /** Default system title bar */
        DEFAULT,
        /** Hidden title bar - content extends to window edge */
        HIDDEN,
        /** Hidden title bar with inset traffic lights (macOS style) */
        HIDDEN_INSET
    }
    public static Builder builder() {
        return new Builder();
    }

    public static WindowOptions defaults() {
        return builder().build();
    }

    public static class Builder {
        private String title = "Krema App";
        private int width = 1024;
        private int height = 768;
        private int minWidth = 0;
        private int minHeight = 0;
        private int maxWidth = Integer.MAX_VALUE;
        private int maxHeight = Integer.MAX_VALUE;
        private boolean resizable = true;
        private boolean fullscreen = false;
        private boolean alwaysOnTop = false;
        private boolean transparent = false;
        private boolean decorations = true;
        private boolean center = true;
        private Integer x = null;
        private Integer y = null;
        private boolean debug = false;
        private TitleBarStyle titleBarStyle = TitleBarStyle.DEFAULT;
        private Integer trafficLightX = null;
        private Integer trafficLightY = null;
        private boolean titlebarAppearsTransparent = false;

        public Builder title(String title) {
            this.title = title;
            return this;
        }

        public Builder size(int width, int height) {
            this.width = width;
            this.height = height;
            return this;
        }

        public Builder width(int width) {
            this.width = width;
            return this;
        }

        public Builder height(int height) {
            this.height = height;
            return this;
        }

        public Builder minSize(int minWidth, int minHeight) {
            this.minWidth = minWidth;
            this.minHeight = minHeight;
            return this;
        }

        public Builder maxSize(int maxWidth, int maxHeight) {
            this.maxWidth = maxWidth;
            this.maxHeight = maxHeight;
            return this;
        }

        public Builder resizable(boolean resizable) {
            this.resizable = resizable;
            return this;
        }

        public Builder fullscreen(boolean fullscreen) {
            this.fullscreen = fullscreen;
            return this;
        }

        public Builder alwaysOnTop(boolean alwaysOnTop) {
            this.alwaysOnTop = alwaysOnTop;
            return this;
        }

        public Builder transparent(boolean transparent) {
            this.transparent = transparent;
            return this;
        }

        public Builder decorations(boolean decorations) {
            this.decorations = decorations;
            return this;
        }

        public Builder center(boolean center) {
            this.center = center;
            return this;
        }

        public Builder position(int x, int y) {
            this.x = x;
            this.y = y;
            this.center = false;
            return this;
        }

        public Builder debug(boolean debug) {
            this.debug = debug;
            return this;
        }

        public Builder debug() {
            return debug(true);
        }

        /**
         * Sets the title bar style for frameless/custom title bar windows.
         */
        public Builder titleBarStyle(TitleBarStyle style) {
            this.titleBarStyle = style;
            return this;
        }

        /**
         * Shorthand for hidden inset title bar style (macOS native frameless look).
         */
        public Builder hiddenInset() {
            this.titleBarStyle = TitleBarStyle.HIDDEN_INSET;
            this.titlebarAppearsTransparent = true;
            return this;
        }

        /**
         * Sets the traffic light (close/minimize/maximize buttons) position.
         * Only applicable when titleBarStyle is HIDDEN or HIDDEN_INSET on macOS.
         */
        public Builder trafficLightPosition(int x, int y) {
            this.trafficLightX = x;
            this.trafficLightY = y;
            return this;
        }

        /**
         * Makes the title bar transparent so content can show through.
         */
        public Builder titlebarAppearsTransparent(boolean transparent) {
            this.titlebarAppearsTransparent = transparent;
            return this;
        }

        public WindowOptions build() {
            return new WindowOptions(
                title, width, height, minWidth, minHeight, maxWidth, maxHeight,
                resizable, fullscreen, alwaysOnTop, transparent, decorations,
                center, x, y, debug,
                titleBarStyle, trafficLightX, trafficLightY, titlebarAppearsTransparent
            );
        }
    }
}
