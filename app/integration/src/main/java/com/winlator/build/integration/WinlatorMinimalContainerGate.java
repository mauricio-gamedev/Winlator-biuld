package com.winlator.build.integration;

import android.content.Context;

import com.winlator.container.Container;
import com.winlator.container.ContainerManager;
import com.winlator.core.Callback;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;

public final class WinlatorMinimalContainerGate {
    private static final String TEST_NAME = "Winlator Build Minimal";

    public static final class Result {
        public final boolean passed;
        public final boolean reused;
        public final String message;
        public final Container container;

        Result(boolean passed, boolean reused, String message, Container container) {
            this.passed = passed;
            this.reused = reused;
            this.message = message == null ? "" : message;
            this.container = container;
        }
    }

    private WinlatorMinimalContainerGate() {}

    public static void create(Context context, Callback<Result> callback) {
        if (context == null) {
            finish(callback, new Result(false, false, "Context is unavailable", null));
            return;
        }

        ContainerManager manager = new ContainerManager(context.getApplicationContext());
        Container existing = findExisting(manager);
        if (existing != null) {
            String error = validate(existing);
            if (error.isEmpty()) {
                finish(callback, new Result(true, true,
                        "Existing minimal container was revalidated successfully", existing));
            } else {
                manager.removeContainerAsync(existing, () -> finish(callback,
                        new Result(false, false,
                                error + "; invalid previous test container was removed", null)));
            }
            return;
        }

        JSONObject data = new JSONObject();
        try {
            data.put("name", TEST_NAME);
            data.put("envVars", "");
            data.put("wincomponents", Container.FALLBACK_WINCOMPONENTS);
            data.put("hudMode", 0);
            data.put("startupSelection", Container.STARTUP_SELECTION_ESSENTIAL);
        } catch (JSONException e) {
            finish(callback, new Result(false, false,
                    "Unable to build minimal container configuration", null));
            return;
        }

        manager.createContainerAsync(data, container -> {
            if (container == null) {
                finish(callback, new Result(false, false,
                        "ContainerManager failed to create the minimal container", null));
                return;
            }

            String validationError = validate(container);
            if (!validationError.isEmpty()) {
                manager.removeContainerAsync(container, () -> finish(callback,
                        new Result(false, false,
                                validationError + "; invalid test container was removed", null)));
                return;
            }

            finish(callback, new Result(true, false,
                    "Minimal container structure was created and validated", container));
        });
    }

    private static Container findExisting(ContainerManager manager) {
        for (Container container : manager.getContainers()) {
            if (TEST_NAME.equals(container.getName())) return container;
        }
        return null;
    }

    private static String validate(Container container) {
        File root = container.getRootDir();
        if (root == null || !root.isDirectory()) return "Container root directory is missing";

        File config = container.getConfigFile();
        if (!config.isFile() || config.length() == 0) return "Container configuration file is missing";

        File winePrefix = new File(root, ".wine");
        if (!winePrefix.isDirectory()) return "Wine prefix directory is missing";

        File driveC = new File(winePrefix, "drive_c");
        if (!driveC.isDirectory()) return "Wine drive_c directory is missing";

        File windows = new File(driveC, "windows");
        if (!windows.isDirectory()) return "Wine Windows directory is missing";

        File system32 = new File(windows, "system32");
        if (!system32.isDirectory()) return "Wine system32 directory is missing";

        File userReg = new File(winePrefix, "user.reg");
        File systemReg = new File(winePrefix, "system.reg");
        if (!userReg.isFile() || userReg.length() == 0) return "Wine user.reg is missing";
        if (!systemReg.isFile() || systemReg.length() == 0) return "Wine system.reg is missing";

        if (!Container.FALLBACK_WINCOMPONENTS.equals(container.getWinComponents())) {
            return "Minimal WinComponents policy was not preserved";
        }
        return "";
    }

    private static void finish(Callback<Result> callback, Result result) {
        if (callback != null) callback.call(result);
    }
}
