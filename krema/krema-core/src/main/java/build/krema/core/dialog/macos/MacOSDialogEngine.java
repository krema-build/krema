package build.krema.core.dialog.macos;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.util.ArrayList;
import java.util.List;

import build.krema.core.dialog.DialogEngine;

/**
 * macOS DialogEngine implementation using Cocoa APIs via FFM.
 * Calls NSOpenPanel, NSSavePanel, and NSAlert directly through the Objective-C runtime.
 *
 * These dialogs must be called from the main thread. The IPC handler runs synchronously
 * on the main thread, and Cocoa's runModal runs a nested event loop that keeps the UI
 * responsive while the dialog is open.
 */
public final class MacOSDialogEngine implements DialogEngine {

    private static final Linker LINKER = Linker.nativeLinker();
    private static final SymbolLookup OBJC_LOOKUP;

    // Objective-C runtime functions
    private static final MethodHandle OBJC_GET_CLASS;
    private static final MethodHandle SEL_REGISTER_NAME;
    private static final MethodHandle OBJC_MSG_SEND;
    private static final MethodHandle OBJC_MSG_SEND_LONG;

    // Common selectors (cached for performance)
    private static final MemorySegment SEL_ALLOC;
    private static final MemorySegment SEL_INIT;

    // NSModalResponse constants
    private static final long NS_MODAL_RESPONSE_OK = 1;

    // CoreFoundation string encoding
    private static final int kCFStringEncodingUTF8 = 0x08000100;
    private static final MethodHandle CF_STRING_CREATE;

    static {
        // Load Objective-C runtime
        OBJC_LOOKUP = SymbolLookup.libraryLookup("/usr/lib/libobjc.dylib", Arena.global());

        // Load AppKit framework (needed for NSOpenPanel, etc.)
        SymbolLookup.libraryLookup(
            "/System/Library/Frameworks/AppKit.framework/AppKit",
            Arena.global()
        );

        // Load CoreFoundation for reliable string creation
        SymbolLookup cfLookup = SymbolLookup.libraryLookup(
            "/System/Library/Frameworks/CoreFoundation.framework/CoreFoundation",
            Arena.global()
        );

        // CFStringRef CFStringCreateWithCString(CFAllocatorRef alloc, const char *cStr, CFStringEncoding encoding)
        CF_STRING_CREATE = LINKER.downcallHandle(
            cfLookup.find("CFStringCreateWithCString").orElseThrow(),
            FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT)
        );

