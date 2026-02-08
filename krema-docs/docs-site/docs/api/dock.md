---
sidebar_position: 5
title: Dock API
description: Dock/taskbar badge and attention API reference
---

# Dock API

The Dock API allows you to control the dock icon badge and request user attention. This is primarily used on macOS but has limited support on other platforms.

## Platform Support

| Feature | macOS | Windows | Linux |
|---------|-------|---------|-------|
| Badge text | Yes | No | Partial |
| Bounce/attention | Yes | Taskbar flash | Urgent hint |

## Commands

### dock:setBadge

Sets badge text on the dock icon.

```javascript
// Set a numeric badge
await window.krema.invoke('dock:setBadge', { text: '5' });

// Set a text badge
await window.krema.invoke('dock:setBadge', { text: '!' });

// Clear the badge
await window.krema.invoke('dock:setBadge', { text: '' });
```

### dock:getBadge

Gets the current badge text.

```javascript
const badge = await window.krema.invoke('dock:getBadge');
// "5" or "" if no badge
```

### dock:clearBadge

Clears the badge from the dock icon.

```javascript
await window.krema.invoke('dock:clearBadge');
```

### dock:bounce

Bounces the dock icon to attract user attention. Returns a request ID that can be used to cancel the bounce.

```javascript
// Informational bounce (bounces once)
const requestId = await window.krema.invoke('dock:bounce', { critical: false });

// Critical bounce (bounces continuously until app is focused)
const requestId = await window.krema.invoke('dock:bounce', { critical: true });
```

**Parameters:**
- `critical` (boolean): If `true`, bounces continuously until the app becomes active. If `false`, bounces once.

**Returns:** A request ID (number) that can be passed to `dock:cancelBounce`.

### dock:cancelBounce

Cancels a previous attention request.

```javascript
const requestId = await window.krema.invoke('dock:bounce', { critical: true });

// Later, cancel the bouncing
await window.krema.invoke('dock:cancelBounce', { id: requestId });
```

### dock:isSupported

Checks if dock badge functionality is supported on the current platform.

```javascript
const supported = await window.krema.invoke('dock:isSupported');
// true on macOS, false on most other platforms
```

## TypeScript Types

```typescript
interface DockBounceOptions {
  critical?: boolean;
}

interface DockCancelOptions {
  id: number;
}
```

## Examples

### Notification Badge

```javascript
// Track unread messages
let unreadCount = 0;

async function updateBadge() {
  if (unreadCount > 0) {
    await window.krema.invoke('dock:setBadge', {
      text: unreadCount > 99 ? '99+' : String(unreadCount)
    });
  } else {
    await window.krema.invoke('dock:clearBadge');
  }
}

function onNewMessage() {
  unreadCount++;
  updateBadge();
}

function onMessagesRead() {
  unreadCount = 0;
  updateBadge();
}
```

### Attention Request

```javascript
async function notifyUser(message, urgent = false) {
  // Show notification
  await window.krema.invoke('notification:show', {
    title: 'Alert',
    body: message
  });

  // Bounce dock icon if app is in background
  const isFocused = await window.krema.invoke('window:isFocused');
  if (!isFocused) {
    await window.krema.invoke('dock:bounce', { critical: urgent });
  }
}
```

### Download Progress Badge

```javascript
async function updateDownloadProgress(percent) {
  if (percent < 100) {
    await window.krema.invoke('dock:setBadge', {
      text: `${Math.round(percent)}%`
    });
  } else {
    await window.krema.invoke('dock:clearBadge');
    await window.krema.invoke('dock:bounce', { critical: false });
  }
}
```

## Notes

- On macOS, the badge appears as a red circle with text in the corner of the dock icon.
- Badge text should be kept short (1-3 characters work best).
- The `critical` bounce continues until the user clicks on the app, so use it sparingly.
- On Windows, `dock:bounce` triggers a taskbar button flash instead of a bounce.
- On Linux with certain desktop environments, badges may appear as overlay icons.
