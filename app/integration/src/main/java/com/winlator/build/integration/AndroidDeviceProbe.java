package com.winlator.build.integration;

import android.app.ActivityManager;
import android.content.Context;
import android.os.Build;

import com.winlator.build.engine.hardware.DeviceProfiler;
import com.winlator.core.GPUHelper;

public final class AndroidDeviceProbe implements DeviceProfiler.Probe {
    private final Context context;
    private Integer vulkanVersion;

    public AndroidDeviceProbe(Context context) {
        if (context == null) throw new IllegalArgumentException("context cannot be null");
        Context appContext = context.getApplicationContext();
        this.context = appContext == null ? context : appContext;
    }

    @Override
    public int getAndroidApi() { return Build.VERSION.SDK_INT; }

    @Override
    public String[] getSupportedAbis() {
        return Build.SUPPORTED_ABIS == null ? new String[0] : Build.SUPPORTED_ABIS.clone();
    }

    @Override
    public long getTotalMemoryBytes() {
        ActivityManager manager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        if (manager == null) return 0L;
        ActivityManager.MemoryInfo info = new ActivityManager.MemoryInfo();
        manager.getMemoryInfo(info);
        return Math.max(0L, info.totalMem);
    }

    @Override
    public String getGpuRenderer() { return GPUHelper.glGetRenderer(context); }
    @Override
    public String getGpuVendor() { return GPUHelper.glGetVendor(context); }
    @Override
    public String getOpenGlEsVersion() { return GPUHelper.glGetVersion(context); }
    @Override
    public int getVulkanMajor() { return GPUHelper.vkVersionMajor(getVulkanVersion()); }
    @Override
    public int getVulkanMinor() { return GPUHelper.vkVersionMinor(getVulkanVersion()); }
    @Override
    public int getVulkanPatch() { return GPUHelper.vkVersionPatch(getVulkanVersion()); }

    private int getVulkanVersion() {
        if (vulkanVersion != null) return vulkanVersion;
        try {
            vulkanVersion = Math.max(0, GPUHelper.vkGetApiVersion());
        }
        catch (Throwable ignored) {
            vulkanVersion = 0;
        }
        return vulkanVersion;
    }
}
