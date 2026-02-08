package build.krema.core.api.securestorage;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import build.krema.core.KremaCommand;
import build.krema.core.platform.Platform;

/**
 * Cross-platform secure storage API using platform-native credential stores.
 * <ul>
 *   <li>macOS: Keychain Services (Security.framework)</li>
 *   <li>Windows: Credential Manager (advapi32.dll)</li>
 *   <li>Linux: Secret Service (libsecret)</li>
 * </ul>
 */
public class SecureStorage {

    private final String serviceName;

    public SecureStorage(String serviceName) {
        this.serviceName = serviceName;
    }

    @KremaCommand("secureStorage:set")
    public boolean set(Map<String, Object> options) {
        String key = (String) options.get("key");
        String value = (String) options.get("value");
        if (key == null || value == null) return false;
        try {
            return switch (Platform.current()) {
                case MACOS -> MacOS.set(serviceName, key, value);
                case WINDOWS -> Windows.set(serviceName, key, value);
                case LINUX -> Linux.set(serviceName, key, value);
                case UNKNOWN -> false;
            };
        } catch (Throwable e) {
            return false;
        }
    }

    @KremaCommand("secureStorage:get")
    public String get(Map<String, Object> options) {
        String key = (String) options.get("key");
        if (key == null) return null;
        try {
            return switch (Platform.current()) {
                case MACOS -> MacOS.get(serviceName, key);
                case WINDOWS -> Windows.get(serviceName, key);
                case LINUX -> Linux.get(serviceName, key);
                case UNKNOWN -> null;
            };
        } catch (Throwable e) {
            return null;
        }
    }

    @KremaCommand("secureStorage:delete")
    public boolean delete(Map<String, Object> options) {
        String key = (String) options.get("key");
        if (key == null) return false;
        try {
            return switch (Platform.current()) {
                case MACOS -> MacOS.delete(serviceName, key);
                case WINDOWS -> Windows.delete(serviceName, key);
                case LINUX -> Linux.delete(serviceName, key);
                case UNKNOWN -> false;
            };
        } catch (Throwable e) {
            return false;
        }
    }

    @KremaCommand("secureStorage:has")
    public boolean has(Map<String, Object> options) {
        String key = (String) options.get("key");
        if (key == null) return false;
        try {
            return switch (Platform.current()) {
                case MACOS -> MacOS.has(serviceName, key);
                case WINDOWS -> Windows.has(serviceName, key);
                case LINUX -> Linux.has(serviceName, key);
                case UNKNOWN -> false;
            };
        } catch (Throwable e) {
            return false;
        }
    }

    // ===== macOS: Keychain Services via FFM =====

    static final class MacOS {

        private static final Linker LINKER = Linker.nativeLinker();

        // CoreFoundation
        private static final SymbolLookup CF_LOOKUP;
        private static final MethodHandle CF_DICTIONARY_CREATE_MUTABLE;
        private static final MethodHandle CF_DICTIONARY_ADD_VALUE;
        private static final MethodHandle CF_STRING_CREATE;
        private static final MethodHandle CF_DATA_CREATE;
        private static final MethodHandle CF_DATA_GET_BYTE_PTR;
        private static final MethodHandle CF_DATA_GET_LENGTH;
        private static final MethodHandle CF_RELEASE;

        // Security.framework
        private static final SymbolLookup SEC_LOOKUP;
        private static final MethodHandle SEC_ITEM_ADD;
        private static final MethodHandle SEC_ITEM_COPY_MATCHING;
        private static final MethodHandle SEC_ITEM_UPDATE;
        private static final MethodHandle SEC_ITEM_DELETE;

        // Security constants (global CFStringRef pointers — need dereference)
        private static final MemorySegment kSecClass;
        private static final MemorySegment kSecClassGenericPassword;
        private static final MemorySegment kSecAttrService;
        private static final MemorySegment kSecAttrAccount;
        private static final MemorySegment kSecValueData;
        private static final MemorySegment kSecReturnData;
        private static final MemorySegment kSecMatchLimit;
        private static final MemorySegment kSecMatchLimitOne;

        // CoreFoundation constants
        private static final MemorySegment kCFBooleanTrue;
        private static final MemorySegment kCFTypeDictionaryKeyCallBacks;
        private static final MemorySegment kCFTypeDictionaryValueCallBacks;

