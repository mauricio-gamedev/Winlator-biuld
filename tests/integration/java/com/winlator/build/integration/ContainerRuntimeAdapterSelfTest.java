package com.winlator.build.integration;

import com.winlator.build.engine.hardware.HardwareCapabilities;
import com.winlator.build.engine.runtime.LaunchRequirements;
import com.winlator.build.engine.runtime.RuntimeComponentCatalog;
import com.winlator.build.engine.runtime.RuntimePlan;
import com.winlator.build.engine.runtime.RuntimePlanner;
import com.winlator.container.Container;

import java.util.Arrays;

public final class ContainerRuntimeAdapterSelfTest {
    public static void main(String[] args) {
        testApplyAndRollback();
        testWineMismatchRejectsWithoutMutation();
        System.out.println("ContainerRuntimeAdapterSelfTest: all tests passed");
    }

    private static void testApplyAndRollback() {
        Container container = new Container();
        RuntimePlan plan = createMaliPlan();

        ContainerRuntimeAdapter.ApplyResult result =
                ContainerRuntimeAdapter.applyPlan(container, plan, true, true);

        assertTrue(result.isSuccess(), "plan should apply");
        assertEquals("vortek,gladio", container.getGraphicsDriver(), "graphics driver");
        assertEquals("dxvk", container.getDXWrapper(), "DX wrapper");
        assertEquals("CONSERVATIVE", container.getBox64Preset(), "Box64 preset");
        assertEquals("", container.getGraphicsDriverConfig(), "graphics config reset");
        assertEquals("", container.getDXWrapperConfig(), "DX config reset");
        assertEquals(1, container.getSaveCount(), "apply save count");

        ContainerRuntimeAdapter.rollback(container, result.getPreviousState(), true);

        assertEquals("old,gl", container.getGraphicsDriver(), "rollback graphics driver");
        assertEquals("old-dx", container.getDXWrapper(), "rollback DX wrapper");
        assertEquals("OLD", container.getBox64Preset(), "rollback Box64 preset");
        assertEquals("graphics-config", container.getGraphicsDriverConfig(), "rollback graphics config");
        assertEquals("dx-config", container.getDXWrapperConfig(), "rollback DX config");
        assertEquals(2, container.getSaveCount(), "rollback save count");
    }

    private static void testWineMismatchRejectsWithoutMutation() {
        Container container = new Container();
        container.setWineVersion("wine-9.0-x86_64");
        RuntimePlan plan = createMaliPlan();

        ContainerRuntimeAdapter.ApplyResult result =
                ContainerRuntimeAdapter.applyPlan(container, plan, true, true);

        assertFalse(result.isSuccess(), "Wine mismatch must reject plan");
        assertEquals("old,gl", container.getGraphicsDriver(), "rejected plan must not mutate driver");
        assertEquals("old-dx", container.getDXWrapper(), "rejected plan must not mutate DX wrapper");
        assertEquals(0, container.getSaveCount(), "rejected plan must not persist");
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
