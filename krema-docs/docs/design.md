# Crema Technical Design Document

## Overview

Crema is a lightweight desktop application framework that uses system webviews for rendering, similar to Tauri. This document describes the technical architecture and design decisions.

---

## Architecture

### High-Level Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                         Crema Application                        │
├─────────────────────────────────────────────────────────────────┤
│  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐ │
│  │   Frontend      │  │    IPC Layer    │  │    Backend      │ │
│  │   (WebView)     │◄─┤                 ├─►│    (Java)       │ │
│  │                 │  │  JSON-RPC       │  │                 │ │
│  │  HTML/CSS/JS    │  │  over FFM       │  │  Commands       │ │
│  │  React/Vue/etc  │  │                 │  │  Plugins        │ │
│  └─────────────────┘  └─────────────────┘  └─────────────────┘ │
├─────────────────────────────────────────────────────────────────┤
│                      Platform Abstraction                        │
│  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐ │
│  │   macOS         │  │   Windows       │  │   Linux         │ │
│  │   WebKit        │  │   WebView2      │  │   WebKitGTK     │ │
│  └─────────────────┘  └─────────────────┘  └─────────────────┘ │
└─────────────────────────────────────────────────────────────────┘
```

### Package Structure

```
build.krema/
├── core/                      # Core framework
│   ├── Crema.java             # Main entry point, builder API
│   ├── CremaWindow.java       # High-level window abstraction
│   └── CremaConfig.java       # Configuration model
├── platform/                  # Platform abstraction
│   ├── Platform.java          # Platform enum
│   ├── PlatformDetector.java  # OS detection
│   └── NativeLibraryLoader.java
├── webview/                   # WebView engine abstraction
│   ├── WebViewEngine.java     # Interface
│   ├── WebViewEngineFactory.java
│   └── macos/
│       └── MacOSWebViewEngine.java
├── window/                    # Window management
│   ├── WindowOptions.java     # Window configuration
│   ├── WindowManager.java     # Multi-window support
│   └── WindowState.java
├── ipc/                       # Inter-process communication
│   ├── IpcHandler.java        # Message handling
│   ├── IpcRequest.java        # Request model
│   └── IpcResponse.java       # Response model
├── command/                   # Command system
│   ├── CremaCommand.java      # Annotation
│   ├── CommandRegistry.java   # Registry
│   └── RequiresPermission.java
├── event/                     # Event system
│   ├── CremaEvent.java        # Event interface
│   ├── EventEmitter.java      # Emitter
│   └── EventListener.java     # Listener
├── api/                       # Native APIs
│   ├── dialog/                # File dialogs
│   ├── clipboard/             # Clipboard
│   ├── notification/          # Notifications
│   ├── tray/                  # System tray
│   ├── shell/                 # Shell commands
│   └── path/                  # Path utilities
├── plugin/                    # Plugin system
│   ├── CremaPlugin.java       # Interface
│   ├── PluginContext.java
│   ├── PluginLoader.java
│   └── PluginManifest.java
├── security/                  # Security
│   ├── Permission.java        # Enum
│   ├── PermissionChecker.java
│   └── ContentSecurityPolicy.java
├── server/                    # Asset server
│   └── AssetServer.java
├── cli/                       # CLI tools
│   ├── CremaCliMain.java
│   ├── InitCommand.java
│   ├── DevCommand.java
│   ├── BuildCommand.java
│   └── BundleCommand.java
└── util/                      # Utilities
    ├── Logger.java
    └── Json.java
```

---

## Core Components

### Platform Abstraction

```java
public enum Platform {
    MACOS("macOS", "dylib", "lib%s.dylib"),
    WINDOWS("Windows", "dll", "%s.dll"),
    LINUX("Linux", "so", "lib%s.so"),
    UNKNOWN("Unknown", null, null);

    private final String displayName;
    private final String libraryExtension;
    private final String libraryPattern;

    public static Platform current() {
        return PlatformDetector.detect();
    }
}

public class PlatformDetector {
    public static Platform detect() {
        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("mac")) return Platform.MACOS;
        if (os.contains("win")) return Platform.WINDOWS;
        if (os.contains("nux") || os.contains("nix")) return Platform.LINUX;
        return Platform.UNKNOWN;
    }

    public static String getArch() {
        String arch = System.getProperty("os.arch").toLowerCase();
        if (arch.contains("aarch64") || arch.contains("arm64")) return "aarch64";
        if (arch.contains("amd64") || arch.contains("x86_64")) return "x86_64";
        return arch;
    }
}
```

### Native Library Loader

```java
public class NativeLibraryLoader {
    private static final Map<String, SymbolLookup> loadedLibraries = new ConcurrentHashMap<>();

