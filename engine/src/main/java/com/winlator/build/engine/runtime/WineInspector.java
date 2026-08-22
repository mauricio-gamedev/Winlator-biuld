package com.winlator.build.engine.runtime;

import java.util.ArrayList;
import java.util.List;

public final class WineInspector {
    public WineInspection inspect(WineProbe probe) {
        if (probe == null) throw new IllegalArgumentException("probe is required");

        boolean rootFsReady = probe.isRootFsLaunchReady();
        boolean box64Ready = probe.isBox64LaunchReady();
        boolean wineDir = probe.isMainWineDirectoryPresent();
        boolean winePresent = probe.isWineBinaryPresent();
        boolean wineRunnable = probe.isWineBinaryRunnable();
        boolean serverPresent = probe.isWineServerPresent();
        boolean serverRunnable = probe.isWineServerRunnable();

        List<String> issues = new ArrayList<>();
        if (!rootFsReady) issues.add("RootFS baseline is not launch-ready");
        if (!box64Ready) issues.add("Box64 baseline is not launch-ready");
        if (!wineDir) issues.add("Main Wine directory is missing: /" + WineSpec.ROOT_RELATIVE_PATH);
        if (!winePresent) issues.add("Wine binary is missing: /" + WineSpec.WINE_RELATIVE_PATH);
        else if (!wineRunnable) issues.add("Wine binary is not executable: /" + WineSpec.WINE_RELATIVE_PATH);
        if (!serverPresent) issues.add("Wine server binary is missing: /" + WineSpec.WINESERVER_RELATIVE_PATH);
        else if (!serverRunnable) issues.add("Wine server binary is not executable: /" + WineSpec.WINESERVER_RELATIVE_PATH);

        WineInspection.Status status;
        if (!wineDir || !winePresent || !serverPresent) status = WineInspection.Status.MISSING;
        else if (!issues.isEmpty()) status = WineInspection.Status.INCOMPLETE;
        else status = WineInspection.Status.CURRENT;

        return new WineInspection(status, rootFsReady, box64Ready, wineDir,
                winePresent, wineRunnable, serverPresent, serverRunnable, issues);
    }
}
