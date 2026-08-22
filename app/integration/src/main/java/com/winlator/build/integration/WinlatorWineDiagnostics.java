package com.winlator.build.integration;

import android.content.Context;
import android.view.View;
import android.widget.Button;

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
