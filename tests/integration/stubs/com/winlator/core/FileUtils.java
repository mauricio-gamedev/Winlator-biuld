package com.winlator.core;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

public abstract class FileUtils {
    public static boolean writeString(File file, String value) {
        File parent = file.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) return false;
        try (FileWriter writer = new FileWriter(file)) {
            writer.write(value);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    public static boolean delete(File file) {
        if (file == null || !file.exists()) return true;
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children == null) return false;
            for (File child : children) if (!delete(child)) return false;
        }
        return file.delete();
    }
}
