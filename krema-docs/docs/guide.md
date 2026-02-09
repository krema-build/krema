# Crema User Guide

Build lightweight, cross-platform desktop applications using web technologies and Java.

---

## Table of Contents

1. [Introduction](#introduction)
2. [Getting Started](#getting-started)
3. [Project Structure](#project-structure)
4. [Configuration](#configuration)
5. [Frontend Development](#frontend-development)
6. [Backend Commands](#backend-commands)
7. [Events](#events)
8. [Native APIs](#native-apis)
9. [Splash Screen](#splash-screen)
10. [Building & Bundling](#building--bundling)
11. [Auto-Updates](#auto-updates)

---

## Introduction

Crema is a desktop application framework that combines:
- **System WebViews** for lightweight UI rendering (WebKit on macOS, WebView2 on Windows, WebKitGTK on Linux)
- **Java backend** for business logic and native API access
- **Project Panama FFM API** for efficient native interop

### Why Crema?

| Feature | Crema | Electron | Tauri |
|---------|-------|----------|-------|
| Bundle size | ~50-80MB | ~150-200MB | ~5-15MB |
| Memory usage | ~80-100MB | ~150-300MB | ~50-80MB |
| Backend language | Java | JavaScript | Rust |
| UI technology | HTML/CSS/JS | HTML/CSS/JS | HTML/CSS/JS |
| Native API access | FFM API | Node.js | Rust FFI |

---

## Getting Started

### Prerequisites

- Java 25+ (required for FFM API)
- Maven 3.8+
- Node.js 18+ (for frontend development)
- macOS 11+, Windows 10+, or Linux (with WebKitGTK)

#### Linux Setup

On Linux, install the required system libraries before building or running Crema apps:

**Ubuntu/Debian:**
```bash
sudo apt install libwebkit2gtk-4.1-0 libgtk-3-0
# Optional: for system tray support
sudo apt install libayatana-appindicator3-1
# Optional: for desktop notifications
sudo apt install libnotify-bin
```

**Fedora/RHEL:**
```bash
sudo dnf install webkit2gtk4.0 gtk3
# Optional: for system tray support
sudo dnf install libayatana-appindicator-gtk3
```

**Arch Linux:**
```bash
sudo pacman -S webkit2gtk-4.1 gtk3
# Optional: for system tray support
sudo pacman -S libayatana-appindicator
```

Note: Krema bundles the `libwebview.so` library - you only need to install the WebKitGTK runtime.

### Installation

```bash
# Clone and install Krema CLI
git clone https://github.com/ApokalypsixDev/krema.git
cd krema
./install.sh

# Or manually:
mvn clean install -DskipTests
export PATH="$PATH:$(pwd)/bin"
```

### Create Your First App

```bash
# Create a new project
krema init my-app --template react

# Navigate to project
cd my-app

# Start development mode
krema dev
```

---

## Project Structure

```
my-app/
├── krema.toml           # Crema configuration
├── pom.xml              # Maven build file
├── package.json         # Frontend dependencies
├── src/                 # Frontend source (HTML/CSS/JS)
│   ├── index.html
│   ├── main.js
│   └── style.css
├── src-java/            # Java backend source
│   └── com/example/myapp/
│       ├── Main.java    # Entry point
│       └── Commands.java # @KremaCommand methods
└── dist/                # Build output
```

---

## Configuration

### krema.toml

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
signing_identity = ""
notarization_apple_id = ""

[permissions]
allow = [
    "fs:read",
    "clipboard:read",
    "clipboard:write",
    "notification"
]

[splash]
enabled = true
image = "splash.png"
width = 480
height = 320
show_progress = true
```

### Window Options

| Option | Type | Default | Description |
|--------|------|---------|-------------|
| `title` | string | "Crema App" | Window title |
| `width` | int | 1024 | Initial width |
| `height` | int | 768 | Initial height |
| `min_width` | int | 0 | Minimum width |
| `min_height` | int | 0 | Minimum height |
| `resizable` | bool | true | Allow resizing |
| `fullscreen` | bool | false | Start fullscreen |
| `decorations` | bool | true | Show title bar |
| `always_on_top` | bool | false | Always on top |
| `transparent` | bool | false | Transparent background |

---

## Frontend Development

### JavaScript Bridge

Crema injects a global `window.krema` object:

```javascript
// Invoke a backend command
const result = await window.krema.invoke('greet', { name: 'World' });
console.log(result); // "Hello, World!"

// Listen for events from backend
window.krema.on('file-changed', (data) => {
    console.log('File changed:', data.path);
});

// One-time event listener
window.krema.once('app-ready', () => {
    console.log('App is ready!');
});

// Remove event listener
const unsubscribe = window.krema.on('update', handler);
unsubscribe(); // Remove listener
```

### TypeScript Support

Install type definitions:

```bash
npm install @krema-build/api
```

Usage:

```typescript
import { invoke, on } from '@krema-build/api';

interface User {
    id: string;
    name: string;
}

const user = await invoke<User>('getUser', { id: '123' });

on('user-updated', (user: User) => {
    console.log('User updated:', user.name);
});
```

### Framework Integration

**React:**

```jsx
import { useEffect, useState } from 'react';

function App() {
    const [greeting, setGreeting] = useState('');

    useEffect(() => {
        window.krema.invoke('greet', { name: 'React' })
            .then(setGreeting);
    }, []);

    return <h1>{greeting}</h1>;
}
```

**Vue:**

```vue
<script setup>
import { ref, onMounted } from 'vue';

const greeting = ref('');

onMounted(async () => {
    greeting.value = await window.krema.invoke('greet', { name: 'Vue' });
});
</script>

<template>
    <h1>{{ greeting }}</h1>
</template>
```

---

## Backend Commands

### Defining Commands

```java
import build.krema.command.KremaCommand;

public class Commands {

    @KremaCommand
    public String greet(String name) {
        return "Hello, " + name + "!";
    }

    @KremaCommand
    public UserInfo getUser(String id) {
        // Return objects are serialized to JSON
        return new UserInfo(id, "John Doe", "john@example.com");
    }

    @KremaCommand
    public void saveSettings(Map<String, Object> settings) {
        // Void commands return null to frontend
        settingsService.save(settings);
    }
}

public record UserInfo(String id, String name, String email) {}
```

### Registering Commands

```java
import build.krema.Crema;

public class Main {
    public static void main(String[] args) {
        Crema.app()
            .title("My App")
            .size(1200, 800)
            .devUrl("http://localhost:5173")
            .commands(new Commands())
            .debug()
            .run();
    }
}
```

### Permission-Protected Commands

```java
import build.krema.security.RequiresPermission;
import build.krema.security.Permission;

public class FileCommands {

    @KremaCommand
    @RequiresPermission(Permission.FS_READ)
    public String readFile(String path) throws IOException {
        return Files.readString(Path.of(path));
    }

    @KremaCommand
    @RequiresPermission(Permission.FS_WRITE)
    public void writeFile(String path, String content) throws IOException {
        Files.writeString(Path.of(path), content);
    }
}
```

### Async Commands

```java
@KremaCommand
public CompletableFuture<SearchResults> search(String query) {
    return CompletableFuture.supplyAsync(() -> {
        // Long-running operation
        return searchService.search(query);
    });
}
```

---

## Events

### Emitting Events from Backend

```java
import build.krema.Crema;
import build.krema.event.EventEmitter;

public class Main {
    private static EventEmitter events;

    public static void main(String[] args) {
        Crema.app()
            .title("My App")
            .events(emitter -> events = emitter)
            .commands(new Commands())
            .run();
    }

    // Call from anywhere to emit events
    public static void notifyFileChanged(String path) {
        events.emit("file-changed", Map.of("path", path));
    }
}
```

### Listening in Frontend

```javascript
// Subscribe to events
window.krema.on('file-changed', (data) => {
    console.log('File changed:', data.path);
    refreshFileList();
});

// Multiple listeners
window.krema.on('progress', (data) => updateProgressBar(data.percent));
window.krema.on('progress', (data) => console.log(`${data.percent}%`));

// One-time listener
window.krema.once('init-complete', () => {
    hideLoadingScreen();
});
```

---

## Native APIs

### File Dialogs

```java
import build.krema.api.dialog.FileDialog;

@KremaCommand
public String openFile() {
    return FileDialog.open()
        .title("Select a file")
        .filters("Text Files", "*.txt", "*.md")
        .filters("All Files", "*.*")
        .show()
        .map(Path::toString)
        .orElse(null);
}

@KremaCommand
public String saveFile() {
    return FileDialog.save()
        .title("Save file")
        .defaultName("document.txt")
        .show()
        .map(Path::toString)
        .orElse(null);
}

@KremaCommand
public List<String> openMultiple() {
    return FileDialog.openMultiple()
        .title("Select files")
        .show()
        .stream()
        .map(Path::toString)
        .toList();
}
```

### Clipboard

```java
import build.krema.api.clipboard.Clipboard;

@KremaCommand
public String getClipboardText() {
    return Clipboard.readText();
}

@KremaCommand
public void setClipboardText(String text) {
    Clipboard.writeText(text);
}

@KremaCommand
public void copyImage(String base64Image) {
    byte[] imageData = Base64.getDecoder().decode(base64Image);
    Clipboard.writeImage(imageData);
}
```

### Notifications

```java
import build.krema.api.notification.Notification;

@KremaCommand
public void showNotification(String title, String body) {
    Notification.show(title, body);
}

@KremaCommand
public void showNotificationWithOptions(String title, String body, String subtitle) {
    Notification.builder()
        .title(title)
        .body(body)
        .subtitle(subtitle)
        .sound(true)
        .show();
}
```

### System Tray

```java
import build.krema.api.tray.SystemTray;
import build.krema.api.tray.TrayMenu;

public void setupTray() {
    SystemTray tray = SystemTray.create();

    tray.setIcon(loadIcon());
    tray.setTooltip("My App");

    TrayMenu menu = TrayMenu.builder()
        .item("Show Window", () -> showWindow())
        .separator()
        .item("Settings", () -> openSettings())
        .separator()
        .item("Quit", () -> System.exit(0))
        .build();

    tray.setMenu(menu);
}
```

### Shell Commands

```java
import build.krema.api.shell.Shell;

@KremaCommand
@RequiresPermission(Permission.SHELL_OPEN)
public void openInBrowser(String url) {
    Shell.open(url);
}

@KremaCommand
@RequiresPermission(Permission.SHELL_OPEN)
public void openInFinder(String path) {
    Shell.open(path);
}

@KremaCommand
@RequiresPermission(Permission.SHELL_EXECUTE)
public Shell.Result runCommand(String command, List<String> args) {
    return Shell.execute(command, args);
}
```

### Path Utilities

```java
import build.krema.api.path.AppPaths;

@KremaCommand
public Map<String, String> getAppPaths() {
    return Map.of(
        "data", AppPaths.dataDir("my-app").toString(),
        "config", AppPaths.configDir("my-app").toString(),
        "cache", AppPaths.cacheDir("my-app").toString(),
        "home", AppPaths.homeDir().toString(),
        "documents", AppPaths.documentsDir().toString(),
        "downloads", AppPaths.downloadsDir().toString()
    );
}
```

---

## Splash Screen

### Basic Usage

```java
Crema.app()
    .title("My App")
    .splash() // Enable with defaults
    .run();
```

### Custom Configuration

```java
import build.krema.splash.SplashScreenOptions;

Crema.app()
    .title("My App")
    .splash(SplashScreenOptions.builder()
        .appName("My App")
        .version("1.0.0")
        .imagePath("/splash.png") // Classpath or file path
        .size(480, 320)
        .showProgress(true)
        .showStatus(true)
        .fadeOut(true)
        .build())
    .run();
```

### Custom Progress Updates

```java
Crema.app()
    .splash(options, splash -> {
        splash.setProgress(50);
        splash.setStatus("Loading plugins...");
        loadPlugins();

        splash.setProgress(80);
        splash.setStatus("Connecting to server...");
        connectToServer();
    })
    .run();
```

### Standalone Usage

```java
import build.krema.splash.SplashScreenManager;

SplashScreenManager.forApp("My App", "1.0.0")
    .runWithProgress(progress -> {
        progress.report(20, "Loading configuration...");
        loadConfig();

        progress.report(50, "Initializing database...");
        initDatabase();

        progress.report(80, "Starting services...");
        startServices();

        return null;
    });
```

---

## Building & Bundling

### Development Mode

```bash
krema dev
```

This:
1. Starts your frontend dev server (npm run dev)
2. Waits for it to be ready
3. Opens a Crema window pointing to the dev server
4. Enables debug tools (right-click to inspect)

### Production Build

```bash
krema build
```

This:
1. Runs frontend build (npm run build)
2. Compiles Java sources
3. Copies assets to resources
4. Creates executable JAR

### Creating Bundles

```bash
# Create platform bundle
krema bundle

# macOS: Creates .app and .dmg
# Windows: Creates .exe and .msi (future)
# Linux: Creates AppDir structure (use appimagetool for .AppImage, jpackage for .deb/.rpm)
```

### Bundle Options

```toml
[bundle]
icon = "icons/icon.icns"    # App icon
identifier = "com.example.app"
copyright = "Copyright 2024 Example Inc."
category = "Productivity"

[bundle.macos]
signing_identity = "Developer ID Application: Your Name (TEAMID)"
notarization_apple_id = "your@email.com"
entitlements = "entitlements.plist"
```

---

## Auto-Updates

### Server Setup

Host a JSON file at your update URL:

```json
{
    "version": "1.1.0",
    "releaseDate": "2024-01-15",
    "releaseNotes": "Bug fixes and improvements",
    "downloadUrl": "https://example.com/releases/myapp-1.1.0.dmg",
    "signature": "base64-signature",
    "size": 52428800,
    "mandatory": false
}
```

### Client Integration

```java
import build.krema.updater.AutoUpdater;

AutoUpdater updater = new AutoUpdater(
    "https://example.com/updates/latest.json",
    "1.0.0" // Current version
);

updater
    .onUpdateAvailable(info -> {
        // Show update dialog to user
        showUpdateDialog(info.getVersion(), info.getReleaseNotes());
    })
    .onDownloadProgress(progress -> {
        // Update progress bar (0.0 to 1.0)
        updateProgressBar(progress);
    })
    .onUpdateReady(path -> {
        // Update downloaded, prompt to install
        promptInstall(path);
    })
    .onError(e -> {
        // Handle error
        log.error("Update check failed", e);
    });

// Check for updates
updater.checkForUpdates()
    .thenAccept(info -> {
        if (info != null) {
            updater.downloadUpdate(info);
        }
    });
```

---

## Best Practices

### Security

1. **Minimal Permissions**: Only request permissions you need
2. **Validate Input**: Validate all data from frontend
3. **Sanitize Paths**: Prevent path traversal attacks
4. **Use CSP**: Configure Content Security Policy

### Performance

1. **Async Commands**: Use CompletableFuture for long operations
2. **Batch Updates**: Combine multiple small updates
3. **Lazy Loading**: Load resources on demand
4. **Event Throttling**: Debounce frequent events

### User Experience

1. **Splash Screen**: Show splash for apps with long startup
2. **Progress Feedback**: Show progress for long operations
3. **Error Handling**: Display user-friendly error messages
4. **Native Feel**: Follow platform UI conventions

---

## Troubleshooting

### Common Issues

**Window doesn't appear:**
Ensure the webview window isn't hidden or off-screen. Check console for native library loading errors.

**Linux: missing system libraries:**
```bash
# If you see errors about missing gtk or webkit2gtk symbols:
sudo apt install libwebkit2gtk-4.1-0 libgtk-3-0
```

**Linux: window positioning ignored (Wayland):**
Window positioning via `setPosition()` is a no-op on Wayland by design.
`gtk_window_move()` is only supported on X11. Use `center()` or let the window manager handle placement.

**Commands not found:**
```java
// Ensure commands are registered
.commands(new MyCommands())
```

**Permission denied:**
```toml
# Add required permissions to krema.toml
[permissions]
allow = ["fs:read", "fs:write"]
```

**Frontend can't connect:**
```bash
# Check dev server is running
curl http://localhost:5173
```

### Debug Mode

Enable debug mode to access developer tools:

```java
Crema.app()
    .debug(true) // or .debug()
    .run();
```

Then right-click in the webview to open inspector.

---

## Next Steps

- [Plugin Development Guide](./plugins.md) - Create custom plugins
- [Migration Guide](./migration.md) - Migrate from Electron/Tauri
- [API Reference](./api.md) - Complete API documentation
- [Examples](https://github.com/ApokalypsixDev/krema/tree/main/examples) - Sample applications
