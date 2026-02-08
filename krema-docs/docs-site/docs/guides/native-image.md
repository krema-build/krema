---
sidebar_position: 4
title: Native Image
description: Compile your Krema app to a native binary with GraalVM
---

# Native Image

Compile your Krema application to a standalone native binary using GraalVM. Native images start instantly, use less memory, and don't require a JVM at runtime.

## Why Native Image?

|  | JAR | Native Image |
|--|-----|-------------|
| **Startup time** | ~1-2s | ~50ms |
| **Memory usage** | ~100MB+ | ~30-50MB |
| **Binary size** | ~20MB + JVM | ~40-60MB standalone |
| **Requires JVM** | Yes (Java 25+) | No |
| **Distribution** | JAR + JVM | Single binary |

Native images are ideal for distribution to end users. During development, use the JAR-based workflow with `krema dev`.

## Prerequisites

### GraalVM JDK 25+

Krema requires GraalVM 25 or later, which includes `native-image` as a built-in tool (no need to install it separately with `gu`).

**macOS (Homebrew):**
```bash
brew install --cask graalvm-jdk@25
```

After installation, verify with:
```bash
/usr/libexec/java_home -v 25
```

**Linux (SDKMAN):**
```bash
sdk install java 25-graalce
```

**Linux (manual):**
```bash
# Download from https://www.graalvm.org/downloads/
tar -xzf graalvm-jdk-25_linux-x64.tar.gz
export GRAALVM_HOME=/path/to/graalvm-jdk-25
export PATH="$GRAALVM_HOME/bin:$PATH"
```

**Windows:**
```powershell
# Download from https://www.graalvm.org/downloads/
# Set environment variables
setx GRAALVM_HOME "C:\path\to\graalvm-jdk-25"
setx PATH "%GRAALVM_HOME%\bin;%PATH%"
```

### Verify Installation

```bash
native-image --version
```

You should see output like `GraalVM ... Java 25 ...`.

## Building a Native Image

### Configuration

Set the `main_class` in your `krema.toml` — this is required for native builds:

```toml
[build]
main_class = "com.example.myapp.Main"
```

### Build

```bash
krema build --native
```

This runs the full build pipeline:

1. Builds the frontend (`npm run build`)
2. Compiles Java sources (`mvn package`)
3. Copies web assets into classpath resources
4. Generates a GraalVM resource config for your frontend assets
5. Invokes `native-image` to produce a standalone binary
6. Copies the native webview library alongside the binary

The output is placed in `target/<app-name>`.

### Skip Steps

If you've already built the frontend or Java code:

```bash
# Skip frontend build
krema build --native --skip-frontend

# Skip Java compilation
krema build --native --skip-java
```

## How Krema Finds GraalVM

The CLI searches for `native-image` in this order:

1. `GRAALVM_HOME/bin/native-image`
2. `JAVA_HOME/bin/native-image`
3. **macOS**: `/usr/libexec/java_home -v 25+` to discover installed JDKs
4. **Linux**: `update-alternatives --query java` to find the active JDK
5. `native-image` on `PATH`

If none are found, the build fails with a clear error message.

:::tip
Setting `GRAALVM_HOME` is the most reliable approach. Add it to your shell profile:
```bash
export GRAALVM_HOME=/path/to/graalvm-jdk-25
```
:::

## GraalVM Configuration

Krema includes built-in GraalVM configuration that handles most cases automatically.

### Reflection Config

Krema ships a `reflect-config.json` that registers framework classes used via reflection at runtime (IPC serialization, event system, plugin classes). This is bundled in `krema-core` and picked up automatically.

If your app uses reflection for its own classes (e.g., custom serialization), add a `reflect-config.json` to your project:

```json
{
  "name": "com.example.MyClass",
  "allDeclaredConstructors": true,
  "allDeclaredMethods": true,
  "allDeclaredFields": true
}
```

Place it in `src/main/resources/META-INF/native-image/<group-id>/<artifact-id>/reflect-config.json`.

### Resource Config

Krema automatically generates a resource config that includes your frontend assets (the `out_dir` from `krema.toml`). You don't need to configure this manually.

### Native Image Properties

Krema's built-in `native-image.properties` enables:

- `--enable-native-access=ALL-UNNAMED` — required for FFM API calls to native webview libraries
- `--initialize-at-build-time` for `PlatformDetector` — OS detection at compile time

## Bundling Native Images

After building a native image, bundle it into a platform-specific installer:

```bash
# Build native image first
krema build --native

# Then bundle
krema bundle --type dmg       # macOS
krema bundle --type exe       # Windows
krema bundle --type appimage  # Linux
```

The bundler detects the native binary in `target/` and uses it instead of the JAR.

## Platform Notes

### macOS (Apple Silicon)

On Apple Silicon Macs, Krema automatically passes `-arch arm64` to the C compiler to prevent architecture mismatches when running under Rosetta. No manual configuration needed.

### macOS (Code Signing)

Native binaries need to be signed for distribution. See the [Code Signing](/docs/guides/code-signing) guide.

### Linux

Ensure the WebKitGTK runtime libraries are installed on the target machine:

```bash
# Ubuntu/Debian
sudo apt install libwebkit2gtk-4.1-0 libgtk-3-0
```

The `libwebview.so` library is copied next to your binary automatically.

### Windows

The WebView2 runtime (Edge-based) is included in Windows 10+. The `webview.dll` is copied next to your binary automatically.

## Troubleshooting

### "native-image tool not found"

Set `GRAALVM_HOME` to your GraalVM installation:
```bash
export GRAALVM_HOME=/path/to/graalvm-jdk-25
```

GraalVM 25+ includes `native-image` built-in — you no longer need `gu install native-image`.

### "main_class must be set"

Add your main class to `krema.toml`:
```toml
[build]
main_class = "com.example.myapp.Main"
```

### Build runs out of memory

Native image compilation is memory-intensive. Increase available heap:
```bash
export MAVEN_OPTS="-Xmx4g"
krema build --native
```

### "native library (libwebview) not found"

The native webview library must be available during the build. Ensure `krema-core` was built with native libraries:
```bash
ls krema-core/lib/
# Should contain libwebview.dylib (macOS), libwebview.so (Linux), or webview.dll (Windows)
```

### Missing reflection config at runtime

If your native binary throws `ClassNotFoundException` or `NoSuchMethodException` at runtime, you may need to add reflection configuration for your own classes. See the [Reflection Config](#reflection-config) section above.

## CI/CD

For automated native image builds in CI, use the GraalVM GitHub Action:

```yaml
- uses: graalvm/setup-graalvm@v1
  with:
    java-version: '25'
    distribution: 'graalvm'

- run: krema build --native
```

See the [Auto Updates](/docs/guides/auto-updates) guide for setting up a full release pipeline.
