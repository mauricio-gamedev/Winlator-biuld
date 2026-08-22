package com.winlator.build.engine.runtime;

import com.winlator.build.engine.components.ComponentRegistry;

public interface RuntimeInventory {
    boolean isRuntimeBaseReady();
    boolean isComponentAvailable(ComponentRegistry.Component component);
    String explainUnavailable(ComponentRegistry.Component component);
}
