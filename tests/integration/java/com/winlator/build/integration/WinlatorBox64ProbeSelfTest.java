package com.winlator.build.integration;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.preference.PreferenceManager;

import com.winlator.build.engine.runtime.Box64Inspection;
import com.winlator.build.engine.runtime.Box64Inspector;
import com.winlator.build.engine.runtime.Box64Spec;
import com.winlator.xenvironment.RootFS;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;

public final class WinlatorBox64ProbeSelfTest {
    private WinlatorBox64ProbeSelfTest() {}

    public static void main(String[] args) {
        testPostRootFsReinstallStateIsRepairableNotReady();
        testCurrentBuiltinBox64IsLaunchReadyBeforeRcDeployment();
        testVersionMismatchIsBlocked();
        testMissingBinaryIsBlockedEvenWhenPreferenceMatches();
        testNonRunnableBinaryIsBlocked();
        testMissingRepairPackageDoesNotInvalidateAlreadyExtractedRuntime();
        System.out.println("WinlatorBox64ProbeSelfTest: all tests passed");
    }

    private static void testPostRootFsReinstallStateIsRepairableNotReady() {
        Fixture fixture = fixture(false, false, true);
        try {
            SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(fixture.context);
            preferences.edit().putString("box64_version", Box64Spec.VERSION).remove("current_box64_version").apply();

            Box64Inspection inspection = inspect(fixture.context);
            assertEquals(Box64Inspection.Status.MISSING, inspection.getStatus(), "post-reinstall Box64 status");
            assertEquals(Box64Spec.VERSION, inspection.getSelectedVersion(), "post-reinstall selected version");
            assertEquals("", inspection.getCurrentExtractedVersion(), "post-reinstall current version");
            assertFalse(inspection.isLaunchReady(), "post-reinstall Box64 must not be launch-ready before preparation");
            assertTrue(inspection.isSelectedPackageAvailable(), "post-reinstall builtin package availability");
            assertTrue(inspection.isDefaultRcAssetAvailable(), "post-reinstall RC asset availability");
            assertTrue(inspection.canRepair(), "post-reinstall Box64 should be repairable");
        } finally {
            fixture.close();
        }
    }

    private static void testCurrentBuiltinBox64IsLaunchReadyBeforeRcDeployment() {
        Fixture fixture = fixture(true, true, true);
        try {
            setVersions(fixture.context, Box64Spec.VERSION, Box64Spec.VERSION);
            Box64Inspection inspection = inspect(fixture.context);

            assertEquals(Box64Inspection.Status.CURRENT, inspection.getStatus(), "current Box64 status");
            assertTrue(inspection.isLaunchReady(), "current Box64 launch readiness");
            assertTrue(inspection.isSelectedPackageAvailable(), "builtin Box64 package availability");
            assertTrue(inspection.isBinaryPresent(), "Box64 binary presence");
            assertTrue(inspection.isBinaryRunnable(), "Box64 binary executability");
            assertTrue(inspection.isDefaultRcAssetAvailable(), "default RC asset availability");
            assertFalse(inspection.isRcFilePresent(), "RC file should not be required before first guest launch");
            assertTrue(inspection.canRepair(), "current builtin Box64 should be repairable");
        } finally {
            fixture.close();
        }
    }

    private static void testVersionMismatchIsBlocked() {
        Fixture fixture = fixture(true, true, true);
        try {
            setVersions(fixture.context, Box64Spec.VERSION, "0.4.3");
            Box64Inspection inspection = inspect(fixture.context);
            assertEquals(Box64Inspection.Status.VERSION_MISMATCH, inspection.getStatus(), "version mismatch status");
            assertFalse(inspection.isLaunchReady(), "version mismatch must block launch readiness");
        } finally {
            fixture.close();
        }
    }

    private static void testMissingBinaryIsBlockedEvenWhenPreferenceMatches() {
        Fixture fixture = fixture(false, false, true);
        try {
            setVersions(fixture.context, Box64Spec.VERSION, Box64Spec.VERSION);
            Box64Inspection inspection = inspect(fixture.context);
            assertEquals(Box64Inspection.Status.INCOMPLETE, inspection.getStatus(), "missing binary status");
            assertFalse(inspection.isLaunchReady(), "missing binary must block launch readiness");
            assertFalse(inspection.isBinaryPresent(), "missing binary presence");
        } finally {
            fixture.close();
        }
    }

    private static void testNonRunnableBinaryIsBlocked() {
        Fixture fixture = fixture(true, false, true);
        try {
            setVersions(fixture.context, Box64Spec.VERSION, Box64Spec.VERSION);
            Box64Inspection inspection = inspect(fixture.context);
            assertEquals(Box64Inspection.Status.INCOMPLETE, inspection.getStatus(), "non-runnable binary status");
            assertFalse(inspection.isLaunchReady(), "non-runnable Box64 must block launch readiness");
            assertTrue(inspection.isBinaryPresent(), "non-runnable binary should still be present");
            assertFalse(inspection.isBinaryRunnable(), "binary executable permission");
        } finally {
            fixture.close();
        }
    }

    private static void testMissingRepairPackageDoesNotInvalidateAlreadyExtractedRuntime() {
        Fixture fixture = fixture(true, true, false);
        try {
            setVersions(fixture.context, Box64Spec.VERSION, Box64Spec.VERSION);
            Box64Inspection inspection = inspect(fixture.context);
            assertEquals(Box64Inspection.Status.CURRENT, inspection.getStatus(), "missing repair package status");
            assertTrue(inspection.isLaunchReady(), "missing repair package must not invalidate extracted Box64");
            assertFalse(inspection.isSelectedPackageAvailable(), "missing package availability");
            assertFalse(inspection.canRepair(), "missing package must disable repair readiness");
        } finally {
            fixture.close();
        }
    }

    private static Box64Inspection inspect(Context context) {
        return new Box64Inspector().inspect(new WinlatorBox64Probe(context));
    }

    private static void setVersions(Context context, String selected, String current) {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        preferences.edit().putString("box64_version", selected).apply();
        preferences.edit().putString("current_box64_version", current).apply();
    }

    private static Fixture fixture(boolean binaryPresent, boolean binaryExecutable,
            boolean packageAssetPresent) {
        File parent = tempDir("winlator-box64-probe");
        File root = new File(parent, "rootfs");
        root.mkdirs();
        RootFS.configure(root, true, 22);

        Context context = new Context();
        PreferenceManager.clear(context);
        context.getAssets().addAsset(Box64Spec.DEFAULT_RC_ASSET);
        if (packageAssetPresent) context.getAssets().addAsset(Box64Spec.PACKAGE_ASSET);

        if (binaryPresent) {
            File binary = new File(root, Box64Spec.BINARY_RELATIVE_PATH);
            write(binary, "box64");
            if (!binary.setExecutable(binaryExecutable, false) && binary.canExecute() != binaryExecutable) {
                throw new IllegalStateException("unable to configure Box64 test executable bit");
            }
        }

        return new Fixture(context, parent);
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
        final File parent;

        Fixture(Context context, File parent) {
            this.context = context;
            this.parent = parent;
        }

        void close() {
            PreferenceManager.clear(context);
            deleteTree(parent);
        }
    }
}
