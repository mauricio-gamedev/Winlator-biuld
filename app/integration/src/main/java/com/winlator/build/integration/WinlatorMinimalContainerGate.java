package com.winlator.build.integration;

import android.content.Context;

import com.winlator.container.Container;
import com.winlator.container.ContainerManager;
import com.winlator.core.Callback;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;

public final class WinlatorMinimalContainerGate {
    public static final class Result {
        public final boolean passed;
        public final String message;
        public final Container container;

        Result(boolean passed, String message, Container container) {
            this.passed = passed;
            this.message = message == null ? "" : message;
            this.container = container;
        }
    }

    private WinlatorMinimalContainerGate() {}

    public static void create(Context context, Callback<Result> callback) {
        if (context == null) {
            if (callback != null) callback.call(new Result(false, "Context is unavailable", null));
            return;
        }

        ContainerManager manager = new ContainerManager(context.getApplicationContext());
        JSONObject data = new JSONObject();
        try {
            data.put("name", "Winlator Build Minimal");
            data.put("envVars", "");
            data.put("wincomponents", Container.FALLBACK_WINCOMPONENTS);
            data.put("hudMode", 0);
            data.put("startupSelection", Container.STARTUP_SELECTION_ESSENTIAL);
        } catch (JSONException e) {
            if (callback != null) callback.call(new Result(false, "Unable to build minimal container configuration", null));
            return;
        }

        manager.createContainerAsync(data, container -> {
            if (container == null) {
                if (callback != null) callback.call(new Result(false, "ContainerManager failed to create the minimal container", null));
                return;
            }

            String validationError = validate(container);
            if (!validationError.isEmpty()) {
                manager.removeContainerAsync(container, () -> {
                    if (callback != null) callback.call(new Result(false,
                            validationError + "; invalid test container was removed", null));
                });
                return;
            }

            if (callback != null) callback.call(new Result(true,
                    "Minimal container structure was created and validated", container));
        });
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
}
