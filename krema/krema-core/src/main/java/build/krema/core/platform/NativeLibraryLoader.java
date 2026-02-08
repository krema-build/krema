package build.krema.core.platform;

import java.io.IOException;
import java.io.InputStream;
import java.lang.foreign.Arena;
import java.lang.foreign.SymbolLookup;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Loads native libraries in a cross-platform manner.
 * Supports loading from: system paths, java.library.path, bundled JAR resources.
 * Uses Java 25 FFM API.
 */
public final class NativeLibraryLoader {

    private static final Map<String, SymbolLookup> loadedLibraries = new ConcurrentHashMap<>();

    private NativeLibraryLoader() {}

    /**
     * Loads a native library by name and returns its symbol lookup.
     * Caches loaded libraries for reuse.
     *
     * @param libraryName the base name of the library (e.g., "webview")
     * @return symbol lookup for the library
     * @throws LibraryLoadException if the library cannot be found or loaded
     */
    public static SymbolLookup load(String libraryName) {
        return loadedLibraries.computeIfAbsent(libraryName, NativeLibraryLoader::doLoad);
    }

    private static SymbolLookup doLoad(String libraryName) {
        Platform platform = Platform.current();
        String fileName = platform.formatLibraryName(libraryName);
        System.out.println("[NativeLibraryLoader] Loading library: " + libraryName + " (" + fileName + ")");
        System.out.println("[NativeLibraryLoader] Platform: " + platform);
        System.out.println("[NativeLibraryLoader] java.library.path: " + System.getProperty("java.library.path", ""));
        System.out.flush();

        // 1. Try java.library.path
        Path javaLibPath = findInJavaLibraryPath(fileName, platform);
        if (javaLibPath != null) {
            System.out.println("[NativeLibraryLoader] Found in java.library.path: " + javaLibPath);
            System.out.flush();
            return loadFromPath(javaLibPath);
        }

        // 2. Try bundled in JAR
        Path extractedPath = extractFromJar(libraryName, platform);
        if (extractedPath != null) {
            return loadFromPath(extractedPath);
        }

        // 3. Try next to the executable (native image builds place the library alongside the binary)
        Path executableSibling = findNextToExecutable(fileName);
        if (executableSibling != null) {
            return loadFromPath(executableSibling);
        }

        // 4. Try common system paths
        Path systemPath = findInSystemPaths(fileName, platform);
        if (systemPath != null) {
            return loadFromPath(systemPath);
        }

        throw new LibraryLoadException(
            "Native library not found: " + libraryName +
            ". Searched: java.library.path, JAR resources, system paths"
        );
    }

    private static Path findInJavaLibraryPath(String fileName, Platform platform) {
        String libraryPath = System.getProperty("java.library.path", "");
        for (String entry : libraryPath.split(System.getProperty("path.separator"))) {
            if (entry.isEmpty()) continue;

            Path entryPath = Path.of(entry);

            // Check if entry is a file that matches the library name
            if (Files.isRegularFile(entryPath) && entryPath.getFileName().toString().equals(fileName)) {
                return entryPath;
            }

            // Check if entry is a directory containing the library
            if (Files.isDirectory(entryPath)) {
                Path path = entryPath.resolve(fileName);
                if (Files.exists(path)) {
                    return path;
                }

                // Also check platform/arch subdirectory (e.g., lib/macos/aarch64/libwebview.dylib)
                Path archPath = entryPath.resolve(
                    platform.name().toLowerCase() + "/" + PlatformDetector.getArch() + "/" + fileName);
                if (Files.exists(archPath)) {
                    return archPath;
                }
            }
        }
        return null;
    }

    private static Path extractFromJar(String libraryName, Platform platform) {
        String arch = PlatformDetector.getArch();
        String resourcePath = "/native/" + platform.name().toLowerCase() + "/" +
                              arch + "/" + platform.formatLibraryName(libraryName);

        try (InputStream is = NativeLibraryLoader.class.getResourceAsStream(resourcePath)) {
            if (is == null) {
                return null;
            }

            Path tempDir = Files.createTempDirectory("krema-native-");
            Path libPath = tempDir.resolve(platform.formatLibraryName(libraryName));
            Files.copy(is, libPath, StandardCopyOption.REPLACE_EXISTING);

            // Schedule cleanup on JVM exit
            libPath.toFile().deleteOnExit();
            tempDir.toFile().deleteOnExit();

            return libPath;
        } catch (IOException e) {
            return null;
        }
    }

    private static Path findNextToExecutable(String fileName) {
        try {
            Path execPath = ProcessHandle.current().info().command()
                .map(Path::of)
                .orElse(null);
            if (execPath != null) {
                Path sibling = execPath.getParent().resolve(fileName);
                if (Files.exists(sibling)) {
                    return sibling;
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private static Path findInSystemPaths(String fileName, Platform platform) {
        String[] searchPaths = switch (platform) {
            case MACOS -> new String[] {
                "/usr/local/lib",
                "/opt/homebrew/lib",
                "/usr/lib"
            };
            case LINUX -> new String[] {
                "/usr/local/lib",
                "/usr/lib",
                "/usr/lib64",
                "/lib",
                "/lib64"
            };
            case WINDOWS -> new String[] {
                System.getenv("SYSTEMROOT") + "\\System32",
                System.getenv("SYSTEMROOT") + "\\SysWOW64"
            };
            case UNKNOWN -> new String[0];
        };

        String arch = PlatformDetector.getArch();
        for (String dir : searchPaths) {
            if (dir != null) {
                Path path = Path.of(dir, fileName);
                if (Files.exists(path)) {
                    return path;
                }

                // Also check arch subdirectory (e.g., /usr/lib/aarch64-linux-gnu/)
                Path archPath = Path.of(dir, arch, fileName);
                if (Files.exists(archPath)) {
                    return archPath;
                }
            }
        }
        return null;
    }

    private static SymbolLookup loadFromPath(Path path) {
        try {
            System.out.println("[NativeLibraryLoader] Loading from path: " + path);
            System.out.flush();
            SymbolLookup lookup = SymbolLookup.libraryLookup(path, Arena.global());
            System.out.println("[NativeLibraryLoader] Library loaded successfully");
            System.out.flush();
            return lookup;
        } catch (IllegalArgumentException e) {
            throw new LibraryLoadException("Failed to load library: " + path, e);
        }
    }

    /**
     * Exception thrown when a native library cannot be loaded.
     */
    public static class LibraryLoadException extends RuntimeException {
        public LibraryLoadException(String message) {
            super(message);
        }

        public LibraryLoadException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
