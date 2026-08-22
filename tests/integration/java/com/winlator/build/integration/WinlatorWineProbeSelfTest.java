package com.winlator.build.integration;

import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceManager;

import com.winlator.build.engine.runtime.Box64Spec;
import com.winlator.build.engine.runtime.RuntimeBaseSpec;
import com.winlator.build.engine.runtime.RuntimeBaseTreeProbe;
import com.winlator.build.engine.runtime.WineInspection;
import com.winlator.build.engine.runtime.WineInspector;
import com.winlator.build.engine.runtime.WineSpec;
import com.winlator.xenvironment.RootFS;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;

public final class WinlatorWineProbeSelfTest {
    public static void main(String[] args) {
        File parent = tempDir();
        try {
            File root = new File(parent, "rootfs");
            write(new File(root, "usr/lib/aarch64-linux-gnu/libc.so.6"), "libc");
            write(new File(root, "usr/lib/aarch64-linux-gnu/ld-linux-aarch64.so.1"), "loader");
            write(new File(root, RuntimeBaseTreeProbe.VERSION_FILE), String.valueOf(RuntimeBaseSpec.ROOTFS_VERSION));
            RootFS.configure(root, true, RuntimeBaseSpec.ROOTFS_VERSION);

            File box64 = new File(root, Box64Spec.BINARY_RELATIVE_PATH);
            write(box64, "box64");
            box64.setExecutable(true, false);
            write(new File(root, Box64Spec.RC_RELATIVE_PATH), "rc");

            File wine = new File(root, WineSpec.WINE_RELATIVE_PATH);
            File server = new File(root, WineSpec.WINESERVER_RELATIVE_PATH);
            write(wine, "wine");
            write(server, "wineserver");
            wine.setExecutable(true, false);
            server.setExecutable(true, false);

            AppCompatActivity activity = new AppCompatActivity();
            activity.getAssets().addAsset(RuntimeBaseSpec.ROOTFS_ASSET);
            activity.getAssets().addAsset(RuntimeBaseSpec.ROOTFS_PATCHES_ASSET);
            activity.getAssets().addAsset(Box64Spec.PACKAGE_ASSET);
            activity.getAssets().addAsset(Box64Spec.DEFAULT_RC_ASSET);
            PreferenceManager.getDefaultSharedPreferences(activity).edit()
                    .putString("box64_version", Box64Spec.VERSION)
                    .putString("current_box64_version", Box64Spec.VERSION)
                    .apply();

            WineInspection inspection = new WineInspector().inspect(new WinlatorWineProbe(activity));
            assertTrue(inspection.isLaunchReady(), "Wine baseline should be launch-ready");
            assertEquals(WineInspection.Status.CURRENT, inspection.getStatus(), "Wine status");

            server.setExecutable(false, false);
            inspection = new WineInspector().inspect(new WinlatorWineProbe(activity));
            assertFalse(inspection.isLaunchReady(), "non-runnable wineserver must block launch");
            assertEquals(WineInspection.Status.INCOMPLETE, inspection.getStatus(), "incomplete Wine status");

            System.out.println("WinlatorWineProbeSelfTest: all tests passed");
        } finally {
            delete(parent);
        }
    }

    private static File tempDir() {
        try { return Files.createTempDirectory("wine-probe").toFile(); }
        catch (IOException e) { throw new IllegalStateException(e); }
    }

    private static void write(File file, String value) {
        File parent = file.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) throw new IllegalStateException("mkdir failed");
        try (FileWriter writer = new FileWriter(file)) { writer.write(value); }
        catch (IOException e) { throw new IllegalStateException(e); }
    }

    private static boolean delete(File file) {
        if (file == null || !file.exists()) return true;
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) for (File child : children) delete(child);
        }
        return file.delete();
    }

    private static void assertTrue(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    private static void assertFalse(boolean value, String message) { assertTrue(!value, message); }

    private static void assertEquals(Object expected, Object actual, String message) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError(message + ": expected=" + expected + " actual=" + actual);
        }
    }
}
