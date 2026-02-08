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
│  │                 │  │  JSON-RPC       │  │                 │ │
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
build.krema/
├── core/                      # Core framework
│   ├── Krema.java             # Main entry point, builder API
│   ├── KremaWindow.java       # High-level window abstraction
│   └── KremaConfig.java       # Configuration model
├── platform/                  # Platform abstraction
│   ├── Platform.java          # Platform enum
│   ├── PlatformDetector.java  # OS detection
│   └── NativeLibraryLoader.java
├── webview/                   # WebView engine abstraction
│   ├── WebViewEngine.java     # Interface
│   ├── WebViewEngineFactory.java
│   ├── macos/MacOSWebViewEngine.java
│   ├── windows/WindowsWebViewEngine.java
│   └── linux/LinuxWebViewEngine.java
├── window/                    # Window management
│   ├── WindowOptions.java     # Window configuration
│   ├── WindowManager.java     # Multi-window support
│   └── WindowState.java
├── ipc/                       # Inter-process communication
│   ├── IpcHandler.java        # Message handling
│   ├── IpcRequest.java        # Request model
│   └── IpcResponse.java       # Response model
├── command/                   # Command system
│   ├── KremaCommand.java      # Annotation
│   ├── CommandRegistry.java   # Registry
│   └── RequiresPermission.java
├── event/                     # Event system
│   ├── KremaEvent.java        # Event interface
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
│   ├── KremaPlugin.java       # Interface
│   ├── PluginContext.java
│   └── PluginLoader.java
└── security/                  # Security
    ├── Permission.java        # Enum
    └── PermissionChecker.java
```

## Core Components

### Platform Abstraction

Krema detects the OS and loads platform-specific implementations:

```java
public enum Platform {
    MACOS, WINDOWS, LINUX, UNKNOWN
}

public class PlatformDetector {
    public static Platform current() {
        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("mac")) return Platform.MACOS;
        if (os.contains("win")) return Platform.WINDOWS;
        if (os.contains("nux")) return Platform.LINUX;
        return Platform.UNKNOWN;
    }
}
```

### WebView Engine

The `WebViewEngine` interface abstracts platform-specific WebView implementations:

```java
public interface WebViewEngine {
    void create(boolean debug);
    void destroy();
    void setTitle(String title);
    void setSize(int width, int height, SizeHint hint);
    void navigate(String url);
    void setHtml(String html);
    void init(String js);
    void eval(String js);
    void bind(String name, BindCallback callback);
    void run();
    void terminate();
}
```

Platform implementations:
- **macOS**: WKWebView via Objective-C FFM
- **Windows**: WebView2 via C++ FFM
- **Linux**: WebKitGTK via C FFM

### IPC (Inter-Process Communication)

Frontend-to-backend communication uses JSON-RPC over FFM bindings:

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
  "id": "uuid",
  "command": "greet",
  "args": {"name": "World"}
}
```

**Response format:**
```json
{
  "id": "uuid",
  "result": "Hello, World!"
}
```

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
    String getName();
    String getVersion();
    void initialize(PluginContext context);
    void shutdown();
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

Each platform has dedicated binding classes:

- **macOS**: `CocoaBindings`, `WebKitBindings`
- **Windows**: `Win32Bindings`, `WebView2Bindings`
- **Linux**: `GtkBindings`, `WebKitGtkBindings`

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
