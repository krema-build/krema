package build.krema.benchmark;

import java.io.BufferedReader;
import java.io.InputStreamReader;

/**
 * Captures a snapshot of heap and RSS memory usage.
 */
public record MemorySnapshot(long heapUsedBytes, long heapMaxBytes, long rssKb) {

    /**
     * Captures current memory usage: heap via Runtime, RSS via OS process query.
     * Runs a GC hint before measuring heap to get a more stable reading.
     */
    public static MemorySnapshot capture() {
        System.gc();
        try { Thread.sleep(100); } catch (InterruptedException ignored) {}

        Runtime rt = Runtime.getRuntime();
        long heapUsed = rt.totalMemory() - rt.freeMemory();
        long heapMax = rt.maxMemory();
        long rss = captureRssKb();

        return new MemorySnapshot(heapUsed, heapMax, rss);
    }

    private static long captureRssKb() {
        String os = System.getProperty("os.name", "").toLowerCase();
        try {
            ProcessBuilder pb;
            if (os.contains("win")) {
                // tasklist outputs memory in K on Windows
                pb = new ProcessBuilder("tasklist", "/FI",
                        "PID eq " + ProcessHandle.current().pid(), "/FO", "CSV", "/NH");
            } else {
                // macOS and Linux: ps -o rss= -p <pid>
                pb = new ProcessBuilder("ps", "-o", "rss=", "-p",
                        String.valueOf(ProcessHandle.current().pid()));
            }
            Process process = pb.start();
            try (var reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line = reader.readLine();
                if (line == null) return -1;

                if (os.contains("win")) {
                    // CSV format: "image","pid","session","session#","mem usage"
                    String[] parts = line.split(",");
                    if (parts.length >= 5) {
                        String mem = parts[4].replace("\"", "").replace(" K", "")
                                .replace(",", "").trim();
                        return Long.parseLong(mem);
                    }
                    return -1;
                } else {
                    return Long.parseLong(line.trim());
                }
            }
        } catch (Exception e) {
            System.err.println("[Benchmark] Failed to capture RSS: " + e.getMessage());
            return -1;
        }
    }

    public double heapUsedMb() {
        return heapUsedBytes / (1024.0 * 1024.0);
    }

    public double heapMaxMb() {
        return heapMaxBytes / (1024.0 * 1024.0);
    }

    public String toJson() {
        return """
                {
                  "heapUsedBytes": %d,
                  "heapMaxBytes": %d,
                  "heapUsedMb": %.1f,
                  "heapMaxMb": %.1f,
                  "rssKb": %d
                }""".formatted(heapUsedBytes, heapMaxBytes, heapUsedMb(), heapMaxMb(), rssKb);
    }
}
