#!/usr/bin/env sh
set -eu

ROOT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
UPSTREAM_DIR="$ROOT_DIR/third_party/winlator-app"
JAVA_ROOT="$UPSTREAM_DIR/app/src/main/java/com/winlator/build"
PROCESS_HELPER="$UPSTREAM_DIR/app/src/main/java/com/winlator/core/ProcessHelper.java"
GUEST_LAUNCHER="$UPSTREAM_DIR/app/src/main/java/com/winlator/xenvironment/components/GuestProgramLauncherComponent.java"
SESSION_ACTIVITY="$UPSTREAM_DIR/app/src/main/java/com/winlator/XServerDisplayActivity.java"

if [ ! -f "$UPSTREAM_DIR/app/build.gradle" ]; then
    echo "Winlator upstream submodule is not initialized at: $UPSTREAM_DIR" >&2
    echo "Run: git submodule update --init --recursive" >&2
    exit 1
fi

rm -rf "$JAVA_ROOT/engine" "$JAVA_ROOT/integration"
mkdir -p "$JAVA_ROOT/engine" "$JAVA_ROOT/integration"
cp -R "$ROOT_DIR/engine/src/main/java/com/winlator/build/engine/." "$JAVA_ROOT/engine/"
cp -R "$ROOT_DIR/app/integration/src/main/java/com/winlator/build/integration/." "$JAVA_ROOT/integration/"
python3 "$ROOT_DIR/scripts/apply_upstream_patches.py" "$UPSTREAM_DIR"
python3 "$ROOT_DIR/scripts/patch_guest_launcher_bootstrap.py" "$GUEST_LAUNCHER"
python3 "$ROOT_DIR/scripts/instrument_process_helper.py" "$PROCESS_HELPER"
python3 "$ROOT_DIR/scripts/instrument_session_gate.py" "$SESSION_ACTIVITY"
python3 "$ROOT_DIR/scripts/patch_session_gate_live_snapshot.py" "$SESSION_ACTIVITY"
python3 "$ROOT_DIR/scripts/instrument_guest_preflight.py" "$SESSION_ACTIVITY"

echo "Winlator Build overlay prepared in $UPSTREAM_DIR"
