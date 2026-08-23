#!/usr/bin/env python3
from __future__ import annotations

import subprocess
import sys
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
PATCHER = ROOT / "scripts" / "patch_box64_bootstrap_fallback.py"

SOURCE = '''class GuestProgramLauncherComponent {
    private static final Object lock = new Object();
    private Object pid;
    private Object envVars;
    private Object terminationCallback;
    private Object environment;

    public void start() {
        synchronized (lock) {
            stop();
            extractBox64File();
            copyDefaultBox64RCFile();
            pid = execGuestProgram();
        }
    }

    private int execGuestProgram() {
        RootFS rootFS = environment.getRootFS();
        File rootDir = rootFS.getRootDir();
        EnvVars envVars = new EnvVars();
        if (this.envVars != null) envVars.putAll(this.envVars);

        File shmDir = new File(rootDir, "/tmp/shm");
        String command = "command";
        return ProcessHelper.exec(command, envVars, rootDir, (status) -> {
            synchronized (lock) {
                pid = -1;
            }
            if (terminationCallback != null) terminationCallback.call(status);
        });
    }

    private void extractBox64File() {
    }

    private void copyDefaultBox64RCFile() {}
    private void stop() {}
}
'''


def run(path: Path):
    return subprocess.run([sys.executable, str(PATCHER), str(path)], text=True,
                          stdout=subprocess.PIPE, stderr=subprocess.PIPE, check=False)


def main() -> int:
    with tempfile.TemporaryDirectory(prefix="box64-bootstrap-fallback-") as tmp:
        path = Path(tmp) / "GuestProgramLauncherComponent.java"
        path.write_text(SOURCE, encoding="utf-8")
        first = run(path)
        assert first.returncode == 0, first.stderr
        patched = path.read_text(encoding="utf-8")

        assert 'BOOTSTRAP_FALLBACK_WINDOW_MS = 15000L' in patched
        assert 'bootstrapFallbackAttempted = false' in patched
        assert 'WINLATOR_BOX64_BASELINE' in patched
        assert '"0.3.8-fallback"' in patched
        assert 'status != 0' in patched
        assert 'System.currentTimeMillis() - bootstrapLaunchStartedAt' in patched
        assert 'extractBootstrapFallbackBox64()' in patched
        assert 'GeneralComponents.extractFile(GeneralComponents.Type.BOX64, context, "0.3.8", DefaultVersion.BOX64)' in patched
        assert 'putString("current_box64_version", "0.3.8-fallback")' in patched
        assert 'if (pid != -1) return;' in patched

        snapshot = patched
        second = run(path)
        assert second.returncode == 0, second.stderr
        assert path.read_text(encoding="utf-8") == snapshot

    with tempfile.TemporaryDirectory(prefix="box64-bootstrap-fallback-drift-") as tmp:
        path = Path(tmp) / "GuestProgramLauncherComponent.java"
        path.write_text(SOURCE.replace('private static final Object lock = new Object();', 'private static final Object mutex = new Object();'), encoding="utf-8")
        result = run(path)
        assert result.returncode != 0
        assert "fallback fields" in result.stderr

    print("test_box64_bootstrap_fallback: all tests passed")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
