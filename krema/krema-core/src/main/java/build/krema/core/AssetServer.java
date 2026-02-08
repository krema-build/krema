package build.krema.core;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.Executors;

/**
 * Lightweight HTTP server for serving production assets.
 * Uses virtual threads for efficient concurrent handling.
 */
public class AssetServer implements AutoCloseable {

    private static final Map<String, String> MIME_TYPES = Map.ofEntries(
        Map.entry(".html", "text/html; charset=utf-8"),
        Map.entry(".htm", "text/html; charset=utf-8"),
        Map.entry(".css", "text/css; charset=utf-8"),
        Map.entry(".js", "application/javascript; charset=utf-8"),
        Map.entry(".mjs", "application/javascript; charset=utf-8"),
        Map.entry(".json", "application/json; charset=utf-8"),
        Map.entry(".png", "image/png"),
        Map.entry(".jpg", "image/jpeg"),
        Map.entry(".jpeg", "image/jpeg"),
        Map.entry(".gif", "image/gif"),
        Map.entry(".svg", "image/svg+xml"),
        Map.entry(".ico", "image/x-icon"),
        Map.entry(".woff", "font/woff"),
        Map.entry(".woff2", "font/woff2"),
        Map.entry(".ttf", "font/ttf"),
        Map.entry(".eot", "application/vnd.ms-fontobject"),
        Map.entry(".webp", "image/webp"),
        Map.entry(".mp3", "audio/mpeg"),
        Map.entry(".mp4", "video/mp4"),
        Map.entry(".webm", "video/webm"),
        Map.entry(".wasm", "application/wasm"),
        Map.entry(".txt", "text/plain; charset=utf-8"),
        Map.entry(".xml", "application/xml; charset=utf-8")
    );

    private final HttpServer server;
    private final String basePath;
    private final int port;

    /**
     * Creates an asset server for the given classpath resource path.
     *
     * @param basePath The base path for resources (e.g., "/assets")
     * @throws IOException if the server cannot be started
     */
    public AssetServer(String basePath) throws IOException {
        this.basePath = basePath.endsWith("/") ? basePath : basePath + "/";

        // Bind to any available port
        this.server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        this.port = server.getAddress().getPort();

        // Use virtual threads for handling requests
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());

        // Handle all requests
        server.createContext("/", this::handleRequest);
    }

    /**
     * Starts the server.
     */
    public void start() {
        server.start();
        System.out.println("[Krema] Asset server started at " + getUrl());
    }

    /**
     * Returns the base URL of the server.
     */
    public String getUrl() {
        return "http://127.0.0.1:" + port;
    }

    /**
     * Returns the port the server is listening on.
     */
    public int getPort() {
        return port;
    }

    private void handleRequest(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();

        // Default to index.html for root
        if (path.equals("/") || path.isEmpty()) {
            path = "/index.html";
        }

        // Construct resource path
        String resourcePath = basePath + path.substring(1); // Remove leading /

        // Try to load the resource
        try (InputStream is = getClass().getResourceAsStream(resourcePath)) {
            if (is == null) {
                // Try with index.html for SPA routing
                String spaPath = basePath + "index.html";
                try (InputStream spaIs = getClass().getResourceAsStream(spaPath)) {
                    if (spaIs != null && !hasFileExtension(path)) {
                        // Serve index.html for SPA routes
                        serveResource(exchange, spaIs, "text/html; charset=utf-8");
                        return;
                    }
                }

                // 404 Not Found
                sendError(exchange, 404, "Not Found: " + path);
                return;
            }

            String contentType = getContentType(path);
            serveResource(exchange, is, contentType);
        }
    }

    private void serveResource(HttpExchange exchange, InputStream is, String contentType) throws IOException {
        byte[] content = is.readAllBytes();

        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.getResponseHeaders().set("Cache-Control", "no-cache");
        exchange.sendResponseHeaders(200, content.length);

        try (OutputStream os = exchange.getResponseBody()) {
            os.write(content);
        }
    }

    private void sendError(HttpExchange exchange, int code, String message) throws IOException {
        byte[] content = message.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
        exchange.sendResponseHeaders(code, content.length);

        try (OutputStream os = exchange.getResponseBody()) {
            os.write(content);
        }
    }

    private String getContentType(String path) {
        int dotIndex = path.lastIndexOf('.');
        if (dotIndex >= 0) {
            String ext = path.substring(dotIndex).toLowerCase();
            return MIME_TYPES.getOrDefault(ext, "application/octet-stream");
        }
        return "application/octet-stream";
    }

    private boolean hasFileExtension(String path) {
        int lastSlash = path.lastIndexOf('/');
        int lastDot = path.lastIndexOf('.');
        return lastDot > lastSlash;
    }

    @Override
    public void close() {
        server.stop(0);
        System.out.println("[Krema] Asset server stopped");
    }
}
