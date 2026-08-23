#!/usr/bin/env python3
from __future__ import annotations

import sys
from pathlib import Path

# Rootless Android cannot resolve the glibc interpreter requested by the ARM64
# Box64 ELF in Android's host namespace. Launch the RootFS loader explicitly,
# but give that loader an ARM64-only library search path. Keep the x86_64 Wine
# library layout isolated in BOX64_LD_LIBRARY_PATH so the host loader never
# tries to consume guest ELF libraries.
#
# AMOD reference: afeimod/winlator-mod @
# 4ad48931e9aaf77063b71f59f62378521cfa3d95, winlator-glibc.
#
# Effective chain:
#   <rootfs>/lib/ld-linux-aarch64.so.1
#     --inhibit-cache
#     --library-path <rootfs ARM64 library dirs>
#     <rootfs>/usr/local/bin/box64
#     <absolute Wine x86_64 executable> <wine args>
OLD_COMMAND = '''        String command = rootDir+"/usr/local/bin/box64 "+guestExecutable;'''
NEW_COMMAND = '''        File loader = new File(rootDir, "/lib/ld-linux-aarch64.so.1");
        if (!loader.isFile()) loader = new File(rootDir, "/usr/lib/ld-linux-aarch64.so.1");
        File box64 = new File(rootDir, "/usr/local/bin/box64");
        if (!loader.isFile() || !loader.canExecute() || !box64.isFile() || !box64.canExecute()) {
            if (terminationCallback != null) terminationCallback.call(-1);
            return -1;
        }

        String[] nativeLibraryCandidates = new String[] {
                "/lib/aarch64-linux-gnu",
                "/usr/lib/aarch64-linux-gnu",
                "/lib64",
                "/usr/lib64",
                "/lib",
                "/usr/lib"
        };
        StringBuilder nativeLibraryPathBuilder = new StringBuilder();
        for (String candidate : nativeLibraryCandidates) {
            File directory = new File(rootDir, candidate);
            if (!directory.isDirectory()) continue;
            if (nativeLibraryPathBuilder.length() > 0) nativeLibraryPathBuilder.append(':');
            nativeLibraryPathBuilder.append(directory.getPath());
        }
        String nativeLibraryPath = nativeLibraryPathBuilder.toString();
        if (nativeLibraryPath.isEmpty()) {
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

            String ldLibraryPath = rootDir+"/usr/lib";
            if (wineLibDir.isDirectory()) ldLibraryPath = wineLibDir.getPath()+":"+ldLibraryPath;
            if (wineLib64Dir.isDirectory()) ldLibraryPath = wineLib64Dir.getPath()+":"+ldLibraryPath;
            if (wineDllDir.isDirectory()) {
                envVars.put("WINEDLLPATH", wineDllDir.getPath());
                File unix64Dir = new File(wineDllDir, "x86_64-unix");
                if (unix64Dir.isDirectory()) ldLibraryPath = unix64Dir.getPath()+":"+ldLibraryPath;
            }

            envVars.put("WINE_HOST_XDG_CURRENT_DESKTOP", "1");
            envVars.put("LD_LIBRARY_PATH", ldLibraryPath);
            envVars.put("BOX64_LD_LIBRARY_PATH",
                    rootDir+"/usr/lib/x86_64-linux-gnu:"+rootDir+"/lib/x86_64-linux-gnu:"+ldLibraryPath);
            envVars.put("BOX64_MMAP32", "1");
            envVars.put("BOX64_X11GLX", "1");
            envVars.put("BOX64_LOG", "1");

            File fontConfigDir = new File(rootDir, "/usr/etc/fonts");
            if (!fontConfigDir.isDirectory()) fontConfigDir = new File(rootDir, "/etc/fonts");
            if (fontConfigDir.isDirectory()) envVars.put("FONTCONFIG_PATH", fontConfigDir.getPath());

            File sysvShm = new File(rootDir, "/usr/lib/libandroid-sysvshm.so");
            if (!sysvShm.isFile()) sysvShm = new File(rootDir, "/lib/libandroid-sysvshm.so");
            if (sysvShm.isFile()) envVars.put("LD_PRELOAD", sysvShm.getPath());

            launchTarget = wine.getPath()+(wineArgs.isEmpty() ? "" : " "+wineArgs);
        }

        String command = loader.getPath()
                +" --inhibit-cache --library-path "+nativeLibraryPath
                +" "+box64.getPath()+" "+launchTarget;'''


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
        updated = replace_once(original, OLD_COMMAND, NEW_COMMAND, "rootfs-loader Box64/Wine guest bootstrap")
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
