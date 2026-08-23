#!/usr/bin/env python3
from __future__ import annotations

import subprocess
import sys
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
PATCHER = ROOT / "scripts" / "patch_amod_box64_baseline.py"

DEFAULT_SOURCE = '''public abstract class DefaultVersion {
    public static final String BOX64 = "0.4.4";
}
'''

LAUNCHER_SOURCE = '''class GuestProgramLauncherComponent {
    void extractBox64File() {
        SharedPreferences preferences = null;
        String box64Version = preferences.getString("box64_version", DefaultVersion.BOX64);
        String currentBox64Version = preferences.getString("current_box64_version", "");
    }
}
'''


def run(default_path: Path, launcher_path: Path):
    return subprocess.run(
        [sys.executable, str(PATCHER), str(default_path), str(launcher_path)],
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        check=False,
    )


def main() -> int:
    with tempfile.TemporaryDirectory(prefix="amod-box64-baseline-") as tmp:
        tmp_path = Path(tmp)
        default_path = tmp_path / "DefaultVersion.java"
        launcher_path = tmp_path / "GuestProgramLauncherComponent.java"
        default_path.write_text(DEFAULT_SOURCE, encoding="utf-8")
        launcher_path.write_text(LAUNCHER_SOURCE, encoding="utf-8")

        result = run(default_path, launcher_path)
        assert result.returncode == 0, result.stderr
        default_text = default_path.read_text(encoding="utf-8")
        launcher_text = launcher_path.read_text(encoding="utf-8")

        assert 'BOX64 = "0.4.1"' in default_text
        assert 'String box64Version = DefaultVersion.BOX64;' in launcher_text
        assert 'preferences.edit().putString("box64_version", box64Version).apply();' in launcher_text

        snapshot_default = default_text
        snapshot_launcher = launcher_text
        second = run(default_path, launcher_path)
        assert second.returncode == 0, second.stderr
        assert default_path.read_text(encoding="utf-8") == snapshot_default
        assert launcher_path.read_text(encoding="utf-8") == snapshot_launcher

    print("test_amod_box64_baseline: all tests passed")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
