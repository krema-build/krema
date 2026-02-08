---
sidebar_position: 11
title: Menu & Shortcut API
description: Application menus, context menus, and global keyboard shortcuts
---

# Menu & Shortcut API

Native menus and global keyboard shortcuts for Krema applications.

## Application Menu

### menu:setApplicationMenu

Sets the application's main menu bar.

```javascript
await window.krema.invoke('menu:setApplicationMenu', {
  menu: [
    {
      label: 'File',
      submenu: [
        { id: 'new', label: 'New', accelerator: 'CmdOrCtrl+N' },
        { id: 'open', label: 'Open...', accelerator: 'CmdOrCtrl+O' },
        { type: 'separator' },
        { id: 'save', label: 'Save', accelerator: 'CmdOrCtrl+S' },
        { type: 'separator' },
        { role: 'quit' }
      ]
    },
    {
      label: 'Edit',
      submenu: [
        { role: 'undo' },
        { role: 'redo' },
        { type: 'separator' },
        { role: 'cut' },
        { role: 'copy' },
        { role: 'paste' },
        { role: 'selectAll' }
      ]
    },
    {
      label: 'View',
      submenu: [
        { id: 'theme-light', label: 'Light Theme', type: 'radio', checked: true },
        { id: 'theme-dark', label: 'Dark Theme', type: 'radio' },
        { type: 'separator' },
        { role: 'toggleFullScreen' }
      ]
    }
  ]
});
```

### Menu Item Properties

| Property | Type | Description |
|----------|------|-------------|
| `id` | string | Unique identifier for click events |
| `label` | string | Display text |
| `type` | string | `'normal'`, `'separator'`, `'checkbox'`, `'radio'`, `'submenu'` |
| `accelerator` | string | Keyboard shortcut |
| `enabled` | boolean | Whether item is clickable (default: true) |
| `checked` | boolean | For checkbox/radio items |
| `submenu` | array | Nested menu items |
| `role` | string | Predefined system role |

### Accelerator Format

```
CmdOrCtrl+S        // Cmd on macOS, Ctrl elsewhere
Cmd+Shift+Z        // macOS only
Ctrl+Alt+Delete    // Windows/Linux
F11                // Function key
```

Modifiers: `Cmd`, `Ctrl`, `CmdOrCtrl`, `Alt`, `Option`, `Shift`, `Super`

### Predefined Roles

| Role | Action |
|------|--------|
| `about` | About dialog |
| `quit` | Quit application |
| `undo` | Undo |
| `redo` | Redo |
| `cut` | Cut selection |
| `copy` | Copy selection |
| `paste` | Paste |
| `selectAll` | Select all |
| `minimize` | Minimize window |
| `close` | Close window |
| `toggleFullScreen` | Toggle fullscreen |
| `front` | Bring all windows to front |
| `hide` | Hide app (macOS) |
| `hideOthers` | Hide other apps (macOS) |
| `unhide` | Show all (macOS) |

## Context Menus

### menu:showContextMenu

Shows a context menu at a position.

```javascript
await window.krema.invoke('menu:showContextMenu', {
  items: [
    { id: 'copy', label: 'Copy', accelerator: 'CmdOrCtrl+C' },
    { id: 'paste', label: 'Paste', accelerator: 'CmdOrCtrl+V' },
    { type: 'separator' },
    { id: 'delete', label: 'Delete', enabled: hasSelection }
  ],
  x: event.clientX,
  y: event.clientY
});
```

## Dock Menu (macOS)

### menu:setDockMenu

Sets the dock icon right-click menu.

```javascript
await window.krema.invoke('menu:setDockMenu', {
  menu: [
    { id: 'new-window', label: 'New Window' },
    { id: 'new-tab', label: 'New Tab' },
    { type: 'separator' },
    { id: 'recent-1', label: 'Recent Document 1' },
    { id: 'recent-2', label: 'Recent Document 2' }
  ]
});
```

## Menu Events

### menu:click

Listen for menu item clicks.

```javascript
window.krema.on('menu:click', (data) => {
  switch (data.id) {
    case 'new':
      createNewDocument();
      break;
    case 'open':
      openFile();
      break;
    case 'theme-light':
      setTheme('light');
      break;
    case 'theme-dark':
      setTheme('dark');
      break;
  }
});
```

### menu:updateItem

Updates a menu item's state.

```javascript
// Toggle checkbox
await window.krema.invoke('menu:updateItem', {
  id: 'auto-save',
  checked: true
});

// Disable item
await window.krema.invoke('menu:updateItem', {
  id: 'save',
  enabled: false
});
```

## Global Shortcuts

Global shortcuts work even when the app is not focused.

### shortcut:register

Registers a global shortcut.

