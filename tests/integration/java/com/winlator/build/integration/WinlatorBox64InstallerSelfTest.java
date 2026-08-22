package com.winlator.build.integration;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.preference.PreferenceManager;

import com.winlator.build.engine.runtime.Box64Inspection;
import com.winlator.build.engine.runtime.Box64Spec;
import com.winlator.build.engine.runtime.RuntimeBaseSpec;
import com.winlator.core.TarCompressorUtils;
import com.winlator.xenvironment.RootFS;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;

public final class WinlatorBox64InstallerSelfTest {
    private WinlatorBox64InstallerSelfTest() {}

    public static void main(String[] args) {
        testPrepareSucceedsAndCommitsOnlyAfterValidation();
        testUnexpectedPackageFileFailsClosed();
        testExtractionFailureDoesNotAdvanceCurrentVersion();
        System.out.println("WinlatorBox64InstallerSelfTest: all tests passed");
    }

    private static void testPrepareSucceedsAndCommitsOnlyAfterValidation() {
        Fixture fixture = fixture();
        try {
            TarCompressorUtils.configureBox64Payload(true, true, false);
            WinlatorBox64Installer.Result result = WinlatorBox64Installer.prepareOrRepair(fixture.context);

            assertEquals(WinlatorBox64Installer.Status.SUCCESS, result.getStatus(), "successful prepare status");
            Box64Inspection inspection = result.getInspection();
            assertTrue(inspection != null && inspection.isLaunchReady(), "Box64 should be launch-ready");
            assertTrue(inspection.isRcFilePresent(), "Box64 RC should be deployed");
            assertTrue(new File(fixture.root, Box64Spec.BINARY_RELATIVE_PATH).canExecute(), "Box64 binary should be executable");
            assertEquals(Box64Spec.VERSION, currentVersion(fixture.context), "current Box64 version should commit");
        } finally {
            fixture.close();
        }
    }

    private static void testUnexpectedPackageFileFailsClosed() {
        Fixture fixture = fixture();
        try {
            TarCompressorUtils.configureBox64Payload(true, true, true);
            WinlatorBox64Installer.Result result = WinlatorBox64Installer.prepareOrRepair(fixture.context);

            assertEquals(WinlatorBox64Installer.Status.STAGING_INVALID, result.getStatus(), "unexpected payload status");
            assertFalse(new File(fixture.root, Box64Spec.BINARY_RELATIVE_PATH).exists(), "unexpected package must not touch active binary");
            assertEquals("", currentVersion(fixture.context), "unexpected package must not advance current version");
        } finally {
            fixture.close();
        }
    }

    private static void testExtractionFailureDoesNotAdvanceCurrentVersion() {
        Fixture fixture = fixture();
        try {
            TarCompressorUtils.configureBox64Payload(false, true, false);
            WinlatorBox64Installer.Result result = WinlatorBox64Installer.prepareOrRepair(fixture.context);

            assertEquals(WinlatorBox64Installer.Status.EXTRACTION_FAILED, result.getStatus(), "extraction failure status");
            assertFalse(new File(fixture.root, Box64Spec.BINARY_RELATIVE_PATH).exists(), "failed extraction must not touch active binary");
            assertEquals("", currentVersion(fixture.context), "failed extraction must not advance current version");
        } finally {
            fixture.close();
        }
    }

    private static Fixture fixture() {
        File parent = tempDir("winlator-box64-installer");
        File root = new File(parent, "rootfs");
        root.mkdirs();
        RootFS.configure(root, true, RuntimeBaseSpec.ROOTFS_VERSION);
        write(new File(root, "usr/lib/aarch64-linux-gnu/libc.so.6"), "libc");
        write(new File(root, "usr/lib/aarch64-linux-gnu/ld-linux-aarch64.so.1"), "loader");

        Context context = new Context();
        PreferenceManager.clear(context);
        context.getAssets().addAsset(RuntimeBaseSpec.ROOTFS_ASSET);
        context.getAssets().addAsset(RuntimeBaseSpec.ROOTFS_PATCHES_ASSET);
        context.getAssets().addAsset(Box64Spec.PACKAGE_ASSET);
        context.getAssets().addAsset(Box64Spec.DEFAULT_RC_ASSET, "rc".getBytes());

        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        preferences.edit().putString("box64_version", Box64Spec.VERSION).remove("current_box64_version").apply();
        TarCompressorUtils.configure(1024L, true);
        return new Fixture(context, root, parent);
    }

    private static String currentVersion(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context)
                .getString("current_box64_version", "");
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
        try (FileWriter writer = new FileWriter(file, false)) {
            writer.write(value);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void deleteTree(File file) {
        if (file == null || !file.exists()) return;
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) for (File child : children) deleteTree(child);
        }
        file.delete();
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

    private static final class Fixture {
        final Context context;
        final File root;
        final File parent;

        Fixture(Context context, File root, File parent) {
            this.context = context;
            this.root = root;
            this.parent = parent;
        }

        void close() {
            PreferenceManager.clear(context);
            deleteTree(parent);
        }
    }
}
