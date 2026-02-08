---
sidebar_position: 4
title: OS Info API
description: Operating system information API reference
---

# OS Info API

The OS Info API provides information about the operating system, hardware, and environment.

## Commands

### os:platform

Returns the current operating system.

```javascript
const platform = await window.krema.invoke('os:platform');
// Returns: "macos" | "windows" | "linux" | "unknown"
```

### os:arch

Returns the CPU architecture.

```javascript
const arch = await window.krema.invoke('os:arch');
// Returns: "arm64" | "x64" | "x86" | "arm" | <raw-arch>
```

### os:version

Returns the OS version string.

```javascript
const version = await window.krema.invoke('os:version');
// macOS: "14.2.1"
// Windows: "10.0"
// Linux: "6.5.0-generic"
```

### os:hostname

Returns the machine's hostname.

```javascript
const hostname = await window.krema.invoke('os:hostname');
// "my-macbook.local"
```

### os:username

Returns the current user's username.

```javascript
const username = await window.krema.invoke('os:username');
// "john"
```

### os:homeDir

Returns the user's home directory path.

```javascript
const home = await window.krema.invoke('os:homeDir');
// macOS/Linux: "/Users/john" or "/home/john"
// Windows: "C:\\Users\\john"
```

### os:tempDir

Returns the system temporary directory path.

```javascript
const temp = await window.krema.invoke('os:tempDir');
// macOS: "/var/folders/..."
// Linux: "/tmp"
// Windows: "C:\\Users\\john\\AppData\\Local\\Temp"
```

### os:locale

Returns locale information.

```javascript
const locale = await window.krema.invoke('os:locale');
// {
//   language: "en",
//   country: "US",
//   displayName: "English (United States)",
//   tag: "en-US"
// }
```

### os:cpuCount

Returns the number of available CPU cores.

```javascript
const cpus = await window.krema.invoke('os:cpuCount');
// 8
```

### os:memory

Returns JVM memory information in bytes.

```javascript
const memory = await window.krema.invoke('os:memory');
// {
//   total: 268435456,   // Total heap memory
//   free: 134217728,    // Free heap memory
//   max: 4294967296,    // Maximum heap memory
//   used: 134217728     // Currently used heap memory
// }
```

### os:info

Returns all OS information in a single call.

```javascript
const info = await window.krema.invoke('os:info');
// {
//   platform: "macos",
//   arch: "arm64",
//   version: "14.2.1",
//   hostname: "my-macbook.local",
//   username: "john",
//   homeDir: "/Users/john",
//   tempDir: "/var/folders/...",
//   locale: { language: "en", country: "US", ... },
//   cpuCount: 8,
//   memory: { total: ..., free: ..., max: ..., used: ... }
// }
```

### os:env

Returns the value of an environment variable.

```javascript
const path = await window.krema.invoke('os:env', { name: 'PATH' });
// "/usr/local/bin:/usr/bin:/bin"

const missing = await window.krema.invoke('os:env', { name: 'NONEXISTENT' });
// null
```

### os:envAll

Returns all environment variables.

```javascript
const env = await window.krema.invoke('os:envAll');
// {
//   PATH: "/usr/local/bin:/usr/bin:/bin",
//   HOME: "/Users/john",
//   USER: "john",
//   ...
// }
```

## TypeScript Types

```typescript
interface Locale {
  language: string;
  country: string;
  displayName: string;
  tag: string;
}

interface Memory {
  total: number;
  free: number;
  max: number;
  used: number;
}

interface OSInfo {
  platform: 'macos' | 'windows' | 'linux' | 'unknown';
  arch: 'arm64' | 'x64' | 'x86' | 'arm' | string;
  version: string;
  hostname: string;
  username: string;
  homeDir: string;
  tempDir: string;
  locale: Locale;
  cpuCount: number;
  memory: Memory;
}
```

## Example: System Info Panel

```javascript
async function showSystemInfo() {
  const info = await window.krema.invoke('os:info');

  const panel = document.getElementById('system-info');
  panel.innerHTML = `
    <h3>System Information</h3>
    <p><strong>Platform:</strong> ${info.platform} (${info.arch})</p>
    <p><strong>Version:</strong> ${info.version}</p>
    <p><strong>Hostname:</strong> ${info.hostname}</p>
    <p><strong>User:</strong> ${info.username}</p>
    <p><strong>CPUs:</strong> ${info.cpuCount} cores</p>
    <p><strong>Memory:</strong> ${formatBytes(info.memory.used)} / ${formatBytes(info.memory.max)}</p>
    <p><strong>Locale:</strong> ${info.locale.displayName}</p>
  `;
}

function formatBytes(bytes) {
  const gb = bytes / (1024 * 1024 * 1024);
  return gb.toFixed(2) + ' GB';
}
```

## Permissions

The OS Info API requires the `system-info` permission:

```toml
[permissions]
allow = ["system-info"]
```
