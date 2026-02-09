---
sidebar_position: 1
title: JavaScript API
description: Frontend API reference for Krema applications
---

# JavaScript API Reference

Krema injects a global `window.krema` object providing access to native APIs.

## Core IPC

### invoke

Invoke a backend command.

```typescript
function invoke<T = unknown>(command: string, args?: Record<string, unknown>): Promise<T>;
```

**Example:**
```javascript
const result = await window.krema.invoke('greet', { name: 'World' });
// result: "Hello, World!"
```

### on

Register an event listener.

```typescript
function on<T = unknown>(event: string, callback: (data: T) => void): () => void;
```

**Returns:** Unsubscribe function

**Example:**
```javascript
const unsubscribe = window.krema.on('file-changed', (data) => {
    console.log('File changed:', data.path);
});

// Later: remove listener
unsubscribe();
```

### once

Register a one-time event listener.

```typescript
function once<T = unknown>(event: string, callback: (data: T) => void): () => void;
```

## Built-in Events

### app:error

Emitted when an unhandled error occurs in either the Java backend or the WebView frontend. Krema automatically captures uncaught Java exceptions, JavaScript errors (`window.onerror`), and unhandled Promise rejections.

**Payload:**

```typescript
interface AppError {
    source: 'JAVA' | 'WEBVIEW';
    message: string;
    stackTrace: string;
    thread?: string;        // Java thread name (Java errors only)
    fileName?: string;      // Source file (WebView errors only)
    lineNumber?: number;    // Line number (WebView errors only)
    os: string;
    appVersion: string;
    recentCommands: string[];
}
```

**Example:**

```javascript
window.krema.on('app:error', (error) => {
    console.error(`[${error.source}] ${error.message}`);

    // Send to your error tracking service
    myErrorTracker.report({
        source: error.source,
        message: error.message,
        stack: error.stackTrace,
        context: {
            os: error.os,
            version: error.appVersion,
            recentCommands: error.recentCommands
        }
    });
});
```

## Dialog API

### openFile

Open a file selection dialog.

```typescript
interface FileFilter {
    name: string;
    extensions: string[];
}

interface OpenFileOptions {
    title?: string;
    defaultPath?: string;
    filters?: FileFilter[];
}

function openFile(options?: OpenFileOptions): Promise<string | null>;
```

**Example:**
```javascript
const file = await window.krema.invoke('dialog:openFile', {
    title: 'Select a file',
    filters: [
        { name: 'Text Files', extensions: ['txt', 'md'] },
        { name: 'All Files', extensions: ['*'] }
    ]
});
```

### openFiles

Open a multi-file selection dialog.

```typescript
function openFiles(options?: OpenFileOptions): Promise<string[] | null>;
```

### saveFile

Open a save file dialog.

```typescript
interface SaveFileOptions {
    title?: string;
    defaultPath?: string;
    filters?: FileFilter[];
}

function saveFile(options?: SaveFileOptions): Promise<string | null>;
```

### selectFolder

Open a folder selection dialog.

```typescript
function selectFolder(options?: FolderOptions): Promise<string | null>;
```

### message

Show a message dialog.

```typescript
type MessageType = 'info' | 'warning' | 'error';

function message(title: string, message: string, type?: MessageType): Promise<void>;
```

### confirm

Show a confirmation dialog.

```typescript
function confirm(title: string, message: string): Promise<boolean>;
```

### prompt

Show a text input prompt.

```typescript
function prompt(title: string, message: string, defaultValue?: string): Promise<string | null>;
```

## Clipboard API

### readText

Read text from the clipboard.

```typescript
function readText(): Promise<string | null>;
```

### writeText

Write text to the clipboard.

```typescript
function writeText(text: string): Promise<boolean>;
```

### readHtml

Read HTML from the clipboard.

```typescript
function readHtml(): Promise<string | null>;
```

### hasText / hasImage

Check clipboard content type.

```typescript
function hasText(): Promise<boolean>;
function hasImage(): Promise<boolean>;
```

### readImageBase64

Read image as base64 PNG.

```typescript
function readImageBase64(): Promise<string | null>;
```

### clear

Clear clipboard contents.

```typescript
function clear(): Promise<boolean>;
```

## Window API

### setTitle

Set the window title.

```typescript
function setTitle(title: string): Promise<void>;
```

### setSize

Set window dimensions.

```typescript
function setSize(width: number, height: number): Promise<void>;
```

