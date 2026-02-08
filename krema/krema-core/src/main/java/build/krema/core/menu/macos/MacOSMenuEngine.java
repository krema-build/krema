package build.krema.core.menu.macos;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import build.krema.core.menu.MenuEngine;
import build.krema.core.menu.MenuItem;
import build.krema.core.menu.MenuItem.MenuItemRole;
import build.krema.core.menu.MenuItem.MenuItemType;

/**
 * macOS MenuEngine implementation using Cocoa APIs via FFM.
 * Creates native NSMenu and NSMenuItem objects.
 */
public final class MacOSMenuEngine implements MenuEngine {

    private static final Linker LINKER = Linker.nativeLinker();
    private static final SymbolLookup OBJC_LOOKUP;

    // Objective-C runtime functions
    private static final MethodHandle OBJC_GET_CLASS;
    private static final MethodHandle SEL_REGISTER_NAME;
    private static final MethodHandle OBJC_MSG_SEND;
    private static final MethodHandle OBJC_MSG_SEND_LONG;
    private static final MethodHandle OBJC_ALLOCATE_CLASS_PAIR;
    private static final MethodHandle OBJC_REGISTER_CLASS_PAIR;
    private static final MethodHandle CLASS_ADD_METHOD;

    // Common selectors
    private static final MemorySegment SEL_ALLOC;
    private static final MemorySegment SEL_INIT;
    private static final MemorySegment SEL_SET_TITLE;
    private static final MemorySegment SEL_ADD_ITEM;
    private static final MemorySegment SEL_SET_SUBMENU;
    private static final MemorySegment SEL_SET_KEY_EQUIVALENT;
    private static final MemorySegment SEL_SET_KEY_EQUIVALENT_MODIFIER_MASK;
    private static final MemorySegment SEL_SET_TARGET;
    private static final MemorySegment SEL_SET_ACTION;
    private static final MemorySegment SEL_SET_ENABLED;
    private static final MemorySegment SEL_SET_STATE;
    private static final MemorySegment SEL_SET_TAG;
    private static final MemorySegment SEL_TAG;
    private static final MemorySegment SEL_SET_MAIN_MENU;
    private static final MemorySegment SEL_SHARED_APPLICATION;
    private static final MemorySegment SEL_SEPARATOR_ITEM;
    private static final MemorySegment SEL_SET_REPRESENTED_OBJECT;
    private static final MemorySegment SEL_REPRESENTED_OBJECT;

    // NSEvent modifier flags
    private static final long NS_COMMAND_KEY_MASK = 1 << 20;
    private static final long NS_SHIFT_KEY_MASK = 1 << 17;
    private static final long NS_ALTERNATE_KEY_MASK = 1 << 19; // Option/Alt
    private static final long NS_CONTROL_KEY_MASK = 1 << 18;

    // NSControl state
    private static final long NS_CONTROL_STATE_VALUE_OFF = 0;
    private static final long NS_CONTROL_STATE_VALUE_ON = 1;

    // CoreFoundation string encoding
    private static final int kCFStringEncodingUTF8 = 0x08000100;
    private static final MethodHandle CF_STRING_CREATE;

    static {
        OBJC_LOOKUP = SymbolLookup.libraryLookup("/usr/lib/libobjc.dylib", Arena.global());

        // Load AppKit framework
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

        OBJC_GET_CLASS = LINKER.downcallHandle(
            OBJC_LOOKUP.find("objc_getClass").orElseThrow(),
            FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS)
        );

