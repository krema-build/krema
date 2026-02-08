---
sidebar_position: 3
title: Project Structure
description: Understanding your Krema project layout
---

# Project Structure

A typical Krema project has the following structure:

```
my-app/
├── krema.toml           # Krema configuration
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

## Configuration: krema.toml

The `krema.toml` file is the central configuration for your Krema app:

```toml
[package]
name = "my-app"
version = "1.0.0"
identifier = "com.example.myapp"
description = "My Krema Application"

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

## Window Options Reference

| Option | Type | Default | Description |
|--------|------|---------|-------------|
| `title` | string | "Krema App" | Window title |
| `width` | int | 1024 | Initial width |
| `height` | int | 768 | Initial height |
| `min_width` | int | 0 | Minimum width |
| `min_height` | int | 0 | Minimum height |
| `resizable` | bool | true | Allow resizing |
| `fullscreen` | bool | false | Start fullscreen |
| `decorations` | bool | true | Show title bar |
| `always_on_top` | bool | false | Always on top |
| `transparent` | bool | false | Transparent background |

## Permissions Reference

Krema uses a permission system to control access to native APIs:

| Permission | Description |
|------------|-------------|
| `fs:read` | Read files from the filesystem |
| `fs:write` | Write files to the filesystem |
| `fs:*` | Full filesystem access |
| `clipboard:read` | Read from clipboard |
| `clipboard:write` | Write to clipboard |
| `notification` | Show desktop notifications |
| `shell:execute` | Execute shell commands |
| `shell:open` | Open files/URLs with default app |
| `system-tray` | System tray access |
| `system-info` | Read system information |
| `network` | Network requests |
| `dialog` | File dialogs |

## Frontend Directory (src/)

Contains your web UI code. Krema supports any frontend framework:
- React, Vue, Svelte, Angular
- Vanilla HTML/CSS/JS
- Any build tool (Vite, Webpack, etc.)

## Java Backend (src-java/)

Contains your Java code:
- **Main.java**: Application entry point using `Krema.app()` builder
- **Commands.java**: Methods annotated with `@KremaCommand` for frontend calls
- **Plugins**: Custom plugin implementations

## Build Output (dist/)

After running `krema build`:
- Compiled frontend assets
- Compiled Java classes
- Bundled JAR file

After running `krema bundle`:
- Platform-specific bundle (.app, .exe, .AppImage)
- Installer if configured (.dmg, .msi)
