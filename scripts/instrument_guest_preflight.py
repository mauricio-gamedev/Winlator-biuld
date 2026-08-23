#!/usr/bin/env python3
from __future__ import annotations

import sys
from pathlib import Path

OLD = '''        sessionGate("07e environment-components-start");
        environment.startEnvironmentComponents();'''

NEW = '''        File sessionRoot = rootFS.getRootDir();
        File sessionLoader = new File(sessionRoot, "/lib/ld-linux-aarch64.so.1");
        if (!sessionLoader.isFile()) sessionLoader = new File(sessionRoot, "/usr/lib/ld-linux-aarch64.so.1");
        File sessionBox64 = new File(sessionRoot, "/usr/local/bin/box64");
        File sessionWine = new File(sessionRoot, rootFS.getWinePath()+"/bin/wine");
        File sessionWine64 = new File(sessionRoot, rootFS.getWinePath()+"/bin/wine64");
        File sessionWineServer = new File(sessionRoot, rootFS.getWinePath()+"/bin/wineserver");
        File sessionWineUnix64 = new File(sessionRoot, rootFS.getWinePath()+"/lib/wine/x86_64-unix");
        File sessionArm64Lib = new File(sessionRoot, "/lib/aarch64-linux-gnu");
        File sessionArm64UsrLib = new File(sessionRoot, "/usr/lib/aarch64-linux-gnu");
        File sessionFontConfig = new File(sessionRoot, "/usr/etc/fonts");
        if (!sessionFontConfig.isDirectory()) sessionFontConfig = new File(sessionRoot, "/etc/fonts");
        File sessionSysvShm = new File(sessionRoot, "/usr/lib/libandroid-sysvshm.so");
        if (!sessionSysvShm.isFile()) sessionSysvShm = new File(sessionRoot, "/lib/libandroid-sysvshm.so");
        File sessionPrefix = new File(sessionRoot.getPath()+RootFS.WINEPREFIX);
        File sessionWindows = new File(sessionPrefix, "drive_c/windows");
        File sessionWinHandler = new File(sessionWindows, "winhandler.exe");
        File sessionWfm = new File(sessionWindows, "wfm.exe");
        sessionGate("07e environment-components-start preflight"
                + " loader=" + sessionLoader.isFile() + "/" + sessionLoader.canExecute()
                + " box64=" + sessionBox64.isFile() + "/" + sessionBox64.canExecute()
                + " arm64lib=" + sessionArm64Lib.isDirectory()
                + " arm64usrlib=" + sessionArm64UsrLib.isDirectory()
                + " wine=" + sessionWine.isFile() + "/" + sessionWine.canExecute()
                + " wine64=" + sessionWine64.isFile() + "/" + sessionWine64.canExecute()
                + " wineserver=" + sessionWineServer.isFile() + "/" + sessionWineServer.canExecute()
                + " wineUnix64=" + sessionWineUnix64.isDirectory()
                + " fontconfig=" + sessionFontConfig.isDirectory()
                + " sysvshm=" + sessionSysvShm.isFile()
                + " prefix=" + sessionPrefix.isDirectory()
                + " windows=" + sessionWindows.isDirectory()
                + " winhandler=" + sessionWinHandler.isFile()
                + " wfm=" + sessionWfm.isFile()
                + " WINEPREFIX=" + envVars.get("WINEPREFIX")
                + " WINELOADERNOEXEC=" + envVars.get("WINELOADERNOEXEC")
                + " BOX64_PATH=" + envVars.get("BOX64_PATH")
                + " BOX64_LD_LIBRARY_PATH=" + envVars.get("BOX64_LD_LIBRARY_PATH")
                + " LD_LIBRARY_PATH=" + envVars.get("LD_LIBRARY_PATH")
                + " LD_PRELOAD=" + envVars.get("LD_PRELOAD")
                + " FONTCONFIG_PATH=" + envVars.get("FONTCONFIG_PATH")
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
