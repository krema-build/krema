package build.krema.core.event;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import build.krema.core.event.EventEmitter;
import build.krema.core.event.KremaEvent;
import build.krema.core.webview.WebViewEngine;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("EventEmitter")
class EventEmitterTest {

    private StubWebViewEngine engine;
    private EventEmitter emitter;

    @BeforeEach
    void setUp() {
        engine = new StubWebViewEngine();
        emitter = new EventEmitter(engine);
    }

    @Test
    @DisplayName("on registers listener that receives events")
    void onRegistersListener() {
        List<KremaEvent> received = new ArrayList<>();
        emitter.on("test", received::add);

        emitter.emit("test", "payload");

        assertEquals(1, received.size());
        assertEquals("test", received.get(0).name());
        assertEquals("payload", received.get(0).payload());
    }

    @Test
    @DisplayName("on returns Runnable that unsubscribes")
    void onReturnsUnsubscribe() {
        List<KremaEvent> received = new ArrayList<>();
        Runnable unsubscribe = emitter.on("test", received::add);

        emitter.emit("test");
        assertEquals(1, received.size());

        unsubscribe.run();
        emitter.emit("test");
        assertEquals(1, received.size());
    }

    @Test
    @DisplayName("off removes listener")
    void offRemovesListener() {
        AtomicInteger count = new AtomicInteger();
        var listener = new java.util.function.Consumer<KremaEvent>() {
            @Override
            public void accept(KremaEvent e) {
                count.incrementAndGet();
            }
        };

        emitter.on("test", listener);
        emitter.emit("test");
        assertEquals(1, count.get());

        emitter.off("test", listener);
        emitter.emit("test");
        assertEquals(1, count.get());
    }

    @Test
    @DisplayName("once fires once then auto-removes")
    void onceFiresOnce() {
        AtomicInteger count = new AtomicInteger();
        emitter.once("test", e -> count.incrementAndGet());

        emitter.emit("test");
        emitter.emit("test");
        emitter.emit("test");

        assertEquals(1, count.get());
    }

    @Test
    @DisplayName("onAll receives events of any name")
    void onAllReceivesAll() {
        List<String> received = new ArrayList<>();
        emitter.onAll(e -> received.add(e.name()));

        emitter.emit("event-a");
        emitter.emit("event-b");
        emitter.emit("event-c");

        assertEquals(List.of("event-a", "event-b", "event-c"), received);
    }

    @Test
    @DisplayName("clear removes all listeners")
    void clearRemovesAll() {
        AtomicInteger specificCount = new AtomicInteger();
        AtomicInteger globalCount = new AtomicInteger();
        emitter.on("test", e -> specificCount.incrementAndGet());
        emitter.onAll(e -> globalCount.incrementAndGet());

        emitter.emit("test");
        assertEquals(1, specificCount.get());
        assertEquals(1, globalCount.get());

        emitter.clear();
        emitter.emit("test");
        assertEquals(1, specificCount.get());
        assertEquals(1, globalCount.get());
    }

    @Test
    @DisplayName("emit calls engine.eval for frontend notification")
    void emitCallsEngineEval() {
        emitter.emit("my-event", "data");

        assertFalse(engine.evalCalls.isEmpty());
        String js = engine.evalCalls.get(0);
        assertTrue(js.contains("__krema_emit"));
        assertTrue(js.contains("my-event"));
    }

    @Test
    @DisplayName("throwing listener does not prevent other listeners")
    void throwingListenerDoesNotBlock() {
        AtomicInteger count = new AtomicInteger();
        emitter.on("test", e -> { throw new RuntimeException("boom"); });
        emitter.on("test", e -> count.incrementAndGet());

        emitter.emit("test");

        assertEquals(1, count.get());
    }

    /**
     * Stub implementation of WebViewEngine that captures eval() calls.
     */
    private static class StubWebViewEngine implements WebViewEngine {
        final List<String> evalCalls = new ArrayList<>();

        @Override
        public void eval(String js) {
            evalCalls.add(js);
        }

        @Override public void setTitle(String title) {}
        @Override public void setSize(int width, int height, SizeHint hint) {}
        @Override public void navigate(String url) {}
        @Override public void setHtml(String html) {}
        @Override public void init(String js) {}
        @Override public void bind(String name, BindCallback callback) {}
        @Override public void returnResult(String seq, boolean success, String result) {}
        @Override public void run() {}
        @Override public void terminate() {}
        @Override public boolean isRunning() { return false; }
        @Override public void close() {}
    }
}
