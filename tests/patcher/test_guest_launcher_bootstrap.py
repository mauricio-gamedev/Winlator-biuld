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
        envVars.put("BOX64_LD_LIBRARY_PATH", rootDir+"/lib/x86_64-linux-gnu");
        String guestExecutable = "guest";
        String command = rootDir+"/usr/local/bin/box64 "+guestExecutable;
        return 0;
    }
}
'''


def run(path: Path):
    return subprocess.run([sys.executable, str(PATCHER), str(path)], text=True,
                          stdout=subprocess.PIPE, stderr=subprocess.PIPE, check=False)


def main() -> int:
    with tempfile.TemporaryDirectory(prefix="guest-launcher-bootstrap-") as tmp:
        path = Path(tmp) / "GuestProgramLauncherComponent.java"
        path.write_text(SOURCE, encoding="utf-8")
        first = run(path)
        assert first.returncode == 0, first.stderr
        patched = path.read_text(encoding="utf-8")
        assert 'new File(rootDir, "/lib/ld-linux-aarch64.so.1")' in patched
        assert 'new File(rootDir, "/usr/local/bin/box64")' in patched
        assert 'loader.getPath()+" "+box64.getPath()+" "+guestExecutable' in patched
        assert 'terminationCallback.call(-1)' in patched
        assert 'envVars.put("BOX64_PATH", rootDir+rootFS.getWinePath()+"/bin:"+rootDir+"/usr/local/bin:"+rootDir+"/usr/bin")' in patched
        assert 'envVars.put("BOX64_LOG", "1")' in patched
        assert 'WINELOADERNOEXEC' not in patched
        assert 'BOX64_SHOWSEGV' not in patched
        assert 'BOX64_DLSYM_ERROR' not in patched
        second = run(path)
        assert second.returncode == 0, second.stderr
        assert path.read_text(encoding="utf-8") == patched

    with tempfile.TemporaryDirectory(prefix="guest-launcher-command-drift-") as tmp:
        path = Path(tmp) / "GuestProgramLauncherComponent.java"
        path.write_text(SOURCE.replace('/usr/local/bin/box64 ', '/usr/local/bin/box64-custom '), encoding="utf-8")
        result = run(path)
        assert result.returncode != 0
        assert "guest launcher bootstrap command" in result.stderr

    with tempfile.TemporaryDirectory(prefix="guest-launcher-env-drift-") as tmp:
        path = Path(tmp) / "GuestProgramLauncherComponent.java"
        path.write_text(SOURCE.replace('BOX64_LD_LIBRARY_PATH', 'BOX64_LD_LIBRARY_PATH_CUSTOM'), encoding="utf-8")
        result = run(path)
        assert result.returncode != 0
        assert "guest launcher Wine/Box64 environment" in result.stderr

    print("test_guest_launcher_bootstrap: all tests passed")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
