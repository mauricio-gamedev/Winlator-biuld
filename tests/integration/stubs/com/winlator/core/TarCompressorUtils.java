package com.winlator.core;

import android.content.Context;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

public abstract class TarCompressorUtils {
    public enum Type { XZ, ZSTD }

    private static long contentLength = 1024L;
    private static boolean extractResult = false;
    private static boolean box64Executable = true;
    private static boolean box64UnexpectedFile = false;

    public static void configure(long length, boolean result) {
        contentLength = length;
        extractResult = result;
        box64Executable = true;
        box64UnexpectedFile = false;
    }

    public static void configureBox64Payload(boolean result, boolean executable, boolean unexpectedFile) {
        extractResult = result;
        box64Executable = executable;
        box64UnexpectedFile = unexpectedFile;
    }

    public static long getContentLength(Type type, Context context, String assetFile, File destination) {
        return contentLength;
    }

    public static boolean extract(Type type, Context context, String assetFile, File destination) {
        if (!extractResult) return false;
        if (assetFile != null && assetFile.startsWith("box64/box64-")) {
            File binary = new File(destination, "usr/local/bin/box64");
            if (!write(binary, "box64")) return false;
            binary.setExecutable(box64Executable, false);
            if (box64UnexpectedFile) {
                if (!write(new File(destination, "unexpected.bin"), "unexpected")) return false;
            }
        }
        return true;
    }

    private static boolean write(File file, String value) {
        File parent = file.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) return false;
        try (FileWriter writer = new FileWriter(file, false)) {
            writer.write(value);
            return true;
        } catch (IOException e) {
            return false;
        }
    }
}
