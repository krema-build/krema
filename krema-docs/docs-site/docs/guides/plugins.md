---
sidebar_position: 2
title: Plugins
description: Extend Krema with custom plugins
---

# Plugin Development

Learn how to extend Krema with custom plugins.

## Overview

Krema plugins allow you to:
- Add new backend commands
- Integrate with external services
- Provide reusable functionality
- Extend the framework capabilities

### Plugin Types

| Type | Discovery | Use Case |
|------|-----------|----------|
| **Built-in** | ServiceLoader | Core framework extensions |
| **External JAR** | URLClassLoader | Third-party plugins |

## Plugin Architecture

```
┌─────────────────────────────────────────────────┐
│                 Krema Application               │
├─────────────────────────────────────────────────┤
│  ┌─────────────┐  ┌─────────────┐  ┌─────────┐ │
│  │   Plugin A  │  │   Plugin B  │  │  ...    │ │
│  └──────┬──────┘  └──────┬──────┘  └────┬────┘ │
│         │                │               │      │
│         └────────────────┼───────────────┘      │
│                          │                      │
│                  ┌───────▼───────┐              │
│                  │ PluginLoader  │              │
│                  └───────┬───────┘              │
│                          │                      │
│                  ┌───────▼───────┐              │
│                  │PluginContext  │              │
│                  │ - Commands    │              │
│                  │ - Events      │              │
│                  │ - Config      │              │
│                  └───────────────┘              │
└─────────────────────────────────────────────────┘
```

### Plugin Lifecycle

1. **Discover** - Plugin JAR is found
2. **Load** - Classes are loaded via URLClassLoader
3. **Initialize** - `initialize(PluginContext)` is called
4. **Active** - Plugin is running and handling commands
5. **Shutdown** - `shutdown()` is called on app exit

## Creating a Plugin

### Project Structure

```
my-plugin/
├── pom.xml
├── src/main/java/
│   └── com/example/myplugin/
│       ├── MyPlugin.java
│       └── MyCommands.java
├── src/main/resources/
│   ├── plugin.json
│   └── META-INF/services/
│       └── build.krema.plugin.KremaPlugin
└── README.md
```

### Plugin Implementation

```java
package com.example.myplugin;

import build.krema.plugin.KremaPlugin;
import build.krema.plugin.PluginContext;

public class MyPlugin implements KremaPlugin {

    private PluginContext context;

    @Override
    public String getName() {
        return "my-plugin";
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    public void initialize(PluginContext context) {
        this.context = context;

        // Register command handlers
        context.registerCommands(new MyCommands(context));

        // Listen for events
        context.events().on("app-ready", this::onAppReady);

        System.out.println("MyPlugin initialized!");
    }

    @Override
    public void shutdown() {
        System.out.println("MyPlugin shutting down...");
    }

    private void onAppReady(Object data) {
        System.out.println("App is ready!");
    }
}
```

### Adding Commands

```java
package com.example.myplugin;

import build.krema.command.KremaCommand;
import build.krema.plugin.PluginContext;

public class MyCommands {

    private final PluginContext context;

    public MyCommands(PluginContext context) {
        this.context = context;
    }

    @KremaCommand("myPlugin:greet")
    public String greet(String name) {
        return "Hello from MyPlugin, " + name + "!";
    }

    @KremaCommand("myPlugin:getData")
    public Map<String, Object> getData() {
        return Map.of(
            "timestamp", System.currentTimeMillis(),
            "pluginName", "my-plugin",
            "version", "1.0.0"
        );
    }
}
```

## Plugin Manifest

Create `src/main/resources/plugin.json`:

```json
{
    "name": "my-plugin",
    "version": "1.0.0",
    "description": "A sample Krema plugin",
    "main": "com.example.myplugin.MyPlugin",
    "permissions": [
        "network",
        "fs:read"
    ],
    "dependencies": []
}
```

### Manifest Fields

| Field | Required | Description |
|-------|----------|-------------|
| `name` | Yes | Plugin identifier (lowercase, hyphens) |
| `version` | Yes | Semantic version |
| `description` | No | Human-readable description |
| `main` | Yes | Fully qualified class name |
| `permissions` | No | Required permissions |
| `dependencies` | No | Other plugins this depends on |

## ServiceLoader Registration

Create `META-INF/services/build.krema.plugin.KremaPlugin`:

```
com.example.myplugin.MyPlugin
```

## Plugin Context

The `PluginContext` provides access to Krema features:

```java
public interface PluginContext {
    // Access event emitter
    EventEmitter events();

    // Get plugin configuration
    Map<String, Object> config();

    // Register command handlers
    void registerCommands(Object handler);

    // Get the main window
    KremaWindow window();

    // Access secure storage
    SecureStorage secureStorage();
}
```

## Built-in Plugins

Krema includes several built-in plugins:

| Plugin | Commands | Description |
|--------|----------|-------------|
| `fs` | `fs:readFile`, `fs:writeFile`, `fs:exists` | File system operations |
| `log` | `log:info`, `log:error`, `log:debug` | Logging to file |
| `deep-link` | `deep-link:getCurrent` | Custom URL scheme handling |
| `sql` | `sql:execute`, `sql:select` | SQLite database |
| `autostart` | `autostart:enable`, `autostart:disable` | Launch at login |

## Using Plugins in Your App

### Register in Main.java

```java
import build.krema.Krema;
import com.example.myplugin.MyPlugin;

public class Main {
    public static void main(String[] args) {
        Krema.app()
            .title("My App")
            .plugin(new MyPlugin())
            .run();
    }
}
```

### Call from Frontend

```javascript
// Call plugin commands
const greeting = await window.krema.invoke('myPlugin:greet', { name: 'World' });
console.log(greeting); // "Hello from MyPlugin, World!"

const data = await window.krema.invoke('myPlugin:getData');
console.log(data.pluginName); // "my-plugin"
```

## Distribution

### Publishing

1. Build your plugin JAR:
   ```bash
   mvn clean package
   ```

2. Distribute the JAR file or publish to Maven Central

3. Users add to their project's `plugins/` directory or as a Maven dependency

### Loading External Plugins

Krema automatically discovers plugins in these locations:
- `plugins/` directory in project root
- Dependencies with `KremaPlugin` service entries