        // OSStatus codes
        private static final int errSecSuccess = 0;
        private static final int errSecItemNotFound = -25300;
        private static final int errSecDuplicateItem = -25299;

        static {
            CF_LOOKUP = SymbolLookup.libraryLookup(
                "/System/Library/Frameworks/CoreFoundation.framework/CoreFoundation",
                Arena.global()
            );
            SEC_LOOKUP = SymbolLookup.libraryLookup(
                "/System/Library/Frameworks/Security.framework/Security",
                Arena.global()
            );

            // CoreFoundation function handles
            CF_DICTIONARY_CREATE_MUTABLE = LINKER.downcallHandle(
                CF_LOOKUP.find("CFDictionaryCreateMutable").orElseThrow(),
                FunctionDescriptor.of(ValueLayout.ADDRESS,
                    ValueLayout.ADDRESS, ValueLayout.JAVA_LONG,
                    ValueLayout.ADDRESS, ValueLayout.ADDRESS)
            );
            CF_DICTIONARY_ADD_VALUE = LINKER.downcallHandle(
                CF_LOOKUP.find("CFDictionaryAddValue").orElseThrow(),
                FunctionDescriptor.ofVoid(
                    ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS)
            );
            CF_STRING_CREATE = LINKER.downcallHandle(
                CF_LOOKUP.find("CFStringCreateWithCString").orElseThrow(),
                FunctionDescriptor.of(ValueLayout.ADDRESS,
                    ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT)
            );
            CF_DATA_CREATE = LINKER.downcallHandle(
                CF_LOOKUP.find("CFDataCreate").orElseThrow(),
                FunctionDescriptor.of(ValueLayout.ADDRESS,
                    ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG)
            );
            CF_DATA_GET_BYTE_PTR = LINKER.downcallHandle(
                CF_LOOKUP.find("CFDataGetBytePtr").orElseThrow(),
                FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS)
            );
            CF_DATA_GET_LENGTH = LINKER.downcallHandle(
                CF_LOOKUP.find("CFDataGetLength").orElseThrow(),
                FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS)
            );
            CF_RELEASE = LINKER.downcallHandle(
                CF_LOOKUP.find("CFRelease").orElseThrow(),
                FunctionDescriptor.ofVoid(ValueLayout.ADDRESS)
            );

