package com.winlator.build.integration;

import android.content.Context;

import com.winlator.build.engine.runtime.RuntimeBaseProbe;
import com.winlator.build.engine.runtime.RuntimeBaseSpec;
import com.winlator.build.engine.runtime.RuntimeBaseTreeProbe;
import com.winlator.xenvironment.RootFS;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

public final class WinlatorRuntimeBaseProbe implements RuntimeBaseProbe {
    private final Context context;
    private final RootFS rootFS;
    private final RuntimeBaseTreeProbe treeProbe;

    public WinlatorRuntimeBaseProbe(Context context) {
        if (context == null) throw new IllegalArgumentException("context is required");
        Context appContext = context.getApplicationContext();
        this.context = appContext != null ? appContext : context;
        this.rootFS = RootFS.find(this.context);
        this.treeProbe = new RuntimeBaseTreeProbe(rootFS.getRootDir(),
                hasAsset(RuntimeBaseSpec.ROOTFS_ASSET),
                hasAsset(RuntimeBaseSpec.ROOTFS_PATCHES_ASSET));
    }

    @Override
    public boolean isRootFsValid() {
        try {
            return rootFS.isValid() && treeProbe.isRootFsValid();
        } catch (RuntimeException e) {
            return false;
        }
    }

    @Override
    public int getRootFsVersion() {
        try {
            return isRootFsValid() ? rootFS.getVersion() : 0;
        } catch (RuntimeException e) {
            return 0;
        }
    }

    @Override
    public boolean hasLibcSo6() { return treeProbe.hasLibcSo6(); }

    @Override
    public boolean hasArm64DynamicLoader() { return treeProbe.hasArm64DynamicLoader(); }

    @Override
    public boolean isRootFsInstallAssetAvailable() {
        return treeProbe.isRootFsInstallAssetAvailable();
    }

    @Override
    public boolean isRootFsPatchesAssetAvailable() {
        return treeProbe.isRootFsPatchesAssetAvailable();
    }

    public File getRootDir() { return rootFS.getRootDir(); }

    private boolean hasAsset(String name) {
        try (InputStream ignored = context.getAssets().open(name)) {
            return true;
        } catch (IOException | RuntimeException e) {
            return false;
        }
    }
}
