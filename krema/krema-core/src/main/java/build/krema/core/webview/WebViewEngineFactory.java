package build.krema.core.webview;

import build.krema.core.platform.Platform;
import build.krema.core.webview.linux.LinuxWebViewEngine;
import build.krema.core.webview.macos.MacOSWebViewEngine;
import build.krema.core.webview.windows.WindowsWebViewEngine;

/**
 * Factory for creating platform-specific WebViewEngine instances.
 */
public final class WebViewEngineFactory {

    private WebViewEngineFactory() {}

    /**
     * Creates a WebViewEngine for the current platform.
     *
     * @param debug enable developer tools
     * @return a new WebViewEngine instance
     * @throws UnsupportedPlatformException if the platform is not supported
     */
    public static WebViewEngine create(boolean debug) {
        Platform platform = Platform.current();

        return switch (platform) {
            case MACOS -> new MacOSWebViewEngine(debug);
            case WINDOWS -> new WindowsWebViewEngine(debug);
            case LINUX -> new LinuxWebViewEngine(debug);
            case UNKNOWN -> throw new UnsupportedPlatformException(
                "Unknown platform: " + System.getProperty("os.name")
            );
        };
    }

    /**
     * Creates a WebViewEngine with developer tools disabled.
     */
    public static WebViewEngine create() {
        return create(false);
    }

    /**
     * Checks if the current platform is supported.
     */
    public static boolean isPlatformSupported() {
        Platform current = Platform.current();
        return current == Platform.MACOS || current == Platform.WINDOWS || current == Platform.LINUX;
    }

    /**
     * Exception thrown when the platform is not supported.
     */
    public static class UnsupportedPlatformException extends RuntimeException {
        public UnsupportedPlatformException(String message) {
            super(message);
        }
    }
}
