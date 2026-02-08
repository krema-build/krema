package build.krema.benchmark;

import build.krema.core.Krema;

/**
 * Measures memory usage after the app has been idle for 3 seconds.
 * Prints a JSON memory snapshot to stdout and exits.
 */
public class MemoryBenchmark {

    public static void main(String[] args) {
        Krema.app()
            .title("Benchmark")
            .size(400, 300)
            .noBuiltinPlugins()
            .noBuiltinApis()
            .html("<html><body><p>Benchmark</p></body></html>")
            .events(emitter -> {
                emitter.on("app:ready", event -> {
                    Thread.startVirtualThread(() -> {
                        try {
                            Thread.sleep(3000);
                        } catch (InterruptedException ignored) {}

                        MemorySnapshot snapshot = MemorySnapshot.capture();
                        System.out.println("BENCHMARK_MEMORY=" + snapshot.toJson());
                        System.exit(0);
                    });
                });
            })
            .run();
    }
}
