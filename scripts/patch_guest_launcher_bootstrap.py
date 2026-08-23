#!/usr/bin/env python3
from __future__ import annotations

import sys
from pathlib import Path

# AMOD's glibc launcher resolves x86_64 Wine and its runtime libraries
# explicitly and hands Wine to Box64 without forcing wine-preloader.
# Our pinned upstream Box64 package is a glibc ARM64 executable. On Android,
# executing that Box64 path directly can fail with ENOENT when its PT_INTERP
# is not visible in Android's host namespace. Therefore this patch keeps the
# AMOD-style Box64 -> Wine handoff, but starts Box64 through the matching
# ARM64 dynamic loader inside the RootFS.
# Reference: afeimod/winlator-mod, branch winlator-glibc,
# GlibcProgramLauncherComponent.java.
OLD_COMMAND = '''        String command = rootDir+"/usr/local/bin/box64 "+guestExecutable;'''
NEW_COMMAND = '''        File box64 = new File(rootDir, "/usr/local/bin/box64");
        File loader = new File(rootDir, "/lib/ld-linux-aarch64.so.1");
        if (!loader.isFile()) loader = new File(rootDir, "/lib64/ld-linux-aarch64.so.1");
        if (!loader.isFile()) loader = new File(rootDir, "/usr/lib/ld-linux-aarch64.so.1");
        if (!loader.isFile()) loader = new File(rootDir, "/usr/lib64/ld-linux-aarch64.so.1");

        if (!box64.isFile() || !box64.canExecute() || !loader.isFile() || !loader.canExecute()) {
            if (terminationCallback != null) terminationCallback.call(-1);
            return -1;
        }

        String launchTarget = guestExecutable;
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

            String box64LdLibraryPath = rootDir+"/lib/x86_64-linux-gnu:"
                    +rootDir+"/usr/lib/x86_64-linux-gnu";
            if (wineLibDir.isDirectory()) box64LdLibraryPath = wineLibDir.getPath()+":"+box64LdLibraryPath;
            if (wineLib64Dir.isDirectory()) box64LdLibraryPath = wineLib64Dir.getPath()+":"+box64LdLibraryPath;
            if (wineDllDir.isDirectory()) {
                envVars.put("WINEDLLPATH", wineDllDir.getPath());
                File unix64Dir = new File(wineDllDir, "x86_64-unix");
                if (unix64Dir.isDirectory()) box64LdLibraryPath = unix64Dir.getPath()+":"+box64LdLibraryPath;
            }

            // Keep LD_LIBRARY_PATH native for the ARM64 loader/Box64 process.
            // x86_64 guest libraries belong in BOX64_LD_LIBRARY_PATH.
            envVars.put("BOX64_LD_LIBRARY_PATH", box64LdLibraryPath);
            envVars.put("BOX64_MMAP32", "1");

            launchTarget = wine.getPath()+(wineArgs.isEmpty() ? "" : " "+wineArgs);
        }

        String command = loader.getPath()+" "+box64.getPath()+" "+launchTarget;'''


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
        updated = replace_once(original, OLD_COMMAND, NEW_COMMAND, "RootFS-loader Box64 guest bootstrap")
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
