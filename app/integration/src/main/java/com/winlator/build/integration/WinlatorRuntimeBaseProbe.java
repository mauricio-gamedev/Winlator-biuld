package com.winlator.build.integration;

import android.content.Context;

import com.winlator.build.engine.runtime.RuntimeBaseProbe;
import com.winlator.build.engine.runtime.RuntimeBaseSpec;
import com.winlator.xenvironment.RootFS;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

public final class WinlatorRuntimeBaseProbe implements RuntimeBaseProbe {
    private static final int SEARCH_DEPTH = 3;

    private final Context context;
    private final RootFS rootFS;
    private Boolean libcPresent;
    private Boolean loaderPresent;

    public WinlatorRuntimeBaseProbe(Context context) {
        if (context == null) throw new IllegalArgumentException("context is required");
        Context appContext = context.getApplicationContext();
        this.context = appContext != null ? appContext : context;
        this.rootFS = RootFS.find(this.context);
    }

    @Override
    public boolean isRootFsValid() {
        try {
            return rootFS.isValid();
        } catch (RuntimeException e) {
            return false;
        }
    }

    @Override
    public int getRootFsVersion() {
        try {
            return rootFS.isValid() ? rootFS.getVersion() : 0;
        } catch (RuntimeException e) {
            return 0;
        }
    }

    @Override
    public boolean hasLibcSo6() {
        if (libcPresent == null) libcPresent = findSystemLibrary("libc.so.6");
        return libcPresent;
    }

    @Override
    public boolean hasArm64DynamicLoader() {
        if (loaderPresent == null) loaderPresent = findSystemLibrary("ld-linux-aarch64.so.1");
        return loaderPresent;
    }

    @Override
    public boolean isRootFsInstallAssetAvailable() {
        return hasAsset(RuntimeBaseSpec.ROOTFS_ASSET);
    }

    @Override
    public boolean isRootFsPatchesAssetAvailable() {
        return hasAsset(RuntimeBaseSpec.ROOTFS_PATCHES_ASSET);
    }

    private boolean hasAsset(String name) {
        try (InputStream ignored = context.getAssets().open(name)) {
            return true;
        } catch (IOException | RuntimeException e) {
            return false;
        }
    }

    private boolean findSystemLibrary(String filename) {
        File root = rootFS.getRootDir();
        if (root == null || !root.isDirectory()) return false;

        String[] roots = {"lib", "lib64", "usr/lib", "usr/lib64", "usr/local/lib"};
        for (String relative : roots) {
            if (findByName(new File(root, relative), filename, SEARCH_DEPTH)) return true;
        }
        return false;
    }

    private static boolean findByName(File directory, String filename, int depth) {
        if (directory == null || depth < 0 || !directory.isDirectory()) return false;
        File direct = new File(directory, filename);
        if (direct.isFile()) return true;
        if (depth == 0) return false;

        File[] children = directory.listFiles();
        if (children == null) return false;
        for (File child : children) {
            if (child.isDirectory() && findByName(child, filename, depth - 1)) return true;
        }
        return false;
    }
}
