# Auto-Updater Guide

This guide covers how to set up automatic updates for your Crema application, including key generation, update server configuration, and frontend integration.

---

## Overview

The Crema auto-updater provides:

- **Ed25519 signature verification** for secure updates
- **Multi-platform manifest** format (Tauri-compatible) or simple single-URL format
- **Platform-specific installation** (macOS .tar.gz, Windows .exe/.msi, Linux AppImage)
- **Frontend API** for checking, downloading, and installing updates from your UI
- **Build-time signing** via environment variable for CI/CD pipelines

---

## Quick Start

### 1. Generate a signing keypair

```bash
crema signer generate
```

This creates a `crema-private.key` file and prints the public key to add to your config.

### 2. Configure `crema.toml`

```toml
[updater]
pubkey = "MCowBQYDK2VwAyEA..."
endpoints = ["https://releases.example.com/{{target}}/{{current_version}}"]
```

### 3. Use the frontend API

```typescript
const result = await crema.updater.check();
if (result.updateAvailable) {
  await crema.updater.download();
  await crema.updater.installAndRestart();
}
```

### 4. Sign your builds

```bash
export CREMA_SIGNING_PRIVATE_KEY="$(cat crema-private.key)"
crema bundle --type dmg
```

---

## Key Generation

Use the `crema signer` CLI to generate an Ed25519 keypair:

```bash
crema signer generate
```

Output:

```
[Crema Signer] Ed25519 keypair generated successfully!

Private key written to: /path/to/crema-private.key

Add this to your crema.toml:

[updater]
pubkey = "MCowBQYDK2VwAyEA..."

WARNING: Keep your private key secure!
  - Do NOT commit it to version control
  - Store it in a secure location or CI secret
  - Set CREMA_SIGNING_PRIVATE_KEY env var for CI builds
```

To write the private key to a custom location:

```bash
crema signer generate --output /path/to/my-key.pem
```

### Signing files manually

```bash
# Using a key file
crema signer sign target/bundle/macos/MyApp.dmg --key crema-private.key

# Using the environment variable
export CREMA_SIGNING_PRIVATE_KEY="$(cat crema-private.key)"
crema signer sign target/bundle/macos/MyApp.dmg
```

This creates a `.sig` file alongside the artifact (e.g. `MyApp.dmg.sig`).

---

## Configuration

Add an `[updater]` section to your `crema.toml`:

```toml
[updater]
# Required: base64-encoded Ed25519 public key for signature verification
pubkey = "MCowBQYDK2VwAyEA..."

# Required: update server endpoints (first successful response wins)
endpoints = ["https://releases.example.com/{{target}}/{{current_version}}"]

# Optional: check for updates when the app starts (default: true)
check_on_startup = true

# Optional: HTTP timeout in seconds (default: 30)
timeout = 30
```

### Endpoint URL variables

Endpoint URLs support these template variables:

| Variable | Example | Description |
|----------|---------|-------------|
| `{{target}}` | `darwin-aarch64` | OS + architecture |
| `{{arch}}` | `aarch64` | Architecture only |
| `{{current_version}}` | `1.0.0` | Current app version |

Valid target strings: `darwin-aarch64`, `darwin-x86_64`, `windows-x86_64`, `linux-x86_64`, `linux-aarch64`.

### Multiple endpoints

You can specify multiple endpoints as fallbacks. The updater tries each in order and uses the first successful response:

```toml
[updater]
pubkey = "MCowBQYDK2VwAyEA..."
endpoints = [
  "https://primary-cdn.example.com/updates/{{target}}/{{current_version}}",
  "https://fallback.example.com/updates/{{target}}/{{current_version}}"
]
```

---

## Update Server

Your update server must return a JSON manifest when a newer version is available, or `204 No Content` when the app is already up to date.

### Multi-platform manifest (recommended)

This format is compatible with the Tauri update protocol. A single endpoint returns all platform entries:

