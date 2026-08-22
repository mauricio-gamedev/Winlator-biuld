package com.winlator.build.integration;

import androidx.appcompat.app.AppCompatActivity;

import com.winlator.R;
import com.winlator.build.engine.runtime.RuntimeBaseInspection;
import com.winlator.build.engine.runtime.RuntimeBaseInspector;
import com.winlator.core.AppUtils;
import com.winlator.core.PreloaderDialog;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public final class WinlatorRootFsMaintenanceController {
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();
    private static final AtomicBoolean RUNNING = new AtomicBoolean(false);

    private WinlatorRootFsMaintenanceController() {}

    public static void ensure(AppCompatActivity activity) {
        run(activity, false);
    }

    public static void repair(AppCompatActivity activity) {
        run(activity, true);
    }

    private static void run(AppCompatActivity activity, boolean forceRepair) {
        if (activity == null) return;

        RuntimeBaseInspection current;
        try {
            current = new RuntimeBaseInspector().inspect(new WinlatorRuntimeBaseProbe(activity));
        } catch (RuntimeException e) {
            current = null;
        }

        if (!forceRepair && current != null && current.isLaunchReady()) return;
        if (current != null && current.getStatus() == RuntimeBaseInspection.Status.FUTURE) {
            AppUtils.showToast(activity,
                    "A newer RootFS is installed. Automatic downgrade was blocked.");
            return;
        }

        if (!RUNNING.compareAndSet(false, true)) return;

        AppUtils.keepScreenOn(activity);
        final PreloaderDialog dialog = new PreloaderDialog(activity);
        dialog.showOnUiThread(R.string.installing_system_files);

        EXECUTOR.execute(() -> {
            WinlatorRootFsInstaller.Result result = null;
            RuntimeException failure = null;
            try {
                result = WinlatorRootFsInstaller.installOrRepair(activity, forceRepair);
            } catch (RuntimeException e) {
                failure = e;
            } finally {
                RUNNING.set(false);
                dialog.closeOnUiThread();
            }

            if (failure != null) {
                AppUtils.showToast(activity,
                        "RootFS maintenance failed: " + messageOf(failure));
                return;
            }
            if (result == null) {
                AppUtils.showToast(activity, "RootFS maintenance failed without a result.");
                return;
            }

            if (result.getStatus() == WinlatorRootFsInstaller.Status.SUCCESS_WITH_WARNING) {
                AppUtils.showToast(activity, result.getMessage());
            } else if (!result.isSuccess()) {
                AppUtils.showToast(activity,
                        "RootFS maintenance failed: " + result.getMessage());
            } else if (forceRepair
                    && result.getStatus() == WinlatorRootFsInstaller.Status.SUCCESS) {
                AppUtils.showToast(activity, "System files reinstalled successfully.");
            }
        });
    }

    private static String messageOf(RuntimeException error) {
        String message = error.getMessage();
        return message == null || message.isEmpty()
                ? error.getClass().getSimpleName() : message;
    }
}
