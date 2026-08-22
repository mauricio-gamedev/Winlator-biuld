package com.winlator.build.engine.runtime;

public interface Box64Probe {
    String getSelectedVersion();
    String getCurrentExtractedVersion();
    boolean isSelectedPackageAvailable();
    boolean isBinaryPresent();
    boolean isBinaryRunnable();
    boolean isDefaultRcAssetAvailable();
    boolean isRcFilePresent();
}
