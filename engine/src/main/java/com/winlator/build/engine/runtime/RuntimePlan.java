package com.winlator.build.engine.runtime;

import com.winlator.build.engine.components.ComponentRegistry;
import com.winlator.build.engine.graphics.RendererPolicy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class RuntimePlan {
    private final LaunchRequirements requirements;
    private final RendererPolicy.Decision rendererDecision;
    private final ComponentRegistry.Component wine;
    private final ComponentRegistry.Component box64;
    private final ComponentRegistry.Component vulkanRenderer;
    private final ComponentRegistry.Component openGlRenderer;
    private final ComponentRegistry.Component dxComponent;
    private final String wineIdentifier;
    private final String graphicsDriver;
    private final String dxWrapperId;
    private final String box64PresetId;
    private final List<ComponentRegistry.Component> requiredComponents;
    private final List<String> notes;
    private final List<String> errors;

    RuntimePlan(LaunchRequirements requirements,
            RendererPolicy.Decision rendererDecision,
            ComponentRegistry.Component wine,
            ComponentRegistry.Component box64,
            ComponentRegistry.Component vulkanRenderer,
            ComponentRegistry.Component openGlRenderer,
            ComponentRegistry.Component dxComponent,
            String wineIdentifier,
            String graphicsDriver,
            String dxWrapperId,
            String box64PresetId,
            List<ComponentRegistry.Component> requiredComponents,
            List<String> notes,
            List<String> errors) {
        this.requirements = requirements;
        this.rendererDecision = rendererDecision;
        this.wine = wine;
        this.box64 = box64;
        this.vulkanRenderer = vulkanRenderer;
        this.openGlRenderer = openGlRenderer;
        this.dxComponent = dxComponent;
        this.wineIdentifier = wineIdentifier == null ? "" : wineIdentifier;
        this.graphicsDriver = graphicsDriver == null ? "" : graphicsDriver;
        this.dxWrapperId = dxWrapperId == null ? "" : dxWrapperId;
        this.box64PresetId = box64PresetId == null ? "" : box64PresetId;
        this.requiredComponents = Collections.unmodifiableList(new ArrayList<>(requiredComponents));
        this.notes = Collections.unmodifiableList(new ArrayList<>(notes));
        this.errors = Collections.unmodifiableList(new ArrayList<>(errors));
    }

    public LaunchRequirements getRequirements() { return requirements; }
    public RendererPolicy.Decision getRendererDecision() { return rendererDecision; }
    public ComponentRegistry.Component getWine() { return wine; }
    public ComponentRegistry.Component getBox64() { return box64; }
    public ComponentRegistry.Component getVulkanRenderer() { return vulkanRenderer; }
    public ComponentRegistry.Component getOpenGlRenderer() { return openGlRenderer; }
    public ComponentRegistry.Component getDxComponent() { return dxComponent; }
    public String getWineIdentifier() { return wineIdentifier; }
    public String getGraphicsDriver() { return graphicsDriver; }
    public String getDxWrapperId() { return dxWrapperId; }
    public String getBox64PresetId() { return box64PresetId; }
    public List<ComponentRegistry.Component> getRequiredComponents() { return requiredComponents; }
    public List<String> getNotes() { return notes; }
    public List<String> getErrors() { return errors; }
    public boolean isValid() { return errors.isEmpty(); }
}
