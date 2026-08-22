package com.winlator.build.integration;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.preference.PreferenceManager;

import com.winlator.build.engine.runtime.Box64Inspection;
import com.winlator.build.engine.runtime.Box64Inspector;
import com.winlator.build.engine.runtime.Box64Spec;
import com.winlator.build.engine.runtime.RuntimeBaseInspection;
import com.winlator.build.engine.runtime.RuntimeBaseInspector;
import com.winlator.core.FileUtils;
import com.winlator.core.TarCompressorUtils;
import com.winlator.xenvironment.RootFS;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

public final class WinlatorBox64Installer {
    public enum Status {
        SUCCESS,
        NOT_NEEDED,
        ROOTFS_NOT_READY,
        UNSUPPORTED_VERSION,
        ASSET_MISSING,
        EXTRACTION_FAILED,
        STAGING_INVALID,
        ACTIVATION_FAILED,
        FINAL_VALIDATION_FAILED,
        ROLLBACK_FAILED,
        RECOVERY_FAILED
    }

    public static final class Result {
        private final Status status;
        private final String message;
        private final Box64Inspection inspection;
        private final boolean rolledBack;

        private Result(Status status, String message, Box64Inspection inspection, boolean rolledBack) {
            this.status = status;
            this.message = message == null ? "" : message;
            this.inspection = inspection;
            this.rolledBack = rolledBack;
        }

        public Status getStatus() { return status; }
        public String getMessage() { return message; }
        public Box64Inspection getInspection() { return inspection; }
        public boolean wasRolledBack() { return rolledBack; }
        public boolean isSuccess() { return status == Status.SUCCESS || status == Status.NOT_NEEDED; }
    }

    private static final String PREF_SELECTED = "box64_version";
    private static final String PREF_CURRENT = "current_box64_version";
    private static final String JOURNAL = ".winlator-build-box64-transaction";
    private static final String PHASE_ACTIVATING = "ACTIVATING";
    private static final String PHASE_COMMITTING = "COMMITTING";
    private static final String BINARY = Box64Spec.BINARY_RELATIVE_PATH;
    private static final String RC = Box64Spec.RC_RELATIVE_PATH;

    private WinlatorBox64Installer() {}

