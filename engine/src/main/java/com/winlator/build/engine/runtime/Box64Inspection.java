package com.winlator.build.engine.runtime;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class Box64Inspection {
    public enum Status { MISSING, VERSION_MISMATCH, CURRENT, INCOMPLETE }

    private final Status status;
    private final String selectedVersion;
    private final String currentExtractedVersion;
    private final boolean selectedPackageAvailable;
    private final boolean binaryPresent;
    private final boolean binaryRunnable;
    private final boolean defaultRcAssetAvailable;
    private final boolean rcFilePresent;
    private final List<String> launchIssues;
    private final List<String> maintenanceIssues;

    Box64Inspection(Status status, String selectedVersion, String currentExtractedVersion,
            boolean selectedPackageAvailable, boolean binaryPresent, boolean binaryRunnable,
            boolean defaultRcAssetAvailable, boolean rcFilePresent,
            List<String> launchIssues, List<String> maintenanceIssues) {
        this.status = status;
        this.selectedVersion = selectedVersion;
        this.currentExtractedVersion = currentExtractedVersion;
        this.selectedPackageAvailable = selectedPackageAvailable;
        this.binaryPresent = binaryPresent;
        this.binaryRunnable = binaryRunnable;
        this.defaultRcAssetAvailable = defaultRcAssetAvailable;
        this.rcFilePresent = rcFilePresent;
        this.launchIssues = Collections.unmodifiableList(new ArrayList<>(launchIssues));
        this.maintenanceIssues = Collections.unmodifiableList(new ArrayList<>(maintenanceIssues));
    }

    public Status getStatus() { return status; }
    public String getSelectedVersion() { return selectedVersion; }
    public String getCurrentExtractedVersion() { return currentExtractedVersion; }
    public boolean isSelectedPackageAvailable() { return selectedPackageAvailable; }
    public boolean isBinaryPresent() { return binaryPresent; }
    public boolean isBinaryRunnable() { return binaryRunnable; }
    public boolean isDefaultRcAssetAvailable() { return defaultRcAssetAvailable; }
    public boolean isRcFilePresent() { return rcFilePresent; }
    public List<String> getLaunchIssues() { return launchIssues; }
    public List<String> getMaintenanceIssues() { return maintenanceIssues; }

    public boolean isLaunchReady() {
        return status == Status.CURRENT && launchIssues.isEmpty();
    }

    public boolean canRepair() {
        return selectedPackageAvailable && defaultRcAssetAvailable;
    }
}
