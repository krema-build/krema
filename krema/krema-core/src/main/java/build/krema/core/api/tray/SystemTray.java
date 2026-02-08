package build.krema.core.api.tray;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import build.krema.core.KremaCommand;
import build.krema.core.platform.Platform;
import build.krema.core.platform.linux.GtkBindings;
import build.krema.core.platform.windows.Win32Bindings;

/**
 * System tray icon and menu management.
 * Uses platform-native APIs: NSStatusBar (macOS), Shell_NotifyIcon (Windows), AppIndicator (Linux).
 */
public class SystemTray {

    private MemorySegment statusItem;
    private Consumer<String> menuClickHandler;
    private final Map<Long, String> tagToIdMap = new ConcurrentHashMap<>();
    private long nextTag = 2000;

    private static volatile SystemTray activeInstance;

    @KremaCommand("tray:isSupported")
    public boolean isSupported() {
        Platform platform = Platform.current();
        return platform == Platform.MACOS
            || platform == Platform.WINDOWS
            || (platform == Platform.LINUX && Linux.AVAILABLE);
    }

    @KremaCommand("tray:create")
    public boolean create(String tooltip, String iconPath) {
        Platform platform = Platform.current();
        if (platform == Platform.MACOS) {
            try {
                activeInstance = this;
                MacOS.ensureDelegateCreated();
                statusItem = MacOS.createStatusItem();
                if (tooltip != null) {
                    MacOS.setTooltip(statusItem, tooltip);
                }
                if (iconPath != null && !iconPath.isEmpty()) {
                    MacOS.setIcon(statusItem, iconPath);
                } else {
                    MacOS.setDefaultTitle(statusItem, tooltip);
                }
                return true;
            } catch (Throwable t) {
                System.err.println("[SystemTray] Failed to create: " + t.getMessage());
                return false;
            }
        } else if (platform == Platform.WINDOWS) {
            try {
                activeInstance = this;
                if (!Windows.create(tooltip, iconPath)) return false;
                statusItem = Windows.hiddenHwnd;
                return true;
            } catch (Throwable t) {
                System.err.println("[SystemTray] Failed to create: " + t.getMessage());
                return false;
            }
        } else if (platform == Platform.LINUX && Linux.AVAILABLE) {
            try {
                activeInstance = this;
                statusItem = Linux.createIndicator(tooltip, iconPath);
                return true;
            } catch (Throwable t) {
                System.err.println("[SystemTray] Failed to create: " + t.getMessage());
                return false;
            }
        }
        return false;
    }

