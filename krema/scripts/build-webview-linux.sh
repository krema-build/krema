#!/usr/bin/env bash
#
# Build libwebview.so from source for Linux.
#
# The webview/webview project does not publish pre-built Linux binaries,
# so this script clones the repo, compiles the shared library, and places
# it in krema-core/lib/.
#
# Prerequisites (install before running):
#   Ubuntu/Debian:  sudo apt install libwebkit2gtk-4.1-dev libgtk-3-dev build-essential pkg-config
#                   (or libwebkit2gtk-4.0-dev on older distros)
#   Fedora/RHEL:    sudo dnf install webkit2gtk4.1-devel gtk3-devel gcc-c++ pkg-config
#   Arch Linux:     sudo pacman -S webkit2gtk-4.1 gtk3 base-devel pkgconf
#
# Usage:
#   ./scripts/build-webview-linux.sh               # build and install to krema-core/lib/
#   ./scripts/build-webview-linux.sh --system       # also copy to /usr/local/lib
#   ./scripts/build-webview-linux.sh --clean        # remove build artifacts
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
            echo "Usage: ./scripts/build-webview-linux.sh [--system] [--clean]"
            echo ""
            echo "Builds libwebview.so from source and places it in krema-core/lib/."
            echo ""
            echo "Options:"
            echo "  --system   Also install to /usr/local/lib (requires sudo)"
            echo "  --clean    Remove build artifacts and exit"
            echo ""
            echo "Prerequisites:"
            echo "  Ubuntu/Debian:  sudo apt install libwebkit2gtk-4.1-dev libgtk-3-dev build-essential pkg-config"
            echo "  Fedora/RHEL:    sudo dnf install webkit2gtk4.1-devel gtk3-devel gcc-c++ pkg-config"
            echo "  Arch Linux:     sudo pacman -S webkit2gtk-4.1 gtk3 base-devel pkgconf"
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

if ! command -v g++ &>/dev/null; then
    echo "Error: g++ not found. Install build-essential (Debian) or gcc-c++ (Fedora)." >&2
    exit 1
fi

if ! command -v pkg-config &>/dev/null; then
    echo "Error: pkg-config not found. Install pkg-config or pkgconf." >&2
    exit 1
fi

if ! pkg-config --exists gtk+-3.0; then
    echo "Error: GTK3 development files not found." >&2
    echo "  Ubuntu/Debian: sudo apt install libgtk-3-dev" >&2
    echo "  Fedora/RHEL:   sudo dnf install gtk3-devel" >&2
    exit 1
fi

# Detect available WebKitGTK version (4.1 preferred, 4.0 as fallback)
if pkg-config --exists webkit2gtk-4.1; then
    WEBKIT_PKG="webkit2gtk-4.1"
elif pkg-config --exists webkit2gtk-4.0; then
    WEBKIT_PKG="webkit2gtk-4.0"
else
    echo "Error: WebKitGTK development files not found." >&2
    echo "  Ubuntu/Debian: sudo apt install libwebkit2gtk-4.1-dev (or libwebkit2gtk-4.0-dev)" >&2
    echo "  Fedora/RHEL:   sudo dnf install webkit2gtk4.1-devel" >&2
    exit 1
fi

echo "  All dependencies found (using $WEBKIT_PKG)."

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

echo "Building libwebview.so..."

WEBVIEW_SRC="$BUILD_DIR/webview"

# Find the main source file (location varies between versions)
if [ -f "$WEBVIEW_SRC/webview.cc" ]; then
    SRC_FILE="$WEBVIEW_SRC/webview.cc"
elif [ -f "$WEBVIEW_SRC/core/src/webview.cc" ]; then
    SRC_FILE="$WEBVIEW_SRC/core/src/webview.cc"
else
    # Fallback: compile from the header (single-header library)
    SRC_FILE=""
fi

OUTFILE="$BUILD_DIR/libwebview.so"

if [ -n "$SRC_FILE" ]; then
    g++ "$SRC_FILE" -fPIC -shared \
        -DWEBVIEW_BUILD_SHARED -DWEBVIEW_GTK \
        -std=c++11 \
        -I"$WEBVIEW_SRC/core/include" \
        $(pkg-config --cflags --libs gtk+-3.0 $WEBKIT_PKG) \
        -o "$OUTFILE"
else
    # Single-header mode: create a minimal .cc that includes the header
    WRAPPER="$BUILD_DIR/webview_wrapper.cc"
    cat > "$WRAPPER" << 'WRAPPER_EOF'
#define WEBVIEW_BUILD_SHARED
#define WEBVIEW_GTK
#include "webview.h"
WRAPPER_EOF
    g++ "$WRAPPER" -fPIC -shared \
        -I"$WEBVIEW_SRC" \
        -std=c++11 \
        $(pkg-config --cflags --libs gtk+-3.0 $WEBKIT_PKG) \
        -o "$OUTFILE"
fi

echo "  Built: $OUTFILE"

# --- Install to project ---

mkdir -p "$OUTPUT_DIR"
cp "$OUTFILE" "$OUTPUT_DIR/libwebview.so"
echo "  Installed to: $OUTPUT_DIR/libwebview.so"

# --- Optional system install ---

if [ "$INSTALL_SYSTEM" = true ]; then
    echo "  Installing to /usr/local/lib/ (requires sudo)..."
    sudo cp "$OUTFILE" /usr/local/lib/libwebview.so
    sudo ldconfig 2>/dev/null || true
    echo "  Installed to: /usr/local/lib/libwebview.so"
fi

echo ""
echo "Done! libwebview.so is ready at: $OUTPUT_DIR/libwebview.so"
