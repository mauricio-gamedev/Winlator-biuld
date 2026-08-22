package com.winlator.build.engine.runtime;

import com.winlator.build.engine.components.ComponentRegistry;
import com.winlator.build.engine.graphics.RendererPolicy;
import com.winlator.build.engine.graphics.RendererPolicy.Backend;
import com.winlator.build.engine.hardware.HardwareCapabilities;

import java.util.ArrayList;
import java.util.List;

public final class RuntimePlanner {
    public static final String WINE_MAIN_IDENTIFIER = "wine-10.10-custom";
    public static final String BOX64_CONSERVATIVE = "CONSERVATIVE";
    public static final String BOX64_INTERMEDIATE = "INTERMEDIATE";

    private final RendererPolicy rendererPolicy;

    public RuntimePlanner() {
        this(new RendererPolicy());
    }

    public RuntimePlanner(RendererPolicy rendererPolicy) {
        if (rendererPolicy == null) throw new IllegalArgumentException("rendererPolicy is required");
        this.rendererPolicy = rendererPolicy;
    }

    public RuntimePlan plan(HardwareCapabilities hardware, LaunchRequirements requirements,
            ComponentRegistry registry) {
        if (hardware == null || registry == null) {
            throw new IllegalArgumentException("hardware and component registry are required");
        }
        if (requirements == null) requirements = LaunchRequirements.defaults();

        List<ComponentRegistry.Component> required = new ArrayList<>();
        List<String> notes = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        RendererPolicy.Decision renderer = rendererPolicy.select(hardware);
        ComponentRegistry.Component wine = require(
                RuntimeComponentCatalog.WINE_MAIN, hardware, registry, required, errors);
        ComponentRegistry.Component box64 = require(
                RuntimeComponentCatalog.BOX64, hardware, registry, required, errors);

        ComponentRegistry.Component vulkanRenderer = null;
        if (renderer.getVulkanBackend() != Backend.NONE) {
            vulkanRenderer = require(rendererComponentId(renderer.getVulkanBackend()),
                    hardware, registry, required, errors);
        }
        ComponentRegistry.Component openGlRenderer = require(
                rendererComponentId(renderer.getOpenGlBackend()),
                hardware, registry, required, errors);

        String dxWrapperId = "";
        ComponentRegistry.Component dxComponent = null;
        LaunchRequirements.GraphicsApi graphicsApi = requirements.getGraphicsApi();

        if (graphicsApi == LaunchRequirements.GraphicsApi.OPENGL) {
            notes.add("Native/OpenGL guest path selected; no DirectX wrapper is forced by the planner.");
        } else if (graphicsApi == LaunchRequirements.GraphicsApi.DIRECTX_LEGACY) {
            dxWrapperId = "wined3d";
            dxComponent = require(RuntimeComponentCatalog.WINED3D,
                    hardware, registry, required, errors);
            notes.add("Legacy DirectX stays on the conservative WineD3D baseline in Core 0.1.");
        } else if (graphicsApi == LaunchRequirements.GraphicsApi.DIRECTX_12) {
            dxWrapperId = "vkd3d";
            if (renderer.getVulkanBackend() == Backend.NONE || !hardware.supportsVulkanAtLeast(1, 1)) {
                errors.add("DirectX 12 requires a usable Vulkan path; no VKD3D plan can be produced.");
            } else {
                dxComponent = require(RuntimeComponentCatalog.VKD3D,
                        hardware, registry, required, errors);
            }
        } else {
            if (renderer.getVulkanBackend() != Backend.NONE && hardware.supportsVulkanAtLeast(1, 1)) {
                dxWrapperId = "dxvk";
                String dxvkId = selectDxvk(renderer, hardware);
                dxComponent = require(dxvkId, hardware, registry, required, errors);
                if (RuntimeComponentCatalog.DXVK_MAJOR.equals(dxvkId)) {
                    notes.add("DXVK 2.4.1 selected using the pinned upstream compatibility rule.");
                } else {
                    notes.add("DXVK 1.10.3 selected for the lower Vulkan compatibility path.");
                }
            } else {
                dxWrapperId = "wined3d";
                dxComponent = require(RuntimeComponentCatalog.WINED3D,
                        hardware, registry, required, errors);
                notes.add("No usable Vulkan path detected; DirectX falls back to WineD3D.");
            }
        }

        String box64Preset = renderer.isConservativeMemoryMode()
                ? BOX64_CONSERVATIVE : BOX64_INTERMEDIATE;
        if (renderer.isConservativeMemoryMode()) {
            notes.add("Low-memory device: use Box64 CONSERVATIVE rather than PERFORMANCE.");
        } else {
            notes.add("Use Box64 INTERMEDIATE as the Core 0.1 balanced baseline.");
        }

        return new RuntimePlan(
                requirements,
                renderer,
                wine,
                box64,
                vulkanRenderer,
                openGlRenderer,
                dxComponent,
                WINE_MAIN_IDENTIFIER,
                renderer.toUpstreamGraphicsDriver(),
                dxWrapperId,
                box64Preset,
                required,
                notes,
                errors);
    }

    private static String selectDxvk(RendererPolicy.Decision renderer, HardwareCapabilities hardware) {
        if (renderer.getVulkanBackend() == Backend.TURNIP) return RuntimeComponentCatalog.DXVK_MAJOR;
        if (renderer.getVulkanBackend() == Backend.VORTEK && hardware.supportsVulkanAtLeast(1, 3)) {
            return RuntimeComponentCatalog.DXVK_MAJOR;
        }
        return RuntimeComponentCatalog.DXVK_MINOR;
    }

    private static String rendererComponentId(Backend backend) {
        switch (backend) {
            case TURNIP: return RuntimeComponentCatalog.TURNIP;
            case VORTEK: return RuntimeComponentCatalog.VORTEK;
            case ZINK: return RuntimeComponentCatalog.ZINK;
            case VIRGL: return RuntimeComponentCatalog.VIRGL;
            case GLADIO: return RuntimeComponentCatalog.GLADIO;
            default: return "";
        }
    }

    private static ComponentRegistry.Component require(String id, HardwareCapabilities hardware,
            ComponentRegistry registry, List<ComponentRegistry.Component> required,
            List<String> errors) {
        if (id == null || id.isEmpty()) return null;
        ComponentRegistry.Component component = registry.get(id);
        if (component == null) {
            errors.add("Missing runtime component: " + id);
            return null;
        }
        if (!component.isCompatible(hardware)) {
            errors.add("Runtime component is not compatible with this device: " + id);
            return null;
        }
        if (!required.contains(component)) required.add(component);
        return component;
    }
}
