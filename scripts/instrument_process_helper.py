#!/usr/bin/env python3
from __future__ import annotations

import sys
from pathlib import Path

# AMOD winlator-glibc uses Runtime.exec(command, envp, workingDir) instead of
# ProcessBuilder.start() for the guest process. On the Android 16 validation
# device our persisted trace reached ProcessBuilder.start() but never reached
# process-created, so keep the upstream public API while replacing only the
# process creation mechanism with the proven AMOD path.
ORIGINAL = '''            ProcessBuilder processBuilder = (new ProcessBuilder(splitCommand(command))).directory(workingDir);
            if (debugCallbacks.isEmpty()) processBuilder.redirectOutput(new File("/dev/null")).redirectErrorStream(true);

            Map<String, String> environment = processBuilder.environment();
            for (String name : envVars) environment.put(name, envVars.get(name));

            java.lang.Process process = processBuilder.start();
            Field pidField = process.getClass().getDeclaredField("pid");
            pidField.setAccessible(true);
            pid = pidField.getInt(process);
            pidField.setAccessible(false);
'''

INSTRUMENTED = '''            String[] processEnv = envVars != null ? envVars.toStringArray() : null;
            emitValidationExecTrace("exec:before-start command=" + command);
            emitValidationExecTrace("exec:mode=runtime-exec");
            emitValidationExecTrace("exec:env WINELOADERNOEXEC=" + String.valueOf(envVars != null ? envVars.get("WINELOADERNOEXEC") : null)
                    + " BOX64_PATH=" + String.valueOf(envVars != null ? envVars.get("BOX64_PATH") : null)
                    + " BOX64_LD_LIBRARY_PATH=" + String.valueOf(envVars != null ? envVars.get("BOX64_LD_LIBRARY_PATH") : null)
                    + " WINEPREFIX=" + String.valueOf(envVars != null ? envVars.get("WINEPREFIX") : null)
                    + " PATH=" + String.valueOf(envVars != null ? envVars.get("PATH") : null)
                    + " LD_LIBRARY_PATH=" + String.valueOf(envVars != null ? envVars.get("LD_LIBRARY_PATH") : null));
            java.lang.Process process = Runtime.getRuntime().exec(splitCommand(command), processEnv, workingDir);
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
    updated = replace_once(original, ORIGINAL, INSTRUMENTED, "AMOD Runtime.exec guest process path")
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
