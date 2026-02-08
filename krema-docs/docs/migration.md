# Migration Guide: Electron & Tauri to Crema

Migrate your existing desktop application to Crema from Electron or Tauri.

---

## Table of Contents

1. [Overview](#overview)
2. [Comparison](#comparison)
3. [Migrating from Electron](#migrating-from-electron)
4. [Migrating from Tauri](#migrating-from-tauri)
5. [Common Patterns](#common-patterns)
6. [API Mapping](#api-mapping)
7. [Build & Distribution](#build--distribution)

---

## Overview

### Why Migrate to Crema?

| Aspect | Crema Advantage |
|--------|-----------------|
| **Bundle Size** | ~50-80MB vs Electron's ~150-200MB |
| **Memory** | ~80-100MB vs Electron's ~150-300MB |
| **Backend** | Java ecosystem (Spring, Maven, libraries) |
| **Native APIs** | Project Panama FFM for efficient native calls |
| **WebView** | System webview = always up-to-date |

### Migration Complexity

| Source | Complexity | Notes |
|--------|------------|-------|
| Electron | Medium | Rewrite Node.js backend to Java |
| Tauri | Low | Similar architecture, port Rust to Java |

---

## Comparison

### Architecture Comparison

```
Electron:
┌─────────────────────────────────────────┐
│              Electron App               │
├─────────────────────────────────────────┤
│  Main Process    │  Renderer Process   │
│  (Node.js)       │  (Chromium)         │
│  - IPC           │  - HTML/CSS/JS      │
│  - Native APIs   │  - React/Vue/etc    │
├──────────────────┴─────────────────────┤
│           Bundled Chromium             │
│              (~150MB)                  │
└─────────────────────────────────────────┘

Tauri:
┌─────────────────────────────────────────┐
│               Tauri App                 │
├─────────────────────────────────────────┤
│  Rust Backend    │  WebView Frontend   │
│  - Commands      │  - HTML/CSS/JS      │
│  - Native APIs   │  - React/Vue/etc    │
├──────────────────┴─────────────────────┤
│        System WebView (WKWebView/       │
│         WebView2/WebKitGTK)            │
└─────────────────────────────────────────┘

Crema:
┌─────────────────────────────────────────┐
│               Crema App                 │
├─────────────────────────────────────────┤
│  Java Backend    │  WebView Frontend   │
│  - @CremaCommand │  - HTML/CSS/JS      │
│  - FFM APIs      │  - React/Vue/etc    │
├──────────────────┴─────────────────────┤
│        System WebView (WKWebView/       │
│         WebView2/WebKitGTK)            │
└─────────────────────────────────────────┘
```

---

## Migrating from Electron

### Project Structure Changes

**Electron:**
```
my-electron-app/
├── package.json
├── main.js           # Main process
├── preload.js        # Preload script
├── renderer/         # Frontend
│   ├── index.html
│   └── app.js
└── node_modules/
```

**Crema:**
```
my-crema-app/
├── crema.toml        # Configuration (replaces electron-builder.json)
├── pom.xml           # Maven build
├── package.json      # Frontend only
├── src/              # Frontend
│   ├── index.html
│   └── app.js
├── src-java/         # Backend (replaces main.js)
│   └── com/example/
│       ├── Main.java
│       └── Commands.java
└── node_modules/
```

### Main Process → Java Backend

**Electron (main.js):**
```javascript
const { app, BrowserWindow, ipcMain } = require('electron');
const path = require('path');
const fs = require('fs');

let mainWindow;

function createWindow() {
    mainWindow = new BrowserWindow({
        width: 1200,
        height: 800,
        webPreferences: {
            preload: path.join(__dirname, 'preload.js'),
            nodeIntegration: false,
            contextIsolation: true
        }
    });

    mainWindow.loadFile('renderer/index.html');
}

app.whenReady().then(createWindow);

// IPC handlers
ipcMain.handle('read-file', async (event, filePath) => {
    return fs.readFileSync(filePath, 'utf-8');
});

ipcMain.handle('write-file', async (event, filePath, content) => {
    fs.writeFileSync(filePath, content);
});

ipcMain.handle('get-app-path', () => {
    return app.getPath('userData');
});
```

**Crema (Main.java):**
```java
package com.example.myapp;

import build.krema.Crema;

public class Main {
    public static void main(String[] args) {
        Crema.app()
            .title("My App")
            .size(1200, 800)
            .prodAssets("/web")  // or .devUrl("http://localhost:5173")
            .commands(new Commands())
            .run();
    }
}
```

**Crema (Commands.java):**
```java
package com.example.myapp;

import build.krema.command.CremaCommand;
import build.krema.api.path.AppPaths;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class Commands {

    @CremaCommand("read-file")
    public String readFile(String filePath) throws IOException {
        return Files.readString(Path.of(filePath));
    }

    @CremaCommand("write-file")
    public void writeFile(String filePath, String content) throws IOException {
        Files.writeString(Path.of(filePath), content);
    }

    @CremaCommand("get-app-path")
    public String getAppPath() {
        return AppPaths.dataDir("my-app").toString();
    }
}
```

### Preload Script → Crema Bridge

**Electron (preload.js):**
```javascript
const { contextBridge, ipcRenderer } = require('electron');

contextBridge.exposeInMainWorld('api', {
    readFile: (path) => ipcRenderer.invoke('read-file', path),
    writeFile: (path, content) => ipcRenderer.invoke('write-file', path, content),
    getAppPath: () => ipcRenderer.invoke('get-app-path'),
    onFileChanged: (callback) => {
        ipcRenderer.on('file-changed', (event, data) => callback(data));
    }
});
```

**Crema (automatic):**

Crema automatically injects the bridge. No preload script needed:

```javascript
// Frontend code - works automatically
window.crema.invoke('read-file', { filePath: '/path/to/file' });
window.crema.invoke('write-file', { filePath: '/path', content: 'data' });
window.crema.invoke('get-app-path');
window.crema.on('file-changed', (data) => console.log(data));
```

### IPC Pattern Changes

**Electron:**
```javascript
// Renderer
const result = await window.api.readFile('/path/to/file');

// Main process
ipcMain.handle('read-file', async (event, path) => {
    return fs.readFileSync(path, 'utf-8');
});
```

**Crema:**
```javascript
// Frontend
const result = await window.crema.invoke('read-file', { filePath: '/path/to/file' });
```

```java
// Backend
@CremaCommand("read-file")
public String readFile(String filePath) throws IOException {
    return Files.readString(Path.of(filePath));
}
```

### Events

**Electron:**
```javascript
// Main process
mainWindow.webContents.send('file-changed', { path: filePath });

// Renderer
window.api.onFileChanged((data) => {
    console.log('File changed:', data.path);
});
```

**Crema:**
```java
// Backend
eventEmitter.emit("file-changed", Map.of("path", filePath));
```

```javascript
// Frontend
window.crema.on('file-changed', (data) => {
    console.log('File changed:', data.path);
});
```

### Native Modules

**Electron (using native Node modules):**
```javascript
const sqlite3 = require('better-sqlite3');
const db = sqlite3('mydb.sqlite');

ipcMain.handle('query', (event, sql) => {
    return db.prepare(sql).all();
});
```

**Crema (using Java libraries):**
```java
// Add to pom.xml: org.xerial:sqlite-jdbc

import java.sql.*;

@CremaCommand("query")
public List<Map<String, Object>> query(String sql) throws SQLException {
    try (Connection conn = DriverManager.getConnection("jdbc:sqlite:mydb.sqlite");
         Statement stmt = conn.createStatement();
         ResultSet rs = stmt.executeQuery(sql)) {

        List<Map<String, Object>> results = new ArrayList<>();
        ResultSetMetaData meta = rs.getMetaData();

        while (rs.next()) {
            Map<String, Object> row = new HashMap<>();
            for (int i = 1; i <= meta.getColumnCount(); i++) {
                row.put(meta.getColumnName(i), rs.getObject(i));
            }
            results.add(row);
        }
        return results;
    }
}
```

---

## Migrating from Tauri

### Project Structure Changes

**Tauri:**
```
my-tauri-app/
├── tauri.conf.json
├── Cargo.toml
├── src-tauri/
│   └── src/
│       └── main.rs
├── package.json
└── src/              # Frontend
    ├── index.html
    └── main.js
```

**Crema:**
```
my-crema-app/
├── crema.toml        # Similar to tauri.conf.json
├── pom.xml           # Similar to Cargo.toml
├── src-java/         # Similar to src-tauri
│   └── com/example/
│       ├── Main.java
│       └── Commands.java
├── package.json
└── src/              # Frontend (unchanged!)
    ├── index.html
    └── main.js
```

### Configuration

**Tauri (tauri.conf.json):**
```json
{
    "package": {
        "productName": "My App",
        "version": "1.0.0"
    },
    "tauri": {
        "windows": [{
            "title": "My App",
            "width": 1200,
            "height": 800,
            "resizable": true
        }],
        "bundle": {
            "identifier": "com.example.myapp",
            "icon": ["icons/icon.icns"]
        }
    }
}
```

**Crema (crema.toml):**
```toml
[package]
name = "my-app"
version = "1.0.0"
identifier = "com.example.myapp"

[window]
title = "My App"
width = 1200
height = 800
resizable = true

[bundle]
identifier = "com.example.myapp"
icon = "icons/icon.icns"
```

### Commands

**Tauri (main.rs):**
```rust
#[tauri::command]
fn greet(name: &str) -> String {
    format!("Hello, {}!", name)
}

#[tauri::command]
fn read_file(path: &str) -> Result<String, String> {
    std::fs::read_to_string(path)
        .map_err(|e| e.to_string())
}

#[tauri::command]
async fn fetch_data(url: String) -> Result<String, String> {
    reqwest::get(&url)
        .await
        .map_err(|e| e.to_string())?
        .text()
        .await
        .map_err(|e| e.to_string())
}

fn main() {
    tauri::Builder::default()
        .invoke_handler(tauri::generate_handler![greet, read_file, fetch_data])
        .run(tauri::generate_context!())
        .expect("error while running tauri application");
}
```

**Crema (Commands.java):**
```java
import build.krema.command.CremaCommand;
import java.net.http.*;
import java.nio.file.*;
import java.util.concurrent.CompletableFuture;

public class Commands {

    @CremaCommand
    public String greet(String name) {
        return "Hello, " + name + "!";
    }

    @CremaCommand("read_file")
    public String readFile(String path) throws IOException {
        return Files.readString(Path.of(path));
    }

    @CremaCommand("fetch_data")
    public CompletableFuture<String> fetchData(String url) {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .build();

        return client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
            .thenApply(HttpResponse::body);
    }
}
```

### Frontend API

**Tauri:**
```javascript
import { invoke } from '@tauri-apps/api/tauri';
import { emit, listen } from '@tauri-apps/api/event';

// Invoke command
const greeting = await invoke('greet', { name: 'World' });

// Listen to events
const unlisten = await listen('file-changed', (event) => {
    console.log(event.payload);
});

// Emit event
await emit('user-action', { action: 'click' });
```

**Crema:**
```javascript
// Invoke command
const greeting = await window.crema.invoke('greet', { name: 'World' });

// Listen to events
const unsubscribe = window.crema.on('file-changed', (payload) => {
    console.log(payload);
});

// Note: Frontend-to-backend events use commands in Crema
await window.crema.invoke('handle-user-action', { action: 'click' });
```

### State Management

**Tauri (using managed state):**
```rust
struct AppState {
    counter: Mutex<i32>,
}

#[tauri::command]
fn increment(state: State<AppState>) -> i32 {
    let mut counter = state.counter.lock().unwrap();
    *counter += 1;
    *counter
}
```

**Crema (using instance fields):**
```java
public class Commands {
    private final AtomicInteger counter = new AtomicInteger(0);

    @CremaCommand
    public int increment() {
        return counter.incrementAndGet();
    }
}
```

### File Dialogs

**Tauri:**
```javascript
import { open, save } from '@tauri-apps/api/dialog';

const selected = await open({
    multiple: false,
    filters: [{ name: 'Text', extensions: ['txt', 'md'] }]
});
```

**Crema:**
```javascript
const selected = await window.crema.invoke('open-file-dialog', {
    filters: [{ name: 'Text', extensions: ['txt', 'md'] }]
});
```

```java
@CremaCommand("open-file-dialog")
public String openFileDialog(List<FileFilter> filters) {
    return FileDialog.open()
        .filters(filters)
        .show()
        .map(Path::toString)
        .orElse(null);
}
```

---

## Common Patterns

### Async Operations

**Electron/Tauri Pattern:**
```javascript
// Long operations block the main thread or use async
const result = await window.api.heavyComputation(data);
```

**Crema Pattern:**
```java
@CremaCommand
public CompletableFuture<Result> heavyComputation(Data data) {
    return CompletableFuture.supplyAsync(() -> {
        // Runs on separate thread
        return performComputation(data);
    });
}
```

### Progress Updates

**Electron:**
```javascript
// Main
ipcMain.on('start-task', (event) => {
    for (let i = 0; i <= 100; i += 10) {
        event.sender.send('progress', i);
        // do work
    }
});
```

**Crema:**
```java
@CremaCommand("start-task")
public void startTask() {
    CompletableFuture.runAsync(() -> {
        for (int i = 0; i <= 100; i += 10) {
            eventEmitter.emit("progress", Map.of("percent", i));
            doWork();
        }
    });
}
```

### Error Handling

**Electron:**
```javascript
ipcMain.handle('risky-operation', async () => {
    try {
        return { success: true, data: await doSomething() };
    } catch (error) {
        return { success: false, error: error.message };
    }
});
```

**Crema:**
```java
// Exceptions are automatically converted to error responses
@CremaCommand
public Data riskyOperation() throws MyException {
    return doSomething(); // Throws? Returns { error: "message" }
}
```

---

## API Mapping

### Electron → Crema

| Electron API | Crema Equivalent |
|--------------|------------------|
| `app.getPath('userData')` | `AppPaths.dataDir(appName)` |
| `app.getPath('home')` | `AppPaths.homeDir()` |
| `app.getPath('temp')` | `AppPaths.tempDir()` |
| `shell.openExternal(url)` | `Shell.open(url)` |
| `shell.openPath(path)` | `Shell.open(path)` |
| `clipboard.readText()` | `Clipboard.readText()` |
| `clipboard.writeText(text)` | `Clipboard.writeText(text)` |
| `dialog.showOpenDialog()` | `FileDialog.open().show()` |
| `dialog.showSaveDialog()` | `FileDialog.save().show()` |
| `Notification` | `Notification.show(title, body)` |
| `Tray` | `SystemTray.create()` |
| `BrowserWindow` | `Crema.app()` / `WindowManager` |

### Tauri → Crema

| Tauri API | Crema Equivalent |
|-----------|------------------|
| `#[tauri::command]` | `@CremaCommand` |
| `tauri::Builder` | `Crema.app()` |
| `State<T>` | Instance fields on Commands class |
| `AppHandle` | `PluginContext` (in plugins) |
| `@tauri-apps/api/tauri.invoke` | `window.crema.invoke` |
| `@tauri-apps/api/event.listen` | `window.crema.on` |
| `@tauri-apps/api/event.emit` | Use command to send to backend |
| `@tauri-apps/api/dialog` | `FileDialog` via command |
| `@tauri-apps/api/clipboard` | `Clipboard` via command |
| `@tauri-apps/api/notification` | `Notification` via command |
| `@tauri-apps/api/shell` | `Shell` via command |
| `@tauri-apps/api/path` | `AppPaths` via command |

---

## Build & Distribution

### Build Commands

| Task | Electron | Tauri | Crema |
|------|----------|-------|-------|
| Dev mode | `electron .` | `tauri dev` | `crema dev` |
| Build | `electron-builder` | `tauri build` | `crema build` |
| Bundle | (in electron-builder) | (in tauri build) | `crema bundle` |

### Configuration Files

| Aspect | Electron | Tauri | Crema |
|--------|----------|-------|-------|
| App config | `package.json` | `tauri.conf.json` | `crema.toml` |
| Build config | `electron-builder.json` | `tauri.conf.json` | `crema.toml` |
| Backend deps | `package.json` | `Cargo.toml` | `pom.xml` |
| Frontend deps | `package.json` | `package.json` | `package.json` |

### Bundle Sizes (Approximate)

| App Type | Electron | Tauri | Crema |
|----------|----------|-------|-------|
| Hello World | ~150MB | ~5MB | ~50MB |
| Medium App | ~180MB | ~10MB | ~60MB |
| Complex App | ~200MB+ | ~15MB | ~80MB |

---

## Migration Checklist

### From Electron

- [ ] Create `crema.toml` from `package.json` + `electron-builder.json`
- [ ] Create `pom.xml` with Crema dependency
- [ ] Port `main.js` to `Main.java`
- [ ] Convert `ipcMain.handle()` to `@CremaCommand` methods
- [ ] Remove `preload.js` (Crema bridge is automatic)
- [ ] Update frontend to use `window.crema.invoke()`
- [ ] Replace `ipcRenderer.on()` with `window.crema.on()`
- [ ] Port native Node modules to Java libraries
- [ ] Update build scripts

### From Tauri

- [ ] Convert `tauri.conf.json` to `crema.toml`
- [ ] Convert `Cargo.toml` to `pom.xml`
- [ ] Port `main.rs` commands to Java `@CremaCommand`
- [ ] Update frontend imports (remove `@tauri-apps/api`)
- [ ] Replace `invoke()` with `window.crema.invoke()`
- [ ] Replace `listen()` with `window.crema.on()`
- [ ] Port Rust libraries to Java equivalents
- [ ] Update build scripts

---

## Getting Help

- [User Guide](./guide.md) - Complete Crema documentation
- [Plugin Guide](./plugins.md) - Extend Crema with plugins
- [API Reference](./api.md) - Detailed API documentation
- [GitHub Issues](https://github.com/krema-build/krema/issues) - Report bugs or request features
- [Discussions](https://github.com/krema-build/krema/discussions) - Ask questions
