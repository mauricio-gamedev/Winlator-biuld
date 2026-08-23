#!/usr/bin/env python3
from __future__ import annotations

import sys
from pathlib import Path

# Keep the upstream Winlator ProcessBuilder path and instrument it only. The
# persisted traces are intentionally bounded to launch-critical environment
# variables so one device test is enough to diagnose the complete bootstrap.
ORIGINAL = '''            java.lang.Process process = processBuilder.start();
            Field pidField = process.getClass().getDeclaredField("pid");
            pidField.setAccessible(true);
            pid = pidField.getInt(process);
            pidField.setAccessible(false);
'''

INSTRUMENTED = '''            emitValidationExecTrace("exec:before-start command=" + command);
            emitValidationExecTrace("exec:mode=process-builder");
            emitValidationExecTrace("exec:env WINELOADERNOEXEC=" + String.valueOf(environment.get("WINELOADERNOEXEC"))
                    + " BOX64_BASELINE=" + String.valueOf(environment.get("WINLATOR_BOX64_BASELINE"))
                    + " BOX64_PATH=" + String.valueOf(environment.get("BOX64_PATH"))
                    + " BOX64_LD_LIBRARY_PATH=" + String.valueOf(environment.get("BOX64_LD_LIBRARY_PATH"))
                    + " BOX64_MMAP32=" + String.valueOf(environment.get("BOX64_MMAP32"))
                    + " BOX64_X11GLX=" + String.valueOf(environment.get("BOX64_X11GLX"))
                    + " BOX64_LOG=" + String.valueOf(environment.get("BOX64_LOG"))
                    + " WINEPREFIX=" + String.valueOf(environment.get("WINEPREFIX"))
                    + " PATH=" + String.valueOf(environment.get("PATH"))
                    + " LD_LIBRARY_PATH=" + String.valueOf(environment.get("LD_LIBRARY_PATH"))
                    + " LD_PRELOAD=" + String.valueOf(environment.get("LD_PRELOAD"))
                    + " FONTCONFIG_PATH=" + String.valueOf(environment.get("FONTCONFIG_PATH"))
                    + " ANDROID_SYSVSHM_SERVER=" + String.valueOf(environment.get("ANDROID_SYSVSHM_SERVER")));
            java.lang.Process process = processBuilder.start();
            emitValidationExecTrace("exec:process-created class=" + process.getClass().getName());
            emitValidationExecTrace("exec:pid-reflection-start");
            Field pidField = process.getClass().getDeclaredField("pid");
            pidField.setAccessible(true);
            pid = pidField.getInt(process);
            pidField.setAccessible(false);
            emitValidationExecTrace("exec:pid-obtained pid=" + pid);
'''

ORIGINAL_CATCH = '''        catch (Exception e) {}
        return pid;
    }
'''

INSTRUMENTED_CATCH = '''        catch (Exception e) {
            emitValidationExecTrace("exec:exception type=" + e.getClass().getName() + " message=" + String.valueOf(e.getMessage()));
        }
        return pid;
    }

    private static void emitValidationExecTrace(String line) {
        synchronized (debugCallbacks) {
            if (!debugCallbacks.isEmpty()) {
                for (Callback<String> callback : debugCallbacks) callback.call("[validation-process] " + line);
            }
        }
    }
'''


def replace_once(text: str, old: str, new: str, label: str) -> str:
    if new in text:
        return text
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly one upstream occurrence, found {count}")
    return text.replace(old, new, 1)


def main() -> int:
    if len(sys.argv) != 2:
        print("usage: instrument_process_helper.py <ProcessHelper.java>", file=sys.stderr)
        return 2

    path = Path(sys.argv[1]).resolve()
    if not path.is_file():
        raise RuntimeError(f"ProcessHelper.java is missing: {path}")

    original = path.read_text(encoding="utf-8")
    updated = replace_once(original, ORIGINAL, INSTRUMENTED, "ProcessHelper exec checkpoints")
    updated = replace_once(updated, ORIGINAL_CATCH, INSTRUMENTED_CATCH, "ProcessHelper exec exception trace")

    if updated != original:
        path.write_text(updated, encoding="utf-8")
        print(f"instrumented {path}")
    else:
        print(f"already instrumented {path}")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except RuntimeError as error:
        print(f"ProcessHelper instrumentation error: {error}", file=sys.stderr)
        raise SystemExit(1)
