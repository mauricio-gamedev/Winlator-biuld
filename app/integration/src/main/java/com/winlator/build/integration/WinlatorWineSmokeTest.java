package com.winlator.build.integration;

import androidx.appcompat.app.AppCompatActivity;

import com.winlator.box64.Box64Preset;
import com.winlator.build.engine.runtime.WineInspection;
import com.winlator.build.engine.runtime.WineInspector;
import com.winlator.build.engine.runtime.WineSpec;
import com.winlator.core.Callback;
import com.winlator.core.EnvVars;
import com.winlator.core.ProcessHelper;
import com.winlator.xenvironment.RootFS;
import com.winlator.xenvironment.XEnvironment;
import com.winlator.xenvironment.components.GuestProgramLauncherComponent;

import java.io.File;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public final class WinlatorWineSmokeTest {
    private static final long TIMEOUT_SECONDS = 15L;
    private static final int MAX_OUTPUT_CHARS = 8192;

    public static final class Result {
        public final boolean passed;
        public final boolean timedOut;
        public final int exitCode;
        public final String output;
        public final String diagnostics;
        public final String message;

        Result(boolean passed, boolean timedOut, int exitCode, String output,
                String diagnostics, String message) {
            this.passed = passed;
            this.timedOut = timedOut;
            this.exitCode = exitCode;
            this.output = output == null ? "" : output.trim();
            this.diagnostics = diagnostics == null ? "" : diagnostics.trim();
            this.message = message == null ? "" : message;
        }
    }

    public interface Listener {
        void onFinished(Result result);
    }

    private WinlatorWineSmokeTest() {}

    public static void run(AppCompatActivity activity, Listener listener) {
        if (activity == null) return;
        Executors.newSingleThreadExecutor().execute(() -> {
            Result result = execute(activity);
            activity.runOnUiThread(() -> {
                if (listener != null) listener.onFinished(result);
            });
        });
    }

    static Result execute(AppCompatActivity activity) {
        StringBuilder diagnostics = new StringBuilder();
        checkpoint(diagnostics, "preflight:start");
        WineInspection preflight = new WineInspector().inspect(new WinlatorWineProbe(activity));
        checkpoint(diagnostics, "preflight:launchReady=" + preflight.isLaunchReady());
        if (!preflight.isLaunchReady()) {
            return new Result(false, false, -1, "", snapshot(diagnostics),
                    "Preflight blocked execution because the Wine baseline is not launch-ready.");
        }

        RootFS rootFS = RootFS.find(activity);
        File rootDir = rootFS.getRootDir();
        File wine = new File(rootDir, WineSpec.WINE_RELATIVE_PATH);
        File box64 = new File(rootDir, "usr/local/bin/box64");
        File loader = new File(rootDir, "lib/ld-linux-aarch64.so.1");
        checkpoint(diagnostics, "rootfs=" + rootDir.getPath());
        checkpoint(diagnostics, "loader=" + describeFile(loader));
        checkpoint(diagnostics, "box64=" + describeFile(box64));
        checkpoint(diagnostics, "wine=" + describeFile(wine));

        XEnvironment environment = new XEnvironment(activity, rootFS);
        GuestProgramLauncherComponent launcher = new GuestProgramLauncherComponent();
        launcher.setBox64Preset(Box64Preset.STABILITY);
        launcher.setGuestExecutable(wine.getPath() + " --version");

        EnvVars diagnosticEnv = new EnvVars();
        diagnosticEnv.put("BOX64_LOG", "1");
        diagnosticEnv.put("BOX64_NOBANNER", "0");
        launcher.setEnvVars(diagnosticEnv);

        CountDownLatch latch = new CountDownLatch(1);
        AtomicInteger exitCode = new AtomicInteger(Integer.MIN_VALUE);
        StringBuilder output = new StringBuilder();
        Callback<String> debugCallback = line -> {
            if (line == null) return;
            synchronized (output) {
                if (output.length() < MAX_OUTPUT_CHARS) {
                    if (output.length() > 0) output.append('\n');
                    output.append(line);
                }
            }
        };

        launcher.setTerminationCallback(status -> {
            exitCode.set(status);
            checkpoint(diagnostics, "terminationCallback:status=" + status);
            latch.countDown();
        });
        environment.addComponent(launcher);
        ProcessHelper.addDebugCallback(debugCallback);

        boolean timedOut = false;
        long startedAt = System.nanoTime();
        boolean sampled1s = false;
        boolean sampled5s = false;
        try {
            checkpoint(diagnostics, "launcher:start requested");
            environment.startEnvironmentComponents();
            checkpoint(diagnostics, "launcher:start returned");

            while (true) {
                if (latch.await(250, TimeUnit.MILLISECONDS)) break;
                long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
                if (!sampled1s && elapsedMs >= 1000) {
                    sampled1s = true;
                    appendProcessSnapshot(diagnostics, "processes@1s");
                    appendOutputCheckpoint(diagnostics, output, "output@1s");
                }
                if (!sampled5s && elapsedMs >= 5000) {
                    sampled5s = true;
                    appendProcessSnapshot(diagnostics, "processes@5s");
                    appendOutputCheckpoint(diagnostics, output, "output@5s");
                }
                if (elapsedMs >= TimeUnit.SECONDS.toMillis(TIMEOUT_SECONDS)) {
                    timedOut = true;
                    appendProcessSnapshot(diagnostics, "processes@timeout");
                    appendOutputCheckpoint(diagnostics, output, "output@timeout");
                    break;
                }
            }
        } catch (Throwable error) {
            checkpoint(diagnostics, "exception=" + error.getClass().getSimpleName() + ":" + error.getMessage());
            return new Result(false, false, -1, snapshot(output), snapshot(diagnostics),
                    "Guest launcher smoke test failed: " + error.getClass().getSimpleName());
        } finally {
            checkpoint(diagnostics, "cleanup:start");
            try {
                environment.stopEnvironmentComponents();
                checkpoint(diagnostics, "cleanup:environment stopped");
            } catch (Throwable error) {
                checkpoint(diagnostics, "cleanup:error=" + error.getClass().getSimpleName());
            }
            ProcessHelper.removeDebugCallback(debugCallback);
            checkpoint(diagnostics, "cleanup:debug callback removed");
        }

        if (timedOut) {
            return new Result(false, true, -1, snapshot(output), snapshot(diagnostics),
                    "Guest launcher smoke test timed out after " + TIMEOUT_SECONDS + " seconds.");
        }

        int status = exitCode.get();
        String captured = snapshot(output);
        boolean versionSeen = captured.toLowerCase().contains("wine-") || captured.contains(WineSpec.VERSION);
        boolean passed = status == 0 && versionSeen;
        checkpoint(diagnostics, "result:versionSeen=" + versionSeen + ",exitCode=" + status);
        String message = passed
                ? "GuestProgramLauncherComponent launched Box64 and Wine successfully."
                : "Guest launcher finished, but status/output did not prove a valid Wine launch.";
        return new Result(passed, false, status, captured, snapshot(diagnostics), message);
    }

    private static String describeFile(File file) {
        return file.getPath() + " [file=" + file.isFile() + ",exec=" + file.canExecute() + "]";
    }

    private static void checkpoint(StringBuilder diagnostics, String value) {
        synchronized (diagnostics) {
            if (diagnostics.length() > 0) diagnostics.append('\n');
            diagnostics.append(value);
        }
    }

    private static void appendOutputCheckpoint(StringBuilder diagnostics, StringBuilder output, String label) {
        String captured = snapshot(output);
        checkpoint(diagnostics, label + ": chars=" + captured.length()
                + (captured.isEmpty() ? "" : ", first=" + firstLine(captured)));
    }

    private static String firstLine(String value) {
        int newline = value.indexOf('\n');
        String line = newline >= 0 ? value.substring(0, newline) : value;
        return line.length() > 180 ? line.substring(0, 180) + "…" : line;
    }

    private static void appendProcessSnapshot(StringBuilder diagnostics, String label) {
        List<ProcessHelper.PStat> processes = ProcessHelper.getChildProcesses();
        checkpoint(diagnostics, label + ": count=" + processes.size());
        int limit = Math.min(processes.size(), 12);
        for (int i = 0; i < limit; i++) {
            ProcessHelper.PStat p = processes.get(i);
            checkpoint(diagnostics, "  pid=" + p.pid + " name=" + p.name + " state=" + p.state
                    + " ppid=" + p.parentPID + " guest=" + p.guestProcess);
        }
        if (processes.size() > limit) checkpoint(diagnostics, "  … " + (processes.size() - limit) + " more");
    }

    private static String snapshot(StringBuilder builder) {
        synchronized (builder) {
            return builder.toString().trim();
        }
    }
}
