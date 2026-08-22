package com.winlator.build.engine;

import com.winlator.build.engine.components.ComponentRegistry;
import com.winlator.build.engine.graphics.RendererPolicy;
import com.winlator.build.engine.hardware.DeviceProfiler;
import com.winlator.build.engine.hardware.HardwareCapabilities;
import com.winlator.build.engine.hardware.HardwareCapabilities.GpuFamily;
import com.winlator.build.engine.runtime.LaunchRequirements;
import com.winlator.build.engine.runtime.RuntimeBaseInspectorSelfTest;
import com.winlator.build.engine.runtime.RuntimeBaseMaintenanceSelfTest;
import com.winlator.build.engine.runtime.RuntimeComponentCatalog;
import com.winlator.build.engine.runtime.RuntimeExecutionCoordinatorSelfTest;
import com.winlator.build.engine.runtime.RuntimeManager;
import com.winlator.build.engine.runtime.RuntimePlan;
import com.winlator.build.engine.runtime.RuntimePlanner;

import java.util.Arrays;
import java.util.Collections;

public final class EngineSelfTest {
    public static void main(String[] args) {
        testMaliPolicy();
        testAdrenoPolicy();
        testNoVulkanFallback();
        testComponentCompatibility();
        testGuestArchitectureDoesNotRejectArmHost();
        testPinnedCatalogVersions();
        testMaliVulkan11RuntimePlan();
        testMaliVulkan13RuntimePlan();
        testAdrenoRuntimePlan();
        testNoVulkanRuntimePlan();
        testDx12WithoutVulkanFailsPlanning();
        testRuntimeStateMachine();
        testRuntimePlanStateMachine();
        RuntimeExecutionCoordinatorSelfTest.runAll();
        RuntimeBaseInspectorSelfTest.runAll();
        RuntimeBaseMaintenanceSelfTest.runAll();
        System.out.println("EngineSelfTest: all tests passed");
    }

    private static void testMaliPolicy() {
        HardwareCapabilities mali = profile("Mali-G52 MC2", "ARM", 1, 1, 0, 4L * 1024 * 1024 * 1024);
        assertEquals(GpuFamily.MALI, mali.getGpuFamily(), "Mali family");
        RendererPolicy.Decision decision = new RendererPolicy().select(mali);
        assertEquals(RendererPolicy.Backend.VORTEK, decision.getVulkanBackend(), "Mali Vulkan backend");
        assertFalse(decision.getFallbackOrder().contains(RendererPolicy.Backend.TURNIP), "Mali must not auto-fallback to Turnip");
        assertTrue(decision.isConservativeMemoryMode(), "4 GiB device should use conservative memory mode");
    }

    private static void testAdrenoPolicy() {
        HardwareCapabilities adreno = profile("Adreno (TM) 740", "Qualcomm", 1, 3, 0, 8L * 1024 * 1024 * 1024);
        assertEquals(GpuFamily.ADRENO, adreno.getGpuFamily(), "Adreno family");
        assertEquals(RendererPolicy.Backend.TURNIP, new RendererPolicy().select(adreno).getVulkanBackend(), "Adreno Vulkan backend");
    }

    private static void testNoVulkanFallback() {
        HardwareCapabilities legacy = profile("PowerVR GE8320", "Imagination", 0, 0, 0, 3L * 1024 * 1024 * 1024);
        RendererPolicy.Decision decision = new RendererPolicy().select(legacy);
        assertEquals(RendererPolicy.Backend.NONE, decision.getVulkanBackend(), "No Vulkan backend");
        assertEquals(RendererPolicy.Backend.GLADIO, decision.getOpenGlBackend(), "OpenGL fallback");
    }

    private static void testComponentCompatibility() {
        HardwareCapabilities mali = profile("Mali-G52 MC2", "ARM", 1, 1, 0, 4L * 1024 * 1024 * 1024);
        ComponentRegistry registry = new ComponentRegistry();
        registry.register(new ComponentRegistry.Component("renderer.turnip", "renderer", "testing",
                Collections.singletonList("arm64-v8a"), Collections.singletonList(GpuFamily.ADRENO), 26, null, true));
        registry.register(new ComponentRegistry.Component("renderer.vortek", "renderer", "testing",
                Collections.singletonList("arm64-v8a"), Arrays.asList(GpuFamily.MALI, GpuFamily.ADRENO, GpuFamily.GENERIC), 26, null, true));
        assertEquals(1, registry.compatible(mali).size(), "Mali compatible component count");
        assertEquals("renderer.vortek", registry.compatible(mali).get(0).getId(), "Mali compatible renderer");
    }

