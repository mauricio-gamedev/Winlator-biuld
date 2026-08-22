package com.winlator.build.integration;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.preference.PreferenceManager;

import com.winlator.build.engine.runtime.Box64Spec;
import com.winlator.xenvironment.RootFS;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;

public final class WinlatorBox64RecoverySelfTest {
    private WinlatorBox64RecoverySelfTest() {}

    public static void main(String[] args) {
        testInterruptedActivationRestoresPreviousFiles();
        testInterruptedCommitKeepsValidatedFiles();
        System.out.println("WinlatorBox64RecoverySelfTest: all tests passed");
    }

    private static void testInterruptedActivationRestoresPreviousFiles() {
        Fixture f = fixture();
        try {
            writeExecutable(new File(f.root, Box64Spec.BINARY_RELATIVE_PATH), "new-box64");
            write(new File(f.root, Box64Spec.RC_RELATIVE_PATH), "new-rc");
            writeExecutable(new File(f.backup, Box64Spec.BINARY_RELATIVE_PATH), "old-box64");
            write(new File(f.backup, Box64Spec.RC_RELATIVE_PATH), "old-rc");
            f.staging.mkdirs();
            current(f.context).edit().putString("current_box64_version", Box64Spec.VERSION).apply();
            writeJournal(f, "ACTIVATING", true, true);

            String error = invokeRecovery(f.context, f.root, f.parent);
            assertEquals("", error, "activation recovery error");
            assertEquals("old-box64", read(new File(f.root, Box64Spec.BINARY_RELATIVE_PATH)), "old binary restored");
            assertEquals("old-rc", read(new File(f.root, Box64Spec.RC_RELATIVE_PATH)), "old RC restored");
            assertEquals("", current(f.context).getString("current_box64_version", ""), "current version cleared");
            assertFalse(f.journal.exists(), "journal removed");
        } finally {
            f.close();
        }
    }

    private static void testInterruptedCommitKeepsValidatedFiles() {
        Fixture f = fixture();
        try {
            writeExecutable(new File(f.root, Box64Spec.BINARY_RELATIVE_PATH), "new-box64");
            write(new File(f.root, Box64Spec.RC_RELATIVE_PATH), "new-rc");
            writeExecutable(new File(f.backup, Box64Spec.BINARY_RELATIVE_PATH), "old-box64");
            write(new File(f.backup, Box64Spec.RC_RELATIVE_PATH), "old-rc");
            f.staging.mkdirs();
            current(f.context).edit().remove("current_box64_version").apply();
            writeJournal(f, "COMMITTING", true, true);

            String error = invokeRecovery(f.context, f.root, f.parent);
            assertEquals("", error, "commit recovery error");
            assertEquals("new-box64", read(new File(f.root, Box64Spec.BINARY_RELATIVE_PATH)), "new binary kept");
            assertEquals(Box64Spec.VERSION,
                    current(f.context).getString("current_box64_version", ""), "current version finalized");
            assertFalse(f.backup.exists(), "backup cleaned");
            assertFalse(f.staging.exists(), "staging cleaned");
            assertFalse(f.journal.exists(), "journal removed");
        } finally {
            f.close();
        }
    }

    private static String invokeRecovery(Context context, File root, File parent) {
        try {
            Method method = WinlatorBox64Installer.class.getDeclaredMethod(
                    "recover", Context.class, File.class, File.class);
            method.setAccessible(true);
            return (String)method.invoke(null, context, root, parent);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static void writeJournal(Fixture f, String phase, boolean binaryExisted, boolean rcExisted) {
        write(f.journal, "phase=" + phase + "\n"
                + "staging=" + f.staging.getName() + "\n"
                + "backup=" + f.backup.getName() + "\n"
                + "binaryExisted=" + binaryExisted + "\n"
                + "rcExisted=" + rcExisted + "\n");
    }

    private static Fixture fixture() {
        File parent = tempDir("box64-recovery");
        File root = new File(parent, "rootfs");
        root.mkdirs();
        RootFS.configure(root, true, 22);
        Context context = new Context();
        PreferenceManager.clear(context);
        return new Fixture(context, root, parent,
                new File(parent, "rootfs.box64-staging-test"),
                new File(parent, "rootfs.box64-backup-test"),
                new File(parent, ".winlator-build-box64-transaction"));
    }

    private static SharedPreferences current(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context);
    }

    private static File tempDir(String prefix) {
        try {
            return Files.createTempDirectory(prefix).toFile();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void writeExecutable(File file, String value) {
        write(file, value);
        if (!file.setExecutable(true, false) && !file.canExecute()) {
            throw new IllegalStateException("unable to mark test file executable");
        }
    }

    private static void write(File file, String value) {
        File parent = file.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
            throw new IllegalStateException("unable to create test directory");
        }
        try (FileWriter writer = new FileWriter(file, false)) {
            writer.write(value);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String read(File file) {
        try {
            return new String(Files.readAllBytes(file.toPath()), "UTF-8");
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void deleteTree(File file) {
        if (file == null || !file.exists()) return;
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) for (File child : children) deleteTree(child);
        }
        file.delete();
    }

    private static void assertFalse(boolean condition, String message) {
        if (condition) throw new AssertionError(message);
    }

    private static void assertEquals(Object expected, Object actual, String message) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError(message + ": expected=" + expected + " actual=" + actual);
        }
    }

    private static final class Fixture {
        final Context context;
        final File root;
        final File parent;
        final File staging;
        final File backup;
        final File journal;

        Fixture(Context context, File root, File parent, File staging, File backup, File journal) {
            this.context = context;
            this.root = root;
            this.parent = parent;
            this.staging = staging;
            this.backup = backup;
            this.journal = journal;
        }

        void close() {
            PreferenceManager.clear(context);
            deleteTree(parent);
        }
    }
}
