#!/usr/bin/env sh
set -eu

ROOT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
ROOTFS="$ROOT_DIR/third_party/winlator-app/app/src/main/assets/rootfs.tzst"
BOX64_PRIMARY="$ROOT_DIR/third_party/winlator-app/app/src/main/assets/box64/box64-0.4.1.tzst"
BOX64_FALLBACK="$ROOT_DIR/third_party/winlator-app/app/src/main/assets/box64/box64-0.3.8.tzst"

for f in "$ROOTFS" "$BOX64_PRIMARY" "$BOX64_FALLBACK"; do
    if [ ! -f "$f" ]; then
        echo "missing packaged runtime asset: $f" >&2
        exit 1
    fi
done

for tool in tar readelf grep head awk sed; do
    if ! command -v "$tool" >/dev/null 2>&1; then
        echo "required validation tool is missing: $tool" >&2
        exit 1
    fi
done

TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT
ROOT_LIST="$TMP/rootfs.list"

tar --zstd -tf "$ROOTFS" > "$ROOT_LIST"

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

mkdir -p "$TMP/rootfs"
tar --zstd -xf "$ROOTFS" -C "$TMP/rootfs" "$LOADER_ENTRY" "$LIBC_ENTRY"
LOADER_FILE="$TMP/rootfs/$LOADER_ENTRY"
LIBC_FILE="$TMP/rootfs/$LIBC_ENTRY"

machine_of() {
    readelf -h "$1" | awk -F: '/Machine:/{gsub(/^[ \t]+/, "", $2); print $2; exit}'
}

for spec in "loader:$LOADER_FILE" "libc:$LIBC_FILE"; do
    name=${spec%%:*}
    file=${spec#*:}
    machine=$(machine_of "$file")
    echo "$name machine=$machine path=$file"
    echo "$machine" | grep -qi 'AArch64' || {
        echo "$name is not AArch64" >&2
        exit 1
    }
done

validate_box64() {
    label=$1
    archive=$2
    outdir="$TMP/$label"
    list="$TMP/$label.list"
    mkdir -p "$outdir"
    tar --zstd -tf "$archive" > "$list"
    entry=$(grep -E '(^|/)usr/local/bin/box64$' "$list" | head -n 1 || true)
    if [ -z "$entry" ]; then
        echo "$label package does not contain usr/local/bin/box64" >&2
        exit 1
    fi

    tar --zstd -xf "$archive" -C "$outdir" "$entry"
    file="$outdir/$entry"
    machine=$(machine_of "$file")
    echo "$label machine=$machine path=$file"
    echo "$machine" | grep -qi 'AArch64' || {
        echo "$label is not AArch64" >&2
        exit 1
    }

    interp=$(readelf -l "$file" | sed -n 's/.*Requesting program interpreter: \(.*\)]/\1/p' | head -n 1)
    if [ -z "$interp" ]; then
        echo "$label ELF interpreter was not found" >&2
        exit 1
    fi
    echo "$label interpreter=$interp"
    case "$interp" in
        */ld-linux-aarch64.so.1) ;;
        *)
            echo "$label interpreter does not match packaged ARM64 glibc loader" >&2
            exit 1
            ;;
    esac

    echo "== $label NEEDED libraries =="
    readelf -d "$file" | grep 'NEEDED' || true
}

validate_box64 box64-primary "$BOX64_PRIMARY"
validate_box64 box64-fallback "$BOX64_FALLBACK"

echo "RESULT: RootFS loader/libc and both pinned AMOD Box64 binaries form coherent AArch64 ELF pairs"
