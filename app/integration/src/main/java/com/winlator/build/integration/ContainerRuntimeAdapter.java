package com.winlator.build.integration;

import com.winlator.build.engine.graphics.RendererPolicy;
import com.winlator.build.engine.runtime.RuntimePlan;
import com.winlator.container.Container;
import com.winlator.core.WineInfo;

public final class ContainerRuntimeAdapter {
    public static final class Snapshot {
        private final String graphicsDriver;
        private final String graphicsDriverConfig;
        private final String dxWrapper;
        private final String dxWrapperConfig;
        private final String box64Preset;
        private final String wineVersion;

        private Snapshot(Container container) {
            graphicsDriver = container.getGraphicsDriver();
            graphicsDriverConfig = container.getGraphicsDriverConfig();
            dxWrapper = container.getDXWrapper();
            dxWrapperConfig = container.getDXWrapperConfig();
            box64Preset = container.getBox64Preset();
            wineVersion = container.getWineVersion();
        }

        public String getGraphicsDriver() { return graphicsDriver; }
        public String getGraphicsDriverConfig() { return graphicsDriverConfig; }
        public String getDxWrapper() { return dxWrapper; }
        public String getDxWrapperConfig() { return dxWrapperConfig; }
        public String getBox64Preset() { return box64Preset; }
        public String getWineVersion() { return wineVersion; }
    }

    public static final class ApplyResult {
        private final boolean success;
        private final Snapshot previousState;
        private final String error;

        private ApplyResult(boolean success, Snapshot previousState, String error) {
            this.success = success;
            this.previousState = previousState;
            this.error = error == null ? "" : error;
        }

        public boolean isSuccess() { return success; }
        public Snapshot getPreviousState() { return previousState; }
        public String getError() { return error; }
    }

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

    public static ApplyResult applyPlan(Container container, RuntimePlan plan,
            boolean resetRuntimeConfigs, boolean persist) {
        if (container == null) throw new IllegalArgumentException("container cannot be null");
        if (plan == null) throw new IllegalArgumentException("runtime plan cannot be null");
        if (!plan.isValid()) return new ApplyResult(false, null, "runtime plan is invalid");

        String plannedWine = plan.getWineIdentifier();
        String currentWine = container.getWineVersion();
        if (!isSameWineRuntime(currentWine, plannedWine)) {
            return new ApplyResult(false, null,
                    "container Wine runtime does not match the validated RuntimePlan");
        }

        Snapshot snapshot = new Snapshot(container);
        try {
            container.setGraphicsDriver(plan.getGraphicsDriver());
            if (resetRuntimeConfigs) container.setGraphicsDriverConfig("");

            if (!plan.getDxWrapperId().isEmpty()) {
                container.setDXWrapper(plan.getDxWrapperId());
                if (resetRuntimeConfigs) container.setDXWrapperConfig("");
            }

            container.setBox64Preset(plan.getBox64PresetId());

            String validationError = validateAppliedState(container, plan, resetRuntimeConfigs);
            if (!validationError.isEmpty()) {
                restore(container, snapshot, persist);
                return new ApplyResult(false, snapshot, validationError);
            }

            if (persist) container.saveData();
            return new ApplyResult(true, snapshot, "");
        } catch (RuntimeException e) {
            restore(container, snapshot, persist);
            String message = e.getMessage();
            return new ApplyResult(false, snapshot,
                    message == null || message.isEmpty() ? e.getClass().getSimpleName() : message);
        }
    }

    public static void rollback(Container container, Snapshot snapshot, boolean persist) {
        if (container == null) throw new IllegalArgumentException("container cannot be null");
        if (snapshot == null) throw new IllegalArgumentException("snapshot cannot be null");
        restore(container, snapshot, persist);
    }

    private static void restore(Container container, Snapshot snapshot, boolean persist) {
        container.setGraphicsDriver(snapshot.graphicsDriver);
        container.setGraphicsDriverConfig(snapshot.graphicsDriverConfig);
        container.setDXWrapper(snapshot.dxWrapper);
        container.setDXWrapperConfig(snapshot.dxWrapperConfig);
        container.setBox64Preset(snapshot.box64Preset);
        container.setWineVersion(snapshot.wineVersion);
        if (persist) container.saveData();
    }

    private static String validateAppliedState(Container container, RuntimePlan plan,
            boolean resetRuntimeConfigs) {
        if (!safeEquals(plan.getGraphicsDriver(), container.getGraphicsDriver())) {
            return "graphics driver was not applied correctly";
        }
        if (!plan.getDxWrapperId().isEmpty()
                && !safeEquals(plan.getDxWrapperId(), container.getDXWrapper())) {
            return "DirectX wrapper was not applied correctly";
        }
        if (!safeEquals(plan.getBox64PresetId(), container.getBox64Preset())) {
            return "Box64 preset was not applied correctly";
        }
        if (resetRuntimeConfigs && !container.getGraphicsDriverConfig().isEmpty()) {
            return "graphics driver config was not reset";
        }
        if (resetRuntimeConfigs && !plan.getDxWrapperId().isEmpty()
                && !container.getDXWrapperConfig().isEmpty()) {
            return "DirectX wrapper config was not reset";
        }
        return "";
    }

    private static boolean isSameWineRuntime(String currentWine, String plannedWine) {
        if (safeEquals(currentWine, plannedWine)) return true;
        return WineInfo.isMainWineVersion(currentWine) && WineInfo.isMainWineVersion(plannedWine);
    }

    private static boolean safeEquals(String first, String second) {
        return first == null ? second == null : first.equals(second);
    }
}
