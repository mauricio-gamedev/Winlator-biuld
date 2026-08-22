package com.winlator.build.integration;

import androidx.appcompat.app.AppCompatActivity;

import com.winlator.box64.Box64Preset;
import com.winlator.build.engine.runtime.WineInspection;
import com.winlator.build.engine.runtime.WineInspector;
import com.winlator.build.engine.runtime.WineSpec;
import com.winlator.core.Callback;
import com.winlator.core.ProcessHelper;
import com.winlator.xenvironment.RootFS;
import com.winlator.xenvironment.XEnvironment;
import com.winlator.xenvironment.components.GuestProgramLauncherComponent;

import java.io.File;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public final class WinlatorWineSmokeTest {
    private static final long TIMEOUT_SECONDS = 15L;

    public static final class Result {
        public final boolean passed;
        public final boolean timedOut;
        public final int exitCode;
        public final String output;
        public final String message;

        Result(boolean passed, boolean timedOut, int exitCode, String output, String message) {
            this.passed = passed;
            this.timedOut = timedOut;
            this.exitCode = exitCode;
            this.output = output == null ? "" : output.trim();
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
        WineInspection preflight = new WineInspector().inspect(new WinlatorWineProbe(activity));
        if (!preflight.isLaunchReady()) {
            return new Result(false, false, -1, "",
                    "Preflight blocked execution because the Wine baseline is not launch-ready.");
        }

        RootFS rootFS = RootFS.find(activity);
        File wine = new File(rootFS.getRootDir(), WineSpec.WINE_RELATIVE_PATH);
        XEnvironment environment = new XEnvironment(activity, rootFS);
        GuestProgramLauncherComponent launcher = new GuestProgramLauncherComponent();
        launcher.setBox64Preset(Box64Preset.STABILITY);
        launcher.setGuestExecutable(wine.getPath() + " --version");

        CountDownLatch latch = new CountDownLatch(1);
        AtomicInteger exitCode = new AtomicInteger(Integer.MIN_VALUE);
        StringBuilder output = new StringBuilder();
        Callback<String> debugCallback = line -> {
            if (line == null) return;
            synchronized (output) {
                if (output.length() < 8192) {
                    if (output.length() > 0) output.append('\n');
                    output.append(line);
                }
            }
        };

        launcher.setTerminationCallback(status -> {
            exitCode.set(status);
            latch.countDown();
        });
        environment.addComponent(launcher);
        ProcessHelper.addDebugCallback(debugCallback);

        boolean timedOut = false;
        try {
            environment.startEnvironmentComponents();
            if (!latch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)) timedOut = true;
        } catch (Throwable error) {
            return new Result(false, false, -1, snapshot(output),
                    "Smoke test failed to start: " + error.getClass().getSimpleName());
        } finally {
            try {
                environment.stopEnvironmentComponents();
            } catch (Throwable ignored) {}
            ProcessHelper.removeDebugCallback(debugCallback);
        }

        if (timedOut) {
            return new Result(false, true, -1, snapshot(output),
                    "Smoke test timed out after " + TIMEOUT_SECONDS + " seconds; guest cleanup was requested.");
        }

        int status = exitCode.get();
        String captured = snapshot(output);
        boolean versionSeen = captured.toLowerCase().contains("wine-") || captured.contains(WineSpec.VERSION);
        boolean passed = status == 0 && versionSeen;
        String message = passed
                ? "Box64 launched Wine and wine --version exited cleanly."
                : "Process finished, but exit status/output did not prove a valid Wine launch.";
        return new Result(passed, false, status, captured, message);
    }

    private static String snapshot(StringBuilder builder) {
        synchronized (builder) {
            return builder.toString().trim();
        }
    }
}
