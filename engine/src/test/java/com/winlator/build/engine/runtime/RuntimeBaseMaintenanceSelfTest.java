package com.winlator.build.engine.runtime;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;

public final class RuntimeBaseMaintenanceSelfTest {
    private RuntimeBaseMaintenanceSelfTest() {}

    public static void runAll() {
        testTreeProbeCurrent();
        testActivationPreservesAndRollsBack();
        testActivationCommitPreservesData();
        testUnsafePreservedPathRejected();
    }

    private static void testTreeProbeCurrent() {
        File parent = tempDir("rootfs-probe");
        try {
            File root = new File(parent, "rootfs");
            createValidRoot(root, RuntimeBaseSpec.ROOTFS_VERSION);

            RuntimeBaseInspection inspection = new RuntimeBaseInspector().inspect(
                    new RuntimeBaseTreeProbe(root, true, true));

            assertEquals(RuntimeBaseInspection.Status.CURRENT, inspection.getStatus(),
                    "valid tree status");
            assertTrue(inspection.isLaunchReady(), "valid tree should be launch ready");
            assertEquals(RuntimeBaseSpec.ROOTFS_VERSION, inspection.getInstalledVersion(),
                    "valid tree version");
        } finally {
            deleteTree(parent);
        }
    }

    private static void testActivationPreservesAndRollsBack() {
        File parent = tempDir("rootfs-rollback");
        try {
            File active = new File(parent, "rootfs");
            File staged = new File(parent, "rootfs.staging");
            createValidRoot(active, 21);
            createValidRoot(staged, RuntimeBaseSpec.ROOTFS_VERSION);
            write(new File(active, "old-system.txt"), "old");
            write(new File(active, "home/xuser/save.dat"), "save");
            write(new File(active, "opt/installed-wine/custom/runtime.dat"), "wine");
            write(new File(staged, "new-system.txt"), "new");
            write(new File(staged, "home/default.txt"), "default-home");
            write(new File(staged, "opt/installed-wine/default.txt"), "default-wine");

            RootFsActivationTransaction transaction = new RootFsActivationTransaction(
                    active, staged, Arrays.asList("home", "opt/installed-wine"));
            transaction.activate();

            assertTrue(new File(active, "new-system.txt").isFile(), "new system must activate");
            assertFalse(new File(active, "old-system.txt").exists(), "old system file must stay in backup");
            assertEquals("save", read(new File(active, "home/xuser/save.dat")), "home must be preserved");
            assertEquals("wine", read(new File(active, "opt/installed-wine/custom/runtime.dat")),
                    "installed Wine must be preserved");
            assertFalse(new File(active, "home/default.txt").exists(),
                    "staged home must be replaced by preserved home");
            assertFalse(new File(active, "opt/installed-wine/default.txt").exists(),
                    "staged installed-wine must be replaced by preserved data");

            assertTrue(transaction.rollback(), "rollback should succeed");
            assertEquals(RootFsActivationTransaction.State.ROLLED_BACK, transaction.getState(),
                    "rollback state");
            assertTrue(new File(active, "old-system.txt").isFile(), "old system must be restored");
            assertFalse(new File(active, "new-system.txt").exists(), "new system must be removed on rollback");
            assertEquals("save", read(new File(active, "home/xuser/save.dat")),
                    "home must survive rollback");
            assertEquals("wine", read(new File(active, "opt/installed-wine/custom/runtime.dat")),
                    "installed Wine must survive rollback");
        } finally {
            deleteTree(parent);
        }
    }

    private static void testActivationCommitPreservesData() {
        File parent = tempDir("rootfs-commit");
        try {
            File active = new File(parent, "rootfs");
            File staged = new File(parent, "rootfs.staging");
            createValidRoot(active, 21);
            createValidRoot(staged, RuntimeBaseSpec.ROOTFS_VERSION);
            write(new File(active, "home/xuser/profile.dat"), "profile");
            write(new File(active, "opt/installed-wine/wine-x/runtime.dat"), "runtime");
            write(new File(staged, "new-system.txt"), "new");

            RootFsActivationTransaction transaction = new RootFsActivationTransaction(
                    active, staged, Arrays.asList("home", "opt/installed-wine"));
            transaction.activate();
            File backup = transaction.getBackupRoot();
            assertTrue(backup.exists(), "backup must exist before commit");

            assertTrue(transaction.commit(), "backup cleanup should succeed");
            assertEquals(RootFsActivationTransaction.State.COMMITTED, transaction.getState(),
                    "commit state");
            assertFalse(backup.exists(), "backup must be removed after commit");
            assertEquals("profile", read(new File(active, "home/xuser/profile.dat")),
                    "home must survive commit");
            assertEquals("runtime", read(new File(active, "opt/installed-wine/wine-x/runtime.dat")),
                    "installed Wine must survive commit");
            assertTrue(new File(active, "new-system.txt").isFile(), "new RootFS must remain active");
        } finally {
            deleteTree(parent);
        }
    }

    private static void testUnsafePreservedPathRejected() {
        File parent = tempDir("rootfs-path");
        try {
            File active = new File(parent, "rootfs");
            File staged = new File(parent, "rootfs.staging");
            createValidRoot(staged, RuntimeBaseSpec.ROOTFS_VERSION);
            boolean rejected = false;
            try {
                new RootFsActivationTransaction(active, staged, Arrays.asList("../outside"));
            } catch (IllegalArgumentException expected) {
                rejected = true;
            }
            assertTrue(rejected, "path traversal in preserved path must be rejected");
        } finally {
            deleteTree(parent);
        }
    }

    private static void createValidRoot(File root, int version) {
        write(new File(root, "usr/lib/aarch64-linux-gnu/libc.so.6"), "libc");
        write(new File(root, "usr/lib/aarch64-linux-gnu/ld-linux-aarch64.so.1"), "loader");
        write(new File(root, RuntimeBaseTreeProbe.VERSION_FILE), String.valueOf(version));
    }

    private static File tempDir(String prefix) {
        try {
            return Files.createTempDirectory(prefix).toFile();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void write(File file, String content) {
        File parent = file.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
            throw new IllegalStateException("unable to create test directory " + parent);
        }
        try (FileWriter writer = new FileWriter(file)) {
            writer.write(content);
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
        try {
            if (Files.isSymbolicLink(file.toPath())) return file.delete();
        } catch (RuntimeException ignored) {}
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) deleteTree(child);
            }
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
