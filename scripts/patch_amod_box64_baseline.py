#!/usr/bin/env python3
from __future__ import annotations

import sys
from pathlib import Path

DEFAULT_OLD = '    public static final String BOX64 = "0.4.4";'
DEFAULT_NEW = '    public static final String BOX64 = "0.4.1";'

SELECT_OLD = '''        String box64Version = preferences.getString("box64_version", DefaultVersion.BOX64);
        String currentBox64Version = preferences.getString("current_box64_version", "");'''
SELECT_NEW = '''        String box64Version = DefaultVersion.BOX64;
        if (!box64Version.equals(preferences.getString("box64_version", ""))) {
            preferences.edit().putString("box64_version", box64Version).apply();
        }
        String currentBox64Version = preferences.getString("current_box64_version", "");'''


def replace_once(text: str, old: str, new: str, label: str) -> str:
    if new in text:
        return text
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly one occurrence, found {count}")
    return text.replace(old, new, 1)


def main() -> int:
    if len(sys.argv) != 3:
        print("usage: patch_amod_box64_baseline.py <DefaultVersion.java> <GuestProgramLauncherComponent.java>", file=sys.stderr)
        return 2

    default_path = Path(sys.argv[1])
    launcher_path = Path(sys.argv[2])
    if not default_path.is_file() or not launcher_path.is_file():
        print("AMOD Box64 baseline patch input file missing", file=sys.stderr)
        return 1

    try:
        default_text = replace_once(default_path.read_text(encoding="utf-8"), DEFAULT_OLD, DEFAULT_NEW, "DefaultVersion Box64 baseline")
        launcher_text = replace_once(launcher_path.read_text(encoding="utf-8"), SELECT_OLD, SELECT_NEW, "Box64 preference pin")
    except RuntimeError as error:
        print(f"AMOD Box64 baseline patch: {error}", file=sys.stderr)
        return 1

    default_path.write_text(default_text, encoding="utf-8")
    launcher_path.write_text(launcher_text, encoding="utf-8")
    print("patched AMOD Box64 0.4.1 baseline")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
