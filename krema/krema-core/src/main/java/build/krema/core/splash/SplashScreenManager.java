package build.krema.core.splash;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import build.krema.core.util.Logger;

/**
 * Manages splash screen lifecycle during application startup.
 * Provides a fluent API for showing splash, tracking progress, and hiding.
 */
public class SplashScreenManager {

    private static final Logger LOG = new Logger("SplashScreenManager");

    private final AtomicReference<SplashScreen> splashRef = new AtomicReference<>();
    private final SplashScreenOptions options;

    public SplashScreenManager(SplashScreenOptions options) {
        this.options = options;
    }

    /**
     * Creates a manager with default options.
     */
    public static SplashScreenManager withDefaults() {
        return new SplashScreenManager(SplashScreenOptions.builder().build());
    }

    /**
     * Creates a manager with the specified app name and version.
     */
    public static SplashScreenManager forApp(String appName, String version) {
        return new SplashScreenManager(
            SplashScreenOptions.builder()
                .appName(appName)
                .version(version)
                .build()
        );
    }

    /**
     * Shows the splash screen.
     */
    public SplashScreenManager show() {
        SplashScreen splash = new SplashScreen(options);
        splashRef.set(splash);
        splash.show();
        return this;
    }

    /**
     * Updates progress (0-100).
     */
    public SplashScreenManager progress(int value) {
        SplashScreen splash = splashRef.get();
        if (splash != null) {
            splash.setProgress(value);
        }
        return this;
    }

    /**
     * Updates status text.
     */
    public SplashScreenManager status(String text) {
        SplashScreen splash = splashRef.get();
        if (splash != null) {
            splash.setStatus(text);
        }
        return this;
    }

    /**
     * Updates both progress and status.
     */
    public SplashScreenManager update(int progress, String status) {
        return progress(progress).status(status);
    }

    /**
     * Hides and disposes the splash screen.
     */
    public void hide() {
        SplashScreen splash = splashRef.getAndSet(null);
        if (splash != null) {
            splash.hide();
        }
    }

    /**
     * Executes a task while showing progress on the splash screen.
     */
    public <T> T runWithProgress(StartupTask<T> task) {
        show();
        try {
            return task.run(this::update);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Startup task failed", e);
        } finally {
            hide();
        }
    }

    /**
     * Executes startup tasks asynchronously with progress tracking.
     */
    public CompletableFuture<Void> runAsync(StartupTask<Void> task) {
        return CompletableFuture.supplyAsync(() -> runWithProgress(task));
    }

    /**
     * Closes JDK splash screen if present and shows custom splash.
     */
    public SplashScreenManager replaceJdkSplash() {
        SplashScreen.closeJdkSplashScreen();
        return show();
    }

    /**
     * A startup task that can report progress.
     */
    @FunctionalInterface
    public interface StartupTask<T> {
        T run(ProgressReporter reporter) throws Exception;
    }

    /**
     * Reports progress during startup.
     */
    @FunctionalInterface
    public interface ProgressReporter {
        void report(int progress, String status);
    }
}
