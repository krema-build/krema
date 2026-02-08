package build.krema.core.webview.macos;

import build.krema.core.webview.WebViewCLibEngine;

/**
 * macOS WebViewEngine implementation using WebKit via the webview C library.
 */
public final class MacOSWebViewEngine extends WebViewCLibEngine {

    public MacOSWebViewEngine(boolean debug) {
        super(debug);
    }
}
