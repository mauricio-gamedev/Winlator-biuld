#!/usr/bin/env python3
from __future__ import annotations

import sys
from pathlib import Path


def replace_exact(text: str, old: str, new: str, expected: int, label: str) -> str:
    old_count = text.count(old)
    new_count = text.count(new)

    if old_count == 0 and new_count == expected:
        return text
    if old_count != expected:
        raise RuntimeError(
            f"{label}: expected {expected} upstream occurrence(s), found {old_count}; "
            "the pinned upstream may have drifted or the patch is partially applied"
        )
    return text.replace(old, new)


def patch_file(path: Path, replacements: list[tuple[str, str, int, str]]) -> None:
    original = path.read_text(encoding="utf-8")
    updated = original
    for old, new, expected, label in replacements:
        updated = replace_exact(updated, old, new, expected, label)
    if updated != original:
        path.write_text(updated, encoding="utf-8")
        print(f"patched {path}")
    else:
        print(f"already patched {path}")


def main() -> int:
    if len(sys.argv) != 2:
        print("usage: apply_upstream_patches.py <winlator-app-dir>", file=sys.stderr)
        return 2

    upstream = Path(sys.argv[1]).resolve()
    java_root = upstream / "app" / "src" / "main" / "java" / "com" / "winlator"
    main_activity = java_root / "MainActivity.java"
    settings_fragment = java_root / "SettingsFragment.java"

    for path in (main_activity, settings_fragment):
        if not path.is_file():
            raise RuntimeError(f"required upstream file is missing: {path}")

    patch_file(
        main_activity,
        [
            (
                "import com.winlator.xenvironment.RootFSInstaller;",
                "import com.winlator.build.integration.WinlatorRootFsMaintenanceController;",
                1,
                "MainActivity RootFS import",
            ),
            (
                "RootFSInstaller.installIfNeeded(this);",
                "WinlatorRootFsMaintenanceController.ensure(this);",
                2,
                "MainActivity RootFS startup hooks",
            ),
        ],
    )

    patch_file(
        settings_fragment,
        [
            (
                "import com.winlator.xenvironment.RootFSInstaller;",
                "import com.winlator.build.integration.WinlatorRootFsMaintenanceController;",
                1,
                "SettingsFragment RootFS import",
            ),
            (
                "RootFSInstaller.install((MainActivity)getActivity())",
                "WinlatorRootFsMaintenanceController.repair((MainActivity)getActivity())",
                1,
                "SettingsFragment reinstall-system-files hook",
            ),
        ],
    )

    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except RuntimeError as error:
        print(f"upstream patch error: {error}", file=sys.stderr)
        raise SystemExit(1)
