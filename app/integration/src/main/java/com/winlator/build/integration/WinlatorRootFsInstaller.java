package com.winlator.build.integration;

import androidx.appcompat.app.AppCompatActivity;

import com.winlator.SettingsFragment;
import com.winlator.build.engine.runtime.RootFsActivationTransaction;
import com.winlator.build.engine.runtime.RuntimeBaseInspection;
import com.winlator.build.engine.runtime.RuntimeBaseInspector;
import com.winlator.build.engine.runtime.RuntimeBaseSpec;
import com.winlator.build.engine.runtime.RuntimeBaseTreeProbe;
import com.winlator.container.Container;
import com.winlator.container.ContainerManager;
import com.winlator.core.FileUtils;
import com.winlator.core.TarCompressorUtils;
import com.winlator.core.WineInfo;
import com.winlator.xenvironment.RootFS;
import com.winlator.xenvironment.RootFSInstaller;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Properties;
import java.util.UUID;

public final class WinlatorRootFsInstaller {
    public enum Status {
        SUCCESS,
        SUCCESS_WITH_WARNING,
        NOT_NEEDED,
        BLOCKED,
        INSUFFICIENT_STORAGE,
        RECOVERY_FAILED,
        EXTRACTION_FAILED,
        STAGING_INVALID,
        ACTIVATION_FAILED,
        FINAL_VALIDATION_FAILED,
        ROLLBACK_FAILED
    }

    public static final class Result {
        private final Status status;
        private final RuntimeBaseInspection before;
        private final RuntimeBaseInspection after;
        private final String message;
        private final boolean rolledBack;

        private Result(Status status, RuntimeBaseInspection before,
                RuntimeBaseInspection after, String message, boolean rolledBack) {
            this.status = status;
            this.before = before;
            this.after = after;
            this.message = message == null ? "" : message;
            this.rolledBack = rolledBack;
        }

        public Status getStatus() { return status; }
        public RuntimeBaseInspection getBefore() { return before; }
        public RuntimeBaseInspection getAfter() { return after; }
        public String getMessage() { return message; }
        public boolean wasRolledBack() { return rolledBack; }
        public boolean isSuccess() {
            return status == Status.SUCCESS || status == Status.SUCCESS_WITH_WARNING
                    || status == Status.NOT_NEEDED;
        }
    }

    private static final String PRESERVE_HOME = "home";
    private static final String PRESERVE_INSTALLED_WINE = "opt/installed-wine";
    private static final String JOURNAL_FILE = ".winlator-build-rootfs-transaction";
    private static final String JOURNAL_PHASE_ACTIVATING = "ACTIVATING";
    private static final String JOURNAL_PHASE_COMMITTING = "COMMITTING";
    private static final long STORAGE_MARGIN_BYTES = 64L * 1024L * 1024L;

    private WinlatorRootFsInstaller() {}

    public static synchronized Result installOrRepair(AppCompatActivity activity) {
        return installOrRepair(activity, false);
    }

