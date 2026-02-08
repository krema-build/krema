---
sidebar_position: 5
title: Code Signing
description: Sign your Krema app for distribution
---

# Code Signing

Sign your Krema application for distribution on macOS and Windows. Code signing establishes your developer identity, prevents tampering warnings, and is required for macOS notarization.

## macOS

### Prerequisites

1. **Apple Developer account** ($99/year) at [developer.apple.com](https://developer.apple.com)
2. **Developer ID Application certificate** in your Keychain
3. **Xcode Command Line Tools** (`xcode-select --install`)

### Obtaining a Certificate

1. Open **Keychain Access** > **Certificate Assistant** > **Request a Certificate From a Certificate Authority**
2. Enter your email and select **Saved to disk**
3. Go to [developer.apple.com/account/resources/certificates](https://developer.apple.com/account/resources/certificates)
4. Click **+**, select **Developer ID Application**, upload your CSR
5. Download and double-click to install

Verify installation:

```bash
security find-identity -v -p codesigning
```

You should see:
```
1) ABC123DEF456 "Developer ID Application: Your Name (TEAMID)"
```

### Configuration

Add to `krema.toml`:

```toml
[bundle.macos]
signing_identity = "Developer ID Application: Your Name (TEAMID)"
```

### Custom Entitlements

Krema generates default entitlements for JVM apps. For additional capabilities, create `entitlements.plist`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN"
  "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
    <key>com.apple.security.cs.allow-jit</key>
    <true/>
    <key>com.apple.security.cs.allow-unsigned-executable-memory</key>
    <true/>
    <key>com.apple.security.cs.allow-dyld-environment-variables</key>
    <true/>
    <key>com.apple.security.network.client</key>
    <true/>
</dict>
</plist>
```

Reference it in config:

```toml
[bundle.macos]
signing_identity = "Developer ID Application: Your Name (TEAMID)"
entitlements = "entitlements.plist"
```

### Notarization

Notarization is Apple's automated review process. Without it, users see a warning dialog.

#### Setup

Add credentials to config:

```toml
[bundle.macos]
signing_identity = "Developer ID Application: Your Name (TEAMID)"
notarization_apple_id = "your@email.com"
notarization_team_id = "TEAMID"
```

Set your app-specific password:

```bash
export KREMA_APPLE_PASSWORD="xxxx-xxxx-xxxx-xxxx"
```

Generate an app-specific password at [appleid.apple.com](https://appleid.apple.com) under **Sign-In and Security > App-Specific Passwords**.

#### Keychain Profile (Alternative)

Store credentials in the macOS Keychain:

```bash
xcrun notarytool store-credentials krema-notarization \
  --apple-id "your@email.com" \
  --team-id "TEAMID" \
  --password "xxxx-xxxx-xxxx-xxxx"
```

### Building and Signing

```bash
# Build only — no signing
krema bundle --type dmg

# Sign the bundle
krema bundle --type dmg --sign

# Sign and notarize
krema bundle --type dmg --notarize
```

### Verify Signature

```bash
codesign --verify --deep --strict --verbose=2 target/bundle/macos/YourApp.app
```

Check notarization:

```bash
spctl --assess --verbose=2 target/bundle/macos/YourApp.dmg
```

## Windows

### Prerequisites

1. **Code signing certificate** from a trusted CA (DigiCert, Sectigo, etc.)
2. **Windows SDK** with `signtool.exe`

### Certificate Options

| Type | Cost | Trust |
|------|------|-------|
| OV (Organization Validation) | ~$200-500/year | Standard |
| EV (Extended Validation) | ~$300-600/year | Highest (SmartScreen bypass) |

### Configuration

Add to `krema.toml`:

```toml
[bundle.windows]
certificate_path = "path/to/certificate.pfx"
```

Set the certificate password:

```bash
set KREMA_CERTIFICATE_PASSWORD=your-pfx-password
```

### Building and Signing

```bash
# Build without signing
krema bundle --type exe

# Build and sign
krema bundle --type exe --sign
```

### Verify Signature

```powershell
signtool verify /pa /v target\bundle\windows\YourApp.exe
```

## CI/CD Integration

### GitHub Actions (macOS)

```yaml
jobs:
  build-macos:
    runs-on: macos-latest
    steps:
      - uses: actions/checkout@v4

      - name: Import certificate
        env:
          CERTIFICATE_P12: ${{ secrets.MACOS_CERTIFICATE }}
          CERTIFICATE_PASSWORD: ${{ secrets.MACOS_CERTIFICATE_PASSWORD }}
        run: |
          echo $CERTIFICATE_P12 | base64 --decode > certificate.p12
          security create-keychain -p "" build.keychain
          security default-keychain -s build.keychain
          security unlock-keychain -p "" build.keychain
          security import certificate.p12 -k build.keychain -P "$CERTIFICATE_PASSWORD" -T /usr/bin/codesign
          security set-key-partition-list -S apple-tool:,apple: -s -k "" build.keychain

      - name: Build and sign
        env:
          KREMA_APPLE_PASSWORD: ${{ secrets.APPLE_PASSWORD }}
        run: krema bundle --type dmg --notarize
```

### GitHub Actions (Windows)

```yaml
jobs:
  build-windows:
    runs-on: windows-latest
    steps:
      - uses: actions/checkout@v4

      - name: Import certificate
        env:
          CERTIFICATE_P12: ${{ secrets.WINDOWS_CERTIFICATE }}
          CERTIFICATE_PASSWORD: ${{ secrets.WINDOWS_CERTIFICATE_PASSWORD }}
        run: |
          $bytes = [Convert]::FromBase64String($env:CERTIFICATE_P12)
          [IO.File]::WriteAllBytes("certificate.pfx", $bytes)

      - name: Build and sign
        env:
          KREMA_CERTIFICATE_PASSWORD: ${{ secrets.WINDOWS_CERTIFICATE_PASSWORD }}
        run: krema bundle --type exe --sign
```

## Troubleshooting

### "Your app is damaged" (macOS)

The app isn't signed or notarized. Run with `--notarize`.

### "Windows protected your PC" (SmartScreen)

Use an EV certificate for immediate trust, or wait for reputation to build with an OV certificate.

### Signature expired

Certificates have expiration dates. Use timestamping (enabled by default) so signatures remain valid after expiration.
