package build.krema.benchmark;

import build.krema.core.Krema;

/**
 * Measures startup time from main() entry to app:ready event.
 * Prints a single JSON line to stdout and exits.
 */
public class StartupBenchmark {

    private static final long MAIN_ENTRY_NS = System.nanoTime();

    public static void main(String[] args) {
        Krema.app()
            .title("Benchmark")
            .size(400, 300)
            .noBuiltinPlugins()
            .noBuiltinApis()
            .html("<html><body><p>Benchmark</p></body></html>")
            .events(emitter -> {
                emitter.on("app:ready", event -> {
                    long elapsedNs = System.nanoTime() - MAIN_ENTRY_NS;
                    double elapsedMs = elapsedNs / 1_000_000.0;
                    System.out.println("BENCHMARK_STARTUP_MS=" + String.format("%.2f", elapsedMs));
                    System.exit(0);
                });
            })
            .run();
    }
}
