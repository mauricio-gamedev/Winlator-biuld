package com.winlator.build.engine.runtime;

public final class RuntimeBaseSpec {
    public static final int ROOTFS_VERSION = 22;
    public static final String ROOTFS_ASSET = "rootfs.tzst";
    public static final long ROOTFS_ASSET_SIZE_BYTES = 65252428L;
    public static final String ROOTFS_UPSTREAM_BLOB_SHA1 = "75ca92cdb7710a7c0376d8435bd22114aed337d9";

    public static final String ROOTFS_PATCHES_ASSET = "rootfs_patches.tzst";
    public static final long ROOTFS_PATCHES_ASSET_SIZE_BYTES = 4174031L;
    public static final String ROOTFS_PATCHES_UPSTREAM_BLOB_SHA1 = "5d2ce398eb79f425da09a31e5cd47550df9b8884";

    public static final String GLIBC_PATCH_SOURCE = "termux-pacman/glibc-packages";

    private RuntimeBaseSpec() {}
}
