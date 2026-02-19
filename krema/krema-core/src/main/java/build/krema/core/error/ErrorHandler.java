package build.krema.core.error;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

import build.krema.core.event.EventEmitter;
import build.krema.core.util.Json;
import build.krema.core.util.LogContext;
import build.krema.core.util.Logger;

/**
 * Central error handler for Krema applications.
 *
 * <p>Installs a global {@link Thread.UncaughtExceptionHandler} for Java errors
 * and accepts WebView error reports via {@link #handleWebViewError}.
 * Both paths produce an {@link ErrorInfo}, then:
 * <ol>
 *   <li>Log via {@link Logger}</li>
 *   <li>Emit an {@code app:error} event via {@link EventEmitter}</li>
 *   <li>Call the user's {@code onError} hook if set</li>
 *   <li>Write a crash report JSON file</li>
 * </ol>
 */
public class ErrorHandler implements Thread.UncaughtExceptionHandler {

    private static final DateTimeFormatter CRASH_FILE_FORMAT =
        DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS").withZone(ZoneId.systemDefault());

    private final Logger logger = new Logger("ErrorHandler");
    private final EventEmitter eventEmitter;
    private final Path crashReportDir;
    private final Consumer<ErrorInfo> userHandler;
    private final Supplier<List<String>> recentCommandsSupplier;

    private Thread.UncaughtExceptionHandler previousHandler;

    public ErrorHandler(
            EventEmitter eventEmitter,
            Path crashReportDir,
            Consumer<ErrorInfo> userHandler,
            Supplier<List<String>> recentCommandsSupplier) {
        this.eventEmitter = eventEmitter;
        this.crashReportDir = crashReportDir;
        this.userHandler = userHandler;
        this.recentCommandsSupplier = recentCommandsSupplier;
    }

    /**
     * Installs this handler as the global uncaught exception handler.
     */
    public void install() {
        previousHandler = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler(this);
    }

    /**
     * Restores the previous uncaught exception handler.
     */
    public void uninstall() {
        Thread.setDefaultUncaughtExceptionHandler(previousHandler);
    }

    @Override
    public void uncaughtException(Thread thread, Throwable throwable) {
        handleJavaError(thread, throwable);
    }

    /**
     * Handles an uncaught Java exception.
     */
    void handleJavaError(Thread thread, Throwable throwable) {
        ErrorContext context = buildContext();
        ErrorInfo info = ErrorInfo.fromJava(thread, throwable, context);
        processError(info, throwable);
    }

    /**
     * Handles a WebView error reported from JavaScript.
     */
    public void handleWebViewError(String message, String source, int lineno, String stack) {
        ErrorContext context = buildContext();
        ErrorInfo info = ErrorInfo.fromWebView(message, source, lineno, stack, context);
        processError(info, null);
    }

    private void processError(ErrorInfo info, Throwable throwable) {
        // 1. Log
        if (throwable != null) {
            logger.error("Uncaught " + info.source() + " error: " + info.message(), throwable);
        } else {
            logger.error("Uncaught %s error: %s", info.source(), info.message());
        }

        // 2. Emit app:error event
        if (eventEmitter != null) {
            try {
                eventEmitter.emit("app:error", buildEventPayload(info));
            } catch (Exception e) {
                // Don't let event emission failure mask the original error
                System.err.println("[ErrorHandler] Failed to emit app:error event: " + e.getMessage());
            }
        }

        // 3. Call user hook
        if (userHandler != null) {
            try {
                userHandler.accept(info);
            } catch (Exception e) {
                System.err.println("[ErrorHandler] User error handler threw: " + e.getMessage());
            }
        }

        // 4. Write crash report
        writeCrashReport(info);
    }

    private ErrorContext buildContext() {
        LogContext logContext = Logger.getLogContext();
        List<String> commands = recentCommandsSupplier != null
            ? recentCommandsSupplier.get()
            : List.of();
        return ErrorContext.from(logContext, commands);
    }

    private Map<String, Object> buildEventPayload(ErrorInfo info) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("source", info.source().name());
        payload.put("message", info.message());
        payload.put("stackTrace", info.stackTrace());
        if (info.threadName() != null) {
            payload.put("thread", info.threadName());
        }
        if (info.fileName() != null) {
            payload.put("fileName", info.fileName());
            payload.put("lineNumber", info.lineNumber());
        }
        ErrorContext ctx = info.context();
        if (ctx != null) {
            payload.put("os", ctx.os());
            payload.put("appVersion", ctx.appVersion());
            payload.put("recentCommands", ctx.recentCommands());
        }
        return payload;
    }

    void writeCrashReport(ErrorInfo info) {
        if (crashReportDir == null) {
            return;
        }
        try {
            Files.createDirectories(crashReportDir);
            String timestamp = CRASH_FILE_FORMAT.format(Instant.now());
            Path file = crashReportDir.resolve("crash-" + timestamp + ".json");

            Map<String, Object> report = new LinkedHashMap<>();
            report.put("timestamp", Instant.now().toString());
            report.put("source", info.source().name());
            report.put("message", info.message());
            report.put("stackTrace", info.stackTrace());
            report.put("threadName", info.threadName());
            report.put("fileName", info.fileName());
            report.put("lineNumber", info.lineNumber());

            ErrorContext ctx = info.context();
            if (ctx != null) {
                Map<String, Object> contextMap = new LinkedHashMap<>();
                contextMap.put("appName", ctx.appName());
                contextMap.put("appVersion", ctx.appVersion());
                contextMap.put("os", ctx.os());
                contextMap.put("sessionId", ctx.sessionId());
                contextMap.put("recentCommands", ctx.recentCommands());
                report.put("context", contextMap);
            }

            Files.writeString(file, Json.prettyMapper().writeValueAsString(report));
        } catch (IOException e) {
            System.err.println("[ErrorHandler] Failed to write crash report: " + e.getMessage());
        }
    }
}
