package build.krema.cli.bundle.windows;

import java.io.IOException;
import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Windows Registry helper for URL protocol registration using FFM.
 * Provides both direct registry manipulation (requires elevation) and
 * script generation for installer integration.
 */
public final class WindowsRegistryHelper {

    private static final Linker LINKER = Linker.nativeLinker();
    private static final SymbolLookup ADVAPI32;

    // Registry root keys (predefined handles)
    public static final long HKEY_CLASSES_ROOT = 0x80000000L;
    public static final long HKEY_CURRENT_USER = 0x80000001L;
    public static final long HKEY_LOCAL_MACHINE = 0x80000002L;

    // Registry access rights
    public static final int KEY_READ = 0x20019;
    public static final int KEY_WRITE = 0x20006;
    public static final int KEY_ALL_ACCESS = 0xF003F;

    // Registry value types
    public static final int REG_SZ = 1;          // Unicode string
    public static final int REG_EXPAND_SZ = 2;   // Expandable string
    public static final int REG_DWORD = 4;       // 32-bit number

    // Registry disposition values (from RegCreateKeyExW)
    public static final int REG_CREATED_NEW_KEY = 1;
    public static final int REG_OPENED_EXISTING_KEY = 2;

    // Error codes
    public static final int ERROR_SUCCESS = 0;

    // Method handles for registry operations
    private static final MethodHandle REG_CREATE_KEY_EX_W;
    private static final MethodHandle REG_SET_VALUE_EX_W;
    private static final MethodHandle REG_CLOSE_KEY;
    private static final MethodHandle REG_DELETE_KEY_W;

    private static boolean available = false;

    static {
        SymbolLookup advapi32 = null;
        MethodHandle regCreateKeyExW = null;
        MethodHandle regSetValueExW = null;
        MethodHandle regCloseKey = null;
        MethodHandle regDeleteKeyW = null;

        try {
            advapi32 = SymbolLookup.libraryLookup("advapi32.dll", Arena.global());

            // LSTATUS RegCreateKeyExW(
            //   HKEY    hKey,
            //   LPCWSTR lpSubKey,
            //   DWORD   Reserved,
            //   LPWSTR  lpClass,
            //   DWORD   dwOptions,
            //   REGSAM  samDesired,
            //   LPSECURITY_ATTRIBUTES lpSecurityAttributes,
            //   PHKEY   phkResult,
            //   LPDWORD lpdwDisposition
            // )
            regCreateKeyExW = LINKER.downcallHandle(
                advapi32.find("RegCreateKeyExW").orElseThrow(),
                FunctionDescriptor.of(
                    ValueLayout.JAVA_INT,       // return: LSTATUS
                    ValueLayout.JAVA_LONG,      // hKey (use JAVA_LONG for HKEY handles)
                    ValueLayout.ADDRESS,        // lpSubKey
                    ValueLayout.JAVA_INT,       // Reserved
                    ValueLayout.ADDRESS,        // lpClass
                    ValueLayout.JAVA_INT,       // dwOptions
                    ValueLayout.JAVA_INT,       // samDesired
                    ValueLayout.ADDRESS,        // lpSecurityAttributes
                    ValueLayout.ADDRESS,        // phkResult
                    ValueLayout.ADDRESS         // lpdwDisposition
                )
            );

            // LSTATUS RegSetValueExW(
            //   HKEY       hKey,
            //   LPCWSTR    lpValueName,
            //   DWORD      Reserved,
            //   DWORD      dwType,
            //   const BYTE *lpData,
            //   DWORD      cbData
            // )
            regSetValueExW = LINKER.downcallHandle(
                advapi32.find("RegSetValueExW").orElseThrow(),
                FunctionDescriptor.of(
                    ValueLayout.JAVA_INT,       // return: LSTATUS
                    ValueLayout.JAVA_LONG,      // hKey
                    ValueLayout.ADDRESS,        // lpValueName
                    ValueLayout.JAVA_INT,       // Reserved
                    ValueLayout.JAVA_INT,       // dwType
                    ValueLayout.ADDRESS,        // lpData
                    ValueLayout.JAVA_INT        // cbData
                )
            );

            // LSTATUS RegCloseKey(HKEY hKey)
            regCloseKey = LINKER.downcallHandle(
                advapi32.find("RegCloseKey").orElseThrow(),
                FunctionDescriptor.of(
                    ValueLayout.JAVA_INT,       // return: LSTATUS
                    ValueLayout.JAVA_LONG       // hKey
                )
            );

            // LSTATUS RegDeleteKeyW(HKEY hKey, LPCWSTR lpSubKey)
            regDeleteKeyW = LINKER.downcallHandle(
                advapi32.find("RegDeleteKeyW").orElseThrow(),
                FunctionDescriptor.of(
                    ValueLayout.JAVA_INT,       // return: LSTATUS
                    ValueLayout.JAVA_LONG,      // hKey
                    ValueLayout.ADDRESS         // lpSubKey
                )
            );

            available = true;
        } catch (Exception e) {
            // Not on Windows or advapi32 not available
        }

        ADVAPI32 = advapi32;
        REG_CREATE_KEY_EX_W = regCreateKeyExW;
        REG_SET_VALUE_EX_W = regSetValueExW;
        REG_CLOSE_KEY = regCloseKey;
        REG_DELETE_KEY_W = regDeleteKeyW;
    }

