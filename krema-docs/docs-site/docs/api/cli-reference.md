---
sidebar_position: 2
title: CLI Reference
description: Krema command-line interface reference
---

# CLI Reference

The Krema CLI provides commands for creating, developing, building, and bundling applications.

## Global Options

```bash
krema [command] [options]

Options:
  -h, --help      Show help
  -V, --version   Show version
```

## Commands

### krema init

Create a new Krema project.

```bash
krema init <name> [options]
```

**Arguments:**
| Argument | Description |
|----------|-------------|
| `name` | Project name |

**Options:**
| Option | Description | Default |
|--------|-------------|---------|
| `-t, --template <template>` | Project template | `vanilla` |
| `-f, --force` | Overwrite existing directory | `false` |
| `--wizard` | Interactive wizard with full configuration | - |
| `--ci` | Non-interactive mode for CI pipelines | - |

**Templates:**
- `vanilla` - Plain HTML/CSS/JS with Vite
- `react` - React 18 with Vite
- `vue` - Vue 3 with Vite
- `svelte` - Svelte 5 with Vite
- `angular` - Angular 18

**Examples:**
```bash
# Create a React project
krema init my-app --template react

# Full interactive wizard (configure plugins, window settings, etc.)
krema init --wizard

# CI mode (no prompts)
krema init my-app --ci -t react
```

---

### krema dev

Start development mode with hot reload.

```bash
krema dev [options]
```

**Options:**
| Option | Description | Default |
|--------|-------------|---------|
| `--env <name>` | Environment profile | `development` |
| `--no-open` | Don't open the window automatically | - |
| `--port <port>` | Override frontend dev server port | - |

**What it does:**
1. Starts the frontend dev server (npm/pnpm/yarn)
2. Waits for the dev server to be ready
3. Launches the native window pointing to the dev server
4. Watches for Java source changes and reloads

**Example:**
```bash
krema dev
krema dev --env staging
krema dev --port 3000
```

---

### krema build

Build the application for production.

```bash
krema build [options]
```

**Options:**
| Option | Description | Default |
|--------|-------------|---------|
| `--env <name>` | Environment profile | `production` |
| `--native` | Build native image with GraalVM | - |
| `--skip-frontend` | Skip frontend build | - |

**What it does:**
1. Runs the frontend build command (`npm run build`)
2. Compiles Java sources
3. Copies web assets to resources
4. Creates an executable JAR (or native binary with `--native`)

With `--native`, the CLI additionally generates a GraalVM resource config, invokes `native-image`, and copies the native webview library alongside the binary. See the [Native Image guide](/docs/guides/native-image) for details.

**Example:**
```bash
krema build
krema build --env production
krema build --native
krema build --native --skip-frontend
```

---

### krema bundle

Create platform-specific application bundles.

```bash
krema bundle [options]
```

**Options:**
| Option | Description | Default |
|--------|-------------|---------|
| `--type <type>` | Bundle type | Platform default |
| `--sign` | Sign the bundle | - |
| `--notarize` | Notarize (macOS only, implies --sign) | - |
| `--env <name>` | Environment profile | `production` |

**Bundle types:**

| Platform | Types | Default |
|----------|-------|---------|
| macOS | `app`, `dmg` | `dmg` |
| Windows | `exe`, `msi` | `exe` |
| Linux | `appimage`, `deb`, `rpm` | `appimage` |

**Example:**
```bash
# macOS
krema bundle --type dmg
krema bundle --type dmg --sign
krema bundle --type dmg --notarize

# Windows
krema bundle --type exe
krema bundle --type exe --sign

# Linux
krema bundle --type appimage
krema bundle --type deb
```

---

### krema signer

Manage Ed25519 signing keys for auto-updates.

#### krema signer generate

Generate a new keypair.

```bash
krema signer generate [options]
```

**Options:**
| Option | Description | Default |
|--------|-------------|---------|
| `-o, --output <path>` | Private key output path | `krema-private.key` |

**Example:**
```bash
krema signer generate
krema signer generate --output /secure/path/key.pem
```

**Output:**
```
[Krema Signer] Ed25519 keypair generated successfully!

Private key written to: ./krema-private.key

Add this to your krema.toml:

[updater]
pubkey = "MCowBQYDK2VwAyEA..."
```

#### krema signer sign

Sign a file.

```bash
krema signer sign <file> [options]
```

**Options:**
| Option | Description | Default |
|--------|-------------|---------|
| `-k, --key <path>` | Private key file | Uses `KREMA_SIGNING_PRIVATE_KEY` env |

**Example:**
```bash
krema signer sign target/bundle/MyApp.dmg --key krema-private.key

# Using environment variable
export KREMA_SIGNING_PRIVATE_KEY="$(cat krema-private.key)"
krema signer sign target/bundle/MyApp.dmg
```

Creates a `.sig` file alongside the original (e.g., `MyApp.dmg.sig`).

---

## Environment Variables

| Variable | Description |
|----------|-------------|
| `KREMA_SIGNING_PRIVATE_KEY` | Ed25519 private key for update signing |
| `KREMA_APPLE_PASSWORD` | Apple app-specific password for notarization |
| `KREMA_CERTIFICATE_PASSWORD` | Windows certificate password |
| `GRAALVM_HOME` | GraalVM installation path for [native builds](/docs/guides/native-image) |
| `JAVA_HOME` | Java installation path (also checked for `native-image`) |

---

## Configuration File

The CLI reads configuration from `krema.toml` in the project root.

```toml
[package]
name = "my-app"
version = "1.0.0"
identifier = "com.example.myapp"

[window]
title = "My App"
width = 1200
height = 800

[build]
frontend_command = "npm run build"
frontend_dev_command = "npm run dev"
frontend_dev_url = "http://localhost:5173"
out_dir = "dist"

[bundle]
icon = "icons/icon.icns"

[bundle.macos]
signing_identity = "Developer ID Application: ..."
notarization_apple_id = "your@email.com"
notarization_team_id = "TEAMID"

[bundle.windows]
certificate_path = "path/to/certificate.pfx"

[permissions]
allow = ["fs:read", "clipboard:read", "notification"]

[updater]
pubkey = "MCowBQYDK2VwAyEA..."
endpoints = ["https://releases.example.com/{{target}}/{{current_version}}"]

[env.development]
[env.development.window]
title = "My App (Dev)"

[env.production]
[env.production.build]
frontend_command = "npm run build:prod"
```

---

## Exit Codes

| Code | Description |
|------|-------------|
| 0 | Success |
| 1 | General error |
| 2 | Configuration error |
| 3 | Build error |
| 4 | Bundle error |
| 5 | Signing error |
