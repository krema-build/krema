---
sidebar_position: 3
title: Architecture
description: Technical architecture of the Krema framework
---

# Architecture

Technical overview of Krema's internal architecture.

## High-Level Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                         Krema Application                       │
├─────────────────────────────────────────────────────────────────┤
│  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐ │
│  │   Frontend      │  │    IPC Layer    │  │    Backend      │ │
│  │   (WebView)     │◄─┤                 ├─►│    (Java)       │ │
│  │                 │  │  Custom IPC     │  │                 │ │
│  │  HTML/CSS/JS    │  │  over FFM       │  │  Commands       │ │
│  │  React/Vue/etc  │  │                 │  │  Plugins        │ │
│  └─────────────────┘  └─────────────────┘  └─────────────────┘ │
├─────────────────────────────────────────────────────────────────┤
│                      Platform Abstraction                       │
│  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐ │
│  │   macOS         │  │   Windows       │  │   Linux         │ │
│  │   WebKit        │  │   WebView2      │  │   WebKitGTK     │ │
│  └─────────────────┘  └─────────────────┘  └─────────────────┘ │
└─────────────────────────────────────────────────────────────────┘
```

## Package Structure

```
build.krema.core/
├── Krema.java                 # Main entry point, builder API
├── KremaWindow.java           # High-level window abstraction
├── KremaCommand.java          # @KremaCommand annotation
├── CommandRegistry.java       # Routes IPC requests to handlers
├── CommandRegistrar.java      # Generated registrar interface
├── CommandInvoker.java        # Functional interface for dispatch
├── AssetServer.java           # Serves frontend assets (SPA-aware)
├── main/                      # Application bootstrap
│   ├── KremaApplication.java
│   ├── KremaBuilder.java
│   └── KremaContext.java
├── platform/                  # Platform abstraction
│   ├── Platform.java          # Platform enum (MACOS, WINDOWS, LINUX)
│   ├── PlatformDetector.java  # OS detection
│   ├── NativeLibraryLoader.java
│   ├── linux/GtkBindings.java
│   └── windows/Win32Bindings.java
├── webview/                   # WebView engine abstraction
│   ├── WebViewEngine.java     # Interface (extends AutoCloseable)
│   ├── WebViewCLibEngine.java # Shared FFM base implementation
│   ├── WebViewEngineFactory.java
│   ├── macos/MacOSWebViewEngine.java
│   ├── windows/WindowsWebViewEngine.java
│   └── linux/LinuxWebViewEngine.java
├── window/                    # Window management
│   ├── WindowEngine.java      # Port interface
│   ├── WindowEngineFactory.java
│   ├── WindowManager.java     # Multi-window support
│   ├── WindowOptions.java     # Window configuration
│   ├── WindowState.java
│   ├── macos/MacOSWindowEngine.java
│   ├── windows/WindowsWindowEngine.java
│   └── linux/LinuxWindowEngine.java
├── ipc/                       # Inter-process communication
│   └── IpcHandler.java        # Message handling + bridge injection
├── event/                     # Event system
│   ├── KremaEvent.java        # Event interface
│   └── EventEmitter.java      # Emitter
├── api/                       # High-level API facades
│   ├── dialog/                # File dialogs
│   ├── clipboard/             # Clipboard
│   ├── notification/          # Notifications
│   ├── tray/                  # System tray
│   ├── shell/                 # Shell commands
│   ├── path/                  # Path utilities
│   ├── screen/                # Screen information
│   ├── window/                # Window API
│   ├── menu/                  # Menus
│   ├── shortcut/              # Global shortcuts
│   ├── store/                 # Key-value store
│   ├── securestorage/         # OS keychain storage
│   ├── dock/                  # macOS dock
│   ├── dragdrop/              # Drag and drop
│   ├── http/                  # HTTP client
│   ├── os/                    # OS information
│   ├── app/                   # App environment
│   └── instance/              # Single instance
├── ports/                     # Port interfaces (hexagonal arch)
│   ├── DialogPort.java
│   ├── ScreenPort.java
│   ├── WindowPort.java
│   ├── MenuPort.java
│   ├── NotificationPort.java
│   ├── DockPort.java
│   └── GlobalShortcutPort.java
├── adapters/                  # Adapter implementations
│   └── factory/AdapterFactory.java
├── plugin/                    # Plugin system
│   ├── KremaPlugin.java       # Interface
│   ├── PluginContext.java
│   ├── PluginLoader.java
│   ├── PluginManifest.java
│   ├── PluginException.java
│   └── builtin/               # Built-in plugins
│       ├── DeepLinkPlugin.java
│       ├── FsPlugin.java
│       ├── LogPlugin.java
│       └── UpdaterPlugin.java
├── security/                  # Security
│   ├── Permission.java        # Enum
│   ├── RequiresPermission.java # Annotation
│   ├── PermissionChecker.java
│   └── ContentSecurityPolicy.java
├── error/                     # Error handling
│   ├── ErrorHandler.java
│   ├── ErrorInfo.java
│   └── ErrorContext.java
├── util/                      # Utilities
│   ├── Logger.java
│   ├── LogEntry.java
│   ├── LogContext.java
│   └── JsonFileLogWriter.java
├── screen/                    # Screen engine implementations
│   ├── ScreenEngine.java
│   ├── ScreenEngineFactory.java
│   ├── ScreenInfo.java
│   ├── ScreenBounds.java
│   └── CursorPosition.java
├── dialog/                    # Dialog engine implementations
├── menu/                      # Menu engine implementations
├── notification/              # Notification engine implementations
├── shortcut/                  # Global shortcut engine implementations
├── dock/                      # Dock engine implementations
├── updater/                   # Auto-update system
├── splash/                    # Splash screen
├── dev/                       # Development tools
│   ├── DevServer.java
│   ├── FileWatcher.java
│   └── ErrorOverlay.java
└── native_/                   # Native bindings
    └── WebViewBindings.java
