package com.winlator.build.integration;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.text.method.ScrollingMovementMethod;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.winlator.R;
import com.winlator.contentdialog.ContentDialog;

import java.io.File;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;

public final class WinlatorSessionDiagnostics {
    private static final String FILE_NAME = "session-gate.log";
    private static final int MAX_DISPLAY_BYTES = 48 * 1024;

    private WinlatorSessionDiagnostics() {}

    public static void show(Context context) {
        if (context == null) return;

        File file = new File(context.getFilesDir(), FILE_NAME);
        ContentDialog dialog = new ContentDialog(context);
        dialog.setTitle("Session gate");

        final String logText;
        final boolean hasLog = file.isFile() && file.length() > 0;
        if (!hasLog) {
            logText = "No session-gate log has been recorded yet.\n\nRun a container once, then inspect this gate again.";
            dialog.setBottomBarText("Persistent diagnostic — survives Activity exit/crash");
        } else {
            logText = readTail(file);
            dialog.setBottomBarText("Persistent session trace: " + file.getAbsolutePath());
        }
        dialog.setMessage(logText);

        TextView message = dialog.findViewById(R.id.TVMessage);
        if (message != null) {
            message.setMovementMethod(new ScrollingMovementMethod());
            message.setVerticalScrollBarEnabled(true);
            message.setScrollbarFadingEnabled(false);
            message.setMaxLines(18);
            message.setTextIsSelectable(true);
        }

        Button cancel = dialog.findViewById(R.id.BTCancel);
        if (cancel != null) {
            if (hasLog) {
                cancel.setVisibility(View.VISIBLE);
                cancel.setText("COPY LOG");
                dialog.setOnCancelCallback(() -> {
                    ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
                    if (clipboard != null) {
                        clipboard.setPrimaryClip(ClipData.newPlainText("Winlator session gate", logText));
                        Toast.makeText(context, "Session log copied", Toast.LENGTH_SHORT).show();
                    }
                });
            } else {
                cancel.setVisibility(View.GONE);
            }
        }

        Button confirm = dialog.findViewById(R.id.BTConfirm);
        if (confirm != null) confirm.setText("OK");
        dialog.show();
    }

    private static String readTail(File file) {
        try (FileInputStream input = new FileInputStream(file)) {
            long length = file.length();
            int size = (int)Math.min(length, MAX_DISPLAY_BYTES);
            long skip = Math.max(0, length - size);
            while (skip > 0) {
                long skipped = input.skip(skip);
                if (skipped <= 0) break;
                skip -= skipped;
            }
            byte[] data = new byte[size];
            int offset = 0;
            while (offset < data.length) {
                int count = input.read(data, offset, data.length - offset);
                if (count < 0) break;
                offset += count;
            }
            String text = new String(data, 0, offset, StandardCharsets.UTF_8);
            if (length > MAX_DISPLAY_BYTES) {
                text = "[showing last " + MAX_DISPLAY_BYTES + " bytes]\n" + text;
            }
            return text;
        } catch (Throwable error) {
            String message = error.getMessage();
            return "Unable to read session-gate log: " + error.getClass().getSimpleName()
                    + (message == null || message.isEmpty() ? "" : ": " + message);
        }
    }
}
