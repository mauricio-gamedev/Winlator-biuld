package com.winlator.build.integration;

import android.content.Context;
import android.view.View;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;

import com.winlator.R;
import com.winlator.build.engine.runtime.WineInspection;
import com.winlator.build.engine.runtime.WineInspector;
import com.winlator.build.engine.runtime.WineSpec;
import com.winlator.contentdialog.ContentDialog;

public final class WinlatorWineDiagnostics {
    private WinlatorWineDiagnostics() {}

    public static void show(Context context) {
        if (context == null) return;

        WineInspection inspection = new WineInspector().inspect(new WinlatorWineProbe(context));
        ContentDialog dialog = new ContentDialog(context);
        dialog.setTitle("Wine baseline");
        dialog.setMessage(format(inspection));

        View cancel = dialog.findViewById(R.id.BTCancel);
        if (cancel != null) cancel.setVisibility(View.GONE);
        Button confirm = dialog.findViewById(R.id.BTConfirm);

        if (inspection.isLaunchReady() && context instanceof AppCompatActivity && confirm != null) {
            dialog.setBottomBarText("Controlled smoke test: wine --version via the real guest launcher");
            confirm.setText("Run smoke test");
            dialog.setOnConfirmCallback(() -> runSmokeTest((AppCompatActivity)context));
        } else {
            dialog.setBottomBarText("Read-only diagnostic — no Wine prefix/container was created");
            if (confirm != null) confirm.setText("OK");
        }
        dialog.show();
    }

    private static void runSmokeTest(AppCompatActivity activity) {
        ContentDialog progress = new ContentDialog(activity);
        progress.setTitle("Wine smoke test");
        progress.setMessage("Running wine --version through the real GuestProgramLauncherComponent…\n\nTimeout: 15 seconds\nNo game will be launched.");
        View cancel = progress.findViewById(R.id.BTCancel);
        if (cancel != null) cancel.setVisibility(View.GONE);
        Button confirm = progress.findViewById(R.id.BTConfirm);
        if (confirm != null) {
            confirm.setText("Running…");
            confirm.setEnabled(false);
        }
        progress.show();

        WinlatorWineSmokeTest.run(activity, result -> {
            try { progress.dismiss(); } catch (Throwable ignored) {}
            ContentDialog done = new ContentDialog(activity);
            done.setTitle(result.passed ? "Wine smoke test PASSED" : "Wine smoke test FAILED");
            StringBuilder message = new StringBuilder();
            message.append("Passed: ").append(result.passed ? "YES" : "NO").append('\n');
            message.append("Timed out: ").append(result.timedOut ? "YES" : "NO").append('\n');
            message.append("Exit code: ").append(result.exitCode).append('\n');
            message.append("Result: ").append(result.message);
            if (!result.output.isEmpty()) {
                message.append("\n\nCaptured output:\n").append(result.output);
            }
            if (!result.diagnostics.isEmpty()) {
                message.append("\n\nDiagnostics:\n").append(result.diagnostics);
            }
            done.setMessage(message.toString());

            View doneCancel = done.findViewById(R.id.BTCancel);
            Button doneConfirm = done.findViewById(R.id.BTConfirm);
            if (result.passed && doneConfirm != null) {
                done.setBottomBarText("Next gate: create and validate one minimal upstream container");
                if (doneCancel != null) {
                    doneCancel.setVisibility(View.VISIBLE);
                    doneCancel.setOnClickListener(v -> done.dismiss());
                }
                doneConfirm.setText("Create minimal container");
                done.setOnConfirmCallback(() -> runMinimalContainerGate(activity));
            } else {
                done.setBottomBarText("Guest cleanup requested; diagnostic state captured before cleanup");
                if (doneCancel != null) doneCancel.setVisibility(View.GONE);
                if (doneConfirm != null) doneConfirm.setText("OK");
            }
            done.show();
        });
    }

    private static void runMinimalContainerGate(AppCompatActivity activity) {
        ContentDialog progress = new ContentDialog(activity);
        progress.setTitle("Minimal container gate");
        progress.setMessage("Creating one upstream container with the main Wine runtime and minimal WinComponents…\n\nNo graphical session or game will be launched.");
        View cancel = progress.findViewById(R.id.BTCancel);
        if (cancel != null) cancel.setVisibility(View.GONE);
        Button confirm = progress.findViewById(R.id.BTConfirm);
        if (confirm != null) {
            confirm.setText("Creating…");
            confirm.setEnabled(false);
        }
        progress.show();

        WinlatorMinimalContainerGate.create(activity, result -> {
            try { progress.dismiss(); } catch (Throwable ignored) {}
            ContentDialog done = new ContentDialog(activity);
            done.setTitle(result.passed ? "Minimal container PASSED" : "Minimal container FAILED");
            StringBuilder message = new StringBuilder();
            message.append("Passed: ").append(result.passed ? "YES" : "NO").append('\n');
            message.append("Reused: ").append(result.reused ? "YES" : "NO").append('\n');
            if (result.container != null) {
                message.append("Container id: ").append(result.container.id).append('\n');
                message.append("Name: ").append(result.container.getName()).append('\n');
                message.append("Root: ").append(result.container.getRootDir().getPath()).append('\n');
            }
            message.append("Result: ").append(result.message);
            done.setMessage(message.toString());
            done.setBottomBarText(result.passed
                    ? "Container kept for the next prefix/session gate"
                    : "Invalid test containers are removed automatically");
            View doneCancel = done.findViewById(R.id.BTCancel);
            if (doneCancel != null) doneCancel.setVisibility(View.GONE);
            Button doneConfirm = done.findViewById(R.id.BTConfirm);
            if (doneConfirm != null) doneConfirm.setText("OK");
            done.show();
        });
    }

    static String format(WineInspection inspection) {
        StringBuilder builder = new StringBuilder();
        append(builder, "Status", inspection.getStatus().name());
        append(builder, "Expected", WineSpec.VERSION);
        append(builder, "RootFS ready", yesNo(inspection.isRootFsReady()));
        append(builder, "Box64 ready", yesNo(inspection.isBox64Ready()));
        append(builder, "Wine directory", yesNo(inspection.isWineDirPresent()));
        append(builder, "wine present", yesNo(inspection.isWinePresent()));
        append(builder, "wine runnable", yesNo(inspection.isWineRunnable()));
        append(builder, "wineserver present", yesNo(inspection.isWineServerPresent()));
        append(builder, "wineserver runnable", yesNo(inspection.isWineServerRunnable()));
        append(builder, "Launch ready", yesNo(inspection.isLaunchReady()));

        if (!inspection.getIssues().isEmpty()) {
            builder.append("\nIssues:\n");
            for (String issue : inspection.getIssues()) builder.append("• ").append(issue).append('\n');
        }
        return builder.toString().trim();
    }

    private static void append(StringBuilder builder, String label, String value) {
        if (builder.length() > 0) builder.append('\n');
        builder.append(label).append(": ").append(value);
    }

    private static String yesNo(boolean value) {
        return value ? "YES" : "NO";
    }
}
