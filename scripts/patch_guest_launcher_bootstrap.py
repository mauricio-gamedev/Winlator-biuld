#!/usr/bin/env python3
from __future__ import annotations

import sys
from pathlib import Path

OLD = '''        String command = rootDir+"/usr/local/bin/box64 "+guestExecutable;'''
NEW = '''        File loader = new File(rootDir, "/lib/ld-linux-aarch64.so.1");
        File box64 = new File(rootDir, "/usr/local/bin/box64");
        if (!loader.isFile() || !loader.canExecute() || !box64.isFile() || !box64.canExecute()) {
            if (terminationCallback != null) terminationCallback.call(-1);
            return -1;
        }

        String command = loader.getPath()+" "+box64.getPath()+" "+guestExecutable;'''


def main() -> int:
    if len(sys.argv) != 2:
        print("usage: patch_guest_launcher_bootstrap.py <GuestProgramLauncherComponent.java>", file=sys.stderr)
        return 2

    path = Path(sys.argv[1])
    if not path.is_file():
        print(f"guest launcher file missing: {path}", file=sys.stderr)
        return 1

    text = path.read_text(encoding="utf-8")
    if NEW in text:
        if OLD in text.replace(NEW, ""):
            print("guest launcher bootstrap patch is in an ambiguous partial state", file=sys.stderr)
            return 1
        print(f"already patched {path}")
        return 0

    count = text.count(OLD)
    if count != 1:
        print(f"guest launcher bootstrap: expected 1 upstream command occurrence, found {count}", file=sys.stderr)
        return 1

    path.write_text(text.replace(OLD, NEW, 1), encoding="utf-8")
    print(f"patched {path}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
