#!/usr/bin/env sh
set -eu

ROOT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
ASSET="$ROOT_DIR/third_party/winlator-app/app/src/main/assets/box64/box64-0.4.4.tzst"

if [ ! -f "$ASSET" ]; then
    echo "Pinned Box64 asset is missing: $ASSET" >&2
    exit 1
fi

FILES=$(tar --zstd -tf "$ASSET" \
    | sed -e 's#^\./##' -e '/\/$/d' -e '/^$/d' \
    | sort -u)

EXPECTED='usr/local/bin/box64'
if [ "$FILES" != "$EXPECTED" ]; then
    echo "Unexpected Box64 0.4.4 package file layout:" >&2
    printf '%s\n' "$FILES" >&2
    echo "Expected only: $EXPECTED" >&2
    exit 1
fi

echo "Box64 baseline asset layout validated: $EXPECTED"
