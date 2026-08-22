#!/usr/bin/env python3
from __future__ import annotations

import sys
from pathlib import Path

TAG = "WinlatorSessionGate"

REPLACEMENTS = [
    (
        "import android.view.KeyEvent;",
        "import android.view.KeyEvent;\nimport android.util.Log;",
        "Log import",
    ),
    (
        "public class XServerDisplayActivity extends AppCompatActivity implements NavigationView.OnNavigationItemSelectedListener {",
        "public class XServerDisplayActivity extends AppCompatActivity implements NavigationView.OnNavigationItemSelectedListener {\n    private static final String SESSION_GATE_TAG = \"WinlatorSessionGate\";\n\n    private void sessionGate(String stage) {\n        Log.i(SESSION_GATE_TAG, stage);\n    }",
        "session gate helper",
    ),
    (
        "        rootFS = RootFS.find(this);",
        "        rootFS = RootFS.find(this);\n        sessionGate(\"01 activity-created rootfs-valid=\" + rootFS.isValid());",
        "activity/rootfs stage",
    ),
    (
        "            containerManager.activateContainer(container);",
        "            containerManager.activateContainer(container);\n            sessionGate(\"02 container-activated id=\" + container.id + \" name=\" + container.getName());",
        "container activation stage",
    ),
    (
        "                setupWineSystemFiles();\n                extractGraphicsDriverFiles();\n                changeWineAudioDriver();",
        "                sessionGate(\"03 wine-preparation-start\");\n                setupWineSystemFiles();\n                sessionGate(\"04 wine-system-files-ready\");\n                extractGraphicsDriverFiles();\n                sessionGate(\"05 graphics-driver-files-ready\");\n                changeWineAudioDriver();\n                sessionGate(\"06 wine-audio-ready\");",
        "wine preparation stages",
    ),
    (
        "            setupXEnvironment();",
        "            sessionGate(\"07 xenvironment-setup-start\");\n            setupXEnvironment();\n            sessionGate(\"08 xenvironment-setup-returned\");",
        "xenvironment stage",
    ),
    (
        "                if (!flags[0] && window.isRenderable() && !window.getClassName().isEmpty()) {",
        "                if (!flags[0] && window.isRenderable() && !window.getClassName().isEmpty()) {\n                    sessionGate(\"09 first-renderable-window class=\" + window.getClassName());",
        "first window stage",
    ),
    (
        "    protected void onDestroy() {\n        winHandler.stop();",
        "    protected void onDestroy() {\n        sessionGate(\"10 activity-destroy\");\n        winHandler.stop();",
        "destroy stage",
    ),
]


def main() -> int:
    if len(sys.argv) != 2:
        print("usage: instrument_session_gate.py <XServerDisplayActivity.java>", file=sys.stderr)
        return 2

    path = Path(sys.argv[1])
    if not path.is_file():
        print(f"session activity file missing: {path}", file=sys.stderr)
        return 1

    text = path.read_text(encoding="utf-8")
    if TAG in text:
        print(f"already instrumented {path}")
        return 0

    updated = text
    for old, new, label in REPLACEMENTS:
        count = updated.count(old)
        if count != 1:
            print(f"session gate {label}: expected 1 occurrence, found {count}", file=sys.stderr)
            return 1
        updated = updated.replace(old, new, 1)

    path.write_text(updated, encoding="utf-8")
    print(f"instrumented {path}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
