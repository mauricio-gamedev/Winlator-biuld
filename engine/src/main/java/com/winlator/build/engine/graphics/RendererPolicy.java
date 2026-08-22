package com.winlator.build.engine.graphics;

import com.winlator.build.engine.hardware.HardwareCapabilities;
import com.winlator.build.engine.hardware.HardwareCapabilities.GpuFamily;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class RendererPolicy {
    public enum Backend {
        NONE(""), TURNIP("turnip"), VORTEK("vortek"), ZINK("zink"), VIRGL("virgl"), GLADIO("gladio");
        private final String upstreamId;
        Backend(String upstreamId) { this.upstreamId = upstreamId; }
        public String getUpstreamId() { return upstreamId; }
    }

    public static final class Decision {
        private final Backend vulkanBackend;
        private final Backend openGlBackend;
        private final List<Backend> fallbackOrder;
        private final String reason;
        private final boolean conservativeMemoryMode;

        private Decision(Backend vulkanBackend, Backend openGlBackend, List<Backend> fallbackOrder,
                String reason, boolean conservativeMemoryMode) {
            this.vulkanBackend = vulkanBackend;
            this.openGlBackend = openGlBackend;
            this.fallbackOrder = Collections.unmodifiableList(new ArrayList<>(fallbackOrder));
            this.reason = reason;
            this.conservativeMemoryMode = conservativeMemoryMode;
        }

        public Backend getVulkanBackend() { return vulkanBackend; }
        public Backend getOpenGlBackend() { return openGlBackend; }
        public List<Backend> getFallbackOrder() { return fallbackOrder; }
        public String getReason() { return reason; }
        public boolean isConservativeMemoryMode() { return conservativeMemoryMode; }
        public String toUpstreamGraphicsDriver() {
            if (vulkanBackend == Backend.NONE) return openGlBackend.getUpstreamId();
            return vulkanBackend.getUpstreamId() + "," + openGlBackend.getUpstreamId();
        }
    }

    public Decision select(HardwareCapabilities hardware) {
        if (hardware == null) throw new IllegalArgumentException("hardware cannot be null");
        boolean lowMemory = hardware.getTotalMemoryBytes() > 0 && hardware.getTotalMemoryMiB() <= 4096;
        boolean usableVulkan = hardware.supportsVulkanAtLeast(1, 1);
        GpuFamily family = hardware.getGpuFamily();

        if (family == GpuFamily.ADRENO && usableVulkan) {
            return decision(Backend.TURNIP, Backend.GLADIO,
                    new Backend[]{Backend.VORTEK, Backend.ZINK, Backend.VIRGL},
                    "Adreno with Vulkan: prefer Turnip; retain Vortek/OpenGL fallbacks.", lowMemory);
        }
        if (family == GpuFamily.MALI && usableVulkan) {
            return decision(Backend.VORTEK, Backend.GLADIO,
                    new Backend[]{Backend.VIRGL, Backend.ZINK},
                    "Mali with Vulkan: prefer Vortek and never auto-select Turnip.", lowMemory);
        }
        if (family == GpuFamily.XCLIPSE && usableVulkan) {
            return decision(Backend.VORTEK, Backend.GLADIO,
                    new Backend[]{Backend.ZINK, Backend.VIRGL},
                    "Xclipse with Vulkan: use the generic Vulkan path first.", lowMemory);
        }
        if (usableVulkan) {
            return decision(Backend.VORTEK, Backend.GLADIO,
                    new Backend[]{Backend.ZINK, Backend.VIRGL},
                    "Unknown/PowerVR Vulkan GPU: start with the generic Vortek path.", lowMemory);
        }
        return decision(Backend.NONE, Backend.GLADIO,
                new Backend[]{Backend.VIRGL, Backend.ZINK},
                "No usable Vulkan 1.1 path detected: remain on OpenGL-compatible backends.", lowMemory);
    }

    private static Decision decision(Backend vulkan, Backend openGl, Backend[] fallback,
            String reason, boolean lowMemory) {
        List<Backend> fallbackList = new ArrayList<>();
        Collections.addAll(fallbackList, fallback);
        return new Decision(vulkan, openGl, fallbackList, reason, lowMemory);
    }
}