    public static SymbolLookup load(String libraryName) {
        return loadedLibraries.computeIfAbsent(libraryName, NativeLibraryLoader::doLoad);
    }

    private static SymbolLookup doLoad(String libraryName) {
        Platform platform = Platform.current();

        // 1. Try system library path
        Path systemPath = findInSystemPath(libraryName, platform);
        if (systemPath != null) {
            return loadFromPath(systemPath);
        }

        // 2. Try bundled in JAR
        Path extractedPath = extractFromJar(libraryName, platform);
        if (extractedPath != null) {
            return loadFromPath(extractedPath);
        }

        // 3. Try java.library.path
        String javaLibPath = System.getProperty("java.library.path");
        // ... search logic

        throw new RuntimeException("Library not found: " + libraryName);
    }

    private static Path extractFromJar(String libraryName, Platform platform) {
        String resourcePath = "/native/" + platform.name().toLowerCase() + "/" +
                              PlatformDetector.getArch() + "/" +
                              platform.formatLibraryName(libraryName);

        try (InputStream is = NativeLibraryLoader.class.getResourceAsStream(resourcePath)) {
            if (is == null) return null;

            Path tempDir = Files.createTempDirectory("crema-native");
            Path libPath = tempDir.resolve(platform.formatLibraryName(libraryName));
            Files.copy(is, libPath);
            libPath.toFile().deleteOnExit();
            return libPath;
        } catch (IOException e) {
            return null;
        }
    }
}
```

### WebView Engine Interface

```java
public interface WebViewEngine extends AutoCloseable {
    // Lifecycle
    void create(boolean debug);
    void destroy();
    void run();
    void terminate();

    // Window
    void setTitle(String title);
    void setSize(int width, int height, SizeHint hint);

    // Content
    void navigate(String url);
    void setHtml(String html);

    // JavaScript
    void init(String js);
    void eval(String js);

    // IPC
    void bind(String name, BindCallback callback);
    void returnResult(String seq, boolean success, String result);

    // Handle access
    Object getNativeHandle();

    @FunctionalInterface
    interface BindCallback {
        void invoke(String seq, String request);
    }

    enum SizeHint {
        NONE(0), MIN(1), MAX(2), FIXED(3);
        final int value;
        SizeHint(int value) { this.value = value; }
    }
}
```

### Window Options

```java
public record WindowOptions(
    String title,
    int width,
    int height,
    int minWidth,
    int minHeight,
    int maxWidth,
    int maxHeight,
    boolean resizable,
    boolean fullscreen,
    boolean alwaysOnTop,
    boolean transparent,
    boolean decorations,
    boolean center,
    Integer x,
    Integer y
) {
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String title = "Crema App";
        private int width = 1024;
        private int height = 768;
        private int minWidth = 0;
        private int minHeight = 0;
        private int maxWidth = Integer.MAX_VALUE;
        private int maxHeight = Integer.MAX_VALUE;
        private boolean resizable = true;
        private boolean fullscreen = false;
        private boolean alwaysOnTop = false;
        private boolean transparent = false;
        private boolean decorations = true;
        private boolean center = true;
        private Integer x = null;
        private Integer y = null;

        public Builder title(String title) { this.title = title; return this; }
        public Builder size(int width, int height) {
            this.width = width;
            this.height = height;
            return this;
        }
        // ... other setters

        public WindowOptions build() {
            return new WindowOptions(
                title, width, height, minWidth, minHeight, maxWidth, maxHeight,
                resizable, fullscreen, alwaysOnTop, transparent, decorations,
                center, x, y
            );
        }
    }
}
```

### Event System

```java
public interface CremaEvent {
    String name();
    Object payload();
    long timestamp();
}

public class EventEmitter {
    private final WebViewEngine engine;
    private final ObjectMapper mapper = new ObjectMapper();

    public EventEmitter(WebViewEngine engine) {
        this.engine = engine;
    }