    public static synchronized Result prepareOrRepair(Context context) {
        if (context == null) throw new IllegalArgumentException("context is required");
        Context appContext = context.getApplicationContext();
        if (appContext == null) appContext = context;

        RootFS rootFS = RootFS.find(appContext);
        File root = rootFS.getRootDir();
        File parent = root == null ? null : root.getAbsoluteFile().getParentFile();
        if (root == null || parent == null || !root.isDirectory()) {
            return result(Status.ROOTFS_NOT_READY, "RootFS directory is unavailable", null, false);
        }

        String recoveryError = recover(appContext, root, parent);
        if (!recoveryError.isEmpty()) {
            return result(Status.RECOVERY_FAILED, recoveryError, inspectSafely(appContext), false);
        }

        RuntimeBaseInspection base = new RuntimeBaseInspector().inspect(new WinlatorRuntimeBaseProbe(appContext));
        if (!base.isLaunchReady()) {
            return result(Status.ROOTFS_NOT_READY, "RootFS baseline is not launch-ready", inspectSafely(appContext), false);
        }

        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(appContext);
        String selected = normalize(preferences.getString(PREF_SELECTED, Box64Spec.VERSION));
        if (!Box64Spec.VERSION.equals(selected)) {
            return result(Status.UNSUPPORTED_VERSION,
                    "Box64 baseline preparation only supports pinned version " + Box64Spec.VERSION,
                    inspectSafely(appContext), false);
        }

        Box64Inspection before = inspectSafely(appContext);
        if (before != null && before.isLaunchReady() && before.isRcFilePresent()) {
            return result(Status.NOT_NEEDED, "Box64 baseline is already ready", before, false);
        }
        if (before == null || !before.isSelectedPackageAvailable() || !before.isDefaultRcAssetAvailable()) {
            return result(Status.ASSET_MISSING, "Box64 package or default RC asset is unavailable", before, false);
        }

        String token = UUID.randomUUID().toString().replace("-", "");
        File staging = new File(parent, root.getName() + ".box64-staging-" + token);
        File backup = new File(parent, root.getName() + ".box64-backup-" + token);
        File journal = new File(parent, JOURNAL);
        if (!staging.mkdirs()) {
            return result(Status.STAGING_INVALID, "Unable to create Box64 staging directory", before, false);
        }

        try {
            boolean extracted = TarCompressorUtils.extract(
                    TarCompressorUtils.Type.ZSTD, appContext, Box64Spec.PACKAGE_ASSET, staging);
            if (!extracted) {
                return result(Status.EXTRACTION_FAILED, "Box64 package extraction failed", before, false);
            }

            File stagedBinary = new File(staging, BINARY);
            if (!stagedBinary.isFile() || !stagedBinary.canExecute()) {
                return result(Status.STAGING_INVALID,
                        "Staged Box64 binary is missing or not executable: /" + BINARY,
                        before, false);
            }

            List<String> stagedFiles = new ArrayList<>();
            String treeError = collectRegularFiles(staging, staging, stagedFiles);
            if (!treeError.isEmpty()) {
                return result(Status.STAGING_INVALID, treeError, before, false);
            }
            if (stagedFiles.size() != 1 || !BINARY.equals(stagedFiles.get(0))) {
                return result(Status.STAGING_INVALID,
                        "Pinned Box64 package contains an unexpected file layout: " + stagedFiles,
                        before, false);
            }

            File stagedRc = new File(staging, RC);
            if (!copyAsset(appContext, Box64Spec.DEFAULT_RC_ASSET, stagedRc) || !stagedRc.isFile() || stagedRc.length() == 0) {
                return result(Status.STAGING_INVALID, "Unable to stage default Box64 RC file", before, false);
            }

            File activeBinary = new File(root, BINARY);
            File activeRc = new File(root, RC);
            boolean binaryExisted = activeBinary.isFile();
            boolean rcExisted = activeRc.isFile();

            if (!backup.mkdirs()) {
                return result(Status.ACTIVATION_FAILED, "Unable to create Box64 backup directory", before, false);
            }
            if (binaryExisted && !copyFile(activeBinary, new File(backup, BINARY))) {
                return result(Status.ACTIVATION_FAILED, "Unable to back up existing Box64 binary", before, false);
            }
            if (rcExisted && !copyFile(activeRc, new File(backup, RC))) {
                return result(Status.ACTIVATION_FAILED, "Unable to back up existing Box64 RC file", before, false);
            }

            if (!writeJournal(journal, staging, backup, PHASE_ACTIVATING, binaryExisted, rcExisted)) {
                return result(Status.ACTIVATION_FAILED, "Unable to create Box64 transaction journal", before, false);
            }

            if (!copyFile(stagedBinary, activeBinary) || !activeBinary.setExecutable(true, false)
                    || !copyFile(stagedRc, activeRc)) {
                boolean rolledBack = rollback(root, backup, binaryExisted, rcExisted);
                preferences.edit().remove(PREF_CURRENT).apply();
                if (rolledBack) cleanup(staging, backup, journal);
                return result(rolledBack ? Status.ACTIVATION_FAILED : Status.ROLLBACK_FAILED,
                        rolledBack ? "Box64 activation failed and was rolled back"
                                : "Box64 activation failed and rollback also failed",
                        inspectSafely(appContext), rolledBack);
            }

            if (!activeBinary.isFile() || !activeBinary.canExecute() || !activeRc.isFile() || activeRc.length() == 0) {
                boolean rolledBack = rollback(root, backup, binaryExisted, rcExisted);
                preferences.edit().remove(PREF_CURRENT).apply();
                if (rolledBack) cleanup(staging, backup, journal);
                return result(rolledBack ? Status.FINAL_VALIDATION_FAILED : Status.ROLLBACK_FAILED,
                        rolledBack ? "Box64 files failed validation and were rolled back"
                                : "Box64 validation failed and rollback also failed",
                        inspectSafely(appContext), rolledBack);
            }

            preferences.edit().putString(PREF_CURRENT, Box64Spec.VERSION).apply();
            if (!writeJournal(journal, staging, backup, PHASE_COMMITTING, binaryExisted, rcExisted)) {
                boolean rolledBack = rollback(root, backup, binaryExisted, rcExisted);
                preferences.edit().remove(PREF_CURRENT).apply();
                if (rolledBack) cleanup(staging, backup, journal);
                return result(rolledBack ? Status.ACTIVATION_FAILED : Status.ROLLBACK_FAILED,
                        "Unable to persist Box64 commit phase", inspectSafely(appContext), rolledBack);
            }

            Box64Inspection after = inspectSafely(appContext);
            if (after == null || !after.isLaunchReady() || !after.isRcFilePresent()) {
                boolean rolledBack = rollback(root, backup, binaryExisted, rcExisted);
                preferences.edit().remove(PREF_CURRENT).apply();
                if (rolledBack) cleanup(staging, backup, journal);
                return result(rolledBack ? Status.FINAL_VALIDATION_FAILED : Status.ROLLBACK_FAILED,
                        "Box64 final readiness validation failed", after, rolledBack);
            }

            cleanup(staging, backup, journal);
            return result(Status.SUCCESS, "Box64 " + Box64Spec.VERSION + " prepared successfully", after, false);
        } finally {
            if (staging.exists() && !journal.exists()) FileUtils.delete(staging);
            if (backup.exists() && !journal.exists()) FileUtils.delete(backup);
        }
    }

