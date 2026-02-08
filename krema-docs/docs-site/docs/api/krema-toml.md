---
sidebar_position: 3
title: krema.toml Reference
description: Complete configuration reference for krema.toml
---

# krema.toml Reference

The `krema.toml` file is the central configuration for your Krema application. This page documents all available options.

## [package]

Application metadata and identity.

```toml
[package]
name = "my-app"
version = "1.0.0"
identifier = "com.example.myapp"
description = "My Krema Application"
```

| Option | Type | Default | Description |
|--------|------|---------|-------------|
| `name` | string | `"krema-app"` | Application name (used in bundle, window title) |
| `version` | string | `"0.1.0"` | Semantic version string |
| `identifier` | string | `"com.example.app"` | Reverse-domain bundle identifier |
| `description` | string | `""` | Application description |

## [window]

Main window configuration.

```toml
[window]
title = "My App"
width = 1200
height = 800
min_width = 800
min_height = 600
resizable = true
fullscreen = false
decorations = true
```

| Option | Type | Default | Description |
|--------|------|---------|-------------|
| `title` | string | `"Krema App"` | Window title |
| `width` | int | `1024` | Initial window width in pixels |
| `height` | int | `768` | Initial window height in pixels |
| `min_width` | int | `0` | Minimum window width (0 = no limit) |
| `min_height` | int | `0` | Minimum window height (0 = no limit) |
| `resizable` | bool | `true` | Allow window resizing |
| `fullscreen` | bool | `false` | Start in fullscreen mode |
| `decorations` | bool | `true` | Show window decorations (title bar, borders) |

## [build]

Build and development settings.

```toml
[build]
frontend_command = "npm run build"
frontend_dev_command = "npm run dev"
frontend_dev_url = "http://localhost:5173"
out_dir = "dist"
java_source_dir = "src-java"
main_class = "com.example.Main"
assets_path = "assets"
```

| Option | Type | Default | Description |
|--------|------|---------|-------------|
| `frontend_command` | string | `"npm run build"` | Command to build frontend for production |
| `frontend_dev_command` | string | `"npm run dev"` | Command to start frontend dev server |
| `frontend_dev_url` | string | `"http://localhost:5173"` | URL of frontend dev server |
| `out_dir` | string | `"dist"` | Output directory for built assets |
| `java_source_dir` | string | `"src-java"` | Directory containing Java source files |
| `main_class` | string | `null` | Main class (auto-detected if not specified) |
| `assets_path` | string | `"assets"` | Path to static assets directory |

## [bundle]

Platform bundling configuration.

```toml
[bundle]
icon = "icons/icon.icns"
identifier = "com.example.myapp"
copyright = "Copyright 2024 Example Inc."
```

| Option | Type | Default | Description |
|--------|------|---------|-------------|
| `icon` | string | `null` | Path to application icon |
| `identifier` | string | `null` | Bundle identifier (overrides package.identifier) |
| `copyright` | string | `null` | Copyright notice |

### [bundle.macos]

macOS-specific bundling options.

```toml
[bundle.macos]
signing_identity = "Developer ID Application: Example Inc."
entitlements = "entitlements.plist"
notarization_apple_id = "dev@example.com"
notarization_team_id = "ABCDEF1234"
```

| Option | Type | Default | Description |
|--------|------|---------|-------------|
| `signing_identity` | string | `null` | Code signing identity for macOS |
| `entitlements` | string | `null` | Path to entitlements.plist file |
| `notarization_apple_id` | string | `null` | Apple ID for notarization |
| `notarization_team_id` | string | `null` | Team ID for notarization |

### [bundle.windows]

Windows-specific bundling options.

```toml
[bundle.windows]
signing_certificate = "cert.pfx"
timestamp_url = "http://timestamp.digicert.com"
```

| Option | Type | Default | Description |
|--------|------|---------|-------------|
| `signing_certificate` | string | `null` | Path to code signing certificate (.pfx) |
| `timestamp_url` | string | `"http://timestamp.digicert.com"` | Timestamp server URL |