    public void emit(String eventName, Object payload) {
        try {
            String json = mapper.writeValueAsString(Map.of(
                "name", eventName,
                "payload", payload,
                "timestamp", System.currentTimeMillis()
            ));
            engine.eval("window.__crema_event && window.__crema_event(" + json + ")");
        } catch (Exception e) {
            // Log error
        }
    }
}
```

**JavaScript side (crema-bridge.js):**

```javascript
window.crema = window.crema || {};

const listeners = new Map();

window.crema.on = function(event, callback) {
    if (!listeners.has(event)) {
        listeners.set(event, new Set());
    }
    listeners.get(event).add(callback);
    return () => window.crema.off(event, callback);
};

window.crema.off = function(event, callback) {
    if (listeners.has(event)) {
        listeners.get(event).delete(callback);
    }
};

window.crema.once = function(event, callback) {
    const wrapper = (data) => {
        window.crema.off(event, wrapper);
        callback(data);
    };
    return window.crema.on(event, wrapper);
};

window.__crema_event = function(event) {
    const eventListeners = listeners.get(event.name);
    if (eventListeners) {
        eventListeners.forEach(cb => {
            try {
                cb(event.payload);
            } catch (e) {
                console.error('Event listener error:', e);
            }
        });
    }
};
```

---

## IPC Protocol

### Request Format

```json
{
  "cmd": "commandName",
  "args": {
    "param1": "value1",
    "param2": 123
  }
}
```

### Response Format (Success)

```json
{
  "result": { ... }
}
```

### Response Format (Error)

```json
{
  "error": "Error message"
}
```

### Sequence Diagram

```
┌──────────┐          ┌──────────┐          ┌──────────┐
│ Frontend │          │   IPC    │          │ Backend  │
│   (JS)   │          │  Bridge  │          │  (Java)  │
└────┬─────┘          └────┬─────┘          └────┬─────┘
     │                     │                     │
     │ invoke(cmd, args)   │                     │
     │────────────────────>│                     │
     │                     │                     │
     │                     │ __crema_invoke(seq, json)
     │                     │────────────────────>│
     │                     │                     │
     │                     │    invoke command   │
     │                     │                     │
     │                     │<────────────────────│
     │                     │    returnResult     │
     │                     │                     │
     │<────────────────────│                     │
     │  resolve/reject     │                     │
     │  promise            │                     │
```

---

## Plugin System

### Plugin Interface

```java
public interface CremaPlugin {
    String getName();
    String getVersion();

    default void initialize(PluginContext context) {}
    default void shutdown() {}
    default List<Object> getCommandHandlers() { return List.of(); }
}

public interface PluginContext {
    WindowManager getWindowManager();
    EventEmitter getEventEmitter();
    CommandRegistry getCommandRegistry();
    <T> T getConfig(Class<T> configClass);
    Logger getLogger();
}
```

### Plugin Manifest (plugin.json)

```json
{
  "name": "fs",
  "version": "1.0.0",
  "description": "File system operations",
  "main": "build.krema.plugin.fs.FsPlugin",
  "permissions": ["fs:read", "fs:write"],
  "dependencies": []
}
```

### Plugin Loading

```java
public class PluginLoader {
    private final List<CremaPlugin> plugins = new ArrayList<>();

    public void loadBuiltinPlugins() {
        ServiceLoader<CremaPlugin> loader = ServiceLoader.load(CremaPlugin.class);
        for (CremaPlugin plugin : loader) {
            plugins.add(plugin);
        }
    }

    public void loadExternalPlugin(Path jarPath) throws Exception {
        URLClassLoader classLoader = new URLClassLoader(
            new URL[] { jarPath.toUri().toURL() },
            getClass().getClassLoader()
        );

        // Read plugin.json from JAR
        URL manifestUrl = classLoader.findResource("plugin.json");
        PluginManifest manifest = parseManifest(manifestUrl);

        // Load plugin class
        Class<?> pluginClass = classLoader.loadClass(manifest.main());
        CremaPlugin plugin = (CremaPlugin) pluginClass.getDeclaredConstructor().newInstance();

        plugins.add(plugin);
    }

    public void initializeAll(PluginContext context) {
        for (CremaPlugin plugin : plugins) {
            plugin.initialize(context);
        }
    }
}
```

---

## Security Model

### Permissions

```java
public enum Permission {
    // File system
    FS_READ("fs:read", "Read files"),
    FS_WRITE("fs:write", "Write files"),
    FS_ALL("fs:*", "Full file system access"),

