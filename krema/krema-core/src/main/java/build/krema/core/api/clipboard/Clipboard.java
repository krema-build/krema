package build.krema.core.api.clipboard;

import java.io.IOException;
import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.logging.Logger;

import build.krema.core.KremaCommand;
import build.krema.core.platform.Platform;
import build.krema.core.platform.linux.GtkBindings;

/**
 * Clipboard read/write operations.
 * Uses platform-native APIs: NSPasteboard (macOS), GTK3 Clipboard (Linux), PowerShell (Windows).
 */
public class Clipboard {

    @KremaCommand("clipboard:readText")
    public String readText() {
        try {
            return switch (Platform.current()) {
                case MACOS -> MacOS.readString("public.utf8-plain-text");
                case LINUX -> Linux.readText();
                case WINDOWS -> Windows.readText();
                case UNKNOWN -> null;
            };
        } catch (Throwable e) {
            return null;
        }
    }

    @KremaCommand("clipboard:writeText")
    public boolean writeText(String text) {
        try {
            return switch (Platform.current()) {
                case MACOS -> MacOS.writeString(text, "public.utf8-plain-text");
                case LINUX -> Linux.writeText(text);
                case WINDOWS -> Windows.writeText(text);
                case UNKNOWN -> false;
            };
        } catch (Throwable e) {
            throw new RuntimeException("Failed to write clipboard: " + e.getMessage(), e);
        }
    }

    @KremaCommand("clipboard:readHtml")
    public String readHtml() {
        try {
            return switch (Platform.current()) {
                case MACOS -> MacOS.readString("public.html");
                case LINUX -> Linux.readHtml();
                case WINDOWS -> Windows.readHtml();
                case UNKNOWN -> null;
            };
        } catch (Throwable e) {
            return null;
        }
    }

    @KremaCommand("clipboard:hasText")
    public boolean hasText() {
        try {
            return switch (Platform.current()) {
                case MACOS -> MacOS.hasType("public.utf8-plain-text");
                case LINUX -> Linux.hasText();
                case WINDOWS -> Windows.hasText();
                case UNKNOWN -> false;
            };
        } catch (Throwable e) {
            return false;
        }
    }

    @KremaCommand("clipboard:hasImage")
    public boolean hasImage() {
        try {
            return switch (Platform.current()) {
                case MACOS -> MacOS.hasType("public.tiff") || MacOS.hasType("public.png");
                case LINUX -> Linux.hasImage();
                case WINDOWS -> Windows.hasImage();
                case UNKNOWN -> false;
            };
        } catch (Throwable e) {
            return false;
        }
    }

    @KremaCommand("clipboard:readImageBase64")
    public String readImageBase64() {
        try {
            return switch (Platform.current()) {
                case MACOS -> MacOS.readImageBase64();
                case LINUX -> Linux.readImageBase64();
                case WINDOWS -> Windows.readImageBase64();
                case UNKNOWN -> null;
            };
        } catch (Throwable e) {
            return null;
        }
    }

    @KremaCommand("clipboard:clear")
    public boolean clear() {
        try {
            return switch (Platform.current()) {
                case MACOS -> MacOS.clear();
                case LINUX -> Linux.clear();
                case WINDOWS -> Windows.clear();
                case UNKNOWN -> false;
            };
        } catch (Throwable e) {
            return false;
        }
    }

    @KremaCommand("clipboard:getAvailableFormats")
    public String[] getAvailableFormats() {
        try {
            return switch (Platform.current()) {
                case MACOS -> MacOS.getAvailableFormats();
                case LINUX -> Linux.getAvailableFormats();
                case WINDOWS -> Windows.getAvailableFormats();
                case UNKNOWN -> new String[0];
            };
        } catch (Throwable e) {
            return new String[0];
        }
    }

    // ===== Command-line helpers for Linux/Windows =====

    private static final class CommandLine {

        static String exec(String... command) throws IOException, InterruptedException {
            Process p = new ProcessBuilder(command).redirectErrorStream(true).start();
            String output;
            try (var is = p.getInputStream()) {
                output = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            }
            int exitCode = p.waitFor();
            return exitCode == 0 ? output.stripTrailing() : null;
        }

        static byte[] execBytes(String... command) throws IOException, InterruptedException {
            Process p = new ProcessBuilder(command).start();
            byte[] data;
            try (var is = p.getInputStream()) {
                data = is.readAllBytes();
            }
            p.waitFor();
            return p.exitValue() == 0 ? data : null;
        }

        static boolean pipe(String input, String... command) throws IOException, InterruptedException {
            Process p = new ProcessBuilder(command).start();
            try (var os = p.getOutputStream()) {
                os.write(input.getBytes(StandardCharsets.UTF_8));
            }
            return p.waitFor() == 0;
        }
    }

    // ===== macOS: NSPasteboard via FFM =====

