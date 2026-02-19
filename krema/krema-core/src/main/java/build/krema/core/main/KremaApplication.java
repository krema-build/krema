package build.krema.core.main;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import build.krema.core.AssetServer;
import build.krema.core.CommandRegistry;
import build.krema.core.KremaWindow;
import build.krema.core.api.app.AppEnvironment;
import build.krema.core.api.clipboard.Clipboard;
import build.krema.core.api.dialog.FileDialog;
import build.krema.core.api.dock.Dock;
import build.krema.core.api.dragdrop.DragDrop;
import build.krema.core.api.http.HttpClient;
import build.krema.core.api.instance.SingleInstance;
import build.krema.core.api.menu.Menu;
import build.krema.core.api.notification.Notification;
import build.krema.core.api.os.OSInfo;
import build.krema.core.api.path.AppPaths;
import build.krema.core.api.screen.Screen;
import build.krema.core.api.securestorage.SecureStorage;
import build.krema.core.api.shell.Shell;
import build.krema.core.api.shortcut.GlobalShortcut;
import build.krema.core.api.store.Store;
import build.krema.core.api.tray.SystemTray;
import build.krema.core.api.window.Window;
import build.krema.core.error.ErrorHandler;
import build.krema.core.event.EventEmitter;
import build.krema.core.ipc.IpcHandler;
import build.krema.core.platform.Platform;
import build.krema.core.plugin.DefaultPluginContext;
import build.krema.core.plugin.KremaPlugin;
import build.krema.core.plugin.PluginException;
import build.krema.core.plugin.PluginLoader;
import build.krema.core.splash.SplashScreen;
import build.krema.core.util.LogContext;
import build.krema.core.util.Logger;
import build.krema.core.window.WindowEngine;
import build.krema.core.window.WindowEngineFactory;
import build.krema.core.window.WindowManager;
import build.krema.core.window.WindowOptions;
import build.krema.core.window.WindowOptions.TitleBarStyle;

/**
 * Runs a Krema application based on builder configuration.
 * This class contains the application lifecycle logic extracted from Krema.
 */
public final class KremaApplication {

    private final KremaBuilder config;

    KremaApplication(KremaBuilder config) {
        this.config = config;
    }

