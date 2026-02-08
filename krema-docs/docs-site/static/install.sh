#!/usr/bin/env bash
#
# Krema standalone installer
# Usage: curl -fsSL https://krema.build/install.sh | bash
#
# Environment:
#   KREMA_VERSION       Override version to install (default: latest)
#   KREMA_INSTALL_DIR   Override install directory (default: ~/.krema/bin)
#   KREMA_JAVA_HOME     Override JDK location at runtime
#

set -euo pipefail

VERSION="${KREMA_VERSION:-0.1.0}"
GITHUB_REPO="krema-build/krema"
REQUIRED_JAVA_MAJOR=25
KREMA_HOME="${HOME}/.krema"
LIB_DIR="${KREMA_HOME}/lib"
JDK_DIR="${KREMA_HOME}/jdk"
BIN_DIR="${KREMA_INSTALL_DIR:-${KREMA_HOME}/bin}"
JAR_NAME="krema-cli.jar"

# --- Helpers ---

info() { printf '  %s\n' "$@"; }
error() { printf 'Error: %s\n' "$@" >&2; }

detect_platform() {
    local os arch
    os="$(uname -s)"
    arch="$(uname -m)"

    case "$os" in
        Darwin) os="darwin" ;;
        Linux)  os="linux" ;;
        *)      error "Unsupported OS: $os"; exit 1 ;;
    esac

    case "$arch" in
        x86_64|amd64)  arch="x64" ;;
        aarch64|arm64) arch="arm64" ;;
        *)             error "Unsupported architecture: $arch"; exit 1 ;;
    esac

    echo "${os}-${arch}"
}

detect_adoptium_platform() {
    local os arch
    os="$(uname -s)"
    arch="$(uname -m)"

    case "$os" in
        Darwin) os="mac" ;;
        Linux)  os="linux" ;;
    esac

    case "$arch" in
        x86_64|amd64)  arch="x64" ;;
        aarch64|arm64) arch="aarch64" ;;
    esac

    echo "${os}/${arch}"
}

download() {
    local url="$1" dest="$2"
    mkdir -p "$(dirname "$dest")"
    if command -v curl &>/dev/null; then
        curl -fSL --progress-bar -o "$dest" "$url"
    elif command -v wget &>/dev/null; then
        wget -q --show-progress -O "$dest" "$url"
    else
        error "Neither curl nor wget found. Install one and try again."
        exit 1
    fi
}

# --- Locate Java 25 ---
# Ported from bin/krema (lines 17-55)

