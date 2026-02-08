---
sidebar_position: 2
title: Error Handling
description: How Krema captures and reports errors in your application
---

# Error Handling

Krema provides built-in error handling that captures unhandled exceptions from both the Java backend and the WebView frontend.

## What Krema Handles Automatically

Out of the box, Krema captures three categories of errors:

- **Uncaught Java exceptions** — via `Thread.setDefaultUncaughtExceptionHandler()`
- **JavaScript errors** — via `window.onerror`
- **Unhandled Promise rejections** — via `unhandledrejection` event listener

When an error is captured, Krema:

1. Logs the error
2. Emits an `app:error` event to the frontend
3. Calls your `onError` hook (if registered)
4. Writes a crash report to disk

## `onError` Hook

Register a Java-side error handler using `KremaBuilder.onError()`:

```java
Krema.app()
    .onError(error -> {
        System.err.println("Error from " + error.source() + ": " + error.message());

        // Send to your error tracking service
        if (error.source() == ErrorInfo.Source.JAVA) {
            Sentry.captureMessage(error.message());
        }
    })
    .commands(new MyCommands())
    .run();
```

The `ErrorInfo` record contains:

| Field         | Type           | Description                                   |
|---------------|----------------|-----------------------------------------------|
| `source`      | `Source`        | `JAVA` or `WEBVIEW`                           |
| `message`     | `String`        | Error message                                 |
| `stackTrace`  | `String`        | Full stack trace                               |
| `threadName`  | `String`        | Thread name (Java errors only, null otherwise) |
| `fileName`    | `String`        | Source file (WebView errors only)              |
| `lineNumber`  | `int`           | Line number (WebView errors only, 0 for Java)  |
| `context`     | `ErrorContext`  | Diagnostic context                             |

## `app:error` Event

Listen for errors on the frontend:

```javascript
window.krema.on('app:error', (error) => {
    if (error.source === 'JAVA') {
        showErrorDialog(`Backend error: ${error.message}`);
    } else {
        console.error(`JS error in ${error.fileName}:${error.lineNumber}`);
    }
});
```

The event payload includes `source`, `message`, `stackTrace`, `thread`, `fileName`, `lineNumber`, `os`, `appVersion`, and `recentCommands`. See the [JavaScript API reference](/docs/api/javascript-api#apperror) for the full type definition.

## Crash Reports

Krema writes a JSON crash report for every unhandled error to:

```
{appDataDir}/crash-reports/crash-yyyyMMdd-HHmmss-SSS.json
```

Platform-specific locations:

| Platform | Path                                                   |
|----------|--------------------------------------------------------|
| macOS    | `~/Library/Application Support/YourApp/crash-reports/` |
| Linux    | `~/.local/share/YourApp/crash-reports/`                |
| Windows  | `%APPDATA%\YourApp\crash-reports\`                     |

Each crash report contains:

```json
{
    "timestamp": "2026-02-06T14:30:52.127",
    "source": "JAVA",
    "message": "NullPointerException: Cannot invoke method on null",
    "stackTrace": "java.lang.NullPointerException: ...",
    "threadName": "main",
    "fileName": null,
    "lineNumber": 0,
    "context": {
        "appName": "MyApp",
        "appVersion": "1.0.0",
        "os": "macOS 15.3",
        "sessionId": "a1b2c3d4",
        "recentCommands": ["greet", "dialog:openFile"]
    }
}
```

## Error Context

Every error is enriched with diagnostic context (`ErrorContext`):

| Field            | Description                            |
|------------------|----------------------------------------|
| `appName`        | Application name from configuration    |
| `appVersion`     | Application version                    |
| `os`             | Operating system identifier            |
| `sessionId`      | Unique identifier for the app session  |
| `recentCommands` | Last IPC commands invoked before error |

The recent commands list helps reproduce issues by showing what the user was doing when the error occurred.
