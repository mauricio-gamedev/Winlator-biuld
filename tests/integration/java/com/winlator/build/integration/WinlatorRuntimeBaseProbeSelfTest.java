package com.winlator.build.integration;

import android.content.Context;

import com.winlator.build.engine.runtime.RuntimeBaseInspection;
import com.winlator.build.engine.runtime.RuntimeBaseInspector;
import com.winlator.build.engine.runtime.RuntimeBaseSpec;
import com.winlator.xenvironment.RootFS;

import java.io.File;
import java.io.IOException;

public final class WinlatorRuntimeBaseProbeSelfTest {
    private WinlatorRuntimeBaseProbeSelfTest() {}

    public static void runAll() throws Exception {
        testCurrentRootFsIsReady();
        testOutdatedRootFsIsBlocked();
        testMissingLoaderIsIncomplete();
        testInventoryUsesStrictBaseInspection();
    }

    private static void testCurrentRootFsIsReady() throws Exception {
        File root = createRootFs(true, true);
        try {
            RootFS.configure(root, true, 22);
            Context context = contextWithAssets();
            RuntimeBaseInspection inspection = new RuntimeBaseInspector().inspect(
                    new WinlatorRuntimeBaseProbe(context));

            assertEquals(RuntimeBaseInspection.Status.CURRENT, inspection.getStatus(), "current RootFS status");
            assertTrue(inspection.isLaunchReady(), "current RootFS launch readiness");
            assertTrue(inspection.isLibcPresent(), "libc detection");
            assertTrue(inspection.isDynamicLoaderPresent(), "loader detection");
        } finally {
            delete(root);
        }
    }

    private static void testOutdatedRootFsIsBlocked() throws Exception {
        File root = createRootFs(true, true);
        try {
            RootFS.configure(root, true, 21);
            RuntimeBaseInspection inspection = new RuntimeBaseInspector().inspect(
                    new WinlatorRuntimeBaseProbe(contextWithAssets()));
            assertEquals(RuntimeBaseInspection.Status.OUTDATED, inspection.getStatus(), "outdated RootFS status");
            assertFalse(inspection.isLaunchReady(), "outdated RootFS must be blocked");
        } finally {
            delete(root);
        }
    }

    private static void testMissingLoaderIsIncomplete() throws Exception {
        File root = createRootFs(true, false);
        try {
            RootFS.configure(root, true, 22);
            RuntimeBaseInspection inspection = new RuntimeBaseInspector().inspect(
                    new WinlatorRuntimeBaseProbe(contextWithAssets()));
            assertEquals(RuntimeBaseInspection.Status.INCOMPLETE, inspection.getStatus(), "missing loader status");
            assertFalse(inspection.isLaunchReady(), "missing loader must block launch");
        } finally {
            delete(root);
        }
    }

    private static void testInventoryUsesStrictBaseInspection() throws Exception {
        File root = createRootFs(true, true);
        try {
            Context context = contextWithAssets();
            RootFS.configure(root, true, 22);
            assertTrue(new WinlatorRuntimeInventory(context).isRuntimeBaseReady(), "inventory current base readiness");

            RootFS.configure(root, true, 21);
            assertFalse(new WinlatorRuntimeInventory(context).isRuntimeBaseReady(), "inventory outdated base readiness");
        } finally {
            delete(root);
        }
    }

    private static Context contextWithAssets() {
        Context context = new Context();
        context.getAssets().addAsset(RuntimeBaseSpec.ROOTFS_ASSET);
        context.getAssets().addAsset(RuntimeBaseSpec.ROOTFS_PATCHES_ASSET);
        return context;
    }

    private static File createRootFs(boolean libc, boolean loader) throws IOException {
        File root = new File(System.getProperty("java.io.tmpdir"),
                "winlator-base-test-" + System.nanoTime());
        File libDir = new File(root, "usr/lib/aarch64-linux-gnu");
        if (!libDir.mkdirs()) throw new IOException("unable to create test RootFS");
        if (libc && !new File(libDir, "libc.so.6").createNewFile()) {
            throw new IOException("unable to create libc test file");
        }
        if (loader && !new File(libDir, "ld-linux-aarch64.so.1").createNewFile()) {
            throw new IOException("unable to create loader test file");
        }
        return root;
    }

    private static void delete(File file) {
        if (file == null || !file.exists()) return;
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) for (File child : children) delete(child);
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
}
