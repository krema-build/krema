package build.krema.core;

import org.junit.jupiter.api.*;

import build.krema.core.AssetServer;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("AssetServer")
class AssetServerTest {

    private AssetServer server;
    private HttpClient client;

    @BeforeEach
    void setUp() throws Exception {
        server = new AssetServer("/test-assets");
        server.start();
        client = HttpClient.newHttpClient();
    }

    @AfterEach
    void tearDown() {
        server.close();
    }

    private HttpResponse<String> get(String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(server.getUrl() + path))
                .GET()
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<byte[]> getBytes(String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(server.getUrl() + path))
                .GET()
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofByteArray());
    }

    @Nested
    @DisplayName("startup and shutdown")
    class StartupShutdown {

        @Test
        @DisplayName("getUrl returns http://127.0.0.1:PORT format")
        void urlFormat() {
            String url = server.getUrl();
            assertTrue(url.startsWith("http://127.0.0.1:"));
        }

        @Test
        @DisplayName("getPort returns positive port number")
        void portPositive() {
            assertTrue(server.getPort() > 0);
        }

        @Test
        @DisplayName("close stops the server")
        void closeStopsServer() throws Exception {
            server.close();
            // After close, connection should be refused
            assertThrows(Exception.class, () -> get("/"));
        }
    }

    @Nested
    @DisplayName("static resources")
    class StaticResources {

        @Test
        @DisplayName("root serves index.html")
        void rootServesIndex() throws Exception {
            HttpResponse<String> resp = get("/");
            assertEquals(200, resp.statusCode());
            assertTrue(resp.body().contains("Hello"));
        }

        @Test
        @DisplayName("explicit /index.html serves index.html")
        void explicitIndex() throws Exception {
            HttpResponse<String> resp = get("/index.html");
            assertEquals(200, resp.statusCode());
            assertTrue(resp.body().contains("Hello"));
        }

        @Test
        @DisplayName("/style.css returns text/css content type")
        void cssContentType() throws Exception {
            HttpResponse<String> resp = get("/style.css");
            assertEquals(200, resp.statusCode());
            assertTrue(resp.headers().firstValue("Content-Type").orElse("").contains("text/css"));
            assertTrue(resp.body().contains("color: red"));
        }

        @Test
        @DisplayName("/script.js returns application/javascript content type")
        void jsContentType() throws Exception {
            HttpResponse<String> resp = get("/script.js");
            assertEquals(200, resp.statusCode());
            assertTrue(resp.headers().firstValue("Content-Type").orElse("").contains("application/javascript"));
        }

        @Test
        @DisplayName("/image.png returns image/png content type")
        void pngContentType() throws Exception {
            HttpResponse<byte[]> resp = getBytes("/image.png");
            assertEquals(200, resp.statusCode());
            assertTrue(resp.headers().firstValue("Content-Type").orElse("").contains("image/png"));
        }

        @Test
        @DisplayName("/data.json returns application/json content type")
        void jsonContentType() throws Exception {
            HttpResponse<String> resp = get("/data.json");
            assertEquals(200, resp.statusCode());
            assertTrue(resp.headers().firstValue("Content-Type").orElse("").contains("application/json"));
            assertTrue(resp.body().contains("\"key\":\"value\""));
        }
    }

    @Nested
    @DisplayName("SPA fallback")
    class SpaFallback {

        @Test
        @DisplayName("/about (no extension) serves index.html")
        void noExtensionServesIndex() throws Exception {
            HttpResponse<String> resp = get("/about");
            assertEquals(200, resp.statusCode());
            assertTrue(resp.body().contains("Hello"));
        }

        @Test
        @DisplayName("/app/settings serves index.html")
        void nestedPathServesIndex() throws Exception {
            HttpResponse<String> resp = get("/app/settings");
            assertEquals(200, resp.statusCode());
            assertTrue(resp.body().contains("Hello"));
        }
    }

    @Nested
    @DisplayName("404")
    class NotFound {

        @Test
        @DisplayName("missing resource with extension returns 404")
        void missingResourceReturns404() throws Exception {
            HttpResponse<String> resp = get("/nonexistent.css");
            assertEquals(404, resp.statusCode());
            assertTrue(resp.body().contains("Not Found"));
        }
    }

    @Nested
    @DisplayName("headers")
    class Headers {

        @Test
        @DisplayName("Cache-Control: no-cache on successful responses")
        void cacheControl() throws Exception {
            HttpResponse<String> resp = get("/");
            assertEquals("no-cache", resp.headers().firstValue("Cache-Control").orElse(""));
        }
    }

    @Nested
    @DisplayName("unknown MIME type")
    class UnknownMime {

        @Test
        @DisplayName("unknown extension returns application/octet-stream")
        void unknownExtension() throws Exception {
            HttpResponse<String> resp = get("/data.xyz");
            assertEquals(200, resp.statusCode());
            assertTrue(resp.headers().firstValue("Content-Type").orElse("").contains("application/octet-stream"));
        }
    }
}