    public static synchronized Result installOrRepair(AppCompatActivity activity,
            boolean forceRepair) {
        if (activity == null) throw new IllegalArgumentException("activity is required");

        RuntimeBaseInspector inspector = new RuntimeBaseInspector();
        RootFS rootFS = RootFS.find(activity);
        File activeRoot = rootFS.getRootDir();
        File parent = activeRoot.getAbsoluteFile().getParentFile();
        if (parent == null || (!parent.isDirectory() && !parent.mkdirs())) {
            return result(Status.BLOCKED, null, null,
                    "Unable to prepare RootFS parent directory", false);
        }

        String recoveryError = recoverInterruptedTransaction(activity, activeRoot, parent);
        if (!recoveryError.isEmpty()) {
            RuntimeBaseInspection current = inspectSafely(inspector, activity);
            return result(Status.RECOVERY_FAILED, current, current, recoveryError, false);
        }

        WinlatorRuntimeBaseProbe currentProbe = new WinlatorRuntimeBaseProbe(activity);
        RuntimeBaseInspection before = inspector.inspect(currentProbe);

        if (before.getStatus() == RuntimeBaseInspection.Status.FUTURE) {
            return result(Status.BLOCKED, before, before,
                    "Refusing to downgrade a RootFS newer than pinned version "
                            + RuntimeBaseSpec.ROOTFS_VERSION, false);
        }

        if (!forceRepair && before.isLaunchReady()
                && before.getStatus() == RuntimeBaseInspection.Status.CURRENT) {
            return result(Status.NOT_NEEDED, before, before, "RootFS is already current", false);
        }

        if (!currentProbe.isRootFsInstallAssetAvailable()) {
            return result(Status.BLOCKED, before, before,
                    "RootFS install asset is unavailable: " + RuntimeBaseSpec.ROOTFS_ASSET, false);
        }
        if (!currentProbe.isRootFsPatchesAssetAvailable()) {
            return result(Status.BLOCKED, before, before,
                    "RootFS patch asset is unavailable: " + RuntimeBaseSpec.ROOTFS_PATCHES_ASSET, false);
        }

        long expandedBytes = TarCompressorUtils.getContentLength(
                TarCompressorUtils.Type.ZSTD, activity,
                RuntimeBaseSpec.ROOTFS_ASSET, activeRoot);
        long usableBytes = parent.getUsableSpace();
        if (expandedBytes > 0 && usableBytes > 0
                && usableBytes < expandedBytes + STORAGE_MARGIN_BYTES) {
            return result(Status.INSUFFICIENT_STORAGE, before, before,
                    "Not enough free storage for safe RootFS staging. Required approximately "
                            + (expandedBytes + STORAGE_MARGIN_BYTES) + " bytes, available "
                            + usableBytes + " bytes", false);
        }

        File stagingRoot = new File(parent,
                activeRoot.getName() + ".staging-" + UUID.randomUUID().toString().replace("-", ""));
        File journal = new File(parent, JOURNAL_FILE);
        if (!stagingRoot.mkdirs()) {
            return result(Status.BLOCKED, before, before,
                    "Unable to create RootFS staging directory", false);
        }

        RootFsActivationTransaction transaction = null;
        boolean journalResolved = true;
        try {
            boolean extracted = TarCompressorUtils.extract(
                    TarCompressorUtils.Type.ZSTD,
                    activity,
                    RuntimeBaseSpec.ROOTFS_ASSET,
                    stagingRoot);
            if (!extracted) {
                return result(Status.EXTRACTION_FAILED, before, before,
                        "RootFS archive extraction failed", false);
            }

            if (!RuntimeBaseTreeProbe.hasRequiredRuntimeFiles(stagingRoot)) {
                return result(Status.STAGING_INVALID, before, before,
                        "Staged RootFS is missing libc.so.6 or ld-linux-aarch64.so.1", false);
            }

            File versionFile = new File(stagingRoot, RuntimeBaseTreeProbe.VERSION_FILE);
            File versionParent = versionFile.getParentFile();
            if (versionParent == null
                    || (!versionParent.isDirectory() && !versionParent.mkdirs())
                    || !FileUtils.writeString(versionFile, String.valueOf(RuntimeBaseSpec.ROOTFS_VERSION))) {
                return result(Status.STAGING_INVALID, before, before,
                        "Unable to write staged RootFS version marker", false);
            }

            RuntimeBaseInspection staged = inspector.inspect(new RuntimeBaseTreeProbe(
                    stagingRoot, true, true));
            if (!staged.isLaunchReady()
                    || staged.getStatus() != RuntimeBaseInspection.Status.CURRENT) {
                return result(Status.STAGING_INVALID, before, staged,
                        joinIssues(staged), false);
            }

            transaction = new RootFsActivationTransaction(
                    activeRoot,
                    stagingRoot,
                    Arrays.asList(PRESERVE_HOME, PRESERVE_INSTALLED_WINE));
            if (!writeJournal(journal, transaction.getBackupRoot(), stagingRoot,
                    JOURNAL_PHASE_ACTIVATING)) {
                return result(Status.BLOCKED, before, before,
                        "Unable to create RootFS transaction journal", false);
            }
            journalResolved = false;

            try {
                transaction.activate();
            } catch (RuntimeException e) {
                boolean rolledBack = transaction.getState()
                        == RootFsActivationTransaction.State.ROLLED_BACK;
                if (rolledBack || transaction.getState() == RootFsActivationTransaction.State.NEW) {
                    journalResolved = deleteJournal(journal);
                }
                Status status = messageOf(e).contains("rollback failed")
                        ? Status.ROLLBACK_FAILED : Status.ACTIVATION_FAILED;
                String message = messageOf(e);
                if (!journalResolved) {
                    message = appendWarning(message,
                            "transaction journal was kept for recovery on the next attempt");
                }
                return result(status, before, inspectSafely(inspector, activity),
                        message, rolledBack);
            }

            RuntimeBaseInspection after = inspectSafely(inspector, activity);
            if (!after.isLaunchReady()
                    || after.getStatus() != RuntimeBaseInspection.Status.CURRENT) {
                boolean rolledBack = transaction.rollback();
                if (rolledBack) journalResolved = deleteJournal(journal);
                return result(rolledBack ? Status.FINAL_VALIDATION_FAILED : Status.ROLLBACK_FAILED,
                        before, after,
                        appendRollback(joinIssues(after), rolledBack), rolledBack);
            }

            if (!writeJournal(journal, transaction.getBackupRoot(), stagingRoot,
                    JOURNAL_PHASE_COMMITTING)) {
                boolean rolledBack = transaction.rollback();
                if (rolledBack) journalResolved = deleteJournal(journal);
                return result(rolledBack ? Status.ACTIVATION_FAILED : Status.ROLLBACK_FAILED,
                        before, after,
                        appendRollback("Unable to persist RootFS commit phase", rolledBack), rolledBack);
            }

            String warning = resetPostInstallState(activity);
            boolean backupCleaned = transaction.commit();
            if (!backupCleaned) {
                warning = appendWarning(warning,
                        "RootFS is valid; old backup cleanup is pending recovery");
            } else {
                journalResolved = deleteJournal(journal);
                if (!journalResolved) {
                    warning = appendWarning(warning,
                            "RootFS is valid, but transaction journal cleanup failed");
                }
            }

            RuntimeBaseInspection committed = inspectSafely(inspector, activity);
            if (warning.isEmpty()) {
                return result(Status.SUCCESS, before, committed,
                        "RootFS " + RuntimeBaseSpec.ROOTFS_VERSION + " activated successfully", false);
            }
            return result(Status.SUCCESS_WITH_WARNING, before, committed, warning, false);
        } finally {
            if (stagingRoot.exists() && journalResolved) FileUtils.delete(stagingRoot);
        }
    }

