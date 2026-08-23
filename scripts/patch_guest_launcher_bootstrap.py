#!/usr/bin/env python3
from __future__ import annotations

import sys
from pathlib import Path

# AMOD's glibc launcher keeps x86_64 Wine under Box64 and resolves the Wine
# executable and runtime libraries explicitly. It does not force the ARM64
# loader or wine-preloader into the x86_64 Wine command line.
# Reference: afeimod/winlator-mod, branch winlator-glibc,
# GlibcProgramLauncherComponent.java.
OLD_COMMAND = '''        String command = rootDir+"/usr/local/bin/box64 "+guestExecutable;'''
NEW_COMMAND = '''        String launchTarget = guestExecutable;
        String trimmedGuestExecutable = guestExecutable != null ? guestExecutable.trim() : "";
        boolean launchesWine = trimmedGuestExecutable.equals("wine")
                || trimmedGuestExecutable.startsWith("wine ")
                || trimmedGuestExecutable.equals("wine64")
                || trimmedGuestExecutable.startsWith("wine64 ");

        if (launchesWine) {
            String wineArgs;
            if (trimmedGuestExecutable.equals("wine") || trimmedGuestExecutable.equals("wine64")) {
                wineArgs = "";
            }
            else if (trimmedGuestExecutable.startsWith("wine64 ")) {
                wineArgs = trimmedGuestExecutable.substring(7).trim();
            }
            else {
                wineArgs = trimmedGuestExecutable.substring(5).trim();
            }

            String winePath = rootFS.getWinePath();
            if (winePath.startsWith("/")) winePath = winePath.substring(1);
            File wineDir = new File(rootDir, winePath);
            File wineBinDir = new File(wineDir, "bin");
            File wine = new File(wineBinDir, "wine64");
            if (!wine.isFile()) wine = new File(wineBinDir, "wine");
            if (!wine.isFile() || !wine.canExecute()) {
                if (terminationCallback != null) terminationCallback.call(-1);
                return -1;
            }

            File wineLibDir = new File(wineDir, "lib");
            File wineLib64Dir = new File(wineDir, "lib64");
            File wineDllDir = new File(wineLibDir, "wine");
            if (!wineDllDir.isDirectory()) wineDllDir = new File(wineLib64Dir, "wine");

            String ldLibraryPath = rootFS.getLibDir().getPath();
            if (wineLibDir.isDirectory()) ldLibraryPath = wineLibDir.getPath()+":"+ldLibraryPath;
            if (wineLib64Dir.isDirectory()) ldLibraryPath = wineLib64Dir.getPath()+":"+ldLibraryPath;
            if (wineDllDir.isDirectory()) {
                envVars.put("WINEDLLPATH", wineDllDir.getPath());
                File unix64Dir = new File(wineDllDir, "x86_64-unix");
                if (unix64Dir.isDirectory()) ldLibraryPath = unix64Dir.getPath()+":"+ldLibraryPath;
            }

            envVars.put("LD_LIBRARY_PATH", ldLibraryPath);
            envVars.put("BOX64_LD_LIBRARY_PATH",
                    rootDir+"/lib/x86_64-linux-gnu:"+rootDir+"/usr/lib/x86_64-linux-gnu:"+ldLibraryPath);
            envVars.put("BOX64_MMAP32", "1");

            launchTarget = wine.getPath()+(wineArgs.isEmpty() ? "" : " "+wineArgs);
        }

        String command = rootDir+"/usr/local/bin/box64 "+launchTarget;'''


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
        updated = replace_once(original, OLD_COMMAND, NEW_COMMAND, "AMOD-style guest launcher bootstrap")
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
