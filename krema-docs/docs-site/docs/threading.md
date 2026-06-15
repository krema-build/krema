---
sidebar_position: 9
title: Threading
---

# Threading

Krema keeps frontend command dispatch responsive by supporting asynchronous command results. The rule of thumb is simple: commands may validate arguments on the caller thread, but blocking work belongs on a worker executor.

## Main-thread rules

Keep these operations short:

- JSON argument validation
- permission checks
- dispatch to an executor
- returning a cached in-memory value

Move these operations off the caller thread:

- JDBC and filesystem scans
- HTTP requests
- process execution
- compression, hashing, parsing, and large serialization jobs

## Futures and reply delivery

When a command returns `CompletableFuture<T>`, Krema returns from the native callback immediately. When the future completes, the result is delivered back to JavaScript and the original `window.krema.invoke` Promise resolves or rejects.

```java
@KremaCommand("files:hash")
public CompletableFuture<String> hashFile(Path path) {
    return CompletableFuture.supplyAsync(() -> hash(path), fileWorker);
}
```

## Ordered resources

Some resources need serialized access. Use a dedicated single-thread executor for each resource instance.

```java
final class DatabaseHandle {
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    CompletableFuture<Integer> execute(String sql) {
        return CompletableFuture.supplyAsync(() -> executeOnConnection(sql), executor);
    }
}
```

This preserves FIFO ordering without blocking the UI.

## Events

Backend events are delivered to JavaScript on a macrotask, so frontend frameworks can observe updates. See [Events](./events.md) for the event API.
