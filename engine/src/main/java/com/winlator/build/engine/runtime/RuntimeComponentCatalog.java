package com.winlator.build.engine.runtime;

import com.winlator.build.engine.components.ComponentRegistry;
import com.winlator.build.engine.hardware.HardwareCapabilities.GpuFamily;

import java.util.Arrays;
import java.util.Collections;

public final class RuntimeComponentCatalog {
    public static final String WINE_MAIN = "wine.main-10.10";
    public static final String BOX64 = "box64.0.4.4";
    public static final String TURNIP = "renderer.turnip-26.1.0";
    public static final String VORTEK = "renderer.vortek-2.1";
    public static final String ZINK = "renderer.zink-22.2.5";
    public static final String VIRGL = "renderer.virgl-23.1.9";
    public static final String GLADIO = "renderer.gladio-1.1";
    public static final String DXVK_MAJOR = "dxvk.2.4.1";
    public static final String DXVK_MINOR = "dxvk.1.10.3";
    public static final String VKD3D = "vkd3d.2.14.1";
    public static final String WINED3D = "wined3d.10.10";

    private RuntimeComponentCatalog() {}

    public static ComponentRegistry createPinnedRegistry() {
        ComponentRegistry registry = new ComponentRegistry();

        registry.register(new ComponentRegistry.Component(
                WINE_MAIN, "wine", "10.10", "stable",
                Arrays.asList("x86", "x86_64"), Collections.singletonList(GpuFamily.GENERIC),
                26, null, false));

        registry.register(new ComponentRegistry.Component(
                BOX64, "box64", "0.4.4", "stable",
                Collections.singletonList("arm64-v8a"), Collections.singletonList(GpuFamily.GENERIC),
                26, null, false));

        registry.register(new ComponentRegistry.Component(
                TURNIP, "renderer", "26.1.0", "stable",
                Collections.singletonList("arm64-v8a"), Collections.singletonList(GpuFamily.ADRENO),
                26, null, true));

        registry.register(new ComponentRegistry.Component(
                VORTEK, "renderer", "2.1", "stable",
                Collections.singletonList("arm64-v8a"), Collections.singletonList(GpuFamily.GENERIC),
                26, null, true));

        registry.register(new ComponentRegistry.Component(
                ZINK, "renderer", "22.2.5", "stable",
                Collections.singletonList("arm64-v8a"), Collections.singletonList(GpuFamily.GENERIC),
                26, null, false));

        registry.register(new ComponentRegistry.Component(
                VIRGL, "renderer", "23.1.9", "stable",
                Collections.singletonList("arm64-v8a"), Collections.singletonList(GpuFamily.GENERIC),
                26, null, false));

        registry.register(new ComponentRegistry.Component(
                GLADIO, "renderer", "1.1", "stable",
                Collections.singletonList("arm64-v8a"), Collections.singletonList(GpuFamily.GENERIC),
                26, null, false));

        registry.register(new ComponentRegistry.Component(
                DXVK_MAJOR, "dxvk", "2.4.1", "stable",
                Arrays.asList("x86", "x86_64"), Collections.singletonList(GpuFamily.GENERIC),
                26, null, true));

        registry.register(new ComponentRegistry.Component(
                DXVK_MINOR, "dxvk", "1.10.3", "stable",
                Arrays.asList("x86", "x86_64"), Collections.singletonList(GpuFamily.GENERIC),
                26, null, true));

        registry.register(new ComponentRegistry.Component(
                VKD3D, "vkd3d", "2.14.1", "stable",
                Arrays.asList("x86", "x86_64"), Collections.singletonList(GpuFamily.GENERIC),
                26, null, true));

        registry.register(new ComponentRegistry.Component(
                WINED3D, "wined3d", "10.10", "stable",
                Arrays.asList("x86", "x86_64"), Collections.singletonList(GpuFamily.GENERIC),
                26, null, false));

        return registry;
    }
}
