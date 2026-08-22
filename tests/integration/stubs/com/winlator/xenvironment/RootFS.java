package com.winlator.xenvironment;

import android.content.Context;

import java.io.File;

public class RootFS {
    private static File rootDir;
    private static boolean valid;
    private static int version;

    public static void configure(File root, boolean isValid, int value) {
        rootDir = root;
        valid = isValid;
        version = value;
    }

    public static RootFS find(Context context) { return new RootFS(); }
    public boolean isValid() { return valid; }
    public int getVersion() { return version; }
    public File getRootDir() { return rootDir; }
}
