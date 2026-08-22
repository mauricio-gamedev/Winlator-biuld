package com.winlator.build.engine.runtime;

import java.util.ArrayList;
import java.util.List;

public final class RuntimeBaseInspector {
    public RuntimeBaseInspection inspect(RuntimeBaseProbe probe) {
        if (probe == null) throw new IllegalArgumentException("runtime base probe is required");

        List<String> launchIssues = new ArrayList<>();
        List<String> maintenanceIssues = new ArrayList<>();

        boolean valid = probe.isRootFsValid();
        int version = valid ? Math.max(0, probe.getRootFsVersion()) : 0;
        boolean libc = valid && probe.hasLibcSo6();
        boolean loader = valid && probe.hasArm64DynamicLoader();
        boolean installAsset = probe.isRootFsInstallAssetAvailable();
        boolean patchesAsset = probe.isRootFsPatchesAssetAvailable();

        RuntimeBaseInspection.Status status;
        if (!valid) {
            status = RuntimeBaseInspection.Status.MISSING;
            launchIssues.add("RootFS is missing or invalid");
        } else if (version < RuntimeBaseSpec.ROOTFS_VERSION) {
            status = RuntimeBaseInspection.Status.OUTDATED;
            launchIssues.add("RootFS version " + version + " is older than pinned baseline "
                    + RuntimeBaseSpec.ROOTFS_VERSION);
        } else if (version > RuntimeBaseSpec.ROOTFS_VERSION) {
            status = RuntimeBaseInspection.Status.FUTURE;
            launchIssues.add("RootFS version " + version + " is newer than the pinned baseline "
                    + RuntimeBaseSpec.ROOTFS_VERSION + " and has not been validated");
        } else if (!libc || !loader) {
            status = RuntimeBaseInspection.Status.INCOMPLETE;
        } else {
            status = RuntimeBaseInspection.Status.CURRENT;
        }

        if (valid && !libc) launchIssues.add("glibc runtime file libc.so.6 was not found");
        if (valid && !loader) launchIssues.add("ARM64 glibc dynamic loader ld-linux-aarch64.so.1 was not found");
        if (!patchesAsset) launchIssues.add("required RootFS patch asset is unavailable: "
                + RuntimeBaseSpec.ROOTFS_PATCHES_ASSET);

        if (!installAsset) maintenanceIssues.add("RootFS install/repair asset is unavailable: "
                + RuntimeBaseSpec.ROOTFS_ASSET);

        return new RuntimeBaseInspection(status, version, libc, loader,
                installAsset, patchesAsset, launchIssues, maintenanceIssues);
    }
}
