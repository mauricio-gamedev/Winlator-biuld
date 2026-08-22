package com.winlator.build.engine.runtime;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public final class RootFsActivationTransaction {
    public enum State { NEW, ACTIVE, COMMITTED, ROLLED_BACK }

    private final File activeRoot;
    private final File stagedRoot;
    private final File backupRoot;
    private final List<String> preservedPaths;
    private final List<String> movedPreservedPaths = new ArrayList<>();
    private final boolean hadActiveRoot;
    private State state = State.NEW;

    public RootFsActivationTransaction(File activeRoot, File stagedRoot,
            List<String> preservedPaths) {
        if (activeRoot == null || stagedRoot == null) {
            throw new IllegalArgumentException("activeRoot and stagedRoot are required");
        }
        File activeParent = activeRoot.getAbsoluteFile().getParentFile();
        File stagedParent = stagedRoot.getAbsoluteFile().getParentFile();
        if (activeParent == null || stagedParent == null || !samePath(activeParent, stagedParent)) {
            throw new IllegalArgumentException("active and staged RootFS must be siblings on the same filesystem");
        }
        if (!stagedRoot.isDirectory()) throw new IllegalArgumentException("staged RootFS does not exist");

        List<String> normalized = new ArrayList<>();
        if (preservedPaths != null) {
            for (String path : preservedPaths) {
                String value = normalizeRelativePath(path);
                if (!normalized.contains(value)) normalized.add(value);
            }
        }

        this.activeRoot = activeRoot;
        this.stagedRoot = stagedRoot;
        this.preservedPaths = Collections.unmodifiableList(normalized);
        this.hadActiveRoot = existsNoFollow(activeRoot);
        this.backupRoot = new File(activeParent,
                activeRoot.getName() + ".backup-" + UUID.randomUUID().toString().replace("-", ""));
    }

    public synchronized State getState() { return state; }
    public File getBackupRoot() { return backupRoot; }
    public List<String> getPreservedPaths() { return preservedPaths; }

    public synchronized void activate() {
        requireState(State.NEW);

        boolean oldRootMoved = false;
        boolean newRootActivated = false;
        try {
            if (hadActiveRoot) {
                if (!activeRoot.renameTo(backupRoot)) {
                    throw new IllegalStateException("unable to move existing RootFS to backup");
                }
                oldRootMoved = true;
            }

            if (!stagedRoot.renameTo(activeRoot)) {
                if (oldRootMoved && !backupRoot.renameTo(activeRoot)) {
                    throw new IllegalStateException("unable to activate staged RootFS and unable to restore backup");
                }
                throw new IllegalStateException("unable to activate staged RootFS");
            }
            newRootActivated = true;

            if (hadActiveRoot) {
                for (String relative : preservedPaths) {
                    File source = new File(backupRoot, relative);
                    if (!existsNoFollow(source)) continue;

                    File target = new File(activeRoot, relative);
                    if (existsNoFollow(target) && !deleteTree(target)) {
                        throw new IllegalStateException("unable to replace staged preserved path: " + relative);
                    }
                    File parent = target.getParentFile();
                    if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
                        throw new IllegalStateException("unable to create parent for preserved path: " + relative);
                    }
                    if (!source.renameTo(target)) {
                        throw new IllegalStateException("unable to preserve RootFS path: " + relative);
                    }
                    movedPreservedPaths.add(relative);
                }
            }

            state = State.ACTIVE;
        } catch (RuntimeException e) {
            if (newRootActivated || oldRootMoved) {
                boolean rolledBack = rollbackInternal();
                if (!rolledBack) {
                    throw new IllegalStateException(e.getMessage() + "; RootFS rollback failed", e);
                }
            }
            throw e;
        }
    }

    public synchronized boolean commit() {
        requireState(State.ACTIVE);
        state = State.COMMITTED;
        return !existsNoFollow(backupRoot) || deleteTree(backupRoot);
    }

    public synchronized boolean rollback() {
        if (state == State.ROLLED_BACK) return true;
        if (state != State.ACTIVE) return false;
        return rollbackInternal();
    }

    private boolean rollbackInternal() {
        boolean allOk = true;

        if (hadActiveRoot && existsNoFollow(backupRoot)) {
            for (int i = movedPreservedPaths.size() - 1; i >= 0; i--) {
                String relative = movedPreservedPaths.get(i);
                File source = new File(activeRoot, relative);
                File target = new File(backupRoot, relative);
                if (!existsNoFollow(source)) continue;

                boolean pathOk = true;
                File parent = target.getParentFile();
                if (parent != null && !parent.isDirectory() && !parent.mkdirs()) pathOk = false;
                if (existsNoFollow(target) && !deleteTree(target)) pathOk = false;
                if (pathOk && !source.renameTo(target)) pathOk = false;
                if (!pathOk) allOk = false;
            }
        }

        if (existsNoFollow(activeRoot) && !deleteTree(activeRoot)) allOk = false;

        if (hadActiveRoot) {
            if (!existsNoFollow(backupRoot) || !backupRoot.renameTo(activeRoot)) allOk = false;
        }

        if (allOk) state = State.ROLLED_BACK;
        return allOk;
    }

    private void requireState(State expected) {
        if (state != expected) {
            throw new IllegalStateException("invalid RootFS transaction state: " + state);
        }
    }

    private static String normalizeRelativePath(String path) {
        if (path == null) throw new IllegalArgumentException("preserved path cannot be null");
        String value = path.replace('\\', '/').trim();
        while (value.startsWith("./")) value = value.substring(2);
        while (value.endsWith("/") && value.length() > 1) value = value.substring(0, value.length() - 1);
        if (value.isEmpty() || value.startsWith("/") || value.equals("..")
                || value.startsWith("../") || value.contains("/../")) {
            throw new IllegalArgumentException("unsafe preserved RootFS path: " + path);
        }
        return value;
    }

    private static boolean samePath(File first, File second) {
        try {
            return first.getCanonicalFile().equals(second.getCanonicalFile());
        } catch (IOException e) {
            return first.getAbsoluteFile().equals(second.getAbsoluteFile());
        }
    }

    private static boolean existsNoFollow(File file) {
        try {
            return Files.exists(file.toPath(), LinkOption.NOFOLLOW_LINKS);
        } catch (RuntimeException e) {
            return file.exists();
        }
    }

    private static boolean deleteTree(File file) {
        if (!existsNoFollow(file)) return true;
        try {
            if (Files.isSymbolicLink(file.toPath())) return file.delete();
        } catch (RuntimeException ignored) {}

        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children == null) return false;
            for (File child : children) {
                if (!deleteTree(child)) return false;
            }
        }
        return file.delete();
    }
}
