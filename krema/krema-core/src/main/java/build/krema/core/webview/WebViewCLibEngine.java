package build.krema.core.webview;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

import build.krema.core.platform.NativeLibraryLoader;

/**
 * Shared WebViewEngine implementation using the webview C library via FFM.
 * Platform-agnostic: the native webview library uses GTK+WebKit on Linux
 * and Cocoa+WebKit on macOS, but the C API is identical.
 */
public abstract class WebViewCLibEngine implements WebViewEngine {

    private static final Linker LINKER = Linker.nativeLinker();
    private static final SymbolLookup LOOKUP;

    private static final MethodHandle WEBVIEW_CREATE;
    private static final MethodHandle WEBVIEW_DESTROY;
    private static final MethodHandle WEBVIEW_RUN;
    private static final MethodHandle WEBVIEW_TERMINATE;
    private static final MethodHandle WEBVIEW_SET_TITLE;
    private static final MethodHandle WEBVIEW_SET_SIZE;
    private static final MethodHandle WEBVIEW_NAVIGATE;
    private static final MethodHandle WEBVIEW_SET_HTML;
    private static final MethodHandle WEBVIEW_INIT;
    private static final MethodHandle WEBVIEW_EVAL;
    private static final MethodHandle WEBVIEW_BIND;
    private static final MethodHandle WEBVIEW_RETURN;
    private static final MethodHandle WEBVIEW_GET_WINDOW;

    private static final Map<MemorySegment, MemorySegment> CALLBACK_STUBS = new ConcurrentHashMap<>();

    private static final AtomicReference<WebViewCLibEngine> ACTIVE_INSTANCE = new AtomicReference<>();

    static {
        LOOKUP = NativeLibraryLoader.load("webview");

        WEBVIEW_CREATE = downcall("webview_create",
            FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.ADDRESS));

