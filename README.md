# Krema

[![Build](https://github.com/krema-build/krema/actions/workflows/release.yml/badge.svg)](https://github.com/krema-build/krema/actions/workflows/release.yml)
[![License: BSL 1.1](https://img.shields.io/badge/License-BSL_1.1-blue.svg)](LICENSE)
[![Java 25](https://img.shields.io/badge/Java-25-orange.svg)](https://jdk.java.net/25/)
[![npm](https://img.shields.io/npm/v/krema)](https://www.npmjs.com/package/krema)

Lightweight desktop apps with system webviews using Project Panama.

Krema lets you build cross-platform desktop applications using Java and web technologies. It leverages Java's Foreign Function & Memory API (Project Panama) to interface directly with native system webviews — no bundled browser engine, no Electron overhead.

## Features

- **System Webviews** — Uses the OS-native webview (WebKit on macOS, WebView2 on Windows, WebKitGTK on Linux) for minimal resource usage
- **Project Panama FFI** — Direct native calls via Java 25's Foreign Function & Memory API, no JNI
- **Any Frontend Framework** — Works with React, Vue, Angular, or any web framework
- **Java Backend** — Write your backend logic in Java with full access to the JDK ecosystem
- **Type-Safe IPC** — Annotate methods with `@KremaCommand` for automatic frontend/backend communication
- **Plugin System** — Extend functionality with plugins (SQL, WebSocket, file upload, window positioning, autostart)
- **Native Packaging** — Build native executables with GraalVM or distribute as JARs
- **Splash Screens** — Built-in configurable splash screen support
- **Cross-Platform** — macOS (ARM64, x64), Linux (x64), Windows (x64)

## Quick Start

### Prerequisites

- Java 25 (with preview features)
- Maven 3.9+

### Install via npm

```bash
npm install -g krema
```

### Install via curl

```bash
curl -fsSL https://krema.build/install.sh | bash
```

### Create a New Project

```bash
krema init my-app --template react
cd my-app
```

### Run in Development Mode

```bash
mvn compile exec:exec -Pdev
```

## Documentation

Full documentation is available at [krema.build](https://krema.build).

## Project Structure

```
krema/                  Core framework
  krema-core/           Webview bindings, IPC, window management
  krema-cli/            Command-line interface
  krema-processor/      Annotation processor for @KremaCommand
krema-plugins/          Official plugins
  krema-plugin-sql/     SQLite database access
  krema-plugin-websocket/  WebSocket connections
  krema-plugin-upload/  File upload handling
  krema-plugin-positioner/  Window positioning
  krema-plugin-autostart/   Launch-at-login
krema-demos/            Example applications (React, Vue, Angular)
krema-docs/             Documentation site (Docusaurus)
```

## License

This project is licensed under the [Business Source License 1.1](LICENSE).

- **Change Date:** 2030-02-06
- **Change License:** Apache License, Version 2.0

After the change date, the code becomes available under the Apache 2.0 license. See the [LICENSE](LICENSE) file for full terms.
