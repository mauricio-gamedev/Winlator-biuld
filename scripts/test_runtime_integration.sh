#!/usr/bin/env sh
set -eu

ROOT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
OUT_DIR="$ROOT_DIR/.runtime-integration-test-out"
SOURCES_FILE="$OUT_DIR/sources.txt"

rm -rf "$OUT_DIR"
mkdir -p "$OUT_DIR/classes"

find "$ROOT_DIR/engine/src/main/java" -name '*.java' -print > "$SOURCES_FILE"
echo "$ROOT_DIR/app/integration/src/main/java/com/winlator/build/integration/ContainerRuntimeAdapter.java" >> "$SOURCES_FILE"
echo "$ROOT_DIR/app/integration/src/main/java/com/winlator/build/integration/ContainerRuntimePlanApplier.java" >> "$SOURCES_FILE"
echo "$ROOT_DIR/app/integration/src/main/java/com/winlator/build/integration/WinlatorRuntimeBaseProbe.java" >> "$SOURCES_FILE"
echo "$ROOT_DIR/app/integration/src/main/java/com/winlator/build/integration/WinlatorRuntimeInventory.java" >> "$SOURCES_FILE"
find "$ROOT_DIR/tests/integration/stubs" -name '*.java' -print >> "$SOURCES_FILE"
find "$ROOT_DIR/tests/integration/java" -name '*.java' -print >> "$SOURCES_FILE"

javac -source 8 -target 8 -d "$OUT_DIR/classes" @"$SOURCES_FILE"
java -cp "$OUT_DIR/classes" com.winlator.build.integration.ContainerRuntimeAdapterSelfTest
java -cp "$OUT_DIR/classes" com.winlator.build.integration.WinlatorRuntimeBaseProbeSelfTest

rm -rf "$OUT_DIR"