        WEBVIEW_DESTROY = downcall("webview_destroy",
            FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));

        WEBVIEW_RUN = downcall("webview_run",
            FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));

        WEBVIEW_TERMINATE = downcall("webview_terminate",
            FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));

        WEBVIEW_SET_TITLE = downcall("webview_set_title",
            FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.ADDRESS));

        WEBVIEW_SET_SIZE = downcall("webview_set_size",
            FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));

        WEBVIEW_NAVIGATE = downcall("webview_navigate",
            FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.ADDRESS));

        WEBVIEW_SET_HTML = downcall("webview_set_html",
            FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.ADDRESS));

        WEBVIEW_INIT = downcall("webview_init",
            FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.ADDRESS));

        WEBVIEW_EVAL = downcall("webview_eval",
            FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.ADDRESS));

        WEBVIEW_BIND = downcall("webview_bind",
            FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS));

        WEBVIEW_RETURN = downcall("webview_return",
            FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.ADDRESS));

        WEBVIEW_GET_WINDOW = downcall("webview_get_window",
            FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS));
    }

    private static MethodHandle downcall(String name, FunctionDescriptor descriptor) {
        MemorySegment symbol = LOOKUP.find(name)
            .orElseThrow(() -> new RuntimeException("Symbol not found: " + name));
        return LINKER.downcallHandle(symbol, descriptor);
    }

    private final Arena arena;
    private final MemorySegment webview;
    private volatile boolean running = false;
    private volatile boolean closed = false;

    protected WebViewCLibEngine(boolean debug) {
        this.arena = Arena.ofAuto();
        this.webview = createWebview(debug);
        ACTIVE_INSTANCE.set(this);
    }

    private MemorySegment createWebview(boolean debug) {
        try {
            MemorySegment handle = (MemorySegment) WEBVIEW_CREATE.invokeExact(
                debug ? 1 : 0,
                MemorySegment.NULL
            );
            if (handle == null || handle.equals(MemorySegment.NULL)) {
                throw new RuntimeException("Failed to create webview: null handle returned");
            }
            return handle;
        } catch (Throwable t) {
            throw new RuntimeException("Failed to create webview", t);
        }
    }

    /**
     * Returns the native window handle (NSWindow* on macOS, GtkWindow* on Linux).
     */
    public MemorySegment getNativeWindow() {
        ensureNotClosed();
        try {
            return (MemorySegment) WEBVIEW_GET_WINDOW.invokeExact(webview);
        } catch (Throwable t) {
            throw new RuntimeException("Failed to get native window", t);
        }
    }

    /**
     * Returns the currently active WebViewCLibEngine instance.
     */
    public static WebViewCLibEngine getActive() {
        return ACTIVE_INSTANCE.get();
    }

    @Override
    public void setTitle(String title) {
        ensureNotClosed();
        try {
            MemorySegment titlePtr = arena.allocateFrom(title);
            WEBVIEW_SET_TITLE.invokeExact(webview, titlePtr);
        } catch (Throwable t) {
            throw new RuntimeException("Failed to set title", t);
        }
    }

    @Override
    public void setSize(int width, int height, SizeHint hint) {
        ensureNotClosed();
        try {
            WEBVIEW_SET_SIZE.invokeExact(webview, width, height, hint.getValue());
        } catch (Throwable t) {
            throw new RuntimeException("Failed to set size", t);
        }
    }

    @Override
    public void navigate(String url) {
        ensureNotClosed();
        try {
            MemorySegment urlPtr = arena.allocateFrom(url);
            WEBVIEW_NAVIGATE.invokeExact(webview, urlPtr);
        } catch (Throwable t) {
            throw new RuntimeException("Failed to navigate", t);
        }
    }

    @Override
    public void setHtml(String html) {
        ensureNotClosed();
        try {
            MemorySegment htmlPtr = arena.allocateFrom(html);
            WEBVIEW_SET_HTML.invokeExact(webview, htmlPtr);
        } catch (Throwable t) {
            throw new RuntimeException("Failed to set HTML", t);
        }
    }

    @Override
    public void init(String js) {
        ensureNotClosed();
        try {
            MemorySegment jsPtr = arena.allocateFrom(js);
            WEBVIEW_INIT.invokeExact(webview, jsPtr);
        } catch (Throwable t) {
            throw new RuntimeException("Failed to init JS", t);
        }
    }

    @Override
    public void eval(String js) {
        ensureNotClosed();
        try {
            MemorySegment jsPtr = arena.allocateFrom(js);
            WEBVIEW_EVAL.invokeExact(webview, jsPtr);
        } catch (Throwable t) {
            throw new RuntimeException("Failed to eval JS", t);
        }
    }

    @Override
    public void bind(String name, BindCallback callback) {
        ensureNotClosed();
        try {
            FunctionDescriptor callbackDescriptor = FunctionDescriptor.ofVoid(
                ValueLayout.ADDRESS,
                ValueLayout.ADDRESS,
                ValueLayout.ADDRESS
            );

            MethodHandle wrapper = MethodHandles.lookup().findStatic(
                WebViewCLibEngine.class,
                "bindCallbackWrapper",
                MethodType.methodType(void.class, BindCallback.class, MemorySegment.class, MemorySegment.class, MemorySegment.class)
            ).bindTo(callback);

            MemorySegment callbackStub = LINKER.upcallStub(wrapper, callbackDescriptor, arena);

            CALLBACK_STUBS.put(webview, callbackStub);

            MemorySegment namePtr = arena.allocateFrom(name);
            WEBVIEW_BIND.invokeExact(webview, namePtr, callbackStub, webview);
        } catch (Throwable t) {
            throw new RuntimeException("Failed to bind function: " + name, t);
        }
    }

    @SuppressWarnings("unused")
    private static void bindCallbackWrapper(BindCallback callback, MemorySegment seq, MemorySegment req, MemorySegment arg) {
        String seqStr = readString(seq);
        String reqStr = readString(req);
        callback.invoke(seqStr, reqStr);
    }

    private static String readString(MemorySegment segment) {
        if (segment.byteSize() == 0) {
            segment = segment.reinterpret(Integer.MAX_VALUE);
        }
        return segment.getString(0);
    }

    @Override
    public void returnResult(String seq, boolean success, String result) {
        ensureNotClosed();
        try {
            MemorySegment seqPtr = arena.allocateFrom(seq);
            MemorySegment resultPtr = arena.allocateFrom(result);
            WEBVIEW_RETURN.invokeExact(webview, seqPtr, success ? 0 : 1, resultPtr);
        } catch (Throwable t) {
            throw new RuntimeException("Failed to return result", t);
        }
    }

    @Override
    public void run() {
        ensureNotClosed();
        running = true;
        try {
            WEBVIEW_RUN.invokeExact(webview);
        } catch (Throwable t) {
            throw new RuntimeException("Failed to run webview", t);
        } finally {
            running = false;
        }
    }

    @Override
    public void terminate() {
        if (running && !closed) {
            try {
                WEBVIEW_TERMINATE.invokeExact(webview);
            } catch (Throwable t) {
                throw new RuntimeException("Failed to terminate webview", t);
            }
        }
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        ACTIVE_INSTANCE.compareAndSet(this, null);
        terminate();
        try {
            WEBVIEW_DESTROY.invokeExact(webview);
            CALLBACK_STUBS.remove(webview);
        } catch (Throwable t) {
            throw new RuntimeException("Failed to destroy webview", t);
        }
    }

    private void ensureNotClosed() {
        if (closed) {
            throw new IllegalStateException("WebViewEngine has been closed");
        }
    }
}
