package com.winlator.build.engine.components;

import com.winlator.build.engine.hardware.HardwareCapabilities;
import com.winlator.build.engine.hardware.HardwareCapabilities.GpuFamily;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ComponentRegistry {
    public static final class Component {
        private final String id;
        private final String type;
        private final String status;
        private final List<String> architectures;
        private final List<GpuFamily> gpuFamilies;
        private final int minAndroidApi;
        private final Integer maxAndroidApi;
        private final boolean requiresVulkan;

        public Component(String id, String type, String status, List<String> architectures,
                List<GpuFamily> gpuFamilies, int minAndroidApi, Integer maxAndroidApi,
                boolean requiresVulkan) {
            if (id == null || id.trim().isEmpty()) throw new IllegalArgumentException("component id is required");
            this.id = id.trim();
            this.type = type == null ? "" : type.trim();
            this.status = status == null ? "" : status.trim();
            this.architectures = architectures == null ? Collections.<String>emptyList() : Collections.unmodifiableList(new ArrayList<>(architectures));
            this.gpuFamilies = gpuFamilies == null ? Collections.<GpuFamily>emptyList() : Collections.unmodifiableList(new ArrayList<>(gpuFamilies));
            this.minAndroidApi = minAndroidApi;
            this.maxAndroidApi = maxAndroidApi;
            this.requiresVulkan = requiresVulkan;
        }

        public String getId() { return id; }
        public String getType() { return type; }
        public String getStatus() { return status; }

        private static boolean isAndroidHostArchitecture(String architecture) {
            return "arm64-v8a".equalsIgnoreCase(architecture)
                    || "armeabi-v7a".equalsIgnoreCase(architecture)
                    || "aarch64".equalsIgnoreCase(architecture);
        }

        public boolean isCompatible(HardwareCapabilities hardware) {
            if (hardware == null) return false;
            if ("deprecated".equalsIgnoreCase(status)) return false;
            if (minAndroidApi > 0 && hardware.getAndroidApi() < minAndroidApi) return false;
            if (maxAndroidApi != null && hardware.getAndroidApi() > maxAndroidApi) return false;
            if (requiresVulkan && !hardware.hasVulkan()) return false;

            if (!architectures.isEmpty()) {
                boolean hasHostArchitectureConstraint = false;
                boolean hostArchitectureMatch = false;
                for (String architecture : architectures) {
                    if (isAndroidHostArchitecture(architecture)) {
                        hasHostArchitectureConstraint = true;
                        if (hardware.supportsAbi(architecture)) hostArchitectureMatch = true;
                    }
                }
                if (hasHostArchitectureConstraint && !hostArchitectureMatch) return false;
            }

            return gpuFamilies.isEmpty()
                    || gpuFamilies.contains(GpuFamily.GENERIC)
                    || gpuFamilies.contains(hardware.getGpuFamily());
        }
    }

    private final Map<String, Component> components = new LinkedHashMap<>();

    public synchronized void register(Component component) {
        if (component == null) throw new IllegalArgumentException("component cannot be null");
        if (components.containsKey(component.getId())) throw new IllegalStateException("duplicate component id: " + component.getId());
        components.put(component.getId(), component);
    }

    public synchronized Component get(String id) { return components.get(id); }
    public synchronized Collection<Component> all() { return Collections.unmodifiableList(new ArrayList<>(components.values())); }
    public synchronized List<Component> compatible(HardwareCapabilities hardware) {
        List<Component> result = new ArrayList<>();
        for (Component component : components.values()) if (component.isCompatible(hardware)) result.add(component);
        return Collections.unmodifiableList(result);
    }
}
