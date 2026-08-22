package com.winlator.build.engine;

import com.winlator.build.engine.components.ComponentRegistry;
import com.winlator.build.engine.hardware.HardwareCapabilities;
import com.winlator.build.engine.runtime.LaunchRequirements;
import com.winlator.build.engine.runtime.RuntimeComponentCatalog;
import com.winlator.build.engine.runtime.RuntimeInventory;
import com.winlator.build.engine.runtime.RuntimePlan;
import com.winlator.build.engine.runtime.RuntimePlanner;
import com.winlator.build.engine.runtime.RuntimeReadiness;
import com.winlator.build.engine.runtime.RuntimeReadinessChecker;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public final class RuntimeReadinessSelfTest {
    public static void main(String[] args) {
        testReadyInventory();
        testMissingDxvkBlocksReadiness();
        testInvalidRootfsBlocksReadiness();
        System.out.println("RuntimeReadinessSelfTest: all tests passed");
    }

    private static void testReadyInventory() {
        RuntimePlan plan = createMaliPlan();
        RuntimeReadiness readiness = new RuntimeReadinessChecker().check(
                plan, new FakeInventory(true));
        assertTrue(readiness.isReady(), "complete inventory should be ready");
        assertEquals(0, readiness.getMissingComponentIds().size(), "missing component count");
    }

    private static void testMissingDxvkBlocksReadiness() {
        RuntimePlan plan = createMaliPlan();
        FakeInventory inventory = new FakeInventory(true);
        inventory.missing.add(RuntimeComponentCatalog.DXVK_MINOR);

        RuntimeReadiness readiness = new RuntimeReadinessChecker().check(plan, inventory);
        assertFalse(readiness.isReady(), "missing DXVK must block readiness");
        assertTrue(readiness.getMissingComponentIds().contains(RuntimeComponentCatalog.DXVK_MINOR),
                "missing DXVK id should be reported");
    }

    private static void testInvalidRootfsBlocksReadiness() {
        RuntimePlan plan = createMaliPlan();
        RuntimeReadiness readiness = new RuntimeReadinessChecker().check(
                plan, new FakeInventory(false));
        assertFalse(readiness.isReady(), "invalid rootfs must block readiness");
        assertFalse(readiness.isRuntimeBaseReady(), "runtime base flag");
    }

    private static RuntimePlan createMaliPlan() {
        HardwareCapabilities hardware = new HardwareCapabilities(
                36,
                Arrays.asList("arm64-v8a", "armeabi-v7a"),
                4L * 1024 * 1024 * 1024,
                "Mali-G52 MC2",
                "ARM",
                "OpenGL ES 3.2",
                1, 1, 0);
        return new RuntimePlanner().plan(
                hardware,
                LaunchRequirements.forGraphicsApi(LaunchRequirements.GraphicsApi.DIRECTX_9_11),
                RuntimeComponentCatalog.createPinnedRegistry());
    }

    private static final class FakeInventory implements RuntimeInventory {
        private final boolean rootfsReady;
        private final Set<String> missing = new HashSet<>();

        private FakeInventory(boolean rootfsReady) {
            this.rootfsReady = rootfsReady;
        }

        public boolean isRuntimeBaseReady() { return rootfsReady; }

        public boolean isComponentAvailable(ComponentRegistry.Component component) {
            return component != null && !missing.contains(component.getId());
        }

        public String explainUnavailable(ComponentRegistry.Component component) {
            return "missing test component: " + component.getId();
        }
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
