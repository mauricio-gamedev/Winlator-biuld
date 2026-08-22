package com.winlator.build.integration;

import android.content.Context;

import com.winlator.build.engine.components.ComponentRegistry;
import com.winlator.build.engine.runtime.RuntimeBaseInspection;
import com.winlator.build.engine.runtime.RuntimeBaseInspector;
import com.winlator.build.engine.runtime.RuntimeComponentCatalog;
import com.winlator.build.engine.runtime.RuntimeInventory;
import com.winlator.core.GeneralComponents;

import java.util.List;

public final class WinlatorRuntimeInventory implements RuntimeInventory {
    private final Context context;
    private final RuntimeBaseInspection runtimeBaseInspection;

    public WinlatorRuntimeInventory(Context context) {
        if (context == null) throw new IllegalArgumentException("context is required");
        Context appContext = context.getApplicationContext();
        this.context = appContext != null ? appContext : context;
        this.runtimeBaseInspection = new RuntimeBaseInspector().inspect(
                new WinlatorRuntimeBaseProbe(this.context));
    }

    @Override
    public boolean isRuntimeBaseReady() {
        return runtimeBaseInspection.isLaunchReady();
    }

    @Override
    public String explainRuntimeBaseUnavailable() {
        StringBuilder builder = new StringBuilder();
        for (String issue : runtimeBaseInspection.getLaunchIssues()) {
            if (issue == null || issue.isEmpty()) continue;
            if (builder.length() > 0) builder.append("; ");
            builder.append(issue);
        }
        return builder.length() == 0 ? "runtime base/rootfs is not ready" : builder.toString();
    }

    public RuntimeBaseInspection getRuntimeBaseInspection() {
        return runtimeBaseInspection;
    }

    @Override
    public boolean isComponentAvailable(ComponentRegistry.Component component) {
        if (component == null) return false;

        String id = component.getId();
        if (RuntimeComponentCatalog.WINE_MAIN.equals(id)
                || RuntimeComponentCatalog.VORTEK.equals(id)
                || RuntimeComponentCatalog.ZINK.equals(id)
                || RuntimeComponentCatalog.VIRGL.equals(id)
                || RuntimeComponentCatalog.GLADIO.equals(id)) {
            return true;
        }

        GeneralComponents.Type type = mapType(component);
        if (type == null) return false;

        String version = component.getVersion();
        if (GeneralComponents.isBuiltinComponent(type, version)) return true;
        return containsIgnoreCase(GeneralComponents.getInstalledComponentNames(type, context), version);
    }

    @Override
    public String explainUnavailable(ComponentRegistry.Component component) {
        if (component == null) return "runtime component metadata is missing";
        return "runtime component is not bundled or installed: "
                + component.getId() + " (" + component.getVersion() + ")";
    }

    private static GeneralComponents.Type mapType(ComponentRegistry.Component component) {
        String type = component.getType();
        if ("box64".equalsIgnoreCase(type)) return GeneralComponents.Type.BOX64;
        if ("dxvk".equalsIgnoreCase(type)) return GeneralComponents.Type.DXVK;
        if ("vkd3d".equalsIgnoreCase(type)) return GeneralComponents.Type.VKD3D;
        if ("wined3d".equalsIgnoreCase(type)) return GeneralComponents.Type.WINED3D;
        if (RuntimeComponentCatalog.TURNIP.equals(component.getId())) return GeneralComponents.Type.TURNIP;
        return null;
    }

    private static boolean containsIgnoreCase(List<String> values, String expected) {
        if (values == null || expected == null) return false;
        for (String value : values) {
            if (expected.equalsIgnoreCase(value)) return true;
        }
        return false;
    }
}
