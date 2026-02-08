---
sidebar_position: 2
title: From Tauri
description: Migrate your Tauri app to Krema
---

# Migrating from Tauri

Migrate your Tauri application to Krema. The architectures are similar, making migration straightforward.

## Why Migrate?

| Aspect | Tauri | Krema |
|--------|-------|-------|
| Backend | Rust | Java |
| Learning Curve | Steep (Rust) | Gentle (Java) |
| Ecosystem | Cargo crates | Maven/Gradle, Spring |
| Build Time | Slow (Rust compilation) | Fast |

Both use system WebViews, so bundle sizes and memory usage are comparable.

## Architecture Comparison

```
Tauri:
┌─────────────────────────────────────────┐
│  Rust Backend    │  WebView Frontend   │
│  - #[tauri::command] │  - HTML/CSS/JS  │
│  - Native APIs   │  - React/Vue/etc    │
└─────────────────────────────────────────┘

Krema:
┌─────────────────────────────────────────┐
│  Java Backend    │  WebView Frontend   │
│  - @KremaCommand │  - HTML/CSS/JS      │
│  - FFM APIs      │  - React/Vue/etc    │
└─────────────────────────────────────────┘
```

## Project Structure Changes

**Tauri:**
```
my-tauri-app/
├── src-tauri/
│   ├── Cargo.toml
│   ├── tauri.conf.json
│   └── src/
│       └── main.rs
├── src/              # Frontend
└── package.json
```

**Krema:**
```
my-krema-app/
├── src-java/
│   └── com/example/
│       ├── Main.java
│       └── Commands.java
├── krema.toml
├── pom.xml
├── src/              # Frontend (unchanged!)
└── package.json
```

## Step-by-Step Migration

### 1. Create New Krema Project

```bash
krema init my-app --template react
cd my-app
```

### 2. Copy Frontend Code

Your frontend code should work with minimal changes. Copy:
- `src/` directory
- `package.json` dependencies

### 3. Convert Rust Commands to Java

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
async fn fetch_data(url: String) -> Result<ApiResponse, String> {
    let response = reqwest::get(&url)
        .await
        .map_err(|e| e.to_string())?;
    response.json().await.map_err(|e| e.to_string())
}

#[derive(serde::Serialize)]
struct ApiResponse {
    data: Vec<Item>,
    total: i32,
}
```

**Krema (Commands.java):**
```java
import build.krema.command.KremaCommand;
import java.nio.file.Files;
import java.nio.file.Path;

public class Commands {

    @KremaCommand
    public String greet(String name) {
        return "Hello, " + name + "!";
    }

    @KremaCommand
    public String readFile(String path) throws IOException {
        return Files.readString(Path.of(path));
    }

    @KremaCommand
    public ApiResponse fetchData(String url) throws Exception {
        var response = HttpClient.fetch(url).get().body();
        return objectMapper.readValue(response, ApiResponse.class);
    }
}

public record ApiResponse(List<Item> data, int total) {}
```

### 4. Update Frontend Invocations

**Tauri:**
```javascript
import { invoke } from '@tauri-apps/api/tauri';

const greeting = await invoke('greet', { name: 'World' });
const content = await invoke('read_file', { path: '/path/to/file' });
```

**Krema:**
```javascript
const greeting = await window.krema.invoke('greet', { name: 'World' });
const content = await window.krema.invoke('readFile', { path: '/path/to/file' });
```

### 5. Convert Events

**Tauri:**
```rust
// Rust
app.emit_all("file-changed", Payload { path: file_path }).unwrap();
```

```javascript
// Frontend
import { listen } from '@tauri-apps/api/event';