```

## Core Components

### Platform Abstraction

Krema detects the OS and loads platform-specific implementations:

```java
public enum Platform {
    MACOS("macOS", "dylib", "lib%s.dylib"),
    WINDOWS("Windows", "dll", "%s.dll"),
    LINUX("Linux", "so", "lib%s.so"),
    UNKNOWN("Unknown", null, null);

    // Each variant carries display name, library extension, and library name pattern
    public String getDisplayName() { ... }
    public String getLibraryExtension() { ... }
    public String formatLibraryName(String baseName) { ... }

    /** Delegates to PlatformDetector.detect(). */
    public static Platform current() { ... }
}

public final class PlatformDetector {
    public static Platform detect() { ... }
    public static String getArch() { ... }    // "aarch64", "x86_64", "x86"
    public static boolean isMacOS() { ... }
    public static boolean isWindows() { ... }
    public static boolean isLinux() { ... }
}
```

### WebView Engine

The `WebViewEngine` interface abstracts platform-specific WebView implementations:

```java
public interface WebViewEngine extends AutoCloseable {
    void setTitle(String title);
    void setSize(int width, int height, SizeHint hint);
    void navigate(String url);
    void setHtml(String html);
    void init(String js);              // Inject JS executed on every page load
    void eval(String js);              // Execute JS in the current context
    void bind(String name, BindCallback callback);
    void returnResult(String seq, boolean success, String result);
    void run();                        // Blocks until window closed
    void terminate();
    boolean isRunning();
    void close();

    @FunctionalInterface
    interface BindCallback {
        void invoke(String seq, String request);
    }

    enum SizeHint { NONE, MIN, MAX, FIXED }
}
```

`WebViewCLibEngine` is the shared abstract base class that implements `WebViewEngine` using the Foreign Function & Memory (FFM) API to call the native webview C library. Platform subclasses extend it:

- **`MacOSWebViewEngine`** — Cocoa + WKWebView
- **`WindowsWebViewEngine`** — Win32 + WebView2
- **`LinuxWebViewEngine`** — GTK + WebKitGTK

### IPC (Inter-Process Communication)

Frontend-to-backend communication uses a custom IPC protocol over FFM bindings:

```
Frontend                    Backend
   │                           │
   │  invoke('greet', {name})  │
   ├──────────────────────────►│
   │                           │  CommandRegistry.invoke()
   │                           │  @KremaCommand method
   │◄──────────────────────────┤
   │  Promise resolves         │
```

**Request format:**
```json
{
  "cmd": "greet",
  "args": {"name": "World"}
}
```

The sequence ID (`seq`) is managed by the native webview C library's `bind` mechanism, not included in the JSON payload. When a bound JS function is called, the C library passes a `seq` identifier and the serialized arguments to the Java callback.

**Response:** The backend calls `webviewEngine.returnResult(seq, success, json)` to resolve or reject the frontend Promise. There is no JSON response envelope — the result value is passed directly.

### Command System

Commands are registered via annotation processing:

```java
@KremaCommand
public String greet(String name) {
    return "Hello, " + name + "!";
}
```

At compile time, `KremaCommandProcessor` generates:
1. Static invoker classes (no reflection)
2. `CommandRegistrar` implementations
3. ServiceLoader entries
4. `krema-commands.d.ts` — TypeScript type definitions for all commands, including interfaces for record/POJO return types, a `KremaCommandMap` mapping command names to their argument and result types, and a typed `krema.invoke()` overload

The `CommandRegistry` routes incoming requests to the appropriate handler.

### Event System

Backend-to-frontend events use the `EventEmitter`:

```java
events.emit("file-changed", Map.of("path", filePath));
```

This translates to a JavaScript call:
```javascript
window.__krema_event("file-changed", {"path": "/path/to/file"});
```

Frontend listeners registered via `window.krema.on()` are invoked.

### Plugin System

Plugins implement the `KremaPlugin` interface:

```java
public interface KremaPlugin {
    String getId();
    String getName();
    String getVersion();
    default String getDescription() { return ""; }
    default void initialize(PluginContext context) {}
    default void shutdown() {}
    default List<Object> getCommandHandlers() { return List.of(); }
    default List<String> getRequiredPermissions() { return List.of(); }
    default void onWindowCreated(String windowLabel) {}
    default void onWindowClosed(String windowLabel) {}
}
```

Discovery methods:
1. **ServiceLoader**: Built-in plugins
2. **URLClassLoader**: External JAR plugins

The `PluginLoader` manages lifecycle and dependency ordering.

## Native API Implementation

### FFM (Foreign Function & Memory) API

Krema uses Project Panama's FFM API for native calls:

```java
// Define native function signature
MethodHandle createWindow = Linker.nativeLinker().downcallHandle(
    lookup.find("webview_create").orElseThrow(),
    FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.JAVA_INT)
);