    /**
     * Runs the application. Blocks until the window is closed.
     */
    public void run() {
        AssetServer assetServer = null;
        SplashScreen splash = null;
        PluginLoader pluginLoader = null;
        ErrorHandler errorHandler = null;

        // Show splash screen if configured
        if (config.splashOptions != null && !isNativeImage()) {
            SplashScreen.closeJdkSplashScreen();
            splash = new SplashScreen(config.splashOptions);
            splash.show();
            splash.setStatus("Initializing...");
            splash.setProgress(10);
        }

        System.out.println("[Krema] Creating window (debug=" + config.debug + ")...");
        System.out.flush();

        WindowOptions options = buildWindowOptions();

        try (KremaWindow window = new KremaWindow(options)) {
            System.out.println("[Krema] Window created");
            System.out.flush();

            if (splash != null) {
                splash.setProgress(30);
                splash.setStatus("Setting up IPC...");
            }

            // Set up command registry
            CommandRegistry registry = new CommandRegistry();

            // Compute app ID and data path
            String appId = config.appIdentifier != null ? config.appIdentifier :
                config.title.toLowerCase().replaceAll("[^a-z0-9]", "");
            AppPaths appPaths = new AppPaths(config.title, appId);
            Path appDataPath = appPaths.getAppDataDir();

            Logger.setLogContext(LogContext.create(
                config.title,
                config.appVersion,
                Platform.current().getTarget()
            ));

            // Auto-detect environment from system env vars if not set via builder
            String envName = config.environmentName;
            Map<String, String> envVars = config.environmentVars;

            if (envName == null) {
                envName = System.getenv("KREMA_ENV");
            }
            if (envVars.isEmpty()) {
                String json = System.getenv("KREMA_ENV_VARS");
                if (json != null && !json.isEmpty()) {
                    envVars = parseEnvVarsJson(json);
                }
            }

            // Register built-in APIs
            Menu menuApi = null;
            GlobalShortcut globalShortcutApi = null;
            SingleInstance singleInstanceApi = null;

            if (config.registerBuiltinApis) {
                menuApi = new Menu();
                Store storeApi = new Store(appDataPath);
                singleInstanceApi = new SingleInstance(appDataPath, appId);
                globalShortcutApi = new GlobalShortcut();
                SecureStorage secureStorageApi = new SecureStorage(appId);

                registry.register(
                    new AppEnvironment(envName, envVars),
                    new FileDialog(),
                    new Notification(),
                    appPaths,
                    new DragDrop(),
                    new Screen(),
                    new Window(),
                    menuApi,
                    new OSInfo(),
                    storeApi,
                    singleInstanceApi,
                    globalShortcutApi,
                    new Dock(),
                    new HttpClient(),
                    new Clipboard(),
                    new SystemTray(),
                    new Shell(),
                    secureStorageApi
                );
            }

            // Register user-provided command objects
            if (!config.commandObjects.isEmpty()) {
                registry.register(config.commandObjects.toArray());
            }

            // Load plugins
            pluginLoader = new PluginLoader();
            if (config.loadBuiltinPlugins) {
                pluginLoader.loadBuiltinPlugins();
            }
            for (KremaPlugin p : config.explicitPlugins) {
                pluginLoader.registerPlugin(p);
            }
            if (config.pluginDirectory != null) {
                try {
                    pluginLoader.loadFromDirectory(config.pluginDirectory);
                } catch (PluginException e) {
                    System.err.println("[Krema] Failed to load plugins from directory: " + e.getMessage());
                }
            }

            // Set up IPC
            System.out.println("[Krema] Setting up IPC...");
            System.out.flush();
            IpcHandler ipc = new IpcHandler(window);
            ipc.setCommandHandler(request -> {
                try {
                    return registry.invoke(request);
                } catch (CommandRegistry.CommandException e) {
                    throw new RuntimeException(e.getMessage(), e);
                }
            });
            ipc.initialize();
            System.out.println("[Krema] IPC initialized");
            System.out.flush();

            if (splash != null) {
                splash.setProgress(50);
                splash.setStatus("Configuring events...");
            }

            // Set up event emitter
            EventEmitter eventEmitter = new EventEmitter(window.getEngine());

            // Set up global error handler
            Path crashReportDir = appDataPath.resolve("crash-reports");
            errorHandler = new ErrorHandler(
                eventEmitter, crashReportDir, config.errorHandler, ipc::getRecentCommands
            );
            errorHandler.install();
            ipc.setErrorHandler(errorHandler);

            // Connect APIs to event emitter
            if (menuApi != null) {
                menuApi.setEventEmitter(eventEmitter);
            }
            if (globalShortcutApi != null) {
                globalShortcutApi.setEventEmitter(eventEmitter);
            }
            if (singleInstanceApi != null) {
                singleInstanceApi.setEventEmitter(eventEmitter);
            }

            if (config.eventSetup != null) {
                config.eventSetup.accept(eventEmitter);
            }

            // Initialize plugins and register their command handlers
            if (pluginLoader.size() > 0) {
                DefaultPluginContext ctx = new DefaultPluginContext(
                    WindowManager.getInstance(), eventEmitter, registry,
                    appDataPath, config.title, config.appVersion, config.updaterConfig
                );
                pluginLoader.initializeAll(ctx);
                List<Object> handlers = pluginLoader.collectCommandHandlers();
                if (!handlers.isEmpty()) {
                    registry.register(handlers.toArray());
                }
                System.out.println("[Krema] Loaded " + pluginLoader.size() + " plugin(s)");
            }

            if (splash != null) {
                splash.setProgress(70);
                splash.setStatus("Loading content...");
            }

            // Allow custom splash setup
            if (splash != null && config.splashSetup != null) {
                config.splashSetup.accept(splash);
            }

            // Determine content source
            if (config.devUrl != null) {
                System.out.println("[Krema] Development mode: " + config.devUrl);
                window.navigate(config.devUrl);
            } else if (config.prodAssets != null) {
                try {
                    assetServer = new AssetServer(config.prodAssets);
                    assetServer.start();
                    window.navigate(assetServer.getUrl());
                } catch (IOException e) {
                    throw new RuntimeException("Failed to start asset server", e);
                }
            } else if (config.html != null) {
                System.out.println("[Krema] Setting HTML content (" + config.html.length() + " chars)...");
                System.out.flush();
                window.html(config.html);
            } else {
                System.out.println("[Krema] Setting default HTML...");
                System.out.flush();
                window.html(getDefaultHtml());
            }

            if (splash != null) {
                splash.setProgress(100);
                splash.setStatus("Ready!");
            }

            System.out.println("[Krema] Starting application: " + config.title);
            System.out.println("[Krema] Registered " + registry.size() + " commands");
            System.out.flush();

            // Hide splash screen before showing main window
            if (splash != null) {
                try {
                    Thread.sleep(200);
                } catch (InterruptedException ignored) {}
                splash.hide();
                splash = null;
            }

            // Apply frameless window options if configured
            applyFramelessOptions(options);

            // Emit app:ready event
            eventEmitter.emit("app:ready");

            // Run the event loop (blocks until window closes)
            System.out.println("[Krema] Running event loop...");
            System.out.flush();
            window.run();

            // Emit lifecycle events
            eventEmitter.emit("app:window-all-closed");
            eventEmitter.emit("app:before-quit");

            System.out.println("[Krema] Application closed");

        } finally {
            if (errorHandler != null) {
                errorHandler.uninstall();
            }
            if (pluginLoader != null) {
                pluginLoader.shutdownAll();
            }
            Logger.disableFileLogging();
            if (assetServer != null) {
                assetServer.close();
            }
            if (splash != null) {
                splash.close();
            }
        }
    }

