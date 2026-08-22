package com.winlator.build.engine.runtime;

public final class RuntimeBaseInspectorSelfTest {
    private RuntimeBaseInspectorSelfTest() {}

    public static void runAll() {
        testMissingRootFs();
        testOutdatedRootFs();
        testCurrentRootFs();
        testIncompleteGlibcRuntime();
        testFutureRootFsBlocked();
        testMissingInstallAssetDoesNotBlockLaunch();
    }

    private static void testMissingRootFs() {
        RuntimeBaseInspection result = inspect(false, 0, false, false, true, true);
        assertEquals(RuntimeBaseInspection.Status.MISSING, result.getStatus(), "missing RootFS status");
        assertFalse(result.isLaunchReady(), "missing RootFS launch readiness");
    }

    private static void testOutdatedRootFs() {
        RuntimeBaseInspection result = inspect(true, 21, true, true, true, true);
        assertEquals(RuntimeBaseInspection.Status.OUTDATED, result.getStatus(), "outdated RootFS status");
        assertFalse(result.isLaunchReady(), "outdated RootFS launch readiness");
    }

    private static void testCurrentRootFs() {
        RuntimeBaseInspection result = inspect(true, 22, true, true, true, true);
        assertEquals(RuntimeBaseInspection.Status.CURRENT, result.getStatus(), "current RootFS status");
        assertTrue(result.isLaunchReady(), "current RootFS launch readiness");
        assertTrue(result.canInstallOrRepair(), "current RootFS maintenance media");
    }

    private static void testIncompleteGlibcRuntime() {
        RuntimeBaseInspection result = inspect(true, 22, false, true, true, true);
        assertEquals(RuntimeBaseInspection.Status.INCOMPLETE, result.getStatus(), "incomplete glibc status");
        assertFalse(result.isLaunchReady(), "incomplete glibc launch readiness");
        assertTrue(contains(result.getLaunchIssues(), "libc.so.6"), "missing libc diagnostic");
    }

    private static void testFutureRootFsBlocked() {
        RuntimeBaseInspection result = inspect(true, 23, true, true, true, true);
        assertEquals(RuntimeBaseInspection.Status.FUTURE, result.getStatus(), "future RootFS status");
        assertFalse(result.isLaunchReady(), "future RootFS must require validation");
    }

    private static void testMissingInstallAssetDoesNotBlockLaunch() {
        RuntimeBaseInspection result = inspect(true, 22, true, true, false, true);
        assertTrue(result.isLaunchReady(), "missing repair asset should not block existing runtime launch");
        assertFalse(result.canInstallOrRepair(), "missing repair asset maintenance readiness");
        assertTrue(contains(result.getMaintenanceIssues(), RuntimeBaseSpec.ROOTFS_ASSET), "repair asset diagnostic");
    }

    private static RuntimeBaseInspection inspect(final boolean valid, final int version,
            final boolean libc, final boolean loader, final boolean installAsset,
            final boolean patchesAsset) {
        return new RuntimeBaseInspector().inspect(new RuntimeBaseProbe() {
            public boolean isRootFsValid() { return valid; }
            public int getRootFsVersion() { return version; }
            public boolean hasLibcSo6() { return libc; }
            public boolean hasArm64DynamicLoader() { return loader; }
            public boolean isRootFsInstallAssetAvailable() { return installAsset; }
            public boolean isRootFsPatchesAssetAvailable() { return patchesAsset; }
        });
    }

    private static boolean contains(Iterable<String> values, String text) {
        for (String value : values) if (value != null && value.contains(text)) return true;
        return false;
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