        // objc_getClass(const char* name) -> Class
        OBJC_GET_CLASS = LINKER.downcallHandle(
            OBJC_LOOKUP.find("objc_getClass").orElseThrow(),
            FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS)
        );

        // sel_registerName(const char* name) -> SEL
        SEL_REGISTER_NAME = LINKER.downcallHandle(
            OBJC_LOOKUP.find("sel_registerName").orElseThrow(),
            FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS)
        );

        // objc_msgSend(id self, SEL op, ...) -> id
        OBJC_MSG_SEND = LINKER.downcallHandle(
            OBJC_LOOKUP.find("objc_msgSend").orElseThrow(),
            FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS),
            Linker.Option.firstVariadicArg(2)
        );

        // objc_msgSend for NSInteger/long return
        OBJC_MSG_SEND_LONG = LINKER.downcallHandle(
            OBJC_LOOKUP.find("objc_msgSend").orElseThrow(),
            FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.ADDRESS),
            Linker.Option.firstVariadicArg(2)
        );

        // Cache common selectors
        SEL_ALLOC = sel("alloc");
        SEL_INIT = sel("init");
    }

    private final Arena arena = Arena.ofAuto();

    @Override
    public String openFile(FileDialogOptions options) {
        try {
            List<String> files = showOpenPanel(false, false, options);
            return (files != null && !files.isEmpty()) ? files.get(0) : null;
        } catch (Throwable t) {
            System.err.println("[MacOSDialogEngine] openFile failed: " + t.getMessage());
            t.printStackTrace();
            throw new RuntimeException("Failed to show open file dialog", t);
        }
    }

    @Override
    public List<String> openFiles(FileDialogOptions options) {
        try {
            return showOpenPanel(false, true, options);
        } catch (Throwable t) {
            System.err.println("[MacOSDialogEngine] openFiles failed: " + t.getMessage());
            t.printStackTrace();
            throw new RuntimeException("Failed to show open files dialog", t);
        }
    }

    @Override
    public String saveFile(FileDialogOptions options) {
        try {
            return showSavePanel(options);
        } catch (Throwable t) {
            System.err.println("[MacOSDialogEngine] saveFile failed: " + t.getMessage());
            t.printStackTrace();
            throw new RuntimeException("Failed to show save file dialog", t);
        }
    }

    @Override
    public String selectFolder(FolderDialogOptions options) {
        try {
            List<String> folders = showOpenPanel(true, false,
                new FileDialogOptions(options.title(), options.defaultPath(), null));
            return (folders != null && !folders.isEmpty()) ? folders.get(0) : null;
        } catch (Throwable t) {
            System.err.println("[MacOSDialogEngine] selectFolder failed: " + t.getMessage());
            t.printStackTrace();
            throw new RuntimeException("Failed to show select folder dialog", t);
        }
    }

    @Override
    public void showMessage(String title, String message, MessageType type) {
        try {
            MemorySegment alert = createAlert(title, message, type);
            msgSend(alert, sel("runModal"));
        } catch (Throwable t) {
            System.err.println("[MacOSDialogEngine] showMessage failed: " + t.getMessage());
            t.printStackTrace();
            throw new RuntimeException("Failed to show message dialog", t);
        }
    }

    @Override
    public boolean showConfirm(String title, String message) {
        try {
            MemorySegment alert = createAlert(title, message, MessageType.INFO);

            MemorySegment okLabel = createNSString("OK");
            msgSend(alert, sel("addButtonWithTitle:"), okLabel);
            MemorySegment cancelLabel = createNSString("Cancel");
            msgSend(alert, sel("addButtonWithTitle:"), cancelLabel);

            long result = msgSendLong(alert, sel("runModal"));
            // NSAlertFirstButtonReturn = 1000 (OK)
            return result == 1000;
        } catch (Throwable t) {
            System.err.println("[MacOSDialogEngine] showConfirm failed: " + t.getMessage());
            t.printStackTrace();
            throw new RuntimeException("Failed to show confirm dialog", t);
        }
    }

    @Override
    public String showPrompt(String title, String message, String defaultValue) {
        try {
            MemorySegment alert = createAlert(title, message, MessageType.INFO);

            MemorySegment okLabel = createNSString("OK");
            msgSend(alert, sel("addButtonWithTitle:"), okLabel);
            MemorySegment cancelLabel = createNSString("Cancel");
            msgSend(alert, sel("addButtonWithTitle:"), cancelLabel);

            // Create NSTextField as accessory view (NSRect: x=0, y=0, w=200, h=24)
            MemorySegment textFieldClass = getClass("NSTextField");
            MemorySegment textField = msgSend(textFieldClass, SEL_ALLOC);
            MemorySegment rect = arena.allocate(ValueLayout.JAVA_DOUBLE, 4);
            rect.set(ValueLayout.JAVA_DOUBLE, 0, 0.0);   // x
            rect.set(ValueLayout.JAVA_DOUBLE, 8, 0.0);   // y
            rect.set(ValueLayout.JAVA_DOUBLE, 16, 200.0); // width
            rect.set(ValueLayout.JAVA_DOUBLE, 24, 24.0);  // height
            textField = msgSendWithRect(textField, sel("initWithFrame:"), rect);

            if (defaultValue != null) {
                MemorySegment nsDefault = createNSString(defaultValue);
                msgSend(textField, sel("setStringValue:"), nsDefault);
            }

            msgSend(alert, sel("setAccessoryView:"), textField);

            long result = msgSendLong(alert, sel("runModal"));
            // NSAlertFirstButtonReturn = 1000 (OK)
            if (result == 1000) {
                MemorySegment nsValue = msgSend(textField, sel("stringValue"));
                return nsStringToJava(nsValue);
            }
            return null;
        } catch (Throwable t) {
            System.err.println("[MacOSDialogEngine] showPrompt failed: " + t.getMessage());
            t.printStackTrace();
            throw new RuntimeException("Failed to show prompt dialog", t);
        }
    }

    private List<String> showOpenPanel(boolean directories, boolean multiSelect, FileDialogOptions options) throws Throwable {
        MemorySegment panelClass = getClass("NSOpenPanel");
        MemorySegment panel = msgSend(panelClass, sel("openPanel"));

        msgSend(panel, sel("setCanChooseFiles:"), !directories);
        msgSend(panel, sel("setCanChooseDirectories:"), directories);
        msgSend(panel, sel("setAllowsMultipleSelection:"), multiSelect);

        if (options.title() != null) {
            MemorySegment nsTitle = createNSString(options.title());
            msgSend(panel, sel("setTitle:"), nsTitle);
        }
        if (options.defaultPath() != null) {
            MemorySegment nsUrlClass = getClass("NSURL");
            MemorySegment nsPath = createNSString(options.defaultPath());
            MemorySegment url = msgSend(nsUrlClass, sel("fileURLWithPath:"), nsPath);
            msgSend(panel, sel("setDirectoryURL:"), url);
        }

        long result = msgSendLong(panel, sel("runModal"));

        if (result == NS_MODAL_RESPONSE_OK) {
            MemorySegment urls = msgSend(panel, sel("URLs"));

            // For single selection, just get the first URL
            if (!multiSelect) {
                MemorySegment url = msgSend(urls, sel("firstObject"));
                if (url.address() != 0) {
                    MemorySegment path = msgSend(url, sel("path"));
                    String pathStr = nsStringToJava(path);
                    if (pathStr != null) {
                        return List.of(pathStr);
                    }
                }
                return null;
            }

            // For multi-selection, iterate using enumerator
            List<String> paths = new ArrayList<>();
            MemorySegment enumerator = msgSend(urls, sel("objectEnumerator"));
            while (true) {
                MemorySegment url = msgSend(enumerator, sel("nextObject"));
                if (url.address() == 0) {
                    break;
                }
                MemorySegment path = msgSend(url, sel("path"));
                String pathStr = nsStringToJava(path);
                if (pathStr != null) {
                    paths.add(pathStr);
                }
            }
            return paths.isEmpty() ? null : paths;
        }
        return null;
    }

    private String showSavePanel(FileDialogOptions options) throws Throwable {
        MemorySegment panelClass = getClass("NSSavePanel");
        MemorySegment panel = msgSend(panelClass, sel("savePanel"));

        if (options.title() != null) {
            MemorySegment nsTitle = createNSString(options.title());
            msgSend(panel, sel("setTitle:"), nsTitle);
        }
        if (options.defaultPath() != null) {
            java.nio.file.Path path = java.nio.file.Path.of(options.defaultPath());
            java.nio.file.Path parent = path.getParent();
            String fileName = path.getFileName().toString();

            if (parent != null) {
                MemorySegment nsUrlClass = getClass("NSURL");
                MemorySegment nsParent = createNSString(parent.toString());
                MemorySegment dirUrl = msgSend(nsUrlClass, sel("fileURLWithPath:"), nsParent);
                msgSend(panel, sel("setDirectoryURL:"), dirUrl);
            }
            MemorySegment nsFileName = createNSString(fileName);
            msgSend(panel, sel("setNameFieldStringValue:"), nsFileName);
        }

        long result = msgSendLong(panel, sel("runModal"));

        if (result == NS_MODAL_RESPONSE_OK) {
            MemorySegment url = msgSend(panel, sel("URL"));
            MemorySegment path = msgSend(url, sel("path"));
            return nsStringToJava(path);
        }
        return null;
    }

    private MemorySegment createAlert(String title, String message, MessageType type) throws Throwable {
        MemorySegment alertClass = getClass("NSAlert");
        MemorySegment alert = msgSend(alertClass, SEL_ALLOC);
        alert = msgSend(alert, SEL_INIT);

        long alertStyle = switch (type) {
            case INFO -> 1;
            case WARNING -> 0;
            case ERROR -> 2;
        };
        msgSend(alert, sel("setAlertStyle:"), alertStyle);

        if (message != null) {
            MemorySegment nsMessage = createNSString(message);
            msgSend(alert, sel("setMessageText:"), nsMessage);
        }
        if (title != null) {
            MemorySegment nsTitle = createNSString(title);
            msgSend(alert, sel("setInformativeText:"), nsTitle);
        }

        return alert;
    }

    // ===== Objective-C helpers =====

    private MemorySegment getClass(String name) {
        try {
            MemorySegment namePtr = arena.allocateFrom(name);
            return (MemorySegment) OBJC_GET_CLASS.invokeExact(namePtr);
        } catch (Throwable t) {
            throw new RuntimeException("Failed to get class: " + name, t);
        }
    }

    private static MemorySegment sel(String name) {
        try {
            try (Arena temp = Arena.ofConfined()) {
                MemorySegment namePtr = temp.allocateFrom(name);
                return (MemorySegment) SEL_REGISTER_NAME.invokeExact(namePtr);
            }
        } catch (Throwable t) {
            throw new RuntimeException("Failed to get selector: " + name, t);
        }
    }

    private MemorySegment msgSend(MemorySegment target, MemorySegment selector) throws Throwable {
        return (MemorySegment) OBJC_MSG_SEND.invokeExact(target, selector);
    }

    private MemorySegment msgSend(MemorySegment target, MemorySegment selector, MemorySegment arg) throws Throwable {
        MethodHandle mh = LINKER.downcallHandle(
            OBJC_LOOKUP.find("objc_msgSend").orElseThrow(),
            FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS)
        );
        return (MemorySegment) mh.invokeExact(target, selector, arg);
    }

    private void msgSend(MemorySegment target, MemorySegment selector, boolean arg) throws Throwable {
        MethodHandle mh = LINKER.downcallHandle(
            OBJC_LOOKUP.find("objc_msgSend").orElseThrow(),
            FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT)
        );
        mh.invokeExact(target, selector, arg ? 1 : 0);
    }

    private MemorySegment msgSend(MemorySegment target, MemorySegment selector, long arg) throws Throwable {
        MethodHandle mh = LINKER.downcallHandle(
            OBJC_LOOKUP.find("objc_msgSend").orElseThrow(),
            FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG)
        );
        return (MemorySegment) mh.invokeExact(target, selector, arg);
    }

    private MemorySegment msgSendWithRect(MemorySegment target, MemorySegment selector, MemorySegment rect) throws Throwable {
        MethodHandle mh = LINKER.downcallHandle(
            OBJC_LOOKUP.find("objc_msgSend").orElseThrow(),
            FunctionDescriptor.of(
                ValueLayout.ADDRESS,
                ValueLayout.ADDRESS,
                ValueLayout.ADDRESS,
                ValueLayout.JAVA_DOUBLE, ValueLayout.JAVA_DOUBLE,
                ValueLayout.JAVA_DOUBLE, ValueLayout.JAVA_DOUBLE
            )
        );
        return (MemorySegment) mh.invokeExact(
            target, selector,
            rect.get(ValueLayout.JAVA_DOUBLE, 0),
            rect.get(ValueLayout.JAVA_DOUBLE, 8),
            rect.get(ValueLayout.JAVA_DOUBLE, 16),
            rect.get(ValueLayout.JAVA_DOUBLE, 24)
        );
    }

    private long msgSendLong(MemorySegment target, MemorySegment selector) throws Throwable {
        return (long) OBJC_MSG_SEND_LONG.invokeExact(target, selector);
    }

    private MemorySegment createNSString(String str) throws Throwable {
        // Use CoreFoundation's CFStringCreateWithCString - pure C function, more reliable with FFM
        // CFStringRef is toll-free bridged with NSString
        if (str == null) {
            str = "";
        }
        MemorySegment utf8Ptr = arena.allocateFrom(str);
        MemorySegment result = (MemorySegment) CF_STRING_CREATE.invokeExact(
            MemorySegment.NULL,  // Use default allocator
            utf8Ptr,
            kCFStringEncodingUTF8
        );

        if (result.address() == 0) {
            throw new RuntimeException("Failed to create CFString for: " + str);
        }

        return result;
    }

    private String nsStringToJava(MemorySegment nsString) throws Throwable {
        MemorySegment utf8 = msgSend(nsString, sel("UTF8String"));
        if (utf8.address() == 0) {
            return null;
        }
        return utf8.reinterpret(Integer.MAX_VALUE).getString(0);
    }

    private MemorySegment createNSArray(List<String> strings) throws Throwable {
        MemorySegment arrayClass = getClass("NSMutableArray");
        MemorySegment array = msgSend(arrayClass, SEL_ALLOC);
        array = msgSend(array, SEL_INIT);

        for (String s : strings) {
            MemorySegment nsStr = createNSString(s);
            msgSend(array, sel("addObject:"), nsStr);
        }

        return array;
    }
}