    @KremaCommand("tray:setTooltip")
    public boolean setTooltip(String tooltip) {
        try {
            Platform platform = Platform.current();
            if (platform == Platform.MACOS) {
                if (statusItem == null) return false;
                MacOS.setTooltip(statusItem, tooltip);
            } else if (platform == Platform.WINDOWS) {
                if (statusItem == null) return false;
                Windows.setTooltip(tooltip);
            } else if (platform == Platform.LINUX) {
                if (statusItem == null) return false;
                Linux.setTooltip(statusItem, tooltip);
            }
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    @KremaCommand("tray:setMenu")
    public boolean setMenu(List<Map<String, Object>> menuItems) {
        try {
            Platform platform = Platform.current();
            if (platform == Platform.MACOS) {
                if (statusItem == null) return false;
                MacOS.setMenu(statusItem, menuItems, tagToIdMap, nextTag);
                nextTag += menuItems.size();
            } else if (platform == Platform.WINDOWS) {
                if (statusItem == null) return false;
                Windows.setMenu(menuItems);
            } else if (platform == Platform.LINUX) {
                if (statusItem == null) return false;
                Linux.setMenu(statusItem, menuItems);
            }
            return true;
        } catch (Throwable t) {
            System.err.println("[SystemTray] Failed to set menu: " + t.getMessage());
            return false;
        }
    }

    public void setMenuClickHandler(Consumer<String> handler) {
        this.menuClickHandler = handler;
    }

    @KremaCommand("tray:remove")
    public boolean remove() {
        try {
            Platform platform = Platform.current();
            if (platform == Platform.MACOS) {
                if (statusItem == null) return false;
                MacOS.removeStatusItem(statusItem);
                statusItem = null;
            } else if (platform == Platform.WINDOWS) {
                if (statusItem == null) return false;
                Windows.remove();
                statusItem = null;
            } else if (platform == Platform.LINUX) {
                if (statusItem == null) return false;
                Linux.remove(statusItem);
                statusItem = null;
            } else {
                return false;
            }
            tagToIdMap.clear();
            if (activeInstance == this) {
                activeInstance = null;
            }
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    @KremaCommand("tray:displayMessage")
    public boolean displayMessage(String caption, String text, String messageType) {
        Platform platform = Platform.current();
        if (platform == Platform.MACOS) {
            try {
                String script = String.format(
                    "display notification \"%s\" with title \"%s\"",
                    escapeAppleScript(text), escapeAppleScript(caption)
                );
                new ProcessBuilder("osascript", "-e", script).start().waitFor();
                return true;
            } catch (Exception e) {
                return false;
            }
        } else if (platform == Platform.WINDOWS) {
            try {
                return Windows.displayMessage(caption, text, messageType);
            } catch (Throwable t) {
                return false;
            }
        } else if (platform == Platform.LINUX) {
            try {
                new ProcessBuilder("notify-send", caption, text).start().waitFor();
                return true;
            } catch (Exception e) {
                return false;
            }
        }
        return false;
    }

    private static String escapeAppleScript(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    @SuppressWarnings("unused")
    private static void handleMenuClick(MemorySegment self, MemorySegment cmd, MemorySegment sender) {
        SystemTray tray = activeInstance;
        if (tray == null) return;
        try {
            long tag = MacOS.getTag(sender);
            String itemId = tray.tagToIdMap.get(tag);
            if (itemId != null && tray.menuClickHandler != null) {
                tray.menuClickHandler.accept(itemId);
            }
        } catch (Throwable t) {
            System.err.println("[SystemTray] Error handling menu click: " + t.getMessage());
        }
    }

    @SuppressWarnings("unused")
    private static void handleLinuxMenuClick(MemorySegment widget, MemorySegment userData) {
        SystemTray tray = activeInstance;
        if (tray == null) return;
        String itemId = Linux.widgetToId.get(userData.address());
        if (itemId != null && tray.menuClickHandler != null) {
            tray.menuClickHandler.accept(itemId);
        }
    }

    // ===== macOS: NSStatusBar via FFM =====

    private static final class MacOS {

        private static final Linker LINKER = Linker.nativeLinker();
        private static final SymbolLookup OBJC_LOOKUP;

        private static final MethodHandle OBJC_GET_CLASS;
        private static final MethodHandle SEL_REGISTER_NAME;
        private static final MethodHandle OBJC_MSG_SEND;           // (id, SEL) -> id
        private static final MethodHandle OBJC_MSG_SEND_LONG;      // (id, SEL) -> long
        private static final MethodHandle OBJC_MSG_SEND_WITH_PTR;  // (id, SEL, id) -> void
        private static final MethodHandle OBJC_MSG_SEND_1PTR;      // (id, SEL, id) -> id
        private static final MethodHandle OBJC_MSG_SEND_WITH_INT;  // (id, SEL, int) -> void
        private static final MethodHandle OBJC_MSG_SEND_WITH_LONG; // (id, SEL, long) -> void
        private static final MethodHandle OBJC_MSG_SEND_DOUBLE_RET;// (id, SEL, double) -> id
        private static final MethodHandle OBJC_MSG_SEND_2DOUBLE;   // (id, SEL, double, double) -> void
        private static final MethodHandle OBJC_MSG_SEND_WITH_BOOL; // (id, SEL, bool) -> void
        private static final MethodHandle OBJC_MSG_SEND_5PTR;      // (id, SEL, id, id, id) -> id
        private static final MethodHandle OBJC_MSG_SEND_PTR_LONG;  // (id, SEL, id, long) -> id

        // ObjC runtime class creation
        private static final MethodHandle OBJC_ALLOCATE_CLASS_PAIR;
        private static final MethodHandle OBJC_REGISTER_CLASS_PAIR;
        private static final MethodHandle CLASS_ADD_METHOD;

        private static final MethodHandle CF_STRING_CREATE;

        // Cached selectors
        private static final MemorySegment SEL_SYSTEM_STATUS_BAR;
        private static final MemorySegment SEL_STATUS_ITEM_WITH_LENGTH;
        private static final MemorySegment SEL_BUTTON;
        private static final MemorySegment SEL_SET_TITLE;
        private static final MemorySegment SEL_SET_IMAGE;
        private static final MemorySegment SEL_SET_TOOL_TIP;
        private static final MemorySegment SEL_SET_MENU;
        private static final MemorySegment SEL_REMOVE_STATUS_ITEM;
        private static final MemorySegment SEL_ALLOC;
        private static final MemorySegment SEL_INIT;
        private static final MemorySegment SEL_INIT_WITH_TITLE_ACTION_KEY;
        private static final MemorySegment SEL_ADD_ITEM;
        private static final MemorySegment SEL_SEPARATOR_ITEM;
        private static final MemorySegment SEL_SET_ENABLED;
        private static final MemorySegment SEL_SET_TARGET;
        private static final MemorySegment SEL_SET_ACTION;
        private static final MemorySegment SEL_SET_TAG;
        private static final MemorySegment SEL_TAG;
        private static final MemorySegment SEL_SET_SIZE;
        private static final MemorySegment SEL_SET_TEMPLATE;
        private static final MemorySegment SEL_INIT_WITH_CONTENTS_OF_FILE;
        private static final MemorySegment SEL_INIT_WITH_DATA;
        private static final MemorySegment SEL_DATA_WITH_BYTES_LENGTH;

        private static final Arena ARENA = Arena.ofAuto();

        private static MemorySegment delegateInstance;
        private static MemorySegment menuClickedSelector;
        private static MemorySegment upcallStub;
        private static boolean delegateCreated = false;

        static {
            OBJC_LOOKUP = SymbolLookup.libraryLookup("/usr/lib/libobjc.dylib", Arena.global());

            SymbolLookup.libraryLookup(
                "/System/Library/Frameworks/AppKit.framework/AppKit",
                Arena.global()
            );

            SymbolLookup cfLookup = SymbolLookup.libraryLookup(
                "/System/Library/Frameworks/CoreFoundation.framework/CoreFoundation",
                Arena.global()
            );

            OBJC_GET_CLASS = LINKER.downcallHandle(
                OBJC_LOOKUP.find("objc_getClass").orElseThrow(),
                FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS)
            );

            SEL_REGISTER_NAME = LINKER.downcallHandle(
                OBJC_LOOKUP.find("sel_registerName").orElseThrow(),
                FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS)
            );

            OBJC_MSG_SEND = LINKER.downcallHandle(
                OBJC_LOOKUP.find("objc_msgSend").orElseThrow(),
                FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS)
            );

            OBJC_MSG_SEND_LONG = LINKER.downcallHandle(
                OBJC_LOOKUP.find("objc_msgSend").orElseThrow(),
                FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.ADDRESS)
            );

            OBJC_MSG_SEND_WITH_PTR = LINKER.downcallHandle(
                OBJC_LOOKUP.find("objc_msgSend").orElseThrow(),
                FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS)
            );

            OBJC_MSG_SEND_1PTR = LINKER.downcallHandle(
                OBJC_LOOKUP.find("objc_msgSend").orElseThrow(),
                FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS)
            );

            OBJC_MSG_SEND_WITH_INT = LINKER.downcallHandle(
                OBJC_LOOKUP.find("objc_msgSend").orElseThrow(),
                FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT)
            );

