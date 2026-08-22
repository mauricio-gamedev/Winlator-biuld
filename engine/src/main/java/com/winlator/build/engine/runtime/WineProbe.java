package com.winlator.build.engine.runtime;

public interface WineProbe {
    boolean isRootFsLaunchReady();
    boolean isBox64LaunchReady();
    boolean isMainWineDirectoryPresent();
    boolean isWineBinaryPresent();
    boolean isWineBinaryRunnable();
    boolean isWineServerPresent();
    boolean isWineServerRunnable();
}
