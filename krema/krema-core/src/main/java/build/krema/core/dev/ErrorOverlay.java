package build.krema.core.dev;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import build.krema.core.webview.WebViewEngine;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Map;

/**
 * Displays error overlays in the webview during development.
 */
public class ErrorOverlay {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final WebViewEngine engine;

    public ErrorOverlay(WebViewEngine engine) {
        this.engine = engine;
        injectStyles();
    }

    private void injectStyles() {
        String css = """
            #__krema_error_overlay {
                position: fixed;
                top: 0;
                left: 0;
                right: 0;
                bottom: 0;
                background: rgba(0, 0, 0, 0.9);
                color: #ff6b6b;
                font-family: 'SF Mono', Monaco, 'Consolas', monospace;
                font-size: 14px;
                padding: 20px;
                overflow: auto;
                z-index: 999999;
                box-sizing: border-box;
            }
            #__krema_error_overlay h1 {
                color: #ff6b6b;
                font-size: 24px;
                margin: 0 0 20px 0;
                display: flex;
                align-items: center;
                gap: 10px;
            }
            #__krema_error_overlay h1::before {
                content: '\\26A0';
                font-size: 28px;
            }
            #__krema_error_overlay .error-message {
                background: #2d1f1f;
                border-left: 4px solid #ff6b6b;
                padding: 15px;
                margin-bottom: 20px;
                border-radius: 4px;
            }
            #__krema_error_overlay .stack-trace {
                background: #1a1a1a;
                padding: 15px;
                border-radius: 4px;
                white-space: pre-wrap;
                word-wrap: break-word;
                color: #888;
                line-height: 1.5;
            }
            #__krema_error_overlay .stack-trace .highlight {
                color: #ff6b6b;
            }
            #__krema_error_overlay .dismiss-btn {
                position: absolute;
                top: 20px;
                right: 20px;
                background: #333;
                border: none;
                color: #888;
                padding: 8px 16px;
                border-radius: 4px;
                cursor: pointer;
                font-size: 14px;
            }
            #__krema_error_overlay .dismiss-btn:hover {
                background: #444;
                color: #fff;
            }
            #__krema_error_overlay .file-link {
                color: #4fc3f7;
                text-decoration: underline;
                cursor: pointer;
            }
            """;

        String js = """
            (function() {
                if (!document.getElementById('__krema_error_styles')) {
                    const style = document.createElement('style');
                    style.id = '__krema_error_styles';
                    style.textContent = %s;
                    document.head.appendChild(style);
                }

                window.__krema_showError = function(title, message, stack) {
                    let overlay = document.getElementById('__krema_error_overlay');
                    if (!overlay) {
                        overlay = document.createElement('div');
                        overlay.id = '__krema_error_overlay';
                        document.body.appendChild(overlay);
                    }

                    overlay.innerHTML = `
                        <button class="dismiss-btn" onclick="window.__krema_hideError()">Dismiss</button>
                        <h1>${title}</h1>
                        <div class="error-message">${message}</div>
                        <div class="stack-trace">${stack || ''}</div>
                    `;
                    overlay.style.display = 'block';
                };

                window.__krema_hideError = function() {
                    const overlay = document.getElementById('__krema_error_overlay');
                    if (overlay) {
                        overlay.style.display = 'none';
                    }
                };
            })();
            """;

        try {
            String cssJson = MAPPER.writeValueAsString(css);
            engine.init(String.format(js, cssJson));
        } catch (JsonProcessingException e) {
            // Ignore
        }
    }

    public void showError(String title, String message, Throwable throwable) {
        String stack = throwable != null ? getStackTrace(throwable) : "";
        showError(title, message, stack);
    }

    public void showError(String title, String message, String stackTrace) {
        try {
            String titleJson = MAPPER.writeValueAsString(escapeHtml(title));
            String messageJson = MAPPER.writeValueAsString(escapeHtml(message));
            String stackJson = MAPPER.writeValueAsString(formatStackTrace(stackTrace));

            String js = String.format(
                "window.__krema_showError && window.__krema_showError(%s, %s, %s)",
                titleJson, messageJson, stackJson
            );
            engine.eval(js);
        } catch (JsonProcessingException e) {
            // Ignore
        }
    }

    public void showCompilationError(String error) {
        showError("Compilation Error", "Failed to compile Java sources", error);
    }

    public void showRuntimeError(Throwable throwable) {
        showError("Runtime Error", throwable.getMessage(), throwable);
    }

    public void hide() {
        engine.eval("window.__krema_hideError && window.__krema_hideError()");
    }

    private String getStackTrace(Throwable throwable) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        throwable.printStackTrace(pw);
        return sw.toString();
    }

    private String escapeHtml(String text) {
        if (text == null) return "";
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;");
    }

    private String formatStackTrace(String stackTrace) {
        if (stackTrace == null || stackTrace.isEmpty()) {
            return "";
        }

        // Highlight important parts of the stack trace
        return escapeHtml(stackTrace)
            .replaceAll("(at [\\w$.]+\\([\\w]+\\.java:\\d+\\))",
                "<span class=\"highlight\">$1</span>")
            .replaceAll("(Caused by: [\\w$.]+:)",
                "<span class=\"highlight\">$1</span>");
    }
}
