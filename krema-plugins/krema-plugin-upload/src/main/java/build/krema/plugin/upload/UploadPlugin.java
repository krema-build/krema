package build.krema.plugin.upload;

import java.io.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.Flow;

import build.krema.core.KremaCommand;
import build.krema.core.event.EventEmitter;
import build.krema.core.plugin.KremaPlugin;
import build.krema.core.plugin.PluginContext;

/**
 * Built-in upload plugin.
 * Uploads files via multipart/form-data with progress tracking.
 */
public class UploadPlugin implements KremaPlugin {

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private EventEmitter emitter;

    @Override
    public String getId() {
        return "krema.upload";
    }

    @Override
    public String getName() {
        return "Upload";
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    public String getDescription() {
        return "File uploads with multipart/form-data and progress tracking";
    }

    @Override
    public void initialize(PluginContext context) {
        this.emitter = context.getEventEmitter();
    }

    @Override
    public List<Object> getCommandHandlers() {
        return List.of(new UploadCommands(this));
    }

    @Override
    public List<String> getRequiredPermissions() {
        return List.of("upload:send");
    }

    public record UploadRequest(
        String url,
        List<String> files,
        String method,
        Map<String, String> headers,
        Map<String, String> formFields,
        int timeout,
        String id
    ) {}

    public record UploadResult(String id, int status, String body, Map<String, List<String>> headers) {}

    public static class UploadCommands {

        private final UploadPlugin plugin;

        UploadCommands(UploadPlugin plugin) {
            this.plugin = plugin;
        }

        @KremaCommand("upload:upload")
        public UploadResult upload(UploadRequest request) throws IOException, InterruptedException {
            String uploadId = request.id() != null ? request.id() : UUID.randomUUID().toString();
            String boundary = "----KremaUpload" + UUID.randomUUID().toString().replace("-", "");
            String method = request.method() != null ? request.method().toUpperCase() : "POST";
            int timeout = request.timeout() > 0 ? request.timeout() : 30;

            try {
                byte[] body = buildMultipartBody(boundary, request);
                long totalBytes = body.length;

                HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(request.url()))
                    .timeout(Duration.ofSeconds(timeout))
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary);

                if (request.headers() != null) {
                    request.headers().forEach(reqBuilder::header);
                }

                HttpRequest.BodyPublisher publisher = trackingPublisher(body, uploadId, totalBytes);
                reqBuilder.method(method, publisher);

                HttpResponse<String> response = plugin.httpClient.send(
                    reqBuilder.build(), HttpResponse.BodyHandlers.ofString());

                plugin.emitter.emit("upload:completed", Map.of("id", uploadId, "status", response.statusCode()));

                return new UploadResult(uploadId, response.statusCode(), response.body(), response.headers().map());

            } catch (Exception e) {
                plugin.emitter.emit("upload:error", Map.of("id", uploadId, "error", e.getMessage()));
                throw e;
            }
        }

        private byte[] buildMultipartBody(String boundary, UploadRequest request) throws IOException {
            ByteArrayOutputStream out = new ByteArrayOutputStream();

            // Form fields
            if (request.formFields() != null) {
                for (var entry : request.formFields().entrySet()) {
                    out.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
                    out.write(("Content-Disposition: form-data; name=\"" + entry.getKey() + "\"\r\n\r\n")
                        .getBytes(StandardCharsets.UTF_8));
                    out.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                    out.write("\r\n".getBytes(StandardCharsets.UTF_8));
                }
            }

            // File parts
            if (request.files() != null) {
                for (String filePath : request.files()) {
                    Path path = Path.of(filePath);
                    String fileName = path.getFileName().toString();
                    String contentType = Files.probeContentType(path);
                    if (contentType == null) {
                        contentType = "application/octet-stream";
                    }

                    out.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
                    out.write(("Content-Disposition: form-data; name=\"file\"; filename=\"" + fileName + "\"\r\n")
                        .getBytes(StandardCharsets.UTF_8));
                    out.write(("Content-Type: " + contentType + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
                    out.write(Files.readAllBytes(path));
                    out.write("\r\n".getBytes(StandardCharsets.UTF_8));
                }
            }

            // Closing boundary
            out.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
            return out.toByteArray();
        }

        private HttpRequest.BodyPublisher trackingPublisher(byte[] body, String uploadId, long totalBytes) {
            return HttpRequest.BodyPublishers.ofInputStream(() -> {
                return new InputStream() {
                    private int pos = 0;
                    private int lastPercentage = -1;

                    @Override
                    public int read() {
                        if (pos >= body.length) return -1;
                        int b = body[pos++] & 0xFF;
                        emitProgress();
                        return b;
                    }

                    @Override
                    public int read(byte[] buf, int off, int len) {
                        if (pos >= body.length) return -1;
                        int available = Math.min(len, body.length - pos);
                        System.arraycopy(body, pos, buf, off, available);
                        pos += available;
                        emitProgress();
                        return available;
                    }

                    private void emitProgress() {
                        int percentage = (int) ((pos * 100L) / totalBytes);
                        if (percentage != lastPercentage) {
                            lastPercentage = percentage;
                            plugin.emitter.emit("upload:progress", Map.of(
                                "id", uploadId,
                                "bytesSent", pos,
                                "totalBytes", totalBytes,
                                "percentage", percentage
                            ));
                        }
                    }
                };
            });
        }
    }
}