            // Security function handles
            SEC_ITEM_ADD = LINKER.downcallHandle(
                SEC_LOOKUP.find("SecItemAdd").orElseThrow(),
                FunctionDescriptor.of(ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS, ValueLayout.ADDRESS)
            );
            SEC_ITEM_COPY_MATCHING = LINKER.downcallHandle(
                SEC_LOOKUP.find("SecItemCopyMatching").orElseThrow(),
                FunctionDescriptor.of(ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS, ValueLayout.ADDRESS)
            );
            SEC_ITEM_UPDATE = LINKER.downcallHandle(
                SEC_LOOKUP.find("SecItemUpdate").orElseThrow(),
                FunctionDescriptor.of(ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS, ValueLayout.ADDRESS)
            );
            SEC_ITEM_DELETE = LINKER.downcallHandle(
                SEC_LOOKUP.find("SecItemDelete").orElseThrow(),
                FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS)
            );

            // Resolve Security constants (global const CFStringRef — pointer dereference)
            kSecClass = resolveConstant(SEC_LOOKUP, "kSecClass");
            kSecClassGenericPassword = resolveConstant(SEC_LOOKUP, "kSecClassGenericPassword");
            kSecAttrService = resolveConstant(SEC_LOOKUP, "kSecAttrService");
            kSecAttrAccount = resolveConstant(SEC_LOOKUP, "kSecAttrAccount");
            kSecValueData = resolveConstant(SEC_LOOKUP, "kSecValueData");
            kSecReturnData = resolveConstant(SEC_LOOKUP, "kSecReturnData");
            kSecMatchLimit = resolveConstant(SEC_LOOKUP, "kSecMatchLimit");
            kSecMatchLimitOne = resolveConstant(SEC_LOOKUP, "kSecMatchLimitOne");

            // CoreFoundation constants
            kCFBooleanTrue = resolveConstant(CF_LOOKUP, "kCFBooleanTrue");
            // These are struct values — use the symbol address directly (no dereference)
            kCFTypeDictionaryKeyCallBacks = CF_LOOKUP.find("kCFTypeDictionaryKeyCallBacks").orElseThrow();
            kCFTypeDictionaryValueCallBacks = CF_LOOKUP.find("kCFTypeDictionaryValueCallBacks").orElseThrow();
        }

        private static MemorySegment resolveConstant(SymbolLookup lookup, String name) {
            return lookup.find(name).orElseThrow()
                .reinterpret(ValueLayout.ADDRESS.byteSize())
                .get(ValueLayout.ADDRESS, 0);
        }

        private static MemorySegment createCFString(String str) throws Throwable {
            try (Arena temp = Arena.ofConfined()) {
                MemorySegment cString = temp.allocateFrom(str);
                return (MemorySegment) CF_STRING_CREATE.invokeExact(
                    MemorySegment.NULL, cString, 0x08000100 // kCFStringEncodingUTF8
                );
            }
        }

        private static MemorySegment createCFData(byte[] data) throws Throwable {
            try (Arena temp = Arena.ofConfined()) {
                MemorySegment buf = temp.allocate(data.length);
                buf.copyFrom(MemorySegment.ofArray(data));
                return (MemorySegment) CF_DATA_CREATE.invokeExact(
                    MemorySegment.NULL, buf, (long) data.length
                );
            }
        }

        private static byte[] cfDataToBytes(MemorySegment cfData) throws Throwable {
            long length = (long) CF_DATA_GET_LENGTH.invokeExact(cfData);
            if (length <= 0) return new byte[0];
            MemorySegment ptr = (MemorySegment) CF_DATA_GET_BYTE_PTR.invokeExact(cfData);
            return ptr.reinterpret(length).toArray(ValueLayout.JAVA_BYTE);
        }

        private static MemorySegment createMutableDictionary(int capacity) throws Throwable {
            return (MemorySegment) CF_DICTIONARY_CREATE_MUTABLE.invokeExact(
                MemorySegment.NULL, (long) capacity,
                kCFTypeDictionaryKeyCallBacks, kCFTypeDictionaryValueCallBacks
            );
        }

        private static void safeRelease(MemorySegment ref) {
            if (ref != null && ref.address() != 0) {
                try {
                    CF_RELEASE.invokeExact(ref);
                } catch (Throwable ignored) {}
            }
        }

        static boolean set(String service, String key, String value) throws Throwable {
            MemorySegment cfService = createCFString(service);
            MemorySegment cfKey = createCFString(key);
            MemorySegment cfValue = createCFData(value.getBytes(StandardCharsets.UTF_8));
            try {
                // Build add dictionary
                MemorySegment dict = createMutableDictionary(4);
                try {
                    CF_DICTIONARY_ADD_VALUE.invokeExact(dict, kSecClass, kSecClassGenericPassword);
                    CF_DICTIONARY_ADD_VALUE.invokeExact(dict, kSecAttrService, cfService);
                    CF_DICTIONARY_ADD_VALUE.invokeExact(dict, kSecAttrAccount, cfKey);
                    CF_DICTIONARY_ADD_VALUE.invokeExact(dict, kSecValueData, cfValue);

                    int status = (int) SEC_ITEM_ADD.invokeExact(dict, MemorySegment.NULL);
                    if (status == errSecSuccess) return true;

                    if (status == errSecDuplicateItem) {
                        // Update existing item
                        MemorySegment query = createMutableDictionary(3);
                        MemorySegment update = createMutableDictionary(1);
                        try {
                            CF_DICTIONARY_ADD_VALUE.invokeExact(query, kSecClass, kSecClassGenericPassword);
                            CF_DICTIONARY_ADD_VALUE.invokeExact(query, kSecAttrService, cfService);
                            CF_DICTIONARY_ADD_VALUE.invokeExact(query, kSecAttrAccount, cfKey);
                            CF_DICTIONARY_ADD_VALUE.invokeExact(update, kSecValueData, cfValue);

                            status = (int) SEC_ITEM_UPDATE.invokeExact(query, update);
                            return status == errSecSuccess;
                        } finally {
                            safeRelease(query);
                            safeRelease(update);
                        }
                    }
                    return false;
                } finally {
                    safeRelease(dict);
                }
            } finally {
                safeRelease(cfService);
                safeRelease(cfKey);
                safeRelease(cfValue);
            }
        }

        static String get(String service, String key) throws Throwable {
            MemorySegment cfService = createCFString(service);
            MemorySegment cfKey = createCFString(key);
            try {
                MemorySegment query = createMutableDictionary(5);
                try {
                    CF_DICTIONARY_ADD_VALUE.invokeExact(query, kSecClass, kSecClassGenericPassword);
                    CF_DICTIONARY_ADD_VALUE.invokeExact(query, kSecAttrService, cfService);
                    CF_DICTIONARY_ADD_VALUE.invokeExact(query, kSecAttrAccount, cfKey);
                    CF_DICTIONARY_ADD_VALUE.invokeExact(query, kSecReturnData, kCFBooleanTrue);
                    CF_DICTIONARY_ADD_VALUE.invokeExact(query, kSecMatchLimit, kSecMatchLimitOne);

                    try (Arena temp = Arena.ofConfined()) {
                        MemorySegment resultPtr = temp.allocate(ValueLayout.ADDRESS);
                        int status = (int) SEC_ITEM_COPY_MATCHING.invokeExact(query, resultPtr);
                        if (status != errSecSuccess) return null;

                        MemorySegment cfData = resultPtr.get(ValueLayout.ADDRESS, 0);
                        try {
                            byte[] bytes = cfDataToBytes(cfData);
                            return new String(bytes, StandardCharsets.UTF_8);
                        } finally {
                            safeRelease(cfData);
                        }
                    }
                } finally {
                    safeRelease(query);
                }
            } finally {
                safeRelease(cfService);
                safeRelease(cfKey);
            }
        }

        static boolean delete(String service, String key) throws Throwable {
            MemorySegment cfService = createCFString(service);
            MemorySegment cfKey = createCFString(key);
            try {
                MemorySegment query = createMutableDictionary(3);
                try {
                    CF_DICTIONARY_ADD_VALUE.invokeExact(query, kSecClass, kSecClassGenericPassword);
                    CF_DICTIONARY_ADD_VALUE.invokeExact(query, kSecAttrService, cfService);
                    CF_DICTIONARY_ADD_VALUE.invokeExact(query, kSecAttrAccount, cfKey);

                    int status = (int) SEC_ITEM_DELETE.invokeExact(query);
                    return status == errSecSuccess;
                } finally {
                    safeRelease(query);
                }
            } finally {
                safeRelease(cfService);
                safeRelease(cfKey);
            }
        }

        static boolean has(String service, String key) throws Throwable {
            MemorySegment cfService = createCFString(service);
            MemorySegment cfKey = createCFString(key);
            try {
                MemorySegment query = createMutableDictionary(4);
                try {
                    CF_DICTIONARY_ADD_VALUE.invokeExact(query, kSecClass, kSecClassGenericPassword);
                    CF_DICTIONARY_ADD_VALUE.invokeExact(query, kSecAttrService, cfService);
                    CF_DICTIONARY_ADD_VALUE.invokeExact(query, kSecAttrAccount, cfKey);
                    CF_DICTIONARY_ADD_VALUE.invokeExact(query, kSecMatchLimit, kSecMatchLimitOne);

                    int status = (int) SEC_ITEM_COPY_MATCHING.invokeExact(query, MemorySegment.NULL);
                    return status == errSecSuccess;
                } finally {
                    safeRelease(query);
                }
            } finally {
                safeRelease(cfService);
                safeRelease(cfKey);
            }
        }
    }

    // ===== Windows: Credential Manager via FFM =====

    static final class Windows {

        private static final Linker LINKER = Linker.nativeLinker();
        private static final SymbolLookup ADVAPI32 = SymbolLookup.libraryLookup("advapi32.dll", Arena.global());

        private static final int CRED_TYPE_GENERIC = 1;

        // CREDENTIALW struct offsets (64-bit)
        private static final long OFFSET_FLAGS = 0;
        private static final long OFFSET_TYPE = 4;
        private static final long OFFSET_TARGET_NAME = 8;
        private static final long OFFSET_CRED_BLOB_SIZE = 24;
        private static final long OFFSET_CRED_BLOB = 32;
        private static final long OFFSET_USER_NAME = 48;
        private static final long CREDENTIALW_SIZE = 80;

        private static final MethodHandle CRED_WRITE_W;
        private static final MethodHandle CRED_READ_W;
        private static final MethodHandle CRED_DELETE_W;
        private static final MethodHandle CRED_FREE;

        static {
            CRED_WRITE_W = LINKER.downcallHandle(
                ADVAPI32.find("CredWriteW").orElseThrow(),
                FunctionDescriptor.of(ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS, ValueLayout.JAVA_INT)
            );
            CRED_READ_W = LINKER.downcallHandle(
                ADVAPI32.find("CredReadW").orElseThrow(),
                FunctionDescriptor.of(ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS, ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT, ValueLayout.ADDRESS)
            );
            CRED_DELETE_W = LINKER.downcallHandle(
                ADVAPI32.find("CredDeleteW").orElseThrow(),
                FunctionDescriptor.of(ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT)
            );
            CRED_FREE = LINKER.downcallHandle(
                ADVAPI32.find("CredFree").orElseThrow(),
                FunctionDescriptor.ofVoid(ValueLayout.ADDRESS)
            );
        }

        private static MemorySegment allocateWideString(Arena arena, String str) {
            byte[] bytes = (str + "\0").getBytes(StandardCharsets.UTF_16LE);
            MemorySegment seg = arena.allocate(bytes.length);
            seg.copyFrom(MemorySegment.ofArray(bytes));
            return seg;
        }

        private static String targetName(String service, String key) {
            return service + "/" + key;
        }

        static boolean set(String service, String key, String value) throws Throwable {
            try (Arena arena = Arena.ofConfined()) {
                MemorySegment target = allocateWideString(arena, targetName(service, key));
                MemorySegment userName = allocateWideString(arena, key);
                byte[] blobBytes = value.getBytes(StandardCharsets.UTF_8);
                MemorySegment blob = arena.allocate(blobBytes.length);
                blob.copyFrom(MemorySegment.ofArray(blobBytes));

                MemorySegment cred = arena.allocate(CREDENTIALW_SIZE);
                cred.set(ValueLayout.JAVA_INT, OFFSET_FLAGS, 0);
                cred.set(ValueLayout.JAVA_INT, OFFSET_TYPE, CRED_TYPE_GENERIC);
                cred.set(ValueLayout.ADDRESS, OFFSET_TARGET_NAME, target);
                cred.set(ValueLayout.JAVA_INT, OFFSET_CRED_BLOB_SIZE, blobBytes.length);
                cred.set(ValueLayout.ADDRESS, OFFSET_CRED_BLOB, blob);
                cred.set(ValueLayout.ADDRESS, OFFSET_USER_NAME, userName);

                int result = (int) CRED_WRITE_W.invokeExact(cred, 0);
                return result != 0;
            }
        }

        static String get(String service, String key) throws Throwable {
            try (Arena arena = Arena.ofConfined()) {
                MemorySegment target = allocateWideString(arena, targetName(service, key));
                MemorySegment ppCred = arena.allocate(ValueLayout.ADDRESS);

                int result = (int) CRED_READ_W.invokeExact(target, CRED_TYPE_GENERIC, 0, ppCred);
                if (result == 0) return null;

                MemorySegment pCred = ppCred.get(ValueLayout.ADDRESS, 0);
                try {
                    pCred = pCred.reinterpret(CREDENTIALW_SIZE);
                    int blobSize = pCred.get(ValueLayout.JAVA_INT, OFFSET_CRED_BLOB_SIZE);
                    if (blobSize <= 0) return null;
                    MemorySegment blobPtr = pCred.get(ValueLayout.ADDRESS, OFFSET_CRED_BLOB);
                    byte[] bytes = blobPtr.reinterpret(blobSize).toArray(ValueLayout.JAVA_BYTE);
                    return new String(bytes, StandardCharsets.UTF_8);
                } finally {
                    CRED_FREE.invokeExact(pCred);
                }
            }
        }

        static boolean delete(String service, String key) throws Throwable {
            try (Arena arena = Arena.ofConfined()) {
                MemorySegment target = allocateWideString(arena, targetName(service, key));
                int result = (int) CRED_DELETE_W.invokeExact(target, CRED_TYPE_GENERIC, 0);
                return result != 0;
            }
        }

        static boolean has(String service, String key) throws Throwable {
            try (Arena arena = Arena.ofConfined()) {
                MemorySegment target = allocateWideString(arena, targetName(service, key));
                MemorySegment ppCred = arena.allocate(ValueLayout.ADDRESS);

                int result = (int) CRED_READ_W.invokeExact(target, CRED_TYPE_GENERIC, 0, ppCred);
                if (result == 0) return false;

                MemorySegment pCred = ppCred.get(ValueLayout.ADDRESS, 0);
                CRED_FREE.invokeExact(pCred);
                return true;
            }
        }
    }

    // ===== Linux: libsecret via FFM =====

    static final class Linux {

        private static final Linker LINKER = Linker.nativeLinker();
        private static final SymbolLookup LIBSECRET =
            SymbolLookup.libraryLookup("libsecret-1.so.0", Arena.global());
        private static final SymbolLookup GLIB =
            SymbolLookup.libraryLookup("libglib-2.0.so.0", Arena.global());

        // SecretSchemaAttributeType
        private static final int SECRET_SCHEMA_ATTRIBUTE_STRING = 0;
        // SecretSchemaFlags
        private static final int SECRET_SCHEMA_NONE = 0;

        // Schema struct layout (name + flags + 3 attributes of {name, type} + null terminator)
        // SecretSchema: pointer(name) + int(flags) + padding + array of {pointer(name), int(type)}
        private static final MemorySegment SCHEMA;

        private static final MethodHandle SECRET_PASSWORD_STORE_SYNC;
        private static final MethodHandle SECRET_PASSWORD_LOOKUP_SYNC;
        private static final MethodHandle SECRET_PASSWORD_CLEAR_SYNC;
        private static final MethodHandle SECRET_PASSWORD_FREE;
        private static final MethodHandle G_FREE;

        // SECRET_COLLECTION_DEFAULT = "default"
        private static final MemorySegment SECRET_COLLECTION_DEFAULT;

        static {
            // Build a custom SecretSchema in global memory
            // struct SecretSchema {
            //   const gchar *name;           // offset 0 (8 bytes)
            //   SecretSchemaFlags flags;      // offset 8 (4 bytes)
            //   /* padding */                 // offset 12 (4 bytes)
            //   struct {
            //     const gchar *name;          // 8 bytes
            //     SecretSchemaAttributeType type; // 4 bytes + 4 padding
            //   } attributes[32];
            // };
            Arena global = Arena.global();
            MemorySegment schemaName = global.allocateFrom("build.krema.core.SecureStorage");
            MemorySegment attrService = global.allocateFrom("service");
            MemorySegment attrKey = global.allocateFrom("key");

            // Total size: 8 (name) + 4 (flags) + 4 (pad) + 32 * 16 (attributes) = 528
            SCHEMA = global.allocate(528);
            SCHEMA.set(ValueLayout.ADDRESS, 0, schemaName);            // name
            SCHEMA.set(ValueLayout.JAVA_INT, 8, SECRET_SCHEMA_NONE);   // flags
            // Attribute 0: "service" -> STRING
            SCHEMA.set(ValueLayout.ADDRESS, 16, attrService);
            SCHEMA.set(ValueLayout.JAVA_INT, 24, SECRET_SCHEMA_ATTRIBUTE_STRING);
            // Attribute 1: "key" -> STRING
            SCHEMA.set(ValueLayout.ADDRESS, 32, attrKey);
            SCHEMA.set(ValueLayout.JAVA_INT, 40, SECRET_SCHEMA_ATTRIBUTE_STRING);
            // Attribute 2: null terminator (name = NULL)
            SCHEMA.set(ValueLayout.ADDRESS, 48, MemorySegment.NULL);
            SCHEMA.set(ValueLayout.JAVA_INT, 56, 0);

            SECRET_COLLECTION_DEFAULT = global.allocateFrom("default");

            // secret_password_store_sync(schema, collection, label, password, cancellable, error, ..., NULL)
            // Variadic after error (arg index 5)
            SECRET_PASSWORD_STORE_SYNC = LINKER.downcallHandle(
                LIBSECRET.find("secret_password_store_sync").orElseThrow(),
                FunctionDescriptor.of(ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS, // schema
                    ValueLayout.ADDRESS, // collection
                    ValueLayout.ADDRESS, // label
                    ValueLayout.ADDRESS, // password
                    ValueLayout.ADDRESS, // cancellable
                    ValueLayout.ADDRESS, // error
                    // variadic: attr_name, attr_val, attr_name, attr_val, NULL
                    ValueLayout.ADDRESS, ValueLayout.ADDRESS,
                    ValueLayout.ADDRESS, ValueLayout.ADDRESS,
                    ValueLayout.ADDRESS
                ),
                Linker.Option.firstVariadicArg(6)
            );

            // secret_password_lookup_sync(schema, cancellable, error, ..., NULL)
            // Variadic after error (arg index 2)
            SECRET_PASSWORD_LOOKUP_SYNC = LINKER.downcallHandle(
                LIBSECRET.find("secret_password_lookup_sync").orElseThrow(),
                FunctionDescriptor.of(ValueLayout.ADDRESS,
                    ValueLayout.ADDRESS, // schema
                    ValueLayout.ADDRESS, // cancellable
                    ValueLayout.ADDRESS, // error
                    // variadic: attr_name, attr_val, attr_name, attr_val, NULL
                    ValueLayout.ADDRESS, ValueLayout.ADDRESS,
                    ValueLayout.ADDRESS, ValueLayout.ADDRESS,
                    ValueLayout.ADDRESS
                ),
                Linker.Option.firstVariadicArg(3)
            );

            // secret_password_clear_sync(schema, cancellable, error, ..., NULL)
            // Variadic after error (arg index 2)
            SECRET_PASSWORD_CLEAR_SYNC = LINKER.downcallHandle(
                LIBSECRET.find("secret_password_clear_sync").orElseThrow(),
                FunctionDescriptor.of(ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS, // schema
                    ValueLayout.ADDRESS, // cancellable
                    ValueLayout.ADDRESS, // error
                    // variadic: attr_name, attr_val, attr_name, attr_val, NULL
                    ValueLayout.ADDRESS, ValueLayout.ADDRESS,
                    ValueLayout.ADDRESS, ValueLayout.ADDRESS,
                    ValueLayout.ADDRESS
                ),
                Linker.Option.firstVariadicArg(3)
            );

            SECRET_PASSWORD_FREE = LINKER.downcallHandle(
                LIBSECRET.find("secret_password_free").orElseThrow(),
                FunctionDescriptor.ofVoid(ValueLayout.ADDRESS)
            );

            G_FREE = LINKER.downcallHandle(
                GLIB.find("g_free").orElseThrow(),
                FunctionDescriptor.ofVoid(ValueLayout.ADDRESS)
            );
        }

        static boolean set(String service, String key, String value) throws Throwable {
            try (Arena arena = Arena.ofConfined()) {
                MemorySegment label = arena.allocateFrom(service + "/" + key);
                MemorySegment password = arena.allocateFrom(value);
                MemorySegment svcStr = arena.allocateFrom(service);
                MemorySegment keyStr = arena.allocateFrom(key);
                MemorySegment attrService = arena.allocateFrom("service");
                MemorySegment attrKey = arena.allocateFrom("key");

                int result = (int) SECRET_PASSWORD_STORE_SYNC.invokeExact(
                    SCHEMA, SECRET_COLLECTION_DEFAULT, label, password,
                    MemorySegment.NULL, MemorySegment.NULL,
                    attrService, svcStr, attrKey, keyStr, MemorySegment.NULL
                );
                return result != 0;
            }
        }

        static String get(String service, String key) throws Throwable {
            try (Arena arena = Arena.ofConfined()) {
                MemorySegment svcStr = arena.allocateFrom(service);
                MemorySegment keyStr = arena.allocateFrom(key);
                MemorySegment attrService = arena.allocateFrom("service");
                MemorySegment attrKey = arena.allocateFrom("key");

                MemorySegment result = (MemorySegment) SECRET_PASSWORD_LOOKUP_SYNC.invokeExact(
                    SCHEMA, MemorySegment.NULL, MemorySegment.NULL,
                    attrService, svcStr, attrKey, keyStr, MemorySegment.NULL
                );
                if (result.address() == 0) return null;
                try {
                    return result.reinterpret(Integer.MAX_VALUE).getString(0);
                } finally {
                    SECRET_PASSWORD_FREE.invokeExact(result);
                }
            }
        }

        static boolean delete(String service, String key) throws Throwable {
            try (Arena arena = Arena.ofConfined()) {
                MemorySegment svcStr = arena.allocateFrom(service);
                MemorySegment keyStr = arena.allocateFrom(key);
                MemorySegment attrService = arena.allocateFrom("service");
                MemorySegment attrKey = arena.allocateFrom("key");

                int result = (int) SECRET_PASSWORD_CLEAR_SYNC.invokeExact(
                    SCHEMA, MemorySegment.NULL, MemorySegment.NULL,
                    attrService, svcStr, attrKey, keyStr, MemorySegment.NULL
                );
                return result != 0;
            }
        }

        static boolean has(String service, String key) throws Throwable {
            String value = get(service, key);
            return value != null;
        }
    }
}
