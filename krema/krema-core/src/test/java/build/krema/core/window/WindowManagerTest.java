package build.krema.core.window;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import build.krema.core.webview.WebViewEngine;
import build.krema.core.window.WindowManager;
import build.krema.core.window.WindowOptions;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("WindowManager")
class WindowManagerTest {

    private WindowManager manager;

    @BeforeEach
    void setUp() {
        manager = new WindowManager();
    }

    private StubWebViewEngine createStub() {
        return new StubWebViewEngine();
    }

    private WindowOptions defaultOptions() {
        return WindowOptions.defaults();
    }

    private WindowOptions optionsWithTitle(String title) {
        return WindowOptions.builder().title(title).build();
    }

    @Nested
    @DisplayName("singleton")
    class Singleton {

        @Test
        @DisplayName("getInstance returns same instance")
        void sameInstance() {
            WindowManager.setInstance(manager);
            assertSame(manager, WindowManager.getInstance());
        }

        @Test
        @DisplayName("setInstance replaces instance")
        void replaces() {
            WindowManager other = new WindowManager();
            WindowManager.setInstance(other);
            assertSame(other, WindowManager.getInstance());
            // restore
            WindowManager.setInstance(manager);
        }
    }

    @Nested
    @DisplayName("window creation")
    class WindowCreation {

        @Test
        @DisplayName("creates and tracks window")
        void createsAndTracks() {
            var window = manager.createWindowWithEngine("main", createStub(), defaultOptions());
            assertNotNull(window);
            assertEquals("main", window.getLabel());
            assertEquals(1, manager.windowCount());
        }

        @Test
        @DisplayName("first window becomes main window")
        void firstIsMain() {
            manager.createWindowWithEngine("first", createStub(), defaultOptions());
            assertTrue(manager.getMainWindow().isPresent());
            assertEquals("first", manager.getMainWindow().get().getLabel());
        }

        @Test
        @DisplayName("second window does not replace main window")
        void secondDoesNotReplaceMain() {
            manager.createWindowWithEngine("first", createStub(), defaultOptions());
            manager.createWindowWithEngine("second", createStub(), defaultOptions());
            assertEquals("first", manager.getMainWindow().get().getLabel());
        }

        @Test
        @DisplayName("duplicate label throws IllegalArgumentException")
        void duplicateLabelThrows() {
            manager.createWindowWithEngine("dup", createStub(), defaultOptions());
            assertThrows(IllegalArgumentException.class,
                    () -> manager.createWindowWithEngine("dup", createStub(), defaultOptions()));
        }

        @Test
        @DisplayName("configures title from options")
        void configuresTitle() {
            var stub = createStub();
            manager.createWindowWithEngine("win", stub, optionsWithTitle("My Window"));
            assertTrue(stub.titleCalls.contains("My Window"));
        }

        @Test
        @DisplayName("configures size from options")
        void configuresSize() {
            var stub = createStub();
            var options = WindowOptions.builder().size(1024, 768).build();
            manager.createWindowWithEngine("win", stub, options);
            assertTrue(stub.sizeCalls.stream()
                    .anyMatch(c -> c.width == 1024 && c.height == 768 && c.hint == WebViewEngine.SizeHint.NONE));
        }

        @Test
        @DisplayName("configures minSize from options")
        void configuresMinSize() {
            var stub = createStub();
            var options = WindowOptions.builder().minSize(400, 300).build();
            manager.createWindowWithEngine("win", stub, options);
            assertTrue(stub.sizeCalls.stream()
                    .anyMatch(c -> c.width == 400 && c.height == 300 && c.hint == WebViewEngine.SizeHint.MIN));
        }

        @Test
        @DisplayName("configures maxSize from options")
        void configuresMaxSize() {
            var stub = createStub();
            var options = WindowOptions.builder().maxSize(1920, 1080).build();
            manager.createWindowWithEngine("win", stub, options);
            assertTrue(stub.sizeCalls.stream()
                    .anyMatch(c -> c.width == 1920 && c.height == 1080 && c.hint == WebViewEngine.SizeHint.MAX));
        }

