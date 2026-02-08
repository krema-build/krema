package build.krema.core.api.http;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import build.krema.core.KremaCommand;

/**
 * HTTP Client API for making backend HTTP requests.
 * Bypasses CORS restrictions since requests are made from the Java backend.
 *
 * All command methods return {@link CompletableFuture} so the IPC handler can
 * process them asynchronously, keeping the main Cocoa run loop free to handle
 * network events (DNS, proxy, TLS) that would otherwise deadlock.
 */
public class HttpClient {

    private final java.net.http.HttpClient client;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    public HttpClient() {
        this.client = java.net.http.HttpClient.newBuilder()
            .followRedirects(java.net.http.HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(30))
            .build();
    }

    /**
     * Makes an HTTP GET request.
     */
    @KremaCommand("http:get")
    public CompletableFuture<HttpResult> get(String url, Map<String, Object> options) {
        return request("GET", url, options);
    }

    /**
     * Makes an HTTP POST request.
     */
    @KremaCommand("http:post")
    public CompletableFuture<HttpResult> post(String url, Map<String, Object> options) {
        return request("POST", url, options);
    }

    /**
     * Makes an HTTP PUT request.
     */
    @KremaCommand("http:put")
    public CompletableFuture<HttpResult> put(String url, Map<String, Object> options) {
        return request("PUT", url, options);
    }

    /**
     * Makes an HTTP DELETE request.
     */
    @KremaCommand("http:delete")
    public CompletableFuture<HttpResult> delete(String url, Map<String, Object> options) {
        return request("DELETE", url, options);
    }

    /**
     * Makes an HTTP PATCH request.
     */
    @KremaCommand("http:patch")
    public CompletableFuture<HttpResult> patch(String url, Map<String, Object> options) {
        return request("PATCH", url, options);
    }

    /**
     * Makes an HTTP HEAD request.
     */
    @KremaCommand("http:head")
    public CompletableFuture<HttpResult> head(String url, Map<String, Object> options) {
        return request("HEAD", url, options);
    }

    /**
     * Makes a generic HTTP request with configurable method.
     * Returns a CompletableFuture that executes entirely on a virtual thread —
     * nothing HTTP-related (proxy resolution, DNS, TLS, I/O) touches the main
     * Cocoa thread, avoiding deadlocks with macOS system services that deliver
     * responses via the main dispatch queue.
     */
    @KremaCommand("http:request")
    public CompletableFuture<HttpResult> request(String method, String url, Map<String, Object> options) {
        final Map<String, Object> opts = options != null ? options : Map.of();
        return CompletableFuture.supplyAsync(() -> {
            try {
                return doRequest(method, url, opts);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }, executor);
    }

    /**
     * Performs the actual HTTP request. Runs on a virtual thread, never on the
     * main Cocoa thread.
     */
    private HttpResult doRequest(String method, String url, Map<String, Object> options) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
            .uri(URI.create(url));

        // Set timeout
        if (options.containsKey("timeout")) {
            long timeoutMs = ((Number) options.get("timeout")).longValue();
            builder.timeout(Duration.ofMillis(timeoutMs));
        } else {
            builder.timeout(Duration.ofSeconds(30));
        }

        // Set headers
        if (options.containsKey("headers")) {
            @SuppressWarnings("unchecked")
            Map<String, Object> headers = (Map<String, Object>) options.get("headers");
            for (Map.Entry<String, Object> entry : headers.entrySet()) {
                builder.header(entry.getKey(), String.valueOf(entry.getValue()));
            }
        }

        // Set body and method
        String body = null;
        if (options.containsKey("body")) {
            Object bodyObj = options.get("body");
            if (bodyObj instanceof String) {
                body = (String) bodyObj;
            } else {
                // Assume it's a Map/Object that should be JSON serialized
                body = toJson(bodyObj);
                // Auto-set content-type if not set
                if (!hasHeader(options, "Content-Type")) {
                    builder.header("Content-Type", "application/json");
                }
            }
        }

        // Set method with optional body
        switch (method.toUpperCase()) {
            case "GET" -> builder.GET();
            case "DELETE" -> builder.DELETE();
            case "HEAD" -> builder.method("HEAD", HttpRequest.BodyPublishers.noBody());
            case "POST" -> builder.POST(body != null ?
                HttpRequest.BodyPublishers.ofString(body) :
                HttpRequest.BodyPublishers.noBody());
            case "PUT" -> builder.PUT(body != null ?
                HttpRequest.BodyPublishers.ofString(body) :
                HttpRequest.BodyPublishers.noBody());
            case "PATCH" -> builder.method("PATCH", body != null ?
                HttpRequest.BodyPublishers.ofString(body) :
                HttpRequest.BodyPublishers.noBody());
            default -> builder.method(method.toUpperCase(), body != null ?
                HttpRequest.BodyPublishers.ofString(body) :
                HttpRequest.BodyPublishers.noBody());
        }

        HttpRequest httpRequest = builder.build();
        HttpResponse<String> response = client.send(httpRequest, HttpResponse.BodyHandlers.ofString());

        // Convert response headers to simple map
        Map<String, String> responseHeaders = new HashMap<>();
        for (Map.Entry<String, List<String>> entry : response.headers().map().entrySet()) {
            if (entry.getKey() != null && !entry.getValue().isEmpty()) {
                responseHeaders.put(entry.getKey(), String.join(", ", entry.getValue()));
            }
        }

        return new HttpResult(
            response.statusCode(),
            response.body(),
            responseHeaders,
            response.statusCode() >= 200 && response.statusCode() < 300
        );
    }