    // Clipboard
    CLIPBOARD_READ("clipboard:read", "Read clipboard"),
    CLIPBOARD_WRITE("clipboard:write", "Write clipboard"),

    // Shell
    SHELL_EXECUTE("shell:execute", "Execute shell commands"),
    SHELL_OPEN("shell:open", "Open files/URLs"),

    // System
    NOTIFICATION("notification", "Show notifications"),
    SYSTEM_TRAY("system-tray", "System tray access"),
    SYSTEM_INFO("system-info", "Read system information");

    private final String key;
    private final String description;

    public boolean implies(Permission other) {
        if (this == other) return true;
        if (this.key.endsWith(":*")) {
            String prefix = this.key.substring(0, this.key.length() - 1);
            return other.key.startsWith(prefix);
        }
        return false;
    }
}

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface RequiresPermission {
    Permission[] value();
}
```

### Permission Checking

```java
public class PermissionChecker {
    private final Set<Permission> allowedPermissions;

    public PermissionChecker(Set<Permission> allowed) {
        this.allowedPermissions = allowed;
    }

    public void check(Method method) throws SecurityException {
        RequiresPermission annotation = method.getAnnotation(RequiresPermission.class);
        if (annotation == null) return;

        for (Permission required : annotation.value()) {
            if (!isAllowed(required)) {
                throw new SecurityException(
                    "Permission denied: " + required.getKey() +
                    " required for " + method.getName()
                );
            }
        }
    }

    private boolean isAllowed(Permission required) {
        for (Permission allowed : allowedPermissions) {
            if (allowed.implies(required)) {
                return true;
            }
        }
        return false;
    }
}
```

---

## Configuration Schema

### crema.toml

```toml
[package]
name = "my-app"
version = "1.0.0"
identifier = "com.example.myapp"
description = "My Crema Application"

[window]
title = "My App"
width = 1200
height = 800
min_width = 800
min_height = 600
resizable = true
fullscreen = false
decorations = true

[build]
frontend_command = "npm run build"
frontend_dev_command = "npm run dev"
frontend_dev_url = "http://localhost:5173"
out_dir = "dist"

[bundle]
icon = "icons/icon.icns"
identifier = "com.example.myapp"
copyright = "Copyright 2024"

[bundle.macos]
signing_identity = "Developer ID Application: ..."
notarization_apple_id = "..."

[permissions]
allow = [
    "fs:read",
    "clipboard:read",
    "clipboard:write",
    "notification"
]
```

### Configuration Class

```java
public record CremaConfig(
    PackageConfig packageConfig,
    WindowConfig window,
    BuildConfig build,
    BundleConfig bundle,
    PermissionsConfig permissions
) {
    public static CremaConfig load(Path path) throws IOException {
        Toml toml = new Toml().read(path.toFile());
        return toml.to(CremaConfig.class);
    }

    public record PackageConfig(
        String name,
        String version,
        String identifier,
        String description
    ) {}

    public record WindowConfig(
        String title,
        int width,
        int height,
        int minWidth,
        int minHeight,
        boolean resizable,
        boolean fullscreen,
        boolean decorations
    ) {}

    public record BuildConfig(
        String frontendCommand,
        String frontendDevCommand,
        String frontendDevUrl,
        String outDir
    ) {}

    public record BundleConfig(
        String icon,
        String identifier,
        String copyright,
        MacOSBundleConfig macos
    ) {}

    public record MacOSBundleConfig(
        String signingIdentity,
        String notarizationAppleId
    ) {}

    public record PermissionsConfig(
        List<String> allow
    ) {}
}
```

---

## CLI Commands

### Command Structure

```java
@Command(
    name = "crema",
    mixinStandardHelpOptions = true,
    version = "0.1.0",
    subcommands = {
        InitCommand.class,
        DevCommand.class,
        BuildCommand.class,
        BundleCommand.class
    }
)
public class CremaCliMain implements Callable<Integer> {
    public static void main(String[] args) {
        int exitCode = new CommandLine(new CremaCliMain()).execute(args);
        System.exit(exitCode);
    }

    @Override
    public Integer call() {
        CommandLine.usage(this, System.out);
        return 0;
    }
}
```

### Init Command

```java
@Command(name = "init", description = "Create a new Crema project")
public class InitCommand implements Callable<Integer> {

