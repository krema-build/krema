# Changelog

All notable changes to Krema will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/).

## [0.1.0] - 2026-02-06

### Added
- Core framework with system webview support (macOS, Linux, Windows)
- Java FFM (Project Panama) bindings for native webview APIs
- `@KremaCommand` annotation for type-safe frontend/backend IPC
- CLI tool (`krema init`, `krema dev`, `krema build`)
- Plugin system with 5 official plugins (SQL, WebSocket, Upload, Positioner, Autostart)
- Splash screen support with customizable options
- Native menu bar, context menu, and dock integration (macOS)
- File dialog support (open, save, select folder)
- Notification system (macOS, Linux, Windows)
- Global keyboard shortcuts
- Screen and window management APIs
- npm distribution (`npm install -g krema`)
- curl installer (`curl -fsSL https://krema.build/install.sh | bash`)
- Demo applications (React, Vue, Angular)
- Error handling with global exception handler, crash reports, and `app:error` event
- Documentation site at krema.build
