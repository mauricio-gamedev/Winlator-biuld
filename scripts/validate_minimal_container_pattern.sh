#!/usr/bin/env sh
set -eu

ROOT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
ASSET="$ROOT_DIR/third_party/winlator-app/app/src/main/assets/container_pattern.tzst"

if [ ! -f "$ASSET" ]; then
    echo "Pinned container pattern asset is missing: $ASSET" >&2
    exit 1
fi

FILES=$(tar --zstd -tf "$ASSET" | sed -e 's#^\./##')

require_path() {
    path="$1"
    if ! printf '%s\n' "$FILES" | grep -Fxq "$path"; then
        echo "Required container pattern entry is missing: $path" >&2
        exit 1
    fi
}

require_path ".wine/user.reg"
require_path ".wine/system.reg"

if ! printf '%s\n' "$FILES" | grep -Eq '^\.wine/drive_c/windows/system32(/|$)'; then
    echo "Required container pattern system32 tree is missing" >&2
    exit 1
fi

if ! printf '%s\n' "$FILES" | grep -Eq '^\.wine/drive_c/windows(/|$)'; then
    echo "Required container pattern Windows tree is missing" >&2
    exit 1
fi

echo "Minimal container pattern prerequisites validated"
