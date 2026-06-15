---
sidebar_position: 7
title: Events
---

# Events

Use events when Java needs to push state changes to the frontend without waiting for a frontend command.

## Emit from Java

Every Krema application has an `EventEmitter`. Plugins can access it from `PluginContext`, and application code can keep a reference when wiring services.

```java
import build.krema.core.Krema;
import build.krema.core.event.EventEmitter;

import java.util.Map;

public final class Jobs {
    private final EventEmitter events;

    public Jobs(EventEmitter events) {
        this.events = events;
    }

    public void reportProgress(String jobId, int percent) {
        events.emit("job:progress", Map.of(
            "jobId", jobId,
            "percent", percent
        ));
    }
}
```

Payloads are serialized as JSON and delivered to the browser on a JavaScript macrotask. That means framework runtimes such as Angular can observe the update during their normal change-detection cycle.

## Listen in JavaScript

Register frontend listeners with `window.krema.on`.

```ts
const unsubscribe = window.krema.on("job:progress", (event) => {
  renderProgress(event.payload.jobId, event.payload.percent);
});

// Later, when the view is destroyed:
unsubscribe();
```

Listeners receive the event payload and timestamp. Keep handlers small; do heavy frontend work in your framework's scheduler or state layer.

## Backend listeners

Java code can also listen to events for local coordination.

```java
Runnable unsubscribe = events.on("job:progress", event -> {
    System.out.println(event.name() + " " + event.payload());
});
```

Backend listeners run in the same Java call path as `emit`, so they should not perform blocking work.
