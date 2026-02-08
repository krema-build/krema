package build.krema.core.native_;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Panama FFM bindings to the webview C library.
 * Provides low-level native access to webview functions.
 *
 * Uses Java 25 FFM API.
 *
 * @deprecated Use {@link build.krema.core.webview.macos.MacOSWebViewEngine} instead
 */
@Deprecated
public final class WebViewBindings {

    private static final Linker LINKER = Linker.nativeLinker();
    private static final SymbolLookup LOOKUP;

    // Method handles for native functions
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

    // Store callback stubs to prevent GC
    private static final Map<MemorySegment, MemorySegment> CALLBACK_STUBS = new ConcurrentHashMap<>();

    static {
        // Load the webview library
        String libraryPath = System.getProperty("java.library.path", "./lib");
        try {
            LOOKUP = SymbolLookup.libraryLookup(
                Path.of(libraryPath, "libwebview.dylib"),
                Arena.global()
            );
        } catch (IllegalArgumentException e) {
            throw new RuntimeException(
                "Failed to load libwebview.dylib. Ensure it exists in: " + libraryPath +
                "\nBuild instructions: https://github.com/webview/webview", e
            );
        }

        // Initialize method handles
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
    }

    private static MethodHandle downcall(String name, FunctionDescriptor descriptor) {
        MemorySegment symbol = LOOKUP.find(name)
            .orElseThrow(() -> new RuntimeException("Symbol not found: " + name));
        return LINKER.downcallHandle(symbol, descriptor);
    }

    /**
     * Creates a new webview instance.
     *
     * @param debug Enable developer tools (0 = disabled, 1 = enabled)
     * @return Pointer to the webview instance
     */
    public static MemorySegment create(int debug) {
        try {
            return (MemorySegment) WEBVIEW_CREATE.invokeExact(debug, MemorySegment.NULL);
        } catch (Throwable t) {
            throw new RuntimeException("Failed to create webview", t);
        }
    }

    /**
     * Destroys a webview instance and frees resources.
     */
    public static void destroy(MemorySegment webview) {
        try {
            WEBVIEW_DESTROY.invokeExact(webview);
            // Clean up any callback stubs associated with this webview
            CALLBACK_STUBS.remove(webview);
        } catch (Throwable t) {
            throw new RuntimeException("Failed to destroy webview", t);
        }
    }

    /**
     * Runs the webview event loop. Blocks until the window is closed.
     */
    public static void run(MemorySegment webview) {
        try {
            WEBVIEW_RUN.invokeExact(webview);
        } catch (Throwable t) {
            throw new RuntimeException("Failed to run webview", t);
        }
    }

    /**
     * Terminates the webview event loop.
     */
    public static void terminate(MemorySegment webview) {
        try {
            WEBVIEW_TERMINATE.invokeExact(webview);
        } catch (Throwable t) {
            throw new RuntimeException("Failed to terminate webview", t);
        }
    }

    /**
     * Sets the window title.
     */
    public static void setTitle(MemorySegment webview, String title, Arena arena) {
        try {
            MemorySegment titlePtr = arena.allocateFrom(title);
            WEBVIEW_SET_TITLE.invokeExact(webview, titlePtr);
        } catch (Throwable t) {
            throw new RuntimeException("Failed to set title", t);
        }
    }

    /**
     * Sets the window size.
     *
     * @param hints 0 = NONE, 1 = MIN, 2 = MAX, 3 = FIXED
     */
    public static void setSize(MemorySegment webview, int width, int height, int hints) {
        try {
            WEBVIEW_SET_SIZE.invokeExact(webview, width, height, hints);
        } catch (Throwable t) {
            throw new RuntimeException("Failed to set size", t);
        }
    }

    /**
     * Navigates to the specified URL.
     */
    public static void navigate(MemorySegment webview, String url, Arena arena) {
        try {
            MemorySegment urlPtr = arena.allocateFrom(url);
            WEBVIEW_NAVIGATE.invokeExact(webview, urlPtr);
        } catch (Throwable t) {
            throw new RuntimeException("Failed to navigate", t);
        }
    }

    /**
     * Sets the HTML content directly.
     */
    public static void setHtml(MemorySegment webview, String html, Arena arena) {
        try {
            MemorySegment htmlPtr = arena.allocateFrom(html);
            WEBVIEW_SET_HTML.invokeExact(webview, htmlPtr);
        } catch (Throwable t) {
            throw new RuntimeException("Failed to set HTML", t);
        }
    }

