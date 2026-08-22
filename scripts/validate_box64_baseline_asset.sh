#!/usr/bin/env sh
set -eu

ROOT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
ASSET="$ROOT_DIR/third_party/winlator-app/app/src/main/assets/box64/box64-0.4.4.tzst"
ROOTFS_ASSET="$ROOT_DIR/third_party/winlator-app/app/src/main/assets/rootfs.tzst"
TMP_DIR=$(mktemp -d)
trap 'rm -rf "$TMP_DIR"' EXIT

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

tar --zstd -xf "$ASSET" -C "$TMP_DIR"
BOX64="$TMP_DIR/usr/local/bin/box64"

if [ ! -f "$BOX64" ]; then
    echo "Box64 binary was not extracted to expected path" >&2
    exit 1
fi

echo "Box64 baseline asset layout validated: $EXPECTED"
echo "--- Box64 ELF identity ---"
file "$BOX64"
readelf -h "$BOX64" | grep -E 'Class:|Data:|Machine:|Type:' || true

echo "--- Box64 ELF interpreter ---"
INTERP=$(readelf -l "$BOX64" | sed -n 's/.*Requesting program interpreter: \(.*\)]/\1/p')
if [ -n "$INTERP" ]; then
    echo "PT_INTERP=$INTERP"
else
    echo "PT_INTERP=<none>"
fi

echo "--- Box64 dynamic dependencies ---"
readelf -d "$BOX64" | grep -E '\(NEEDED\)|\(RPATH\)|\(RUNPATH\)' || true

if [ -f "$ROOTFS_ASSET" ] && [ -n "$INTERP" ]; then
    ROOTFS_INTERP=${INTERP#/}
    if tar --zstd -tf "$ROOTFS_ASSET" | sed -e 's#^\./##' | grep -Fxq "$ROOTFS_INTERP"; then
        echo "ROOTFS_INTERP_PRESENT=yes path=$INTERP"
    else
        echo "ROOTFS_INTERP_PRESENT=no path=$INTERP"
    fi
fi
