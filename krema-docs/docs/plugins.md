# Crema Plugin Development Guide

Learn how to extend Crema with custom plugins.

---

## Table of Contents

1. [Overview](#overview)
2. [Plugin Architecture](#plugin-architecture)
3. [Creating a Plugin](#creating-a-plugin)
4. [Plugin Manifest](#plugin-manifest)
5. [Plugin Context](#plugin-context)
6. [Adding Commands](#adding-commands)
7. [Event Handling](#event-handling)
8. [Configuration](#configuration)
9. [Permissions](#permissions)
10. [Testing](#testing)
11. [Distribution](#distribution)

---

## Overview

Crema plugins allow you to:
- Add new backend commands
- Integrate with external services
- Provide reusable functionality
- Extend the framework capabilities

### Plugin Types

| Type | Discovery | Use Case |
|------|-----------|----------|
| **Built-in** | ServiceLoader | Core framework extensions |
| **External JAR** | URLClassLoader | Third-party plugins |

---

## Plugin Architecture

```
┌─────────────────────────────────────────────────┐
│                 Crema Application               │
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

```
  ┌──────────┐     ┌────────────┐     ┌──────────┐     ┌──────────┐
  │ Discover │ ──► │   Load     │ ──► │Initialize│ ──► │  Active  │
  └──────────┘     └────────────┘     └──────────┘     └──────────┘
                                                              │
                                                              ▼
                                                       ┌──────────┐
                                                       │ Shutdown │
                                                       └──────────┘
```

---

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
│       └── build.krema.plugin.CremaPlugin
└── README.md
```

### Maven Configuration

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
                             http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <groupId>com.example</groupId>
    <artifactId>crema-plugin-example</artifactId>
    <version>1.0.0</version>
    <packaging>jar</packaging>

    <properties>
        <maven.compiler.source>20</maven.compiler.source>
        <maven.compiler.target>20</maven.compiler.target>
        <crema.version>0.1.0</crema.version>
    </properties>

    <dependencies>
        <dependency>
            <groupId>build.krema</groupId>
            <artifactId>crema</artifactId>
            <version>${crema.version}</version>
            <scope>provided</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-jar-plugin</artifactId>
                <version>3.3.0</version>
                <configuration>
                    <archive>
                        <manifestEntries>
                            <Plugin-Name>my-plugin</Plugin-Name>
                            <Plugin-Version>1.0.0</Plugin-Version>
                        </manifestEntries>
                    </archive>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
```

### Plugin Implementation

```java
package com.example.myplugin;

import build.krema.plugin.CremaPlugin;
import build.krema.plugin.PluginContext;

import java.util.List;

public class MyPlugin implements CremaPlugin {

    private PluginContext context;
    private MyCommands commands;

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
        this.commands = new MyCommands(context);

        context.getLogger().info("MyPlugin initialized");
    }

    @Override
    public void shutdown() {
        context.getLogger().info("MyPlugin shutting down");
        // Cleanup resources
    }

    @Override
    public List<Object> getCommandHandlers() {
        return List.of(commands);
    }
}
```

### ServiceLoader Registration

Create file `src/main/resources/META-INF/services/build.krema.plugin.CremaPlugin`:

```
com.example.myplugin.MyPlugin
```

---

## Plugin Manifest

### plugin.json

```json
{
    "name": "my-plugin",
    "version": "1.0.0",
    "description": "An example Crema plugin",
    "author": "Your Name",
    "homepage": "https://github.com/example/my-plugin",
    "main": "com.example.myplugin.MyPlugin",
    "permissions": [
        "fs:read",
        "notification"
    ],
    "dependencies": [],
    "cremaVersion": ">=0.1.0"
}
```

### Manifest Fields

| Field | Required | Description |
|-------|----------|-------------|
| `name` | Yes | Unique plugin identifier |
| `version` | Yes | Semantic version (x.y.z) |
| `description` | No | Short description |
| `author` | No | Author name or organization |
| `homepage` | No | Plugin website or repository |
| `main` | Yes | Fully qualified class name |
| `permissions` | No | Required permissions |
| `dependencies` | No | Other plugins this depends on |
| `cremaVersion` | No | Compatible Crema versions |

---

## Plugin Context

The `PluginContext` provides access to Crema internals:

```java
public interface PluginContext {

    /**
     * Get the window manager for multi-window support.
     */
    WindowManager getWindowManager();

    /**
     * Get the event emitter for backend→frontend events.
     */
    EventEmitter getEventEmitter();

    /**
     * Get the command registry for programmatic command registration.
     */
    CommandRegistry getCommandRegistry();

    /**
     * Get plugin-specific configuration.
     */
    <T> T getConfig(Class<T> configClass);

    /**
     * Get a logger for this plugin.
     */
    Logger getLogger();

    /**
     * Get the plugin's data directory.
     */
    Path getDataDir();

    /**
     * Get the plugin's configuration directory.
     */
    Path getConfigDir();
}
```

### Using PluginContext

```java
public class MyPlugin implements CremaPlugin {

    private PluginContext context;

    @Override
    public void initialize(PluginContext context) {
        this.context = context;

        // Access logger
        context.getLogger().info("Initializing...");

        // Access event emitter
        EventEmitter events = context.getEventEmitter();
        events.emit("my-plugin:ready", Map.of("version", getVersion()));

        // Access data directory
        Path dataDir = context.getDataDir();
        // ~/.crema/plugins/my-plugin/data/

        // Load configuration
        MyPluginConfig config = context.getConfig(MyPluginConfig.class);
    }
}
```

---

## Adding Commands

### Using @CremaCommand

```java
package com.example.myplugin;

import build.krema.command.CremaCommand;
import build.krema.plugin.PluginContext;

public class MyCommands {

    private final PluginContext context;

    public MyCommands(PluginContext context) {
        this.context = context;
    }

    @CremaCommand("my-plugin:greet")
    public String greet(String name) {
        context.getLogger().debug("Greeting: " + name);
        return "Hello from MyPlugin, " + name + "!";
    }

    @CremaCommand("my-plugin:getData")
    public MyData getData(String id) {
        return new MyData(id, "Sample data");
    }

    @CremaCommand("my-plugin:process")
    public CompletableFuture<ProcessResult> processAsync(ProcessRequest request) {
        return CompletableFuture.supplyAsync(() -> {
            // Long-running operation
            return new ProcessResult(true, "Processed: " + request.input());
        });
    }
}

public record MyData(String id, String value) {}
public record ProcessRequest(String input) {}
public record ProcessResult(boolean success, String message) {}
```

### Programmatic Registration

```java
@Override
public void initialize(PluginContext context) {
    CommandRegistry registry = context.getCommandRegistry();

    // Register a lambda handler
    registry.register("my-plugin:quick", (args) -> {
        String input = (String) args.get("input");
        return "Quick response: " + input;
    });

    // Register with explicit types
    registry.register("my-plugin:typed", TypedCommand.class, new TypedCommand());
}
```

### Command Naming Conventions

Use namespaced command names to avoid collisions:

```
plugin-name:command-name

Examples:
- my-plugin:greet
- my-plugin:getData
- my-plugin:config:get
- my-plugin:config:set
```

---

## Event Handling

### Emitting Events

```java
public class MyCommands {

    private final EventEmitter events;

    public MyCommands(PluginContext context) {
        this.events = context.getEventEmitter();
    }

    @CremaCommand("my-plugin:startProcess")
    public void startProcess(String jobId) {
        // Emit start event
        events.emit("my-plugin:process:started", Map.of("jobId", jobId));

        // Do work in background
        CompletableFuture.runAsync(() -> {
            for (int i = 0; i <= 100; i += 10) {
                events.emit("my-plugin:process:progress", Map.of(
                    "jobId", jobId,
                    "percent", i
                ));
                sleep(100);
            }

            events.emit("my-plugin:process:completed", Map.of(
                "jobId", jobId,
                "result", "Success"
            ));
        });
    }
}
```

### Listening to Events (Backend)

```java
@Override
public void initialize(PluginContext context) {
    EventEmitter events = context.getEventEmitter();

    // Listen to events from other plugins or core
    events.on("app:window:closed", (payload) -> {
        context.getLogger().info("Window closed: " + payload);
        cleanup();
    });
}
```

### Frontend Event Handling

```javascript
// Listen for plugin events
window.crema.on('my-plugin:process:started', (data) => {
    console.log('Process started:', data.jobId);
    showProgressDialog();
});

window.crema.on('my-plugin:process:progress', (data) => {
    updateProgress(data.percent);
});

window.crema.on('my-plugin:process:completed', (data) => {
    hideProgressDialog();
    showResult(data.result);
});
```

---

## Configuration

### Plugin Configuration File

Users can configure plugins in `crema.toml`:

```toml
[plugins.my-plugin]
enabled = true
api_key = "secret123"
max_connections = 5
debug = false
```

### Configuration Class

```java
public class MyPluginConfig {
    private boolean enabled = true;
    private String apiKey;
    private int maxConnections = 10;
    private boolean debug = false;

    // Getters and setters
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }

    public int getMaxConnections() { return maxConnections; }
    public void setMaxConnections(int maxConnections) {
        this.maxConnections = maxConnections;
    }

    public boolean isDebug() { return debug; }
    public void setDebug(boolean debug) { this.debug = debug; }
}
```

### Using Configuration

```java
@Override
public void initialize(PluginContext context) {
    MyPluginConfig config = context.getConfig(MyPluginConfig.class);

    if (!config.isEnabled()) {
        context.getLogger().info("Plugin disabled by configuration");
        return;
    }

    if (config.getApiKey() == null) {
        throw new PluginException("API key is required");
    }

    // Use configuration
    initializeWithConfig(config);
}
```

---

## Permissions

### Declaring Required Permissions

In `plugin.json`:

```json
{
    "permissions": [
        "fs:read",
        "fs:write",
        "notification",
        "shell:open"
    ]
}
```

### Using Permission Annotations

```java
import build.krema.security.RequiresPermission;
import build.krema.security.Permission;

public class FileCommands {

    @CremaCommand("my-plugin:readConfig")
    @RequiresPermission(Permission.FS_READ)
    public String readConfig(String path) {
        return Files.readString(Path.of(path));
    }

    @CremaCommand("my-plugin:saveConfig")
    @RequiresPermission({Permission.FS_READ, Permission.FS_WRITE})
    public void saveConfig(String path, String content) {
        Files.writeString(Path.of(path), content);
    }
}
```

### Available Permissions

| Permission | Description |
|------------|-------------|
| `fs:read` | Read files |
| `fs:write` | Write files |
| `fs:*` | Full file system access |
| `clipboard:read` | Read clipboard |
| `clipboard:write` | Write clipboard |
| `shell:execute` | Execute shell commands |
| `shell:open` | Open files/URLs |
| `notification` | Show notifications |
| `system-tray` | System tray access |
| `system-info` | Read system information |
| `network` | Network access |

---

## Testing

### Unit Testing

```java
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MyPluginTest {

    private MyPlugin plugin;
    private PluginContext mockContext;

    @BeforeEach
    void setUp() {
        plugin = new MyPlugin();
        mockContext = mock(PluginContext.class);

        when(mockContext.getLogger()).thenReturn(new TestLogger());
        when(mockContext.getDataDir()).thenReturn(Path.of("/tmp/test-plugin"));
    }

    @Test
    void shouldInitializeSuccessfully() {
        assertDoesNotThrow(() -> plugin.initialize(mockContext));
    }

    @Test
    void shouldProvideCommandHandlers() {
        plugin.initialize(mockContext);

        List<Object> handlers = plugin.getCommandHandlers();

        assertFalse(handlers.isEmpty());
    }

    @Test
    void shouldShutdownGracefully() {
        plugin.initialize(mockContext);

        assertDoesNotThrow(() -> plugin.shutdown());
    }
}
```

### Command Testing

```java
class MyCommandsTest {

    private MyCommands commands;
    private PluginContext mockContext;

    @BeforeEach
    void setUp() {
        mockContext = mock(PluginContext.class);
        when(mockContext.getLogger()).thenReturn(new TestLogger());

        commands = new MyCommands(mockContext);
    }

    @Test
    void greetShouldReturnFormattedMessage() {
        String result = commands.greet("World");

        assertEquals("Hello from MyPlugin, World!", result);
    }

    @Test
    void getDataShouldReturnValidData() {
        MyData data = commands.getData("123");

        assertNotNull(data);
        assertEquals("123", data.id());
    }
}
```

### Integration Testing

```java
@EnabledOnOs(OS.MAC)
class MyPluginIntegrationTest {

    @Test
    void shouldWorkWithRealCremaApp() throws Exception {
        // Create test app with plugin
        CountDownLatch ready = new CountDownLatch(1);

        Thread appThread = new Thread(() -> {
            Crema.app()
                .html("<html><body>Test</body></html>")
                .plugins(new MyPlugin())
                .events(e -> ready.countDown())
                .run();
        });

        appThread.start();
        assertTrue(ready.await(5, TimeUnit.SECONDS));

        // Test plugin functionality...
    }
}
```

---

## Distribution

### Building the Plugin

```bash
mvn clean package
```

This creates `target/crema-plugin-example-1.0.0.jar`.

### Installation Locations

Plugins can be installed in:

1. **Application plugins directory:**
   ```
   ~/.crema/plugins/my-plugin.jar
   ```

2. **Project-local plugins:**
   ```
   ./plugins/my-plugin.jar
   ```

3. **Classpath (built-in):**
   Included in the application JAR via ServiceLoader.

### Plugin Repository (Future)

```bash
# Install from repository (future feature)
crema plugin install my-plugin

# List installed plugins
crema plugin list

# Remove plugin
crema plugin remove my-plugin
```

### Publishing Checklist

Before publishing your plugin:

- [ ] Version follows semantic versioning
- [ ] `plugin.json` is complete and accurate
- [ ] All dependencies are declared
- [ ] Permissions are minimal and documented
- [ ] README includes installation and usage instructions
- [ ] Tests pass on all supported platforms
- [ ] No hardcoded paths or platform assumptions
- [ ] Logging uses plugin logger, not System.out

---

## Example Plugins

### Database Plugin

```java
public class DatabasePlugin implements CremaPlugin {

    private DataSource dataSource;

    @Override
    public String getName() { return "database"; }

    @Override
    public String getVersion() { return "1.0.0"; }

    @Override
    public void initialize(PluginContext context) {
        DatabaseConfig config = context.getConfig(DatabaseConfig.class);

        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setJdbcUrl(config.getUrl());
        hikariConfig.setUsername(config.getUsername());
        hikariConfig.setPassword(config.getPassword());

        this.dataSource = new HikariDataSource(hikariConfig);
    }

    @Override
    public void shutdown() {
        if (dataSource instanceof HikariDataSource hds) {
            hds.close();
        }
    }

    @Override
    public List<Object> getCommandHandlers() {
        return List.of(new DatabaseCommands(dataSource));
    }
}
```

### Analytics Plugin

```java
public class AnalyticsPlugin implements CremaPlugin {

    private AnalyticsClient client;
    private EventEmitter events;

    @Override
    public void initialize(PluginContext context) {
        AnalyticsConfig config = context.getConfig(AnalyticsConfig.class);
        this.client = new AnalyticsClient(config.getApiKey());
        this.events = context.getEventEmitter();

        // Track app events
        events.on("app:window:opened", p -> client.track("window_opened"));
        events.on("app:window:closed", p -> client.track("window_closed"));
    }

    @Override
    public List<Object> getCommandHandlers() {
        return List.of(new AnalyticsCommands(client));
    }
}
```

---

## Best Practices

### Do

- Use namespaced command and event names
- Handle errors gracefully
- Document all public APIs
- Use the provided logger
- Clean up resources in shutdown()
- Follow semantic versioning
- Test on all target platforms

### Don't

- Access internal Crema APIs directly
- Store state in static fields
- Block the main thread
- Catch and swallow exceptions
- Assume specific file paths
- Bundle unnecessary dependencies

---

## Troubleshooting

### Plugin Not Loading

```bash
# Check plugin is in correct location
ls ~/.crema/plugins/

# Check plugin.json is valid
cat my-plugin.jar | jar -xf - plugin.json

# Enable debug logging
export CREMA_LOG_LEVEL=DEBUG
```

### Commands Not Found

```java
// Ensure commands are returned from getCommandHandlers()
@Override
public List<Object> getCommandHandlers() {
    return List.of(commands); // Not empty!
}
```

### Permission Errors

```toml
# Add plugin permissions to crema.toml
[permissions]
allow = [
    "fs:read",   # Add permissions required by plugin
    "fs:write"
]
```

---

## Next Steps

- [User Guide](./guide.md) - General Crema usage
- [API Reference](./api.md) - Complete API documentation
- [Example Plugins](https://github.com/krema-build/krema/tree/main/plugins) - Reference implementations
