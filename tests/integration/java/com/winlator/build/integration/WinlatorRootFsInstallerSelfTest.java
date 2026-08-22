package com.winlator.build.integration;

import androidx.appcompat.app.AppCompatActivity;

import com.winlator.build.engine.runtime.RuntimeBaseSpec;
import com.winlator.build.engine.runtime.RuntimeBaseTreeProbe;
import com.winlator.xenvironment.RootFS;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;

public final class WinlatorRootFsInstallerSelfTest {
    private WinlatorRootFsInstallerSelfTest() {}

    public static void main(String[] args) {
        testCurrentRootFsDoesNotMutate();
        testFutureRootFsBlocksDowngrade();
        testInterruptedActivationRestoresOldRoot();
        testInterruptedCommitKeepsValidatedNewRoot();
        System.out.println("WinlatorRootFsInstallerSelfTest: all tests passed");
    }

    private static void testCurrentRootFsDoesNotMutate() {
        File parent = tempDir("rootfs-installer-current");
        try {
            File root = new File(parent, "rootfs");
            createRoot(root, RuntimeBaseSpec.ROOTFS_VERSION);
            RootFS.configure(root, true, RuntimeBaseSpec.ROOTFS_VERSION);

            AppCompatActivity activity = configuredActivity();
            WinlatorRootFsInstaller.Result result =
                    WinlatorRootFsInstaller.installOrRepair(activity, false);

            assertEquals(WinlatorRootFsInstaller.Status.NOT_NEEDED, result.getStatus(),
                    "current RootFS should not be reinstalled");
            assertTrue(new File(root, "sentinel.txt").isFile(),
                    "current RootFS must not be mutated");
        } finally {
            deleteTree(parent);
        }
    }

    private static void testFutureRootFsBlocksDowngrade() {
        File parent = tempDir("rootfs-installer-future");
        try {
            File root = new File(parent, "rootfs");
            createRoot(root, RuntimeBaseSpec.ROOTFS_VERSION + 1);
            RootFS.configure(root, true, RuntimeBaseSpec.ROOTFS_VERSION + 1);

            AppCompatActivity activity = configuredActivity();
            WinlatorRootFsInstaller.Result result =
                    WinlatorRootFsInstaller.installOrRepair(activity, true);

            assertEquals(WinlatorRootFsInstaller.Status.BLOCKED, result.getStatus(),
                    "future RootFS downgrade must be blocked even for forced repair");
            assertTrue(new File(root, "sentinel.txt").isFile(),
                    "blocked downgrade must not mutate RootFS");
        } finally {
            deleteTree(parent);
        }
    }

    private static void testInterruptedActivationRestoresOldRoot() {
        File parent = tempDir("rootfs-recover-activating");
        try {
            File active = new File(parent, "rootfs");
            File backup = new File(parent, "rootfs.backup-test");
            File staging = new File(parent, "rootfs.staging-test");

            createRoot(active, RuntimeBaseSpec.ROOTFS_VERSION);
            write(new File(active, "new-system.txt"), "new");
            write(new File(active, "home/xuser/save.dat"), "save");
            write(new File(active, "opt/installed-wine/custom/runtime.dat"), "wine");

            createRoot(backup, RuntimeBaseSpec.ROOTFS_VERSION - 1);
            write(new File(backup, "old-system.txt"), "old");
            deleteTree(new File(backup, "home"));
            deleteTree(new File(backup, "opt/installed-wine"));
            staging.mkdirs();

            writeJournal(parent, backup.getName(), staging.getName(), "ACTIVATING");
            String error = invokeRecovery(active, parent);

            assertEquals("", error, "activating recovery error");
            assertTrue(new File(active, "old-system.txt").isFile(),
                    "activating recovery must restore old RootFS");
            assertFalse(new File(active, "new-system.txt").exists(),
                    "activating recovery must discard new RootFS");
            assertEquals("save", read(new File(active, "home/xuser/save.dat")),
                    "activating recovery must restore preserved home into backup before rollback");
            assertEquals("wine", read(new File(active, "opt/installed-wine/custom/runtime.dat")),
                    "activating recovery must restore installed Wine into backup before rollback");
            assertFalse(backup.exists(), "backup should become active RootFS after recovery");
            assertFalse(staging.exists(), "staging should be cleaned after recovery");
            assertFalse(journal(parent).exists(), "journal should be removed after recovery");
        } finally {
            deleteTree(parent);
        }
    }

