package build.krema.core.dev;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Set;

import build.krema.core.util.Logger;
import build.krema.core.webview.WebViewEngine;

/**
 * Development server with hot reload support.
 * Watches for file changes and triggers appropriate reload actions.
 */
public class DevServer implements AutoCloseable {

    private static final Logger LOG = new Logger("DevServer");

    private final WebViewEngine engine;
    private final ErrorOverlay errorOverlay;
    private FileWatcher fileWatcher;
    private boolean running = false;

    public DevServer(WebViewEngine engine) {
        this.engine = engine;
        this.errorOverlay = new ErrorOverlay(engine);
    }

    public void watchJava(Path sourceDir) throws IOException {
        if (fileWatcher == null) {
            fileWatcher = new FileWatcher(this::handleFileChange, Set.of("java"), 300);
        }
        fileWatcher.watch(sourceDir);
        LOG.info("Watching Java sources: %s", sourceDir);
    }

    public void watchResources(Path resourceDir) throws IOException {
        if (fileWatcher == null) {
            fileWatcher = new FileWatcher(this::handleFileChange, Set.of("html", "css", "js", "json"), 100);
        }
        fileWatcher.watch(resourceDir);
        LOG.info("Watching resources: %s", resourceDir);
    }

    public void start() {
        if (running) {
            return;
        }
        running = true;

        if (fileWatcher != null) {
            fileWatcher.start();
        }

        // Inject hot reload client script
        injectHotReloadClient();

        LOG.info("Development server started");
    }

    private void injectHotReloadClient() {
        String js = """
            (function() {
                window.__krema_reload = function() {
                    console.log('[Krema HMR] Reloading...');
                    window.location.reload();
                };

                window.__krema_hotUpdate = function(type, data) {
                    console.log('[Krema HMR] Hot update:', type, data);
                    if (type === 'css') {
                        // Hot replace CSS
                        const links = document.querySelectorAll('link[rel="stylesheet"]');
                        links.forEach(link => {
                            const href = link.getAttribute('href');
                            if (href) {
                                link.setAttribute('href', href.split('?')[0] + '?t=' + Date.now());
                            }
                        });
                    } else {
                        // Full reload for other types
                        window.__krema_reload();
                    }
                };

                console.log('[Krema HMR] Hot reload enabled');
            })();
            """;
        engine.init(js);
    }

    private void handleFileChange(FileWatcher.FileChangeEvent event) {
        LOG.debug("File changed: %s (%s)", event.path(), event.type());

        if (event.isJavaFile()) {
            handleJavaChange(event);
        } else if (event.isResourceFile()) {
            handleResourceChange(event);
        }
    }

    private void handleJavaChange(FileWatcher.FileChangeEvent event) {
        LOG.info("Java file changed: %s", event.path().getFileName());

        // For Java changes, we need to recompile
        // In a full implementation, this would trigger incremental compilation
        // For now, we'll just show a notification

        String js = """
            console.log('[Krema HMR] Java source changed. Restart required for: %s');
            """.formatted(event.path().getFileName());
        engine.eval(js);
    }

    private void handleResourceChange(FileWatcher.FileChangeEvent event) {
        String fileName = event.path().getFileName().toString();
        LOG.info("Resource changed: %s", fileName);

        if (fileName.endsWith(".css")) {
            // Hot replace CSS without full reload
            engine.eval("window.__krema_hotUpdate && window.__krema_hotUpdate('css', {})");
        } else {
            // Full reload for HTML/JS changes
            engine.eval("window.__krema_reload && window.__krema_reload()");
        }
    }

    public void showError(String title, String message, Throwable throwable) {
        errorOverlay.showError(title, message, throwable);
    }

    public void hideError() {
        errorOverlay.hide();
    }

    public void triggerReload() {
        engine.eval("window.__krema_reload && window.__krema_reload()");
    }

    @Override
    public void close() {
        running = false;
        if (fileWatcher != null) {
            fileWatcher.close();
        }
        LOG.info("Development server stopped");
    }
}
