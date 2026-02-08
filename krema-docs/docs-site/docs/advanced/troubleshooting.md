---
sidebar_position: 2
title: Troubleshooting
description: Common issues and solutions for Krema apps
---

# Troubleshooting

Solutions to common issues when developing Krema applications.

## Installation Issues

### "Java not found" or wrong version

Krema requires Java 25+ for the Foreign Function & Memory API.

```bash
# Check Java version
java -version

# Should show 25 or higher
```

**Solution:** Install JDK 25:
```bash
# macOS
brew install openjdk@25

# Add to PATH
export JAVA_HOME=/opt/homebrew/opt/openjdk@25
export PATH="$JAVA_HOME/bin:$PATH"
```

### "Maven not found"

```bash
# Install Maven
brew install maven  # macOS
apt install maven   # Ubuntu
choco install maven # Windows
```

### Linux: "WebKitGTK not found"

Install the WebKitGTK runtime libraries:

```bash
# Ubuntu/Debian
sudo apt install libwebkit2gtk-4.1-0 libgtk-3-0

# Fedora
sudo dnf install webkit2gtk4.0 gtk3

# Arch
sudo pacman -S webkit2gtk-4.1 gtk3
```

Note: Krema bundles `libwebview.so` - you only need the WebKitGTK runtime, not the development headers.

## Development Issues

### "krema dev" shows blank window

**Cause:** Frontend dev server not ready.

**Solutions:**
1. Check if dev server is running: `curl http://localhost:5173`
2. Verify `frontend_dev_url` in `krema.toml` matches your dev server
3. Check for port conflicts

### Hot reload not working

**Cause:** File watcher not detecting changes.

**Solutions:**
1. Check if your IDE is using external file sync
2. Increase file watcher limit on Linux:
   ```bash
   echo fs.inotify.max_user_watches=524288 | sudo tee -a /etc/sysctl.conf
   sudo sysctl -p
   ```

### "Command not found" when invoking

**Cause:** Command not registered or typo in command name.

**Solutions:**
1. Verify the method has `@KremaCommand` annotation
2. Check command name matches exactly (case-sensitive)
3. Ensure command class is registered:
   ```java
   Krema.app()
       .commands(new MyCommands())  // Don't forget this!
       .run();
   ```

### Permission denied errors

**Cause:** Required permission not configured.

**Solution:** Add permission to `krema.toml`:
```toml
[permissions]
allow = [
    "fs:read",      # Add missing permission
    "fs:write"
]
```

### JavaScript errors in console

**Cause:** `window.krema` not injected yet.

**Solution:** Wait for DOMContentLoaded:
```javascript
document.addEventListener('DOMContentLoaded', async () => {
    const result = await window.krema.invoke('myCommand');
});
```

Or check if krema is available:
```javascript
if (window.krema) {
    // Safe to use
}
```

## Build Issues

### "GraalVM not found" for native builds

**Solution:** Install GraalVM JDK 25+ and set environment:
```bash
export GRAALVM_HOME=/path/to/graalvm-jdk-25
export PATH="$GRAALVM_HOME/bin:$PATH"

# Verify (native-image is built-in with GraalVM 25+)
native-image --version
```

See the [Native Image guide](/docs/guides/native-image) for platform-specific installation instructions.

### Native image build fails

**Common causes:**
1. **Missing `main_class`**: Set `main_class` in the `[build]` section of `krema.toml`
2. **Missing reflection config**: Add your classes to `reflect-config.json` if they use reflection
3. **Incompatible library**: Some libraries need special GraalVM configuration
4. **Insufficient memory**: Increase heap: `MAVEN_OPTS="-Xmx4g"`
5. **Architecture mismatch on macOS**: Krema handles Apple Silicon automatically, but Rosetta can cause issues with third-party tools

See the [Native Image troubleshooting](/docs/guides/native-image#troubleshooting) section for detailed solutions.

### Frontend build fails

**Solution:** Build frontend separately first:
```bash
cd src
npm install
npm run build
```

Check for TypeScript or bundler errors.

## Bundle Issues

### macOS: "App is damaged"

**Cause:** App not signed or notarized.

**Solution:**
```bash
# Sign and notarize
krema bundle --type dmg --notarize
```

Or for development, remove quarantine:
```bash
xattr -cr /path/to/YourApp.app
```

### macOS: Notarization fails

**Common issues:**
1. **Wrong credentials**: Check `KREMA_APPLE_PASSWORD` is app-specific password
2. **Hardened runtime missing**: Krema enables this by default
3. **Entitlements issue**: Ensure JVM entitlements are present

Check notarization log:
```bash
xcrun notarytool log <submission-id> --apple-id your@email.com --team-id TEAMID
```

### Windows: SmartScreen warning

**Cause:** App not signed with trusted certificate.

**Solutions:**
1. Sign with an EV certificate (bypasses SmartScreen immediately)
2. Sign with OV certificate (reputation builds over time)
3. Users can click "More info" → "Run anyway"

### Windows: Missing DLLs

**Cause:** Visual C++ runtime not installed.

**Solution:** Bundle the VC++ redistributable or instruct users to install it.

### Linux: AppImage won't run

**Solution:** Make it executable:
```bash
chmod +x MyApp.AppImage
./MyApp.AppImage
```

If that fails, check for missing libraries:
```bash
./MyApp.AppImage --appimage-extract
ldd squashfs-root/usr/bin/java | grep "not found"
```

## Runtime Issues

### High memory usage

**Causes and solutions:**
1. **Memory leak**: Profile with VisualVM or JFR
2. **Large caches**: Implement cache eviction
3. **Too many windows**: Close unused windows

### Slow startup

**Solutions:**
1. Use native image for instant startup
2. Lazy-load heavy dependencies
3. Use splash screen to improve perceived performance

### Crashes on specific platforms

**Debugging:**

1. **Check Krema crash reports first** — Krema automatically writes crash reports to `{appDataDir}/crash-reports/` as JSON files. Each report includes the error source, stack trace, OS info, app version, and recent IPC commands:
   ```bash
   # macOS
   ls ~/Library/Application\ Support/YourApp/crash-reports/

   # Linux
   ls ~/.local/share/YourApp/crash-reports/

   # Windows
   dir %APPDATA%\YourApp\crash-reports\
   ```

2. **Run with debug logging:**
   ```bash
   java -Dkrema.debug=true -jar myapp.jar
   ```

3. **Check OS-level crash logs** (if Krema crash reports aren't available):
   ```bash
   # macOS: ~/Library/Logs/DiagnosticReports/
   # Windows: Event Viewer > Windows Logs > Application
   # Linux: /var/log/syslog or journalctl
   ```

## Auto-Update Issues

### "Signature verification failed"

**Causes:**
1. Wrong public key in config
2. File corrupted during download
3. Signature file missing

**Solution:**
```bash
# Verify signature manually
krema signer verify MyApp.dmg --pubkey "MCowBQYDK2..."
```

### Update check returns 204 but update exists

**Cause:** Server returning 204 incorrectly.

**Check:** Verify server endpoint returns JSON for newer versions.

### Update downloads but doesn't install

**Cause:** Platform-specific installation issue.

**Debug:** Check logs in app data directory for error details.

## Getting Help

1. **Check the logs**: `~/.krema/logs/` or app-specific log location
2. **Enable debug mode**: Run with `-Dkrema.debug=true`
3. **Search issues**: [GitHub Issues](https://github.com/krema-build/krema/issues)

When reporting issues, include:
- Krema version (`krema --version`)
- Java version (`java -version`)
- OS and version
- Full error message and stack trace
- Steps to reproduce