```javascript
const success = await window.krema.invoke('shortcut:register', {
  accelerator: 'CmdOrCtrl+Shift+Space'
});
```

### shortcut:unregister

Removes a registered shortcut.

```javascript
await window.krema.invoke('shortcut:unregister', {
  accelerator: 'CmdOrCtrl+Shift+Space'
});
```

### shortcut:unregisterAll

Removes all registered shortcuts.

```javascript
await window.krema.invoke('shortcut:unregisterAll');
```

### shortcut:isRegistered

Checks if a shortcut is registered.

```javascript
const registered = await window.krema.invoke('shortcut:isRegistered', {
  accelerator: 'CmdOrCtrl+Shift+Space'
});
```

### shortcut:getAll

Lists all registered shortcuts.

```javascript
const shortcuts = await window.krema.invoke('shortcut:getAll');
// ['CmdOrCtrl+Shift+Space', 'CmdOrCtrl+Shift+V']
```

### shortcut:triggered Event

Listen for shortcut activations.

```javascript
window.krema.on('shortcut:triggered', (data) => {
  if (data.accelerator === 'CmdOrCtrl+Shift+Space') {
    toggleQuickSearch();
  }
});
```

## Examples

### Complete Application Menu

```javascript
async function setupMenu() {
  const isMac = (await window.krema.invoke('os:platform')) === 'macos';

  const appMenu = isMac ? [{
    label: 'App Name',
    submenu: [
      { role: 'about' },
      { type: 'separator' },
      { role: 'hide' },
      { role: 'hideOthers' },
      { role: 'unhide' },
      { type: 'separator' },
      { role: 'quit' }
    ]
  }] : [];

  await window.krema.invoke('menu:setApplicationMenu', {
    menu: [
      ...appMenu,
      {
        label: 'File',
        submenu: [
          { id: 'new', label: 'New', accelerator: 'CmdOrCtrl+N' },
          { id: 'open', label: 'Open...', accelerator: 'CmdOrCtrl+O' },
          { type: 'separator' },
          isMac ? { role: 'close' } : { role: 'quit' }
        ]
      },
      {
        label: 'Edit',
        submenu: [
          { role: 'undo' },
          { role: 'redo' },
          { type: 'separator' },
          { role: 'cut' },
          { role: 'copy' },
          { role: 'paste' },
          ...(isMac ? [
            { role: 'selectAll' },
            { type: 'separator' },
            {
              label: 'Speech',
              submenu: [
                { role: 'startSpeaking' },
                { role: 'stopSpeaking' }
              ]
            }
          ] : [
            { role: 'selectAll' }
          ])
        ]
      }
    ]
  });
}
```

### Context Menu on Right-Click

```javascript
document.addEventListener('contextmenu', async (e) => {
  e.preventDefault();

  const selection = window.getSelection().toString();

  await window.krema.invoke('menu:showContextMenu', {
    items: [
      { id: 'cut', label: 'Cut', accelerator: 'CmdOrCtrl+X', enabled: !!selection },
      { id: 'copy', label: 'Copy', accelerator: 'CmdOrCtrl+C', enabled: !!selection },
      { id: 'paste', label: 'Paste', accelerator: 'CmdOrCtrl+V' },
      { type: 'separator' },
      { id: 'inspect', label: 'Inspect Element' }
    ],
    x: e.clientX,
    y: e.clientY
  });
});

window.krema.on('menu:click', async ({ id }) => {
  switch (id) {
    case 'cut':
      document.execCommand('cut');
      break;
    case 'copy':
      document.execCommand('copy');
      break;
    case 'paste':
      const text = await window.krema.invoke('clipboard:readText');
      document.execCommand('insertText', false, text);
      break;
  }
});
```

### Global Quick Search

```javascript
async function setupQuickSearch() {
  // Register global shortcut
  await window.krema.invoke('shortcut:register', {
    accelerator: 'CmdOrCtrl+Shift+Space'
  });

  // Listen for activation
  window.krema.on('shortcut:triggered', async (data) => {
    if (data.accelerator === 'CmdOrCtrl+Shift+Space') {
      // Show/focus the app
      await window.krema.invoke('window:show');
      await window.krema.invoke('window:focus');

      // Focus search input
      document.getElementById('search-input').focus();
    }
  });
}

// Clean up on exit
window.addEventListener('beforeunload', async () => {
  await window.krema.invoke('shortcut:unregisterAll');
});
```

## Platform Notes

- **macOS**: Application menu appears in the system menu bar. First menu should be the app name.
- **Windows/Linux**: Menu bar appears in the window. No app-name menu needed.
- **Global shortcuts**: May conflict with system shortcuts. Test on all platforms.
- **Dock menu**: macOS only. On Windows, use system tray menu instead.
