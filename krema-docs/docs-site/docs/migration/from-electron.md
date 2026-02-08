---
sidebar_position: 1
title: From Electron
description: Migrate your Electron app to Krema
---

# Migrating from Electron

Migrate your Electron application to Krema for smaller bundle sizes and lower memory usage.

## Why Migrate?

| Aspect | Electron | Krema |
|--------|----------|-------|
| Bundle Size | ~150-200MB | ~50-80MB |
| Memory Usage | ~150-300MB | ~80-100MB |
| Backend | Node.js | Java |
| WebView | Bundled Chromium | System WebView |

## Architecture Comparison

```
Electron:
┌─────────────────────────────────────────┐
│  Main Process    │  Renderer Process   │
│  (Node.js)       │  (Chromium)         │
│  - IPC           │  - HTML/CSS/JS      │
│  - Native APIs   │  - React/Vue/etc    │
├──────────────────┴─────────────────────┤
│           Bundled Chromium (~150MB)    │
└─────────────────────────────────────────┘

Krema:
┌─────────────────────────────────────────┐
│  Java Backend    │  WebView Frontend   │
│  - @KremaCommand │  - HTML/CSS/JS      │
│  - FFM APIs      │  - React/Vue/etc    │
├──────────────────┴─────────────────────┤
│     System WebView (WKWebView/WebView2)│
└─────────────────────────────────────────┘
```

## Project Structure Changes

**Electron:**
```
my-electron-app/
├── package.json
├── main.js           # Main process
├── preload.js        # Preload script
├── renderer/         # Frontend
└── node_modules/
```

**Krema:**
```
my-krema-app/
├── krema.toml        # Configuration
├── pom.xml           # Maven build
├── package.json      # Frontend only
├── src/              # Frontend
├── src-java/         # Backend
└── node_modules/
```

## Step-by-Step Migration

### 1. Create New Krema Project

```bash
krema init my-app --template react
cd my-app
```

### 2. Copy Frontend Code

Copy your renderer code to `src/`:
- `index.html`
- React/Vue/Svelte components
- Styles and assets

### 3. Convert IPC Handlers

**Electron (main.js):**
```javascript
const { ipcMain } = require('electron');
const fs = require('fs');

ipcMain.handle('read-file', async (event, filePath) => {
    return fs.readFileSync(filePath, 'utf-8');
});

ipcMain.handle('write-file', async (event, filePath, content) => {
    fs.writeFileSync(filePath, content);
});

ipcMain.handle('get-user', async (event, userId) => {
    const user = await database.findUser(userId);
    return { id: user.id, name: user.name, email: user.email };
});
```

**Krema (Commands.java):**
```java
import build.krema.command.KremaCommand;
import java.nio.file.Files;
import java.nio.file.Path;

public class Commands {

    @KremaCommand
    public String readFile(String filePath) throws IOException {
        return Files.readString(Path.of(filePath));
    }

    @KremaCommand
    public void writeFile(String filePath, String content) throws IOException {
        Files.writeString(Path.of(filePath), content);
    }

    @KremaCommand
    public UserInfo getUser(String userId) {
        User user = database.findUser(userId);
        return new UserInfo(user.getId(), user.getName(), user.getEmail());
    }
}

public record UserInfo(String id, String name, String email) {}
```

### 4. Update Frontend Calls

**Electron:**
```javascript
const content = await window.electronAPI.readFile('/path/to/file');
const user = await window.electronAPI.getUser('123');
```

**Krema:**
```javascript
const content = await window.krema.invoke('readFile', { filePath: '/path/to/file' });
const user = await window.krema.invoke('getUser', { userId: '123' });
```

### 5. Convert Events

**Electron:**
```javascript
// Main process
mainWindow.webContents.send('file-changed', { path: filePath });

// Renderer
ipcRenderer.on('file-changed', (event, data) => {
    console.log('File changed:', data.path);
});
```

**Krema:**
```java
// Java backend
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

| Electron | Krema |
|----------|-------|
| `dialog.showOpenDialog()` | `dialog:openFile` |
| `dialog.showSaveDialog()` | `dialog:saveFile` |
| `dialog.showMessageBox()` | `dialog:message` |

**Electron:**
```javascript
const { dialog } = require('electron');

const result = await dialog.showOpenDialog({
    properties: ['openFile'],
    filters: [{ name: 'Text', extensions: ['txt'] }]
});
```

**Krema:**
```javascript
const file = await window.krema.invoke('dialog:openFile', {
    filters: [{ name: 'Text', extensions: ['txt'] }]
});
```

### Clipboard APIs

| Electron | Krema |
|----------|-------|
| `clipboard.readText()` | `clipboard:readText` |
| `clipboard.writeText()` | `clipboard:writeText` |
| `clipboard.readImage()` | `clipboard:readImageBase64` |

### Shell APIs

| Electron | Krema |
|----------|-------|
| `shell.openExternal()` | `shell:open` |
| `shell.openPath()` | `shell:open` |

### Notification APIs

| Electron | Krema |
|----------|-------|
| `new Notification()` | `notification:show` |

## Configuration Mapping

**Electron (electron-builder.json):**
```json
{
  "appId": "com.example.myapp",
  "productName": "My App",
  "mac": {
    "category": "public.app-category.developer-tools",
    "identity": "Developer ID Application: ..."
  },
  "win": {
    "target": "nsis"
  }
}
```

**Krema (krema.toml):**
```toml
[package]
name = "my-app"
identifier = "com.example.myapp"

[bundle]
icon = "icons/icon.icns"

[bundle.macos]
signing_identity = "Developer ID Application: ..."

[bundle.windows]
certificate_path = "certificate.pfx"
```

## Common Gotchas

### No Node.js APIs

Krema frontend doesn't have Node.js. Use backend commands for:
- File system access
- Child processes
- Native modules

### Async by Default

All Krema commands are async. Always use `await`:
```javascript
// Won't work
const result = window.krema.invoke('readFile', { path });

// Correct
const result = await window.krema.invoke('readFile', { path });
```

### Type Conversions

Java types map to JavaScript:
- `String` → `string`
- `int/long` → `number`
- `boolean` → `boolean`
- `List<T>` → `array`
- `Map<K,V>` → `object`
- `record/class` → `object`

## Build & Distribution

**Electron:**
```bash
npm run build
electron-builder --mac --win --linux
```

**Krema:**
```bash
krema build
krema bundle --type dmg   # macOS
krema bundle --type exe   # Windows
krema bundle --type appimage  # Linux
```