    private static void testInterruptedCommitKeepsValidatedNewRoot() {
        File parent = tempDir("rootfs-recover-committing");
        try {
            File active = new File(parent, "rootfs");
            File backup = new File(parent, "rootfs.backup-test");
            File staging = new File(parent, "rootfs.staging-test");

            createRoot(active, RuntimeBaseSpec.ROOTFS_VERSION);
            write(new File(active, "new-system.txt"), "new");
            write(new File(active, "home/xuser/save.dat"), "save");
            createRoot(backup, RuntimeBaseSpec.ROOTFS_VERSION - 1);
            write(new File(backup, "partial-old-file.txt"), "partial");
            staging.mkdirs();

            writeJournal(parent, backup.getName(), staging.getName(), "COMMITTING");
            String error = invokeRecovery(active, parent);

            assertEquals("", error, "committing recovery error");
            assertTrue(new File(active, "new-system.txt").isFile(),
                    "committing recovery must keep validated new RootFS");
            assertEquals("save", read(new File(active, "home/xuser/save.dat")),
                    "committing recovery must keep preserved home in active RootFS");
            assertFalse(backup.exists(), "partial old backup should be cleaned");
            assertFalse(staging.exists(), "staging should be cleaned after commit recovery");
            assertFalse(journal(parent).exists(), "journal should be removed after commit recovery");
        } finally {
            deleteTree(parent);
        }
    }

    private static String invokeRecovery(File active, File parent) {
        try {
            Method method = WinlatorRootFsInstaller.class.getDeclaredMethod(
                    "recoverInterruptedTransaction", File.class, File.class);
            method.setAccessible(true);
            return (String)method.invoke(null, active, parent);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static void writeJournal(File parent, String backup, String staging, String phase) {
        write(journal(parent), "backup=" + backup + "\n"
                + "staging=" + staging + "\n"
                + "phase=" + phase + "\n");
    }

    private static File journal(File parent) {
        return new File(parent, ".winlator-build-rootfs-transaction");
    }

    private static AppCompatActivity configuredActivity() {
        AppCompatActivity activity = new AppCompatActivity();
        activity.getAssets().addAsset(RuntimeBaseSpec.ROOTFS_ASSET);
        activity.getAssets().addAsset(RuntimeBaseSpec.ROOTFS_PATCHES_ASSET);
        return activity;
    }

    private static void createRoot(File root, int version) {
        write(new File(root, "usr/lib/aarch64-linux-gnu/libc.so.6"), "libc");
        write(new File(root, "usr/lib/aarch64-linux-gnu/ld-linux-aarch64.so.1"), "loader");
        write(new File(root, RuntimeBaseTreeProbe.VERSION_FILE), String.valueOf(version));
        write(new File(root, "sentinel.txt"), "keep");
    }

    private static File tempDir(String prefix) {
        try {
            return Files.createTempDirectory(prefix).toFile();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void write(File file, String value) {
        File parent = file.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
            throw new IllegalStateException("unable to create test directory");
        }
        try (FileWriter writer = new FileWriter(file)) {
            writer.write(value);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String read(File file) {
        try {
            return new String(Files.readAllBytes(file.toPath()), "UTF-8");
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static boolean deleteTree(File file) {
        if (file == null || !file.exists()) return true;
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) for (File child : children) deleteTree(child);
        }
        return file.delete();
    }

    private static void assertTrue(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static void assertFalse(boolean condition, String message) {
        assertTrue(!condition, message);
    }

    private static void assertEquals(Object expected, Object actual, String message) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError(message + ": expected=" + expected + " actual=" + actual);
        }
    }
}
