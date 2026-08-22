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
import java.util.Arrays;
import java.util.UUID;

public final class WinlatorRootFsInstaller {
    public enum Status {
        SUCCESS,
        SUCCESS_WITH_WARNING,
        NOT_NEEDED,
        BLOCKED,
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

    private WinlatorRootFsInstaller() {}

    public static Result installOrRepair(AppCompatActivity activity) {
        return installOrRepair(activity, false);
    }

    public static Result installOrRepair(AppCompatActivity activity, boolean forceRepair) {
        if (activity == null) throw new IllegalArgumentException("activity is required");

        RuntimeBaseInspector inspector = new RuntimeBaseInspector();
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

        RootFS rootFS = RootFS.find(activity);
        File activeRoot = rootFS.getRootDir();
        File parent = activeRoot.getAbsoluteFile().getParentFile();
        if (parent == null || (!parent.isDirectory() && !parent.mkdirs())) {
            return result(Status.BLOCKED, before, before,
                    "Unable to prepare RootFS parent directory", false);
        }

        File stagingRoot = new File(parent,
                activeRoot.getName() + ".staging-" + UUID.randomUUID().toString().replace("-", ""));
        if (!stagingRoot.mkdirs()) {
            return result(Status.BLOCKED, before, before,
                    "Unable to create RootFS staging directory", false);
        }

        RootFsActivationTransaction transaction = null;
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
            try {
                transaction.activate();
            } catch (RuntimeException e) {
                Status status = messageOf(e).contains("rollback failed")
                        ? Status.ROLLBACK_FAILED : Status.ACTIVATION_FAILED;
                return result(status, before, inspectSafely(inspector, activity),
                        messageOf(e), status != Status.ROLLBACK_FAILED);
            }

            RuntimeBaseInspection after = inspectSafely(inspector, activity);
            if (!after.isLaunchReady()
                    || after.getStatus() != RuntimeBaseInspection.Status.CURRENT) {
                boolean rolledBack = transaction.rollback();
                return result(rolledBack ? Status.FINAL_VALIDATION_FAILED : Status.ROLLBACK_FAILED,
                        before, after,
                        appendRollback(joinIssues(after), rolledBack), rolledBack);
            }

            String warning = resetPostInstallState(activity);
            boolean backupCleaned = transaction.commit();
            if (!backupCleaned) {
                warning = appendWarning(warning,
                        "RootFS was activated but old backup cleanup failed");
            }

            RuntimeBaseInspection committed = inspectSafely(inspector, activity);
            if (warning.isEmpty()) {
                return result(Status.SUCCESS, before, committed,
                        "RootFS " + RuntimeBaseSpec.ROOTFS_VERSION + " activated successfully", false);
            }
            return result(Status.SUCCESS_WITH_WARNING, before, committed, warning, false);
        } finally {
            if (stagingRoot.exists()) FileUtils.delete(stagingRoot);
        }
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
