#!/bin/sh
set -eu

ASSETS_DIR="third_party/winlator-app/app/src/main/assets"
found_archive=0
found_wine=0
found_preloader=0

echo "== Packaged Wine loader layout =="
for archive in \
  "$ASSETS_DIR/rootfs.tzst" \
  "$ASSETS_DIR/rootfs_patches.tzst"
do
  [ -f "$archive" ] || continue
  found_archive=1
  echo "-- $(basename "$archive") --"

  # Keep only executable loader/server paths. Avoid broad /wine/ matches,
  # which previously flooded the CI log with fonts/NLS and hid the paths
  # we actually need to diagnose runtime startup.
  matches="$(tar --zstd -tf "$archive" 2>/dev/null | grep -E '(^|/)(wine|wine64|wineserver|wine-preloader|wine64-preloader)$' || true)"

  if [ -n "$matches" ]; then
    printf '%s\n' "$matches"
  else
    echo "(no Wine loader/server paths in this archive)"
  fi

  if printf '%s\n' "$matches" | grep -Eq '(^|/)wine$'; then
    found_wine=1
  fi
  if printf '%s\n' "$matches" | grep -Eq '(^|/)(wine-preloader|wine64-preloader)$'; then
    found_preloader=1
  fi
done

if [ "$found_archive" -ne 1 ]; then
  echo "ERROR: no pinned rootfs archive was found" >&2
  exit 2
fi

if [ "$found_wine" -ne 1 ]; then
  echo "ERROR: packaged wine executable was not found in inspected archives" >&2
  exit 3
fi

if [ "$found_preloader" -eq 1 ]; then
  echo "RESULT: a packaged Wine preloader exists; exact path(s) printed above"
else
  echo "RESULT: NO packaged wine-preloader or wine64-preloader exists"
fi
