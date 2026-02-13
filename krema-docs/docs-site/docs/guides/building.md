---
sidebar_position: 3
title: Building & Bundling
description: Build your Krema app for production and create distributable packages
---

# Building & Bundling

Build your Krema application for production and package it for distribution.

## Building

### Basic Build

```bash
krema build
```

This runs the full production build pipeline:

1. **Frontend build** — runs your frontend build command (e.g., `npm run build`)
2. **Java compilation** — compiles your Java backend (via Maven if `pom.xml` exists, otherwise `javac`)
3. **Type generation** — the annotation processor generates `krema-commands.d.ts` with TypeScript types for your `@KremaCommand` methods
4. **Asset packaging** — copies the frontend build output into the Java classpath resources

The output is placed in the `target/` directory.

### Build Options

```bash
# Use a specific environment profile
krema build --env staging

# Skip frontend build (useful when iterating on Java only)
krema build --skip-frontend

# Skip Java compilation (useful when iterating on frontend only)
krema build --skip-java

# Compile to a native binary (see Native Image guide)
krema build --native
```

### Configuration

Build settings are defined in `krema.toml`:

```toml
[build]
main_class = "com.example.myapp.Main"
frontend_command = "npm run build"
out_dir = "dist"                        # frontend build output
java_source_dir = "src-java"            # Java source directory
assets_path = "web"                     # where assets are placed in classpath
```

### Package Manager Detection

Krema auto-detects your package manager by checking for lock files:

| Lock file | Package manager |
|-----------|----------------|
| `pnpm-lock.yaml` | pnpm |
| `yarn.lock` | yarn |
| `bun.lockb` | bun |
| _(default)_ | npm |

### Environment Profiles

Use environment profiles to customize builds for different targets:

```toml
[build]
frontend_command = "npm run build"

[env.staging]
[env.staging.build]
frontend_command = "npm run build:staging"

[env.production]
[env.production.build]
frontend_command = "npm run build:prod"
```

```bash
krema build --env staging
krema build --env production
```

Environment variables from `.env` files are also loaded automatically (`.env`, `.env.production`, etc.).

### TypeScript Type Generation

During Java compilation, the Krema annotation processor automatically generates a `krema-commands.d.ts` file containing TypeScript type definitions for all your `@KremaCommand` methods. By default, this file is written to `target/classes/`.

To write it directly into your frontend source tree for seamless IDE integration:

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-compiler-plugin</artifactId>
    <configuration>
        <annotationProcessorPaths>
            <path>
                <groupId>build.krema</groupId>
                <artifactId>krema-processor</artifactId>
                <version>${krema.version}</version>
            </path>
        </annotationProcessorPaths>
        <compilerArgs>
            <arg>-Akrema.ts.outDir=${project.basedir}/src</arg>
        </compilerArgs>
    </configuration>
</plugin>
```

See [TypeScript Support](/docs/api/javascript-api#generated-command-types) for details on the generated types.

## Bundling

After building, create a platform-specific distributable:

```bash
krema bundle
```

This detects your platform and creates the appropriate bundle format.

### Bundle Types

| Platform | Types | Default |
|----------|-------|---------|
| macOS | `app`, `dmg` | `dmg` |
| Windows | `exe`, `msi` | `exe` |
| Linux | `appimage`, `deb`, `rpm` | `appimage` |

```bash
# macOS
krema bundle --type dmg
krema bundle --type app    # .app only, no disk image

# Windows
krema bundle --type exe
krema bundle --type msi

# Linux
krema bundle --type appimage
krema bundle --type deb
```

### Bundle Options

```bash
# Skip the build step (if already built)
krema bundle --skip-build

# Sign the bundle
krema bundle --sign

# Sign and notarize (macOS only)
krema bundle --notarize

# Use a specific environment profile
krema bundle --env production
```

### Configuration

Configure bundle settings in `krema.toml`:

```toml
[package]
name = "my-app"
version = "1.0.0"
identifier = "com.example.myapp"

[bundle]
icon = "icons/icon.icns"

[bundle.macos]
signing_identity = "Developer ID Application: ..."

[bundle.windows]
certificate_path = "path/to/certificate.pfx"
```

### What the Bundler Produces

**macOS (.dmg):**
```
target/bundle/macos/
  MyApp.app/
    Contents/
      MacOS/         # JVM or native binary + native libs
      Resources/     # App icon, frontend assets
      Info.plist     # App metadata
  MyApp.dmg          # Disk image for distribution
```

**Windows (.exe):**
```
target/bundle/windows/
  MyApp/
    MyApp.exe        # Installer
    runtime/         # Bundled JVM
    app/             # Application JAR + assets
```

**Linux (.AppImage):**
```
target/bundle/linux/
  MyApp.AppImage     # Self-contained executable
```

## JAR vs Native Image

By default, `krema build` produces a JAR that requires a JVM at runtime. For distribution to end users, you have two options:

| Approach | Command | Pros | Cons |
|----------|---------|------|------|
| **JAR + bundled JVM** | `krema bundle` | Simple, no extra tools | Larger bundle size |
| **Native binary** | `krema build --native` then `krema bundle` | Fast startup, smaller bundle, no JVM needed | Requires GraalVM, longer build time |

For native image builds, see the [Native Image](/docs/guides/native-image) guide.

## Next Steps

- [Native Image](/docs/guides/native-image) — compile to a standalone native binary
- [Code Signing](/docs/guides/code-signing) — sign your app for distribution
- [Auto-Updates](/docs/guides/auto-updates) — ship updates to your users