    private static void testGuestArchitectureDoesNotRejectArmHost() {
        HardwareCapabilities mali = profile("Mali-G52 MC2", "ARM", 1, 1, 0, 4L * 1024 * 1024 * 1024);
        ComponentRegistry registry = new ComponentRegistry();
        registry.register(new ComponentRegistry.Component("dxvk.x64", "dxvk", "testing",
                Collections.singletonList("x86_64"), Collections.singletonList(GpuFamily.GENERIC), 26, null, true));
        assertEquals(1, registry.compatible(mali).size(), "Guest x86_64 component must remain valid on ARM64 host");
    }

    private static void testPinnedCatalogVersions() {
        ComponentRegistry registry = RuntimeComponentCatalog.createPinnedRegistry();
        assertEquals("10.10", registry.get(RuntimeComponentCatalog.WINE_MAIN).getVersion(), "Wine version");
        assertEquals("0.4.4", registry.get(RuntimeComponentCatalog.BOX64).getVersion(), "Box64 version");
        assertEquals("2.1", registry.get(RuntimeComponentCatalog.VORTEK).getVersion(), "Vortek version");
        assertEquals("2.4.1", registry.get(RuntimeComponentCatalog.DXVK_MAJOR).getVersion(), "major DXVK version");
        assertEquals("1.10.3", registry.get(RuntimeComponentCatalog.DXVK_MINOR).getVersion(), "minor DXVK version");
        assertEquals("2.14.1", registry.get(RuntimeComponentCatalog.VKD3D).getVersion(), "VKD3D version");
    }

    private static void testMaliVulkan11RuntimePlan() {
        HardwareCapabilities mali = profile("Mali-G52 MC2", "ARM", 1, 1, 0, 4L * 1024 * 1024 * 1024);
        RuntimePlan plan = defaultPlanner().plan(mali,
                LaunchRequirements.forGraphicsApi(LaunchRequirements.GraphicsApi.DIRECTX_9_11),
                RuntimeComponentCatalog.createPinnedRegistry());
        assertTrue(plan.isValid(), "Mali Vulkan 1.1 plan");
        assertEquals("vortek,gladio", plan.getGraphicsDriver(), "Mali graphics driver");
        assertEquals("dxvk", plan.getDxWrapperId(), "Mali DX wrapper");
        assertEquals("1.10.3", plan.getDxComponent().getVersion(), "Mali Vulkan 1.1 DXVK");
        assertEquals(RuntimePlanner.BOX64_CONSERVATIVE, plan.getBox64PresetId(), "4 GiB Box64 preset");
    }

    private static void testMaliVulkan13RuntimePlan() {
        HardwareCapabilities mali = profile("Mali-G610", "ARM", 1, 3, 0, 8L * 1024 * 1024 * 1024);
        RuntimePlan plan = defaultPlanner().plan(mali,
                LaunchRequirements.forGraphicsApi(LaunchRequirements.GraphicsApi.DIRECTX_9_11),
                RuntimeComponentCatalog.createPinnedRegistry());
        assertTrue(plan.isValid(), "Mali Vulkan 1.3 plan");
        assertEquals("2.4.1", plan.getDxComponent().getVersion(), "Mali Vulkan 1.3 DXVK");
        assertEquals(RuntimePlanner.BOX64_INTERMEDIATE, plan.getBox64PresetId(), "8 GiB Box64 preset");
    }

    private static void testAdrenoRuntimePlan() {
        HardwareCapabilities adreno = profile("Adreno (TM) 740", "Qualcomm", 1, 3, 0, 8L * 1024 * 1024 * 1024);
        RuntimePlan plan = defaultPlanner().plan(adreno,
                LaunchRequirements.forGraphicsApi(LaunchRequirements.GraphicsApi.DIRECTX_9_11),
                RuntimeComponentCatalog.createPinnedRegistry());
        assertTrue(plan.isValid(), "Adreno plan");
        assertEquals("turnip,gladio", plan.getGraphicsDriver(), "Adreno graphics driver");
        assertEquals("2.4.1", plan.getDxComponent().getVersion(), "Adreno DXVK");
    }

