package build.krema.demo.app;

import java.awt.Color;
import java.util.concurrent.atomic.AtomicReference;

import build.krema.core.Krema;
import build.krema.core.event.EventEmitter;
import build.krema.core.splash.SplashScreenOptions;

/**
 * Main entry point for the Krema React desktop application.
 */
public class Main {

    public static void main(String[] args) {
        AtomicReference<EventEmitter> emitterRef = new AtomicReference<>();
        Commands commands = new Commands(emitterRef);

        boolean devMode = isDevMode(args);
        String devUrl = "http://localhost:5174";

        Krema app = Krema.app()
            .title("Krema React")
            .version("1.0.0")
            .size(1024, 768)
            .minSize(800, 600)
            .debug(devMode)
            .events(emitterRef::set)
            .commands(commands);

        if (!devMode) {
            app.splash(SplashScreenOptions.builder()
                .appName("Krema React")
                .version("1.0.0")
                .backgroundColor(new Color(26, 26, 46))
                .progressColor(new Color(102, 126, 234))
                .showProgress(true)
                .showStatus(true)
                .fadeOut(true)
                .build());
        }

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
        String env = System.getenv("KREMA_DEV");
        return "true".equalsIgnoreCase(env) || "1".equals(env);
    }
}
