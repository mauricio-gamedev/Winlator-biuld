package com.winlator.build.integration;

import androidx.appcompat.app.AppCompatActivity;

import com.winlator.build.engine.runtime.RuntimeBaseSpec;
import com.winlator.build.engine.runtime.RuntimeBaseTreeProbe;
import com.winlator.xenvironment.RootFS;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;

public final class WinlatorRootFsInstallerSelfTest {
    private WinlatorRootFsInstallerSelfTest() {}

    public static void main(String[] args) {
        testCurrentRootFsDoesNotMutate();
        testFutureRootFsBlocksDowngrade();
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

    private static void assertEquals(Object expected, Object actual, String message) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError(message + ": expected=" + expected + " actual=" + actual);
        }
    }
}
