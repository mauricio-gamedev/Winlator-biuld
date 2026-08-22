package com.winlator.build.integration;

import androidx.appcompat.app.AppCompatActivity;

import com.winlator.core.AppUtils;
import com.winlator.core.PreloaderDialog;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public final class WinlatorBox64MaintenanceController {
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();
    private static final AtomicBoolean RUNNING = new AtomicBoolean(false);

    private WinlatorBox64MaintenanceController() {}

    public static void prepare(AppCompatActivity activity) {
        if (activity == null || !RUNNING.compareAndSet(false, true)) return;

        AppUtils.keepScreenOn(activity);
        final PreloaderDialog dialog = new PreloaderDialog(activity);
        dialog.showOnUiThread("Preparing Box64 baseline...");

        EXECUTOR.execute(() -> {
            WinlatorBox64Installer.Result result = null;
            RuntimeException failure = null;
            try {
                result = WinlatorBox64Installer.prepareOrRepair(activity);
            } catch (RuntimeException e) {
                failure = e;
            } finally {
                RUNNING.set(false);
                dialog.closeOnUiThread();
            }

            if (failure != null) {
                AppUtils.showToast(activity, "Box64 preparation failed: " + messageOf(failure));
                return;
            }
            if (result == null) {
                AppUtils.showToast(activity, "Box64 preparation failed without a result.");
                return;
            }
            if (result.isSuccess()) {
                AppUtils.showToast(activity, result.getMessage());
            } else {
                AppUtils.showToast(activity, "Box64 preparation failed: " + result.getMessage());
            }
        });
    }

    private static String messageOf(RuntimeException error) {
        String message = error.getMessage();
        return message == null || message.isEmpty() ? error.getClass().getSimpleName() : message;
    }
}
