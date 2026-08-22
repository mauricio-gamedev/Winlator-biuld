package com.winlator.build.engine.hardware;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class DeviceProfiler {
    public interface Probe {
        int getAndroidApi();
        String[] getSupportedAbis();
        long getTotalMemoryBytes();
        String getGpuRenderer();
        String getGpuVendor();
        String getOpenGlEsVersion();
        int getVulkanMajor();
        int getVulkanMinor();
        int getVulkanPatch();
    }

    public HardwareCapabilities profile(Probe probe) {
        if (probe == null) throw new IllegalArgumentException("probe cannot be null");
        String[] abiArray = probe.getSupportedAbis();
        List<String> abis = abiArray == null ? Collections.<String>emptyList() : Arrays.asList(abiArray.clone());
        return new HardwareCapabilities(
                Math.max(1, probe.getAndroidApi()),
                abis,
                Math.max(0L, probe.getTotalMemoryBytes()),
                probe.getGpuRenderer(),
                probe.getGpuVendor(),
                probe.getOpenGlEsVersion(),
                Math.max(0, probe.getVulkanMajor()),
                Math.max(0, probe.getVulkanMinor()),
                Math.max(0, probe.getVulkanPatch()));
    }
}
