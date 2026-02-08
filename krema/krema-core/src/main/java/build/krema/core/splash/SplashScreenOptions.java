package build.krema.core.splash;

import java.awt.*;

/**
 * Configuration options for the splash screen.
 * Uses the builder pattern for fluent configuration.
 */
public record SplashScreenOptions(
    String imagePath,
    String appName,
    String version,
    int width,
    int height,
    Color backgroundColor,
    Color textColor,
    Color progressColor,
    boolean showProgress,
    boolean showStatus,
    boolean fadeOut
) {

    /**
     * Creates a builder for SplashScreenOptions.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for SplashScreenOptions with sensible defaults.
     */
    public static class Builder {
        private String imagePath = null;
        private String appName = "Krema";
        private String version = null;
        private int width = 480;
        private int height = 320;
        private Color backgroundColor = new Color(102, 126, 234);
        private Color textColor = Color.WHITE;
        private Color progressColor = new Color(255, 255, 255, 200);
        private boolean showProgress = true;
        private boolean showStatus = true;
        private boolean fadeOut = true;

        /**
         * Sets the path to the splash image.
         * Can be a file path or classpath resource.
         */
        public Builder imagePath(String imagePath) {
            this.imagePath = imagePath;
            return this;
        }

        /**
         * Sets the application name displayed on the default splash.
         */
        public Builder appName(String appName) {
            this.appName = appName;
            return this;
        }

        /**
         * Sets the version displayed on the default splash.
         */
        public Builder version(String version) {
            this.version = version;
            return this;
        }

        /**
         * Sets the splash screen width.
         */
        public Builder width(int width) {
            this.width = width;
            return this;
        }

        /**
         * Sets the splash screen height.
         */
        public Builder height(int height) {
            this.height = height;
            return this;
        }

        /**
         * Sets the width and height together.
         */
        public Builder size(int width, int height) {
            this.width = width;
            this.height = height;
            return this;
        }

        /**
         * Sets the background color.
         */
        public Builder backgroundColor(Color backgroundColor) {
            this.backgroundColor = backgroundColor;
            return this;
        }

        /**
         * Sets the text color.
         */
        public Builder textColor(Color textColor) {
            this.textColor = textColor;
            return this;
        }

        /**
         * Sets the progress bar color.
         */
        public Builder progressColor(Color progressColor) {
            this.progressColor = progressColor;
            return this;
        }

        /**
         * Whether to show the progress bar.
         */
        public Builder showProgress(boolean showProgress) {
            this.showProgress = showProgress;
            return this;
        }

        /**
         * Whether to show the status text.
         */
        public Builder showStatus(boolean showStatus) {
            this.showStatus = showStatus;
            return this;
        }

        /**
         * Whether to fade out when hiding.
         */
        public Builder fadeOut(boolean fadeOut) {
            this.fadeOut = fadeOut;
            return this;
        }

        /**
         * Builds the SplashScreenOptions.
         */
        public SplashScreenOptions build() {
            return new SplashScreenOptions(
                imagePath,
                appName,
                version,
                width,
                height,
                backgroundColor,
                textColor,
                progressColor,
                showProgress,
                showStatus,
                fadeOut
            );
        }
    }
}
