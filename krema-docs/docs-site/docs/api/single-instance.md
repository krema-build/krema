---
sidebar_position: 6
title: Single Instance API
description: Single instance lock and second instance handling
---

# Single Instance API

The Single Instance API ensures only one instance of your application runs at a time. When a second instance is launched, it can pass arguments to the primary instance and optionally trigger actions like focusing the window.

## How It Works

1. When your app starts, call `instance:requestLock` to try to become the primary instance
2. If successful, your app is the primary instance and will receive events when other instances try to launch
3. If unsuccessful, another instance is already running - you can notify it and exit

## Commands

### instance:requestLock

Attempts to acquire the single instance lock.

```javascript
const isPrimary = await window.krema.invoke('instance:requestLock');

if (!isPrimary) {
  // Another instance is running
  console.log('App is already running');
  await window.krema.invoke('window:close');
}
```

**Returns:** `true` if this is the primary instance, `false` if another instance already holds the lock.

### instance:releaseLock

Releases the single instance lock. Typically called on shutdown.

```javascript
await window.krema.invoke('instance:releaseLock');
```

### instance:isPrimary

Checks if this instance is the primary instance.

```javascript
const isPrimary = await window.krema.invoke('instance:isPrimary');
```

### instance:focusWindow

Emits an event to request focusing the window. Typically used by the primary instance when notified of a second instance.

```javascript
await window.krema.invoke('instance:focusWindow');
```

## Events

### app:second-instance

Emitted on the primary instance when another instance tries to launch.

```javascript
window.krema.on('app:second-instance', (data) => {
  console.log('Second instance launched with args:', data.args);

  // Focus our window
  window.krema.invoke('window:focus');
  window.krema.invoke('window:show');
});
```

**Event data:**
- `args` (string[]): Command line arguments from the second instance

### app:focus-requested

Emitted when `instance:focusWindow` is called.

```javascript
window.krema.on('app:focus-requested', () => {
  window.krema.invoke('window:focus');
  window.krema.invoke('window:show');
});
```

## TypeScript Types

```typescript
interface SecondInstanceEvent {
  args: string[];
}
```

## Examples

### Basic Single Instance

```javascript
async function initSingleInstance() {
  const isPrimary = await window.krema.invoke('instance:requestLock');

  if (!isPrimary) {
    // We're a secondary instance
    // The primary instance will receive our args via the event
    await window.krema.invoke('window:close');
    return false;
  }

  // We're the primary instance
  // Listen for other instances
  window.krema.on('app:second-instance', async (data) => {
    console.log('Another instance tried to launch:', data.args);

    // Bring our window to front
    await window.krema.invoke('window:show');
    await window.krema.invoke('window:focus');

    // Handle any arguments (e.g., file to open)
    if (data.args.length > 0) {
      handleArguments(data.args);
    }
  });

  return true;
}
```

### Open File from Second Instance

```javascript
// In main initialization
window.krema.on('app:second-instance', async (data) => {
  // Check for file arguments
  const fileArgs = data.args.filter(arg =>
    arg.endsWith('.txt') || arg.endsWith('.md')
  );

  if (fileArgs.length > 0) {
    // Open the file in a new tab/window
    await openFile(fileArgs[0]);
  }

  // Always focus when user tries to open another instance
  await window.krema.invoke('window:focus');
});

async function openFile(path) {
  const content = await window.krema.invoke('fs:readTextFile', { path });
  // Display content in your app
  editor.setValue(content);
  currentFile = path;
}
```

### Deep Link Handling

When combined with deep links, second instance events can handle URL-based app activation:

```javascript
window.krema.on('app:second-instance', async (data) => {
  // Check for deep link URLs in args
  const deepLink = data.args.find(arg => arg.startsWith('myapp://'));

  if (deepLink) {
    handleDeepLink(deepLink);
  }

  await window.krema.invoke('window:focus');
});

function handleDeepLink(url) {
  const parsed = new URL(url);

  switch (parsed.pathname) {
    case '/open':
      const fileId = parsed.searchParams.get('id');
      loadDocument(fileId);
      break;
    case '/settings':
      showSettings();
      break;
  }
}
```

## Implementation Notes

- The lock is implemented using a file lock in the app data directory
- Inter-instance communication uses a local socket on a port derived from the app ID
- The lock is automatically released when the app exits normally
- If the app crashes, the lock file may persist but the socket won't be listening, so a new instance can acquire the lock
