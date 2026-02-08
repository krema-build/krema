#!/usr/bin/env bash
set -euo pipefail

# Krema Benchmark Orchestrator
# Builds the project, runs benchmarks, and assembles results.

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
BENCHMARK_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
KREMA_ROOT="$(cd "$BENCHMARK_DIR/.." && pwd)"
RESULTS_DIR="$BENCHMARK_DIR/results"
ITERATIONS="${1:-10}"

# --- Platform detection ---

OS="$(uname -s)"
ARCH="$(uname -m)"
JVM_FLAGS=("--enable-native-access=ALL-UNNAMED")

case "$OS" in
    Darwin)
        JVM_FLAGS+=("-XstartOnFirstThread")
        ;;
esac

JAVA_LIB_PATH="$KREMA_ROOT/krema/krema-core/lib"
JVM_FLAGS+=("-Djava.library.path=$JAVA_LIB_PATH")

echo "================================================"
echo "  Krema Performance Benchmarks"
echo "================================================"
echo "  Platform:   $OS $ARCH"
echo "  Java:       $(java -version 2>&1 | head -1)"
echo "  Iterations: $ITERATIONS"
echo "  Output:     $RESULTS_DIR/latest.json"
echo "================================================"
echo

# --- Build ---

echo "[1/5] Building krema-core..."
mvn clean install -f "$KREMA_ROOT/krema/pom.xml" -DskipTests -q

echo "[2/5] Building krema-benchmark..."
mvn clean package -f "$BENCHMARK_DIR/pom.xml" -DskipTests -q

# Resolve classpath once
CLASSPATH=$(mvn -f "$BENCHMARK_DIR/pom.xml" dependency:build-classpath -Dmdep.outputFile=/dev/stdout -q 2>/dev/null || true)
BENCHMARK_JAR=$(ls "$BENCHMARK_DIR/target/krema-benchmark"*.jar 2>/dev/null | head -1)
if [ -n "$BENCHMARK_JAR" ]; then
    CLASSPATH="$BENCHMARK_JAR:$CLASSPATH"
fi

# --- Startup benchmark (N iterations) ---

echo "[3/5] Running startup benchmark ($ITERATIONS iterations)..."
STARTUP_TIMES=()

for i in $(seq 1 "$ITERATIONS"); do
    # Measure wall-clock time as well
    START_WALL=$(python3 -c "import time; print(time.time())")

    OUTPUT=$(java "${JVM_FLAGS[@]}" -classpath "$CLASSPATH" build.krema.benchmark.StartupBenchmark 2>/dev/null || true)

    END_WALL=$(python3 -c "import time; print(time.time())")
    WALL_MS=$(python3 -c "print(round(($END_WALL - $START_WALL) * 1000, 2))")

    # Extract the app-internal startup time
    APP_MS=$(echo "$OUTPUT" | grep "BENCHMARK_STARTUP_MS=" | sed 's/BENCHMARK_STARTUP_MS=//' || echo "")

    if [ -n "$APP_MS" ]; then
        STARTUP_TIMES+=("$APP_MS")
        printf "  Iteration %2d: %s ms (wall: %s ms)\n" "$i" "$APP_MS" "$WALL_MS"
    else
        echo "  Iteration $i: FAILED"
        echo "  Output: $OUTPUT"
    fi
done

