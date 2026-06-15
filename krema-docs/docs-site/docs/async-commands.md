---
sidebar_position: 8
title: Async Commands
---

# Async Commands

Commands that perform I/O, database work, network calls, or CPU-heavy processing should return `CompletableFuture<T>`. Krema sends the resolved value back to JavaScript through the same `window.krema.invoke` Promise contract.

## Return a future

```java
import build.krema.core.KremaCommand;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class ReportCommands {
    private final ExecutorService worker = Executors.newSingleThreadExecutor();

    @KremaCommand("reports:generate")
    public CompletableFuture<ReportSummary> generate(ReportRequest request) {
        return CompletableFuture.supplyAsync(() -> buildReport(request), worker);
    }
}
```

The frontend still awaits a Promise of the value type.

```ts
const summary = await window.krema.invoke<ReportSummary>("reports:generate", {
  month: "2026-06"
});
```

## Error behavior

If the future completes exceptionally, the JavaScript Promise rejects with the error message. Prefer clear exception messages at command boundaries.

```java
return CompletableFuture.supplyAsync(() -> {
    if (!Files.exists(path)) {
        throw new IllegalArgumentException("Report source does not exist");
    }
    return parseReport(path);
}, worker);
```

## Thread ownership

Use one executor per resource when ordering matters. For example, a single SQLite connection should use one single-thread executor so statements run in submission order.

Shut executors down from your plugin or application lifecycle hook.

```java
public void shutdown() {
    worker.shutdown();
}
```

The development watchdog warns when a command keeps the main thread busy past the internal threshold. Treat that warning as a signal to move blocking work behind a `CompletableFuture`.