    /**
     * Fetches a URL and returns the response as text.
     * Convenience method for simple GET requests.
     */
    @KremaCommand("http:fetch")
    public CompletableFuture<String> fetch(String url) {
        return get(url, null).thenApply(result -> {
            if (!result.ok()) {
                throw new RuntimeException("HTTP " + result.status() + ": " + result.body());
            }
            return result.body();
        });
    }

    /**
     * Fetches a URL and parses the response as JSON.
     * Returns a Map for objects or List for arrays.
     */
    @KremaCommand("http:fetchJson")
    public CompletableFuture<Object> fetchJson(String url) {
        return get(url, Map.of("headers", Map.of("Accept", "application/json"))).thenApply(result -> {
            if (!result.ok()) {
                throw new RuntimeException("HTTP " + result.status() + ": " + result.body());
            }
            return parseJson(result.body());
        });
    }

    private boolean hasHeader(Map<String, Object> options, String headerName) {
        if (!options.containsKey("headers")) {
            return false;
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> headers = (Map<String, Object>) options.get("headers");
        return headers.keySet().stream()
            .anyMatch(k -> k.equalsIgnoreCase(headerName));
    }

    private String toJson(Object obj) {
        // Simple JSON serialization for Maps
        if (obj instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) obj;
            StringBuilder sb = new StringBuilder("{");
            boolean first = true;
            for (Map.Entry<String, Object> entry : map.entrySet()) {
                if (!first) sb.append(",");
                first = false;
                sb.append("\"").append(escapeJson(entry.getKey())).append("\":");
                sb.append(valueToJson(entry.getValue()));
            }
            sb.append("}");
            return sb.toString();
        } else if (obj instanceof List) {
            @SuppressWarnings("unchecked")
            List<Object> list = (List<Object>) obj;
            StringBuilder sb = new StringBuilder("[");
            boolean first = true;
            for (Object item : list) {
                if (!first) sb.append(",");
                first = false;
                sb.append(valueToJson(item));
            }
            sb.append("]");
            return sb.toString();
        }
        return String.valueOf(obj);
    }

    private String valueToJson(Object value) {
        if (value == null) {
            return "null";
        } else if (value instanceof String) {
            return "\"" + escapeJson((String) value) + "\"";
        } else if (value instanceof Number || value instanceof Boolean) {
            return String.valueOf(value);
        } else if (value instanceof Map || value instanceof List) {
            return toJson(value);
        }
        return "\"" + escapeJson(String.valueOf(value)) + "\"";
    }

    private String escapeJson(String s) {
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private Object parseJson(String json) {
        json = json.trim();
        if (json.startsWith("{")) {
            return parseJsonObject(json);
        } else if (json.startsWith("[")) {
            return parseJsonArray(json);
        } else if (json.startsWith("\"")) {
            return json.substring(1, json.length() - 1);
        } else if (json.equals("null")) {
            return null;
        } else if (json.equals("true")) {
            return true;
        } else if (json.equals("false")) {
            return false;
        } else {
            try {
                if (json.contains(".")) {
                    return Double.parseDouble(json);
                }
                return Long.parseLong(json);
            } catch (NumberFormatException e) {
                return json;
            }
        }
    }

    private Map<String, Object> parseJsonObject(String json) {
        // Basic JSON object parser - for complex JSON, use a proper library
        // This handles simple cases that are common in HTTP responses
        Map<String, Object> result = new HashMap<>();
        // Strip braces
        json = json.trim();
        if (json.equals("{}")) return result;
        json = json.substring(1, json.length() - 1).trim();

        int depth = 0;
        int start = 0;
        String currentKey = null;
        boolean inString = false;
        boolean escaped = false;

        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);

            if (escaped) {
                escaped = false;
                continue;
            }

            if (c == '\\') {
                escaped = true;
                continue;
            }

            if (c == '"') {
                inString = !inString;
                continue;
            }

            if (inString) continue;

            if (c == '{' || c == '[') depth++;
            if (c == '}' || c == ']') depth--;

            if (depth == 0 && c == ':' && currentKey == null) {
                currentKey = json.substring(start, i).trim();
                if (currentKey.startsWith("\"")) {
                    currentKey = currentKey.substring(1, currentKey.length() - 1);
                }
                start = i + 1;
            }

            if (depth == 0 && (c == ',' || i == json.length() - 1)) {
                String value = json.substring(start, c == ',' ? i : i + 1).trim();
                if (currentKey != null) {
                    result.put(currentKey, parseJson(value));
                }
                currentKey = null;
                start = i + 1;
            }
        }

        return result;
    }

    private List<Object> parseJsonArray(String json) {
        List<Object> result = new java.util.ArrayList<>();
        json = json.trim();
        if (json.equals("[]")) return result;
        json = json.substring(1, json.length() - 1).trim();

        int depth = 0;
        int start = 0;
        boolean inString = false;
        boolean escaped = false;

        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);

            if (escaped) {
                escaped = false;
                continue;
            }

            if (c == '\\') {
                escaped = true;
                continue;
            }

            if (c == '"') {
                inString = !inString;
                continue;
            }

            if (inString) continue;

            if (c == '{' || c == '[') depth++;
            if (c == '}' || c == ']') depth--;

            if (depth == 0 && (c == ',' || i == json.length() - 1)) {
                String value = json.substring(start, c == ',' ? i : i + 1).trim();
                result.add(parseJson(value));
                start = i + 1;
            }
        }

        return result;
    }

    /**
     * HTTP response result.
     */
    public record HttpResult(
        int status,
        String body,
        Map<String, String> headers,
        boolean ok
    ) {}
}
