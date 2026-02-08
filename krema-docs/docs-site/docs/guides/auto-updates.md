---
sidebar_position: 6
title: Auto-Updates
description: Set up automatic updates for your Krema app
---

# Auto-Updates

Set up automatic updates for your Krema application with secure Ed25519 signature verification.

## Overview

The Krema auto-updater provides:

- **Ed25519 signature verification** for secure updates
- **Multi-platform manifest** format (Tauri-compatible)
- **Platform-specific installation** (macOS .tar.gz, Windows .exe/.msi, Linux AppImage)
- **Frontend API** for checking, downloading, and installing updates
- **Build-time signing** via environment variable for CI/CD pipelines

## Quick Start

### 1. Generate a Signing Keypair

```bash
krema signer generate
```

This creates a `krema-private.key` file and prints the public key.

### 2. Configure krema.toml

```toml
[updater]
pubkey = "MCowBQYDK2VwAyEA..."
endpoints = ["https://releases.example.com/{{target}}/{{current_version}}"]
check_on_startup = true
```

### 3. Use the Frontend API

```typescript
const result = await krema.updater.check();
if (result.updateAvailable) {
    await krema.updater.download();
    await krema.updater.installAndRestart();
}
```

### 4. Sign Your Builds

```bash
export KREMA_SIGNING_PRIVATE_KEY="$(cat krema-private.key)"
krema bundle --type dmg
```

## Key Generation

Generate an Ed25519 keypair:

```bash
krema signer generate
```

Output:
```
[Krema Signer] Ed25519 keypair generated successfully!

Private key written to: /path/to/krema-private.key

Add this to your krema.toml:

[updater]
pubkey = "MCowBQYDK2VwAyEA..."

WARNING: Keep your private key secure!
  - Do NOT commit it to version control
  - Store it in a secure location or CI secret
  - Set KREMA_SIGNING_PRIVATE_KEY env var for CI builds
```

### Sign Files Manually

```bash
# Using a key file
krema signer sign target/bundle/macos/MyApp.dmg --key krema-private.key

# Using environment variable
export KREMA_SIGNING_PRIVATE_KEY="$(cat krema-private.key)"
krema signer sign target/bundle/macos/MyApp.dmg
```

This creates a `.sig` file alongside the artifact.

## Configuration

Add an `[updater]` section to `krema.toml`:

```toml
[updater]
# Required: Ed25519 public key for signature verification
pubkey = "MCowBQYDK2VwAyEA..."

# Required: update server endpoints
endpoints = ["https://releases.example.com/{{target}}/{{current_version}}"]

# Optional: check on startup (default: true)
check_on_startup = true

# Optional: HTTP timeout in seconds (default: 30)
timeout = 30
```

### Endpoint URL Variables

| Variable | Example | Description |
|----------|---------|-------------|
| `{{target}}` | `darwin-aarch64` | OS + architecture |
| `{{arch}}` | `aarch64` | Architecture only |
| `{{current_version}}` | `1.0.0` | Current app version |

Valid targets: `darwin-aarch64`, `darwin-x86_64`, `windows-x86_64`, `linux-x86_64`, `linux-aarch64`

## Update Server

Your server must return JSON when a newer version is available, or `204 No Content` when up to date.

### Multi-Platform Manifest (Recommended)

```json
{
  "version": "1.1.0",
  "notes": "Bug fixes and improvements",
  "pub_date": "2024-01-15T10:30:00Z",
  "platforms": {
    "darwin-aarch64": {
      "signature": "base64-ed25519-signature",
      "url": "https://cdn.example.com/v1.1.0/MyApp-darwin-aarch64.tar.gz",
      "size": 52428800
    },
    "darwin-x86_64": {
      "signature": "base64-ed25519-signature",
      "url": "https://cdn.example.com/v1.1.0/MyApp-darwin-x86_64.tar.gz"
    },
    "windows-x86_64": {
      "signature": "base64-ed25519-signature",
      "url": "https://cdn.example.com/v1.1.0/MyApp-Setup.exe"
    },
    "linux-x86_64": {
      "signature": "base64-ed25519-signature",
      "url": "https://cdn.example.com/v1.1.0/MyApp.AppImage"
    }
  }
}
```

### Simple Manifest

```json
{
  "version": "1.1.0",
  "downloadUrl": "https://cdn.example.com/v1.1.0/MyApp-Setup.exe",
  "releaseNotes": "Bug fixes and improvements",
  "signature": "base64-ed25519-signature"
}
```

## Frontend API

### Check for Updates

```javascript
const result = await window.krema.invoke('updater:check');

if (result.updateAvailable) {
    console.log('New version:', result.version);
    console.log('Release notes:', result.notes);
}
```

### Download Update

```javascript
// Listen for progress events
window.krema.on('download-progress', (data) => {
    console.log(`Downloaded: ${data.percent}%`);
});

await window.krema.invoke('updater:download');
```

### Install Update

```javascript
// Restart and install
await window.krema.invoke('updater:install');
```

### Full Example

```javascript
async function checkForUpdates() {
    const result = await window.krema.invoke('updater:check');

    if (!result.updateAvailable) {
        console.log('App is up to date');
        return;
    }

    // Show update dialog
    const userWantsUpdate = await showUpdateDialog(result);

    if (!userWantsUpdate) return;

    // Download with progress
    window.krema.on('download-progress', updateProgressBar);

    try {
        await window.krema.invoke('updater:download');
        await window.krema.invoke('updater:install');
    } catch (error) {
        console.error('Update failed:', error);
    }
}
```

## CI/CD Integration

### GitHub Actions Example

```yaml
name: Build and Release

on:
  push:
    tags: ['v*']

jobs:
  build:
    runs-on: ${{ matrix.os }}
    strategy:
      matrix:
        os: [macos-latest, windows-latest, ubuntu-latest]

    steps:
      - uses: actions/checkout@v4

      - name: Set up Java
        uses: actions/setup-java@v4
        with:
          java-version: '22'
          distribution: 'temurin'

      - name: Build and sign
        env:
          KREMA_SIGNING_PRIVATE_KEY: ${{ secrets.KREMA_SIGNING_KEY }}
        run: krema bundle

      - name: Upload artifacts
        uses: actions/upload-artifact@v4
        with:
          name: bundle-${{ matrix.os }}
          path: target/bundle/*
```

## Platform-Specific Notes

### macOS

- Updates delivered as `.tar.gz` containing the `.app` bundle
- App is replaced atomically
- Gatekeeper may require notarization for unsigned apps

### Windows

- Supports `.exe` installer with install modes: `passive`, `basicUi`, `quiet`
- User may need to confirm UAC prompt

### Linux

- AppImage replacement (make executable after download)
- Deb/RPM updates require package manager integration
