package build.krema.benchmark;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Measures file sizes of the Krema distribution: core JAR, native libraries, dependencies.
 * No Krema window needed — standalone main that takes the krema root path as argument.
 */
public class BundleSizeBenchmark {

    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: BundleSizeBenchmark <krema-root-path>");
            System.exit(1);
        }

        Path root = Path.of(args[0]);
        Path coreTarget = root.resolve("krema/krema-core/target");
        Path libDir = root.resolve("krema/krema-core/lib");

        long coreJarBytes = findJarSize(coreTarget, "krema-core");
        long nativeLibBytes = dirSize(libDir);
        long depsBytes = depJarsSize(coreTarget);

        long totalBytes = coreJarBytes + nativeLibBytes + depsBytes;

        String json = """
                {
                  "coreJarKb": %d,
                  "nativeLibKb": %d,
                  "depsKb": %d,
                  "totalKb": %d,
                  "coreJarBytes": %d,
                  "nativeLibBytes": %d,
                  "depsBytes": %d,
                  "totalBytes": %d
                }""".formatted(
                    coreJarBytes / 1024, nativeLibBytes / 1024,
                    depsBytes / 1024, totalBytes / 1024,
                    coreJarBytes, nativeLibBytes, depsBytes, totalBytes);

        System.out.println("BENCHMARK_BUNDLE=" + json);
    }

    private static long findJarSize(Path targetDir, String artifactPrefix) {
        if (!Files.isDirectory(targetDir)) return 0;
        try (var stream = Files.list(targetDir)) {
            return stream
                .filter(p -> p.getFileName().toString().startsWith(artifactPrefix))
                .filter(p -> p.toString().endsWith(".jar"))
                .filter(p -> !p.toString().contains("-sources") && !p.toString().contains("-javadoc"))
                .mapToLong(p -> {
                    try { return Files.size(p); }
                    catch (IOException e) { return 0; }
                })
                .max()
                .orElse(0);
        } catch (IOException e) {
            return 0;
        }
    }

    private static long dirSize(Path dir) {
        if (!Files.isDirectory(dir)) return 0;
        try (var stream = Files.walk(dir)) {
            return stream
                .filter(Files::isRegularFile)
                .mapToLong(p -> {
                    try { return Files.size(p); }
                    catch (IOException e) { return 0; }
                })
                .sum();
        } catch (IOException e) {
            return 0;
        }
    }

    private static long depJarsSize(Path targetDir) {
        Path depsDir = targetDir.resolve("dependency");
        if (!Files.isDirectory(depsDir)) {
            // Fallback: look in Maven local repo via classpath
            return 0;
        }
        try (var stream = Files.list(depsDir)) {
            return stream
                .filter(p -> p.toString().endsWith(".jar"))
                .mapToLong(p -> {
                    try { return Files.size(p); }
                    catch (IOException e) { return 0; }
                })
                .sum();
        } catch (IOException e) {
            return 0;
        }
    }
}
