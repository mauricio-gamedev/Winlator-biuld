package com.winlator.xenvironment;

import android.content.Context;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

public class RootFS {
    private static final String VERSION_FILE = ".winlator/.rfs_version";
    private static File rootDir;
    private static boolean valid;

    public static void configure(File root, boolean isValid, int value) {
        rootDir = root;
        valid = isValid;
        if (root == null) return;

        File marker = new File(root, VERSION_FILE);
        if (isValid) {
            File parent = marker.getParentFile();
            if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
                throw new IllegalStateException("unable to create RootFS marker directory");
            }
            try (FileWriter writer = new FileWriter(marker, false)) {
                writer.write(String.valueOf(value));
            } catch (IOException e) {
                throw new IllegalStateException("unable to write RootFS version marker", e);
            }
        } else if (marker.exists() && !marker.delete()) {
            throw new IllegalStateException("unable to remove RootFS version marker");
        }
    }

    public static RootFS find(Context context) { return new RootFS(); }

    public boolean isValid() {
        return valid && rootDir != null && rootDir.isDirectory()
                && new File(rootDir, VERSION_FILE).isFile();
    }

    public int getVersion() {
        if (rootDir == null) return 0;
        File marker = new File(rootDir, VERSION_FILE);
        if (!marker.isFile()) return 0;
        try (BufferedReader reader = new BufferedReader(new FileReader(marker))) {
            String line = reader.readLine();
            return line == null ? 0 : Integer.parseInt(line.trim());
        } catch (IOException | NumberFormatException e) {
            return 0;
        }
    }

    public File getRootDir() { return rootDir; }
}
