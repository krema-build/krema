package build.krema.core.error;

import java.io.PrintWriter;
import java.io.StringWriter;

/**
 * Immutable record representing an error from either Java or the WebView.
 */
public record ErrorInfo(
    Source source,
    String message,
    String stackTrace,
    String threadName,
    String fileName,
    int lineNumber,
    ErrorContext context
) {

    public enum Source { JAVA, WEBVIEW }

    /**
     * Creates an ErrorInfo for a Java uncaught exception.
     */
    public static ErrorInfo fromJava(Thread thread, Throwable throwable, ErrorContext context) {
        StringWriter sw = new StringWriter();
        throwable.printStackTrace(new PrintWriter(sw));
        return new ErrorInfo(
            Source.JAVA,
            throwable.getClass().getName() + ": " + throwable.getMessage(),
            sw.toString(),
            thread.getName(),
            null,
            0,
            context
        );
    }

    /**
     * Creates an ErrorInfo for a WebView error reported via JS.
     */
    public static ErrorInfo fromWebView(String message, String source, int lineno, String stack, ErrorContext context) {
        return new ErrorInfo(
            Source.WEBVIEW,
            message,
            stack,
            null,
            source,
            lineno,
            context
        );
    }
}
