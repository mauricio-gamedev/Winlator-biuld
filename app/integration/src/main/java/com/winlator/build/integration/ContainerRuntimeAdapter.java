package com.winlator.build.integration;

import com.winlator.build.engine.graphics.RendererPolicy;
import com.winlator.container.Container;

public final class ContainerRuntimeAdapter {
    private ContainerRuntimeAdapter() {}

    public static void applyRendererDecision(
            Container container,
            RendererPolicy.Decision decision,
            boolean resetDriverConfig) {
        if (container == null) throw new IllegalArgumentException("container cannot be null");
        if (decision == null) throw new IllegalArgumentException("decision cannot be null");

        container.setGraphicsDriver(decision.toUpstreamGraphicsDriver());
        if (resetDriverConfig) container.setGraphicsDriverConfig("");
    }
}
