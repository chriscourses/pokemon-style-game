#!/usr/bin/env bash
# Kopiert die WASM-Binaries aus dem carnage3d-Repo in die Android-Assets.
# Muss einmalig vor dem ersten Build ausgeführt werden.
#
# Verwendung (aus dem android/-Verzeichnis):
#   ./setup-assets.sh                    # sucht web/ zwei Ebenen höher (Standard)
#   ./setup-assets.sh /pfad/zu/carnage3d/web

set -euo pipefail

WEB_DIR="${1:-../../web}"
ASSETS_DIR="app/src/main/assets"

if [ ! -d "$WEB_DIR" ]; then
    echo "Fehler: '$WEB_DIR' nicht gefunden."
    echo "Verwendung: $0 [pfad/zu/carnage3d/web]"
    exit 1
fi

for f in carnage3D.wasm carnage3D.js carnage3D.data; do
    if [ ! -f "$WEB_DIR/$f" ]; then
        echo "Fehler: '$WEB_DIR/$f' nicht gefunden."
        exit 1
    fi
done

mkdir -p "$ASSETS_DIR"
cp "$WEB_DIR/carnage3D.wasm" "$ASSETS_DIR/"
cp "$WEB_DIR/carnage3D.js"   "$ASSETS_DIR/"
cp "$WEB_DIR/carnage3D.data" "$ASSETS_DIR/"

echo "Assets kopiert nach $ASSETS_DIR/ ($(du -sh "$ASSETS_DIR" | cut -f1))"
echo ""
echo "Jetzt APK bauen:"
echo "  ./gradlew assembleDebug"
echo "  # APK liegt unter: app/build/outputs/apk/debug/app-debug.apk"
