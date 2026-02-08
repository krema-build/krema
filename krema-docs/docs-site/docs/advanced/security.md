---
sidebar_position: 1
title: Security
description: Security model and best practices for Krema apps
---

# Security

Krema's security model protects users while enabling powerful desktop capabilities.

## Permission System

Krema uses declarative permissions configured in `krema.toml`:

```toml
[permissions]
allow = [
    "fs:read",
    "fs:write",
    "clipboard:read",
    "clipboard:write",
    "notification",
    "shell:open"
]
```

### Available Permissions

| Permission | Description |
|------------|-------------|
| `fs:read` | Read files from the filesystem |
| `fs:write` | Write files to the filesystem |
| `fs:*` | Full filesystem access |
| `clipboard:read` | Read from clipboard |
| `clipboard:write` | Write to clipboard |
| `notification` | Show desktop notifications |
| `shell:execute` | Execute shell commands |
| `shell:open` | Open files/URLs with default app |
| `system-tray` | System tray access |
| `system-info` | Read system information |
| `network` | Make HTTP requests |
| `dialog` | Show file dialogs |
| `sql:read` | Read from SQLite databases |
| `sql:write` | Write to SQLite databases |
| `autostart:manage` | Manage launch-at-login |
| `deep-link:read` | Handle custom URL schemes |

### Permission Enforcement

Commands requiring permissions must be annotated:

```java
import build.krema.security.RequiresPermission;
import build.krema.security.Permission;

@KremaCommand
@RequiresPermission(Permission.FS_READ)
public String readFile(String path) throws IOException {
    return Files.readString(Path.of(path));
}

@KremaCommand
@RequiresPermission(Permission.SHELL_EXECUTE)
public String runCommand(String command) {
    return Shell.execute(command).output();
}
```

If a command is called without the required permission, an error is returned to the frontend.

## Content Security Policy

Configure CSP headers in `krema.toml`:

```toml
[security]
csp = "default-src 'self'; script-src 'self' 'unsafe-inline'; style-src 'self' 'unsafe-inline'"
```

Default CSP restricts:
- Only load resources from the app itself
- No inline scripts (unless explicitly allowed)
- No eval()
- No external connections (unless `network` permission granted)

### Development Mode

In dev mode, CSP is relaxed to allow:
- Hot module replacement
- Source maps
- Dev server connections

## Secure Storage

Store sensitive data using the OS keychain:

```java
// Store a secret
SecureStorage.set("api-key", "sk-secret123");

// Retrieve a secret
String apiKey = SecureStorage.get("api-key").orElse(null);

// Delete a secret
SecureStorage.delete("api-key");
```

Secrets are stored in:
- **macOS**: Keychain Services
- **Windows**: Credential Manager
- **Linux**: Secret Service (libsecret)

Never store secrets in:
- Plain text files
- localStorage
- The regular key-value store
- Environment variables (for runtime secrets)

## Input Validation

### Path Traversal

Always validate file paths:

```java
@KremaCommand
@RequiresPermission(Permission.FS_READ)
public String readFile(String path) throws IOException {
    Path normalized = Path.of(path).normalize();
    Path appDir = AppPaths.appDataDir();

    // Ensure path is within allowed directory
    if (!normalized.startsWith(appDir)) {
        throw new SecurityException("Access denied: path outside app directory");
    }

    return Files.readString(normalized);
}
```

### Command Injection

Avoid string concatenation for shell commands:

```java
// DANGEROUS - command injection possible
@KremaCommand
public String unsafeSearch(String query) {
    return Shell.execute("grep " + query + " /var/log/*").output();
}

// SAFE - use argument arrays
@KremaCommand
public String safeSearch(String query) {
    return Shell.execute("grep", query, "/var/log/app.log").output();
}
```

### SQL Injection

Use parameterized queries:

```java
// DANGEROUS - SQL injection
String sql = "SELECT * FROM users WHERE name = '" + name + "'";

// SAFE - parameterized query
String sql = "SELECT * FROM users WHERE name = ?";
connection.prepareStatement(sql).setString(1, name);
```

## Update Security

### Signature Verification

All updates are verified with Ed25519 signatures:

1. Generate a keypair: `krema signer generate`
2. Configure public key in `krema.toml`
3. Sign builds with private key
4. Updates are rejected if signatures don't match

```toml
[updater]
pubkey = "MCowBQYDK2VwAyEA..."
endpoints = ["https://releases.example.com/..."]
```

### Secure Update Delivery

- Always serve updates over HTTPS
- Use content checksums in addition to signatures
- Pin TLS certificates in production (future feature)

## Code Signing

Sign your application to:
- Establish developer identity
- Prevent tampering warnings
- Enable macOS notarization

See the [Code Signing Guide](/docs/guides/code-signing) for details.

## Network Security

### CORS Bypass

Backend HTTP requests bypass CORS, enabling API calls that would be blocked in a browser. This is intentional but requires caution:

```java
// Backend can access any API
@KremaCommand
public String fetchData(String url) {
    return HttpClient.fetch(url).get().body();
}
```

Validate URLs if accepting user input:

```java
@KremaCommand
public String fetchData(String url) {
    // Only allow specific domains
    if (!url.startsWith("https://api.example.com/")) {
        throw new SecurityException("Invalid URL");
    }
    return HttpClient.fetch(url).get().body();
}
```

### Secrets in Requests

Never expose secrets to the frontend:

```java
// Backend handles auth, frontend never sees the key
@KremaCommand
public String fetchProtectedData() {
    String apiKey = SecureStorage.get("api-key").orElseThrow();
    return HttpClient.fetch("https://api.example.com/data")
        .header("Authorization", "Bearer " + apiKey)
        .get()
        .body();
}
```

## Best Practices

1. **Principle of Least Privilege**: Only request permissions you need
2. **Validate All Input**: Never trust frontend data
3. **Use Secure Storage**: For API keys, tokens, and secrets
4. **Sign Your Releases**: Enable signature verification for updates
5. **Keep Dependencies Updated**: Monitor for security advisories
6. **Audit Third-Party Plugins**: Review permissions and code
7. **Log Security Events**: Track authentication failures, permission denials
8. **Test Security**: Include security tests in your CI pipeline