    private static void testNoVulkanRuntimePlan() {
        HardwareCapabilities legacy = profile("PowerVR GE8320", "Imagination", 0, 0, 0, 3L * 1024 * 1024 * 1024);
        RuntimePlan plan = defaultPlanner().plan(legacy,
                LaunchRequirements.forGraphicsApi(LaunchRequirements.GraphicsApi.DIRECTX_9_11),
                RuntimeComponentCatalog.createPinnedRegistry());
        assertTrue(plan.isValid(), "no Vulkan fallback plan");
        assertEquals("gladio", plan.getGraphicsDriver(), "no Vulkan graphics driver");
        assertEquals("wined3d", plan.getDxWrapperId(), "no Vulkan DX wrapper");
    }

    private static void testDx12WithoutVulkanFailsPlanning() {
        HardwareCapabilities legacy = profile("PowerVR GE8320", "Imagination", 0, 0, 0, 3L * 1024 * 1024 * 1024);
        RuntimePlan plan = defaultPlanner().plan(legacy,
                LaunchRequirements.forGraphicsApi(LaunchRequirements.GraphicsApi.DIRECTX_12),
                RuntimeComponentCatalog.createPinnedRegistry());
        assertFalse(plan.isValid(), "DX12 without Vulkan must fail planning");
        RuntimeManager manager = new RuntimeManager();
        assertEquals(RuntimeManager.State.FAILED, manager.prepare(plan).getState(), "invalid plan state");
    }

    private static void testRuntimeStateMachine() {
        HardwareCapabilities mali = profile("Mali-G52 MC2", "ARM", 1, 1, 0, 4L * 1024 * 1024 * 1024);
        ComponentRegistry registry = new ComponentRegistry();
        registry.register(new ComponentRegistry.Component("renderer.vortek", "renderer", "testing",
                Collections.singletonList("arm64-v8a"), Collections.singletonList(GpuFamily.GENERIC), 26, null, true));
        RuntimeManager manager = new RuntimeManager();
        assertEquals(RuntimeManager.State.READY, manager.prepare(mali, new RendererPolicy(), registry).getState(), "prepare state");
        assertEquals(RuntimeManager.State.RUNNING, manager.start().getState(), "start state");
        assertEquals(RuntimeManager.State.STOPPED, manager.stop().getState(), "stop state");
        assertEquals(RuntimeManager.State.IDLE, manager.reset().getState(), "reset state");
    }

    private static void testRuntimePlanStateMachine() {
        HardwareCapabilities mali = profile("Mali-G52 MC2", "ARM", 1, 1, 0, 4L * 1024 * 1024 * 1024);
        RuntimePlan plan = defaultPlanner().plan(mali, LaunchRequirements.defaults(), RuntimeComponentCatalog.createPinnedRegistry());
        RuntimeManager manager = new RuntimeManager();
        RuntimeManager.Snapshot prepared = manager.prepare(plan);
        assertEquals(RuntimeManager.State.READY, prepared.getState(), "runtime plan prepare state");
        assertEquals(plan, prepared.getRuntimePlan(), "runtime plan retained");
        assertEquals(RuntimeManager.State.RUNNING, manager.start().getState(), "runtime plan start state");
        assertEquals(RuntimeManager.State.STOPPED, manager.stop().getState(), "runtime plan stop state");
    }

    private static RuntimePlanner defaultPlanner() { return new RuntimePlanner(); }

    private static HardwareCapabilities profile(final String renderer, final String vendor,
            final int vkMajor, final int vkMinor, final int vkPatch, final long memoryBytes) {
        return new DeviceProfiler().profile(new DeviceProfiler.Probe() {
            public int getAndroidApi() { return 36; }
            public String[] getSupportedAbis() { return new String[]{"arm64-v8a", "armeabi-v7a"}; }
            public long getTotalMemoryBytes() { return memoryBytes; }
            public String getGpuRenderer() { return renderer; }
            public String getGpuVendor() { return vendor; }
            public String getOpenGlEsVersion() { return "OpenGL ES 3.2"; }
            public int getVulkanMajor() { return vkMajor; }
            public int getVulkanMinor() { return vkMinor; }
            public int getVulkanPatch() { return vkPatch; }
        });
    }

    private static void assertTrue(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    private static void assertFalse(boolean condition, String message) { assertTrue(!condition, message); }
    private static void assertEquals(Object expected, Object actual, String message) {
        if (expected == null ? actual != null : !expected.equals(actual)) throw new AssertionError(message + ": expected=" + expected + " actual=" + actual);
    }
}