### setPosition

Set window position.

```typescript
function setPosition(x: number, y: number): Promise<void>;
```

### center

Center the window on screen.

```typescript
function center(): Promise<void>;
```

### minimize / maximize / restore

Window state controls.

```typescript
function minimize(): Promise<void>;
function maximize(): Promise<void>;
function restore(): Promise<void>;
```

### setFullscreen

Toggle fullscreen mode.

```typescript
function setFullscreen(fullscreen: boolean): Promise<void>;
```

### setAlwaysOnTop

Set always-on-top state.

```typescript
function setAlwaysOnTop(alwaysOnTop: boolean): Promise<void>;
```

### close

Close the window.

```typescript
function close(): Promise<void>;
```

## Notification API

### show

Show a desktop notification.

```typescript
interface NotificationOptions {
    title: string;
    body: string;
    icon?: string;
}

function show(options: NotificationOptions): Promise<void>;
```

**Example:**
```javascript
await window.krema.invoke('notification:show', {
    title: 'Download Complete',
    body: 'Your file has been downloaded successfully.'
});
```

## Shell API

### open

Open a file or URL with the default application.

```typescript
function open(path: string): Promise<void>;
```

**Example:**
```javascript
// Open a URL in the browser
await window.krema.invoke('shell:open', { path: 'https://example.com' });

// Open a file with its default app
await window.krema.invoke('shell:open', { path: '/path/to/document.pdf' });
```

### execute

Execute a shell command.

```typescript
interface ExecuteResult {
    stdout: string;
    stderr: string;
    exitCode: number;
}

function execute(command: string, args?: string[]): Promise<ExecuteResult>;
```

## Path API

### appDataDir

Get the application data directory.

```typescript
function appDataDir(): Promise<string>;
```

### appConfigDir

Get the application config directory.

```typescript
function appConfigDir(): Promise<string>;
```

### homeDir

Get the user's home directory.

```typescript
function homeDir(): Promise<string>;
```

### desktopDir / documentsDir / downloadsDir

Get common user directories.

```typescript
function desktopDir(): Promise<string>;
function documentsDir(): Promise<string>;
function downloadsDir(): Promise<string>;
```

## Screen API

### all

Get all displays.

```typescript
interface ScreenInfo {
    id: string;
    bounds: { x: number; y: number; width: number; height: number };
    workArea: { x: number; y: number; width: number; height: number };
    scaleFactor: number;
    isPrimary: boolean;
}

function all(): Promise<ScreenInfo[]>;
```

### primary

Get the primary display.

```typescript
function primary(): Promise<ScreenInfo>;
```

### cursorPosition

Get current cursor position.

```typescript
function cursorPosition(): Promise<{ x: number; y: number }>;
```

## Store API

### get

Get a value from the store.

```typescript
function get<T = unknown>(key: string): Promise<T | null>;
```

### set

Set a value in the store.

```typescript
function set(key: string, value: unknown): Promise<void>;
```

### delete

Delete a key from the store.

```typescript
function delete(key: string): Promise<boolean>;
```

### has

Check if a key exists.

```typescript
function has(key: string): Promise<boolean>;
```

### keys

Get all keys in the store.

```typescript
function keys(): Promise<string[]>;
```

## Secure Storage API

Store sensitive data in the OS keychain.

### set

```typescript
function set(key: string, value: string): Promise<void>;
```

### get

```typescript
function get(key: string): Promise<string | null>;
```

### delete

```typescript
function delete(key: string): Promise<void>;
```

### has

```typescript
function has(key: string): Promise<boolean>;
```

## Updater API

### check

Check for updates.

```typescript
interface UpdateCheckResult {
    updateAvailable: boolean;
    version?: string;
    notes?: string;
    pubDate?: string;
}

function check(): Promise<UpdateCheckResult>;
```

### download

Download an available update.

```typescript
function download(): Promise<void>;
```

**Events:**
- `download-progress`: Emitted during download with `{ percent: number, downloaded: number, total: number }`

### install

Install the downloaded update and restart.

```typescript
function install(): Promise<void>;
```

## TypeScript Support

Install the type definitions package:

```bash
npm install @krema-build/api
```

Usage:

```typescript
import { invoke, on } from '@krema-build/api';

interface User {
    id: string;
    name: string;
}

const user = await invoke<User>('getUser', { id: '123' });

on('user-updated', (user: User) => {
    console.log('User updated:', user.name);
});
```
