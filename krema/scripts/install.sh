#!/usr/bin/env bash
#
# Krema installer
# Builds the project and installs the `krema` command to /usr/local/bin.
#
# Usage:
#   ./install.sh            # build + install
#   ./install.sh --no-build # install only (skip maven build)
#

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
INSTALL_DIR="${KREMA_INSTALL_DIR:-/usr/local/bin}"
LAUNCHER="$(cd "$PROJECT_DIR/bin" && pwd -P)/krema"
JAR="$PROJECT_DIR/krema-cli/target/krema-cli.jar"
REQUIRED_JAVA_MAJOR=25

# --- Parse args ---

SKIP_BUILD=false
for arg in "$@"; do
    case "$arg" in
        --no-build) SKIP_BUILD=true ;;
        --help|-h)
            echo "Usage: ./install.sh [--no-build]"
            echo ""
            echo "Builds Krema and installs the 'krema' command to $INSTALL_DIR."
            echo ""
            echo "Options:"
            echo "  --no-build   Skip the Maven build (use existing JAR)"
            echo ""
            echo "Environment:"
            echo "  KREMA_INSTALL_DIR   Install directory (default: /usr/local/bin)"
            echo "  KREMA_JAVA_HOME    Override JDK location at runtime"
            exit 0
            ;;
    esac
done

# --- Check Java 25 ---

echo "Checking for Java $REQUIRED_JAVA_MAJOR..."

JAVA_HOME_25=""
if [ -x /usr/libexec/java_home ]; then
    JAVA_HOME_25="$(/usr/libexec/java_home -v "$REQUIRED_JAVA_MAJOR" 2>/dev/null || true)"
fi

if [ -z "$JAVA_HOME_25" ]; then
    echo "Error: Java $REQUIRED_JAVA_MAJOR not found." >&2
    echo "Install JDK $REQUIRED_JAVA_MAJOR and try again." >&2
    exit 1
fi

echo "  Found: $JAVA_HOME_25"

# --- Build ---

if [ "$SKIP_BUILD" = false ]; then
    echo ""
    echo "Building Krema..."
    export JAVA_HOME="$JAVA_HOME_25"
    (cd "$PROJECT_DIR" && "$JAVA_HOME/bin/java" -version 2>&1 | head -1)
    (cd "$PROJECT_DIR" && mvn clean install -q -DskipTests)
    echo "  Build complete."
fi

# --- Verify JAR ---

if [ ! -f "$JAR" ]; then
    echo "Error: $JAR not found. Run without --no-build." >&2
    exit 1
fi

# --- Make launcher executable ---

chmod +x "$LAUNCHER"

# --- Install ---

echo ""
echo "Installing to $INSTALL_DIR/krema..."

if [ -w "$INSTALL_DIR" ]; then
    ln -sf "$LAUNCHER" "$INSTALL_DIR/krema"
else
    echo "  (requires sudo)"
    sudo ln -sf "$LAUNCHER" "$INSTALL_DIR/krema"
fi

echo ""
echo "Done! You can now run:"
echo "  krema --help"
