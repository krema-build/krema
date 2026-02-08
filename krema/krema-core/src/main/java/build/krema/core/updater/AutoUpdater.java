package build.krema.core.updater;

import com.fasterxml.jackson.databind.ObjectMapper;

import build.krema.core.platform.Platform;
import build.krema.core.platform.PlatformDetector;
import build.krema.core.util.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.GeneralSecurityException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Auto-updater for Krema applications.
 * Checks for updates from configured endpoints, downloads new versions,
 * verifies Ed25519 signatures, and delegates installation to platform-specific installers.
 */
public class AutoUpdater {

    private static final Logger LOG = new Logger("AutoUpdater");
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final AutoUpdaterConfig config;
    private final String target;
    private final Path downloadDir;
    private final HttpClient httpClient;

    private Consumer<UpdateInfo> onUpdateAvailable;
    private Consumer<Double> onDownloadProgress;
    private Consumer<Path> onUpdateReady;
    private Consumer<Exception> onError;

    private Path lastDownloadedUpdate;

    /**
     * Creates an AutoUpdater with the given configuration.
     */
    public AutoUpdater(AutoUpdaterConfig config) {
        this.config = config;
        this.target = Platform.current().getTarget();
        this.downloadDir = Paths.get(System.getProperty("java.io.tmpdir"), "krema-updates");
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(config.timeout())
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    }

    /**
     * Creates an AutoUpdater with a single update URL and current version.
     *
     * @deprecated Use {@link #AutoUpdater(AutoUpdaterConfig)} instead.
     */
    @Deprecated
    public AutoUpdater(String updateUrl, String currentVersion) {
        this(AutoUpdaterConfig.builder()
            .endpoints(List.of(updateUrl))
            .currentVersion(currentVersion)
            .build());
    }

    public AutoUpdater onUpdateAvailable(Consumer<UpdateInfo> handler) {
        this.onUpdateAvailable = handler;
        return this;
    }

    public AutoUpdater onDownloadProgress(Consumer<Double> handler) {
        this.onDownloadProgress = handler;
        return this;
    }

    public AutoUpdater onUpdateReady(Consumer<Path> handler) {
        this.onUpdateReady = handler;
        return this;
    }

    public AutoUpdater onError(Consumer<Exception> handler) {
        this.onError = handler;
        return this;
    }

    /**
     * Returns the path of the last successfully downloaded update, or null.
     */
    public Path getLastDownloadedUpdate() {
        return lastDownloadedUpdate;
    }

    /**
     * Checks for updates asynchronously by querying configured endpoints.
     * The first endpoint to respond successfully is used.
     * A 204 No Content response means no update is available (returns null).
     */
    public CompletableFuture<UpdateInfo> checkForUpdates() {
        return CompletableFuture.supplyAsync(() -> {
            List<String> endpoints = config.endpoints();
            if (endpoints == null || endpoints.isEmpty()) {
                RuntimeException ex = new RuntimeException(
                    "No update endpoints configured. Add [updater] endpoints to krema.toml.");
                notifyError(ex);
                throw ex;
            }

            Exception lastException = null;
            for (String endpointTemplate : endpoints) {
                try {
                    String url = substituteVariables(endpointTemplate);
                    LOG.info("Checking for updates at: %s", url);

                    HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .timeout(config.timeout())
                        .header("User-Agent", "Krema-Updater/1.0")
                        .GET()
                        .build();

                    HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                    int statusCode = response.statusCode();

                    if (statusCode == 204) {
                        LOG.info("No update available (204 No Content)");
                        return null;
                    }

                    if (statusCode != 200) {
                        throw new IOException("Update check failed: HTTP " + statusCode);
                    }

                    UpdateManifest manifest = MAPPER.readValue(response.body(), UpdateManifest.class);
                    UpdateInfo info = manifest.resolve(target);

                    if (info == null) {
                        LOG.warn("No update entry found for target: %s", target);
                        return null;
                    }

                    if (isNewerVersion(info.getVersion())) {
                        LOG.info("Update available: %s -> %s", config.currentVersion(), info.getVersion());
                        if (onUpdateAvailable != null) {
                            onUpdateAvailable.accept(info);
                        }
                        return info;
                    } else {
                        LOG.info("Already up to date: %s", config.currentVersion());
                        return null;
                    }
                } catch (Exception e) {
                    LOG.warn("Endpoint failed: %s — %s", endpointTemplate, e.getMessage());
                    lastException = e;
                }
            }

            RuntimeException ex = new RuntimeException(
                "All update endpoints failed", lastException);
            notifyError(ex);
            throw ex;
        });
    }

    /**
     * Downloads an update and optionally verifies its signature.
     */
    public CompletableFuture<Path> downloadUpdate(UpdateInfo info) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                LOG.info("Downloading update: %s", info.getDownloadUrl());