```json
{
  "version": "1.1.0",
  "notes": "Bug fixes and performance improvements",
  "pub_date": "2024-01-15T10:30:00Z",
  "platforms": {
    "darwin-aarch64": {
      "signature": "base64-ed25519-signature",
      "url": "https://cdn.example.com/releases/v1.1.0/MyApp-darwin-aarch64.tar.gz",
      "size": 52428800
    },
    "darwin-x86_64": {
      "signature": "base64-ed25519-signature",
      "url": "https://cdn.example.com/releases/v1.1.0/MyApp-darwin-x86_64.tar.gz",
      "size": 53000000
    },
    "windows-x86_64": {
      "signature": "base64-ed25519-signature",
      "url": "https://cdn.example.com/releases/v1.1.0/MyApp-Setup.exe",
      "size": 48000000
    },
    "linux-x86_64": {
      "signature": "base64-ed25519-signature",
      "url": "https://cdn.example.com/releases/v1.1.0/MyApp.AppImage",
      "size": 60000000
    }
  }
}
```

### Simple manifest (single platform)

For single-platform apps or simple setups:

```json
{
  "version": "1.1.0",
  "downloadUrl": "https://cdn.example.com/releases/v1.1.0/MyApp-Setup.exe",
  "releaseNotes": "Bug fixes and performance improvements",
  "releaseDate": "2024-01-15T10:30:00Z",
  "signature": "base64-ed25519-signature",
  "size": 48000000,
  "mandatory": false
}
```

### 204 No Content

If the app is already on the latest version, the server should return HTTP 204 with no body. The updater treats this as "no update available" (not an error).

### Static file hosting

You don't need a dynamic server. A static JSON file on any CDN works:

```
https://releases.example.com/
  darwin-aarch64/
    1.0.0     -> update.json (returns manifest for >1.0.0)
  windows-x86_64/
    1.0.0     -> update.json
```

Alternatively, serve a single manifest file and let the client resolve the correct platform entry:

```
https://releases.example.com/latest.json
```

With endpoint config:

```toml
endpoints = ["https://releases.example.com/latest.json"]
```

---

## Frontend API

The updater exposes four commands and several events to the frontend.

### Commands

#### `updater:check`

Checks for an available update.

```typescript
const result = await crema.updater.check();
// result: { updateAvailable, version, notes, date, mandatory, size }

if (result.updateAvailable) {
  console.log(`Version ${result.version} available (${result.size} bytes)`);
}
```

Returns `{ updateAvailable: false }` when no update is available or the updater is not configured.

#### `updater:download`

Downloads the available update. Emits `updater:download-progress` events during download.

```typescript
const result = await crema.updater.download();
// result: { downloaded, path }

if (result.downloaded) {
  console.log(`Update downloaded to ${result.path}`);
}
```

#### `updater:install`

Installs a previously downloaded update without restarting.

```typescript
await crema.updater.install();
```

#### `updater:installAndRestart`

Installs the update and restarts the application. Emits `updater:before-restart` before restarting to let your app save state.

```typescript
await crema.updater.installAndRestart();
```

### Events

Listen for events using `crema.on()`:

```typescript
// Update available (also emitted during check if update found)
crema.on<crema.updater.UpdateAvailableEvent>('updater:update-available', (data) => {
  console.log(`Update ${data.version} available`);
  console.log(`Notes: ${data.notes}`);
  console.log(`Mandatory: ${data.mandatory}`);
});

// Download progress (0.0 to 1.0)
crema.on<crema.updater.DownloadProgressEvent>('updater:download-progress', (data) => {
  progressBar.value = data.progress * 100;
});

// Download complete, ready to install
crema.on<crema.updater.UpdateReadyEvent>('updater:update-ready', (data) => {
  showInstallButton();
});

// Error occurred
crema.on<crema.updater.UpdateErrorEvent>('updater:error', (data) => {
  console.error('Update error:', data.message);
});

// About to restart (save state here)
crema.on('updater:before-restart', () => {
  saveAppState();
});
```

### Full example

```typescript
async function checkForUpdates() {
  try {
    const result = await crema.updater.check();
    if (!result.updateAvailable) {
      showNotification('You are up to date!');
      return;
    }

    const userConfirmed = await showUpdateDialog(
      result.version, result.notes, result.mandatory
    );
    if (!userConfirmed) return;

    // Listen for progress
    const unsub = crema.on('updater:download-progress', (data) => {
      updateProgressBar(data.progress);
    });

    await crema.updater.download();
    unsub(); // stop listening

    // Ask user to restart
    const restartNow = await showRestartDialog();
    if (restartNow) {
      await crema.updater.installAndRestart();
    } else {
      // Install will happen next time the app starts
      await crema.updater.install();
    }
  } catch (err) {
    console.error('Update failed:', err);
  }
}
```

---

## Build-Time Signing