if [ ${#STARTUP_TIMES[@]} -eq 0 ]; then
    echo "ERROR: No successful startup measurements. Aborting."
    exit 1
fi

# --- Memory benchmark ---

echo "[4/5] Running memory benchmark..."
MEMORY_OUTPUT=$(java "${JVM_FLAGS[@]}" -classpath "$CLASSPATH" build.krema.benchmark.MemoryBenchmark 2>/dev/null || true)
MEMORY_JSON=$(echo "$MEMORY_OUTPUT" | grep "BENCHMARK_MEMORY=" | sed 's/BENCHMARK_MEMORY=//' || echo '{}')

if [ "$MEMORY_JSON" = "{}" ]; then
    echo "  WARNING: Memory benchmark failed"
    echo "  Output: $MEMORY_OUTPUT"
else
    HEAP_MB=$(echo "$MEMORY_JSON" | python3 -c "import sys,json; d=json.load(sys.stdin); print(f\"{d['heapUsedMb']:.1f}\")" 2>/dev/null || echo "?")
    RSS_KB=$(echo "$MEMORY_JSON" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d['rssKb'])" 2>/dev/null || echo "?")
    echo "  Heap used: ${HEAP_MB} MB, RSS: ${RSS_KB} KB"
fi

# --- Bundle size benchmark ---

echo "[5/5] Running bundle size benchmark..."
BUNDLE_OUTPUT=$(java -classpath "$CLASSPATH" build.krema.benchmark.BundleSizeBenchmark "$KREMA_ROOT" 2>/dev/null || true)
BUNDLE_JSON=$(echo "$BUNDLE_OUTPUT" | grep "BENCHMARK_BUNDLE=" | sed 's/BENCHMARK_BUNDLE=//' || echo '{}')

if [ "$BUNDLE_JSON" = "{}" ]; then
    echo "  WARNING: Bundle size benchmark failed"
    echo "  Output: $BUNDLE_OUTPUT"
else
    TOTAL_KB=$(echo "$BUNDLE_JSON" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d['totalKb'])" 2>/dev/null || echo "?")
    echo "  Total bundle: ${TOTAL_KB} KB"
fi

# --- Assemble results ---

echo
echo "Assembling results..."
mkdir -p "$RESULTS_DIR"

java -classpath "$CLASSPATH" build.krema.benchmark.BenchmarkResult \
    "$RESULTS_DIR/latest.json" \
    "$MEMORY_JSON" \
    "$BUNDLE_JSON" \
    "${STARTUP_TIMES[@]}"

# --- Summary ---

echo
echo "================================================"
echo "  Results Summary"
echo "================================================"

if [ -f "$RESULTS_DIR/latest.json" ]; then
    python3 -c "
import json, sys
with open('$RESULTS_DIR/latest.json') as f:
    d = json.load(f)
s = d.get('startup', {})
m = d.get('memory', {})
b = d.get('bundleSize', {})
p = d.get('platform', {})
print(f\"  Platform:      {p.get('os', '?')}\")
print(f\"  Java:          {p.get('java', '?')}\")
print(f\"  CPU Cores:     {p.get('cpuCores', '?')}\")
print()
print(f\"  Startup (app:ready):\")
print(f\"    Median:      {s.get('medianMs', '?')} ms\")
print(f\"    Min:         {s.get('minMs', '?')} ms\")
print(f\"    Max:         {s.get('maxMs', '?')} ms\")
print(f\"    P95:         {s.get('p95Ms', '?')} ms\")
print(f\"    Iterations:  {s.get('iterations', '?')}\")
print()
print(f\"  Memory (3s idle):\")
print(f\"    Heap used:   {m.get('heapUsedMb', '?')} MB\")
print(f\"    RSS:         {m.get('rssKb', '?')} KB ({(m.get('rssKb', 0) / 1024):.1f} MB)\")
print()
print(f\"  Bundle Size:\")
print(f\"    Core JAR:    {b.get('coreJarKb', '?')} KB\")
print(f\"    Native libs: {b.get('nativeLibKb', '?')} KB\")
print(f\"    Dependencies:{b.get('depsKb', '?')} KB\")
print(f\"    Total:       {b.get('totalKb', '?')} KB ({(b.get('totalKb', 0) / 1024):.1f} MB)\")
" 2>/dev/null || echo "  (install python3 for pretty summary)"
fi

echo
echo "Full results: $RESULTS_DIR/latest.json"
echo "================================================"
