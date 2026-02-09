---
sidebar_position: 1
title: Installation
description: Install Krema and its dependencies
---

# Installation

Get started with Krema by installing the required dependencies.

## Prerequisites

Before installing Krema, ensure you have the following:

- **Java 25+** (required for Foreign Function & Memory API)
- **Maven 3.8+**
- **Node.js 18+** (for frontend development)
- **macOS 11+**, **Windows 10+**, or **Linux** (with WebKitGTK)

## Platform-Specific Setup

### macOS

macOS comes with the necessary WebKit libraries built-in. Just install the development tools:

```bash
# Install Java 25 (required)
brew install openjdk@25

# Install Maven
brew install maven

# Install Node.js
brew install node
```

### Windows

Windows 10+ includes WebView2 (Edge-based) by default. If needed:

```powershell
# Install WebView2 Runtime (usually pre-installed)
winget install Microsoft.EdgeWebView2Runtime

# Install Java 25
winget install EclipseAdoptium.Temurin.25.JDK

# Install Maven
winget install Apache.Maven

# Install Node.js
winget install OpenJS.NodeJS
```

### Linux

Install the WebKitGTK runtime libraries (the webview wrapper library is bundled with Krema):

**Ubuntu/Debian:**
```bash
sudo apt install libwebkit2gtk-4.1-0 libgtk-3-0
# Optional: for system tray support
sudo apt install libayatana-appindicator3-1
```

**Fedora/RHEL:**
```bash
sudo dnf install webkit2gtk4.0 gtk3
# Optional: for system tray support
sudo dnf install libayatana-appindicator-gtk3
```

**Arch Linux:**
```bash
sudo pacman -S webkit2gtk-4.1 gtk3
# Optional: for system tray support
sudo pacman -S libayatana-appindicator
```

Then install Java 25, Maven, and Node.js using your package manager or [SDKMAN](https://sdkman.io/).

## Installing Krema CLI

Choose the installation method that works best for you:

### npm (recommended for web developers)

```bash
npm install -g @krema-build/krema
```

Or use it without installing globally:

```bash
npx @krema-build/krema init my-app --template react
```

:::tip No Java? No problem
The npm package handles Java automatically. It ships a native binary when available for your platform, falls back to a fat JAR with your existing JDK 25, or offers to install Eclipse Temurin 25 for you.
:::

### curl (macOS / Linux)

```bash
curl -fsSL https://raw.githubusercontent.com/krema-build/krema/master/packages/install.sh | bash
```

This installs krema to `~/.krema/bin/`. The script will:
1. Download a native binary if available for your platform
2. Otherwise download the fat JAR and locate (or install) Java 25
3. Create a launcher script and suggest adding it to your PATH

### From source

```bash
# Clone the Krema repository
git clone https://github.com/ApokalypsixDev/krema.git
cd krema

# Build and install the CLI
./install.sh

# Verify installation
krema --version
```

The install script checks for Java 25, builds with Maven, and creates a symlink in `/usr/local/bin`.

For manual setup:

```bash
mvn clean install -DskipTests
export PATH="$PATH:$(pwd)/bin"
```

Add the PATH export to your shell profile (`~/.bashrc`, `~/.zshrc`, etc.) for persistence.

## Verify Installation

```bash
krema --version
```

You should see the Krema version number printed.

## Next Steps

Now that Krema is installed, [create your first app](/docs/getting-started/quick-start).
