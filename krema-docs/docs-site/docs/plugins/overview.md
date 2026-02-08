---
sidebar_position: 1
title: Plugins Overview
description: Overview of Krema's plugin system
---

# Plugins Overview

Krema uses a plugin system to extend functionality. Plugins can provide new commands, register event handlers, and access native APIs.

## Built-in Plugins

These plugins are included in krema-core:

| Plugin | Description |
|--------|-------------|
| **FsPlugin** | Filesystem read/write operations |
| **LogPlugin** | Backend logging from frontend |
| **DeepLinkPlugin** | Custom URL scheme handling |
| **UpdaterPlugin** | Auto-update functionality |

## External Plugins

These plugins are available as separate modules:

| Plugin | Module | Description |
|--------|--------|-------------|
| [SQL](/docs/plugins/sql) | `krema-plugin-sql` | SQLite database access |
| [WebSocket](/docs/plugins/websocket) | `krema-plugin-websocket` | WebSocket connections |
| [Upload](/docs/plugins/upload) | `krema-plugin-upload` | File uploads with progress |
| [Positioner](/docs/plugins/positioner) | `krema-plugin-positioner` | Semantic window positioning |
| [Autostart](/docs/plugins/autostart) | `krema-plugin-autostart` | Launch at login |

## Using Plugins

### Adding a Plugin Dependency

Add the plugin to your `pom.xml`:

```xml
<dependency>
    <groupId>build.krema</groupId>
    <artifactId>krema-plugin-sql</artifactId>
    <version>${krema.version}</version>
</dependency>
```

### Required Permissions

Each plugin may require specific permissions in `krema.toml`:

```toml
[permissions]
allow = [
    "sql:read",
    "sql:write",
    "websocket:connect",
    "upload:send",
    "autostart:manage"
]
```

### Plugin Loading

Plugins are automatically discovered via Java's ServiceLoader mechanism. No additional configuration is needed - just add the dependency.

## Creating Custom Plugins

### Plugin Interface

Implement the `KremaPlugin` interface:

```java
package com.example.myplugin;

import build.krema.plugin.KremaPlugin;
import build.krema.plugin.PluginContext;
import java.util.List;

public class MyPlugin implements KremaPlugin {

    @Override
    public String getId() {
        return "com.example.myplugin";
    }

    @Override
    public String getName() {
        return "My Plugin";
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    public void initialize(PluginContext context) {
        // Called when plugin is loaded
    }

    @Override
    public void shutdown() {
        // Called when app is shutting down
    }

    @Override
    public List<Object> getCommandHandlers() {
        return List.of(new MyCommands());
    }

    @Override
    public List<String> getRequiredPermissions() {
        return List.of("myplugin:access");
    }
}
```

### Command Handlers

Create command handlers using the `@KremaCommand` annotation:

```java
public class MyCommands {

    @KremaCommand("myplugin:doSomething")
    public String doSomething(Map<String, Object> options) {
        String input = (String) options.get("input");
        return "Processed: " + input;
    }
}
```

### Service Registration

Create `META-INF/services/build.krema.plugin.KremaPlugin`:

```
com.example.myplugin.MyPlugin
```

## Frontend Usage

Call plugin commands from JavaScript:

```javascript
// SQL Plugin
await window.krema.invoke('sql:open', { name: 'mydb' });
const users = await window.krema.invoke('sql:select', {
  name: 'mydb',
  sql: 'SELECT * FROM users WHERE active = ?',
  params: [true]
});

// Listen for plugin events
window.krema.on('websocket:message', (data) => {
  console.log('Received:', data);
});
```
