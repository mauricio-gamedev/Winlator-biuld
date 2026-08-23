#!/usr/bin/env sh
set -eu

ROOT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
ROOTFS="$ROOT_DIR/third_party/winlator-app/app/src/main/assets/rootfs.tzst"
BOX64="$ROOT_DIR/third_party/winlator-app/app/src/main/assets/box64/box64-0.4.1.tzst"

for f in "$ROOTFS" "$BOX64"; do
    if [ ! -f "$f" ]; then
        echo "missing packaged runtime asset: $f" >&2
        exit 1
    fi
done

for tool in tar readelf grep head; do
    if ! command -v "$tool" >/dev/null 2>&1; then
        echo "required validation tool is missing: $tool" >&2
        exit 1
    fi
done

TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT
ROOT_LIST="$TMP/rootfs.list"
BOX_LIST="$TMP/box64.list"

tar --zstd -tf "$ROOTFS" > "$ROOT_LIST"
tar --zstd -tf "$BOX64" > "$BOX_LIST"

LOADER_ENTRY=$(grep -E '(^|/)(lib|usr/lib)/ld-linux-aarch64\.so\.1$' "$ROOT_LIST" | head -n 1 || true)
if [ -z "$LOADER_ENTRY" ]; then
    echo "RootFS does not contain an ARM64 glibc loader" >&2
    exit 1
fi

LIBC_ENTRY=$(grep -E '(^|/)(lib|usr/lib)(/aarch64-linux-gnu)?/libc\.so\.6$' "$ROOT_LIST" | head -n 1 || true)
if [ -z "$LIBC_ENTRY" ]; then
    echo "RootFS does not contain an ARM64 libc candidate" >&2
    exit 1
fi

BOX64_ENTRY=$(grep -E '(^|/)usr/local/bin/box64$' "$BOX_LIST" | head -n 1 || true)
if [ -z "$BOX64_ENTRY" ]; then
    echo "AMOD Box64 package does not contain usr/local/bin/box64" >&2
    exit 1
fi

mkdir -p "$TMP/rootfs" "$TMP/box64"
tar --zstd -xf "$ROOTFS" -C "$TMP/rootfs" "$LOADER_ENTRY" "$LIBC_ENTRY"
tar --zstd -xf "$BOX64" -C "$TMP/box64" "$BOX64_ENTRY"

LOADER_FILE="$TMP/rootfs/$LOADER_ENTRY"
LIBC_FILE="$TMP/rootfs/$LIBC_ENTRY"
BOX64_FILE="$TMP/box64/$BOX64_ENTRY"

for spec in "loader:$LOADER_FILE" "libc:$LIBC_FILE" "box64:$BOX64_FILE"; do
    name=${spec%%:*}
    file=${spec#*:}
    machine=$(readelf -h "$file" | awk -F: '/Machine:/{gsub(/^[ \t]+/, "", $2); print $2; exit}')
    echo "$name machine=$machine path=$file"
    echo "$machine" | grep -qi 'AArch64' || {
        echo "$name is not AArch64" >&2
        exit 1
    }
done

INTERP=$(readelf -l "$BOX64_FILE" | sed -n 's/.*Requesting program interpreter: \(.*\)]/\1/p' | head -n 1)
if [ -z "$INTERP" ]; then
    echo "Box64 ELF interpreter was not found" >&2
    exit 1
fi

echo "box64 interpreter=$INTERP"
case "$INTERP" in
    */ld-linux-aarch64.so.1) ;;
    *)
        echo "Box64 interpreter does not match packaged ARM64 glibc loader" >&2
        exit 1
        ;;
esac

echo "== Box64 NEEDED libraries =="
readelf -d "$BOX64_FILE" | grep 'NEEDED' || true

echo "RESULT: RootFS ARM64 loader/libc and AMOD Box64 form a coherent ELF pair"