    @Parameters(index = "0", description = "Project name")
    private String projectName;

    @Option(names = {"-t", "--template"}, description = "Template: vanilla, react, vue, svelte")
    private String template = "vanilla";

    @Override
    public Integer call() {
        Path projectDir = Path.of(projectName);

        // Create directory structure
        Files.createDirectories(projectDir.resolve("src"));
        Files.createDirectories(projectDir.resolve("src-java"));

        // Copy template files
        copyTemplate(template, projectDir);

        // Generate crema.toml
        generateConfig(projectDir, projectName);

        System.out.println("Created project: " + projectName);
        return 0;
    }
}
```

### Dev Command

```java
@Command(name = "dev", description = "Start development mode")
public class DevCommand implements Callable<Integer> {

    @Override
    public Integer call() throws Exception {
        CremaConfig config = CremaConfig.load(Path.of("crema.toml"));

        // Start frontend dev server
        Process frontendProcess = startFrontendDevServer(config);

        // Wait for dev server to be ready
        waitForServer(config.build().frontendDevUrl());

        // Start Crema window
        Crema.app()
            .title(config.window().title())
            .size(config.window().width(), config.window().height())
            .devUrl(config.build().frontendDevUrl())
            .debug()
            .run();

        // Cleanup
        frontendProcess.destroy();
        return 0;
    }
}
```

---

## Memory Management

### Resource Tracking

```java
public class ResourceTracker implements AutoCloseable {
    private final List<AutoCloseable> resources = new ArrayList<>();
    private final Arena arena;

    public ResourceTracker() {
        this.arena = Arena.ofConfined();
    }

    public Arena getArena() {
        return arena;
    }

    public <T extends AutoCloseable> T track(T resource) {
        resources.add(resource);
        return resource;
    }

    @Override
    public void close() {
        // Close in reverse order
        for (int i = resources.size() - 1; i >= 0; i--) {
            try {
                resources.get(i).close();
            } catch (Exception e) {
                // Log but continue cleanup
            }
        }
        arena.close();
    }
}
```

### Usage Pattern

```java
try (ResourceTracker tracker = new ResourceTracker()) {
    MemorySegment buffer = tracker.getArena().allocate(1024);
    WebViewEngine engine = tracker.track(new MacOSWebViewEngine());
    // ... use resources
} // All resources automatically cleaned up
```

---

## Testing Strategy

### Unit Tests

```java
class CommandRegistryTest {
    @Test
    void shouldRegisterAnnotatedMethods() {
        CommandRegistry registry = new CommandRegistry();
        registry.register(new TestCommands());

        assertTrue(registry.hasCommand("greet"));
        assertEquals(1, registry.size());
    }

    @Test
    void shouldInvokeCommand() throws Exception {
        CommandRegistry registry = new CommandRegistry();
        registry.register(new TestCommands());

        IpcRequest request = new IpcRequest("greet", Map.of("name", "World"));
        Object result = registry.invoke(request);

        assertEquals("Hello, World!", result);
    }

    static class TestCommands {
        @CremaCommand
        public String greet(String name) {
            return "Hello, " + name + "!";
        }
    }
}
```

### Integration Tests

```java
class WindowIntegrationTest {
    @Test
    @EnabledOnOs(OS.MAC)
    void shouldCreateAndDestroyWindow() {
        try (CremaWindow window = new CremaWindow(false)) {
            assertNotNull(window.getHandle());
            window.title("Test Window");
            window.size(800, 600);
            // Note: Can't call run() in test as it blocks
        }
    }
}
```

---

## Performance Targets

| Metric | Target | Notes |
|--------|--------|-------|
| Cold start to first paint | < 1 second | Including JVM startup |
| Memory usage (idle) | < 100 MB | Private memory |
| Bundle size (macOS) | < 80 MB | Including JRE |
| IPC round-trip latency | < 1 ms | Simple command |

---

## Future Considerations

1. **GraalVM Native Image**: Investigate for faster startup and smaller bundles
2. **Multi-window support**: Already designed for, needs implementation
3. **Custom protocols**: `crema://` URL scheme for asset loading
4. **WebGL/GPU acceleration**: Ensure hardware acceleration is enabled
5. **Accessibility**: Implement accessibility APIs for screen readers
