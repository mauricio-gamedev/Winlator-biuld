#!/usr/bin/env python3
from __future__ import annotations

import sys
from pathlib import Path

VALIDATION_APPLICATION_ID = "com.winlator.buildtest"


def replace_exact(text: str, old: str, new: str, expected: int, label: str) -> str:
    old_count = text.count(old)
    new_count = text.count(new)

    if old_count == 0 and new_count == expected:
        return text
    if old_count != expected:
        raise RuntimeError(
            f"{label}: expected {expected} upstream occurrence(s), found {old_count}; "
            "the pinned upstream may have drifted or the patch is partially applied"
        )
    return text.replace(old, new)


def patch_file(path: Path, replacements: list[tuple[str, str, int, str]]) -> None:
    if not path.is_file():
        raise RuntimeError(f"required upstream file is missing: {path}")

    original = path.read_text(encoding="utf-8")
    updated = original
    for old, new, expected, label in replacements:
        updated = replace_exact(updated, old, new, expected, label)
    if updated != original:
        path.write_text(updated, encoding="utf-8")
        print(f"patched {path}")
    else:
        print(f"already patched {path}")


def main() -> int:
    if len(sys.argv) != 2:
        print("usage: apply_upstream_patches.py <winlator-app-dir>", file=sys.stderr)
        return 2

    upstream = Path(sys.argv[1]).resolve()
    app_root = upstream / "app"
    java_root = app_root / "src" / "main" / "java" / "com" / "winlator"
    cpp_root = app_root / "src" / "main" / "cpp"

    patch_file(
        java_root / "MainActivity.java",
        [
            (
                "import com.winlator.xenvironment.RootFSInstaller;",
                "import com.winlator.build.integration.WinlatorRootFsMaintenanceController;",
                1,
                "MainActivity RootFS import",
            ),
            (
                "RootFSInstaller.installIfNeeded(this);",
                "WinlatorRootFsMaintenanceController.ensure(this);",
                2,
                "MainActivity RootFS startup hooks",
            ),
        ],
    )

    patch_file(
        java_root / "SettingsFragment.java",
        [
            (
                "import com.winlator.xenvironment.RootFSInstaller;",
                "import com.winlator.build.integration.WinlatorRootFsMaintenanceController;",
                1,
                "SettingsFragment RootFS import",
            ),
            (
                "RootFSInstaller.install((MainActivity)getActivity())",
                "WinlatorRootFsMaintenanceController.repair((MainActivity)getActivity())",
                1,
                "SettingsFragment reinstall-system-files hook",
            ),
        ],
    )

    # Validation builds must install beside the official Winlator. Keep the Java/JNI
    # namespace unchanged, but isolate the Android application id and every runtime
    # path/authority that upstream hard-codes to com.winlator.
    patch_file(
        app_root / "build.gradle",
        [
            (
                "applicationId 'com.winlator'",
                f"applicationId '{VALIDATION_APPLICATION_ID}'",
                1,
                "validation application id",
            ),
        ],
    )

    patch_file(
        app_root / "src" / "main" / "AndroidManifest.xml",
        [
            (
                'android:label="@string/app_name"',
                'android:label="Winlator Build Test"',
                1,
                "validation app label",
            ),
            (
                'android:authorities="com.winlator.FileProvider"',
                'android:authorities="${applicationId}.FileProvider"',
                1,
                "FileProvider authority",
            ),
        ],
    )

    patch_file(
        java_root / "core" / "FileUtils.java",
        [
            (
                'FileProvider.getUriForFile(activity, "com.winlator.FileProvider", file)',
                'FileProvider.getUriForFile(activity, activity.getPackageName()+".FileProvider", file)',
                1,
                "FileProvider runtime authority",
            ),
        ],
    )

    patch_file(
        java_root / "core" / "AppUtils.java",
        [
            (
                'public static final String INTERNAL_STORAGE = "/data/data/com.winlator/storage";',
                f'public static final String INTERNAL_STORAGE = "/data/data/{VALIDATION_APPLICATION_ID}/storage";',
                1,
                "internal storage application path",
            ),
        ],
    )

    patch_file(
        cpp_root / "winlator" / "include" / "winlator.h",
        [
            (
                '#define APP_CACHE_DIR "/data/data/com.winlator/cache"',
                f'#define APP_CACHE_DIR "/data/data/{VALIDATION_APPLICATION_ID}/cache"',
                1,
                "native app cache path",
            ),
        ],
    )

    patch_file(
        cpp_root / "vortekrenderer" / "include" / "vortek.h",
        [
            (
                '#define VORTEK_SERVER_PATH "/data/data/com.winlator/files/rootfs/tmp/.vortek/V0"',
                f'#define VORTEK_SERVER_PATH "/data/data/{VALIDATION_APPLICATION_ID}/files/rootfs/tmp/.vortek/V0"',
                1,
                "Vortek socket path",
            ),
        ],
    )

    patch_file(
        cpp_root / "gladiorenderer" / "include" / "gladio.h",
        [
            (
                '#define X11_SERVER_PATH "/data/data/com.winlator/files/rootfs/tmp/.X11-unix/X0"',
                f'#define X11_SERVER_PATH "/data/data/{VALIDATION_APPLICATION_ID}/files/rootfs/tmp/.X11-unix/X0"',
                1,
                "Gladio X11 socket path",
            ),
        ],
    )

    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except RuntimeError as error:
        print(f"upstream patch error: {error}", file=sys.stderr)
        raise SystemExit(1)
