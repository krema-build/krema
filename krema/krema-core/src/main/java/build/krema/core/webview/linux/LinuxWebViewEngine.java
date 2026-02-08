package build.krema.core.webview.linux;

import build.krema.core.webview.WebViewCLibEngine;

/**
 * Linux WebViewEngine implementation using WebKitGTK via the webview C library.
 * Requires libwebkit2gtk-4.1 (or 4.0) and libgtk-3-0 installed on the system.
 */
public final class LinuxWebViewEngine extends WebViewCLibEngine {

    public LinuxWebViewEngine(boolean debug) {
        super(debug);
    }
}
