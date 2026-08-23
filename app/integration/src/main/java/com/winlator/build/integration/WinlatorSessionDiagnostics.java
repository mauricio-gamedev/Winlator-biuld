package com.winlator.build.integration;

import android.content.Context;
import android.view.View;
import android.widget.Button;

import com.winlator.R;
import com.winlator.contentdialog.ContentDialog;

import java.io.File;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;

public final class WinlatorSessionDiagnostics {
    private static final String FILE_NAME = "session-gate.log";
    private static final int MAX_DISPLAY_BYTES = 48 * 1024;
    private static final String SESSION_START_MARKER = "00 session-start";
    private static final String EXEC_COMMAND_MARKER = "exec:before-start command=";
    private static final String EXEC_EXCEPTION_MARKER = "exec:exception ";
    private static final String TAIL_START = "P1-tail-start";
    private static final String TAIL_END = "P1-tail-end";

    private WinlatorSessionDiagnostics() {}

    public static void show(Context context) {
        if (context == null) return;

        File file = new File(context.getFilesDir(), FILE_NAME);
        final boolean hasLog = file.isFile() && file.length() > 0;
        final String logText;
        if (hasLog) {
            String latest = latestSession(readTail(file));
            logText = launchCommand(latest) + "\n\n" + sessionState(latest) + "\n\n" + launchException(latest) + "\n\n" + preservedTerminationTail(latest);
        }
        else {
            logText = "No session-gate log has been recorded yet.\n\nRun a container once, then inspect this gate again.";
        }

        ContentDialog dialog = new ContentDialog(context);
        dialog.setTitle("Session gate — launch + state + tail");
        dialog.setMessage(logText);
        dialog.setBottomBarText(hasLog
                ? "Latest session — persisted launch snapshot and guest output"
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

    private static String launchCommand(String text) {
        if (text == null || text.isEmpty()) return "[launch command]\n(not captured)";
        String[] lines = text.split("\\r?\\n");
        for (String line : lines) {
            int marker = line.indexOf(EXEC_COMMAND_MARKER);
            if (marker >= 0) {
                return "[launch command]\n" + line.substring(marker + EXEC_COMMAND_MARKER.length()).trim();
            }
        }
        return "[launch command]\n(not captured)";
    }

    private static String sessionState(String text) {
        if (text == null || text.isEmpty()) return "[session state]\n(not captured)";
        boolean firstWindow = text.contains("09 first-renderable-window");
        boolean terminated = text.contains("G1 guest-terminated");
        boolean destroyed = text.contains("10 activity-destroy");
        boolean processCreated = text.contains("exec:process-created");
        boolean pidObtained = text.contains("exec:pid-obtained");
        boolean launchException = text.contains(EXEC_EXCEPTION_MARKER);
        return "[session state]\n"
                + "process-created=" + processCreated + "\n"
                + "pid-obtained=" + pidObtained + "\n"
                + "first-renderable-window=" + firstWindow + "\n"
                + "guest-terminated=" + terminated + "\n"
                + "activity-destroyed=" + destroyed + "\n"
                + "launch-exception=" + launchException;
    }

    private static String launchException(String text) {
        if (text == null || text.isEmpty()) return "[launch exception]\n(none captured)";
        String[] lines = text.split("\\r?\\n");
        for (int i = lines.length - 1; i >= 0; i--) {
            int marker = lines[i].indexOf(EXEC_EXCEPTION_MARKER);
            if (marker >= 0) {
                return "[launch exception]\n" + lines[i].substring(marker + EXEC_EXCEPTION_MARKER.length()).trim();
            }
        }
        return "[launch exception]\n(none captured)";
    }

    private static String preservedTerminationTail(String text) {
        if (text == null || text.isEmpty()) return text;
        String[] lines = text.split("\\r?\\n");
        int start = -1;
        int end = -1;
        int termination = -1;
        for (int i = lines.length - 1; i >= 0; i--) {
            if (termination < 0 && lines[i].contains("G1 guest-terminated")) termination = i;
            if (end < 0 && lines[i].contains(TAIL_END)) end = i;
            if (end >= 0 && lines[i].contains(TAIL_START)) {
                start = i;
                break;
            }
        }
        if (start < 0 || end < start) {
            return "[last guest output captured before exit]\n(not captured)";
        }
        StringBuilder builder = new StringBuilder();
        builder.append("[last guest output captured before exit]\n");
        for (int i = start + 1; i < end; i++) {
            String line = lines[i];
            int marker = line.indexOf("P1-tail ");
            if (marker >= 0) line = line.substring(marker + "P1-tail ".length());
            builder.append(line).append('\n');
        }
        if (termination >= 0) builder.append(lines[termination]);
        return builder.toString().trim();
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
            return new String(data, 0, offset, StandardCharsets.UTF_8);
        } catch (Throwable error) {
            String message = error.getMessage();
            return "Unable to read session-gate log: " + error.getClass().getSimpleName()
                    + (message == null || message.isEmpty() ? "" : ": " + message);
        }
    }
}
