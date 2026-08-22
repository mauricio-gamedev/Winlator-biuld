package com.winlator.build.engine.runtime;

public final class Box64InspectorSelfTest {
    private Box64InspectorSelfTest() {}

    public static void main(String[] args) {
        testCurrentBaseline();
        testVersionMismatch();
        testMissingBinary();
        testNonRunnableBinary();
        testMissingRcDoesNotBlockLaunch();
        testMissingPackageDoesNotBreakAlreadyExtractedRuntime();
        System.out.println("Box64InspectorSelfTest: all tests passed");
    }

    private static void testCurrentBaseline() {
        Box64Inspection result = inspect(Box64Spec.VERSION, Box64Spec.VERSION,
                true, true, true, true, true);
        assertEquals(Box64Inspection.Status.CURRENT, result.getStatus(), "current status");
        assertTrue(result.isLaunchReady(), "current Box64 must be launch ready");
        assertTrue(result.canRepair(), "current packaged Box64 must be repairable");
    }

    private static void testVersionMismatch() {
        Box64Inspection result = inspect("0.4.4", "0.4.3",
                true, true, true, true, true);
        assertEquals(Box64Inspection.Status.VERSION_MISMATCH, result.getStatus(),
                "version mismatch status");
        assertFalse(result.isLaunchReady(), "mismatched Box64 must not pass strict readiness");
    }

    private static void testMissingBinary() {
        Box64Inspection result = inspect(Box64Spec.VERSION, Box64Spec.VERSION,
                true, false, false, true, true);
        assertEquals(Box64Inspection.Status.INCOMPLETE, result.getStatus(), "missing binary status");
        assertFalse(result.isLaunchReady(), "missing Box64 binary must block launch readiness");
    }

    private static void testNonRunnableBinary() {
        Box64Inspection result = inspect(Box64Spec.VERSION, Box64Spec.VERSION,
                true, true, false, true, true);
        assertEquals(Box64Inspection.Status.INCOMPLETE, result.getStatus(), "non-runnable binary status");
        assertFalse(result.isLaunchReady(), "non-runnable Box64 binary must block launch readiness");
    }

    private static void testMissingRcDoesNotBlockLaunch() {
        Box64Inspection result = inspect(Box64Spec.VERSION, Box64Spec.VERSION,
                true, true, true, true, false);
        assertEquals(Box64Inspection.Status.CURRENT, result.getStatus(), "missing RC prelaunch status");
        assertTrue(result.isLaunchReady(), "RC is deployed by guest-launch preparation and must not pre-block launch");
        assertFalse(result.getMaintenanceIssues().isEmpty(), "missing RC must remain visible as maintenance state");
    }

    private static void testMissingPackageDoesNotBreakAlreadyExtractedRuntime() {
        Box64Inspection result = inspect(Box64Spec.VERSION, Box64Spec.VERSION,
                false, true, true, true, true);
        assertTrue(result.isLaunchReady(), "already extracted Box64 can remain launch ready without package asset");
        assertFalse(result.canRepair(), "missing Box64 package must block repair capability");
    }

    private static Box64Inspection inspect(final String selected, final String current,
            final boolean packageAvailable, final boolean binaryPresent,
            final boolean binaryRunnable, final boolean rcAssetAvailable,
            final boolean rcFilePresent) {
        return new Box64Inspector().inspect(new Box64Probe() {
            public String getSelectedVersion() { return selected; }
            public String getCurrentExtractedVersion() { return current; }
            public boolean isSelectedPackageAvailable() { return packageAvailable; }
            public boolean isBinaryPresent() { return binaryPresent; }
            public boolean isBinaryRunnable() { return binaryRunnable; }
            public boolean isDefaultRcAssetAvailable() { return rcAssetAvailable; }
            public boolean isRcFilePresent() { return rcFilePresent; }
        });
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
