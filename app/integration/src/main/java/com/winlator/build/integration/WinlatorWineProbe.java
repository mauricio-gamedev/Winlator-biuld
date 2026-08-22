package com.winlator.build.integration;

import android.content.Context;

import com.winlator.build.engine.runtime.Box64Inspector;
import com.winlator.build.engine.runtime.RuntimeBaseInspector;
import com.winlator.build.engine.runtime.WineProbe;
import com.winlator.build.engine.runtime.WineSpec;
import com.winlator.xenvironment.RootFS;

import java.io.File;

public final class WinlatorWineProbe implements WineProbe {
    private final Context context;
    private final File rootDir;

    public WinlatorWineProbe(Context context) {
        if (context == null) throw new IllegalArgumentException("context is required");
        Context appContext = context.getApplicationContext();
        this.context = appContext != null ? appContext : context;
        this.rootDir = RootFS.find(this.context).getRootDir();
    }

    @Override
    public boolean isRootFsLaunchReady() {
        return new RuntimeBaseInspector().inspect(new WinlatorRuntimeBaseProbe(context)).isLaunchReady();
    }

    @Override
    public boolean isBox64LaunchReady() {
        return new Box64Inspector().inspect(new WinlatorBox64Probe(context)).isLaunchReady();
    }

    @Override
    public boolean isMainWineDirectoryPresent() {
        return new File(rootDir, WineSpec.ROOT_RELATIVE_PATH).isDirectory();
    }

    @Override
    public boolean isWineBinaryPresent() {
        return wine().isFile();
    }

    @Override
    public boolean isWineBinaryRunnable() {
        File file = wine();
        return file.isFile() && file.canExecute();
    }

    @Override
    public boolean isWineServerPresent() {
        return wineServer().isFile();
    }

    @Override
    public boolean isWineServerRunnable() {
        File file = wineServer();
        return file.isFile() && file.canExecute();
    }

    private File wine() { return new File(rootDir, WineSpec.WINE_RELATIVE_PATH); }
    private File wineServer() { return new File(rootDir, WineSpec.WINESERVER_RELATIVE_PATH); }
}
