<div align="center">

# Krema

**Build modern desktop apps with Java and your favorite web framework.**

Krema is to the Java ecosystem what [Tauri](https://tauri.app) is to Rust — lightweight, native desktop applications powered by system webviews instead of a bundled browser. Write your backend in Java, your frontend in React/Vue/Angular/Svelte, and ship small, fast, cross-platform apps.

[![Build](https://github.com/krema-build/krema/actions/workflows/release.yml/badge.svg)](https://github.com/krema-build/krema/actions/workflows/release.yml)
[![License: BSL 1.1](https://img.shields.io/badge/License-BSL_1.1-blue.svg)](LICENSE)
[![Java 25](https://img.shields.io/badge/Java-25-orange.svg)](https://jdk.java.net/25/)
[![npm](https://img.shields.io/npm/v/krema)](https://www.npmjs.com/package/krema)

[Getting Started](#quick-start) · [Documentation](https://krema.build) · [Examples](krema-demos/)

</div>

---

## Why Krema?

| | Electron | Tauri | Krema |
|---|---|---|---|
| **Backend language** | JavaScript | Rust | Java |
| **Webview** | Bundled Chromium | System | System |
| **App size** | ~150 MB | ~3 MB | ~5 MB |
| **Memory usage** | High | Low | Low |
| **Ecosystem** | npm | Cargo | Maven + npm |

Krema uses the OS-native webview (WebKit on macOS, WebView2 on Windows, WebKitGTK on Linux) and communicates with Java via [Project Panama](https://openjdk.org/projects/panama/) (Foreign Function & Memory API) — no JNI, no bundled browser, no Electron overhead. You get the full power of the JDK ecosystem for your backend logic, and complete freedom to use any web framework for the UI.

## Features

- **System Webviews** — WebKit (macOS), WebView2 (Windows), WebKitGTK (Linux). No bundled browser.
- **Project Panama FFI** — Direct native calls via Java 25's Foreign Function & Memory API. No JNI.
- **Any Frontend Framework** — React, Vue, Angular, Svelte, or plain HTML/CSS/JS.
- **Type-Safe IPC** — Annotate Java methods with `@KremaCommand` and call them from the frontend with full type safety.
- **Backend-to-Frontend Events** — Push real-time events from Java to the UI.
- **Rich Native APIs** — Window management, menus, dialogs, notifications, clipboard, system tray, global shortcuts, drag & drop, secure storage, and more.
- **Plugin System** — Extend with official plugins (SQLite, WebSocket, file upload, window positioning, autostart) or build your own.
- **Native Packaging** — Bundle as native executables with GraalVM, or ship as JARs.
- **Splash Screens** — Built-in configurable splash screens.
- **Cross-Platform** — macOS (ARM64, x64), Linux (x64), Windows (x64).

## Quick Start

### Prerequisites

- Java 25+
- Maven 3.9+

### Install the CLI

```bash
# via npm
npm install -g krema

# or via curl
curl -fsSL https://krema.build/install.sh | bash
```

### Create and Run a Project

```bash
krema init my-app --template react
cd my-app
krema dev
```

That's it. A native window opens with your React app, backed by Java.

## How It Works

Krema apps have two sides: a **Java backend** that handles business logic and native APIs, and a **web frontend** that renders the UI in a system webview. They communicate through a type-safe IPC bridge.

### 1. Define Commands in Java

Annotate any method with `@KremaCommand` to expose it to the frontend:

```java
import build.krema.core.KremaCommand;

public class Commands {

    @KremaCommand
    public String greet(String name) {
        return "Hello, " + name + "!";
    }

    @KremaCommand
    public SystemInfo systemInfo() {
        return new SystemInfo(
            System.getProperty("os.name"),
            System.getProperty("java.version"),
            Runtime.getRuntime().availableProcessors()
        );
    }

    public record SystemInfo(String os, String javaVersion, int cpus) {}
}
```

### 2. Call Them from the Frontend

```typescript
// Invoke a command and get a typed response
const greeting = await window.krema.invoke<string>('greet', { name: 'World' });

const info = await window.krema.invoke<SystemInfo>('systemInfo', {});
console.log(`Running on ${info.os} with Java ${info.javaVersion}`);
```

### 3. Wire Up the Application

```java
import build.krema.core.Krema;

public class Main {
    public static void main(String[] args) {
        Krema.app()
            .title("My App")
            .size(1024, 768)
            .commands(new Commands())
            .devUrl("http://localhost:5173")  // points to your frontend dev server
            .run();
    }
}
```

### 4. Push Events from Java to the Frontend

```java
// Java: emit events to the frontend
emitter.emit("timer-tick", Map.of("count", tickCount, "timestamp", System.currentTimeMillis()));
```

```typescript
// Frontend: listen for events
window.krema.on('timer-tick', (data) => {
  console.log(`Tick #${data.count}`);
});
```

## Configuration

Projects are configured with a `krema.toml` file:

```toml
[package]
name = "my-app"
version = "1.0.0"
identifier = "com.example.my-app"

[window]
title = "My App"
width = 1024
height = 768
resizable = true

[build]
frontend_command = "npm run build"
frontend_dev_command = "npm run dev"
frontend_dev_url = "http://localhost:5173"
main_class = "com.example.app.Main"

[permissions]
allow = ["clipboard:read", "clipboard:write", "notification"]
```

## Native APIs

Krema provides built-in access to native platform features, available from both Java and the frontend:

| API | Description |
|---|---|
| **Window** | Resize, move, minimize, maximize, fullscreen, frameless mode |
| **Menu** | Menu bar, context menus, dock menus |
| **Dialogs** | File open/save, folder picker, message boxes |
| **Notifications** | Native desktop notifications |
| **Clipboard** | Read/write text and images |
| **System Tray** | Tray icons with menus |
| **Global Shortcuts** | System-wide keyboard shortcuts |
| **Drag & Drop** | File drop handling |
| **Secure Storage** | OS keychain / credential storage |
| **Store** | Persistent key-value storage |
| **Shell** | Open URLs, reveal files in Finder/Explorer |
| **Screen** | Multi-monitor info and positioning |
| **HTTP Client** | Native HTTP requests |

## Plugins

Extend your app with official plugins:

```xml
<dependency>
    <groupId>build.krema</groupId>
    <artifactId>krema-plugin-sql</artifactId>
    <version>0.1.0</version>
</dependency>
```

| Plugin | Description |
|---|---|
| `krema-plugin-sql` | SQLite database access |
| `krema-plugin-websocket` | WebSocket connections |
| `krema-plugin-upload` | File upload handling |
| `krema-plugin-positioner` | Window positioning (center, corners, etc.) |
| `krema-plugin-autostart` | Launch at login |

## Project Structure

```
krema/                        Core framework
  krema-core/                 Webview bindings, IPC, window management
  krema-cli/                  Command-line interface
  krema-processor/            Annotation processor for @KremaCommand
krema-plugins/                Official plugins
  krema-plugin-sql/           SQLite database access
  krema-plugin-websocket/     WebSocket connections
  krema-plugin-upload/        File upload handling
  krema-plugin-positioner/    Window positioning
  krema-plugin-autostart/     Launch-at-login
krema-demos/                  Example applications
  krema-react/                React + TypeScript demo
  krema-vue/                  Vue demo
  krema-angular/              Angular demo
krema-docs/                   Documentation site
```

## Documentation

Full documentation is available at **[krema.build](https://krema.build)**.

## License

This project is licensed under the [Business Source License 1.1](LICENSE).

- **Change Date:** 2030-02-06
- **Change License:** Apache License, Version 2.0

After the change date, the code becomes available under the Apache 2.0 license. See the [LICENSE](LICENSE) file for full terms.