    private static final class MacOS {

        private static final Linker LINKER = Linker.nativeLinker();
        private static final SymbolLookup OBJC_LOOKUP;

        // Objective-C runtime functions
        private static final MethodHandle OBJC_GET_CLASS;
        private static final MethodHandle SEL_REGISTER_NAME;
        private static final MethodHandle OBJC_MSG_SEND;          // (id, SEL) -> id
        private static final MethodHandle OBJC_MSG_SEND_LONG;     // (id, SEL) -> long
        private static final MethodHandle OBJC_MSG_SEND_1PTR;     // (id, SEL, id) -> id
        private static final MethodHandle OBJC_MSG_SEND_1LONG;    // (id, SEL, long) -> id
        private static final MethodHandle OBJC_MSG_SEND_2PTR_BOOL;// (id, SEL, id, id) -> bool
        private static final MethodHandle OBJC_MSG_SEND_LONG_PTR; // (id, SEL, long, id) -> id

        // CoreFoundation
        private static final MethodHandle CF_STRING_CREATE;

        // Cached selectors
        private static final MemorySegment SEL_GENERAL_PASTEBOARD;
        private static final MemorySegment SEL_STRING_FOR_TYPE;
        private static final MemorySegment SEL_CLEAR_CONTENTS;
        private static final MemorySegment SEL_SET_STRING_FOR_TYPE;
        private static final MemorySegment SEL_TYPES;
        private static final MemorySegment SEL_COUNT;
        private static final MemorySegment SEL_OBJECT_AT_INDEX;
        private static final MemorySegment SEL_UTF8_STRING;
        private static final MemorySegment SEL_LENGTH;
        private static final MemorySegment SEL_DATA_FOR_TYPE;
        private static final MemorySegment SEL_BYTES;
        private static final MemorySegment SEL_IMAGE_REP_WITH_DATA;
        private static final MemorySegment SEL_REPRESENTATION_USING_TYPE;
        private static final MemorySegment SEL_DICTIONARY;

        private static final Arena ARENA = Arena.ofAuto();

        static {
            OBJC_LOOKUP = SymbolLookup.libraryLookup("/usr/lib/libobjc.dylib", Arena.global());

            SymbolLookup.libraryLookup(
                "/System/Library/Frameworks/AppKit.framework/AppKit",
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

            OBJC_MSG_SEND_1PTR = LINKER.downcallHandle(
                OBJC_LOOKUP.find("objc_msgSend").orElseThrow(),
                FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS)
            );

            OBJC_MSG_SEND_1LONG = LINKER.downcallHandle(
                OBJC_LOOKUP.find("objc_msgSend").orElseThrow(),
                FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG)
            );

            OBJC_MSG_SEND_2PTR_BOOL = LINKER.downcallHandle(
                OBJC_LOOKUP.find("objc_msgSend").orElseThrow(),
                FunctionDescriptor.of(ValueLayout.JAVA_BOOLEAN, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS)
            );

            OBJC_MSG_SEND_LONG_PTR = LINKER.downcallHandle(
                OBJC_LOOKUP.find("objc_msgSend").orElseThrow(),
                FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS)
            );

