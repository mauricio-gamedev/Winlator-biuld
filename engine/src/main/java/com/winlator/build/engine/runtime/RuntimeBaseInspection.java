package com.winlator.build.engine.runtime;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class RuntimeBaseInspection {
    public enum Status { MISSING, OUTDATED, CURRENT, FUTURE, INCOMPLETE }

    private final Status status;
    private final int installedVersion;
    private final boolean libcPresent;
    private final boolean dynamicLoaderPresent;
    private final boolean installAssetAvailable;
    private final boolean patchesAssetAvailable;
    private final List<String> launchIssues;
    private final List<String> maintenanceIssues;

    RuntimeBaseInspection(Status status, int installedVersion,
            boolean libcPresent, boolean dynamicLoaderPresent,
            boolean installAssetAvailable, boolean patchesAssetAvailable,
            List<String> launchIssues, List<String> maintenanceIssues) {
        this.status = status;
        this.installedVersion = installedVersion;
        this.libcPresent = libcPresent;
        this.dynamicLoaderPresent = dynamicLoaderPresent;
        this.installAssetAvailable = installAssetAvailable;
        this.patchesAssetAvailable = patchesAssetAvailable;
        this.launchIssues = Collections.unmodifiableList(new ArrayList<>(launchIssues));
        this.maintenanceIssues = Collections.unmodifiableList(new ArrayList<>(maintenanceIssues));
    }

    public Status getStatus() { return status; }
    public int getInstalledVersion() { return installedVersion; }
    public boolean isLibcPresent() { return libcPresent; }
    public boolean isDynamicLoaderPresent() { return dynamicLoaderPresent; }
    public boolean isInstallAssetAvailable() { return installAssetAvailable; }
    public boolean isPatchesAssetAvailable() { return patchesAssetAvailable; }
    public List<String> getLaunchIssues() { return launchIssues; }
    public List<String> getMaintenanceIssues() { return maintenanceIssues; }
    public boolean isLaunchReady() { return status == Status.CURRENT && launchIssues.isEmpty(); }
    public boolean canInstallOrRepair() { return installAssetAvailable; }
}
