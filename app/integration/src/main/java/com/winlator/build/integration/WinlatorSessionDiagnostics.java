package com.winlator.build.integration;

import android.content.Context;
import android.view.View;
import android.widget.Button;

import com.winlator.R;
import com.winlator.contentdialog.ContentDialog;

import java.io.File;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public final class WinlatorSessionDiagnostics {
    private static final String FILE_NAME = "session-gate.log";
    private static final int MAX_DISPLAY_BYTES = 48 * 1024;
    private static final String SESSION_START_MARKER = "00 session-start";
    private static final int MAX_FAILURE_LINES = 16;

    private WinlatorSessionDiagnostics() {}

    public static void show(Context context) {
        if (context == null) return;

        File file = new File(context.getFilesDir(), FILE_NAME);
        final boolean hasLog = file.isFile() && file.length() > 0;
        final String logText = hasLog
                ? failureTail(latestSession(readTail(file)))
                : "No session-gate log has been recorded yet.\n\nRun a container once, then inspect this gate again.";

        ContentDialog dialog = new ContentDialog(context);
        dialog.setTitle("Session gate — Box64/Wine failure");
        dialog.setMessage(logText);
        dialog.setBottomBarText(hasLog
                ? "Latest session — final Box64/Wine failure events only"
                : "Persistent diagnostic — survives Activity exit/crash");

        View cancel = dialog.findViewById(R.id.BTCancel);
        if (cancel != null) cancel.setVisibility(View.GONE);
        Button confirm = dialog.findViewById(R.id.BTConfirm);
        if (confirm != null) confirm.setText("OK");
        dialog.show();
    }

    private static String latestSession(String text) {
        if (text == null || text.isEmpty()) return text;

        int marker = text.lastIndexOf(SESSION_START_MARKER);
        if (marker < 0) return text;

        int lineStart = text.lastIndexOf('\n', marker);
        if (lineStart < 0) lineStart = 0;
        else lineStart += 1;

        String latest = text.substring(lineStart).trim();
        return latest.isEmpty() ? text : latest;
    }

    private static String failureTail(String text) {
        if (text == null || text.isEmpty()) return text;

        String[] lines = text.split("\\r?\\n");
        List<String> relevant = new ArrayList<>();
        for (String line : lines) {
            String lower = line.toLowerCase();
            boolean guestOutput = line.contains("P1 guest-output");
            boolean box64OrWine = line.contains("[BOX64]")
                    || lower.contains("wine:")
                    || lower.contains("error")
                    || lower.contains("failed")
                    || lower.contains("missing")
                    || lower.contains("cannot")
                    || lower.contains("could not")
                    || lower.contains("not found");

            if ((guestOutput && box64OrWine)
                    || line.contains("G1 guest-terminated")
                    || line.contains("CRASH ")) {
                relevant.add(line);
            }
        }

        if (relevant.isEmpty()) {
            return "No Box64/Wine failure output was found in the latest session.";
        }

        int start = Math.max(0, relevant.size() - MAX_FAILURE_LINES);
        StringBuilder builder = new StringBuilder();
        if (start > 0) builder.append("[showing final ").append(MAX_FAILURE_LINES).append(" matching events]\n");
        for (int i = start; i < relevant.size(); i++) {
            if (builder.length() > 0) builder.append('\n');
            builder.append(relevant.get(i));
        }
        return builder.toString();
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