    private WindowOptions buildWindowOptions() {
        WindowOptions.Builder builder = WindowOptions.builder()
            .title(config.title)
            .size(config.width, config.height)
            .debug(config.debug)
            .titleBarStyle(config.titleBarStyle)
            .titlebarAppearsTransparent(config.titlebarAppearsTransparent);

        if (config.minWidth > 0 && config.minHeight > 0) {
            builder.minSize(config.minWidth, config.minHeight);
        }

        if (config.trafficLightX != null && config.trafficLightY != null) {
            builder.trafficLightPosition(config.trafficLightX, config.trafficLightY);
        }

        return builder.build();
    }

    private void applyFramelessOptions(WindowOptions options) {
        if (options.titleBarStyle() == TitleBarStyle.DEFAULT &&
            !options.titlebarAppearsTransparent() &&
            options.trafficLightX() == null) {
            return;
        }

        try {
            WindowEngine windowEngine = WindowEngineFactory.get();

            if (options.titleBarStyle() != TitleBarStyle.DEFAULT) {
                String style = switch (options.titleBarStyle()) {
                    case HIDDEN -> "hidden";
                    case HIDDEN_INSET -> "hiddenInset";
                    default -> "default";
                };
                windowEngine.setTitleBarStyle(style);
            }

            if (options.titlebarAppearsTransparent()) {
                windowEngine.setTitlebarAppearsTransparent(true);
            }

            if (options.trafficLightX() != null && options.trafficLightY() != null) {
                windowEngine.setTrafficLightPosition(options.trafficLightX(), options.trafficLightY());
            }
        } catch (Exception e) {
            System.err.println("[Krema] Failed to apply frameless options: " + e.getMessage());
        }
    }

    private String getDefaultHtml() {
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>Krema</title>
                <style>
                    * { margin: 0; padding: 0; box-sizing: border-box; }
                    body {
                        font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
                        display: flex;
                        justify-content: center;
                        align-items: center;
                        min-height: 100vh;
                        background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
                        color: white;
                    }
                    .container {
                        text-align: center;
                        padding: 2rem;
                    }
                    h1 { font-size: 3rem; margin-bottom: 1rem; }
                    p { font-size: 1.2rem; opacity: 0.9; }
                    code {
                        background: rgba(255,255,255,0.2);
                        padding: 0.2rem 0.5rem;
                        border-radius: 4px;
                        font-family: 'SF Mono', Monaco, monospace;
                    }
                </style>
            </head>
            <body>
                <div class="container">
                    <h1>Krema</h1>
                    <p>Lightweight desktop apps with system webviews</p>
                    <p style="margin-top: 1rem;">
                        Use <code>.html()</code>, <code>.devUrl()</code>, or <code>.prodAssets()</code> to set content
                    </p>
                </div>
            </body>
            </html>
            """;
    }

    private static boolean isNativeImage() {
        return System.getProperty("org.graalvm.nativeimage.imagecode") != null;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, String> parseEnvVarsJson(String json) {
        try {
            return build.krema.core.util.Json.mapper().readValue(json, Map.class);
        } catch (Exception e) {
            System.err.println("[Krema] Failed to parse KREMA_ENV_VARS: " + e.getMessage());
            return Collections.emptyMap();
        }
    }
}