        SEL_REGISTER_NAME = LINKER.downcallHandle(
            OBJC_LOOKUP.find("sel_registerName").orElseThrow(),
            FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS)
        );

        // On ARM64, objc_msgSend uses standard calling convention, not variadic
        OBJC_MSG_SEND = LINKER.downcallHandle(
            OBJC_LOOKUP.find("objc_msgSend").orElseThrow(),
            FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS)
        );

        OBJC_MSG_SEND_LONG = LINKER.downcallHandle(
            OBJC_LOOKUP.find("objc_msgSend").orElseThrow(),
            FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.ADDRESS)
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

        SEL_ALLOC = sel("alloc");
        SEL_INIT = sel("init");
        SEL_SET_TITLE = sel("setTitle:");
        SEL_ADD_ITEM = sel("addItem:");
        SEL_SET_SUBMENU = sel("setSubmenu:");
        SEL_SET_KEY_EQUIVALENT = sel("setKeyEquivalent:");
        SEL_SET_KEY_EQUIVALENT_MODIFIER_MASK = sel("setKeyEquivalentModifierMask:");
        SEL_SET_TARGET = sel("setTarget:");
        SEL_SET_ACTION = sel("setAction:");
        SEL_SET_ENABLED = sel("setEnabled:");
        SEL_SET_STATE = sel("setState:");
        SEL_SET_TAG = sel("setTag:");
        SEL_TAG = sel("tag");
        SEL_SET_MAIN_MENU = sel("setMainMenu:");
        SEL_SHARED_APPLICATION = sel("sharedApplication");
        SEL_SEPARATOR_ITEM = sel("separatorItem");
        SEL_SET_REPRESENTED_OBJECT = sel("setRepresentedObject:");
        SEL_REPRESENTED_OBJECT = sel("representedObject");
    }

    private final Arena arena = Arena.ofAuto();
    private Consumer<String> clickCallback;
    private MemorySegment delegateInstance;
    private MemorySegment menuClickedSelector;
    private final Map<Long, String> tagToIdMap = new HashMap<>();
    private final Map<String, MemorySegment> idToMenuItem = new HashMap<>();
    private long nextTag = 1000;

    // Keep references to upcall stubs to prevent GC
    private MemorySegment upcallStub;
    private MemorySegment dockMenuUpcallStub;
    private MemorySegment dockMenu;

    public MacOSMenuEngine() {
        createDelegateClass();
    }

    @Override
    public void setApplicationMenu(List<MenuItem> menu) {
        try {
            MemorySegment mainMenu = createMenu("");

            for (MenuItem topLevelItem : menu) {
                if (topLevelItem.submenu() != null) {
                    // Create a placeholder item for the menu bar
                    MemorySegment menuItem = createMenuItem("", null, 0);

                    // Create the submenu
                    MemorySegment submenu = createMenu(topLevelItem.label() != null ? topLevelItem.label() : "");
                    populateMenu(submenu, topLevelItem.submenu());

                    // Attach submenu to item
                    msgSendWithArg(menuItem, SEL_SET_SUBMENU, submenu);

                    // Add item to main menu bar
                    msgSendWithArg(mainMenu, SEL_ADD_ITEM, menuItem);
                }
            }

            // Set as application main menu
            MemorySegment nsApp = getClass("NSApplication");
            MemorySegment sharedApp = msgSend(nsApp, SEL_SHARED_APPLICATION);
            msgSendWithArg(sharedApp, SEL_SET_MAIN_MENU, mainMenu);

        } catch (Throwable t) {
            throw new RuntimeException("Failed to set application menu", t);
        }
    }

    @Override
    public void showContextMenu(List<MenuItem> items, double x, double y) {
        try {
            MemorySegment menu = createMenu("");
            populateMenu(menu, items);

            // [NSMenu popUpContextMenu:withEvent:forView:] requires an event and view.
            // Use popUpMenuPositioningItem:atLocation:inView: with nil view to pop up at screen coordinates.
            MemorySegment selPopUp = sel("popUpMenuPositioningItem:atLocation:inView:");
            MethodHandle mh = LINKER.downcallHandle(
                OBJC_LOOKUP.find("objc_msgSend").orElseThrow(),
                FunctionDescriptor.of(
                    ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS, ValueLayout.ADDRESS,
                    ValueLayout.ADDRESS,
                    ValueLayout.JAVA_DOUBLE, ValueLayout.JAVA_DOUBLE,
                    ValueLayout.ADDRESS
                )
            );
            mh.invokeExact(menu, selPopUp, MemorySegment.NULL, x, y, MemorySegment.NULL);

        } catch (Throwable t) {
            throw new RuntimeException("Failed to show context menu", t);
        }
    }

    @Override
    public void setDockMenu(List<MenuItem> items) {
        try {
            dockMenu = createMenu("");
            populateMenu(dockMenu, items);

            // Create a dock delegate class that responds to applicationDockMenu:
            MemorySegment nsObjectClass = getClass("NSObject");
            MemorySegment className = arena.allocateFrom("KremaDockDelegate");

            MemorySegment dockDelegateClass = (MemorySegment) OBJC_ALLOCATE_CLASS_PAIR.invokeExact(
                nsObjectClass, className, 0L
            );

            if (dockDelegateClass.address() == 0) {
                // Class already exists, reuse it
                dockDelegateClass = getClass("KremaDockDelegate");
            } else {
                // applicationDockMenu: returns NSMenu, signature: @@:@ (id return, id self, SEL, id app)
                MemorySegment dockMenuSelector = sel("applicationDockMenu:");
                MemorySegment typeEncoding = arena.allocateFrom("@@:@");

                FunctionDescriptor dockDescriptor = FunctionDescriptor.of(
                    ValueLayout.ADDRESS,
                    ValueLayout.ADDRESS,  // self
                    ValueLayout.ADDRESS,  // _cmd
                    ValueLayout.ADDRESS   // sender (NSApplication)
                );

                MethodHandle dockHandle = MethodHandles.lookup().findVirtual(
                    MacOSMenuEngine.class,
                    "handleDockMenuRequest",
                    MethodType.methodType(MemorySegment.class, MemorySegment.class, MemorySegment.class, MemorySegment.class)
                ).bindTo(this);

                dockMenuUpcallStub = LINKER.upcallStub(dockHandle, dockDescriptor, arena);

                boolean added = (boolean) CLASS_ADD_METHOD.invokeExact(
                    dockDelegateClass, dockMenuSelector, dockMenuUpcallStub, typeEncoding
                );

                OBJC_REGISTER_CLASS_PAIR.invokeExact(dockDelegateClass);
            }

            // Create an instance and set as NSApplication delegate
            MemorySegment allocated = msgSend(dockDelegateClass, SEL_ALLOC);
            MemorySegment dockDelegate = msgSend(allocated, SEL_INIT);

            MemorySegment nsApp = getClass("NSApplication");
            MemorySegment sharedApp = msgSend(nsApp, SEL_SHARED_APPLICATION);
            msgSendWithArg(sharedApp, sel("setDelegate:"), dockDelegate);

        } catch (Throwable t) {
            throw new RuntimeException("Failed to set dock menu", t);
        }
    }

    @SuppressWarnings("unused")
    private MemorySegment handleDockMenuRequest(MemorySegment self, MemorySegment cmd, MemorySegment sender) {
        return dockMenu != null ? dockMenu : MemorySegment.NULL;
    }

    @Override
    public void updateItem(String itemId, boolean enabled, boolean checked) {
        try {
            MemorySegment nsItem = idToMenuItem.get(itemId);
            if (nsItem == null) {
                return;
            }
            msgSendWithBool(nsItem, SEL_SET_ENABLED, enabled);
            msgSendWithLong(nsItem, SEL_SET_STATE, checked ? NS_CONTROL_STATE_VALUE_ON : NS_CONTROL_STATE_VALUE_OFF);
        } catch (Throwable t) {
            throw new RuntimeException("Failed to update menu item: " + itemId, t);
        }
    }

    @Override
    public void setMenuClickCallback(Consumer<String> callback) {
        this.clickCallback = callback;
    }

    private void createDelegateClass() {
        try {
            // Create a custom Objective-C class at runtime
            MemorySegment nsObjectClass = getClass("NSObject");
            MemorySegment className = arena.allocateFrom("KremaMenuDelegate");

            MemorySegment delegateClass = (MemorySegment) OBJC_ALLOCATE_CLASS_PAIR.invokeExact(
                nsObjectClass, className, 0L
            );

            if (delegateClass.address() == 0) {
                // Class may already exist from a previous run
                delegateClass = getClass("KremaMenuDelegate");
            } else {
                // Add menuItemClicked: method
                menuClickedSelector = sel("menuItemClicked:");

                // Method signature: void menuItemClicked(id self, SEL _cmd, id sender)
                // Type encoding: v@:@ (void, id, selector, id)
                MemorySegment typeEncoding = arena.allocateFrom("v@:@");

                // Create upcall stub for the method implementation
                FunctionDescriptor callbackDescriptor = FunctionDescriptor.ofVoid(
                    ValueLayout.ADDRESS,  // self
                    ValueLayout.ADDRESS,  // _cmd
                    ValueLayout.ADDRESS   // sender
                );

                MethodHandle callbackHandle = MethodHandles.lookup().findVirtual(
                    MacOSMenuEngine.class,
                    "handleMenuClick",
                    MethodType.methodType(void.class, MemorySegment.class, MemorySegment.class, MemorySegment.class)
                ).bindTo(this);

                upcallStub = LINKER.upcallStub(callbackHandle, callbackDescriptor, arena);

                // Add method to class
                boolean added = (boolean) CLASS_ADD_METHOD.invokeExact(
                    delegateClass, menuClickedSelector, upcallStub, typeEncoding
                );

                // Register the class
                OBJC_REGISTER_CLASS_PAIR.invokeExact(delegateClass);
            }

            // Create an instance of the delegate
            MemorySegment allocated = msgSend(delegateClass, SEL_ALLOC);
            delegateInstance = msgSend(allocated, SEL_INIT);

        } catch (Throwable t) {
            throw new RuntimeException("Failed to create menu delegate class", t);
        }
    }

    /**
     * Called by Objective-C when a menu item is clicked.
     */
    @SuppressWarnings("unused")
    private void handleMenuClick(MemorySegment self, MemorySegment cmd, MemorySegment sender) {
        try {
            // Get the tag from the sender (NSMenuItem)
            long tag = msgSendLong(sender, SEL_TAG);

            // Look up the item ID
            String itemId = tagToIdMap.get(tag);

            if (itemId != null && clickCallback != null) {
                clickCallback.accept(itemId);
            }
        } catch (Throwable t) {
            System.err.println("[MacOSMenuEngine] Error handling menu click: " + t.getMessage());
        }
    }

    private MemorySegment createMenu(String title) throws Throwable {
        MemorySegment menuClass = getClass("NSMenu");
        MemorySegment menu = msgSend(menuClass, SEL_ALLOC);
        menu = msgSend(menu, SEL_INIT);

        if (title != null && !title.isEmpty()) {
            MemorySegment nsTitle = createNSString(title);
            msgSendWithArg(menu, SEL_SET_TITLE, nsTitle);
        }

        return menu;
    }

    private void populateMenu(MemorySegment menu, List<MenuItem> items) throws Throwable {
        for (MenuItem item : items) {
            MemorySegment nsItem;

            if (item.type() == MenuItemType.SEPARATOR) {
                MemorySegment menuItemClass = getClass("NSMenuItem");
                nsItem = msgSend(menuItemClass, SEL_SEPARATOR_ITEM);
            } else if (item.role() != null) {
                nsItem = createRoleMenuItem(item.role());
            } else {
                long modifiers = 0;
                String keyEquiv = "";

                if (item.accelerator() != null) {
                    AcceleratorParts parts = parseAccelerator(item.accelerator());
                    keyEquiv = parts.key;
                    modifiers = parts.modifiers;
                }

                nsItem = createMenuItem(item.label() != null ? item.label() : "", keyEquiv, modifiers);

                // Set enabled state
                if (!item.enabled()) {
                    msgSendWithBool(nsItem, SEL_SET_ENABLED, false);
                }

                // Set checked state
                if (item.type() == MenuItemType.CHECKBOX && item.checked()) {
                    msgSendWithLong(nsItem, SEL_SET_STATE, NS_CONTROL_STATE_VALUE_ON);
                }

                // Set action and target if this item has an ID
                if (item.id() != null && delegateInstance != null) {
                    long tag = nextTag++;
                    tagToIdMap.put(tag, item.id());
                    idToMenuItem.put(item.id(), nsItem);

                    msgSendWithLong(nsItem, SEL_SET_TAG, tag);
                    msgSendWithArg(nsItem, SEL_SET_TARGET, delegateInstance);
                    msgSendWithArg(nsItem, SEL_SET_ACTION, menuClickedSelector);
                }

                // Handle submenu
                if (item.submenu() != null && !item.submenu().isEmpty()) {
                    MemorySegment submenu = createMenu(item.label() != null ? item.label() : "");
                    populateMenu(submenu, item.submenu());
                    msgSendWithArg(nsItem, SEL_SET_SUBMENU, submenu);
                }
            }

            msgSendWithArg(menu, SEL_ADD_ITEM, nsItem);
        }
    }

    private MemorySegment createMenuItem(String title, String keyEquiv, long modifiers) throws Throwable {
        MemorySegment menuItemClass = getClass("NSMenuItem");
        MemorySegment item = msgSend(menuItemClass, SEL_ALLOC);

        // initWithTitle:action:keyEquivalent:
        MemorySegment selInit = sel("initWithTitle:action:keyEquivalent:");
        MemorySegment nsTitle = createNSString(title);
        MemorySegment nsKeyEquiv = createNSString(keyEquiv != null ? keyEquiv : "");

        // On ARM64, don't use firstVariadicArg - objc_msgSend uses standard calling convention
        MethodHandle mh = LINKER.downcallHandle(
            OBJC_LOOKUP.find("objc_msgSend").orElseThrow(),
            FunctionDescriptor.of(
                ValueLayout.ADDRESS,
                ValueLayout.ADDRESS, ValueLayout.ADDRESS,
                ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS
            )
            // No firstVariadicArg - ARM64 passes all args in registers
        );

        item = (MemorySegment) mh.invokeExact(item, selInit, nsTitle, MemorySegment.NULL, nsKeyEquiv);

        // Set modifier mask if we have modifiers
        if (modifiers != 0) {
            msgSendWithLong(item, SEL_SET_KEY_EQUIVALENT_MODIFIER_MASK, modifiers);
        }

        return item;
    }

    private MemorySegment createRoleMenuItem(MenuItemRole role) throws Throwable {
        // Create standard menu items based on role
        return switch (role) {
            case QUIT -> createMenuItem("Quit", "q", NS_COMMAND_KEY_MASK);
            case UNDO -> createMenuItem("Undo", "z", NS_COMMAND_KEY_MASK);
            case REDO -> createMenuItem("Redo", "Z", NS_COMMAND_KEY_MASK | NS_SHIFT_KEY_MASK);
            case CUT -> createMenuItem("Cut", "x", NS_COMMAND_KEY_MASK);
            case COPY -> createMenuItem("Copy", "c", NS_COMMAND_KEY_MASK);
            case PASTE -> createMenuItem("Paste", "v", NS_COMMAND_KEY_MASK);
            case SELECT_ALL -> createMenuItem("Select All", "a", NS_COMMAND_KEY_MASK);
            case MINIMIZE -> createMenuItem("Minimize", "m", NS_COMMAND_KEY_MASK);
            case CLOSE -> createMenuItem("Close Window", "w", NS_COMMAND_KEY_MASK);
            case ZOOM_IN -> createMenuItem("Zoom In", "+", NS_COMMAND_KEY_MASK);
            case ZOOM_OUT -> createMenuItem("Zoom Out", "-", NS_COMMAND_KEY_MASK);
            case TOGGLE_FULLSCREEN -> createMenuItem("Toggle Full Screen", "f", NS_COMMAND_KEY_MASK | NS_CONTROL_KEY_MASK);
            default -> createMenuItem(role.name(), "", 0);
        };
    }

    private record AcceleratorParts(String key, long modifiers) {}

    private AcceleratorParts parseAccelerator(String accelerator) {
        long modifiers = 0;
        String key = "";

        String[] parts = accelerator.split("\\+");
        for (String part : parts) {
            String p = part.trim().toLowerCase();
            switch (p) {
                case "cmd", "command", "meta" -> modifiers |= NS_COMMAND_KEY_MASK;
                case "shift" -> modifiers |= NS_SHIFT_KEY_MASK;
                case "alt", "option" -> modifiers |= NS_ALTERNATE_KEY_MASK;
                case "ctrl", "control" -> modifiers |= NS_CONTROL_KEY_MASK;
                default -> key = part.trim();
            }
        }

        // If no modifiers specified but we have a key, default to Cmd
        if (modifiers == 0 && !key.isEmpty()) {
            modifiers = NS_COMMAND_KEY_MASK;
        }

        return new AcceleratorParts(key.toLowerCase(), modifiers);
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

    private long msgSendLong(MemorySegment target, MemorySegment selector) throws Throwable {
        return (long) OBJC_MSG_SEND_LONG.invokeExact(target, selector);
    }

    private void msgSendWithArg(MemorySegment target, MemorySegment selector, MemorySegment arg) throws Throwable {
        MethodHandle mh = LINKER.downcallHandle(
            OBJC_LOOKUP.find("objc_msgSend").orElseThrow(),
            FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS)
        );
        mh.invokeExact(target, selector, arg);
    }

    private void msgSendWithBool(MemorySegment target, MemorySegment selector, boolean arg) throws Throwable {
        MethodHandle mh = LINKER.downcallHandle(
            OBJC_LOOKUP.find("objc_msgSend").orElseThrow(),
            FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT)
        );
        mh.invokeExact(target, selector, arg ? 1 : 0);
    }

    private void msgSendWithLong(MemorySegment target, MemorySegment selector, long arg) throws Throwable {
        MethodHandle mh = LINKER.downcallHandle(
            OBJC_LOOKUP.find("objc_msgSend").orElseThrow(),
            FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG)
        );
        mh.invokeExact(target, selector, arg);
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

    private MemorySegment msgSendWithArgReturn(MemorySegment target, MemorySegment selector, MemorySegment arg) throws Throwable {
        MethodHandle mh = LINKER.downcallHandle(
            OBJC_LOOKUP.find("objc_msgSend").orElseThrow(),
            FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS)
        );
        return (MemorySegment) mh.invokeExact(target, selector, arg);
    }
}