        @Test
        @DisplayName("configures fixedSize when not resizable")
        void configuresFixedSize() {
            var stub = createStub();
            var options = WindowOptions.builder().size(800, 600).resizable(false).build();
            manager.createWindowWithEngine("win", stub, options);
            assertTrue(stub.sizeCalls.stream()
                    .anyMatch(c -> c.width == 800 && c.height == 600 && c.hint == WebViewEngine.SizeHint.FIXED));
        }
    }

    @Nested
    @DisplayName("retrieval")
    class Retrieval {

        @Test
        @DisplayName("getWindow returns present for existing")
        void getWindowPresent() {
            manager.createWindowWithEngine("win", createStub(), defaultOptions());
            assertTrue(manager.getWindow("win").isPresent());
        }

        @Test
        @DisplayName("getWindow returns empty for nonexistent")
        void getWindowEmpty() {
            assertTrue(manager.getWindow("nope").isEmpty());
        }

        @Test
        @DisplayName("getWindowLabels returns all labels")
        void getWindowLabels() {
            manager.createWindowWithEngine("a", createStub(), defaultOptions());
            manager.createWindowWithEngine("b", createStub(), defaultOptions());
            String[] labels = manager.getWindowLabels();
            assertEquals(2, labels.length);
            List<String> labelList = Arrays.asList(labels);
            assertTrue(labelList.contains("a"));
            assertTrue(labelList.contains("b"));
        }

        @Test
        @DisplayName("windowCount returns correct count")
        void windowCount() {
            assertEquals(0, manager.windowCount());
            manager.createWindowWithEngine("a", createStub(), defaultOptions());
            assertEquals(1, manager.windowCount());
            manager.createWindowWithEngine("b", createStub(), defaultOptions());
            assertEquals(2, manager.windowCount());
        }
    }

    @Nested
    @DisplayName("child and modal")
    class ChildAndModal {

        @Test
        @DisplayName("setParentLabel sets parent")
        void setParentLabel() {
            var parent = manager.createWindowWithEngine("parent", createStub(), defaultOptions());
            var child = manager.createWindowWithEngine("child", createStub(), defaultOptions());
            child.setParentLabel("parent");
            assertEquals("parent", child.getParentLabel());
        }

        @Test
        @DisplayName("setModal sets modal flag")
        void setModal() {
            var window = manager.createWindowWithEngine("modal", createStub(), defaultOptions());
            assertFalse(window.isModal());
            window.setModal(true);
            assertTrue(window.isModal());
        }
    }

    @Nested
    @DisplayName("closeWindow")
    class CloseWindow {

        @Test
        @DisplayName("removes window and calls engine.close")
        void removesAndCloses() {
            var stub = createStub();
            manager.createWindowWithEngine("win", stub, defaultOptions());
            manager.closeWindow("win");
            assertTrue(stub.closeCalled);
            assertEquals(0, manager.windowCount());
        }

        @Test
        @DisplayName("fires onWindowClosed callback")
        void firesCallback() {
            List<String> closed = new ArrayList<>();
            manager.setOnWindowClosed(closed::add);
            manager.createWindowWithEngine("win", createStub(), defaultOptions());
            manager.closeWindow("win");
            assertEquals(List.of("win"), closed);
        }

        @Test
        @DisplayName("nonexistent label is a no-op")
        void nonexistentNoOp() {
            assertDoesNotThrow(() -> manager.closeWindow("nope"));
        }
    }

    @Nested
    @DisplayName("messaging")
    class Messaging {

        @Test
        @DisplayName("sendToWindow evals JS with __krema_event")
        void sendToWindow() {
            var stub = createStub();
            manager.createWindowWithEngine("win", stub, defaultOptions());
            manager.sendToWindow("win", "my-event", "hello");
            assertEquals(1, stub.evalCalls.size());
            String js = stub.evalCalls.getFirst();
            assertTrue(js.contains("__krema_event"));
            assertTrue(js.contains("my-event"));
            assertTrue(js.contains("\"hello\""));
        }

        @Test
        @DisplayName("sendToWindow unknown label is a no-op")
        void sendToUnknownNoOp() {
            assertDoesNotThrow(() -> manager.sendToWindow("nope", "event", null));
        }

