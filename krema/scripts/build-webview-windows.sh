#!/usr/bin/env bash
#
# Build webview.dll from source for Windows.
#
# The webview/webview C library wraps WebView2 on Windows.
# This script clones the repo, compiles the DLL, and places it in krema-core/lib/.
#
# Prerequisites:
#   - MSYS2/MinGW or Visual Studio Build Tools with C++ workload
#   - Microsoft Edge WebView2 SDK (NuGet: Microsoft.Web.WebView2)
#   - git
#
# On Windows with MSYS2/MinGW:
#   pacman -S mingw-w64-x86_64-gcc mingw-w64-x86_64-cmake git
#
# On Windows with Visual Studio:
#   Install "Desktop development with C++" workload
#
# Usage:
#   ./scripts/build-webview-windows.sh               # build and install to krema-core/lib/
#   ./scripts/build-webview-windows.sh --clean        # remove build artifacts
#
# Note: This script is intended to run on Windows (Git Bash, MSYS2, or WSL).
# Cross-compilation from macOS/Linux is not supported.
#

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
BUILD_DIR="$PROJECT_DIR/.webview-build"
OUTPUT_DIR="$PROJECT_DIR/krema-core/lib"
WEBVIEW_REPO="https://github.com/webview/webview.git"
WEBVIEW2_VERSION="1.0.2535.41"

# --- Parse args ---

CLEAN=false

for arg in "$@"; do
    case "$arg" in
        --clean)   CLEAN=true ;;
        --help|-h)
            echo "Usage: ./scripts/build-webview-windows.sh [--clean]"
            echo ""
            echo "Builds webview.dll from source and places it in krema-core/lib/."
            echo ""
            echo "Options:"
            echo "  --clean    Remove build artifacts and exit"
            echo ""
            echo "Prerequisites:"
            echo "  MSYS2:  pacman -S mingw-w64-x86_64-gcc mingw-w64-x86_64-cmake git"
            echo "  VS:     Visual Studio Build Tools with C++ workload"
            echo "  WebView2 SDK: downloaded automatically via NuGet"
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

# --- Check platform ---

if [[ "$(uname -s)" != MINGW* ]] && [[ "$(uname -s)" != MSYS* ]] && [[ "$(uname -s)" != CYGWIN* ]]; then
    echo "Warning: This script is intended for Windows (Git Bash, MSYS2, or Cygwin)."
    echo "Cross-compilation from $(uname -s) is not supported."
    echo ""
    echo "To build on Windows:"
    echo "  1. Install MSYS2 or Git Bash"
    echo "  2. Run this script from there"
    exit 1
fi

# --- Check dependencies ---

echo "Checking build dependencies..."

if ! command -v g++ &>/dev/null && ! command -v cl &>/dev/null; then
    echo "Error: C++ compiler not found. Install MinGW-w64 or Visual Studio Build Tools." >&2
    exit 1
fi

echo "  Compiler found."

# --- Clone or update ---

if [ -d "$BUILD_DIR/webview" ]; then
    echo "Updating webview source..."
    (cd "$BUILD_DIR/webview" && git pull --quiet)
else
    echo "Cloning webview/webview..."
    mkdir -p "$BUILD_DIR"
    git clone --quiet --depth 1 "$WEBVIEW_REPO" "$BUILD_DIR/webview"
fi

# --- Download WebView2 SDK ---

WEBVIEW2_DIR="$BUILD_DIR/webview2"
if [ ! -d "$WEBVIEW2_DIR" ]; then
    echo "Downloading WebView2 SDK..."
    mkdir -p "$WEBVIEW2_DIR"
    NUGET_URL="https://www.nuget.org/api/v2/package/Microsoft.Web.WebView2/${WEBVIEW2_VERSION}"
    curl -sL "$NUGET_URL" -o "$BUILD_DIR/webview2.zip"
    unzip -q "$BUILD_DIR/webview2.zip" -d "$WEBVIEW2_DIR"
    rm "$BUILD_DIR/webview2.zip"
    echo "  WebView2 SDK downloaded."
fi

WEBVIEW2_INCLUDE="$WEBVIEW2_DIR/build/native/include"
WEBVIEW2_LIB="$WEBVIEW2_DIR/build/native/x64"

# --- Build ---

echo "Building webview.dll..."

WEBVIEW_SRC="$BUILD_DIR/webview"
OUTFILE="$BUILD_DIR/webview.dll"

# Find the main source file
if [ -f "$WEBVIEW_SRC/webview.cc" ]; then
    SRC_FILE="$WEBVIEW_SRC/webview.cc"
elif [ -f "$WEBVIEW_SRC/core/src/webview.cc" ]; then
    SRC_FILE="$WEBVIEW_SRC/core/src/webview.cc"
else
    SRC_FILE=""
fi

if command -v g++ &>/dev/null; then
    # MinGW build
    if [ -n "$SRC_FILE" ]; then
        g++ "$SRC_FILE" -shared \
            -DWEBVIEW_BUILD_SHARED -DWEBVIEW_MSEDGE \
            -std=c++17 \
            -static-libgcc -static-libstdc++ -Wl,-Bstatic -lwinpthread -Wl,-Bdynamic \
            -I"$WEBVIEW_SRC/core/include" \
            -I"$WEBVIEW2_INCLUDE" \
            -L"$WEBVIEW2_LIB" \
            -lole32 -lcomctl32 -lshlwapi -lversion \
            -o "$OUTFILE"
    else
        # Single-header mode
        WRAPPER="$BUILD_DIR/webview_wrapper.cc"
        cat > "$WRAPPER" << 'WRAPPER_EOF'
#define WEBVIEW_BUILD_SHARED
#define WEBVIEW_MSEDGE
#include "webview.h"
WRAPPER_EOF
        g++ "$WRAPPER" -shared \
            -I"$WEBVIEW_SRC" \
            -I"$WEBVIEW2_INCLUDE" \
            -L"$WEBVIEW2_LIB" \
            -std=c++17 \
            -static-libgcc -static-libstdc++ -Wl,-Bstatic -lwinpthread -Wl,-Bdynamic \
            -lole32 -lcomctl32 -lshlwapi -lversion \
            -o "$OUTFILE"
    fi
else
    echo "Error: MinGW g++ not found. Visual Studio cl.exe build not yet automated." >&2
    echo "To build manually with Visual Studio:"
    echo "  cl /std:c++17 /LD /DWEBVIEW_BUILD_SHARED /DWEBVIEW_MSEDGE \\"
    echo "     /I\"$WEBVIEW2_INCLUDE\" \\"
    echo "     webview.cc /link /LIBPATH:\"$WEBVIEW2_LIB\" /out:webview.dll"
    exit 1
fi

echo "  Built: $OUTFILE"

# --- Install to project ---

mkdir -p "$OUTPUT_DIR"
cp "$OUTFILE" "$OUTPUT_DIR/webview.dll"
echo "  Installed to: $OUTPUT_DIR/webview.dll"

echo ""
echo "Done! webview.dll is ready at: $OUTPUT_DIR/webview.dll"
echo ""
echo "Runtime requirement: Microsoft Edge WebView2 Runtime must be installed."
echo "  Download: https://developer.microsoft.com/en-us/microsoft-edge/webview2/"
