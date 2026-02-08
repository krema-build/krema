---
sidebar_position: 6
title: Autostart Plugin
description: Launch at login management
---

# Autostart Plugin

The Autostart plugin manages launch-at-login registration across platforms.

## Installation

```xml
<dependency>
    <groupId>build.krema</groupId>
    <artifactId>krema-plugin-autostart</artifactId>
    <version>${krema.version}</version>
</dependency>
```

## Permissions

```toml
[permissions]
allow = ["autostart:manage"]
```

## Platform Support

| Platform | Implementation |
|----------|----------------|
| macOS | LaunchAgent in `~/Library/LaunchAgents/` |
| Windows | Registry key in `HKCU\...\Run` |
| Linux | Desktop entry in `~/.config/autostart/` |

## Commands

### autostart:enable

Registers the app to start at login.

```javascript
const success = await window.krema.invoke('autostart:enable');
```

### autostart:disable

Removes the app from login startup.

```javascript
const success = await window.krema.invoke('autostart:disable');
```

### autostart:isEnabled

Checks if autostart is enabled.

```javascript
const enabled = await window.krema.invoke('autostart:isEnabled');
```

## Example: Settings Toggle

```javascript
async function initAutostartSetting() {
  const toggle = document.getElementById('autostart-toggle');

  // Set initial state
  toggle.checked = await window.krema.invoke('autostart:isEnabled');

  // Handle changes
  toggle.addEventListener('change', async (e) => {
    if (e.target.checked) {
      await window.krema.invoke('autostart:enable');
    } else {
      await window.krema.invoke('autostart:disable');
    }
  });
}
```
