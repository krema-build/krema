---
sidebar_position: 1
title: Native APIs
description: Access native desktop features from your web frontend
---

# Native APIs

Krema provides a rich set of native APIs accessible from your frontend via the JavaScript bridge.

## JavaScript Bridge

Krema injects a global `window.krema` object in your frontend:

```javascript
// Invoke a backend command
const result = await window.krema.invoke('greet', { name: 'World' });

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
unsubscribe();
```

## File Dialogs

### Open File

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
```

### Save File

```java
@KremaCommand
public String saveFile() {
    return FileDialog.save()
        .title("Save file")
        .defaultName("document.txt")
        .show()
        .map(Path::toString)
        .orElse(null);
}
```

### Select Multiple Files

```java
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

### Select Folder

```java
@KremaCommand
public String selectFolder() {
    return FileDialog.folder()
        .title("Select folder")
        .show()
        .map(Path::toString)
        .orElse(null);
}
```

## Clipboard

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

## Notifications

```java
import build.krema.api.notification.Notification;

@KremaCommand
public void showNotification(String title, String body) {
    Notification.show(title, body);
}
```

## System Tray

```java
import build.krema.api.tray.SystemTray;
import build.krema.api.tray.TrayMenu;

@KremaCommand
public void setupTray() {
    SystemTray.get()
        .setIcon(iconBytes)
        .setTooltip("My App")
        .setMenu(TrayMenu.builder()
            .item("Show", () -> window.show())
            .separator()
            .item("Quit", () -> System.exit(0))
            .build())
        .show();
}
```

## Shell Commands

```java
import build.krema.api.shell.Shell;

@KremaCommand
@RequiresPermission(Permission.SHELL_OPEN)
public void openInBrowser(String url) {
    Shell.open(url);
}

@KremaCommand
@RequiresPermission(Permission.SHELL_EXECUTE)
public String runCommand(String command) {
    return Shell.execute(command).output();
}
```

## Path Utilities

```java
import build.krema.api.path.AppPaths;

@KremaCommand
public Map<String, String> getPaths() {
    return Map.of(
        "appData", AppPaths.appDataDir().toString(),
        "config", AppPaths.appConfigDir().toString(),
        "cache", AppPaths.appCacheDir().toString(),
        "home", AppPaths.homeDir().toString(),
        "desktop", AppPaths.desktopDir().toString(),
        "documents", AppPaths.documentsDir().toString(),
        "downloads", AppPaths.downloadsDir().toString()
    );
}
```

## Screen Information

```java
import build.krema.api.screen.Screen;

@KremaCommand
public List<ScreenInfo> getScreens() {
    return Screen.all().stream()
        .map(s -> new ScreenInfo(
            s.bounds(),
            s.scaleFactor(),
            s.isPrimary()
        ))
        .toList();
}

@KremaCommand
public Point getCursorPosition() {
    return Screen.cursorPosition();
}
```

## Global Shortcuts

Register system-wide keyboard shortcuts:

```java
import build.krema.api.shortcut.GlobalShortcut;

@KremaCommand
public void registerShortcut() {
    GlobalShortcut.register("Cmd+Shift+P", () -> {
        events.emit("shortcut-triggered", Map.of("key", "Cmd+Shift+P"));
    });
}

@KremaCommand
public void unregisterShortcut() {
    GlobalShortcut.unregister("Cmd+Shift+P");
}
```

## Native Menus

### Application Menu

```java
import build.krema.api.menu.Menu;
import build.krema.api.menu.MenuItem;

Menu appMenu = Menu.builder()
    .submenu("File")
        .item("New", "Cmd+N", () -> newDocument())
        .item("Open...", "Cmd+O", () -> openDocument())
        .separator()
        .item("Save", "Cmd+S", () -> saveDocument())
        .end()
    .submenu("Edit")
        .item("Undo", "Cmd+Z", () -> undo())
        .item("Redo", "Cmd+Shift+Z", () -> redo())
        .end()
    .build();

window.setMenu(appMenu);
```

### Context Menu

```java
@KremaCommand
public void showContextMenu(int x, int y) {
    Menu contextMenu = Menu.builder()
        .item("Cut", () -> cut())
        .item("Copy", () -> copy())
        .item("Paste", () -> paste())
        .build();

    contextMenu.showAt(x, y);
}
```

## Secure Storage

Store sensitive data securely using the OS keychain:

```java
import build.krema.api.storage.SecureStorage;

@KremaCommand
public void storeSecret(String key, String value) {
    SecureStorage.set(key, value);
}

@KremaCommand
public String getSecret(String key) {
    return SecureStorage.get(key).orElse(null);
}

@KremaCommand
public void deleteSecret(String key) {
    SecureStorage.delete(key);
}
```

## HTTP Client

Make HTTP requests from the backend (bypasses CORS):

```java
import build.krema.api.http.HttpClient;

@KremaCommand
public String fetchData(String url) {
    return HttpClient.fetch(url)
        .header("Authorization", "Bearer " + token)
        .get()
        .body();
}

@KremaCommand
public String postJson(String url, Map<String, Object> data) {
    return HttpClient.fetch(url)
        .header("Content-Type", "application/json")
        .body(Json.stringify(data))
        .post()
        .body();
}
```

## Events from Backend

Emit events to the frontend:

```java
import build.krema.event.EventEmitter;

public class Main {
    private static EventEmitter events;

    public static void main(String[] args) {
        Krema.app()
            .events(emitter -> events = emitter)
            .commands(new Commands())
            .run();
    }

    public static void notifyProgress(int percent) {
        events.emit("progress", Map.of("percent", percent));
    }
}
```

Listen in frontend:

```javascript
window.krema.on('progress', (data) => {
    progressBar.style.width = `${data.percent}%`;
});
```
