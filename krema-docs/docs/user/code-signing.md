# Code Signing Guide

This guide covers how to sign your Crema application for distribution on macOS and Windows. Code signing establishes your identity as the developer, prevents tampering warnings, and is required for macOS notarization.

---

## macOS

### Prerequisites

1. **Apple Developer account** ($99/year) at [developer.apple.com](https://developer.apple.com)
2. **Developer ID Application certificate** installed in your Keychain
3. **Xcode Command Line Tools** (`xcode-select --install`)

### Obtaining a signing certificate

1. Open **Keychain Access** and go to **Keychain Access > Certificate Assistant > Request a Certificate From a Certificate Authority**
2. Enter your email and select **Saved to disk**
3. Go to [developer.apple.com/account/resources/certificates](https://developer.apple.com/account/resources/certificates)
4. Click **+**, select **Developer ID Application**, and upload your certificate signing request
5. Download and double-click the certificate to install it in your Keychain

Verify it's installed:

```bash
security find-identity -v -p codesigning
```

You should see a line like:

```
1) ABC123DEF456 "Developer ID Application: Your Name (TEAMID)"
```

The quoted string is your **signing identity**.

### Configuration

Add the following to your `crema.toml`:

```toml
[bundle.macos]
signing_identity = "Developer ID Application: Your Name (TEAMID)"
```

#### Optional: custom entitlements

Crema generates default entitlements for JVM apps automatically. If you need additional entitlements (e.g., network access, camera), create an `entitlements.plist`:

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

Then reference it in your config:

```toml
[bundle.macos]
signing_identity = "Developer ID Application: Your Name (TEAMID)"
entitlements = "entitlements.plist"
```

The three `com.apple.security.cs.*` entitlements are required for JVM applications and are included in the defaults that Crema generates when no custom file is specified.

### Notarization

Notarization is Apple's automated review process. Without it, users see a warning dialog when opening your app. Notarization requires a signed app and submits your DMG to Apple's servers for verification.

#### Setup

Add your Apple ID credentials to the config:

```toml
[bundle.macos]
signing_identity = "Developer ID Application: Your Name (TEAMID)"
notarization_apple_id = "your@email.com"
notarization_team_id = "TEAMID"
```

Set your app-specific password as an environment variable:

```bash
export CREMA_APPLE_PASSWORD="xxxx-xxxx-xxxx-xxxx"
```

> **Generating an app-specific password:** Go to [appleid.apple.com](https://appleid.apple.com), sign in, go to **Sign-In and Security > App-Specific Passwords**, and generate one. This is *not* your Apple ID password.

The `APPLE_PASSWORD` environment variable is also accepted as a fallback.

#### Keychain profile alternative

If you prefer not to use environment variables, you can store credentials in the macOS Keychain:

```bash
xcrun notarytool store-credentials crema-notarization \
  --apple-id "your@email.com" \
  --team-id "TEAMID" \
  --password "xxxx-xxxx-xxxx-xxxx"
```

When `notarization_apple_id` and `notarization_team_id` are not set in your config, Crema falls back to the `crema-notarization` keychain profile automatically.

### Building and signing

Signing and notarization are opt-in via flags. The config tells Crema *how* to sign; the flags tell it *whether* to sign.

```bash
# Build only — no signing
crema bundle --type dmg

# Sign the bundle
crema bundle --type dmg --sign

# Sign and notarize (--notarize implies --sign)
crema bundle --type dmg --notarize
```

With `--sign`, Crema will:

1. Build your application
2. Create the `.app` bundle
3. Sign the `.app` with your signing identity (hardened runtime enabled)
4. Create the `.dmg`
5. Sign the `.dmg`

With `--notarize`, Crema additionally:

6. Submits the `.dmg` to Apple for notarization
7. Staples the notarization ticket to the `.dmg`

### Verifying the result

Check the signature:

```bash
codesign --verify --deep --strict --verbose=2 target/bundle/macos/YourApp.app
```

Check notarization:

```bash
spctl --assess --type open --context context:primary-signature -v target/bundle/macos/YourApp.dmg
```

---

## Windows

### Prerequisites

1. **Code signing certificate** in `.pfx` format from a certificate authority (DigiCert, Sectigo, etc.)
2. **Windows SDK** with `signtool.exe` installed

### Installing the Windows SDK

Download from [developer.microsoft.com/en-us/windows/downloads/windows-sdk](https://developer.microsoft.com/en-us/windows/downloads/windows-sdk/). During installation, make sure **Windows SDK Signing Tools for Desktop Apps** is selected.

Crema searches for `signtool.exe` in:

1. Your system `PATH`
2. `C:\Program Files (x86)\Windows Kits\10\bin\x64\signtool.exe`
3. `C:\Program Files (x86)\Windows Kits\10\App Certification Kit\signtool.exe`

### Configuration

Add the following to your `crema.toml`:

```toml
[bundle.windows]
signing_certificate = "certs/myapp.pfx"
```

Set your certificate password as an environment variable:

```bash
set CREMA_WINDOWS_SIGN_PASSWORD=your-certificate-password
```

#### Optional: custom timestamp server

The default timestamp server is `http://timestamp.digicert.com`. If your certificate authority provides a different one:

```toml
[bundle.windows]
signing_certificate = "certs/myapp.pfx"
timestamp_url = "http://timestamp.sectigo.com"
```

Timestamping ensures the signature remains valid after the certificate expires.

### Building and signing

Signing is opt-in via the `--sign` flag:

```bash
# Build only — no signing
crema bundle

# Sign the executable
crema bundle --sign
```

With `--sign`, Crema will:

1. Build your application
2. Create the `.exe` installer via jpackage
3. Sign all `.exe` files with your certificate (SHA256, timestamped)

### Verifying the result

Right-click the `.exe` file, go to **Properties > Digital Signatures** to see the signature details.

Or from the command line:

```bash
signtool verify /pa /v YourApp.exe
```

---

## Troubleshooting

### macOS: "Developer ID Application" certificate not found

Make sure the certificate is in your **login** keychain (not System). Run:

```bash
security find-identity -v -p codesigning
```

If the certificate shows as `CSSMERR_TP_NOT_TRUSTED`, open Keychain Access, find the certificate, expand it, double-click the issuing CA certificate, and set **Trust > Code Signing** to **Always Trust**.

### macOS: notarization fails with "Invalid credentials"

- Verify your Team ID at [developer.apple.com/account](https://developer.apple.com/account) (it's shown under Membership)
- Make sure you're using an **app-specific password**, not your Apple ID password
- Check that the `CREMA_APPLE_PASSWORD` environment variable is set in the shell where you run `crema bundle`

### macOS: notarization fails with "The signature is invalid"

The app must be signed with hardened runtime (`--options runtime`). Crema enables this automatically. If you see this error, ensure your signing identity is a **Developer ID Application** certificate, not a development or distribution certificate.

### Windows: "signtool.exe not found"

Install the Windows SDK. If it's installed but not found, add the SDK bin directory to your `PATH`:

```bash
set PATH=%PATH%;C:\Program Files (x86)\Windows Kits\10\bin\10.0.22621.0\x64
```

Replace `10.0.22621.0` with your installed SDK version.

### Windows: "CREMA_WINDOWS_SIGN_PASSWORD not found"

Set the environment variable before running the bundle command:

```bash
set CREMA_WINDOWS_SIGN_PASSWORD=your-password
```

For CI/CD, use your platform's secrets mechanism (GitHub Actions secrets, Azure DevOps variables, etc.).

---

## CI/CD

### GitHub Actions example (macOS)

```yaml
- name: Import certificate
  env:
    CERTIFICATE_P12: ${{ secrets.APPLE_CERTIFICATE_P12 }}
    CERTIFICATE_PASSWORD: ${{ secrets.APPLE_CERTIFICATE_PASSWORD }}
  run: |
    echo "$CERTIFICATE_P12" | base64 --decode > certificate.p12
    security create-keychain -p "" build.keychain
    security default-keychain -s build.keychain
    security unlock-keychain -p "" build.keychain
    security import certificate.p12 -k build.keychain -P "$CERTIFICATE_PASSWORD" -T /usr/bin/codesign
    security set-key-partition-list -S apple-tool:,apple: -s -k "" build.keychain

- name: Bundle, sign, and notarize
  env:
    CREMA_APPLE_PASSWORD: ${{ secrets.APPLE_PASSWORD }}
  run: crema bundle --type dmg --notarize
```

### GitHub Actions example (Windows)

```yaml
- name: Decode certificate
  shell: bash
  run: echo "${{ secrets.WINDOWS_PFX_BASE64 }}" | base64 --decode > certs/myapp.pfx

- name: Bundle and sign
  env:
    CREMA_WINDOWS_SIGN_PASSWORD: ${{ secrets.WINDOWS_SIGN_PASSWORD }}
  run: crema bundle --sign
```

---

## Full configuration reference

```toml
[bundle.macos]
# Required for signing. The full name of your Developer ID Application certificate.
signing_identity = "Developer ID Application: Your Name (TEAMID)"

# Optional. Path to a custom entitlements.plist file.
# If omitted, Crema generates defaults with JVM-required entitlements.
entitlements = "entitlements.plist"

# Optional. Required for notarization via Apple ID mode.
notarization_apple_id = "your@email.com"
notarization_team_id = "TEAMID"
# Password read from CREMA_APPLE_PASSWORD or APPLE_PASSWORD env var.
# If neither apple_id nor team_id is set, falls back to "crema-notarization" keychain profile.

[bundle.windows]
# Required for signing. Path to your .pfx certificate file.
signing_certificate = "certs/myapp.pfx"

# Optional. Timestamp server URL. Default: http://timestamp.digicert.com
timestamp_url = "http://timestamp.digicert.com"
# Password read from CREMA_WINDOWS_SIGN_PASSWORD env var.
```