        @Test
        @DisplayName("broadcast evals on all windows")
        void broadcastAll() {
            var stub1 = createStub();
            var stub2 = createStub();
            manager.createWindowWithEngine("a", stub1, defaultOptions());
            manager.createWindowWithEngine("b", stub2, defaultOptions());
            manager.broadcast("ping", 42);
            assertEquals(1, stub1.evalCalls.size());
            assertEquals(1, stub2.evalCalls.size());
        }

        @Test
        @DisplayName("null payload serializes to null")
        void nullPayload() {
            var stub = createStub();
            manager.createWindowWithEngine("win", stub, defaultOptions());
            manager.sendToWindow("win", "evt", null);
            assertTrue(stub.evalCalls.getFirst().contains("null"));
        }

        @Test
        @DisplayName("string payload is quoted")
        void stringPayload() {
            var stub = createStub();
            manager.createWindowWithEngine("win", stub, defaultOptions());
            manager.sendToWindow("win", "evt", "text");
            assertTrue(stub.evalCalls.getFirst().contains("\"text\""));
        }

        @Test
        @DisplayName("number payload is unquoted")
        void numberPayload() {
            var stub = createStub();
            manager.createWindowWithEngine("win", stub, defaultOptions());
            manager.sendToWindow("win", "evt", 99);
            assertTrue(stub.evalCalls.getFirst().contains("99"));
        }

        @Test
        @DisplayName("boolean payload is unquoted")
        void booleanPayload() {
            var stub = createStub();
            manager.createWindowWithEngine("win", stub, defaultOptions());
            manager.sendToWindow("win", "evt", true);
            assertTrue(stub.evalCalls.getFirst().contains("true"));
        }

        @Test
        @DisplayName("map payload serializes to JSON object")
        void mapPayload() {
            var stub = createStub();
            manager.createWindowWithEngine("win", stub, defaultOptions());
            // Use LinkedHashMap for deterministic ordering
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("key", "val");
            manager.sendToWindow("win", "evt", map);
            String js = stub.evalCalls.getFirst();
            assertTrue(js.contains("\"key\":\"val\""));
        }
    }

    @Nested
    @DisplayName("close all")
    class CloseAll {

        @Test
        @DisplayName("close() closes all windows and clears map")
        void closesAll() {
            var stub1 = createStub();
            var stub2 = createStub();
            manager.createWindowWithEngine("a", stub1, defaultOptions());
            manager.createWindowWithEngine("b", stub2, defaultOptions());
            manager.close();
            assertTrue(stub1.closeCalled);
            assertTrue(stub2.closeCalled);
            assertEquals(0, manager.windowCount());
        }
    }

    // ---- Stub ----

    static class SizeCall {
        final int width;
        final int height;
        final WebViewEngine.SizeHint hint;

        SizeCall(int width, int height, WebViewEngine.SizeHint hint) {
            this.width = width;
            this.height = height;
            this.hint = hint;
        }
    }

    static class StubWebViewEngine implements WebViewEngine {
        final List<String> titleCalls = new ArrayList<>();
        final List<SizeCall> sizeCalls = new ArrayList<>();
        final List<String> navigateCalls = new ArrayList<>();
        final List<String> htmlCalls = new ArrayList<>();
        final List<String> initCalls = new ArrayList<>();
        final List<String> evalCalls = new ArrayList<>();
        final List<String> bindNames = new ArrayList<>();
        boolean runCalled;
        boolean terminateCalled;
        boolean closeCalled;
        boolean running;

        @Override public void setTitle(String title) { titleCalls.add(title); }
        @Override public void setSize(int w, int h, SizeHint hint) { sizeCalls.add(new SizeCall(w, h, hint)); }
        @Override public void navigate(String url) { navigateCalls.add(url); }
        @Override public void setHtml(String html) { htmlCalls.add(html); }
        @Override public void init(String js) { initCalls.add(js); }
        @Override public void eval(String js) { evalCalls.add(js); }
        @Override public void bind(String name, BindCallback callback) { bindNames.add(name); }
        @Override public void returnResult(String seq, boolean success, String result) {}
        @Override public void run() { runCalled = true; }
        @Override public void terminate() { terminateCalled = true; }
        @Override public boolean isRunning() { return running; }
        @Override public void close() { closeCalled = true; }
    }
}