            SymbolLookup cfLookup = SymbolLookup.libraryLookup(
                "/System/Library/Frameworks/CoreFoundation.framework/CoreFoundation",
                Arena.global()
            );
            CF_STRING_CREATE = LINKER.downcallHandle(
                cfLookup.find("CFStringCreateWithCString").orElseThrow(),
                FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT)
            );

            SEL_GENERAL_PASTEBOARD = sel("generalPasteboard");
            SEL_STRING_FOR_TYPE = sel("stringForType:");
            SEL_CLEAR_CONTENTS = sel("clearContents");
            SEL_SET_STRING_FOR_TYPE = sel("setString:forType:");
            SEL_TYPES = sel("types");
            SEL_COUNT = sel("count");
            SEL_OBJECT_AT_INDEX = sel("objectAtIndex:");
            SEL_UTF8_STRING = sel("UTF8String");
            SEL_LENGTH = sel("length");
            SEL_DATA_FOR_TYPE = sel("dataForType:");
            SEL_BYTES = sel("bytes");
            SEL_IMAGE_REP_WITH_DATA = sel("imageRepWithData:");
            SEL_REPRESENTATION_USING_TYPE = sel("representationUsingType:properties:");
            SEL_DICTIONARY = sel("dictionary");
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
            MemorySegment cString = ARENA.allocateFrom(str);
            return (MemorySegment) CF_STRING_CREATE.invokeExact(
                MemorySegment.NULL, cString, 0x08000100 // kCFStringEncodingUTF8
            );
        }

        private static MemorySegment getGeneralPasteboard() throws Throwable {
            MemorySegment pbClass = getClass("NSPasteboard");
            return (MemorySegment) OBJC_MSG_SEND.invokeExact(pbClass, SEL_GENERAL_PASTEBOARD);
        }

        private static String nsStringToJava(MemorySegment nsString) throws Throwable {
            if (nsString.address() == 0) return null;
            MemorySegment utf8Ptr = (MemorySegment) OBJC_MSG_SEND.invokeExact(nsString, SEL_UTF8_STRING);
            if (utf8Ptr.address() == 0) return null;
            long charCount = (long) OBJC_MSG_SEND_LONG.invokeExact(nsString, SEL_LENGTH);
            long maxBytes = Math.max(charCount * 4 + 1, 256);
            return utf8Ptr.reinterpret(maxBytes).getString(0);
        }

        static String readString(String type) throws Throwable {
            MemorySegment pb = getGeneralPasteboard();
            MemorySegment nsType = createNSString(type);
            MemorySegment result = (MemorySegment) OBJC_MSG_SEND_1PTR.invokeExact(
                pb, SEL_STRING_FOR_TYPE, nsType
            );
            return nsStringToJava(result);
        }

        static boolean writeString(String text, String type) throws Throwable {
            MemorySegment pb = getGeneralPasteboard();
            long changeCount = (long) OBJC_MSG_SEND_LONG.invokeExact(pb, SEL_CLEAR_CONTENTS);
            MemorySegment nsText = createNSString(text);
            MemorySegment nsType = createNSString(type);
            return (boolean) OBJC_MSG_SEND_2PTR_BOOL.invokeExact(
                pb, SEL_SET_STRING_FOR_TYPE, nsText, nsType
            );
        }

        static boolean hasType(String type) throws Throwable {
            MemorySegment pb = getGeneralPasteboard();
            MemorySegment nsType = createNSString(type);
            MemorySegment result = (MemorySegment) OBJC_MSG_SEND_1PTR.invokeExact(
                pb, SEL_DATA_FOR_TYPE, nsType
            );
            return result.address() != 0;
        }

        static boolean clear() throws Throwable {
            MemorySegment pb = getGeneralPasteboard();
            long changeCount = (long) OBJC_MSG_SEND_LONG.invokeExact(pb, SEL_CLEAR_CONTENTS);
            return true;
        }

        static String readImageBase64() throws Throwable {
            MemorySegment pb = getGeneralPasteboard();

            // Try PNG first
            MemorySegment pngType = createNSString("public.png");
            MemorySegment pngData = (MemorySegment) OBJC_MSG_SEND_1PTR.invokeExact(
                pb, SEL_DATA_FOR_TYPE, pngType
            );
            if (pngData.address() != 0) {
                return dataToBase64(pngData);
            }

            // Fall back to TIFF, convert to PNG via NSBitmapImageRep
            MemorySegment tiffType = createNSString("public.tiff");
            MemorySegment tiffData = (MemorySegment) OBJC_MSG_SEND_1PTR.invokeExact(
                pb, SEL_DATA_FOR_TYPE, tiffType
            );
            if (tiffData.address() != 0) {
                return convertTiffToPngBase64(tiffData);
            }

            return null;
        }

        private static String dataToBase64(MemorySegment nsData) throws Throwable {
            MemorySegment bytesPtr = (MemorySegment) OBJC_MSG_SEND.invokeExact(nsData, SEL_BYTES);
            long length = (long) OBJC_MSG_SEND_LONG.invokeExact(nsData, SEL_LENGTH);
            if (bytesPtr.address() == 0 || length <= 0) return null;
            byte[] bytes = bytesPtr.reinterpret(length).toArray(ValueLayout.JAVA_BYTE);
            return Base64.getEncoder().encodeToString(bytes);
        }

        private static String convertTiffToPngBase64(MemorySegment tiffData) throws Throwable {
            MemorySegment bitmapClass = getClass("NSBitmapImageRep");
            MemorySegment imageRep = (MemorySegment) OBJC_MSG_SEND_1PTR.invokeExact(
                bitmapClass, SEL_IMAGE_REP_WITH_DATA, tiffData
            );
            if (imageRep.address() == 0) return null;

            MemorySegment dictClass = getClass("NSDictionary");
            MemorySegment emptyDict = (MemorySegment) OBJC_MSG_SEND.invokeExact(
                dictClass, SEL_DICTIONARY
            );

            // NSBitmapImageFileTypePNG = 4
            MemorySegment pngData = (MemorySegment) OBJC_MSG_SEND_LONG_PTR.invokeExact(
                imageRep, SEL_REPRESENTATION_USING_TYPE, 4L, emptyDict
            );
            if (pngData.address() == 0) return null;

            return dataToBase64(pngData);
        }

        static String[] getAvailableFormats() throws Throwable {
            MemorySegment pb = getGeneralPasteboard();
            MemorySegment types = (MemorySegment) OBJC_MSG_SEND.invokeExact(pb, SEL_TYPES);
            if (types.address() == 0) return new String[0];

            long count = (long) OBJC_MSG_SEND_LONG.invokeExact(types, SEL_COUNT);
            List<String> formats = new ArrayList<>();

            for (long i = 0; i < count; i++) {
                MemorySegment typeObj = (MemorySegment) OBJC_MSG_SEND_1LONG.invokeExact(
                    types, SEL_OBJECT_AT_INDEX, i
                );
                String typeName = nsStringToJava(typeObj);
                if (typeName != null) {
                    formats.add(typeName);
                }
            }

            return formats.toArray(new String[0]);
        }
    }

    // ===== Windows: Win32 Clipboard via FFM =====

    private static final class Windows {

        private static final Linker LINKER = Linker.nativeLinker();
        private static final SymbolLookup USER32 = SymbolLookup.libraryLookup("user32.dll", Arena.global());
        private static final SymbolLookup KERNEL32 = SymbolLookup.libraryLookup("kernel32.dll", Arena.global());

        // Clipboard formats
        private static final int CF_TEXT = 1;
        private static final int CF_UNICODETEXT = 13;
        private static final int CF_BITMAP = 2;
        private static final int CF_DIB = 8;
        private static final int CF_HTML = 0; // Registered dynamically

        // Memory flags
        private static final int GMEM_MOVEABLE = 0x0002;

        private static final MethodHandle OPEN_CLIPBOARD;
        private static final MethodHandle CLOSE_CLIPBOARD;
        private static final MethodHandle EMPTY_CLIPBOARD;
        private static final MethodHandle GET_CLIPBOARD_DATA;
        private static final MethodHandle SET_CLIPBOARD_DATA;
        private static final MethodHandle IS_CLIPBOARD_FORMAT_AVAILABLE;
        private static final MethodHandle ENUM_CLIPBOARD_FORMATS;
        private static final MethodHandle GET_CLIPBOARD_FORMAT_NAME_W;
        private static final MethodHandle REGISTER_CLIPBOARD_FORMAT_W;

        private static final MethodHandle GLOBAL_ALLOC;
        private static final MethodHandle GLOBAL_LOCK;
        private static final MethodHandle GLOBAL_UNLOCK;
        private static final MethodHandle GLOBAL_SIZE;

        private static final int CF_HTML_FORMAT;

        static {
            OPEN_CLIPBOARD = user32("OpenClipboard",
                FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
            CLOSE_CLIPBOARD = user32("CloseClipboard",
                FunctionDescriptor.of(ValueLayout.JAVA_INT));
            EMPTY_CLIPBOARD = user32("EmptyClipboard",
                FunctionDescriptor.of(ValueLayout.JAVA_INT));
            GET_CLIPBOARD_DATA = user32("GetClipboardData",
                FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
            SET_CLIPBOARD_DATA = user32("SetClipboardData",
                FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
            IS_CLIPBOARD_FORMAT_AVAILABLE = user32("IsClipboardFormatAvailable",
                FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));
            ENUM_CLIPBOARD_FORMATS = user32("EnumClipboardFormats",
                FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));
            GET_CLIPBOARD_FORMAT_NAME_W = user32("GetClipboardFormatNameW",
                FunctionDescriptor.of(ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
            REGISTER_CLIPBOARD_FORMAT_W = user32("RegisterClipboardFormatW",
                FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));

            GLOBAL_ALLOC = kernel32("GlobalAlloc",
                FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG));
            GLOBAL_LOCK = kernel32("GlobalLock",
                FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS));
            GLOBAL_UNLOCK = kernel32("GlobalUnlock",
                FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
            GLOBAL_SIZE = kernel32("GlobalSize",
                FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS));

            // Register HTML clipboard format
            int htmlFmt = 0;
            try (Arena a = Arena.ofConfined()) {
                byte[] bytes = "HTML Format\0".getBytes(StandardCharsets.UTF_16LE);
                MemorySegment namePtr = a.allocate(bytes.length);
                namePtr.copyFrom(MemorySegment.ofArray(bytes));
                htmlFmt = (int) REGISTER_CLIPBOARD_FORMAT_W.invokeExact(namePtr);
            } catch (Throwable ignored) {
            }
            CF_HTML_FORMAT = htmlFmt;
        }

        // --- GDI+ bindings for clipboard image read (optional, null if unavailable) ---
        private static final SymbolLookup GDIPLUS;
        private static final MethodHandle GDIP_STARTUP;
        private static final MethodHandle GDIP_SHUTDOWN;
        private static final MethodHandle GDIP_CREATE_BITMAP_FROM_GDI_DIB;
        private static final MethodHandle GDIP_SAVE_IMAGE_TO_FILE;
        private static final MethodHandle GDIP_DISPOSE_IMAGE;

        // PNG encoder CLSID: {557CF406-1A04-11D3-9A73-0000F81EF32E}
        private static final byte[] PNG_ENCODER_CLSID = {
            (byte)0x06, (byte)0xF4, (byte)0x7C, (byte)0x55,
            (byte)0x04, (byte)0x1A,
            (byte)0xD3, (byte)0x11,
            (byte)0x9A, (byte)0x73,
            (byte)0x00, (byte)0x00, (byte)0xF8, (byte)0x1E, (byte)0xF3, (byte)0x2E
        };

        static {
            SymbolLookup gdiplus = null;
            MethodHandle startup = null, shutdown = null, createBitmap = null,
                          saveImage = null, disposeImage = null;
            try {
                gdiplus = SymbolLookup.libraryLookup("gdiplus.dll", Arena.global());

                startup = LINKER.downcallHandle(
                    gdiplus.find("GdiplusStartup").orElseThrow(),
                    FunctionDescriptor.of(ValueLayout.JAVA_INT,
                        ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS));

                shutdown = LINKER.downcallHandle(
                    gdiplus.find("GdiplusShutdown").orElseThrow(),
                    FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));

                createBitmap = LINKER.downcallHandle(
                    gdiplus.find("GdipCreateBitmapFromGdiDib").orElseThrow(),
                    FunctionDescriptor.of(ValueLayout.JAVA_INT,
                        ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS));

                saveImage = LINKER.downcallHandle(
                    gdiplus.find("GdipSaveImageToFile").orElseThrow(),
                    FunctionDescriptor.of(ValueLayout.JAVA_INT,
                        ValueLayout.ADDRESS, ValueLayout.ADDRESS,
                        ValueLayout.ADDRESS, ValueLayout.ADDRESS));

                disposeImage = LINKER.downcallHandle(
                    gdiplus.find("GdipDisposeImage").orElseThrow(),
                    FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
            } catch (Exception ignored) {
                // GDI+ not available
            }
            GDIPLUS = gdiplus;
            GDIP_STARTUP = startup;
            GDIP_SHUTDOWN = shutdown;
            GDIP_CREATE_BITMAP_FROM_GDI_DIB = createBitmap;
            GDIP_SAVE_IMAGE_TO_FILE = saveImage;
            GDIP_DISPOSE_IMAGE = disposeImage;
        }

        private static MethodHandle user32(String name, FunctionDescriptor desc) {
            return LINKER.downcallHandle(USER32.find(name).orElseThrow(), desc);
        }

        private static MethodHandle kernel32(String name, FunctionDescriptor desc) {
            return LINKER.downcallHandle(KERNEL32.find(name).orElseThrow(), desc);
        }

        static String readImageBase64() throws Throwable {
            if (GDIP_STARTUP == null) return null;

            if ((int) OPEN_CLIPBOARD.invokeExact(MemorySegment.NULL) == 0) return null;
            try {
                MemorySegment hDib = (MemorySegment) GET_CLIPBOARD_DATA.invokeExact(CF_DIB);
                if (hDib.address() == 0) return null;

                MemorySegment dibPtr = (MemorySegment) GLOBAL_LOCK.invokeExact(hDib);
                if (dibPtr.address() == 0) return null;
                try {
                    long dibSize = (long) GLOBAL_SIZE.invokeExact(hDib);
                    dibPtr = dibPtr.reinterpret(dibSize);

                    // Parse BITMAPINFOHEADER to find pixel data offset
                    int headerSize = dibPtr.get(ValueLayout.JAVA_INT, 0);
                    short bitsPerPixel = dibPtr.get(ValueLayout.JAVA_SHORT, 14);
                    int clrUsed = dibPtr.get(ValueLayout.JAVA_INT, 32);
                    int compression = dibPtr.get(ValueLayout.JAVA_INT, 16);

                    // Compute color table size
                    int colorTableEntries;
                    if (clrUsed > 0) {
                        colorTableEntries = clrUsed;
                    } else if (bitsPerPixel <= 8) {
                        colorTableEntries = 1 << bitsPerPixel;
                    } else if (compression == 3 && bitsPerPixel >= 16) {
                        // BI_BITFIELDS: 3 DWORD masks follow the header
                        colorTableEntries = 3;
                    } else {
                        colorTableEntries = 0;
                    }
                    long pixelDataOffset = headerSize + (long) colorTableEntries * 4;

                    // Initialize GDI+
                    try (Arena arena = Arena.ofConfined()) {
                        // GdiplusStartupInput: 24 bytes on 64-bit
                        // offset 0: UINT32 GdiplusVersion = 1
                        // offset 4: padding
                        // offset 8: pointer DebugEventCallback = NULL
                        // offset 16: BOOL SuppressBackgroundThread = 0
                        // offset 20: BOOL SuppressExternalCodecs = 0
                        MemorySegment startupInput = arena.allocate(24);
                        startupInput.set(ValueLayout.JAVA_INT, 0, 1); // GdiplusVersion

                        MemorySegment token = arena.allocate(ValueLayout.ADDRESS);
                        int status = (int) GDIP_STARTUP.invokeExact(token, startupInput, MemorySegment.NULL);
                        if (status != 0) return null;

                        MemorySegment gdipToken = token.get(ValueLayout.ADDRESS, 0);
                        try {
                            // Create bitmap from DIB
                            MemorySegment bitmapOut = arena.allocate(ValueLayout.ADDRESS);
                            MemorySegment pixelData = dibPtr.asSlice(pixelDataOffset);
                            status = (int) GDIP_CREATE_BITMAP_FROM_GDI_DIB.invokeExact(
                                dibPtr, pixelData, bitmapOut
                            );
                            if (status != 0) return null;

                            MemorySegment bitmap = bitmapOut.get(ValueLayout.ADDRESS, 0);
                            try {
                                // Save to temp PNG file
                                Path tempPng = Files.createTempFile("krema_clip_", ".png");
                                try {
                                    // Allocate CLSID (16 bytes)
                                    MemorySegment clsid = arena.allocate(16);
                                    clsid.copyFrom(MemorySegment.ofArray(PNG_ENCODER_CLSID));

                                    // Convert path to wide string
                                    String tempPath = tempPng.toAbsolutePath().toString();
                                    byte[] pathBytes = (tempPath + "\0")
                                        .getBytes(StandardCharsets.UTF_16LE);
                                    MemorySegment pathPtr = arena.allocate(pathBytes.length);
                                    pathPtr.copyFrom(MemorySegment.ofArray(pathBytes));

                                    status = (int) GDIP_SAVE_IMAGE_TO_FILE.invokeExact(
                                        bitmap, pathPtr, clsid, MemorySegment.NULL
                                    );
                                    if (status != 0) return null;

                                    // Read PNG bytes and encode to Base64
                                    byte[] pngBytes = Files.readAllBytes(tempPng);
                                    return Base64.getEncoder().encodeToString(pngBytes);
                                } finally {
                                    Files.deleteIfExists(tempPng);
                                }
                            } finally {
                                GDIP_DISPOSE_IMAGE.invokeExact(bitmap);
                            }
                        } finally {
                            GDIP_SHUTDOWN.invokeExact(gdipToken);
                        }
                    }
                } finally {
                    GLOBAL_UNLOCK.invokeExact(hDib);
                }
            } finally {
                CLOSE_CLIPBOARD.invokeExact();
            }
        }

        static String readText() throws Throwable {
            if ((int) OPEN_CLIPBOARD.invokeExact(MemorySegment.NULL) == 0) return null;
            try {
                MemorySegment hMem = (MemorySegment) GET_CLIPBOARD_DATA.invokeExact(CF_UNICODETEXT);
                if (hMem.address() == 0) return null;
                MemorySegment ptr = (MemorySegment) GLOBAL_LOCK.invokeExact(hMem);
                if (ptr.address() == 0) return null;
                try {
                    long size = (long) GLOBAL_SIZE.invokeExact(hMem);
                    byte[] bytes = ptr.reinterpret(size).toArray(ValueLayout.JAVA_BYTE);
                    // Find null terminator
                    int len = 0;
                    for (int i = 0; i < bytes.length - 1; i += 2) {
                        if (bytes[i] == 0 && bytes[i + 1] == 0) break;
                        len += 2;
                    }
                    return new String(bytes, 0, len, StandardCharsets.UTF_16LE);
                } finally {
                    GLOBAL_UNLOCK.invokeExact(hMem);
                }
            } finally {
                CLOSE_CLIPBOARD.invokeExact();
            }
        }

        static boolean writeText(String text) throws Throwable {
            byte[] bytes = (text + "\0").getBytes(StandardCharsets.UTF_16LE);
            MemorySegment hMem = (MemorySegment) GLOBAL_ALLOC.invokeExact(GMEM_MOVEABLE, (long) bytes.length);
            if (hMem.address() == 0) return false;
            MemorySegment ptr = (MemorySegment) GLOBAL_LOCK.invokeExact(hMem);
            if (ptr.address() == 0) return false;
            ptr.reinterpret(bytes.length).copyFrom(MemorySegment.ofArray(bytes));
            GLOBAL_UNLOCK.invokeExact(hMem);

            if ((int) OPEN_CLIPBOARD.invokeExact(MemorySegment.NULL) == 0) return false;
            try {
                EMPTY_CLIPBOARD.invokeExact();
                MemorySegment result = (MemorySegment) SET_CLIPBOARD_DATA.invokeExact(CF_UNICODETEXT, hMem);
                return result.address() != 0;
            } finally {
                CLOSE_CLIPBOARD.invokeExact();
            }
        }

        static String readHtml() throws Throwable {
            if (CF_HTML_FORMAT == 0) return null;
            if ((int) OPEN_CLIPBOARD.invokeExact(MemorySegment.NULL) == 0) return null;
            try {
                MemorySegment hMem = (MemorySegment) GET_CLIPBOARD_DATA.invokeExact(CF_HTML_FORMAT);
                if (hMem.address() == 0) return null;
                MemorySegment ptr = (MemorySegment) GLOBAL_LOCK.invokeExact(hMem);
                if (ptr.address() == 0) return null;
                try {
                    long size = (long) GLOBAL_SIZE.invokeExact(hMem);
                    byte[] bytes = ptr.reinterpret(size).toArray(ValueLayout.JAVA_BYTE);
                    return new String(bytes, StandardCharsets.UTF_8).stripTrailing();
                } finally {
                    GLOBAL_UNLOCK.invokeExact(hMem);
                }
            } finally {
                CLOSE_CLIPBOARD.invokeExact();
            }
        }

        static boolean hasText() throws Throwable {
            return (int) IS_CLIPBOARD_FORMAT_AVAILABLE.invokeExact(CF_UNICODETEXT) != 0;
        }

        static boolean hasImage() throws Throwable {
            return (int) IS_CLIPBOARD_FORMAT_AVAILABLE.invokeExact(CF_BITMAP) != 0
                || (int) IS_CLIPBOARD_FORMAT_AVAILABLE.invokeExact(CF_DIB) != 0;
        }

        static boolean clear() throws Throwable {
            if ((int) OPEN_CLIPBOARD.invokeExact(MemorySegment.NULL) == 0) return false;
            try {
                EMPTY_CLIPBOARD.invokeExact();
                return true;
            } finally {
                CLOSE_CLIPBOARD.invokeExact();
            }
        }

        static String[] getAvailableFormats() throws Throwable {
            if ((int) OPEN_CLIPBOARD.invokeExact(MemorySegment.NULL) == 0) return new String[0];
            try (Arena arena = Arena.ofConfined()) {
                List<String> formats = new ArrayList<>();
                int format = 0;
                while ((format = (int) ENUM_CLIPBOARD_FORMATS.invokeExact(format)) != 0) {
                    String name = getFormatName(arena, format);
                    formats.add(name != null ? name : "CF_" + format);
                }
                return formats.toArray(new String[0]);
            } finally {
                CLOSE_CLIPBOARD.invokeExact();
            }
        }

        private static String getFormatName(Arena arena, int format) throws Throwable {
            return switch (format) {
                case 1 -> "CF_TEXT";
                case 2 -> "CF_BITMAP";
                case 3 -> "CF_METAFILEPICT";
                case 4 -> "CF_SYLK";
                case 5 -> "CF_DIF";
                case 6 -> "CF_TIFF";
                case 7 -> "CF_OEMTEXT";
                case 8 -> "CF_DIB";
                case 13 -> "CF_UNICODETEXT";
                case 14 -> "CF_ENHMETAFILE";
                case 15 -> "CF_HDROP";
                case 16 -> "CF_LOCALE";
                case 17 -> "CF_DIBV5";
                default -> {
                    MemorySegment buf = arena.allocate(512);
                    int len = (int) GET_CLIPBOARD_FORMAT_NAME_W.invokeExact(format, buf, 256);
                    if (len > 0) {
                        byte[] bytes = buf.reinterpret((long) len * 2).toArray(ValueLayout.JAVA_BYTE);
                        yield new String(bytes, StandardCharsets.UTF_16LE);
                    }
                    yield null;
                }
            };
        }
    }

    // ===== Linux: GTK3 Clipboard via FFM =====

    private static final class Linux {

        private static final Arena ARENA = Arena.ofAuto();
        private static MemorySegment clipboardAtom;

        private static MemorySegment getClipboard() throws Throwable {
            if (clipboardAtom == null) {
                MemorySegment atomName = ARENA.allocateFrom("CLIPBOARD");
                clipboardAtom = (MemorySegment) GtkBindings.GDK_ATOM_INTERN.invokeExact(atomName, 0);
            }
            return (MemorySegment) GtkBindings.GTK_CLIPBOARD_GET.invokeExact(clipboardAtom);
        }

        static String readText() throws Throwable {
            MemorySegment clipboard = getClipboard();
            MemorySegment text = (MemorySegment) GtkBindings.GTK_CLIPBOARD_WAIT_FOR_TEXT
                .invokeExact(clipboard);
            if (text.address() == 0) return null;
            try {
                return text.reinterpret(Integer.MAX_VALUE).getString(0);
            } finally {
                GtkBindings.G_FREE.invokeExact(text);
            }
        }

        static boolean writeText(String text) throws Throwable {
            MemorySegment clipboard = getClipboard();
            MemorySegment textPtr = ARENA.allocateFrom(text);
            GtkBindings.GTK_CLIPBOARD_SET_TEXT.invokeExact(clipboard, textPtr, -1);
            return true;
        }

        static String readHtml() throws Throwable {
            MemorySegment clipboard = getClipboard();
            MemorySegment htmlAtom = (MemorySegment) GtkBindings.GDK_ATOM_INTERN.invokeExact(
                ARENA.allocateFrom("text/html"), 0
            );
            MemorySegment selData = (MemorySegment) GtkBindings.GTK_CLIPBOARD_WAIT_FOR_CONTENTS
                .invokeExact(clipboard, htmlAtom);
            if (selData.address() == 0) return null;
            try {
                int length = (int) GtkBindings.GTK_SELECTION_DATA_GET_LENGTH.invokeExact(selData);
                if (length <= 0) return null;
                MemorySegment data = (MemorySegment) GtkBindings.GTK_SELECTION_DATA_GET_DATA
                    .invokeExact(selData);
                if (data.address() == 0) return null;
                byte[] bytes = data.reinterpret(length).toArray(ValueLayout.JAVA_BYTE);
                return new String(bytes, StandardCharsets.UTF_8);
            } finally {
                GtkBindings.GTK_SELECTION_DATA_FREE.invokeExact(selData);
            }
        }

        static boolean hasText() throws Throwable {
            MemorySegment clipboard = getClipboard();
            return ((int) GtkBindings.GTK_CLIPBOARD_WAIT_IS_TEXT_AVAILABLE
                .invokeExact(clipboard)) != 0;
        }

        static boolean hasImage() throws Throwable {
            String[] formats = getAvailableFormats();
            for (String format : formats) {
                if (format.startsWith("image/")) return true;
            }
            return false;
        }

        static String readImageBase64() throws Throwable {
            MemorySegment clipboard = getClipboard();
            MemorySegment pngAtom = (MemorySegment) GtkBindings.GDK_ATOM_INTERN.invokeExact(
                ARENA.allocateFrom("image/png"), 0
            );
            MemorySegment selData = (MemorySegment) GtkBindings.GTK_CLIPBOARD_WAIT_FOR_CONTENTS
                .invokeExact(clipboard, pngAtom);
            if (selData.address() == 0) return null;
            try {
                int length = (int) GtkBindings.GTK_SELECTION_DATA_GET_LENGTH.invokeExact(selData);
                if (length <= 0) return null;
                MemorySegment data = (MemorySegment) GtkBindings.GTK_SELECTION_DATA_GET_DATA
                    .invokeExact(selData);
                if (data.address() == 0) return null;
                byte[] bytes = data.reinterpret(length).toArray(ValueLayout.JAVA_BYTE);
                return Base64.getEncoder().encodeToString(bytes);
            } finally {
                GtkBindings.GTK_SELECTION_DATA_FREE.invokeExact(selData);
            }
        }

        static boolean clear() throws Throwable {
            MemorySegment clipboard = getClipboard();
            GtkBindings.GTK_CLIPBOARD_CLEAR.invokeExact(clipboard);
            return true;
        }

        static String[] getAvailableFormats() throws Throwable {
            MemorySegment clipboard = getClipboard();
            MemorySegment targetsOut = ARENA.allocate(ValueLayout.ADDRESS);
            MemorySegment nTargetsOut = ARENA.allocate(ValueLayout.JAVA_INT);

            int result = (int) GtkBindings.GTK_CLIPBOARD_WAIT_FOR_TARGETS
                .invokeExact(clipboard, targetsOut, nTargetsOut);
            if (result == 0) return new String[0];

            int nTargets = nTargetsOut.get(ValueLayout.JAVA_INT, 0);
            MemorySegment targets = targetsOut.get(ValueLayout.ADDRESS, 0);
            if (targets.address() == 0 || nTargets <= 0) return new String[0];

            targets = targets.reinterpret((long) nTargets * ValueLayout.ADDRESS.byteSize());
            try {
                List<String> formats = new ArrayList<>();
                for (int i = 0; i < nTargets; i++) {
                    MemorySegment atom = targets.getAtIndex(ValueLayout.ADDRESS, i);
                    MemorySegment name = (MemorySegment) GtkBindings.GDK_ATOM_NAME
                        .invokeExact(atom);
                    if (name.address() != 0) {
                        try {
                            formats.add(name.reinterpret(256).getString(0));
                        } finally {
                            GtkBindings.G_FREE.invokeExact(name);
                        }
                    }
                }
                return formats.toArray(new String[0]);
            } finally {
                GtkBindings.G_FREE.invokeExact(targets);
            }
        }
    }
}
