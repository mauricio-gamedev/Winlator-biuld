package com.winlator.build.engine.runtime;

import com.winlator.build.engine.components.ComponentRegistry;

import java.util.ArrayList;
import java.util.List;

public final class RuntimeReadinessChecker {
    public RuntimeReadiness check(RuntimePlan plan, RuntimeInventory inventory) {
        if (plan == null) throw new IllegalArgumentException("runtime plan is required");
        if (inventory == null) throw new IllegalArgumentException("runtime inventory is required");

        List<String> missing = new ArrayList<>();
        List<String> issues = new ArrayList<>();

        if (!plan.isValid()) {
            issues.addAll(plan.getErrors());
            return new RuntimeReadiness(false, missing, issues);
        }

        boolean runtimeBaseReady = inventory.isRuntimeBaseReady();
        if (!runtimeBaseReady) {
            String explanation = inventory.explainRuntimeBaseUnavailable();
            issues.add(explanation == null || explanation.isEmpty()
                    ? "runtime base/rootfs is not ready"
                    : explanation);
        }

        for (ComponentRegistry.Component component : plan.getRequiredComponents()) {
            if (!inventory.isComponentAvailable(component)) {
                missing.add(component.getId());
                String explanation = inventory.explainUnavailable(component);
                issues.add(explanation == null || explanation.isEmpty()
                        ? "runtime component is unavailable: " + component.getId()
                        : explanation);
            }
        }

        return new RuntimeReadiness(runtimeBaseReady, missing, issues);
    }
}
