package build.krema.benchmark;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Arrays;

/**
 * Assembles partial benchmark results into a final JSON report.
 * Takes startup times, memory JSON, and bundle JSON as arguments.
 *
 * Usage: BenchmarkResult <output-path> <memory-json> <bundle-json> <time1> <time2> ...
 */
public class BenchmarkResult {

    public static void main(String[] args) {
        if (args.length < 4) {
            System.err.println("Usage: BenchmarkResult <output-path> <memory-json> <bundle-json> <time1> [time2] ...");
            System.exit(1);
        }

        Path outputPath = Path.of(args[0]);
        String memoryJson = args[1];
        String bundleJson = args[2];

        double[] times = new double[args.length - 3];
        for (int i = 3; i < args.length; i++) {
            times[i - 3] = Double.parseDouble(args[i]);
        }
        Arrays.sort(times);

        String platformJson = buildPlatformJson();
        String startupJson = buildStartupJson(times);

        String result = """
                {
                  "timestamp": "%s",
                  "platform": %s,
                  "startup": %s,
                  "memory": %s,
                  "bundleSize": %s
                }
                """.formatted(
                    Instant.now().toString(),
                    indent(platformJson, 2),
                    indent(startupJson, 2),
                    indent(memoryJson, 2),
                    indent(bundleJson, 2));

        try {
            Files.createDirectories(outputPath.getParent());
            Files.writeString(outputPath, result);
            System.out.println("Results written to " + outputPath);
        } catch (IOException e) {
            System.err.println("Failed to write results: " + e.getMessage());
            System.exit(1);
        }
    }

    private static String buildPlatformJson() {
        String os = System.getProperty("os.name", "unknown");
        String arch = System.getProperty("os.arch", "unknown");
        String javaVersion = System.getProperty("java.version", "unknown");
        int cpuCores = Runtime.getRuntime().availableProcessors();
        long ramMb = Runtime.getRuntime().maxMemory() / (1024 * 1024);
        long totalRamMb = getTotalSystemRamMb();

        return """
                {
                  "os": "%s-%s",
                  "java": "%s",
                  "cpuCores": %d,
                  "jvmMaxHeapMb": %d,
                  "systemRamMb": %d
                }""".formatted(
                    os.toLowerCase().replace(" ", ""), arch,
                    javaVersion, cpuCores, ramMb, totalRamMb);
    }

    private static long getTotalSystemRamMb() {
        try {
            var bean = java.lang.management.ManagementFactory.getOperatingSystemMXBean();
            if (bean instanceof com.sun.management.OperatingSystemMXBean osMx) {
                return osMx.getTotalMemorySize() / (1024 * 1024);
            }
        } catch (Exception ignored) {}
        return -1;
    }

    private static String buildStartupJson(double[] sortedTimes) {
        int n = sortedTimes.length;
        double min = sortedTimes[0];
        double max = sortedTimes[n - 1];
        double median = n % 2 == 0
                ? (sortedTimes[n / 2 - 1] + sortedTimes[n / 2]) / 2.0
                : sortedTimes[n / 2];
        int p95Index = (int) Math.ceil(n * 0.95) - 1;
        double p95 = sortedTimes[Math.min(p95Index, n - 1)];

        StringBuilder timesArray = new StringBuilder("[");
        for (int i = 0; i < n; i++) {
            if (i > 0) timesArray.append(", ");
            timesArray.append(String.format("%.2f", sortedTimes[i]));
        }
        timesArray.append("]");

        return """
                {
                  "iterations": %d,
                  "timesMs": %s,
                  "medianMs": %.2f,
                  "minMs": %.2f,
                  "maxMs": %.2f,
                  "p95Ms": %.2f
                }""".formatted(n, timesArray, median, min, max, p95);
    }

    private static String indent(String json, int baseIndent) {
        String prefix = " ".repeat(baseIndent);
        String[] lines = json.split("\n");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            if (i == 0) {
                sb.append(lines[i]);
            } else {
                sb.append("\n").append(prefix).append(lines[i]);
            }
        }
        return sb.toString();
    }
}