    private static String recoverInterruptedTransaction(AppCompatActivity activity,
            File activeRoot, File parent) {
        File journal = new File(parent, JOURNAL_FILE);
        if (!journal.isFile()) return "";

        Properties properties = new Properties();
        try (FileInputStream input = new FileInputStream(journal)) {
            properties.load(input);
        } catch (IOException | RuntimeException e) {
            return "Unable to read interrupted RootFS transaction journal: " + e.getClass().getSimpleName();
        }

        String backupName = properties.getProperty("backup", "").trim();
        String stagingName = properties.getProperty("staging", "").trim();
        String phase = properties.getProperty("phase", "").trim();
        if (!isSafeSiblingName(backupName, activeRoot.getName() + ".backup-")
                || !isSafeSiblingName(stagingName, activeRoot.getName() + ".staging-")
                || (!JOURNAL_PHASE_ACTIVATING.equals(phase)
                    && !JOURNAL_PHASE_COMMITTING.equals(phase))) {
            return "Interrupted RootFS transaction journal contains unsafe or invalid state";
        }

        File backup = new File(parent, backupName);
        File staging = new File(parent, stagingName);

        if (JOURNAL_PHASE_COMMITTING.equals(phase)) {
            RuntimeBaseInspection activeInspection = new RuntimeBaseInspector().inspect(
                    new RuntimeBaseTreeProbe(activeRoot, true, true));
            if (!activeInspection.isLaunchReady()) {
                return "Committed RootFS recovery found an invalid active RootFS; manual recovery is required";
            }

            String metadataError = resetPostInstallState(activity);
            if (!metadataError.isEmpty()) {
                return "Unable to finish RootFS post-install recovery: " + metadataError;
            }

            if (backup.exists() && !FileUtils.delete(backup)) {
                return "Unable to finish old RootFS backup cleanup after interrupted commit";
            }
        } else if (backup.exists()) {
            if (activeRoot.exists()) {
                if (!restorePreservedPath(activeRoot, backup, PRESERVE_HOME)) {
                    return "Unable to restore preserved home directory from interrupted RootFS transaction";
                }
                if (!restorePreservedPath(activeRoot, backup, PRESERVE_INSTALLED_WINE)) {
                    return "Unable to restore installed Wine directory from interrupted RootFS transaction";
                }
                if (!FileUtils.delete(activeRoot)) {
                    return "Unable to remove interrupted staged RootFS during recovery";
                }
            }
            if (!backup.renameTo(activeRoot)) {
                return "Unable to restore RootFS backup from interrupted transaction";
            }
        }

        if (staging.exists() && !FileUtils.delete(staging)) {
            return "Unable to clean interrupted RootFS staging directory";
        }
        if (!deleteJournal(journal)) {
            return "RootFS recovery completed but transaction journal could not be removed";
        }
        return "";
    }

