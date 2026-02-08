#!/usr/bin/env bash
#
# Build libwebview.dylib from source for macOS.
#
# Uses the system WebKit framework (ships with macOS, no extra installs needed).
# Requires only Xcode Command Line Tools.
#
# Prerequisites:
#   xcode-select --install
#
# Usage:
#   ./scripts/build-webview-macos.sh               # build and install to krema-core/lib/
#   ./scripts/build-webview-macos.sh --system       # also copy to /usr/local/lib
#   ./scripts/build-webview-macos.sh --clean        # remove build artifacts
#

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
BUILD_DIR="$PROJECT_DIR/.webview-build"
OUTPUT_DIR="$PROJECT_DIR/krema-core/lib"
WEBVIEW_REPO="https://github.com/webview/webview.git"

# --- Parse args ---

INSTALL_SYSTEM=false
CLEAN=false

for arg in "$@"; do
    case "$arg" in
        --system)  INSTALL_SYSTEM=true ;;
        --clean)   CLEAN=true ;;
        --help|-h)
            echo "Usage: ./scripts/build-webview-macos.sh [--system] [--clean]"
            echo ""
            echo "Builds libwebview.dylib from source and places it in krema-core/lib/."
            echo ""
            echo "Options:"
            echo "  --system   Also install to /usr/local/lib (requires sudo)"
            echo "  --clean    Remove build artifacts and exit"
            echo ""
            echo "Prerequisites:"
            echo "  xcode-select --install"
            exit 0
            ;;
        *)
            echo "Unknown option: $arg" >&2
            exit 1
            ;;
    esac
done

# --- Clean ---

if [ "$CLEAN" = true ]; then
    echo "Cleaning build artifacts..."
    rm -rf "$BUILD_DIR"
    echo "Done."
    exit 0
fi

# --- Check dependencies ---

echo "Checking build dependencies..."

if ! command -v clang++ &>/dev/null; then
    echo "Error: clang++ not found. Install Xcode Command Line Tools:" >&2
    echo "  xcode-select --install" >&2
    exit 1
fi

echo "  All dependencies found."

# --- Clone or update ---

if [ -d "$BUILD_DIR/webview" ]; then
    echo "Updating webview source..."
    (cd "$BUILD_DIR/webview" && git pull --quiet)
else
    echo "Cloning webview/webview..."
    mkdir -p "$BUILD_DIR"
    git clone --quiet --depth 1 "$WEBVIEW_REPO" "$BUILD_DIR/webview"
fi

# --- Build ---

echo "Building libwebview.dylib..."

WEBVIEW_SRC="$BUILD_DIR/webview"

# Find the main source file (location varies between versions)
if [ -f "$WEBVIEW_SRC/webview.cc" ]; then
    SRC_FILE="$WEBVIEW_SRC/webview.cc"
elif [ -f "$WEBVIEW_SRC/core/src/webview.cc" ]; then
    SRC_FILE="$WEBVIEW_SRC/core/src/webview.cc"
else
    SRC_FILE=""
fi

OUTFILE="$BUILD_DIR/libwebview.dylib"

if [ -n "$SRC_FILE" ]; then
    clang++ "$SRC_FILE" -dynamiclib \
        -DWEBVIEW_BUILD_SHARED -DWEBVIEW_COCOA \
        -std=c++11 \
        -I"$WEBVIEW_SRC/core/include" \
        -framework WebKit -framework Cocoa \
        -o "$OUTFILE"
else
    # Single-header mode: create a minimal .cc that includes the header
    WRAPPER="$BUILD_DIR/webview_wrapper.cc"
    cat > "$WRAPPER" << 'WRAPPER_EOF'
#define WEBVIEW_BUILD_SHARED
#define WEBVIEW_COCOA
#include "webview.h"
WRAPPER_EOF
    clang++ "$WRAPPER" -dynamiclib \
        -I"$WEBVIEW_SRC" \
        -std=c++11 \
        -framework WebKit -framework Cocoa \
        -o "$OUTFILE"
fi

echo "  Built: $OUTFILE"

# --- Install to project ---

mkdir -p "$OUTPUT_DIR"
cp "$OUTFILE" "$OUTPUT_DIR/libwebview.dylib"
echo "  Installed to: $OUTPUT_DIR/libwebview.dylib"

# --- Optional system install ---

if [ "$INSTALL_SYSTEM" = true ]; then
    echo "  Installing to /usr/local/lib/ (requires sudo)..."
    sudo cp "$OUTFILE" /usr/local/lib/libwebview.dylib
    echo "  Installed to: /usr/local/lib/libwebview.dylib"
fi

echo ""
echo "Done! libwebview.dylib is ready at: $OUTPUT_DIR/libwebview.dylib"
