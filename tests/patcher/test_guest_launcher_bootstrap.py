#!/usr/bin/env python3
from __future__ import annotations

import subprocess
import sys
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
PATCHER = ROOT / "scripts" / "patch_guest_launcher_bootstrap.py"

SOURCE = '''class GuestProgramLauncherComponent {
    int execGuestProgram() {
        File rootDir = null;
        RootFS rootFS = null;
        EnvVars envVars = new EnvVars();
        envVars.put("LD_LIBRARY_PATH", rootFS.getLibDir().getPath());
        envVars.put("BOX64_LD_LIBRARY_PATH", rootDir+"/lib/x86_64-linux-gnu");
        String guestExecutable = "guest";
        Object terminationCallback = null;
        String command = rootDir+"/usr/local/bin/box64 "+guestExecutable;
        return 0;
    }
}
'''


def run(path: Path):
    return subprocess.run([sys.executable, str(PATCHER), str(path)], text=True,
                          stdout=subprocess.PIPE, stderr=subprocess.PIPE, check=False)


def main() -> int:
    with tempfile.TemporaryDirectory(prefix="guest-launcher-rootfs-loader-") as tmp:
        path = Path(tmp) / "GuestProgramLauncherComponent.java"
        path.write_text(SOURCE, encoding="utf-8")
        first = run(path)
        assert first.returncode == 0, first.stderr
        patched = path.read_text(encoding="utf-8")

        # Native ARM64 loader and Box64 are resolved explicitly.
        assert 'new File(rootDir, "/lib/ld-linux-aarch64.so.1")' in patched
        assert 'new File(rootDir, "/usr/lib/ld-linux-aarch64.so.1")' in patched
        assert 'new File(rootDir, "/usr/local/bin/box64")' in patched
        assert '"/lib/aarch64-linux-gnu"' in patched
        assert '"/usr/lib/aarch64-linux-gnu"' in patched
        assert '"/lib64"' in patched
        assert '"/usr/lib64"' in patched
        assert 'String nativeLibraryPath = nativeLibraryPathBuilder.toString()' in patched
        assert ' --inhibit-cache --library-path ' in patched
        assert 'wine-preloader' not in patched

        # Wine executable and runtime layout follow the AMOD glibc path.
        assert 'trimmedGuestExecutable.equals("wine")' in patched
        assert 'trimmedGuestExecutable.equals("wine64")' in patched
        assert 'String winePath = rootFS.getWinePath()' in patched
        assert 'new File(wineBinDir, "wine64")' in patched
        assert 'new File(wineBinDir, "wine")' in patched
        assert 'new File(wineDllDir, "x86_64-unix")' in patched
        assert 'envVars.put("WINEDLLPATH", wineDllDir.getPath())' in patched
        assert 'envVars.put("WINE_HOST_XDG_CURRENT_DESKTOP", "1")' in patched
        assert 'envVars.put("LD_LIBRARY_PATH", ldLibraryPath)' in patched
        assert 'rootDir+"/usr/lib/x86_64-linux-gnu:"+rootDir+"/lib/x86_64-linux-gnu:"+ldLibraryPath' in patched
        assert 'envVars.put("BOX64_MMAP32", "1")' in patched
        assert 'envVars.put("BOX64_X11GLX", "1")' in patched
        assert 'envVars.put("BOX64_LOG", "1")' in patched

        # AMOD runtime support paths are included in the same patch.
        assert 'envVars.put("FONTCONFIG_PATH", fontConfigDir.getPath())' in patched
        assert 'libandroid-sysvshm.so' in patched
        assert 'envVars.put("LD_PRELOAD", sysvShm.getPath())' in patched

        assert 'launchTarget = wine.getPath()+(wineArgs.isEmpty() ? "" : " "+wineArgs)' in patched
        assert patched.count('terminationCallback.call(-1)') >= 3

        second = run(path)
        assert second.returncode == 0, second.stderr
        assert path.read_text(encoding="utf-8") == patched

    with tempfile.TemporaryDirectory(prefix="guest-launcher-command-drift-") as tmp:
        path = Path(tmp) / "GuestProgramLauncherComponent.java"
        path.write_text(SOURCE.replace('/usr/local/bin/box64 ', '/usr/local/bin/box64-custom '), encoding="utf-8")
        result = run(path)
        assert result.returncode != 0
        assert "rootfs-loader Box64/Wine guest bootstrap" in result.stderr

    print("test_guest_launcher_bootstrap: all tests passed")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
