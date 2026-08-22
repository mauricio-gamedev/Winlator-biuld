package com.winlator.core;

import android.content.Context;

import java.io.File;

public abstract class TarCompressorUtils {
    public enum Type { XZ, ZSTD }

    private static long contentLength = 1024L;
    private static boolean extractResult = false;

    public static void configure(long length, boolean result) {
        contentLength = length;
        extractResult = result;
    }

    public static long getContentLength(Type type, Context context, String assetFile, File destination) {
        return contentLength;
    }

    public static boolean extract(Type type, Context context, String assetFile, File destination) {
        return extractResult;
    }
}
