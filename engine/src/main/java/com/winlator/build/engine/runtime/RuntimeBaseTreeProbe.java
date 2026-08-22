package com.winlator.build.engine.runtime;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

public final class RuntimeBaseTreeProbe implements RuntimeBaseProbe {
    public static final String VERSION_FILE = ".winlator/.rfs_version";
    private static final int SEARCH_DEPTH = 3;

    private final File rootDir;
    private final boolean installAssetAvailable;
    private final boolean patchesAssetAvailable;
    private Boolean libcPresent;
    private Boolean loaderPresent;

    public RuntimeBaseTreeProbe(File rootDir, boolean installAssetAvailable,
            boolean patchesAssetAvailable) {
        if (rootDir == null) throw new IllegalArgumentException("rootDir is required");
        this.rootDir = rootDir;
        this.installAssetAvailable = installAssetAvailable;
        this.patchesAssetAvailable = patchesAssetAvailable;
    }

    public File getRootDir() { return rootDir; }

    @Override
    public boolean isRootFsValid() {
        return rootDir.isDirectory() && getVersionFile().isFile();
    }

    @Override
    public int getRootFsVersion() {
        File versionFile = getVersionFile();
        if (!versionFile.isFile()) return 0;
        try (BufferedReader reader = new BufferedReader(new FileReader(versionFile))) {
            String line = reader.readLine();
            if (line == null) return 0;
            return Math.max(0, Integer.parseInt(line.trim()));
        } catch (IOException | NumberFormatException e) {
            return 0;
        }
    }

    @Override
    public boolean hasLibcSo6() {
        if (libcPresent == null) libcPresent = findSystemLibrary(rootDir, "libc.so.6");
        return libcPresent;
    }

    @Override
    public boolean hasArm64DynamicLoader() {
        if (loaderPresent == null) {
            loaderPresent = findSystemLibrary(rootDir, "ld-linux-aarch64.so.1");
        }
        return loaderPresent;
    }

    @Override
    public boolean isRootFsInstallAssetAvailable() { return installAssetAvailable; }

    @Override
    public boolean isRootFsPatchesAssetAvailable() { return patchesAssetAvailable; }

    public File getVersionFile() { return new File(rootDir, VERSION_FILE); }

    public static boolean hasRequiredRuntimeFiles(File rootDir) {
        return findSystemLibrary(rootDir, "libc.so.6")
                && findSystemLibrary(rootDir, "ld-linux-aarch64.so.1");
    }

    public static boolean findSystemLibrary(File rootDir, String filename) {
        if (rootDir == null || filename == null || filename.isEmpty() || !rootDir.isDirectory()) {
            return false;
        }
        String[] roots = {"lib", "lib64", "usr/lib", "usr/lib64", "usr/local/lib"};
        for (String relative : roots) {
            if (findByName(new File(rootDir, relative), filename, SEARCH_DEPTH)) return true;
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