    private static String recover(Context context, File root, File parent) {
        File journal = new File(parent, JOURNAL);
        if (!journal.isFile()) return "";

        Properties properties = new Properties();
        try (FileInputStream input = new FileInputStream(journal)) {
            properties.load(input);
        } catch (IOException e) {
            return "Unable to read interrupted Box64 transaction journal";
        }

        String phase = properties.getProperty("phase", "");
        String stagingName = properties.getProperty("staging", "");
        String backupName = properties.getProperty("backup", "");
        boolean binaryExisted = Boolean.parseBoolean(properties.getProperty("binaryExisted", "false"));
        boolean rcExisted = Boolean.parseBoolean(properties.getProperty("rcExisted", "false"));
        if (!isSafeSibling(stagingName, root.getName() + ".box64-staging-")
                || !isSafeSibling(backupName, root.getName() + ".box64-backup-")
                || (!PHASE_ACTIVATING.equals(phase) && !PHASE_COMMITTING.equals(phase))) {
            return "Interrupted Box64 transaction journal contains invalid state";
        }

        File staging = new File(parent, stagingName);
        File backup = new File(parent, backupName);
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);

        if (PHASE_COMMITTING.equals(phase)) {
            File activeBinary = new File(root, BINARY);
            File activeRc = new File(root, RC);
            if (!activeBinary.isFile() || !activeBinary.canExecute() || !activeRc.isFile() || activeRc.length() == 0) {
                return "Interrupted Box64 commit found invalid active files";
            }
            preferences.edit().putString(PREF_CURRENT, Box64Spec.VERSION).apply();
            cleanup(staging, backup, journal);
            return journal.exists() ? "Unable to finish Box64 commit cleanup" : "";
        }

        boolean restored = rollback(root, backup, binaryExisted, rcExisted);
        preferences.edit().remove(PREF_CURRENT).apply();
        if (!restored) return "Unable to roll back interrupted Box64 activation";
        cleanup(staging, backup, journal);
        return journal.exists() ? "Unable to finish Box64 rollback cleanup" : "";
    }

    private static boolean rollback(File root, File backup, boolean binaryExisted, boolean rcExisted) {
        boolean binaryOk = restoreTarget(new File(root, BINARY), new File(backup, BINARY), binaryExisted);
        boolean rcOk = restoreTarget(new File(root, RC), new File(backup, RC), rcExisted);
        return binaryOk && rcOk;
    }

    private static boolean restoreTarget(File active, File backup, boolean existed) {
        if (active.exists() && !FileUtils.delete(active)) return false;
        if (!existed) return true;
        return backup.isFile() && copyFile(backup, active);
    }

    private static boolean writeJournal(File journal, File staging, File backup, String phase,
            boolean binaryExisted, boolean rcExisted) {
        String data = "phase=" + phase + "\n"
                + "staging=" + staging.getName() + "\n"
                + "backup=" + backup.getName() + "\n"
                + "binaryExisted=" + binaryExisted + "\n"
                + "rcExisted=" + rcExisted + "\n";
        return FileUtils.writeString(journal, data);
    }

    private static boolean isSafeSibling(String name, String prefix) {
        return name != null && name.startsWith(prefix) && name.indexOf('/') < 0
                && name.indexOf('\\') < 0 && name.indexOf("..") < 0;
    }

    private static void cleanup(File staging, File backup, File journal) {
        if (staging.exists()) FileUtils.delete(staging);
        if (backup.exists()) FileUtils.delete(backup);
        if (journal.exists()) journal.delete();
    }

    private static String collectRegularFiles(File base, File current, List<String> files) {
        if (java.nio.file.Files.isSymbolicLink(current.toPath())) {
            return "Box64 staging contains an unsupported symbolic link";
        }
        if (current.isFile()) {
            files.add(relative(base, current));
            return "";
        }
        File[] children = current.listFiles();
        if (children == null) return current.isDirectory() ? "" : "Unable to inspect Box64 staging tree";
        for (File child : children) {
            String error = collectRegularFiles(base, child, files);
            if (!error.isEmpty()) return error;
        }
        return "";
    }

    private static String relative(File base, File file) {
        String value = base.toURI().relativize(file.toURI()).getPath();
        while (value.endsWith("/")) value = value.substring(0, value.length() - 1);
        return value;
    }

    private static boolean copyAsset(Context context, String asset, File target) {
        File parent = target.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) return false;
        try (InputStream input = context.getAssets().open(asset);
             FileOutputStream output = new FileOutputStream(target, false)) {
            byte[] buffer = new byte[32768];
            int read;
            while ((read = input.read(buffer)) != -1) output.write(buffer, 0, read);
            output.flush();
            return target.isFile();
        } catch (IOException e) {
            return false;
        }
    }

    private static boolean copyFile(File source, File target) {
        File parent = target.getParentFile();
        if (!source.isFile() || (parent != null && !parent.isDirectory() && !parent.mkdirs())) return false;
        boolean executable = source.canExecute();
        try (FileInputStream input = new FileInputStream(source);
             FileOutputStream output = new FileOutputStream(target, false)) {
            byte[] buffer = new byte[32768];
            int read;
            while ((read = input.read(buffer)) != -1) output.write(buffer, 0, read);
            output.flush();
        } catch (IOException e) {
            return false;
        }
        if (executable) target.setExecutable(true, false);
        return target.isFile();
    }

    private static Box64Inspection inspectSafely(Context context) {
        try {
            return new Box64Inspector().inspect(new WinlatorBox64Probe(context));
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private static Result result(Status status, String message, Box64Inspection inspection, boolean rolledBack) {
        return new Result(status, message, inspection, rolledBack);
    }
}
