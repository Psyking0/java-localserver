#!/usr/bin/env bash
# build.sh — compile and run WebServ
set -e

SRC_DIR="src"
OUT_DIR="out"
MAIN_CLASS="core.Launcher"
SETTINGS="settings.json"

echo "==> Cleaning output..."
rm -rf "$OUT_DIR"
mkdir -p "$OUT_DIR"

echo "==> Compiling sources..."
find "$SRC_DIR" -name "*.java" | xargs javac -d "$OUT_DIR" -sourcepath "$SRC_DIR"

echo "==> Compilation successful."
echo "==> Starting WebServ (Ctrl+C to stop)..."
java -cp "$OUT_DIR" "$MAIN_CLASS" "${1:-$SETTINGS}"
