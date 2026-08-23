#!/usr/bin/env python3
from __future__ import annotations

import sys
from pathlib import Path

OLD = '''        sessionGate("07e environment-components-start");
        environment.startEnvironmentComponents();'''

NEW = '''        File sessionRoot = rootFS.getRootDir();
        File sessionWine = new File(sessionRoot, rootFS.getWinePath()+"/bin/wine");
        File sessionWineServer = new File(sessionRoot, rootFS.getWinePath()+"/bin/wineserver");
        File sessionPrefix = new File(sessionRoot.getPath()+RootFS.WINEPREFIX);
        File sessionWindows = new File(sessionPrefix, "drive_c/windows");
        File sessionWinHandler = new File(sessionWindows, "winhandler.exe");
        File sessionWfm = new File(sessionWindows, "wfm.exe");
        sessionGate("07e environment-components-start preflight"
                + " wine=" + sessionWine.isFile() + "/" + sessionWine.canExecute()
                + " wineserver=" + sessionWineServer.isFile() + "/" + sessionWineServer.canExecute()
                + " prefix=" + sessionPrefix.isDirectory()
                + " windows=" + sessionWindows.isDirectory()
                + " winhandler=" + sessionWinHandler.isFile()
                + " wfm=" + sessionWfm.isFile()
                + " WINEPREFIX=" + envVars.get("WINEPREFIX")
                + " WINELOADERNOEXEC=" + envVars.get("WINELOADERNOEXEC")
                + " BOX64_PATH=" + envVars.get("BOX64_PATH")
                + " WINEDEBUG=" + envVars.get("WINEDEBUG"));
        environment.startEnvironmentComponents();'''


def main() -> int:
    if len(sys.argv) != 2:
        print("usage: instrument_guest_preflight.py <XServerDisplayActivity.java>", file=sys.stderr)
        return 2

    path = Path(sys.argv[1])
    if not path.is_file():
        print(f"session activity file missing: {path}", file=sys.stderr)
        return 1

    text = path.read_text(encoding="utf-8")
    if NEW in text:
        print(f"already instrumented guest preflight in {path}")
        return 0

    count = text.count(OLD)
    if count != 1:
        print(f"guest preflight: expected 1 instrumented startup occurrence, found {count}", file=sys.stderr)
        return 1

    path.write_text(text.replace(OLD, NEW, 1), encoding="utf-8")
    print(f"instrumented guest preflight in {path}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
