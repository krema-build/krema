---
sidebar_position: 10
title: Window API (Advanced)
description: Multi-window, frameless windows, and advanced window management
---

# Window API (Advanced)

Advanced window management including multi-window support, frameless windows, and inter-window communication.

## Multi-Window Support

### window:create

Creates a new window.

```javascript
const { label } = await window.krema.invoke('window:create', {
  label: 'settings',        // Optional: window identifier
  title: 'Settings',
  width: 600,
  height: 400,
  minWidth: 400,
  minHeight: 300,
  resizable: true,
  center: true,
  url: 'settings.html',     // OR html: '<html>...</html>'
  x: 100,                   // Optional: explicit position
  y: 100
});
```

### window:createChild

Creates a child window with parent relationship.

```javascript
const { label } = await window.krema.invoke('window:createChild', {
  parent: 'main',           // Parent window label
  label: 'child-1',
  title: 'Child Window',
  width: 400,
  height: 300,
  url: 'child.html'
});
```

### window:showModal

Creates a modal dialog that blocks its parent.

```javascript
const { label } = await window.krema.invoke('window:showModal', {
  parent: 'main',
  title: 'Confirm Action',
  width: 300,
  height: 150,
  html: '<html>...</html>'
});
```

### window:list

Lists all open windows.

```javascript
const { windows, count } = await window.krema.invoke('window:list');
// windows: ['main', 'settings', 'modal-123']
// count: 3
```

### window:getWindow

Gets information about a specific window.

```javascript
const info = await window.krema.invoke('window:getWindow', { label: 'settings' });
// { label: 'settings', parent: null, modal: false }
```

### window:close

Closes a specific window.

```javascript
await window.krema.invoke('window:close', { label: 'settings' });
```

## Inter-Window Communication

### window:sendTo

Sends a message to a specific window.

```javascript
await window.krema.invoke('window:sendTo', {
  label: 'settings',
  event: 'theme-changed',
  payload: { theme: 'dark' }
});
```

### window:broadcast

Broadcasts a message to all windows.

```javascript
await window.krema.invoke('window:broadcast', {
  event: 'user-logged-out',
  payload: {}
});
```

Listen for messages:

```javascript
window.krema.on('theme-changed', (data) => {
  applyTheme(data.theme);
});
```

## Frameless Windows

### window:setTitleBarStyle

Sets the title bar style for custom window chrome.

```javascript
await window.krema.invoke('window:setTitleBarStyle', {
  style: 'hidden'  // 'default' | 'hidden' | 'hiddenInset'
});
```

| Style | Description |
|-------|-------------|
| `default` | Standard system title bar |
| `hidden` | Title bar hidden, controls visible |
| `hiddenInset` | Title bar hidden, controls inset (macOS) |

### window:setTrafficLightPosition (macOS)

Repositions the traffic light buttons (close/minimize/maximize).

```javascript
await window.krema.invoke('window:setTrafficLightPosition', {
  x: 20,
  y: 20
});
```

### window:setTitlebarTransparent

Makes the title bar transparent.

```javascript
await window.krema.invoke('window:setTitlebarTransparent', {
  transparent: true
});
```

### window:setFullSizeContentView

Extends content into the title bar area.

```javascript
await window.krema.invoke('window:setFullSizeContentView', {
  extend: true
});
```

## State Queries

```javascript
// Get full window state
const state = await window.krema.invoke('window:getState');
// { x, y, width, height, minimized, maximized, fullscreen, focused, visible }

// Individual queries
const pos = await window.krema.invoke('window:getPosition');    // { x, y }
const size = await window.krema.invoke('window:getSize');       // { width, height }
const title = await window.krema.invoke('window:getTitle');
const opacity = await window.krema.invoke('window:getOpacity');

// Boolean state
const minimized = await window.krema.invoke('window:isMinimized');
const maximized = await window.krema.invoke('window:isMaximized');
const fullscreen = await window.krema.invoke('window:isFullscreen');
const focused = await window.krema.invoke('window:isFocused');
const visible = await window.krema.invoke('window:isVisible');
```

## State Modifications

```javascript
// Basic controls
await window.krema.invoke('window:minimize');
await window.krema.invoke('window:maximize');
await window.krema.invoke('window:restore');
await window.krema.invoke('window:center');
await window.krema.invoke('window:focus');
await window.krema.invoke('window:show');
await window.krema.invoke('window:hide');

// Fullscreen
await window.krema.invoke('window:setFullscreen', { fullscreen: true });
await window.krema.invoke('window:toggleFullscreen');

// Position and size
await window.krema.invoke('window:setPosition', { x: 100, y: 100 });
await window.krema.invoke('window:setSize', { width: 800, height: 600 });
await window.krema.invoke('window:setBounds', { x: 100, y: 100, width: 800, height: 600 });
await window.krema.invoke('window:setMinSize', { width: 400, height: 300 });
await window.krema.invoke('window:setMaxSize', { width: 1920, height: 1080 });

// Properties
await window.krema.invoke('window:setTitle', { title: 'New Title' });
await window.krema.invoke('window:setResizable', { resizable: false });
await window.krema.invoke('window:setAlwaysOnTop', { alwaysOnTop: true });
await window.krema.invoke('window:setOpacity', { opacity: 0.9 });
```

## Example: Settings Window

```javascript
class SettingsManager {
  async open() {
    const windows = await window.krema.invoke('window:list');

    if (windows.windows.includes('settings')) {
      // Already open, focus it
      await window.krema.invoke('window:sendTo', {
        label: 'settings',
        event: 'focus-requested',
        payload: {}
      });
      return;
    }

    // Create new settings window
    await window.krema.invoke('window:create', {
      label: 'settings',
      title: 'Settings',
      width: 600,
      height: 500,
      minWidth: 400,
      minHeight: 400,
      center: true,
      url: 'settings.html'
    });
  }

  async close() {
    await window.krema.invoke('window:close', { label: 'settings' });
  }

  async notifySettingsChanged(settings) {
    await window.krema.invoke('window:broadcast', {
      event: 'settings-updated',
      payload: settings
    });
  }
}
```

## Example: Custom Title Bar

```html
<div class="titlebar" style="-webkit-app-region: drag;">
  <div class="title">My App</div>
  <div class="controls" style="-webkit-app-region: no-drag;">
    <button onclick="minimizeWindow()">−</button>
    <button onclick="maximizeWindow()">□</button>
    <button onclick="closeWindow()">×</button>
  </div>
</div>
```

```javascript
// Initialize frameless window
await window.krema.invoke('window:setTitleBarStyle', { style: 'hidden' });
await window.krema.invoke('window:setFullSizeContentView', { extend: true });

async function minimizeWindow() {
  await window.krema.invoke('window:minimize');
}

async function maximizeWindow() {
  const maximized = await window.krema.invoke('window:isMaximized');
  if (maximized) {
    await window.krema.invoke('window:restore');
  } else {
    await window.krema.invoke('window:maximize');
  }
}

async function closeWindow() {
  await window.krema.invoke('window:close', {});
}
```
