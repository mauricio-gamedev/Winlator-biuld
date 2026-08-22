#!/usr/bin/env python3
from __future__ import annotations

import subprocess
import sys
import tempfile
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
PATCHER = ROOT / "scripts" / "apply_upstream_patches.py"

MAIN_ORIGINAL = """package com.winlator;
import com.winlator.xenvironment.RootFSInstaller;
class MainActivity {
    void a() { RootFSInstaller.installIfNeeded(this); }
    void b() { RootFSInstaller.installIfNeeded(this); }
}
"""

SETTINGS_ORIGINAL = """package com.winlator;
import com.winlator.xenvironment.RootFSInstaller;
class SettingsFragment {
    void x() { RootFSInstaller.install((MainActivity)getActivity()); }
    Object getActivity() { return null; }
}
"""


def make_fixture(base: Path, main_text: str = MAIN_ORIGINAL,
                 settings_text: str = SETTINGS_ORIGINAL) -> tuple[Path, Path, Path]:
    upstream = base / "winlator-app"
    java_root = upstream / "app" / "src" / "main" / "java" / "com" / "winlator"
    java_root.mkdir(parents=True)
    main = java_root / "MainActivity.java"
    settings = java_root / "SettingsFragment.java"
    main.write_text(main_text, encoding="utf-8")
    settings.write_text(settings_text, encoding="utf-8")
    return upstream, main, settings


def run_patcher(upstream: Path) -> subprocess.CompletedProcess[str]:
    return subprocess.run(
        [sys.executable, str(PATCHER), str(upstream)],
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        check=False,
    )


def test_apply_and_idempotency() -> None:
    with tempfile.TemporaryDirectory(prefix="winlator-patcher-") as tmp:
        upstream, main, settings = make_fixture(Path(tmp))
        first = run_patcher(upstream)
        assert first.returncode == 0, first.stderr

        main_text = main.read_text(encoding="utf-8")
        settings_text = settings.read_text(encoding="utf-8")
        assert main_text.count("WinlatorRootFsMaintenanceController.ensure(this);") == 2
        assert "RootFSInstaller.installIfNeeded(this);" not in main_text
        assert "WinlatorRootFsMaintenanceController.repair((MainActivity)getActivity())" in settings_text

        snapshot = (main_text, settings_text)
        second = run_patcher(upstream)
        assert second.returncode == 0, second.stderr
        assert snapshot == (
            main.read_text(encoding="utf-8"),
            settings.read_text(encoding="utf-8"),
        )


def test_upstream_drift_fails_closed() -> None:
    with tempfile.TemporaryDirectory(prefix="winlator-patcher-drift-") as tmp:
        drifted_main = MAIN_ORIGINAL.replace(
            "    void b() { RootFSInstaller.installIfNeeded(this); }\n", ""
        )
        upstream, _, _ = make_fixture(Path(tmp), main_text=drifted_main)
        result = run_patcher(upstream)
        assert result.returncode != 0
        assert "expected 2 upstream occurrence" in result.stderr


def main() -> int:
    test_apply_and_idempotency()
    test_upstream_drift_fails_closed()
    print("test_apply_upstream_patches: all tests passed")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
