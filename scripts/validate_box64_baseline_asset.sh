#!/usr/bin/env sh
set -eu

ROOT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
ASSET="$ROOT_DIR/third_party/winlator-app/app/src/main/assets/box64/box64-0.4.1.tzst"
TMP_DIR=$(mktemp -d)
trap 'rm -rf "$TMP_DIR"' EXIT

if [ ! -f "$ASSET" ]; then
    echo "Pinned AMOD Box64 asset is missing: $ASSET" >&2
    exit 1
fi

FILES=$(tar --zstd -tf "$ASSET" \
    | sed -e 's#^\./##' -e '/\/$/d' -e '/^$/d' \
    | sort -u)

EXPECTED='usr/local/bin/box64'
if ! printf '%s\n' "$FILES" | grep -Fxq "$EXPECTED"; then
    echo "AMOD Box64 0.4.1 package does not contain $EXPECTED" >&2
    printf '%s\n' "$FILES" >&2
    exit 1
fi

echo "--- AMOD Box64 0.4.1 package files ---"
printf '%s\n' "$FILES"

tar --zstd -xf "$ASSET" -C "$TMP_DIR"
BOX64="$TMP_DIR/usr/local/bin/box64"

if [ ! -f "$BOX64" ]; then
    echo "Box64 binary was not extracted to expected path" >&2
    exit 1
fi

chmod +x "$BOX64"
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

# The AMOD glibc launcher executes Box64 directly from app-private storage.
# A traditional RootFS-only glibc interpreter would reproduce the exact
# Android ENOENT that forced our bad loader wrapper, so reject that pairing.
case "$INTERP" in
    /lib/ld-linux-aarch64.so.1|/lib64/ld-linux-aarch64.so.1|/usr/lib/ld-linux-aarch64.so.1|/usr/lib64/ld-linux-aarch64.so.1)
        echo "AMOD Box64 direct-exec gate FAILED: PT_INTERP=$INTERP requires a RootFS-only loader" >&2
        exit 1
        ;;
esac

echo "AMOD Box64 direct-exec gate passed"
