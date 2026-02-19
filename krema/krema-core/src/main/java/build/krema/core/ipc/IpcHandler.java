package build.krema.core.ipc;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import build.krema.core.KremaWindow;
import build.krema.core.util.Json;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;

import build.krema.core.error.ErrorHandler;

/**
 * Handles IPC (Inter-Process Communication) between JavaScript and Java.
 * Sets up the bridge between window.krema.invoke() calls and Java command handlers.
 */
public class IpcHandler {

    private static final ObjectMapper MAPPER = Json.mapper();
    private static final String BRIDGE_JS;
    private static final String DRAGDROP_JS;
    private static final String ERRORS_JS;
    private static final int MAX_RECENT_COMMANDS = 10;

    static {
        // Load the bridge JavaScript from resources
        BRIDGE_JS = loadResource("/krema-bridge.js");
        DRAGDROP_JS = loadResource("/krema-dragdrop.js");
        ERRORS_JS = loadResource("/krema-errors.js");
    }

    private static String loadResource(String path) {
        try (InputStream is = IpcHandler.class.getResourceAsStream(path)) {
            if (is == null) {
                throw new RuntimeException(path + " not found in resources");
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                return reader.lines().collect(Collectors.joining("\n"));
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to load " + path, e);
        }
    }

    private final KremaWindow window;
    private final String[] recentRing = new String[MAX_RECENT_COMMANDS];
    private final AtomicInteger ringIndex = new AtomicInteger(0);
    private Function<IpcRequest, Object> commandHandler;
    private ErrorHandler errorHandler;

    public IpcHandler(KremaWindow window) {
        this.window = window;
    }

    /**
     * Sets the command handler that processes incoming IPC requests.
     *
     * @param handler Function that takes an IpcRequest and returns a result object
     */
    public void setCommandHandler(Function<IpcRequest, Object> handler) {
        this.commandHandler = handler;
    }

    /**
     * Sets the error handler for WebView error reports.
     */
    public void setErrorHandler(ErrorHandler errorHandler) {
        this.errorHandler = errorHandler;
    }

    /**
     * Returns a snapshot of the most recent IPC command names (up to 10).
     */
    public List<String> getRecentCommands() {
        int idx = ringIndex.get();
        List<String> result = new ArrayList<>(MAX_RECENT_COMMANDS);
        for (int i = 0; i < MAX_RECENT_COMMANDS; i++) {
            String cmd = recentRing[(idx + i) % MAX_RECENT_COMMANDS];
            if (cmd != null) {
                result.add(cmd);
            }
        }
        return result;
    }

    /**
     * Initializes the IPC bridge by injecting the bridge JS and binding the native function.
     */
    public void initialize() {
        // Inject the bridge JavaScript
        window.init(BRIDGE_JS);

        // Inject the drag-drop handler JavaScript
        window.init(DRAGDROP_JS);

        // Inject the error capture JavaScript
        window.init(ERRORS_JS);

        // Bind the native invoke function
        window.bind("__krema_invoke", (seq, req) -> {
            handleInvoke(seq, req);
        });

        // Bind the error reporting function called by krema-errors.js
        window.bind("__krema_report_error", (seq, argsJson) -> {
            handleErrorReport(argsJson);
        });
    }

    /**
     * Handles an incoming invoke request from JavaScript.
     *
     * Commands that return a {@link CompletableFuture} are handled asynchronously:
     * the bind callback returns immediately (freeing the main Cocoa run loop) and
     * {@code webview_return} is called later from the future's completion thread.
     * This prevents deadlocks with macOS system services (DNS, proxy, TLS) that
     * deliver responses via the main dispatch queue.
     *
     * Commands that return a plain value run synchronously on the main thread.
     * Native Cocoa dialogs (NSOpenPanel, NSAlert, etc.) use runModal which runs
     * a nested event loop, so they don't block the UI.
     */
    private void handleInvoke(String seq, String argsJson) {
        try {
            // Parse the arguments array
            JsonNode args = MAPPER.readTree(argsJson);

            if (!args.isArray() || args.isEmpty()) {
                returnError(seq, "Invalid invoke format: expected [request], got: " + argsJson);
                return;
            }

            // First (and only) element is the stringified request object
            JsonNode requestNode = args.get(0);
            JsonNode request;
            if (requestNode.isTextual()) {
                // It's a string, parse it as JSON
                request = MAPPER.readTree(requestNode.asText());
            } else if (requestNode.isObject()) {
                // Already an object
                request = requestNode;
            } else {
                returnError(seq, "Invalid request format: expected string or object");
                return;
            }

            // Extract command and args with null safety
            JsonNode cmdNode = request.get("cmd");
            if (cmdNode == null || cmdNode.isNull()) {
                returnError(seq, "Missing 'cmd' field in request: " + request);
                return;
            }
            String command = cmdNode.asText();
            JsonNode cmdArgs = request.get("args");

            // Track command in lock-free ring buffer
            int slot = ringIndex.getAndUpdate(i -> (i + 1) % MAX_RECENT_COMMANDS);
            recentRing[slot] = command;

            // Create IPC request
            IpcRequest ipcRequest = new IpcRequest(command, cmdArgs);

            // Process the command
            if (commandHandler == null) {
                returnError(seq, "No command handler registered");
                return;
            }

            Object result = commandHandler.apply(ipcRequest);

            // If the command returned a CompletableFuture, handle it asynchronously.
            // The result is delivered via eval calling the webview library's internal
            // onReply function, because webview_return does not dispatch correctly
            // when called from non-main threads on macOS.
            if (result instanceof CompletableFuture<?> future) {
                // Async: deliver result via eval when the future completes.
                // webview_return doesn't work from non-main threads on macOS.
                future.thenAccept(r -> {
                          try {
                              String json = MAPPER.writeValueAsString(r);
                              evalReply(seq, 0, json);
                          } catch (Throwable t) {
                              evalReply(seq, 1, "{\"message\":\"" + t.getMessage() + "\"}");
                          }
                      })
                      .exceptionally(e -> {
                          Throwable cause = e;
                          while (cause.getCause() != null) {
                              cause = cause.getCause();
                          }
                          evalReply(seq, 1, "{\"message\":\"" + cause.getMessage() + "\"}");
                          return null;
                      });
            } else {
                // Synchronous result — return via webview_return on the main
                // thread (used by dialog, window, menu commands).
                returnSuccess(seq, result);
            }

        } catch (Exception e) {
            returnError(seq, e.getMessage());
        }
    }

    /**
     * Handles an error report from the WebView (called by __krema_report_error binding).
     */
    private void handleErrorReport(String argsJson) {
        if (errorHandler == null) {
            return;
        }
        try {
            JsonNode args = MAPPER.readTree(argsJson);
            JsonNode payload = args.isArray() && !args.isEmpty() ? args.get(0) : args;
            String jsonStr = payload.isTextual() ? payload.asText() : payload.toString();
            JsonNode error = MAPPER.readTree(jsonStr);

            String message = error.has("message") ? error.get("message").asText() : "Unknown error";
            String source = error.has("source") ? error.get("source").asText() : "";
            int lineno = error.has("lineno") ? error.get("lineno").asInt() : 0;
            String stack = error.has("stack") ? error.get("stack").asText() : "";

            errorHandler.handleWebViewError(message, source, lineno, stack);
        } catch (Exception e) {
            System.err.println("[IPC] Failed to parse error report: " + e.getMessage());
        }
    }

    /**
     * Returns a successful result to JavaScript via webview_return.
     */
    private void returnSuccess(String seq, Object result) {
        try {
            String json = MAPPER.writeValueAsString(result);
            window.returnResult(seq, true, json);
        } catch (JsonProcessingException e) {
            returnError(seq, "Failed to serialize result: " + e.getMessage());
        }
    }

    /**
     * Returns an error to JavaScript via webview_return.
     */
    private void returnError(String seq, String message) {
        try {
            ObjectNode error = MAPPER.createObjectNode();
            error.put("message", message);
            window.returnResult(seq, false, MAPPER.writeValueAsString(error));
        } catch (JsonProcessingException e) {
            window.returnResult(seq, false, "{\"message\":\"Failed to serialize error\"}");
        }
    }

    /**
     * Delivers a command result to JavaScript by calling the webview library's
     * internal {@code onReply} function via {@code eval}.
     *
     * <p>The call is wrapped in {@code setTimeout(0)} so that the Promise
     * resolution runs inside a Zone.js-patched macrotask. Without this,
     * frameworks like Angular never detect the state change and the UI doesn't
     * update (the "2 clicks" bug).
     *
     * <p>This method is safe to call from any thread — {@code webview_eval}
     * uses {@code dispatch_async} internally on macOS.
     *
     * @param seq    the sequence ID assigned by the webview library
     * @param status 0 for success, 1 for error
     * @param json   the JSON result string
     */
    private void evalReply(String seq, int status, String json) {
        try {
            // Double-encode: onReply expects a JSON string that it JSON.parse's
            String quotedJson = MAPPER.writeValueAsString(json);
            // setTimeout(0) ensures the callback runs inside a Zone.js macrotask
            String js = new StringBuilder(80 + quotedJson.length())
                .append("setTimeout(function(){window.__webview__.onReply('")
                .append(seq)
                .append("', ")
                .append(status)
                .append(", ")
                .append(quotedJson)
                .append(");},0)")
                .toString();
            window.eval(js);
        } catch (Throwable t) {
            System.err.println("[IPC] evalReply failed for seq=" + seq + ": " + t.getMessage());
        }
    }

    /**
     * Represents an IPC request from JavaScript to Java.
     */
    public static class IpcRequest {
        private final String command;
        private final JsonNode args;

        public IpcRequest(String command, JsonNode args) {
            this.command = command;
            this.args = args;
        }

        public String getCommand() {
            return command;
        }

        public JsonNode getArgs() {
            return args;
        }

        /**
         * Gets an argument by name as a string.
         */
        public String getString(String name) {
            return args != null && args.has(name) ? args.get(name).asText() : null;
        }

        /**
         * Gets an argument by name as an integer.
         */
        public int getInt(String name, int defaultValue) {
            return args != null && args.has(name) ? args.get(name).asInt(defaultValue) : defaultValue;
        }

        /**
         * Gets an argument by name as a boolean.
         */
        public boolean getBoolean(String name, boolean defaultValue) {
            return args != null && args.has(name) ? args.get(name).asBoolean(defaultValue) : defaultValue;
        }

        /**
         * Gets an argument by name as double.
         */
        public double getDouble(String name, double defaultValue) {
            return args != null && args.has(name) ? args.get(name).asDouble(defaultValue) : defaultValue;
        }

        /**
         * Deserializes the args to a specific type.
         */
        public <T> T getArgsAs(Class<T> type) {
            try {
                return MAPPER.treeToValue(args, type);
            } catch (JsonProcessingException e) {
                throw new RuntimeException("Failed to deserialize args to " + type.getName(), e);
            }
        }

        @Override
        public String toString() {
            return "IpcRequest{command='" + command + "', args=" + args + "}";
        }
    }
}