    private WindowsRegistryHelper() {}

    /**
     * Returns true if registry operations are available (running on Windows).
     */
    public static boolean isAvailable() {
        return available;
    }

    /**
     * Registers a URL protocol handler in HKEY_CURRENT_USER (no elevation required).
     *
     * @param scheme  The URL scheme (e.g., "myapp")
     * @param exePath Full path to the application executable
     * @param appName Display name for the protocol
     * @return true if registration succeeded
     */
    public static boolean registerUrlProtocol(String scheme, String exePath, String appName) {
        if (!available) {
            return false;
        }

        try (Arena arena = Arena.ofConfined()) {
            // Create key: HKEY_CURRENT_USER\Software\Classes\{scheme}
            String baseKey = "Software\\Classes\\" + scheme;
            long hKey = createKey(arena, HKEY_CURRENT_USER, baseKey);
            if (hKey == 0) return false;

            try {
                // Set default value: "URL:{appName} Protocol"
                setStringValue(arena, hKey, null, "URL:" + appName + " Protocol");

                // Set "URL Protocol" = "" (empty string marks this as a URL handler)
                setStringValue(arena, hKey, "URL Protocol", "");

                // Create shell\open\command subkey
                long shellKey = createKey(arena, hKey, "shell");
                if (shellKey != 0) {
                    try {
                        long openKey = createKey(arena, shellKey, "open");
                        if (openKey != 0) {
                            try {
                                long commandKey = createKey(arena, openKey, "command");
                                if (commandKey != 0) {
                                    try {
                                        // Set command: "C:\path\to\app.exe" "%1"
                                        setStringValue(arena, commandKey, null, "\"" + exePath + "\" \"%1\"");
                                    } finally {
                                        closeKey(commandKey);
                                    }
                                }
                            } finally {
                                closeKey(openKey);
                            }
                        }
                    } finally {
                        closeKey(shellKey);
                    }
                }

                // Create DefaultIcon subkey (optional, uses exe icon)
                long iconKey = createKey(arena, hKey, "DefaultIcon");
                if (iconKey != 0) {
                    try {
                        setStringValue(arena, iconKey, null, exePath + ",0");
                    } finally {
                        closeKey(iconKey);
                    }
                }

                return true;
            } finally {
                closeKey(hKey);
            }
        } catch (Throwable t) {
            System.err.println("[WindowsRegistryHelper] Failed to register protocol: " + t.getMessage());
            return false;
        }
    }

    /**
     * Unregisters a URL protocol handler from HKEY_CURRENT_USER.
     *
     * @param scheme The URL scheme to unregister
     * @return true if unregistration succeeded
     */
    public static boolean unregisterUrlProtocol(String scheme) {
        if (!available) {
            return false;
        }

        try (Arena arena = Arena.ofConfined()) {
            // Delete keys in reverse order (deepest first)
            String baseKey = "Software\\Classes\\" + scheme;
            deleteKeyRecursive(arena, HKEY_CURRENT_USER, baseKey + "\\shell\\open\\command");
            deleteKeyRecursive(arena, HKEY_CURRENT_USER, baseKey + "\\shell\\open");
            deleteKeyRecursive(arena, HKEY_CURRENT_USER, baseKey + "\\shell");
            deleteKeyRecursive(arena, HKEY_CURRENT_USER, baseKey + "\\DefaultIcon");
            deleteKeyRecursive(arena, HKEY_CURRENT_USER, baseKey);
            return true;
        } catch (Throwable t) {
            System.err.println("[WindowsRegistryHelper] Failed to unregister protocol: " + t.getMessage());
            return false;
        }
    }

