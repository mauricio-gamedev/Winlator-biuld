#!/usr/bin/env python3
from __future__ import annotations

import sys
from pathlib import Path

OLD_COMMAND = '''        String command = rootDir+"/usr/local/bin/box64 "+guestExecutable;'''
NEW_COMMAND = '''        File loader = new File(rootDir, "/lib/ld-linux-aarch64.so.1");
        File box64 = new File(rootDir, "/usr/local/bin/box64");
        if (!loader.isFile() || !loader.canExecute() || !box64.isFile() || !box64.canExecute()) {
            if (terminationCallback != null) terminationCallback.call(-1);
            return -1;
        }

        String command = loader.getPath()+" "+box64.getPath()+" "+guestExecutable;'''

OLD_ENV = '''        envVars.put("BOX64_LD_LIBRARY_PATH", rootDir+"/lib/x86_64-linux-gnu");'''
NEW_ENV = '''        envVars.put("BOX64_LD_LIBRARY_PATH", rootDir+"/lib/x86_64-linux-gnu");
        envVars.put("BOX64_PATH", rootDir+rootFS.getWinePath()+"/bin:"+rootDir+"/usr/local/bin:"+rootDir+"/usr/bin");
        envVars.put("WINELOADERNOEXEC", "1");
        envVars.put("BOX64_LOG", "2");
        envVars.put("BOX64_SHOWSEGV", "1");
        envVars.put("BOX64_DLSYM_ERROR", "1");'''


def replace_once(text: str, old: str, new: str, label: str) -> str:
    if new in text:
        return text
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly one upstream occurrence, found {count}")
    return text.replace(old, new, 1)


def main() -> int:
    if len(sys.argv) != 2:
        print("usage: patch_guest_launcher_bootstrap.py <GuestProgramLauncherComponent.java>", file=sys.stderr)
        return 2

    path = Path(sys.argv[1])
    if not path.is_file():
        print(f"guest launcher file missing: {path}", file=sys.stderr)
        return 1

    original = path.read_text(encoding="utf-8")
    try:
        updated = replace_once(original, OLD_COMMAND, NEW_COMMAND, "guest launcher bootstrap command")
        updated = replace_once(updated, OLD_ENV, NEW_ENV, "guest launcher Wine/Box64 environment")
    except RuntimeError as error:
        print(f"guest launcher bootstrap: {error}", file=sys.stderr)
        return 1

    if updated != original:
        path.write_text(updated, encoding="utf-8")
        print(f"patched {path}")
    else:
        print(f"already patched {path}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
