#!/usr/bin/env python3
from __future__ import annotations

import subprocess
import sys
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SCRIPT = ROOT / "scripts" / "instrument_process_helper.py"

FIXTURE = '''package com.winlator.core;
import java.io.File;
import java.lang.reflect.Field;
import java.util.Map;
class Callback<T> { void call(T value) {} }
class EnvVars implements Iterable<String> {
    public java.util.Iterator<String> iterator() { return java.util.Collections.<String>emptyList().iterator(); }
    String get(String name) { return ""; }
}
class ProcessHelper {
    static java.util.ArrayList<Callback<String>> debugCallbacks = new java.util.ArrayList<>();
    static String[] splitCommand(String command) { return new String[]{command}; }
    static int exec(String command, EnvVars envVars, File workingDir) {
        int pid = -1;
        try {
            ProcessBuilder processBuilder = (new ProcessBuilder(splitCommand(command))).directory(workingDir);
            if (debugCallbacks.isEmpty()) processBuilder.redirectOutput(new File("/dev/null")).redirectErrorStream(true);

            Map<String, String> environment = processBuilder.environment();
            for (String name : envVars) environment.put(name, envVars.get(name));

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
    with tempfile.TemporaryDirectory(prefix="process-helper-upstream-") as tmp:
        path = Path(tmp) / "ProcessHelper.java"
        path.write_text(FIXTURE, encoding="utf-8")
        first = run(path)
        assert first.returncode == 0, first.stderr
        patched = path.read_text(encoding="utf-8")
        assert 'ProcessBuilder processBuilder' in patched
        assert 'processBuilder.start()' in patched
        assert 'Runtime.getRuntime().exec' not in patched
        assert patched.count("exec:before-start") == 1
        assert patched.count("exec:mode=process-builder") == 1
        assert patched.count("exec:env WINELOADERNOEXEC=") == 1

        for name in [
            "WINLATOR_BOX64_BASELINE",
            "BOX64_PATH",
            "BOX64_LD_LIBRARY_PATH",
            "BOX64_MMAP32",
            "BOX64_X11GLX",
            "BOX64_LOG",
            "WINEPREFIX",
            "PATH",
            "LD_LIBRARY_PATH",
            "LD_PRELOAD",
            "FONTCONFIG_PATH",
            "ANDROID_SYSVSHM_SERVER",
        ]:
            assert f'environment.get("{name}")' in patched

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