    /**
     * Generates a .reg file for importing URL protocol registrations.
     * This file can be distributed with the application for manual or
     * automated registry import.
     *
     * @param schemes List of URL schemes to register
     * @param exePath Path to the application executable (use placeholder for installer)
     * @param appName Display name for the protocols
     * @param outputPath Where to write the .reg file
     */
    public static void generateRegFile(List<String> schemes, String exePath, String appName, Path outputPath) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("Windows Registry Editor Version 5.00\n\n");
        sb.append("; URL Protocol registration for ").append(appName).append("\n");
        sb.append("; Generated by Krema bundler\n\n");

        // Escape backslashes for .reg file format
        String escapedExePath = exePath.replace("\\", "\\\\");

        for (String scheme : schemes) {
            sb.append("; Protocol: ").append(scheme).append("://\n");
            sb.append("[HKEY_CURRENT_USER\\Software\\Classes\\").append(scheme).append("]\n");
            sb.append("@=\"URL:").append(appName).append(" Protocol\"\n");
            sb.append("\"URL Protocol\"=\"\"\n\n");

            sb.append("[HKEY_CURRENT_USER\\Software\\Classes\\").append(scheme).append("\\DefaultIcon]\n");
            sb.append("@=\"").append(escapedExePath).append(",0\"\n\n");

            sb.append("[HKEY_CURRENT_USER\\Software\\Classes\\").append(scheme).append("\\shell]\n\n");

            sb.append("[HKEY_CURRENT_USER\\Software\\Classes\\").append(scheme).append("\\shell\\open]\n\n");

            sb.append("[HKEY_CURRENT_USER\\Software\\Classes\\").append(scheme).append("\\shell\\open\\command]\n");
            sb.append("@=\"\\\"").append(escapedExePath).append("\\\" \\\"%1\\\"\"\n\n");
        }