    private static boolean restorePreservedPath(File activeRoot, File backupRoot,
            String relativePath) {
        File backupPath = new File(backupRoot, relativePath);
        if (backupPath.exists()) return true;

        File activePath = new File(activeRoot, relativePath);
        if (!activePath.exists()) return true;
        File parent = backupPath.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) return false;
        return activePath.renameTo(backupPath);
    }

    private static boolean writeJournal(File journal, File backup, File staging, String phase) {
        String data = "backup=" + backup.getName() + "\n"
                + "staging=" + staging.getName() + "\n"
                + "phase=" + phase + "\n";
        return FileUtils.writeString(journal, data);
    }

    private static boolean deleteJournal(File journal) {
        return !journal.exists() || journal.delete();
    }

    private static boolean isSafeSiblingName(String name, String expectedPrefix) {
        return name != null && !name.isEmpty() && name.startsWith(expectedPrefix)
                && name.indexOf('/') == -1 && name.indexOf('\\') == -1
                && name.indexOf("..") == -1;
    }

    private static String resetPostInstallState(AppCompatActivity activity) {
        String warning = "";
        try {
            ContainerManager manager = new ContainerManager(activity);
            for (Container container : manager.getContainers()) {
                String previousRfsVersion = container.getExtra("rfsVersion");
                if (!previousRfsVersion.isEmpty()
                        && WineInfo.isMainWineVersion(container.getWineVersion())) {
                    try {
                        if (Short.parseShort(previousRfsVersion)
                                <= RootFSInstaller.UPDATE_WINEPREFIX_VERSION) {
                            container.putExtra("wineprefixNeedsUpdate", "t");
                        }
                    } catch (NumberFormatException ignored) {}
                }
                container.putExtra("rfsVersion", null);
                container.saveData();
            }
        } catch (RuntimeException e) {
            warning = "RootFS is valid, but container metadata reset failed: " + messageOf(e);
        }

        try {
            SettingsFragment.resetPreferenceVersions(activity);
        } catch (RuntimeException e) {
            warning = appendWarning(warning,
                    "RootFS is valid, but runtime preference reset failed: " + messageOf(e));
        }
        return warning;
    }

    private static RuntimeBaseInspection inspectSafely(RuntimeBaseInspector inspector,
            AppCompatActivity activity) {
        try {
            return inspector.inspect(new WinlatorRuntimeBaseProbe(activity));
        } catch (RuntimeException e) {
            return inspector.inspect(new RuntimeBaseTreeProbe(
                    RootFS.find(activity).getRootDir(), false, false));
        }
    }

    private static String joinIssues(RuntimeBaseInspection inspection) {
        if (inspection == null) return "RootFS validation failed";
        StringBuilder builder = new StringBuilder();
        for (String issue : inspection.getLaunchIssues()) {
            if (issue == null || issue.isEmpty()) continue;
            if (builder.length() > 0) builder.append("; ");
            builder.append(issue);
        }
        return builder.length() == 0 ? "RootFS validation failed" : builder.toString();
    }

    private static String appendRollback(String message, boolean rolledBack) {
        String base = message == null || message.isEmpty() ? "RootFS validation failed" : message;
        return rolledBack ? base : base + "; rollback failed";
    }

    private static String appendWarning(String first, String second) {
        if (first == null || first.isEmpty()) return second == null ? "" : second;
        if (second == null || second.isEmpty()) return first;
        return first + "; " + second;
    }

    private static String messageOf(RuntimeException e) {
        String message = e.getMessage();
        return message == null || message.isEmpty() ? e.getClass().getSimpleName() : message;
    }

    private static Result result(Status status, RuntimeBaseInspection before,
            RuntimeBaseInspection after, String message, boolean rolledBack) {
        return new Result(status, before, after, message, rolledBack);
    }
}
