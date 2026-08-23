#!/usr/bin/env python3
from __future__ import annotations

import sys
from pathlib import Path

OLD_OUTPUT = '''    private synchronized void sessionGateProcessOutput(String line) {
        if (line == null) return;
        if (sessionGateOutputTail.size() >= SESSION_GATE_OUTPUT_TAIL_LINES) sessionGateOutputTail.removeFirst();
        sessionGateOutputTail.addLast(line);
    }'''

NEW_OUTPUT = '''    private synchronized void sessionGateProcessOutput(String line) {
        if (line == null) return;

        final String validationPrefix = "[validation-process] ";
        if (line.startsWith(validationPrefix)) {
            String trace = line.substring(validationPrefix.length());
            if (trace.startsWith("exec:before-start command=")
                    || trace.startsWith("exec:env ")
                    || trace.startsWith("exec:process-created")
                    || trace.startsWith("exec:pid-obtained")
                    || trace.startsWith("exec:exception")) {
                sessionGate("P0-" + trace);
            }
        }

        if (sessionGateOutputTail.size() >= SESSION_GATE_OUTPUT_TAIL_LINES) sessionGateOutputTail.removeFirst();
        sessionGateOutputTail.addLast(line);
    }'''

OLD_DESTROY = '''        sessionGate("10 activity-destroy finishing=" + isFinishing() + " changing-config=" + isChangingConfigurations());
        ProcessHelper.removeAllDebugCallbacks();'''

NEW_DESTROY = '''        sessionGate("10 activity-destroy finishing=" + isFinishing() + " changing-config=" + isChangingConfigurations());
        sessionGateFlushProcessOutputTail();
        sessionGate("P2-tail-flushed-on-destroy");
        ProcessHelper.removeAllDebugCallbacks();'''


def replace_once(text: str, old: str, new: str, label: str) -> str:
    if new in text:
        return text
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly one occurrence, found {count}")
    return text.replace(old, new, 1)


def main() -> int:
    if len(sys.argv) != 2:
        print("usage: patch_session_gate_live_snapshot.py <XServerDisplayActivity.java>", file=sys.stderr)
        return 2

    path = Path(sys.argv[1])
    if not path.is_file():
        print(f"session activity file missing: {path}", file=sys.stderr)
        return 1

    original = path.read_text(encoding="utf-8")
    try:
        updated = replace_once(original, OLD_OUTPUT, NEW_OUTPUT, "session live process snapshot")
        updated = replace_once(updated, OLD_DESTROY, NEW_DESTROY, "session tail flush on destroy")
    except RuntimeError as error:
        print(f"session live snapshot patch: {error}", file=sys.stderr)
        return 1

    if updated != original:
        path.write_text(updated, encoding="utf-8")
        print(f"patched {path}")
    else:
        print(f"already patched {path}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
