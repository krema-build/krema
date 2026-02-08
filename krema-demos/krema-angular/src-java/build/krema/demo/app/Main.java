package build.krema.demo.app;

import java.awt.Color;
import java.util.concurrent.atomic.AtomicReference;

import build.krema.core.Krema;
import build.krema.core.event.EventEmitter;
import build.krema.core.splash.SplashScreenOptions;

/**
 * Main entry point for the Krema Angular desktop application.
 */
public class Main {

    public static void main(String[] args) {
        AtomicReference<EventEmitter> emitterRef = new AtomicReference<>();
        Commands commands = new Commands(emitterRef);

        // Check if running in dev mode (looking for --dev flag or dev server URL)
        boolean devMode = isDevMode(args);
        String devUrl = "http://localhost:4200";

        Krema app = Krema.app()
            .title("Krema Angular")
            .version("1.0.0")
            .size(1024, 768)
            .minSize(800, 600)
            .debug(devMode)
            .events(emitterRef::set)
            .commands(commands);

        // Configure splash screen for production
        if (!devMode) {
            app.splash(SplashScreenOptions.builder()
                .appName("Krema Angular")
                .version("1.0.0")
                .backgroundColor(new Color(26, 26, 46))
                .progressColor(new Color(102, 126, 234))
                .showProgress(true)
                .showStatus(true)
                .fadeOut(true)
                .build());
        }

        // Use dev server in development, bundled assets in production
        if (devMode) {
            System.out.println("[Main] Running in development mode");
            System.out.println("[Main] Connecting to: " + devUrl);
            app.devUrl(devUrl);
        } else {
            System.out.println("[Main] Running in production mode");
            app.prodAssets("/web");
        }

        app.run();
    }

    private static boolean isDevMode(String[] args) {
        for (String arg : args) {
            if ("--dev".equals(arg) || "-d".equals(arg)) {
                return true;
            }
        }
        // Also check environment variable
        String env = System.getenv("KREMA_DEV");
        return "true".equalsIgnoreCase(env) || "1".equals(env);
    }
}
