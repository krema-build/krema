package build.krema.core.dock.macos;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;

import build.krema.core.dock.DockEngine;

/**
 * macOS DockEngine implementation using NSDockTile via FFM.
 */
public final class MacOSDockEngine implements DockEngine {

    private static final Linker LINKER = Linker.nativeLinker();
    private static final SymbolLookup OBJC_LOOKUP;

    // Objective-C runtime functions
    private static final MethodHandle OBJC_GET_CLASS;
    private static final MethodHandle SEL_REGISTER_NAME;
    private static final MethodHandle OBJC_MSG_SEND;
    private static final MethodHandle OBJC_MSG_SEND_LONG;
    private static final MethodHandle OBJC_MSG_SEND_WITH_LONG;
    private static final MethodHandle OBJC_MSG_SEND_WITH_PTR;

    // Selectors
    private static final MemorySegment SEL_SHARED_APPLICATION;
    private static final MemorySegment SEL_DOCK_TILE;
    private static final MemorySegment SEL_SET_BADGE_LABEL;
    private static final MemorySegment SEL_BADGE_LABEL;
    private static final MemorySegment SEL_DISPLAY;
    private static final MemorySegment SEL_REQUEST_USER_ATTENTION;
    private static final MemorySegment SEL_CANCEL_USER_ATTENTION_REQUEST;
    private static final MemorySegment SEL_UTF8_STRING;

    // NSRequestUserAttentionType
    private static final long NS_CRITICAL_REQUEST = 0;
    private static final long NS_INFORMATIONAL_REQUEST = 10;

    private final Arena arena = Arena.ofAuto();
    private String currentBadge = "";

    static {
        OBJC_LOOKUP = SymbolLookup.libraryLookup("/usr/lib/libobjc.dylib", Arena.global());

        // Load AppKit framework
        SymbolLookup.libraryLookup(
            "/System/Library/Frameworks/AppKit.framework/AppKit",
            Arena.global()
        );

        // objc_getClass
        OBJC_GET_CLASS = LINKER.downcallHandle(
            OBJC_LOOKUP.find("objc_getClass").orElseThrow(),
            FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS)
        );

        // sel_registerName
        SEL_REGISTER_NAME = LINKER.downcallHandle(
            OBJC_LOOKUP.find("sel_registerName").orElseThrow(),
            FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS)
        );

        // objc_msgSend -> id (no firstVariadicArg for ARM64 compatibility)
        OBJC_MSG_SEND = LINKER.downcallHandle(
            OBJC_LOOKUP.find("objc_msgSend").orElseThrow(),
            FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS)
        );

        // objc_msgSend -> long
        OBJC_MSG_SEND_LONG = LINKER.downcallHandle(
            OBJC_LOOKUP.find("objc_msgSend").orElseThrow(),
            FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.ADDRESS)
        );

        // objc_msgSend with int parameter for requestUserAttention:
        // Try JAVA_INT since the enum values fit in 32 bits
        OBJC_MSG_SEND_WITH_LONG = LINKER.downcallHandle(
            OBJC_LOOKUP.find("objc_msgSend").orElseThrow(),
            FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT)
        );

        // objc_msgSend with pointer parameter (for setBadgeLabel:)
        OBJC_MSG_SEND_WITH_PTR = LINKER.downcallHandle(
            OBJC_LOOKUP.find("objc_msgSend").orElseThrow(),
            FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS)
        );

        // Cache selectors
        SEL_SHARED_APPLICATION = sel("sharedApplication");
        SEL_DOCK_TILE = sel("dockTile");
        SEL_SET_BADGE_LABEL = sel("setBadgeLabel:");
        SEL_BADGE_LABEL = sel("badgeLabel");
        SEL_DISPLAY = sel("display");
        SEL_REQUEST_USER_ATTENTION = sel("requestUserAttention:");
        SEL_CANCEL_USER_ATTENTION_REQUEST = sel("cancelUserAttentionRequest:");
        SEL_UTF8_STRING = sel("UTF8String");
    }

    @Override
    public void setBadge(String text) {
        try {
            MemorySegment nsApp = getSharedApplication();
            MemorySegment dockTile = msgSend(nsApp, SEL_DOCK_TILE);

            if (dockTile.address() == 0) {
                System.err.println("[Dock] No dock tile available");
                return;
            }

            // Create NSString for badge label (or nil to clear)
            MemorySegment badgeString;
            if (text == null || text.isEmpty()) {
                badgeString = MemorySegment.NULL;
                currentBadge = "";
            } else {
                badgeString = createNSString(text);
                currentBadge = text;
            }

            // Set badge label
            msgSendWithPtr(dockTile, SEL_SET_BADGE_LABEL, badgeString);

            // Force display update
            msgSend(dockTile, SEL_DISPLAY);

            System.out.println("[Dock] Badge set to: " + (text == null || text.isEmpty() ? "(cleared)" : text));
        } catch (Throwable t) {
            System.err.println("[Dock] Failed to set badge: " + t.getMessage());
        }
    }

    @Override
    public String getBadge() {
        return currentBadge;
    }

    @Override
    public void clearBadge() {
        setBadge(null);
    }

    @Override
    public long requestAttention(boolean critical) {
        // Note: requestUserAttention: has FFM calling convention issues on ARM64.
        // The method is being called but returns -1 consistently, indicating
        // a mismatch in how integer parameters/returns are handled.
        // Badge functionality works correctly - bounce is a known limitation.
        System.out.println("[Dock] Bounce not yet supported on ARM64 (FFM calling convention issue)");
        System.out.println("[Dock] Badge functionality works - use dock:setBadge instead");
        return 0;
    }

    @Override
    public void cancelAttention(long requestId) {
        // Not implemented - bounce is not supported on ARM64
    }

    @Override
    public boolean isSupported() {
        return true;
    }

    // ===== Helper methods =====

    private MemorySegment getSharedApplication() throws Throwable {
        MemorySegment nsAppClass = getClass("NSApplication");
        return msgSend(nsAppClass, SEL_SHARED_APPLICATION);
    }

    private MemorySegment getClass(String name) throws Throwable {
        MemorySegment namePtr = arena.allocateFrom(name);
        return (MemorySegment) OBJC_GET_CLASS.invokeExact(namePtr);
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

    private long msgSendWithLong(MemorySegment target, MemorySegment selector, long arg) throws Throwable {
        return (long) OBJC_MSG_SEND_WITH_LONG.invokeExact(target, selector, arg);
    }

    private void msgSendWithPtr(MemorySegment target, MemorySegment selector, MemorySegment arg) throws Throwable {
        OBJC_MSG_SEND_WITH_PTR.invokeExact(target, selector, arg);
    }

    private MemorySegment createNSString(String str) throws Throwable {
        // Use CFStringCreateWithCString for reliable NSString creation
        MemorySegment cfStringCreate = SymbolLookup.loaderLookup()
            .or(SymbolLookup.libraryLookup("/System/Library/Frameworks/CoreFoundation.framework/CoreFoundation", Arena.global()))
            .find("CFStringCreateWithCString")
            .orElseThrow();

        MethodHandle cfStringCreateHandle = LINKER.downcallHandle(
            cfStringCreate,
            FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT)
        );

        MemorySegment cString = arena.allocateFrom(str);
        // kCFStringEncodingUTF8 = 0x08000100
        return (MemorySegment) cfStringCreateHandle.invokeExact(MemorySegment.NULL, cString, 0x08000100);
    }
}
