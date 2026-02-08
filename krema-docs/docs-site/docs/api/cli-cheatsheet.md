---
sidebar_position: 8
title: CLI Cheat Sheet
description: Quick reference for Krema CLI commands
---

# CLI Cheat Sheet

Quick reference for all Krema CLI commands.

## Development

### Start Development Server

```bash
krema dev
```

Starts the app in development mode with hot reload.

**Options:**
| Flag | Description |
|------|-------------|
| `--profile <name>` | Use environment profile |
| `--verbose` | Enable verbose output |
| `--no-frontend` | Skip starting frontend dev server |

**Examples:**
```bash
krema dev                          # Start dev server
krema dev --profile development    # Use dev profile
krema dev --no-frontend            # Backend only (frontend running separately)
```

## Building

### Build for Production

```bash
krema build
```

Compiles frontend and Java code for production.

**Options:**
| Flag | Description |
|------|-------------|
| `--profile <name>` | Use environment profile |
| `--verbose` | Enable verbose output |
| `--skip-frontend` | Skip frontend build |

**Examples:**
```bash
krema build                        # Full build
krema build --profile production   # Production build
krema build --skip-frontend        # Java only
```

### Create Platform Bundle

```bash
krema bundle
```

Creates a distributable application bundle (.app, .exe, .AppImage).

**Options:**
| Flag | Description |
|------|-------------|
| `--profile <name>` | Use environment profile |
| `--target <platform>` | Target platform (macos, windows, linux) |
| `--sign` | Sign the bundle (macOS/Windows) |
| `--notarize` | Notarize the bundle (macOS only) |
| `--verbose` | Enable verbose output |

**Examples:**
```bash
krema bundle                       # Bundle for current platform
krema bundle --sign                # Bundle and code sign
krema bundle --sign --notarize     # Bundle, sign, and notarize (macOS)
krema bundle --target windows      # Cross-compile for Windows
```

## Project Management

### Initialize New Project

```bash
krema init [name]
```

Creates a new Krema project with interactive setup.

**Options:**
| Flag | Description |
|------|-------------|
| `--template <name>` | Use a project template |
| `--no-git` | Skip git initialization |

**Examples:**
```bash
krema init my-app                  # Create new project
krema init my-app --template react # Use React template
krema init . --no-git              # Init in current dir, no git
```

## Update Signing

### Generate Signing Keys

```bash
krema signer generate
```

Generates Ed25519 key pair for update signing.

**Options:**
| Flag | Description |
|------|-------------|
| `--output <dir>` | Output directory (default: current dir) |
| `--force` | Overwrite existing keys |

**Output:**
- `private.key` - Keep secret, used for signing
- `public.key` - Embed in app for verification

### Sign Update Artifact

```bash
krema signer sign <file>
```

Signs a file for distribution.

**Options:**
| Flag | Description |
|------|-------------|
| `--key <path>` | Path to private key |
| `--output <path>` | Output signature file path |

**Examples:**
```bash
krema signer sign dist/app-1.0.0.dmg --key private.key
# Creates: dist/app-1.0.0.dmg.sig
```

## Environment Variables

| Variable | Description |
|----------|-------------|
| `KREMA_SIGNING_PRIVATE_KEY` | Ed25519 private key for build-time signing |
| `KREMA_LOG_LEVEL` | Log level (debug, info, warn, error) |
| `KREMA_NO_COLOR` | Disable colored output |

## Common Workflows

### Development Cycle

```bash
# Start fresh project
krema init my-app
cd my-app

# Install dependencies
npm install

# Start development
krema dev

# Build and test production
krema build

# Create distributable
krema bundle --sign
```

### Release Workflow

```bash
# Build production
krema build --profile production

# Create signed bundle
krema bundle --sign --notarize

# Sign update artifact
krema signer sign dist/MyApp-1.0.0.dmg --key ~/.keys/private.key

# Upload to release server
# (your release script here)
```

### CI/CD Pipeline

```bash
# Set signing key from secrets
export KREMA_SIGNING_PRIVATE_KEY="$SIGNING_KEY_SECRET"

# Build
krema build --profile production

# Bundle with signing (key from env)
krema bundle --sign

# Artifacts are in dist/bundle/
```

## Configuration Files

| File | Purpose |
|------|---------|
| `krema.toml` | Main configuration |
| `.env` | Environment variables (all profiles) |
| `.env.local` | Local overrides (gitignored) |
| `.env.<profile>` | Profile-specific variables |

## Exit Codes

| Code | Meaning |
|------|---------|
| `0` | Success |
| `1` | General error |
| `2` | Configuration error |
| `3` | Build error |
| `4` | Bundle error |

## Getting Help

```bash
krema --help              # General help
krema <command> --help    # Command-specific help
krema --version           # Show version
```
