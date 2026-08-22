package com.winlator.build.engine.hardware;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public final class HardwareCapabilities {
    public enum GpuFamily {
        ADRENO("adreno"), MALI("mali"), POWERVR("powervr"), XCLIPSE("xclipse"), GENERIC("generic");
        private final String id;
        GpuFamily(String id) { this.id = id; }
        public String getId() { return id; }
    }

    private final int androidApi;
    private final List<String> supportedAbis;
    private final long totalMemoryBytes;
    private final String gpuRenderer;
    private final String gpuVendor;
    private final String openGlEsVersion;
    private final int vulkanMajor;
    private final int vulkanMinor;
    private final int vulkanPatch;
    private final GpuFamily gpuFamily;

    public HardwareCapabilities(int androidApi, List<String> supportedAbis, long totalMemoryBytes,
            String gpuRenderer, String gpuVendor, String openGlEsVersion,
            int vulkanMajor, int vulkanMinor, int vulkanPatch) {
        if (androidApi < 1) throw new IllegalArgumentException("androidApi must be positive");
        if (totalMemoryBytes < 0) throw new IllegalArgumentException("totalMemoryBytes cannot be negative");
        if (vulkanMajor < 0 || vulkanMinor < 0 || vulkanPatch < 0) throw new IllegalArgumentException("Vulkan version values cannot be negative");
        this.androidApi = androidApi;
        this.supportedAbis = supportedAbis == null ? Collections.<String>emptyList() : Collections.unmodifiableList(new ArrayList<>(supportedAbis));
        this.totalMemoryBytes = totalMemoryBytes;
        this.gpuRenderer = safe(gpuRenderer);
        this.gpuVendor = safe(gpuVendor);
        this.openGlEsVersion = safe(openGlEsVersion);
        this.vulkanMajor = vulkanMajor;
        this.vulkanMinor = vulkanMinor;
        this.vulkanPatch = vulkanPatch;
        this.gpuFamily = classifyGpu(this.gpuRenderer, this.gpuVendor);
    }

    public int getAndroidApi() { return androidApi; }
    public List<String> getSupportedAbis() { return supportedAbis; }
    public long getTotalMemoryBytes() { return totalMemoryBytes; }
    public long getTotalMemoryMiB() { return totalMemoryBytes / (1024L * 1024L); }
    public String getGpuRenderer() { return gpuRenderer; }
    public String getGpuVendor() { return gpuVendor; }
    public String getOpenGlEsVersion() { return openGlEsVersion; }
    public int getVulkanMajor() { return vulkanMajor; }
    public int getVulkanMinor() { return vulkanMinor; }
    public int getVulkanPatch() { return vulkanPatch; }
    public GpuFamily getGpuFamily() { return gpuFamily; }
    public boolean hasVulkan() { return vulkanMajor > 0; }
    public boolean supportsVulkanAtLeast(int major, int minor) { if (vulkanMajor != major) return vulkanMajor > major; return vulkanMinor >= minor; }
    public boolean supportsAbi(String abi) { if (abi == null) return false; for (String value : supportedAbis) if (abi.equalsIgnoreCase(value)) return true; return false; }
    public boolean isArm64Device() { return supportsAbi("arm64-v8a") || supportsAbi("aarch64"); }

    public static GpuFamily classifyGpu(String renderer, String vendor) {
        String text = (safe(renderer) + " " + safe(vendor)).toLowerCase(Locale.ROOT);
        if (text.contains("adreno") || text.contains("qualcomm")) return GpuFamily.ADRENO;
        if (text.contains("mali") || text.contains("arm ltd") || text.contains("arm limited")) return GpuFamily.MALI;
        if (text.contains("xclipse")) return GpuFamily.XCLIPSE;
        if (text.contains("powervr") || text.contains("imagination")) return GpuFamily.POWERVR;
        return GpuFamily.GENERIC;
    }

    private static String safe(String value) { return value == null ? "" : value.trim(); }
}
