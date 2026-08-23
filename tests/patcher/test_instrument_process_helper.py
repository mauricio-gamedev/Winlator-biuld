#!/usr/bin/env python3
from __future__ import annotations

import subprocess
import sys
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SCRIPT = ROOT / "scripts" / "instrument_process_helper.py"

FIXTURE = '''package com.winlator.core;
import java.lang.reflect.Field;
class ProcessHelper {
    static Object debugCallbacks;
    static int exec(String command) {
        int pid = -1;
        try {
            Object envVars = null;
            java.util.Map<String, String> environment = new java.util.HashMap<>();
            ProcessBuilder processBuilder = new ProcessBuilder(command);
            java.lang.Process process = processBuilder.start();
            Field pidField = process.getClass().getDeclaredField("pid");
            pidField.setAccessible(true);
            pid = pidField.getInt(process);
            pidField.setAccessible(false);
        }
        catch (Exception e) {}
        return pid;
    }
}
'''


def run(path: Path) -> subprocess.CompletedProcess[str]:
    return subprocess.run([sys.executable, str(SCRIPT), str(path)], text=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE, check=False)


def main() -> int:
    with tempfile.TemporaryDirectory(prefix="process-helper-instrument-") as tmp:
        path = Path(tmp) / "ProcessHelper.java"
        path.write_text(FIXTURE, encoding="utf-8")
        first = run(path)
        assert first.returncode == 0, first.stderr
        patched = path.read_text(encoding="utf-8")
        assert patched.count("exec:before-start") == 1
        assert patched.count("exec:env WINELOADERNOEXEC=") == 1
        assert 'environment.get("BOX64_PATH")' in patched
        assert 'environment.get("BOX64_LD_LIBRARY_PATH")' in patched
        assert 'environment.get("WINEPREFIX")' in patched
        assert 'environment.get("PATH")' in patched
        assert 'environment.get("LD_LIBRARY_PATH")' in patched
        assert patched.count("exec:process-created") == 1
        assert patched.count("exec:pid-reflection-start") == 1
        assert patched.count("exec:pid-obtained") == 1
        assert patched.count("exec:exception type=") == 1
        snapshot = patched
        second = run(path)
        assert second.returncode == 0, second.stderr
        assert path.read_text(encoding="utf-8") == snapshot

    with tempfile.TemporaryDirectory(prefix="process-helper-drift-") as tmp:
        path = Path(tmp) / "ProcessHelper.java"
        path.write_text(FIXTURE.replace('getDeclaredField("pid")', 'getDeclaredField("processId")'), encoding="utf-8")
        drift = run(path)
        assert drift.returncode != 0
        assert "expected exactly one upstream occurrence" in drift.stderr

    print("test_instrument_process_helper: all tests passed")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
