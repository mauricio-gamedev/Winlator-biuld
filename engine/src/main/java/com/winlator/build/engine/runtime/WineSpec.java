package com.winlator.build.engine.runtime;

public final class WineSpec {
    public static final String VERSION = "10.10";
    public static final String IDENTIFIER = "wine-10.10-custom";
    public static final String ROOT_RELATIVE_PATH = "opt/wine";
    public static final String WINE_RELATIVE_PATH = ROOT_RELATIVE_PATH + "/bin/wine";
    public static final String WINESERVER_RELATIVE_PATH = ROOT_RELATIVE_PATH + "/bin/wineserver";

    private WineSpec() {}
}
