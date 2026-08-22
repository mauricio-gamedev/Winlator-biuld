#!/usr/bin/env sh
set -eu

ROOT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
UPSTREAM_DIR="$ROOT_DIR/third_party/winlator-app"
JAVA_ROOT="$UPSTREAM_DIR/app/src/main/java/com/winlator/build"

if [ ! -f "$UPSTREAM_DIR/app/build.gradle" ]; then
    echo "Winlator upstream submodule is not initialized at: $UPSTREAM_DIR" >&2
    echo "Run: git submodule update --init --recursive" >&2
    exit 1
fi

rm -rf "$JAVA_ROOT/engine" "$JAVA_ROOT/integration"
mkdir -p "$JAVA_ROOT/engine" "$JAVA_ROOT/integration"
cp -R "$ROOT_DIR/engine/src/main/java/com/winlator/build/engine/." "$JAVA_ROOT/engine/"
cp -R "$ROOT_DIR/app/integration/src/main/java/com/winlator/build/integration/." "$JAVA_ROOT/integration/"

echo "Winlator Build overlay prepared in $UPSTREAM_DIR"
