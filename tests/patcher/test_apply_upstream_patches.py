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

FIXTURES = {
    "app/build.gradle": "applicationId 'com.winlator'\n",
    "app/src/main/AndroidManifest.xml": (
        '<application android:label="@string/app_name">\n'
        '<provider android:authorities="com.winlator.FileProvider"/>\n'
    ),
    "app/src/main/java/com/winlator/core/FileUtils.java": (
        'FileProvider.getUriForFile(activity, "com.winlator.FileProvider", file);\n'
    ),
    "app/src/main/java/com/winlator/core/AppUtils.java": (
        'public static final String INTERNAL_STORAGE = "/data/data/com.winlator/storage";\n'
    ),
    "app/src/main/cpp/winlator/include/winlator.h": (
        '#define APP_CACHE_DIR "/data/data/com.winlator/cache"\n'
    ),
    "app/src/main/cpp/vortekrenderer/include/vortek.h": (
        '#define VORTEK_SERVER_PATH "/data/data/com.winlator/files/rootfs/tmp/.vortek/V0"\n'
    ),
    "app/src/main/cpp/gladiorenderer/include/gladio.h": (
        '#define X11_SERVER_PATH "/data/data/com.winlator/files/rootfs/tmp/.X11-unix/X0"\n'
    ),
}


def write(path: Path, text: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(text, encoding="utf-8")


def make_fixture(base: Path, main_text: str = MAIN_ORIGINAL,
                 settings_text: str = SETTINGS_ORIGINAL) -> tuple[Path, dict[str, Path]]:
    upstream = base / "winlator-app"
    paths: dict[str, Path] = {}

    main = upstream / "app/src/main/java/com/winlator/MainActivity.java"
    settings = upstream / "app/src/main/java/com/winlator/SettingsFragment.java"
    write(main, main_text)
    write(settings, settings_text)
    paths["main"] = main
    paths["settings"] = settings

    for relative, content in FIXTURES.items():
        path = upstream / relative
        write(path, content)
        paths[relative] = path

    return upstream, paths


def run_patcher(upstream: Path) -> subprocess.CompletedProcess[str]:
    return subprocess.run(
        [sys.executable, str(PATCHER), str(upstream)],
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        check=False,
    )


def snapshot(paths: dict[str, Path]) -> dict[str, str]:
    return {key: path.read_text(encoding="utf-8") for key, path in paths.items()}


def test_apply_and_idempotency() -> None:
    with tempfile.TemporaryDirectory(prefix="winlator-patcher-") as tmp:
        upstream, paths = make_fixture(Path(tmp))
        first = run_patcher(upstream)
        assert first.returncode == 0, first.stderr

        main_text = paths["main"].read_text(encoding="utf-8")
        settings_text = paths["settings"].read_text(encoding="utf-8")
        assert main_text.count("WinlatorRootFsMaintenanceController.ensure(this);") == 2
        assert "RootFSInstaller.installIfNeeded(this);" not in main_text
        assert "WinlatorRootFsMaintenanceController.repair((MainActivity)getActivity())" in settings_text

        assert "applicationId 'com.winlator.buildtest'" in paths["app/build.gradle"].read_text(encoding="utf-8")
        manifest = paths["app/src/main/AndroidManifest.xml"].read_text(encoding="utf-8")
        assert 'android:label="Winlator Build Test"' in manifest
        assert 'android:authorities="${applicationId}.FileProvider"' in manifest

        file_utils = paths["app/src/main/java/com/winlator/core/FileUtils.java"].read_text(encoding="utf-8")
        assert 'activity.getPackageName()+".FileProvider"' in file_utils

        app_utils = paths["app/src/main/java/com/winlator/core/AppUtils.java"].read_text(encoding="utf-8")
        assert '/data/data/com.winlator.buildtest/storage' in app_utils

        assert "/data/data/com.winlator.buildtest/cache" in paths[
            "app/src/main/cpp/winlator/include/winlator.h"
        ].read_text(encoding="utf-8")
        assert "/data/data/com.winlator.buildtest/files/rootfs/tmp/.vortek/V0" in paths[
            "app/src/main/cpp/vortekrenderer/include/vortek.h"
        ].read_text(encoding="utf-8")
        assert "/data/data/com.winlator.buildtest/files/rootfs/tmp/.X11-unix/X0" in paths[
            "app/src/main/cpp/gladiorenderer/include/gladio.h"
        ].read_text(encoding="utf-8")

        first_snapshot = snapshot(paths)
        second = run_patcher(upstream)
        assert second.returncode == 0, second.stderr
        assert first_snapshot == snapshot(paths)


def test_upstream_drift_fails_closed() -> None:
    with tempfile.TemporaryDirectory(prefix="winlator-patcher-drift-") as tmp:
        drifted_main = MAIN_ORIGINAL.replace(
            "    void b() { RootFSInstaller.installIfNeeded(this); }\n", ""
        )
        upstream, _ = make_fixture(Path(tmp), main_text=drifted_main)
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
