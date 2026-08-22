package com.winlator.build.engine;

import com.winlator.build.engine.components.ComponentRegistry;
import com.winlator.build.engine.graphics.RendererPolicy;
import com.winlator.build.engine.hardware.DeviceProfiler;
import com.winlator.build.engine.hardware.HardwareCapabilities;
import com.winlator.build.engine.hardware.HardwareCapabilities.GpuFamily;
import com.winlator.build.engine.runtime.RuntimeManager;

import java.util.Arrays;
import java.util.Collections;

public final class EngineSelfTest {
    public static void main(String[] args) {
        testMaliPolicy();
        testAdrenoPolicy();
        testNoVulkanFallback();
        testComponentCompatibility();
        testGuestArchitectureDoesNotRejectArmHost();
        testRuntimeStateMachine();
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