        Files.writeString(outputPath, sb.toString(), StandardCharsets.UTF_8);
    }

    /**
     * Generates a PowerShell script for registering URL protocols.
     * This script can be run during installation or by the user.
     *
     * @param schemes   List of URL schemes to register
     * @param exePath   Path to the application executable
     * @param appName   Display name for the protocols
     * @param outputPath Where to write the .ps1 file
     */
    public static void generatePowerShellScript(List<String> schemes, String exePath, String appName, Path outputPath) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("# URL Protocol registration for ").append(appName).append("\n");
        sb.append("# Generated by Krema bundler\n");
        sb.append("# Run this script to register custom URL protocols\n\n");

        sb.append("$exePath = \"").append(exePath.replace("\\", "\\\\")).append("\"\n\n");

        for (String scheme : schemes) {
            sb.append("# Register ").append(scheme).append(":// protocol\n");
            sb.append("$scheme = \"").append(scheme).append("\"\n");
            sb.append("$basePath = \"HKCU:\\Software\\Classes\\$scheme\"\n\n");

            sb.append("# Create base key\n");
            sb.append("New-Item -Path $basePath -Force | Out-Null\n");
            sb.append("Set-ItemProperty -Path $basePath -Name \"(Default)\" -Value \"URL:").append(appName).append(" Protocol\"\n");
            sb.append("Set-ItemProperty -Path $basePath -Name \"URL Protocol\" -Value \"\"\n\n");

            sb.append("# Create DefaultIcon key\n");
            sb.append("New-Item -Path \"$basePath\\DefaultIcon\" -Force | Out-Null\n");
            sb.append("Set-ItemProperty -Path \"$basePath\\DefaultIcon\" -Name \"(Default)\" -Value \"$exePath,0\"\n\n");

            sb.append("# Create shell\\open\\command key\n");
            sb.append("New-Item -Path \"$basePath\\shell\\open\\command\" -Force | Out-Null\n");
            sb.append("Set-ItemProperty -Path \"$basePath\\shell\\open\\command\" -Name \"(Default)\" -Value \"`\"$exePath`\" `\"%1`\"\"\n\n");

            sb.append("Write-Host \"Registered $scheme:// protocol handler\"\n\n");
        }

        sb.append("Write-Host \"Protocol registration complete.\"\n");

        Files.writeString(outputPath, sb.toString(), StandardCharsets.UTF_8);
    }

    /**
     * Generates an unregistration PowerShell script.
     *
     * @param schemes    List of URL schemes to unregister
     * @param outputPath Where to write the .ps1 file
     */
    public static void generateUnregisterScript(List<String> schemes, Path outputPath) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("# URL Protocol unregistration script\n");
        sb.append("# Generated by Krema bundler\n\n");

        for (String scheme : schemes) {
            sb.append("# Unregister ").append(scheme).append(":// protocol\n");
            sb.append("$basePath = \"HKCU:\\Software\\Classes\\").append(scheme).append("\"\n");
            sb.append("if (Test-Path $basePath) {\n");
            sb.append("    Remove-Item -Path $basePath -Recurse -Force\n");
            sb.append("    Write-Host \"Unregistered ").append(scheme).append(":// protocol handler\"\n");
            sb.append("} else {\n");
            sb.append("    Write-Host \"Protocol ").append(scheme).append(":// was not registered\"\n");
            sb.append("}\n\n");
        }

        Files.writeString(outputPath, sb.toString(), StandardCharsets.UTF_8);
    }

    // --- Private helper methods ---

    private static long createKey(Arena arena, long parentKey, String subKey) throws Throwable {
        MemorySegment subKeyPtr = allocateWideString(arena, subKey);
        MemorySegment hKeyOut = arena.allocate(ValueLayout.JAVA_LONG);
        MemorySegment disposition = arena.allocate(ValueLayout.JAVA_INT);

        int result = (int) REG_CREATE_KEY_EX_W.invokeExact(
            parentKey,
            subKeyPtr,
            0,                          // Reserved
            MemorySegment.NULL,         // lpClass
            0,                          // dwOptions (REG_OPTION_NON_VOLATILE)
            KEY_ALL_ACCESS,             // samDesired
            MemorySegment.NULL,         // lpSecurityAttributes
            hKeyOut,
            disposition
        );

        if (result != ERROR_SUCCESS) {
            return 0;
        }

        return hKeyOut.get(ValueLayout.JAVA_LONG, 0);
    }

    private static void setStringValue(Arena arena, long hKey, String valueName, String data) throws Throwable {
        MemorySegment valueNamePtr = valueName != null ? allocateWideString(arena, valueName) : MemorySegment.NULL;
        MemorySegment dataPtr = allocateWideString(arena, data);
        // Size includes null terminator, in bytes (UTF-16 = 2 bytes per char)
        int dataSize = (data.length() + 1) * 2;

        int result = (int) REG_SET_VALUE_EX_W.invokeExact(
            hKey,
            valueNamePtr,
            0,              // Reserved
            REG_SZ,         // dwType
            dataPtr,
            dataSize
        );

        if (result != ERROR_SUCCESS) {
            throw new RuntimeException("RegSetValueExW failed with error: " + result);
        }
    }

    private static void closeKey(long hKey) {
        if (hKey != 0) {
            try {
                REG_CLOSE_KEY.invokeExact(hKey);
            } catch (Throwable ignored) {
            }
        }
    }

    private static void deleteKeyRecursive(Arena arena, long parentKey, String subKey) throws Throwable {
        MemorySegment subKeyPtr = allocateWideString(arena, subKey);
        // Note: RegDeleteKeyW only deletes empty keys, so we delete from deepest first
        REG_DELETE_KEY_W.invokeExact(parentKey, subKeyPtr);
    }

    /**
     * Allocates a UTF-16LE (wide) null-terminated string in the given arena.
     */
    private static MemorySegment allocateWideString(Arena arena, String str) {
        byte[] bytes = (str + "\0").getBytes(StandardCharsets.UTF_16LE);
        MemorySegment segment = arena.allocate(bytes.length);
        segment.copyFrom(MemorySegment.ofArray(bytes));
        return segment;
    }
}
