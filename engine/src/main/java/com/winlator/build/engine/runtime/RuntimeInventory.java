package com.winlator.build.engine.runtime;

import com.winlator.build.engine.components.ComponentRegistry;

public interface RuntimeInventory {
    boolean isRuntimeBaseReady();

    default String explainRuntimeBaseUnavailable() {
        return "runtime base/rootfs is not ready";
    }

    boolean isComponentAvailable(ComponentRegistry.Component component);
    String explainUnavailable(ComponentRegistry.Component component);
}
