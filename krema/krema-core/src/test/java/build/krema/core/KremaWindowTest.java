package build.krema.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import build.krema.core.KremaWindow;
import build.krema.core.webview.WebViewEngine;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("KremaWindow")
class KremaWindowTest {

    private StubWebViewEngine engine;
    private KremaWindow window;

    @BeforeEach
    void setUp() {
        engine = new StubWebViewEngine();
        window = new KremaWindow(engine);
    }

    @Nested
    @DisplayName("fluent API")
    class FluentApi {

        @Test
        @DisplayName("title returns same instance")
        void titleReturnsSelf() {
            assertSame(window, window.title("Test"));
        }

        @Test
        @DisplayName("size returns same instance")
        void sizeReturnsSelf() {
            assertSame(window, window.size(800, 600));
        }

        @Test
        @DisplayName("minSize returns same instance")
        void minSizeReturnsSelf() {
            assertSame(window, window.minSize(400, 300));
        }

        @Test
        @DisplayName("maxSize returns same instance")
        void maxSizeReturnsSelf() {
            assertSame(window, window.maxSize(1920, 1080));
        }

        @Test
        @DisplayName("fixedSize returns same instance")
        void fixedSizeReturnsSelf() {
            assertSame(window, window.fixedSize(800, 600));
        }

        @Test
        @DisplayName("navigate returns same instance")
        void navigateReturnsSelf() {
            assertSame(window, window.navigate("http://example.com"));
        }

        @Test
        @DisplayName("html returns same instance")
        void htmlReturnsSelf() {
            assertSame(window, window.html("<p>test</p>"));
        }

        @Test
        @DisplayName("init returns same instance")
        void initReturnsSelf() {
            assertSame(window, window.init("console.log('init')"));
        }

        @Test
        @DisplayName("eval returns same instance")
        void evalReturnsSelf() {
            assertSame(window, window.eval("1+1"));
        }

        @Test
        @DisplayName("bind returns same instance")
        void bindReturnsSelf() {
            assertSame(window, window.bind("myFunc", (seq, req) -> {}));
        }
    }

    @Nested
    @DisplayName("delegation")
    class Delegation {

        @Test
        @DisplayName("title delegates to engine.setTitle")
        void titleDelegates() {
            window.title("My Title");
            assertEquals(List.of("My Title"), engine.titleCalls);
        }

        @Test
        @DisplayName("size delegates to engine.setSize with NONE hint")
        void sizeDelegates() {
            window.size(800, 600);
            assertEquals(1, engine.sizeCalls.size());
            var call = engine.sizeCalls.getFirst();
            assertEquals(800, call.width);
            assertEquals(600, call.height);
            assertEquals(WebViewEngine.SizeHint.NONE, call.hint);
        }

        @Test
        @DisplayName("minSize delegates with MIN hint")
        void minSizeDelegates() {
            window.minSize(400, 300);
            var call = engine.sizeCalls.getFirst();
            assertEquals(400, call.width);
            assertEquals(300, call.height);
            assertEquals(WebViewEngine.SizeHint.MIN, call.hint);
        }

        @Test
        @DisplayName("maxSize delegates with MAX hint")
        void maxSizeDelegates() {
            window.maxSize(1920, 1080);
            var call = engine.sizeCalls.getFirst();
            assertEquals(WebViewEngine.SizeHint.MAX, call.hint);
        }

        @Test
        @DisplayName("fixedSize delegates with FIXED hint")
        void fixedSizeDelegates() {
            window.fixedSize(800, 600);
            var call = engine.sizeCalls.getFirst();
            assertEquals(WebViewEngine.SizeHint.FIXED, call.hint);
        }

        @Test
        @DisplayName("navigate delegates to engine.navigate")
        void navigateDelegates() {
            window.navigate("http://localhost:3000");
            assertEquals(List.of("http://localhost:3000"), engine.navigateCalls);
        }

        @Test
        @DisplayName("html delegates to engine.setHtml")
        void htmlDelegates() {
            window.html("<p>hello</p>");
            assertEquals(List.of("<p>hello</p>"), engine.htmlCalls);
        }

        @Test
        @DisplayName("init delegates to engine.init")
        void initDelegates() {
            window.init("var x = 1;");
            assertEquals(List.of("var x = 1;"), engine.initCalls);
        }

        @Test
        @DisplayName("eval delegates to engine.eval")
        void evalDelegates() {
            window.eval("document.title");
            assertEquals(List.of("document.title"), engine.evalCalls);
        }

        @Test
        @DisplayName("bind delegates to engine.bind")
        void bindDelegates() {
            window.bind("myFunc", (seq, req) -> {});
            assertEquals(List.of("myFunc"), engine.bindNames);
        }
    }

    @Nested
    @DisplayName("engine access")
    class EngineAccess {

        @Test
        @DisplayName("getEngine returns engine")
        void getEngine() {
            assertSame(engine, window.getEngine());
        }

        @Test
        @DisplayName("getHandle (deprecated) returns engine")
        @SuppressWarnings("deprecation")
        void getHandle() {
            assertSame(engine, window.getHandle());
        }
    }

    @Nested
    @DisplayName("lifecycle")
    class Lifecycle {

        @Test
        @DisplayName("run delegates to engine")
        void runDelegates() {
            window.run();
            assertTrue(engine.runCalled);
        }

        @Test
        @DisplayName("terminate delegates to engine")
        void terminateDelegates() {
            window.terminate();
            assertTrue(engine.terminateCalled);
        }

        @Test
        @DisplayName("isRunning delegates to engine")
        void isRunningDelegates() {
            assertFalse(window.isRunning());
            engine.running = true;
            assertTrue(window.isRunning());
        }

        @Test
        @DisplayName("close delegates to engine")
        void closeDelegates() {
            window.close();
            assertTrue(engine.closeCalled);
        }
    }

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
