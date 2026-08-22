package com.winlator.build.engine.runtime;

public final class Box64Spec {
    public static final String VERSION = "0.4.4";

    public static final String PACKAGE_ASSET = "box64/box64-0.4.4.tzst";
    public static final long PACKAGE_ASSET_SIZE_BYTES = 4540371L;
    public static final String PACKAGE_UPSTREAM_BLOB_SHA1 = "c505bc89a765f1e259491730f3064772bfb5e00f";

    public static final String DEFAULT_RC_ASSET = "box64/default.box64rc";
    public static final long DEFAULT_RC_ASSET_SIZE_BYTES = 2296L;
    public static final String DEFAULT_RC_UPSTREAM_BLOB_SHA1 = "4314cc003d5e1b5e75b8eab255017c16902d83df";

    public static final String BINARY_RELATIVE_PATH = "usr/local/bin/box64";
    public static final String RC_RELATIVE_PATH = "etc/config.box64rc";

    private Box64Spec() {}
}