// Call native function
MemorySegment webview = (MemorySegment) createWindow.invokeExact(debug ? 1 : 0);
```

### Platform-Specific Bindings

All platforms share the `WebViewCLibEngine` base class which uses the FFM API to call the native webview C library. Platform-specific extensions and additional native bindings:

- **macOS**: `MacOSWebViewEngine`, `MacOSWindowEngine`, `MacOSDialogEngine`, `MacOSMenuEngine`, etc.
- **Windows**: `WindowsWebViewEngine`, `WindowsWindowEngine`, `Win32Bindings`
- **Linux**: `LinuxWebViewEngine`, `LinuxWindowEngine`, `GtkBindings`

## Build Process

### Development Mode

```
krema dev
    │
    ├─► Start frontend dev server (npm run dev)
    │
    ├─► Wait for server ready
    │
    ├─► Launch Java with WebView pointing to dev URL
    │
    └─► Watch for changes, hot reload
```

### Production Build

```
krema build
    │
    ├─► Run frontend build (npm run build)
    │
    ├─► Compile Java sources
    │
    ├─► Copy assets to resources
    │
    └─► Create executable JAR
```

### Native Image Build

```
krema build --native
    │
    ├─► Resolve Maven classpath
    │
    ├─► Generate resource-config.json for frontend assets
    │
    ├─► Discover native-image tool
    │   ├── GRAALVM_HOME / JAVA_HOME
    │   ├── macOS: /usr/libexec/java_home -v 25+
    │   ├── Linux: update-alternatives --query java
    │   └── PATH lookup
    │
    ├─► Invoke native-image compiler
    │   ├── --enable-native-access=ALL-UNNAMED
    │   ├── Built-in reflect-config.json (IPC, events, plugins)
    │   └── Platform flags (e.g. -arch arm64 on Apple Silicon)
    │
    └─► Copy native webview library to target/
```

See the [Native Image guide](/docs/guides/native-image) for full details.

### Bundling

```
krema bundle
    │
    ├─► Detect platform
    │
    ├─► Create platform-specific bundle
    │   ├── macOS: .app + .dmg
    │   ├── Windows: .exe installer
    │   └── Linux: .AppImage
    │
    └─► Optional: Sign and notarize
```

## Security Model

### Permission Enforcement

```java
@KremaCommand
@RequiresPermission(Permission.FS_READ)
public String readFile(String path) {
    // PermissionChecker validates before execution
}
```

The `PermissionChecker` validates against `krema.toml` configuration.

### CSP (Content Security Policy)

CSP headers are injected based on configuration:

```java
if (cspEnabled) {
    webview.init("const meta = document.createElement('meta');" +
        "meta.httpEquiv = 'Content-Security-Policy';" +
        "meta.content = '" + cspPolicy + "';" +
        "document.head.appendChild(meta);");
}
```

## Auto-Update System

```
┌─────────────┐     ┌─────────────┐     ┌─────────────┐
│   App       │────►│ Update      │────►│ Download    │
│   Startup   │     │ Check       │     │ & Verify    │
└─────────────┘     └─────────────┘     └──────┬──────┘
                                               │
                    ┌─────────────┐     ┌──────▼──────┐
                    │   Restart   │◄────│   Install   │
                    └─────────────┘     └─────────────┘
```

1. **Check**: HTTP GET to update endpoint
2. **Verify**: Ed25519 signature validation
3. **Download**: With progress events
4. **Install**: Platform-specific (replace app/run installer)
5. **Restart**: Graceful shutdown and relaunch

## Performance Considerations

### Memory Management

- FFM memory is explicitly managed with `Arena`
- Native resources tracked via `ResourceTracker`
- Cleanup on window close

### Startup Time

- Annotation-processed commands = no runtime reflection
- Native image build = instant startup
- Lazy plugin loading

### IPC Efficiency

- JSON serialization via Jackson (fast)
- Binary data as base64 (could optimize with custom protocol)
- Async command execution on virtual threads
