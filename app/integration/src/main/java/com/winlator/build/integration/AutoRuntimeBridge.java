package com.winlator.build.integration;

import android.content.Context;

import com.winlator.build.engine.graphics.RendererPolicy;
import com.winlator.build.engine.hardware.DeviceProfiler;
import com.winlator.build.engine.hardware.HardwareCapabilities;
import com.winlator.container.Container;

public final class AutoRuntimeBridge {
    public static final class Result {
        private final HardwareCapabilities hardware;
        private final RendererPolicy.Decision rendererDecision;

        private Result(HardwareCapabilities hardware, RendererPolicy.Decision rendererDecision) {
            this.hardware = hardware;
            this.rendererDecision = rendererDecision;
        }

        public HardwareCapabilities getHardware() { return hardware; }
        public RendererPolicy.Decision getRendererDecision() { return rendererDecision; }
    }

    private final DeviceProfiler deviceProfiler;
    private final RendererPolicy rendererPolicy;

    public AutoRuntimeBridge() {
        this(new DeviceProfiler(), new RendererPolicy());
    }

    AutoRuntimeBridge(DeviceProfiler deviceProfiler, RendererPolicy rendererPolicy) {
        this.deviceProfiler = deviceProfiler;
        this.rendererPolicy = rendererPolicy;
    }

    public Result profileAndApply(Context context, Container container, boolean resetDriverConfig) {
        HardwareCapabilities hardware = deviceProfiler.profile(new AndroidDeviceProbe(context));
        RendererPolicy.Decision decision = rendererPolicy.select(hardware);
        ContainerRuntimeAdapter.applyRendererDecision(container, decision, resetDriverConfig);
        return new Result(hardware, decision);
    }
}