await listen('file-changed', (event) => {
    console.log('File changed:', event.payload.path);
});
```

**Krema:**
```java
// Java
events.emit("file-changed", Map.of("path", filePath));
```

```javascript
// Frontend
window.krema.on('file-changed', (data) => {
    console.log('File changed:', data.path);
});
```

## API Mapping

### Dialog APIs

| Tauri | Krema |
|-------|-------|
| `open()` from `@tauri-apps/api/dialog` | `dialog:openFile` |
| `save()` from `@tauri-apps/api/dialog` | `dialog:saveFile` |
| `message()` from `@tauri-apps/api/dialog` | `dialog:message` |
| `ask()` from `@tauri-apps/api/dialog` | `dialog:confirm` |

### File System APIs

| Tauri | Krema |
|-------|-------|
| `readTextFile()` | `fs:readFile` |
| `writeTextFile()` | `fs:writeFile` |
| `exists()` | `fs:exists` |
| `createDir()` | `fs:mkdir` |

### Shell APIs

| Tauri | Krema |
|-------|-------|
| `open()` from `@tauri-apps/api/shell` | `shell:open` |
| `Command` | `shell:execute` |

### Clipboard APIs

| Tauri | Krema |
|-------|-------|
| `readText()` | `clipboard:readText` |
| `writeText()` | `clipboard:writeText` |

### Window APIs

| Tauri | Krema |
|-------|-------|
| `appWindow.setTitle()` | `window:setTitle` |
| `appWindow.setSize()` | `window:setSize` |
| `appWindow.center()` | `window:center` |
| `appWindow.minimize()` | `window:minimize` |
| `appWindow.maximize()` | `window:maximize` |
| `appWindow.close()` | `window:close` |

## Configuration Mapping

**Tauri (tauri.conf.json):**
```json
{
  "package": {
    "productName": "My App",
    "version": "1.0.0"
  },
  "tauri": {
    "bundle": {
      "identifier": "com.example.myapp",
      "icon": ["icons/icon.icns"]
    },
    "windows": [{
      "title": "My App",
      "width": 1200,
      "height": 800
    }],
    "allowlist": {
      "fs": { "readFile": true, "writeFile": true },
      "clipboard": { "readText": true, "writeText": true }
    }
  }
}
```

**Krema (krema.toml):**
```toml
[package]
name = "my-app"
version = "1.0.0"
identifier = "com.example.myapp"

[window]
title = "My App"
width = 1200
height = 800

[bundle]
icon = "icons/icon.icns"

[permissions]
allow = [
    "fs:read",
    "fs:write",
    "clipboard:read",
    "clipboard:write"
]
```

## Updater Configuration

**Tauri:**
```json
{
  "tauri": {
    "updater": {
      "active": true,
      "endpoints": ["https://releases.example.com/{{target}}/{{current_version}}"],
      "pubkey": "..."
    }
  }
}
```

**Krema:**
```toml
[updater]
pubkey = "..."
endpoints = ["https://releases.example.com/{{target}}/{{current_version}}"]
check_on_startup = true
```

Krema's updater uses the same endpoint URL format and manifest structure as Tauri, making server-side migration unnecessary.

## Key Differences

### Error Handling

**Tauri (Result types):**
```rust
#[tauri::command]
fn read_file(path: &str) -> Result<String, String> {
    std::fs::read_to_string(path).map_err(|e| e.to_string())
}
```

**Krema (Exceptions):**
```java
@KremaCommand
public String readFile(String path) throws IOException {
    return Files.readString(Path.of(path));
}
```

Exceptions are automatically converted to error responses.

### State Management

**Tauri:**
```rust
struct AppState {
    db: Mutex<Database>,
}

#[tauri::command]
fn query(state: State<AppState>) -> Vec<Item> {
    state.db.lock().unwrap().query()
}
```

**Krema:**
```java
public class Commands {
    private final Database db;

    public Commands(Database db) {
        this.db = db;
    }

    @KremaCommand
    public List<Item> query() {
        return db.query();
    }
}
```

### Plugins

Both support plugins. Tauri uses Rust crates; Krema uses Java JARs with ServiceLoader discovery.

## Build & Distribution

**Tauri:**
```bash
npm run tauri build
```

**Krema:**
```bash
krema build
krema bundle --type dmg
```

Both produce similar output:
- macOS: `.app` bundle in `.dmg`
- Windows: `.exe` installer
- Linux: AppImage
