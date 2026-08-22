package com.winlator.core;

public final class WineInfo {
    private WineInfo() {}

    public static boolean isMainWineVersion(String value) {
        return value == null || value.equals("wine-10.10-custom");
    }
}