When the `CREMA_SIGNING_PRIVATE_KEY` environment variable is set, `crema bundle` automatically signs the output artifact and creates a `.sig` file alongside it.

```bash
export CREMA_SIGNING_PRIVATE_KEY="$(cat crema-private.key)"
crema bundle --type dmg
```

This produces:

```
target/bundle/macos/MyApp.dmg
target/bundle/macos/MyApp.dmg.sig
```

Upload both files to your update server and include the signature in the manifest.

### CI/CD example (GitHub Actions)

```yaml
- name: Bundle and sign
  env:
    CREMA_SIGNING_PRIVATE_KEY: ${{ secrets.CREMA_SIGNING_PRIVATE_KEY }}
  run: crema bundle --type dmg

- name: Upload release artifacts
  uses: softprops/action-gh-release@v1
  with:
    files: |
      target/bundle/macos/*.dmg
      target/bundle/macos/*.sig
```

---

## Platform-Specific Installation

The updater handles installation differently per platform:

### macOS

| Format | Behavior |
|--------|----------|
| `.tar.gz` | Extracts to temp directory, replaces current `.app` bundle (backup + swap), restarts |
| `.dmg` | Opens the DMG for manual drag-to-Applications install |

For automatic silent updates, use `.tar.gz` artifacts.

### Windows

| Format | Behavior |
|--------|----------|
| `.exe` | Runs installer with `/SILENT /NORESTART` flags |
| `.msi` | Runs `msiexec /i <file> /passive /norestart` |

The installer handles file locking and replacement.

### Linux

| Format | Behavior |
|--------|----------|
| `.AppImage` | Replaces the current AppImage file, sets `chmod +x`, restarts |
| `.tar.gz` | Extracts and copies files over the current installation directory |

For AppImage updates, the `APPIMAGE` environment variable must be set (this happens automatically when running an AppImage).

### Restart

On all platforms, `installAndRestart` emits `updater:before-restart`, launches the new process, then calls `System.exit(0)`.

---

## Signature Verification

When `pubkey` is set in your config, the updater enforces signature verification:

| Scenario | Behavior |
|----------|----------|
| Public key set, valid signature | Update accepted |
| Public key set, invalid signature | Update rejected with `SecurityException` |
| Public key set, no signature in manifest | Update rejected |
| No public key configured | Update accepted with warning logged |

It is strongly recommended to always configure a public key for production apps.

---

## No-Op Behavior

If no `[updater]` section is present in `crema.toml`, or if `endpoints` is empty:

- The `UpdaterPlugin` initializes but is inactive
- `updater:check` returns `{ updateAvailable: false }`
- No errors are thrown at startup
- No network requests are made

---

## Troubleshooting

### "No update endpoints configured"

The `endpoints` array in your `[updater]` config is missing or empty. Add at least one endpoint URL.

### "Update signature is missing but public key is configured"

Your update manifest does not include a `signature` field, but your app has a `pubkey` configured. Either:
- Sign your artifacts with `crema signer sign` and include the signature in the manifest
- Remove the `pubkey` from your config (not recommended for production)

### "Update signature verification failed"

The signature doesn't match the downloaded file. This could mean:
- The file was corrupted during download
- The file was tampered with
- You're using the wrong public key (regenerate with `crema signer generate`)
- The signature was generated with a different private key

### "No .app bundle found" (macOS)

The `.tar.gz` update archive must contain a `.app` directory at the top level. Make sure your build process creates the archive correctly:

```bash
cd target/bundle/macos && tar czf MyApp-darwin-aarch64.tar.gz MyApp.app
```

### "Cannot determine current AppImage path" (Linux)

The `APPIMAGE` environment variable is not set. This is set automatically when running an AppImage. If you're testing outside an AppImage, set it manually:

```bash
export APPIMAGE=/path/to/MyApp.AppImage
```

---

## Configuration Reference

```toml
[updater]
# Base64-encoded Ed25519 public key.
# Generated by: crema signer generate
# Required for signature verification. Omit to skip verification (not recommended).
pubkey = "MCowBQYDK2VwAyEA..."

# Update endpoint URL templates. At least one required.
# Variables: {{target}}, {{arch}}, {{current_version}}
endpoints = ["https://releases.example.com/{{target}}/{{current_version}}"]

# Check for updates automatically when the app starts. Default: true
check_on_startup = true

# HTTP timeout in seconds for update check and download requests. Default: 30
timeout = 30
```