    /**
     * Injects JavaScript to be executed when the page loads.
     */
    public static void init(MemorySegment webview, String js, Arena arena) {
        try {
            MemorySegment jsPtr = arena.allocateFrom(js);
            WEBVIEW_INIT.invokeExact(webview, jsPtr);
        } catch (Throwable t) {
            throw new RuntimeException("Failed to init JS", t);
        }
    }

    /**
     * Evaluates JavaScript in the webview.
     */
    public static void eval(MemorySegment webview, String js, Arena arena) {
        try {
            MemorySegment jsPtr = arena.allocateFrom(js);
            WEBVIEW_EVAL.invokeExact(webview, jsPtr);
        } catch (Throwable t) {
            throw new RuntimeException("Failed to eval JS", t);
        }
    }

    /**
     * Callback interface for bound JavaScript functions.
     */
    @FunctionalInterface
    public interface BindCallback {
        void invoke(String seq, String req, MemorySegment webview);
    }

    /**
     * Binds a JavaScript function to a native callback.
     * When the function is called in JS, the callback is invoked.
     *
     * @param webview The webview instance
     * @param name The JavaScript function name to bind
     * @param callback The callback to invoke (receives seq ID, request JSON, and webview pointer)
     * @param arena The arena for memory allocation
     */
    public static void bind(MemorySegment webview, String name, BindCallback callback, Arena arena) {
        try {
            // Native callback signature: void (*fn)(const char *seq, const char *req, void *arg)
            FunctionDescriptor callbackDescriptor = FunctionDescriptor.ofVoid(
                ValueLayout.ADDRESS, // seq
                ValueLayout.ADDRESS, // req
                ValueLayout.ADDRESS  // arg (we pass webview here)
            );

            // Wrapper to convert native strings to Java strings
            MethodHandle wrapper = MethodHandles.lookup().findStatic(
                WebViewBindings.class,
                "bindCallbackWrapper",
                MethodType.methodType(void.class, BindCallback.class, MemorySegment.class, MemorySegment.class, MemorySegment.class)
            ).bindTo(callback);

            MemorySegment callbackStub = LINKER.upcallStub(
                wrapper,
                callbackDescriptor,
                arena
            );

            // Store to prevent GC
            CALLBACK_STUBS.put(webview, callbackStub);

            MemorySegment namePtr = arena.allocateFrom(name);
            WEBVIEW_BIND.invokeExact(webview, namePtr, callbackStub, webview);
        } catch (Throwable t) {
            throw new RuntimeException("Failed to bind function: " + name, t);
        }
    }

    /**
     * Internal wrapper for bind callbacks to convert native strings.
     */
    @SuppressWarnings("unused")
    private static void bindCallbackWrapper(BindCallback callback, MemorySegment seq, MemorySegment req, MemorySegment arg) {
        String seqStr = readString(seq);
        String reqStr = readString(req);
        callback.invoke(seqStr, reqStr, arg);
    }

    /**
     * Reads a native string from a memory segment (C string pointer).
     * Handles zero-length segments from native callbacks by reinterpreting with a large size.
     */
    private static String readString(MemorySegment segment) {
        // Native callbacks pass pointers as zero-length segments
        // We need to reinterpret with a usable size to read the C string
        if (segment.byteSize() == 0) {
            segment = segment.reinterpret(Integer.MAX_VALUE);
        }
        return segment.getString(0);
    }

    /**
     * Returns a result to a bound JavaScript function.
     *
     * @param webview The webview instance
     * @param seq The sequence ID from the bind callback
     * @param status 0 for success, non-zero for error
     * @param result The JSON result string
     * @param arena Arena for memory allocation
     */
    public static void returnResult(MemorySegment webview, String seq, int status, String result, Arena arena) {
        try {
            MemorySegment seqPtr = arena.allocateFrom(seq);
            MemorySegment resultPtr = arena.allocateFrom(result);
            WEBVIEW_RETURN.invokeExact(webview, seqPtr, status, resultPtr);
        } catch (Throwable t) {
            throw new RuntimeException("Failed to return result", t);
        }
    }

    // Size hint constants
    public static final int WEBVIEW_HINT_NONE = 0;
    public static final int WEBVIEW_HINT_MIN = 1;
    public static final int WEBVIEW_HINT_MAX = 2;
    public static final int WEBVIEW_HINT_FIXED = 3;

    private WebViewBindings() {}
}
