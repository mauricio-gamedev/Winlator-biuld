#!/usr/bin/env sh
set -eu

ROOT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
UPSTREAM_DIR="$ROOT_DIR/third_party/winlator-app"
TARGET_DIR="$UPSTREAM_DIR/app/src/main/assets/box64"
TARGET="$TARGET_DIR/box64-0.3.8.tzst"

AMOD_COMMIT="4ad48931e9aaf77063b71f59f62378521cfa3d95"
AMOD_BLOB_SHA="827e4af1e4ea9b7160b3461656eb56fe83c75a20"
AMOD_URL="https://raw.githubusercontent.com/afeimod/winlator-mod/$AMOD_COMMIT/app/src/main/assets/box64/box64-0.3.8.tzst"

if [ ! -d "$TARGET_DIR" ]; then
    echo "Winlator upstream Box64 asset directory is missing: $TARGET_DIR" >&2
    exit 1
fi

TMP=$(mktemp)
trap 'rm -f "$TMP"' EXIT

if [ -f "$TARGET" ]; then
    ACTUAL=$(git hash-object "$TARGET")
    if [ "$ACTUAL" = "$AMOD_BLOB_SHA" ]; then
        echo "AMOD Box64 0.3.8 fallback already materialized and verified"
        exit 0
    fi
fi

echo "Downloading pinned AMOD Box64 0.3.8 fallback from commit $AMOD_COMMIT"
curl --fail --location --retry 3 --retry-delay 2 --output "$TMP" "$AMOD_URL"

ACTUAL=$(git hash-object "$TMP")
if [ "$ACTUAL" != "$AMOD_BLOB_SHA" ]; then
    echo "AMOD Box64 0.3.8 fallback blob verification failed" >&2
    echo "expected=$AMOD_BLOB_SHA" >&2
    echo "actual=$ACTUAL" >&2
    exit 1
fi

mv "$TMP" "$TARGET"
trap - EXIT

echo "AMOD Box64 0.3.8 fallback materialized: $TARGET"
echo "git-blob-sha=$AMOD_BLOB_SHA"
