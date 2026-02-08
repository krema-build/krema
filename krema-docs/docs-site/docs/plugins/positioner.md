---
sidebar_position: 5
title: Positioner Plugin
description: Semantic window positioning
---

# Positioner Plugin

The Positioner plugin provides semantic window positioning with screen-aware coordinates.

## Installation

```xml
<dependency>
    <groupId>build.krema</groupId>
    <artifactId>krema-plugin-positioner</artifactId>
    <version>${krema.version}</version>
</dependency>
```

## Permissions

```toml
[permissions]
allow = ["window:manage"]
```

## Commands

### positioner:moveTo

Moves the window to a predefined position.

```javascript
await window.krema.invoke('positioner:moveTo', { position: 'bottom-right' });
```

## Available Positions

```
┌─────────────────────────────────────┐
│  top-left    top-center   top-right │
│                                     │
│left-center     center    right-center│
│                                     │
│bottom-left bottom-center bottom-right│
└─────────────────────────────────────┘
```

| Position | Description |
|----------|-------------|
| `top-left` | Top-left corner |
| `top-center` | Top edge, centered |
| `top-right` | Top-right corner |
| `left-center` | Left edge, centered |
| `center` | Screen center |
| `right-center` | Right edge, centered |
| `bottom-left` | Bottom-left corner |
| `bottom-center` | Bottom edge, centered |
| `bottom-right` | Bottom-right corner |

## Example: Mini Player

```javascript
async function enterMiniPlayer() {
  await window.krema.invoke('window:setSize', { width: 320, height: 180 });
  await window.krema.invoke('positioner:moveTo', { position: 'bottom-right' });
  await window.krema.invoke('window:setAlwaysOnTop', { alwaysOnTop: true });
}

async function exitMiniPlayer() {
  await window.krema.invoke('window:setAlwaysOnTop', { alwaysOnTop: false });
  await window.krema.invoke('window:setSize', { width: 1024, height: 768 });
  await window.krema.invoke('positioner:moveTo', { position: 'center' });
}
```
