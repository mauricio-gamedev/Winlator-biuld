#!/usr/bin/env python3
from __future__ import annotations

import sys
from pathlib import Path

TAG = "WinlatorSessionGate"

REPLACEMENTS = [
    (
        "import android.view.KeyEvent;",
        "import android.view.KeyEvent;\nimport android.util.Log;\n\nimport java.io.File;\nimport java.io.FileWriter;\nimport java.io.PrintWriter;\nimport java.io.StringWriter;",
        "diagnostic imports",
    ),
    (
        "public class XServerDisplayActivity extends AppCompatActivity implements NavigationView.OnNavigationItemSelectedListener {",
        "public class XServerDisplayActivity extends AppCompatActivity implements NavigationView.OnNavigationItemSelectedListener {\n"
        "    private static final String SESSION_GATE_TAG = \"WinlatorSessionGate\";\n"
        "    private static final String SESSION_GATE_FILE = \"session-gate.log\";\n"
        "    private Thread.UncaughtExceptionHandler sessionGatePreviousHandler;\n"
        "    private int sessionGateOutputLines = 0;\n\n"
        "    private synchronized void sessionGate(String stage) {\n"
        "        String line = System.currentTimeMillis() + \" [\" + Thread.currentThread().getName() + \"] \" + stage;\n"
        "        Log.i(SESSION_GATE_TAG, line);\n"
        "        try (FileWriter writer = new FileWriter(new File(getFilesDir(), SESSION_GATE_FILE), true)) {\n"
        "            writer.write(line);\n"
        "            writer.write('\\n');\n"
        "            writer.flush();\n"
        "        } catch (Throwable ignored) {}\n"
        "    }\n\n"
        "    private synchronized void sessionGateProcessOutput(String line) {\n"
        "        if (sessionGateOutputLines < 240) {\n"
        "            sessionGateOutputLines++;\n"
        "            sessionGate(\"P1 guest-output \" + line);\n"
        "        } else if (sessionGateOutputLines == 240) {\n"
        "            sessionGateOutputLines++;\n"
        "            sessionGate(\"P1 guest-output [truncated after 240 lines]\");\n"
        "        }\n"
        "    }\n\n"
        "    private void installSessionGateCrashHandler() {\n"
        "        sessionGatePreviousHandler = Thread.getDefaultUncaughtExceptionHandler();\n"
        "        Thread.setDefaultUncaughtExceptionHandler((thread, error) -> {\n"
        "            try {\n"
        "                StringWriter buffer = new StringWriter();\n"
        "                error.printStackTrace(new PrintWriter(buffer));\n"
        "                sessionGate(\"CRASH thread=\" + thread.getName() + \" type=\" + error.getClass().getName()\n"
        "                        + \" message=\" + String.valueOf(error.getMessage()) + \"\\n\" + buffer.toString());\n"
        "            } catch (Throwable ignored) {}\n"
        "            if (sessionGatePreviousHandler != null) sessionGatePreviousHandler.uncaughtException(thread, error);\n"
        "        });\n"
        "    }\n\n"
        "    private void restoreSessionGateCrashHandler() {\n"
        "        if (sessionGatePreviousHandler != null) {\n"
        "            Thread.setDefaultUncaughtExceptionHandler(sessionGatePreviousHandler);\n"
        "            sessionGatePreviousHandler = null;\n"
        "        }\n"
        "    }",
        "session gate persistent helper",
    ),
    (
        "        AppUtils.setActivityTheme(this);\n        super.onCreate(savedInstanceState);",
        "        AppUtils.setActivityTheme(this);\n        super.onCreate(savedInstanceState);\n"
        "        installSessionGateCrashHandler();\n"
        "        sessionGate(\"00 session-start intent-container-id=\" + getIntent().getIntExtra(\"container_id\", 0));",
        "session start/crash handler",
    ),
    (
        "        ProcessHelper.removeAllDebugCallbacks();",
        "        ProcessHelper.removeAllDebugCallbacks();\n        ProcessHelper.addDebugCallback(this::sessionGateProcessOutput);\n        sessionGate(\"D1 process-output-capture-enabled\");",
        "process output capture",
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
        "        setupUI();",
        "        sessionGate(\"02a setup-ui-start\");\n        setupUI();\n        sessionGate(\"02b setup-ui-returned\");",
        "ui setup stage",
    ),
    (
        "                setupWineSystemFiles();\n                extractGraphicsDriverFiles();\n                changeWineAudioDriver();",
        "                sessionGate(\"03 wine-preparation-start\");\n                setupWineSystemFiles();\n                sessionGate(\"04 wine-system-files-ready\");\n                extractGraphicsDriverFiles();\n                sessionGate(\"05 graphics-driver-files-ready\");\n                changeWineAudioDriver();\n                sessionGate(\"06 wine-audio-ready\");",
        "wine preparation stages",
    ),
    (
        "            setupXEnvironment();",
        "            sessionGate(\"07 xenvironment-setup-start\");\n            setupXEnvironment();\n            sessionGate(\"08 xenvironment-setup-returned environment-null=\" + (environment == null));",
        "xenvironment stage",
    ),
    (
        "        environment = new XEnvironment(this, rootFS);",
        "        sessionGate(\"07a xenvironment-create-start\");\n        environment = new XEnvironment(this, rootFS);\n        sessionGate(\"07b xenvironment-created\");",
        "xenvironment construction stage",
    ),
    (
        "        guestProgramLauncherComponent.setEnvVars(envVars);\n        guestProgramLauncherComponent.setTerminationCallback((status) -> exit());\n        environment.addComponent(guestProgramLauncherComponent);",
        "        guestProgramLauncherComponent.setEnvVars(envVars);\n        sessionGate(\"07c guest-configured command=\" + getWineStartCommand());\n        guestProgramLauncherComponent.setTerminationCallback((status) -> {\n            sessionGate(\"G1 guest-terminated status=\" + status + \" finishing=\" + isFinishing());\n            exit();\n        });\n        environment.addComponent(guestProgramLauncherComponent);\n        sessionGate(\"07d guest-component-added\");",
        "guest launcher termination stage",
    ),
    (
        "        environment.startEnvironmentComponents();\n\n        winHandler.start();",
        "        sessionGate(\"07e environment-components-start\");\n        environment.startEnvironmentComponents();\n        sessionGate(\"07f environment-components-returned\");\n\n        sessionGate(\"07g winhandler-start\");\n        winHandler.start();\n        sessionGate(\"07h winhandler-started\");",
        "environment component startup stage",
    ),
    (
        "                if (!flags[0] && window.isRenderable() && !window.getClassName().isEmpty()) {",
        "                sessionGate(\"08w map-window id=\" + window.id + \" renderable=\" + window.isRenderable() + \" class=\" + window.getClassName());\n                if (!flags[0] && window.isRenderable() && !window.getClassName().isEmpty()) {\n                    sessionGate(\"09 first-renderable-window class=\" + window.getClassName());",
        "window mapping stage",
    ),
    (
        "    public void onResume() {\n        super.onResume();",
        "    public void onResume() {\n        super.onResume();\n        sessionGate(\"L1 onResume environment-null=\" + (environment == null));",
        "resume lifecycle stage",
    ),
    (
        "    public void onPause() {\n        ForegroundService.onPauseSession(this);",
        "    public void onPause() {\n        sessionGate(\"L2 onPause finishing=\" + isFinishing() + \" changing-config=\" + isChangingConfigurations() + \" environment-null=\" + (environment == null));\n        ForegroundService.onPauseSession(this);",
        "pause lifecycle stage",
    ),
    (
        "    protected void onDestroy() {\n        winHandler.stop();",
        "    protected void onDestroy() {\n        sessionGate(\"10 activity-destroy finishing=\" + isFinishing() + \" changing-config=\" + isChangingConfigurations());\n        ProcessHelper.removeAllDebugCallbacks();\n        restoreSessionGateCrashHandler();\n        winHandler.stop();",
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