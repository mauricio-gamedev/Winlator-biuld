package com.winlator.build.integration;

import android.content.Context;
import android.view.View;
import android.widget.Button;

import com.winlator.R;
import com.winlator.build.engine.runtime.Box64Inspection;
import com.winlator.build.engine.runtime.Box64Inspector;
import com.winlator.build.engine.runtime.WineInspection;
import com.winlator.build.engine.runtime.WineInspector;
import com.winlator.contentdialog.ContentDialog;

public final class WinlatorWineDiagnostics {
    private WinlatorWineDiagnostics() {}

    public static void show(Context context) {
        if (context == null) return;

        Box64Inspection box64 = new Box64Inspector().inspect(new WinlatorBox64Probe(context));
        WineInspection inspection = new WineInspector().inspect(
                new WinlatorWineProbe(context, box64.isLaunchReady()));

        ContentDialog dialog = new ContentDialog(context);
        dialog.setTitle("Wine baseline");
        dialog.setMessage(format(inspection));
        dialog.setBottomBarText("Read-only diagnostic — no Wine prefix/container was created");

        View cancel = dialog.findViewById(R.id.BTCancel);
        if (cancel != null) cancel.setVisibility(View.GONE);
        Button confirm = dialog.findViewById(R.id.BTConfirm);
        if (confirm != null) confirm.setText("OK");
        dialog.show();
    }

    static String format(WineInspection inspection) {
        StringBuilder builder = new StringBuilder();
        append(builder, "Status", inspection.getStatus().name());
        append(builder, "Expected", inspection.getExpectedVersion());
        append(builder, "RootFS ready", yesNo(inspection.isRootFsReady()));
        append(builder, "Box64 ready", yesNo(inspection.isBox64Ready()));
        append(builder, "Wine directory", yesNo(inspection.isWineDirectoryPresent()));
        append(builder, "wine present", yesNo(inspection.isWineBinaryPresent()));
        append(builder, "wine runnable", yesNo(inspection.isWineBinaryRunnable()));
        append(builder, "wineserver present", yesNo(inspection.isWineServerPresent()));
        append(builder, "wineserver runnable", yesNo(inspection.isWineServerRunnable()));
        append(builder, "wine64 present", yesNo(inspection.isWine64BinaryPresent()));
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