            OBJC_MSG_SEND_WITH_LONG = LINKER.downcallHandle(
                OBJC_LOOKUP.find("objc_msgSend").orElseThrow(),
                FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG)
            );

            OBJC_MSG_SEND_DOUBLE_RET = LINKER.downcallHandle(
                OBJC_LOOKUP.find("objc_msgSend").orElseThrow(),
                FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_DOUBLE)
            );

            OBJC_MSG_SEND_2DOUBLE = LINKER.downcallHandle(
                OBJC_LOOKUP.find("objc_msgSend").orElseThrow(),
                FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_DOUBLE, ValueLayout.JAVA_DOUBLE)
            );

            OBJC_MSG_SEND_WITH_BOOL = LINKER.downcallHandle(
                OBJC_LOOKUP.find("objc_msgSend").orElseThrow(),
                FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_BOOLEAN)
            );

            OBJC_MSG_SEND_5PTR = LINKER.downcallHandle(
                OBJC_LOOKUP.find("objc_msgSend").orElseThrow(),
                FunctionDescriptor.of(
                    ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS,
                    ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS
                )
            );

            OBJC_MSG_SEND_PTR_LONG = LINKER.downcallHandle(
                OBJC_LOOKUP.find("objc_msgSend").orElseThrow(),
                FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG)
            );

            OBJC_ALLOCATE_CLASS_PAIR = LINKER.downcallHandle(
                OBJC_LOOKUP.find("objc_allocateClassPair").orElseThrow(),
                FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG)
            );

            OBJC_REGISTER_CLASS_PAIR = LINKER.downcallHandle(
                OBJC_LOOKUP.find("objc_registerClassPair").orElseThrow(),
                FunctionDescriptor.ofVoid(ValueLayout.ADDRESS)
            );

            CLASS_ADD_METHOD = LINKER.downcallHandle(
                OBJC_LOOKUP.find("class_addMethod").orElseThrow(),
                FunctionDescriptor.of(ValueLayout.JAVA_BOOLEAN, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS)
            );

            CF_STRING_CREATE = LINKER.downcallHandle(
                cfLookup.find("CFStringCreateWithCString").orElseThrow(),
                FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT)
            );

            SEL_SYSTEM_STATUS_BAR = sel("systemStatusBar");
            SEL_STATUS_ITEM_WITH_LENGTH = sel("statusItemWithLength:");
            SEL_BUTTON = sel("button");
            SEL_SET_TITLE = sel("setTitle:");
            SEL_SET_IMAGE = sel("setImage:");
            SEL_SET_TOOL_TIP = sel("setToolTip:");
            SEL_SET_MENU = sel("setMenu:");
            SEL_REMOVE_STATUS_ITEM = sel("removeStatusItem:");
            SEL_ALLOC = sel("alloc");
            SEL_INIT = sel("init");
            SEL_INIT_WITH_TITLE_ACTION_KEY = sel("initWithTitle:action:keyEquivalent:");
            SEL_ADD_ITEM = sel("addItem:");
            SEL_SEPARATOR_ITEM = sel("separatorItem");
            SEL_SET_ENABLED = sel("setEnabled:");
            SEL_SET_TARGET = sel("setTarget:");
            SEL_SET_ACTION = sel("setAction:");
            SEL_SET_TAG = sel("setTag:");
            SEL_TAG = sel("tag");
            SEL_SET_SIZE = sel("setSize:");
            SEL_SET_TEMPLATE = sel("setTemplate:");
            SEL_INIT_WITH_CONTENTS_OF_FILE = sel("initWithContentsOfFile:");
            SEL_INIT_WITH_DATA = sel("initWithData:");
            SEL_DATA_WITH_BYTES_LENGTH = sel("dataWithBytes:length:");
        }

        static synchronized void ensureDelegateCreated() throws Throwable {
            if (delegateCreated) return;

            MemorySegment nsObjectClass = getClass("NSObject");
            MemorySegment className = ARENA.allocateFrom("KremaTrayDelegate");

            MemorySegment delegateClass = (MemorySegment) OBJC_ALLOCATE_CLASS_PAIR.invokeExact(
                nsObjectClass, className, 0L
            );

            if (delegateClass.address() == 0) {
                delegateClass = getClass("KremaTrayDelegate");
            } else {
                menuClickedSelector = sel("trayMenuItemClicked:");
                MemorySegment typeEncoding = ARENA.allocateFrom("v@:@");

                FunctionDescriptor callbackDescriptor = FunctionDescriptor.ofVoid(
                    ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS
                );

                MethodHandle callbackHandle = MethodHandles.lookup().findStatic(
                    SystemTray.class,
                    "handleMenuClick",
                    MethodType.methodType(void.class, MemorySegment.class, MemorySegment.class, MemorySegment.class)
                );

                upcallStub = LINKER.upcallStub(callbackHandle, callbackDescriptor, ARENA);

                CLASS_ADD_METHOD.invokeExact(delegateClass, menuClickedSelector, upcallStub, typeEncoding);
                OBJC_REGISTER_CLASS_PAIR.invokeExact(delegateClass);
            }

            if (menuClickedSelector == null) {
                menuClickedSelector = sel("trayMenuItemClicked:");
            }

            MemorySegment allocated = (MemorySegment) OBJC_MSG_SEND.invokeExact(delegateClass, SEL_ALLOC);
            delegateInstance = (MemorySegment) OBJC_MSG_SEND.invokeExact(allocated, SEL_INIT);
            delegateCreated = true;
        }

        static MemorySegment createStatusItem() throws Throwable {
            MemorySegment sbClass = getClass("NSStatusBar");
            MemorySegment statusBar = (MemorySegment) OBJC_MSG_SEND.invokeExact(sbClass, SEL_SYSTEM_STATUS_BAR);
            // NSVariableStatusItemLength = -1.0
            return (MemorySegment) OBJC_MSG_SEND_DOUBLE_RET.invokeExact(
                statusBar, SEL_STATUS_ITEM_WITH_LENGTH, -1.0
            );
        }

        static void setTooltip(MemorySegment statusItem, String tooltip) throws Throwable {
            MemorySegment button = (MemorySegment) OBJC_MSG_SEND.invokeExact(statusItem, SEL_BUTTON);
            if (button.address() == 0) return;
            MemorySegment nsTooltip = createNSString(tooltip);
            OBJC_MSG_SEND_WITH_PTR.invokeExact(button, SEL_SET_TOOL_TIP, nsTooltip);
        }

        static void setDefaultTitle(MemorySegment statusItem, String tooltip) throws Throwable {
            MemorySegment button = (MemorySegment) OBJC_MSG_SEND.invokeExact(statusItem, SEL_BUTTON);
            if (button.address() == 0) return;
            String title = (tooltip != null && !tooltip.isEmpty()) ? tooltip.substring(0, Math.min(tooltip.length(), 3)) : "\u25CF";
            MemorySegment nsTitle = createNSString(title);
            OBJC_MSG_SEND_WITH_PTR.invokeExact(button, SEL_SET_TITLE, nsTitle);
        }

        static void setIcon(MemorySegment statusItem, String iconPath) throws Throwable {
            MemorySegment image;

            if (iconPath.startsWith("data:image")) {
                String base64 = iconPath.substring(iconPath.indexOf(",") + 1);
                byte[] imageBytes = Base64.getDecoder().decode(base64);
                image = createImageFromBytes(imageBytes);
            } else {
                image = createImageFromFile(iconPath);
            }

            if (image.address() == 0) return;

            // Resize to 18x18 for status bar
            OBJC_MSG_SEND_2DOUBLE.invokeExact(image, SEL_SET_SIZE, 18.0, 18.0);

            // Mark as template image for proper menu bar appearance
            OBJC_MSG_SEND_WITH_BOOL.invokeExact(image, SEL_SET_TEMPLATE, true);

            MemorySegment button = (MemorySegment) OBJC_MSG_SEND.invokeExact(statusItem, SEL_BUTTON);
            if (button.address() != 0) {
                OBJC_MSG_SEND_WITH_PTR.invokeExact(button, SEL_SET_IMAGE, image);
            }
        }

        static void setMenu(MemorySegment statusItem, List<Map<String, Object>> menuItems,
                            Map<Long, String> tagToIdMap, long startTag) throws Throwable {
            MemorySegment menuClass = getClass("NSMenu");
            MemorySegment menu = (MemorySegment) OBJC_MSG_SEND.invokeExact(menuClass, SEL_ALLOC);
            menu = (MemorySegment) OBJC_MSG_SEND.invokeExact(menu, SEL_INIT);

            long tag = startTag;
            for (Map<String, Object> item : menuItems) {
                String label = item.get("label").toString();

                if ("separator".equals(label) || "-".equals(label)) {
                    MemorySegment menuItemClass = getClass("NSMenuItem");
                    MemorySegment separator = (MemorySegment) OBJC_MSG_SEND.invokeExact(
                        menuItemClass, SEL_SEPARATOR_ITEM
                    );
                    OBJC_MSG_SEND_WITH_PTR.invokeExact(menu, SEL_ADD_ITEM, separator);
                } else {
                    MemorySegment nsItem = createMenuItem(label);

                    String id = item.containsKey("id") ? item.get("id").toString() : label;
                    tagToIdMap.put(tag, id);
                    OBJC_MSG_SEND_WITH_LONG.invokeExact(nsItem, SEL_SET_TAG, tag);
                    tag++;

                    if (delegateInstance != null) {
                        OBJC_MSG_SEND_WITH_PTR.invokeExact(nsItem, SEL_SET_TARGET, delegateInstance);
                        OBJC_MSG_SEND_WITH_PTR.invokeExact(nsItem, SEL_SET_ACTION, menuClickedSelector);
                    }

                    if (item.containsKey("enabled")) {
                        boolean enabled = (Boolean) item.get("enabled");
                        OBJC_MSG_SEND_WITH_INT.invokeExact(nsItem, SEL_SET_ENABLED, enabled ? 1 : 0);
                    }

                    OBJC_MSG_SEND_WITH_PTR.invokeExact(menu, SEL_ADD_ITEM, nsItem);
                }
            }

            OBJC_MSG_SEND_WITH_PTR.invokeExact(statusItem, SEL_SET_MENU, menu);
        }

        static void removeStatusItem(MemorySegment statusItem) throws Throwable {
            MemorySegment sbClass = getClass("NSStatusBar");
            MemorySegment statusBar = (MemorySegment) OBJC_MSG_SEND.invokeExact(sbClass, SEL_SYSTEM_STATUS_BAR);
            OBJC_MSG_SEND_WITH_PTR.invokeExact(statusBar, SEL_REMOVE_STATUS_ITEM, statusItem);
        }

        static long getTag(MemorySegment menuItem) throws Throwable {
            return (long) OBJC_MSG_SEND_LONG.invokeExact(menuItem, SEL_TAG);
        }

        private static MemorySegment createMenuItem(String title) throws Throwable {
            MemorySegment menuItemClass = getClass("NSMenuItem");
            MemorySegment item = (MemorySegment) OBJC_MSG_SEND.invokeExact(menuItemClass, SEL_ALLOC);
            MemorySegment nsTitle = createNSString(title);
            MemorySegment emptyStr = createNSString("");
            return (MemorySegment) OBJC_MSG_SEND_5PTR.invokeExact(
                item, SEL_INIT_WITH_TITLE_ACTION_KEY, nsTitle, MemorySegment.NULL, emptyStr
            );
        }

        private static MemorySegment createImageFromFile(String path) throws Throwable {
            MemorySegment imageClass = getClass("NSImage");
            MemorySegment image = (MemorySegment) OBJC_MSG_SEND.invokeExact(imageClass, SEL_ALLOC);
            MemorySegment nsPath = createNSString(path);
            return (MemorySegment) OBJC_MSG_SEND_1PTR.invokeExact(image, SEL_INIT_WITH_CONTENTS_OF_FILE, nsPath);
        }

        private static MemorySegment createImageFromBytes(byte[] bytes) throws Throwable {
            // Create NSData from bytes
            MemorySegment nativeBytes = ARENA.allocate(bytes.length);
            MemorySegment.copy(bytes, 0, nativeBytes, ValueLayout.JAVA_BYTE, 0, bytes.length);

            MemorySegment dataClass = getClass("NSData");
            MemorySegment nsData = (MemorySegment) OBJC_MSG_SEND_PTR_LONG.invokeExact(
                dataClass, SEL_DATA_WITH_BYTES_LENGTH, nativeBytes, (long) bytes.length
            );

            if (nsData.address() == 0) return MemorySegment.NULL;

            // Create NSImage from NSData
            MemorySegment imageClass = getClass("NSImage");
            MemorySegment image = (MemorySegment) OBJC_MSG_SEND.invokeExact(imageClass, SEL_ALLOC);
            return (MemorySegment) OBJC_MSG_SEND_1PTR.invokeExact(image, SEL_INIT_WITH_DATA, nsData);
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

        private static MemorySegment getClass(String name) throws Throwable {
            MemorySegment namePtr = ARENA.allocateFrom(name);
            return (MemorySegment) OBJC_GET_CLASS.invokeExact(namePtr);
        }

        private static MemorySegment createNSString(String str) throws Throwable {
            if (str == null) str = "";
            MemorySegment cString = ARENA.allocateFrom(str);
            return (MemorySegment) CF_STRING_CREATE.invokeExact(
                MemorySegment.NULL, cString, 0x08000100 // kCFStringEncodingUTF8
            );
        }
    }

    // ===== Linux: AppIndicator via FFM =====

    private static final class Linux {

        private static final Linker LINKER = Linker.nativeLinker();
        static final boolean AVAILABLE;

        private static final MethodHandle APP_INDICATOR_NEW;
        private static final MethodHandle APP_INDICATOR_SET_STATUS;
        private static final MethodHandle APP_INDICATOR_SET_MENU;
        private static final MethodHandle APP_INDICATOR_SET_ICON_FULL;
        private static final MethodHandle APP_INDICATOR_SET_TITLE;

        private static final int APP_INDICATOR_CATEGORY_APPLICATION_STATUS = 0;
        private static final int APP_INDICATOR_STATUS_PASSIVE = 0;
        private static final int APP_INDICATOR_STATUS_ACTIVE = 1;

        private static final Arena ARENA = Arena.ofAuto();
        static final Map<Long, String> widgetToId = new ConcurrentHashMap<>();
        private static MemorySegment activateStub;

        static {
            SymbolLookup lib = null;
            boolean available = false;
            try {
                lib = SymbolLookup.libraryLookup("libayatana-appindicator3.so.1", Arena.global());
                available = true;
            } catch (Throwable ignored) {
                try {
                    lib = SymbolLookup.libraryLookup("libappindicator3.so.1", Arena.global());
                    available = true;
                } catch (Throwable ignored2) {
                    // Neither library available
                }
            }
            AVAILABLE = available;

            if (available) {
                APP_INDICATOR_NEW = LINKER.downcallHandle(
                    lib.find("app_indicator_new").orElseThrow(),
                    FunctionDescriptor.of(ValueLayout.ADDRESS,
                        ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT)
                );
                APP_INDICATOR_SET_STATUS = LINKER.downcallHandle(
                    lib.find("app_indicator_set_status").orElseThrow(),
                    FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_INT)
                );
                APP_INDICATOR_SET_MENU = LINKER.downcallHandle(
                    lib.find("app_indicator_set_menu").orElseThrow(),
                    FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.ADDRESS)
                );
                APP_INDICATOR_SET_ICON_FULL = LINKER.downcallHandle(
                    lib.find("app_indicator_set_icon_full").orElseThrow(),
                    FunctionDescriptor.ofVoid(ValueLayout.ADDRESS,
                        ValueLayout.ADDRESS, ValueLayout.ADDRESS)
                );
                APP_INDICATOR_SET_TITLE = LINKER.downcallHandle(
                    lib.find("app_indicator_set_title").orElseThrow(),
                    FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.ADDRESS)
                );

                try {
                    FunctionDescriptor descriptor = FunctionDescriptor.ofVoid(
                        ValueLayout.ADDRESS, ValueLayout.ADDRESS
                    );
                    MethodHandle handler = MethodHandles.lookup().findStatic(
                        SystemTray.class,
                        "handleLinuxMenuClick",
                        MethodType.methodType(void.class, MemorySegment.class, MemorySegment.class)
                    );
                    activateStub = LINKER.upcallStub(handler, descriptor, ARENA);
                } catch (Throwable t) {
                    throw new RuntimeException("Failed to create tray menu upcall stub", t);
                }
            } else {
                APP_INDICATOR_NEW = null;
                APP_INDICATOR_SET_STATUS = null;
                APP_INDICATOR_SET_MENU = null;
                APP_INDICATOR_SET_ICON_FULL = null;
                APP_INDICATOR_SET_TITLE = null;
            }
        }

        static MemorySegment createIndicator(String tooltip, String iconPath) throws Throwable {
            MemorySegment idPtr = ARENA.allocateFrom("krema-app");

            // Use file path directly if provided, otherwise fall back to theme icon
            String initialIcon;
            if (iconPath != null && !iconPath.isEmpty() && !iconPath.startsWith("data:image")) {
                initialIcon = iconPath;
            } else {
                initialIcon = "application-default-icon";
            }
            MemorySegment iconPtr = ARENA.allocateFrom(initialIcon);

            MemorySegment indicator = (MemorySegment) APP_INDICATOR_NEW.invokeExact(
                idPtr, iconPtr, APP_INDICATOR_CATEGORY_APPLICATION_STATUS
            );
            APP_INDICATOR_SET_STATUS.invokeExact(indicator, APP_INDICATOR_STATUS_ACTIVE);

            if (tooltip != null) {
                setTooltip(indicator, tooltip);
            }

            // Handle base64 icon data by writing to temp file
            if (iconPath != null && iconPath.startsWith("data:image")) {
                setIcon(indicator, iconPath);
            }

            return indicator;
        }

        static void setIcon(MemorySegment indicator, String iconPath) throws Throwable {
            String resolvedPath;
            if (iconPath.startsWith("data:image")) {
                String base64 = iconPath.substring(iconPath.indexOf(",") + 1);
                byte[] bytes = Base64.getDecoder().decode(base64);
                Path tmpFile = Files.createTempFile("krema-tray-icon-", ".png");
                Files.write(tmpFile, bytes);
                tmpFile.toFile().deleteOnExit();
                resolvedPath = tmpFile.toAbsolutePath().toString();
            } else {
                resolvedPath = iconPath;
            }
            MemorySegment pathPtr = ARENA.allocateFrom(resolvedPath);
            MemorySegment descPtr = ARENA.allocateFrom("");
            APP_INDICATOR_SET_ICON_FULL.invokeExact(indicator, pathPtr, descPtr);
        }

        static void setTooltip(MemorySegment indicator, String tooltip) throws Throwable {
            // AppIndicator uses set_title as the closest equivalent to a tooltip
            MemorySegment titlePtr = ARENA.allocateFrom(tooltip != null ? tooltip : "");
            APP_INDICATOR_SET_TITLE.invokeExact(indicator, titlePtr);
        }

        static void setMenu(MemorySegment indicator, List<Map<String, Object>> menuItems)
                throws Throwable {
            widgetToId.clear();

            MemorySegment gtkMenu = (MemorySegment) GtkBindings.GTK_MENU_NEW.invokeExact();

            for (Map<String, Object> item : menuItems) {
                String label = item.get("label").toString();

                if ("separator".equals(label) || "-".equals(label)) {
                    MemorySegment separator =
                        (MemorySegment) GtkBindings.GTK_SEPARATOR_MENU_ITEM_NEW.invokeExact();
                    GtkBindings.GTK_MENU_SHELL_APPEND.invokeExact(gtkMenu, separator);
                } else {
                    MemorySegment labelPtr = ARENA.allocateFrom(label);
                    MemorySegment menuItem =
                        (MemorySegment) GtkBindings.GTK_MENU_ITEM_NEW_WITH_LABEL.invokeExact(labelPtr);

                    String id = item.containsKey("id") ? item.get("id").toString() : label;
                    widgetToId.put(menuItem.address(), id);

                    MemorySegment signalName = ARENA.allocateFrom("activate");
                    GtkBindings.G_SIGNAL_CONNECT_DATA.invokeExact(
                        menuItem, signalName, activateStub, menuItem,
                        MemorySegment.NULL, 0
                    );

                    if (item.containsKey("enabled")) {
                        boolean enabled = (Boolean) item.get("enabled");
                        if (!enabled) {
                            GtkBindings.GTK_WIDGET_SET_SENSITIVE.invokeExact(menuItem, 0);
                        }
                    }

                    GtkBindings.GTK_MENU_SHELL_APPEND.invokeExact(gtkMenu, menuItem);
                }
            }

            GtkBindings.GTK_WIDGET_SHOW_ALL.invokeExact(gtkMenu);
            APP_INDICATOR_SET_MENU.invokeExact(indicator, gtkMenu);
        }

        static void remove(MemorySegment indicator) throws Throwable {
            APP_INDICATOR_SET_STATUS.invokeExact(indicator, APP_INDICATOR_STATUS_PASSIVE);
            widgetToId.clear();
        }
    }

    // ===== Windows: Shell_NotifyIcon via FFM =====

    private static final class Windows {

        private static final Linker LINKER = Linker.nativeLinker();
        private static final Arena ARENA = Arena.ofAuto();

        // Custom message for tray icon callbacks
        private static final int WM_TRAYICON = Win32Bindings.WM_USER + 1;

        // NOTIFYICONDATAW field offsets (64-bit)
        private static final long NID_CBSIZE = 0;
        private static final long NID_HWND = 8;
        private static final long NID_UID = 16;
        private static final long NID_UFLAGS = 20;
        private static final long NID_UCALLBACK_MESSAGE = 24;
        private static final long NID_HICON = 32;
        private static final long NID_SZTIP = 40;         // WCHAR[128] = 256 bytes
        private static final long NID_SZINFO = 304;       // WCHAR[256] = 512 bytes
        private static final long NID_SZINFOTITLE = 820;  // WCHAR[64] = 128 bytes
        private static final long NID_DWINFO_FLAGS = 948;
        private static final long NID_SIZE = 976;

        private static final int TRAY_ICON_UID = 1;

        static MemorySegment hiddenHwnd;
        private static MemorySegment notifyIconData;
        private static MemorySegment hIcon;
        private static MemorySegment wndProcStub;
        private static boolean classRegistered = false;
        private static volatile List<Map<String, Object>> currentMenuItems;

        static boolean create(String tooltip, String iconPath) throws Throwable {
            ensureWindowClassRegistered();
            createHiddenWindow();

            notifyIconData = ARENA.allocate(NID_SIZE);
            notifyIconData.fill((byte) 0);

            notifyIconData.set(ValueLayout.JAVA_INT, NID_CBSIZE, (int) NID_SIZE);
            notifyIconData.set(ValueLayout.ADDRESS, NID_HWND, hiddenHwnd);
            notifyIconData.set(ValueLayout.JAVA_INT, NID_UID, TRAY_ICON_UID);

            int flags = Win32Bindings.NIF_MESSAGE | Win32Bindings.NIF_TIP;
            notifyIconData.set(ValueLayout.JAVA_INT, NID_UCALLBACK_MESSAGE, WM_TRAYICON);

            if (iconPath != null && !iconPath.isEmpty()) {
                loadAndSetIcon(iconPath);
                flags |= Win32Bindings.NIF_ICON;
            }

            notifyIconData.set(ValueLayout.JAVA_INT, NID_UFLAGS, flags);

            if (tooltip != null) {
                writeWideString(notifyIconData, NID_SZTIP, tooltip, 127);
            }

            int result = (int) Win32Bindings.SHELL_NOTIFY_ICON_W.invokeExact(
                Win32Bindings.NIM_ADD, notifyIconData);
            return result != 0;
        }

        static void setTooltip(String tooltip) throws Throwable {
            if (notifyIconData == null) return;
            notifyIconData.set(ValueLayout.JAVA_INT, NID_UFLAGS, Win32Bindings.NIF_TIP);
            writeWideString(notifyIconData, NID_SZTIP, tooltip, 127);
            Win32Bindings.SHELL_NOTIFY_ICON_W.invokeExact(
                Win32Bindings.NIM_MODIFY, notifyIconData);
        }

        static void setMenu(List<Map<String, Object>> menuItems) {
            currentMenuItems = menuItems;
        }

        static void remove() throws Throwable {
            if (notifyIconData != null) {
                Win32Bindings.SHELL_NOTIFY_ICON_W.invokeExact(
                    Win32Bindings.NIM_DELETE, notifyIconData);
                notifyIconData = null;
            }
            if (hiddenHwnd != null) {
                Win32Bindings.DESTROY_WINDOW.invokeExact(hiddenHwnd);
                hiddenHwnd = null;
            }
            if (hIcon != null) {
                Win32Bindings.DESTROY_ICON.invokeExact(hIcon);
                hIcon = null;
            }
            currentMenuItems = null;
        }

        static boolean displayMessage(String caption, String text, String messageType)
                throws Throwable {
            if (notifyIconData == null) return false;

            notifyIconData.set(ValueLayout.JAVA_INT, NID_UFLAGS, Win32Bindings.NIF_INFO);
            writeWideString(notifyIconData, NID_SZINFO, text, 255);
            writeWideString(notifyIconData, NID_SZINFOTITLE, caption, 63);

            int flags = switch (messageType != null ? messageType.toLowerCase() : "info") {
                case "error" -> Win32Bindings.NIIF_ERROR;
                case "warning" -> Win32Bindings.NIIF_WARNING;
                default -> Win32Bindings.NIIF_INFO;
            };
            notifyIconData.set(ValueLayout.JAVA_INT, NID_DWINFO_FLAGS, flags);

            int result = (int) Win32Bindings.SHELL_NOTIFY_ICON_W.invokeExact(
                Win32Bindings.NIM_MODIFY, notifyIconData);
            return result != 0;
        }

        @SuppressWarnings("unused")
        private static long wndProc(MemorySegment hwnd, int msg, long wParam, long lParam) {
            try {
                if (msg == WM_TRAYICON) {
                    int event = (int) (lParam & 0xFFFF);
                    if (event == Win32Bindings.WM_RBUTTONUP) {
                        showContextMenu();
                        return 0;
                    }
                }
                return (long) Win32Bindings.DEF_WINDOW_PROC_W.invokeExact(
                    hwnd, msg, wParam, lParam);
            } catch (Throwable t) {
                return 0;
            }
        }

        private static void showContextMenu() {
            SystemTray tray = activeInstance;
            List<Map<String, Object>> items = currentMenuItems;
            if (tray == null || items == null || items.isEmpty()) return;

            try (Arena arena = Arena.ofConfined()) {
                MemorySegment hMenu =
                    (MemorySegment) Win32Bindings.CREATE_POPUP_MENU.invokeExact();

                Map<Integer, String> idMap = new java.util.HashMap<>();
                int menuId = 1;

                for (Map<String, Object> item : items) {
                    String label = item.get("label").toString();
                    if ("separator".equals(label) || "-".equals(label)) {
                        Win32Bindings.APPEND_MENU_W.invokeExact(
                            hMenu, Win32Bindings.MF_SEPARATOR, 0L, MemorySegment.NULL);
                    } else {
                        String id = item.containsKey("id")
                            ? item.get("id").toString() : label;
                        idMap.put(menuId, id);

                        int flags = Win32Bindings.MF_STRING;
                        if (item.containsKey("enabled") && !(Boolean) item.get("enabled")) {
                            flags |= Win32Bindings.MF_GRAYED;
                        }

                        MemorySegment wLabel =
                            Win32Bindings.allocateWideString(arena, label);
                        Win32Bindings.APPEND_MENU_W.invokeExact(
                            hMenu, flags, (long) menuId, wLabel);
                        menuId++;
                    }
                }

                Win32Bindings.SET_FOREGROUND_WINDOW.invokeExact(hiddenHwnd);

                MemorySegment point = arena.allocate(Win32Bindings.POINT);
                Win32Bindings.GET_CURSOR_POS.invokeExact(point);
                int x = point.get(ValueLayout.JAVA_INT, 0);
                int y = point.get(ValueLayout.JAVA_INT, 4);

                int selected = (int) Win32Bindings.TRACK_POPUP_MENU.invokeExact(
                    hMenu,
                    Win32Bindings.TPM_RETURNCMD | Win32Bindings.TPM_NONOTIFY,
                    x, y, 0, hiddenHwnd, MemorySegment.NULL);

                Win32Bindings.POST_MESSAGE_W.invokeExact(
                    hiddenHwnd, Win32Bindings.WM_NULL, 0L, 0L);
                Win32Bindings.DESTROY_MENU.invokeExact(hMenu);

                if (selected > 0) {
                    String clickedId = idMap.get(selected);
                    if (clickedId != null && tray.menuClickHandler != null) {
                        tray.menuClickHandler.accept(clickedId);
                    }
                }
            } catch (Throwable t) {
                System.err.println("[SystemTray] Windows: Error showing context menu: "
                    + t.getMessage());
            }
        }

        private static synchronized void ensureWindowClassRegistered() throws Throwable {
            if (classRegistered) return;

            FunctionDescriptor wndProcDescriptor = FunctionDescriptor.of(
                ValueLayout.JAVA_LONG,
                ValueLayout.ADDRESS,
                ValueLayout.JAVA_INT,
                ValueLayout.JAVA_LONG,
                ValueLayout.JAVA_LONG
            );

            MethodHandle wndProcHandle = MethodHandles.lookup().findStatic(
                Windows.class, "wndProc",
                MethodType.methodType(long.class,
                    MemorySegment.class, int.class, long.class, long.class)
            );

            wndProcStub = LINKER.upcallStub(wndProcHandle, wndProcDescriptor, ARENA);

            MemorySegment hInstance = (MemorySegment)
                Win32Bindings.GET_MODULE_HANDLE_W.invokeExact(MemorySegment.NULL);

            // WNDCLASSEXW struct (80 bytes on 64-bit)
            MemorySegment wndClass = ARENA.allocate(80);
            wndClass.fill((byte) 0);
            wndClass.set(ValueLayout.JAVA_INT, 0, 80);          // cbSize
            wndClass.set(ValueLayout.ADDRESS, 8, wndProcStub);  // lpfnWndProc
            wndClass.set(ValueLayout.ADDRESS, 24, hInstance);    // hInstance

            MemorySegment className =
                Win32Bindings.allocateWideString(ARENA, "KremaTrayWndClass");
            wndClass.set(ValueLayout.ADDRESS, 64, className);    // lpszClassName

            int atom = (int) Win32Bindings.REGISTER_CLASS_EX_W.invokeExact(wndClass);
            if (atom == 0) {
                throw new RuntimeException("Failed to register tray window class");
            }

            classRegistered = true;
        }

        private static void createHiddenWindow() throws Throwable {
            MemorySegment hInstance = (MemorySegment)
                Win32Bindings.GET_MODULE_HANDLE_W.invokeExact(MemorySegment.NULL);
            MemorySegment className =
                Win32Bindings.allocateWideString(ARENA, "KremaTrayWndClass");
            MemorySegment windowName =
                Win32Bindings.allocateWideString(ARENA, "Krema Tray");

            MemorySegment hwndMessage =
                MemorySegment.ofAddress(Win32Bindings.HWND_MESSAGE);

            hiddenHwnd = (MemorySegment) Win32Bindings.CREATE_WINDOW_EX_W.invokeExact(
                0,                          // dwExStyle
                className,                  // lpClassName
                windowName,                 // lpWindowName
                0,                          // dwStyle
                0, 0, 0, 0,                // x, y, width, height
                hwndMessage,               // hWndParent = HWND_MESSAGE
                MemorySegment.NULL,        // hMenu
                hInstance,                 // hInstance
                MemorySegment.NULL         // lpParam
            );

            if (hiddenHwnd == null || hiddenHwnd.address() == 0) {
                throw new RuntimeException("Failed to create hidden tray window");
            }
        }

        private static void loadAndSetIcon(String iconPath) throws Throwable {
            String resolvedPath = iconPath;
            if (iconPath.startsWith("data:image")) {
                String base64 = iconPath.substring(iconPath.indexOf(",") + 1);
                byte[] bytes = Base64.getDecoder().decode(base64);
                Path tmpFile = Files.createTempFile("krema-tray-icon-", ".ico");
                Files.write(tmpFile, bytes);
                tmpFile.toFile().deleteOnExit();
                resolvedPath = tmpFile.toAbsolutePath().toString();
            }

            MemorySegment wIconPath =
                Win32Bindings.allocateWideString(ARENA, resolvedPath);
            hIcon = (MemorySegment) Win32Bindings.LOAD_IMAGE_W.invokeExact(
                MemorySegment.NULL,
                wIconPath,
                Win32Bindings.IMAGE_ICON,
                16, 16,
                Win32Bindings.LR_LOADFROMFILE
            );

            if (hIcon != null && hIcon.address() != 0) {
                notifyIconData.set(ValueLayout.ADDRESS, NID_HICON, hIcon);
            }
        }

        private static void writeWideString(MemorySegment buffer, long offset,
                                             String str, int maxChars) {
            if (str == null) str = "";
            if (str.length() > maxChars) str = str.substring(0, maxChars);
            byte[] bytes = str.getBytes(java.nio.charset.StandardCharsets.UTF_16LE);
            MemorySegment.copy(MemorySegment.ofArray(bytes), 0, buffer, offset, bytes.length);
            buffer.set(ValueLayout.JAVA_SHORT, offset + bytes.length, (short) 0);
        }
    }
}
