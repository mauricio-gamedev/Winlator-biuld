package com.winlator.build.engine.runtime;

import java.util.ArrayList;
import java.util.List;

public final class Box64Inspector {
    public Box64Inspection inspect(Box64Probe probe) {
        if (probe == null) throw new IllegalArgumentException("probe is required");

        String selected = normalize(probe.getSelectedVersion());
        String current = normalize(probe.getCurrentExtractedVersion());
        boolean packageAvailable = probe.isSelectedPackageAvailable();
        boolean binaryPresent = probe.isBinaryPresent();
        boolean binaryRunnable = binaryPresent && probe.isBinaryRunnable();
        boolean rcAssetAvailable = probe.isDefaultRcAssetAvailable();
        boolean rcFilePresent = probe.isRcFilePresent();

        List<String> launchIssues = new ArrayList<>();
        List<String> maintenanceIssues = new ArrayList<>();
        Box64Inspection.Status status;

        if (selected.isEmpty()) {
            status = Box64Inspection.Status.INCOMPLETE;
            launchIssues.add("selected Box64 version is missing");
        } else if (current.isEmpty()) {
            status = binaryPresent
                    ? Box64Inspection.Status.VERSION_MISMATCH
                    : Box64Inspection.Status.MISSING;
            launchIssues.add("Box64 " + selected + " has not been recorded as extracted");
        } else if (!selected.equals(current)) {
            status = Box64Inspection.Status.VERSION_MISMATCH;
            launchIssues.add("selected Box64 " + selected
                    + " differs from extracted Box64 " + current);
        } else if (!binaryPresent || !binaryRunnable) {
            status = Box64Inspection.Status.INCOMPLETE;
        } else {
            status = Box64Inspection.Status.CURRENT;
        }

        if (!binaryPresent) {
            launchIssues.add("Box64 binary is missing: /" + Box64Spec.BINARY_RELATIVE_PATH);
        } else if (!binaryRunnable) {
            launchIssues.add("Box64 binary is present but not runnable: /"
                    + Box64Spec.BINARY_RELATIVE_PATH);
        }

        if (!packageAvailable) {
            maintenanceIssues.add("selected Box64 package is unavailable: " + selected);
        }
        if (!rcAssetAvailable) {
            maintenanceIssues.add("default Box64 RC asset is unavailable: "
                    + Box64Spec.DEFAULT_RC_ASSET);
        }
        if (!rcFilePresent) {
            maintenanceIssues.add("Box64 RC file has not been deployed yet: /"
                    + Box64Spec.RC_RELATIVE_PATH);
        }

        return new Box64Inspection(status, selected, current,
                packageAvailable, binaryPresent, binaryRunnable,
                rcAssetAvailable, rcFilePresent, launchIssues, maintenanceIssues);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
