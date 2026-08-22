package com.winlator.build.engine.runtime;

public interface RuntimeBaseProbe {
    boolean isRootFsValid();
    int getRootFsVersion();
    boolean hasLibcSo6();
    boolean hasArm64DynamicLoader();
    boolean isRootFsInstallAssetAvailable();
    boolean isRootFsPatchesAssetAvailable();
}
