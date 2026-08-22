package com.winlator.build.integration;

import android.content.Context;
import android.view.View;

import com.winlator.R;
import com.winlator.build.engine.runtime.Box64Inspection;
import com.winlator.build.engine.runtime.Box64Inspector;
import com.winlator.contentdialog.ContentDialog;

public final class WinlatorBox64Diagnostics {
    private WinlatorBox64Diagnostics() {}

    public static void show(Context context) {
        if (context == null) return;

        Box64Inspection inspection = new Box64Inspector().inspect(new WinlatorBox64Probe(context));
        ContentDialog dialog = new ContentDialog(context);
        dialog.setTitle("Box64 baseline");
        dialog.setMessage(format(inspection));
        dialog.setBottomBarText("Read-only diagnostic — no runtime files were changed");
        View cancel = dialog.findViewById(R.id.BTCancel);
        if (cancel != null) cancel.setVisibility(View.GONE);
        dialog.show();
    }

    static String format(Box64Inspection inspection) {
        StringBuilder builder = new StringBuilder();
        append(builder, "Status", inspection.getStatus().name());
        append(builder, "Selected", inspection.getSelectedVersion());
        append(builder, "Extracted", emptyAsNone(inspection.getCurrentExtractedVersion()));
        append(builder, "Package available", yesNo(inspection.isSelectedPackageAvailable()));
        append(builder, "Binary present", yesNo(inspection.isBinaryPresent()));
        append(builder, "Binary runnable", yesNo(inspection.isBinaryRunnable()));
        append(builder, "Default RC asset", yesNo(inspection.isDefaultRcAssetAvailable()));
        append(builder, "RC deployed", yesNo(inspection.isRcFilePresent()));
        append(builder, "Launch ready", yesNo(inspection.isLaunchReady()));
        append(builder, "Repair ready", yesNo(inspection.canRepair()));

        if (!inspection.getLaunchIssues().isEmpty()) {
            builder.append("\nLaunch issues:\n");
            for (String issue : inspection.getLaunchIssues()) builder.append("• ").append(issue).append('\n');
        }
        if (!inspection.getMaintenanceIssues().isEmpty()) {
            builder.append("\nMaintenance notes:\n");
            for (String issue : inspection.getMaintenanceIssues()) builder.append("• ").append(issue).append('\n');
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

    private static String emptyAsNone(String value) {
        return value == null || value.trim().isEmpty() ? "<none>" : value;
    }
}