find_java() {
    # 1. KREMA_JAVA_HOME override
    if [ -n "${KREMA_JAVA_HOME:-}" ]; then
        echo "$KREMA_JAVA_HOME/bin/java"
        return
    fi

    # 2. macOS: /usr/libexec/java_home
    if [ -x /usr/libexec/java_home ]; then
        local jh
        jh="$(/usr/libexec/java_home -v "$REQUIRED_JAVA_MAJOR" 2>/dev/null || true)"
        if [ -n "$jh" ] && [ -x "$jh/bin/java" ]; then
            echo "$jh/bin/java"
            return
        fi
    fi

    # 3. JAVA_HOME if it matches the required version
    if [ -n "${JAVA_HOME:-}" ] && [ -x "$JAVA_HOME/bin/java" ]; then
        local ver
        ver="$("$JAVA_HOME/bin/java" -version 2>&1 | head -1 | sed -E 's/.*"([0-9]+).*/\1/')"
        if [ "$ver" = "$REQUIRED_JAVA_MAJOR" ]; then
            echo "$JAVA_HOME/bin/java"
            return
        fi
    fi

    # 4. java on PATH
    if command -v java &>/dev/null; then
        local ver
        ver="$(java -version 2>&1 | head -1 | sed -E 's/.*"([0-9]+).*/\1/')"
        if [ "$ver" = "$REQUIRED_JAVA_MAJOR" ]; then
            echo "java"
            return
        fi
    fi

    # 5. Previously installed Temurin
    local temurin_dir="${JDK_DIR}/temurin-${REQUIRED_JAVA_MAJOR}"
    if [ -d "$temurin_dir" ]; then
        local jdk_subdir
        for jdk_subdir in "$temurin_dir"/*/; do
            local candidate
            if [ "$(uname -s)" = "Darwin" ]; then
                candidate="${jdk_subdir}Contents/Home/bin/java"
            else
                candidate="${jdk_subdir}bin/java"
            fi
            if [ -x "$candidate" ]; then
                echo "$candidate"
                return
            fi
        done
    fi

    return 1
}

install_jdk() {
    local adoptium_platform
    adoptium_platform="$(detect_adoptium_platform)"
    local url="https://api.adoptium.net/v3/binary/latest/${REQUIRED_JAVA_MAJOR}/ga/${adoptium_platform}/jdk/hotspot/normal/eclipse"
    local dest_dir="${JDK_DIR}/temurin-${REQUIRED_JAVA_MAJOR}"
    local tmp_file="${JDK_DIR}/temurin-${REQUIRED_JAVA_MAJOR}.tar.gz"

    info "Downloading Eclipse Temurin ${REQUIRED_JAVA_MAJOR}..."
    download "$url" "$tmp_file"

    info "Extracting..."
    mkdir -p "$dest_dir"
    tar xzf "$tmp_file" -C "$dest_dir"
    rm -f "$tmp_file"

    # Verify
    local java_path
    java_path="$(find_java)" || {
        error "JDK extraction succeeded but java binary not found"
        exit 1
    }
    info "Installed: $java_path"
}

# --- Main ---

main() {
    echo "Krema Installer v${VERSION}"
    echo ""

    local platform
    platform="$(detect_platform)"
    info "Platform: ${platform}"

    mkdir -p "$LIB_DIR" "$BIN_DIR"

    # --- Step 1: Try native binary ---
    local native_url="https://github.com/${GITHUB_REPO}/releases/download/v${VERSION}/krema-${platform}"
    local native_bin="${BIN_DIR}/krema"
    local use_native=false

    info "Checking for native binary..."
    if curl -fsSL --head "$native_url" &>/dev/null 2>&1; then
        info "Downloading native binary..."
        download "$native_url" "$native_bin"
        chmod +x "$native_bin"
        use_native=true
        info "Native binary installed!"
    else
        info "No native binary for ${platform}, falling back to JAR"
    fi

    # --- Step 2: Download JAR (if no native binary) ---
    if [ "$use_native" = false ]; then
        local jar_path="${LIB_DIR}/${JAR_NAME}"
        if [ ! -f "$jar_path" ]; then
            info "Downloading ${JAR_NAME}..."
            download "https://github.com/${GITHUB_REPO}/releases/download/v${VERSION}/${JAR_NAME}" "$jar_path"
        fi

        # --- Step 3: Find or install Java ---
        local java_path
        java_path="$(find_java 2>/dev/null)" || true

        if [ -z "$java_path" ]; then
            echo ""
            if [ -t 0 ]; then
                printf "  Java %s is required. Install Eclipse Temurin %s to ~/.krema/jdk? [Y/n] " \
                    "$REQUIRED_JAVA_MAJOR" "$REQUIRED_JAVA_MAJOR"
                read -r answer
                case "${answer:-y}" in
                    [Yy]|[Yy]es|"") install_jdk ;;
                    *)
                        echo ""
                        info "Skipping JDK install."
                        info "Install Java ${REQUIRED_JAVA_MAJOR} manually, then set KREMA_JAVA_HOME."
                        info "Download from: https://adoptium.net/temurin/releases/"
                        ;;
                esac
            else
                install_jdk
            fi
            java_path="$(find_java 2>/dev/null)" || true
        fi

        # --- Create launcher script ---
        cat > "${BIN_DIR}/krema" << LAUNCHER
#!/usr/bin/env bash
set -euo pipefail
KREMA_HOME="${KREMA_HOME}"
JAR="${jar_path}"
JAVA="${java_path:-java}"

# Re-detect Java at runtime if needed
if [ ! -x "\$JAVA" ] || [ "\$JAVA" = "java" ]; then
    REQUIRED_JAVA_MAJOR=${REQUIRED_JAVA_MAJOR}
    if [ -n "\${KREMA_JAVA_HOME:-}" ]; then
        JAVA="\$KREMA_JAVA_HOME/bin/java"
    elif [ -x /usr/libexec/java_home ]; then
        JH="\$(/usr/libexec/java_home -v "\$REQUIRED_JAVA_MAJOR" 2>/dev/null || true)"
        [ -n "\$JH" ] && JAVA="\$JH/bin/java"
    fi
fi

exec "\$JAVA" --enable-native-access=ALL-UNNAMED -jar "\$JAR" "\$@"
LAUNCHER
        chmod +x "${BIN_DIR}/krema"
    fi

    # --- Add to PATH ---
    echo ""
    if echo "$PATH" | tr ':' '\n' | grep -qx "$BIN_DIR"; then
        info "krema is already on your PATH"
    else
        info "Add krema to your PATH by adding this to your shell profile:"
        echo ""
        info "  export PATH=\"${BIN_DIR}:\$PATH\""
        echo ""

        # Try to symlink to a common PATH location
        if [ -d "${HOME}/.local/bin" ] && echo "$PATH" | tr ':' '\n' | grep -qx "${HOME}/.local/bin"; then
            ln -sf "${BIN_DIR}/krema" "${HOME}/.local/bin/krema"
            info "Also symlinked to ~/.local/bin/krema"
        fi
    fi

    echo ""
    echo "Done! Run 'krema --help' to get started."
}

main "$@"