## [permissions]

Permission system configuration. Controls which native APIs are accessible.

```toml
[permissions]
allow = [
    "fs:read",
    "fs:write",
    "clipboard:read",
    "clipboard:write",
    "notification",
    "dialog",
    "shell:open"
]
```

### Available Permissions

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
| `window:manage` | Multi-window management |
| `sql:read` | SQL plugin read access |
| `sql:write` | SQL plugin write access |
| `websocket:connect` | WebSocket connections |
| `upload:send` | File uploads |
| `autostart:manage` | Autostart management |

## [updater]

Auto-update configuration.

```toml
[updater]
pubkey = "dW50cnVzdGVkIGNvbW1lbnQ6IG1pbmlzaWduIHB1YmxpYyBrZXkK..."
endpoints = [
    "https://releases.example.com/latest.json"
]
check_on_startup = true
timeout = 30
```

| Option | Type | Default | Description |
|--------|------|---------|-------------|
| `pubkey` | string | `null` | Ed25519 public key for verifying updates |
| `endpoints` | string[] | `null` | URLs to check for update manifests |
| `check_on_startup` | bool | `true` | Automatically check for updates on launch |
| `timeout` | int | `30` | HTTP timeout in seconds |

See the [Auto Updates guide](/docs/guides/auto-updates) for details on setting up updates.

## [deep-link]

Deep link / custom URL scheme configuration.

```toml
[deep-link]
schemes = ["myapp", "myapp-dev"]
```

| Option | Type | Default | Description |
|--------|------|---------|-------------|
| `schemes` | string[] | `null` | Custom URL schemes to register (e.g., `myapp://`) |

## [splash]

Splash screen configuration.

```toml
[splash]
enabled = true
image = "splash.png"
width = 480
height = 320
show_progress = true
```

| Option | Type | Default | Description |
|--------|------|---------|-------------|
| `enabled` | bool | `false` | Show splash screen during startup |
| `image` | string | `null` | Path to splash screen image |
| `width` | int | `480` | Splash window width |
| `height` | int | `320` | Splash window height |
| `show_progress` | bool | `false` | Show loading progress indicator |

## [env.*]

Environment-specific configuration overrides. Profiles are selected via the `--profile` CLI flag.

```toml
[env.development]
[env.development.build]
frontend_dev_url = "http://localhost:3000"

[env.development.window]
title = "My App (Dev)"

[env.staging]
[env.staging.updater]
endpoints = ["https://staging.example.com/updates.json"]

[env.production]
[env.production.updater]
endpoints = ["https://releases.example.com/updates.json"]
```

Environment profiles can override settings in these sections:
- `build`
- `window`
- `package`
- `updater`

### Using Profiles

```bash
# Development
krema dev --profile development

# Staging build
krema build --profile staging

# Production bundle
krema bundle --profile production
```

## Complete Example

```toml
[package]
name = "my-app"
version = "1.0.0"
identifier = "com.example.myapp"
description = "My awesome desktop application"

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
java_source_dir = "src-java"
assets_path = "assets"

[bundle]
icon = "icons/icon.icns"
identifier = "com.example.myapp"
copyright = "Copyright 2024 Example Inc."

[bundle.macos]
signing_identity = "Developer ID Application: Example Inc. (ABCDEF1234)"
notarization_apple_id = "dev@example.com"
notarization_team_id = "ABCDEF1234"

[bundle.windows]
signing_certificate = "certs/signing.pfx"

[permissions]
allow = [
    "fs:read",
    "fs:write",
    "clipboard:*",
    "notification",
    "dialog",
    "shell:open"
]

[updater]
pubkey = "your-base64-encoded-public-key"
endpoints = ["https://releases.example.com/latest.json"]
check_on_startup = true
timeout = 30

[deep-link]
schemes = ["myapp"]

[splash]
enabled = true
image = "splash.png"
width = 480
height = 320

[env.development]
[env.development.window]
title = "My App (Dev)"

[env.production]
[env.production.updater]
endpoints = ["https://releases.example.com/updates.json"]
```
