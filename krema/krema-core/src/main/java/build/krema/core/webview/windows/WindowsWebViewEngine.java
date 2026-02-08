package build.krema.core.webview.windows;

import build.krema.core.webview.WebViewCLibEngine;

/**
 * Windows WebViewEngine implementation using WebView2 (Edge/Chromium) via the webview C library.
 * Requires Microsoft Edge WebView2 Runtime installed on the system.
 *
 * <p>The webview C library abstracts WebView2 COM API internally, so this class
 * simply extends {@link WebViewCLibEngine} (same approach as Linux).</p>
 *
 * <p>{@code webview_get_window()} returns the native HWND on Windows.</p>
 */
public final class WindowsWebViewEngine extends WebViewCLibEngine {

    public WindowsWebViewEngine(boolean debug) {
        super(debug);
    }
}
