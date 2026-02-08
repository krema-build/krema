package build.krema.core.security;

import java.util.*;

/**
 * Content Security Policy configuration for Krema applications.
 * Helps prevent XSS and other injection attacks.
 */
public class ContentSecurityPolicy {

    private final Map<String, Set<String>> directives = new LinkedHashMap<>();

    /**
     * Creates a restrictive default CSP suitable for most applications.
     */
    public static ContentSecurityPolicy restrictive() {
        return new ContentSecurityPolicy()
            .defaultSrc("'self'")
            .scriptSrc("'self'", "'unsafe-inline'")
            .styleSrc("'self'", "'unsafe-inline'")
            .imgSrc("'self'", "data:", "blob:")
            .fontSrc("'self'", "data:")
            .connectSrc("'self'")
            .mediaSrc("'self'")
            .objectSrc("'none'")
            .frameAncestors("'none'")
            .baseUri("'self'")
            .formAction("'self'");
    }

    /**
     * Creates a permissive CSP for development.
     */
    public static ContentSecurityPolicy permissive() {
        return new ContentSecurityPolicy()
            .defaultSrc("*", "'unsafe-inline'", "'unsafe-eval'", "data:", "blob:");
    }

    /**
     * Creates an empty CSP (no restrictions).
     */
    public static ContentSecurityPolicy none() {
        return new ContentSecurityPolicy();
    }

    public ContentSecurityPolicy defaultSrc(String... sources) {
        return directive("default-src", sources);
    }

    public ContentSecurityPolicy scriptSrc(String... sources) {
        return directive("script-src", sources);
    }

    public ContentSecurityPolicy styleSrc(String... sources) {
        return directive("style-src", sources);
    }

    public ContentSecurityPolicy imgSrc(String... sources) {
        return directive("img-src", sources);
    }

    public ContentSecurityPolicy fontSrc(String... sources) {
        return directive("font-src", sources);
    }

    public ContentSecurityPolicy connectSrc(String... sources) {
        return directive("connect-src", sources);
    }

    public ContentSecurityPolicy mediaSrc(String... sources) {
        return directive("media-src", sources);
    }

    public ContentSecurityPolicy objectSrc(String... sources) {
        return directive("object-src", sources);
    }

    public ContentSecurityPolicy frameSrc(String... sources) {
        return directive("frame-src", sources);
    }

    public ContentSecurityPolicy frameAncestors(String... sources) {
        return directive("frame-ancestors", sources);
    }

    public ContentSecurityPolicy baseUri(String... sources) {
        return directive("base-uri", sources);
    }

    public ContentSecurityPolicy formAction(String... sources) {
        return directive("form-action", sources);
    }

    public ContentSecurityPolicy workerSrc(String... sources) {
        return directive("worker-src", sources);
    }

    public ContentSecurityPolicy directive(String name, String... sources) {
        directives.computeIfAbsent(name, k -> new LinkedHashSet<>())
            .addAll(Arrays.asList(sources));
        return this;
    }

    public ContentSecurityPolicy addSource(String directive, String source) {
        directives.computeIfAbsent(directive, k -> new LinkedHashSet<>())
            .add(source);
        return this;
    }

    /**
     * Allows connections to a specific URL for development.
     */
    public ContentSecurityPolicy allowDevServer(String url) {
        return addSource("connect-src", url)
            .addSource("script-src", url)
            .addSource("style-src", url);
    }

    /**
     * Allows WebSocket connections.
     */
    public ContentSecurityPolicy allowWebSocket(String wsUrl) {
        return addSource("connect-src", wsUrl);
    }

    /**
     * Generates the CSP header value.
     */
    public String toHeaderValue() {
        if (directives.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Set<String>> entry : directives.entrySet()) {
            if (!sb.isEmpty()) {
                sb.append("; ");
            }
            sb.append(entry.getKey());
            for (String source : entry.getValue()) {
                sb.append(" ").append(source);
            }
        }
        return sb.toString();
    }

    /**
     * Generates a meta tag for embedding CSP in HTML.
     */
    public String toMetaTag() {
        String value = toHeaderValue();
        if (value.isEmpty()) {
            return "";
        }
        return String.format(
            "<meta http-equiv=\"Content-Security-Policy\" content=\"%s\">",
            escapeHtml(value)
        );
    }

    private String escapeHtml(String text) {
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;");
    }

    @Override
    public String toString() {
        return toHeaderValue();
    }
}
