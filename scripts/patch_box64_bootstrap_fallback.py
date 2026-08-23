#!/usr/bin/env python3
from __future__ import annotations

import sys
from pathlib import Path

FIELD_OLD = '''    private static final Object lock = new Object();'''
FIELD_NEW = '''    private static final Object lock = new Object();
    private static final long BOOTSTRAP_FALLBACK_WINDOW_MS = 15000L;
    private boolean bootstrapFallbackAttempted = false;'''

START_OLD = '''            copyDefaultBox64RCFile();
            pid = execGuestProgram();'''
START_NEW = '''            copyDefaultBox64RCFile();
            bootstrapFallbackAttempted = false;
            pid = execGuestProgram();'''

ENV_OLD = '''        if (this.envVars != null) envVars.putAll(this.envVars);

        File shmDir = new File(rootDir, "/tmp/shm");'''
ENV_NEW = '''        if (this.envVars != null) envVars.putAll(this.envVars);
        envVars.put("WINLATOR_BOX64_BASELINE", bootstrapFallbackAttempted ? "0.3.8-fallback" : DefaultVersion.BOX64);

        File shmDir = new File(rootDir, "/tmp/shm");'''

CALLBACK_OLD = '''        return ProcessHelper.exec(command, envVars, rootDir, (status) -> {
            synchronized (lock) {
                pid = -1;
            }
            if (terminationCallback != null) terminationCallback.call(status);
        });'''
CALLBACK_NEW = '''        final long bootstrapLaunchStartedAt = System.currentTimeMillis();
        return ProcessHelper.exec(command, envVars, rootDir, (status) -> {
            boolean retryWithFallback = status != 0
                    && !bootstrapFallbackAttempted
                    && (System.currentTimeMillis() - bootstrapLaunchStartedAt) <= BOOTSTRAP_FALLBACK_WINDOW_MS;

            synchronized (lock) {
                pid = -1;
            }

            if (retryWithFallback) {
                bootstrapFallbackAttempted = true;
                if (extractBootstrapFallbackBox64()) {
                    synchronized (lock) {
                        pid = execGuestProgram();
                    }
                    if (pid != -1) return;
                }
            }

            if (terminationCallback != null) terminationCallback.call(status);
        });'''

METHOD_ANCHOR = '''    private void extractBox64File() {'''
METHOD_INSERT = '''    private boolean extractBootstrapFallbackBox64() {
        try {
            Context context = environment.getContext();
            RootFS rootFS = environment.getRootFS();
            GeneralComponents.extractFile(GeneralComponents.Type.BOX64, context, "0.3.8", DefaultVersion.BOX64);

            File fallback = new File(rootFS.getRootDir(), "/usr/local/bin/box64");
            if (!fallback.isFile() || !fallback.canExecute()) return false;

            PreferenceManager.getDefaultSharedPreferences(context)
                    .edit()
                    .putString("current_box64_version", "0.3.8-fallback")
                    .apply();
            return true;
        }
        catch (Throwable ignored) {
            return false;
        }
    }

    private void extractBox64File() {'''


def replace_once(text: str, old: str, new: str, label: str) -> str:
    if new in text:
        return text
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly one occurrence, found {count}")
    return text.replace(old, new, 1)


def main() -> int:
    if len(sys.argv) != 2:
        print("usage: patch_box64_bootstrap_fallback.py <GuestProgramLauncherComponent.java>", file=sys.stderr)
        return 2

    path = Path(sys.argv[1])
    if not path.is_file():
        print(f"guest launcher file missing: {path}", file=sys.stderr)
        return 1

    original = path.read_text(encoding="utf-8")
    try:
        updated = replace_once(original, FIELD_OLD, FIELD_NEW, "fallback fields")
        updated = replace_once(updated, START_OLD, START_NEW, "fallback reset")
        updated = replace_once(updated, ENV_OLD, ENV_NEW, "fallback baseline marker")
        updated = replace_once(updated, CALLBACK_OLD, CALLBACK_NEW, "fallback termination retry")
        updated = replace_once(updated, METHOD_ANCHOR, METHOD_INSERT, "fallback extraction helper")
    except RuntimeError as error:
        print(f"Box64 bootstrap fallback patch: {error}", file=sys.stderr)
        return 1

    if updated != original:
        path.write_text(updated, encoding="utf-8")
        print(f"patched {path}")
    else:
        print(f"already patched {path}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