                Files.createDirectories(downloadDir);
                String fileName = getFileName(info.getDownloadUrl());
                Path targetPath = downloadDir.resolve(fileName);

                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(info.getDownloadUrl()))
                    .timeout(Duration.ofMinutes(10))
                    .header("User-Agent", "Krema-Updater/1.0")
                    .GET()
                    .build();

                HttpResponse<InputStream> response = httpClient.send(
                    request, HttpResponse.BodyHandlers.ofInputStream());

                if (response.statusCode() != 200) {
                    throw new IOException("Download failed: HTTP " + response.statusCode());
                }

                long totalSize = response.headers().firstValueAsLong("Content-Length").orElse(-1);
                if (totalSize <= 0) {
                    totalSize = info.getSize();
                }

                try (InputStream in = response.body();
                     OutputStream out = Files.newOutputStream(targetPath)) {
                    byte[] buffer = new byte[8192];
                    long downloaded = 0;
                    int bytesRead;

                    while ((bytesRead = in.read(buffer)) != -1) {
                        out.write(buffer, 0, bytesRead);
                        downloaded += bytesRead;

                        if (totalSize > 0 && onDownloadProgress != null) {
                            double progress = (double) downloaded / totalSize;
                            onDownloadProgress.accept(Math.min(progress, 1.0));
                        }
                    }
                }

                LOG.info("Download complete: %s", targetPath);

                // Verify signature
                verifySignature(targetPath, info.getSignature());

                lastDownloadedUpdate = targetPath;

                if (onUpdateReady != null) {
                    onUpdateReady.accept(targetPath);
                }

                return targetPath;
            } catch (Exception e) {
                LOG.error("Download failed", e);
                notifyError(e);
                throw new RuntimeException(e);
            }
        });
    }

    /**
     * Installs an update using the platform-specific installer.
     */
    public void installUpdate(Path updatePath) throws IOException {
        LOG.info("Installing update from: %s", updatePath);

        build.krema.core.updater.install.UpdateInstaller installer =
            build.krema.core.updater.install.UpdateInstallerFactory.get();
        installer.install(updatePath);
    }

    /**
     * Installs the update and restarts the application.
     */
    public void installAndRestart(Path updatePath) throws IOException {
        installUpdate(updatePath);

        build.krema.core.updater.install.UpdateInstaller installer =
            build.krema.core.updater.install.UpdateInstallerFactory.get();
        installer.restart();
    }

    private void verifySignature(Path file, String signature) throws GeneralSecurityException, IOException {
        String publicKey = config.publicKey();

        if (publicKey == null || publicKey.isBlank()) {
            if (signature != null && !signature.isBlank()) {
                LOG.warn("Signature present but no public key configured — skipping verification");
            } else {
                LOG.warn("No public key configured — update signature not verified");
            }
            return;
        }

        if (signature == null || signature.isBlank()) {
            throw new SecurityException("Update signature is missing but public key is configured. " +
                "Refusing unsigned update.");
        }

        boolean valid = UpdateVerifier.verify(file, signature, publicKey);
        if (!valid) {
            throw new SecurityException("Update signature verification failed. " +
                "The update file may have been tampered with.");
        }

        LOG.info("Signature verified successfully");
    }

    String substituteVariables(String template) {
        return template
            .replace("{{target}}", target)
            .replace("{{arch}}", PlatformDetector.getArch())
            .replace("{{current_version}}", config.currentVersion());
    }

    boolean isNewerVersion(String newVersion) {
        try {
            int[] current = parseVersion(config.currentVersion());
            int[] next = parseVersion(newVersion);

            for (int i = 0; i < Math.max(current.length, next.length); i++) {
                int c = i < current.length ? current[i] : 0;
                int n = i < next.length ? next[i] : 0;
                if (n > c) return true;
                if (n < c) return false;
            }
            return false;
        } catch (Exception e) {
            return newVersion.compareTo(config.currentVersion()) > 0;
        }
    }

    int[] parseVersion(String version) {
        String[] parts = version.replaceAll("[^0-9.]", "").split("\\.");
        int[] result = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            try {
                result[i] = Integer.parseInt(parts[i]);
            } catch (NumberFormatException e) {
                result[i] = 0;
            }
        }
        return result;
    }

    String getFileName(String url) {
        int lastSlash = url.lastIndexOf('/');
        if (lastSlash >= 0 && lastSlash < url.length() - 1) {
            String name = url.substring(lastSlash + 1);
            int queryStart = name.indexOf('?');
            if (queryStart > 0) {
                name = name.substring(0, queryStart);
            }
            return name;
        }
        return "update";
    }

    private void notifyError(Exception e) {
        if (onError != null) {
            onError.accept(e);
        }
    }
}
