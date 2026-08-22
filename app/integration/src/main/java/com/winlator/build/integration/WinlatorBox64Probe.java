package com.winlator.build.integration;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.preference.PreferenceManager;

import com.winlator.build.engine.runtime.Box64Probe;
import com.winlator.build.engine.runtime.Box64Spec;
import com.winlator.core.GeneralComponents;
import com.winlator.xenvironment.RootFS;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

public final class WinlatorBox64Probe implements Box64Probe {
    private static final String PREF_SELECTED_VERSION = "box64_version";
    private static final String PREF_CURRENT_VERSION = "current_box64_version";

    private final Context context;
    private final SharedPreferences preferences;
    private final File rootDir;

    public WinlatorBox64Probe(Context context) {
        if (context == null) throw new IllegalArgumentException("context is required");
        Context appContext = context.getApplicationContext();
        this.context = appContext != null ? appContext : context;
        this.preferences = PreferenceManager.getDefaultSharedPreferences(this.context);
        this.rootDir = RootFS.find(this.context).getRootDir();
    }

    @Override
    public String getSelectedVersion() {
        return preferences.getString(PREF_SELECTED_VERSION, Box64Spec.VERSION);
    }

    @Override
    public String getCurrentExtractedVersion() {
        return preferences.getString(PREF_CURRENT_VERSION, "");
    }

    @Override
    public boolean isSelectedPackageAvailable() {
        String selected = normalize(getSelectedVersion());
        if (selected.isEmpty()) return false;

        if (GeneralComponents.isBuiltinComponent(GeneralComponents.Type.BOX64, selected)) {
            return hasAsset("box64/box64-" + selected + ".tzst");
        }

        return containsIgnoreCase(
                GeneralComponents.getInstalledComponentNames(
                        GeneralComponents.Type.BOX64, context),
                selected);
    }

    @Override
    public boolean isBinaryPresent() {
        return getBinaryFile().isFile();
    }

    @Override
    public boolean isBinaryRunnable() {
        File binary = getBinaryFile();
        return binary.isFile() && binary.canExecute();
    }

    @Override
    public boolean isDefaultRcAssetAvailable() {
        return hasAsset(Box64Spec.DEFAULT_RC_ASSET);
    }

    @Override
    public boolean isRcFilePresent() {
        return new File(rootDir, Box64Spec.RC_RELATIVE_PATH).isFile();
    }

    public File getBinaryFile() {
        return new File(rootDir, Box64Spec.BINARY_RELATIVE_PATH);
    }

    public File getRootDir() {
        return rootDir;
    }

    private boolean hasAsset(String path) {
        try (InputStream ignored = context.getAssets().open(path)) {
            return true;
        } catch (IOException | RuntimeException e) {
            return false;
        }
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private static boolean containsIgnoreCase(List<String> values, String expected) {
        if (values == null || expected == null) return false;
        for (String value : values) {
            if (expected.equalsIgnoreCase(value)) return true;
        }
        return false;
    }
}
