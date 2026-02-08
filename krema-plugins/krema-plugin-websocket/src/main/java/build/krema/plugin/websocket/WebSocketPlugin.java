package build.krema.plugin.websocket;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;

import build.krema.core.KremaCommand;
import build.krema.core.event.EventEmitter;
import build.krema.core.plugin.KremaPlugin;
import build.krema.core.plugin.PluginContext;

/**
 * Built-in WebSocket plugin.
 * Manages WebSocket connections from the backend, bypassing webview restrictions.
 */
public class WebSocketPlugin implements KremaPlugin {

    private EventEmitter emitter;
    private final ConcurrentHashMap<String, WebSocket> connections = new ConcurrentHashMap<>();

    @Override
    public String getId() {
        return "krema.websocket";
    }

    @Override
    public String getName() {
        return "WebSocket";
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    public String getDescription() {
        return "WebSocket connections from the backend, bypassing webview restrictions";
    }

    @Override
    public void initialize(PluginContext context) {
        this.emitter = context.getEventEmitter();
    }

    @Override
    public void shutdown() {
        connections.forEach((name, ws) -> ws.sendClose(WebSocket.NORMAL_CLOSURE, "shutdown"));
        connections.clear();
    }

    @Override
    public List<Object> getCommandHandlers() {
        return List.of(new WebSocketCommands(this));
    }

    @Override
    public List<String> getRequiredPermissions() {
        return List.of("websocket:connect");
    }

    public record ConnectRequest(String name, String url, Map<String, String> headers) {}
    public record SendRequest(String name, String message) {}
    public record DisconnectRequest(String name) {}

    public static class WebSocketCommands {

        private final WebSocketPlugin plugin;

        WebSocketCommands(WebSocketPlugin plugin) {
            this.plugin = plugin;
        }

        @KremaCommand("websocket:connect")
        public boolean connect(ConnectRequest request) {
            if (plugin.connections.containsKey(request.name())) {
                throw new IllegalStateException("Connection '" + request.name() + "' already exists");
            }

            HttpClient client = HttpClient.newHttpClient();
            WebSocket.Builder builder = client.newWebSocketBuilder();

            if (request.headers() != null) {
                request.headers().forEach(builder::header);
            }

            String name = request.name();
            EventEmitter emitter = plugin.emitter;

            builder.buildAsync(URI.create(request.url()), new WebSocket.Listener() {

                private final StringBuilder buffer = new StringBuilder();

                @Override
                public void onOpen(WebSocket webSocket) {
                    plugin.connections.put(name, webSocket);
                    emitter.emit("websocket:connected", Map.of("name", name));
                    webSocket.request(1);
                }

                @Override
                public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
                    buffer.append(data);
                    if (last) {
                        String message = buffer.toString();
                        buffer.setLength(0);
                        emitter.emit("websocket:message", Map.of("name", name, "data", message));
                    }
                    webSocket.request(1);
                    return null;
                }

                @Override
                public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
                    plugin.connections.remove(name);
                    emitter.emit("websocket:disconnected", Map.of(
                        "name", name, "code", statusCode, "reason", reason != null ? reason : ""));
                    return null;
                }

                @Override
                public void onError(WebSocket webSocket, Throwable error) {
                    plugin.connections.remove(name);
                    emitter.emit("websocket:error", Map.of(
                        "name", name, "error", error.getMessage() != null ? error.getMessage() : "Unknown error"));
                }
            });

            return true;
        }

        @KremaCommand("websocket:send")
        public boolean send(SendRequest request) {
            WebSocket ws = plugin.connections.get(request.name());
            if (ws == null) {
                throw new IllegalStateException("No connection named '" + request.name() + "'");
            }
            ws.sendText(request.message(), true);
            return true;
        }

        @KremaCommand("websocket:disconnect")
        public boolean disconnect(DisconnectRequest request) {
            WebSocket ws = plugin.connections.remove(request.name());
            if (ws == null) {
                return false;
            }
            ws.sendClose(WebSocket.NORMAL_CLOSURE, "disconnect");
            return true;
        }
    }
}
