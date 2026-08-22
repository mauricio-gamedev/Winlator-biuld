package com.winlator.build.engine.runtime;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class WineInspection {
    public enum Status { MISSING, INCOMPLETE, CURRENT }

    private final Status status;
    private final boolean rootFsReady;
    private final boolean box64Ready;
    private final boolean wineDirPresent;
    private final boolean winePresent;
    private final boolean wineRunnable;
    private final boolean wineServerPresent;
    private final boolean wineServerRunnable;
    private final List<String> issues;

    WineInspection(Status status, boolean rootFsReady, boolean box64Ready,
            boolean wineDirPresent, boolean winePresent, boolean wineRunnable,
            boolean wineServerPresent, boolean wineServerRunnable, List<String> issues) {
        this.status = status;
        this.rootFsReady = rootFsReady;
        this.box64Ready = box64Ready;
        this.wineDirPresent = wineDirPresent;
        this.winePresent = winePresent;
        this.wineRunnable = wineRunnable;
        this.wineServerPresent = wineServerPresent;
        this.wineServerRunnable = wineServerRunnable;
        this.issues = Collections.unmodifiableList(new ArrayList<>(issues));
    }

    public Status getStatus() { return status; }
    public boolean isRootFsReady() { return rootFsReady; }
    public boolean isBox64Ready() { return box64Ready; }
    public boolean isWineDirPresent() { return wineDirPresent; }
    public boolean isWinePresent() { return winePresent; }
    public boolean isWineRunnable() { return wineRunnable; }
    public boolean isWineServerPresent() { return wineServerPresent; }
    public boolean isWineServerRunnable() { return wineServerRunnable; }
    public List<String> getIssues() { return issues; }
    public boolean isLaunchReady() { return status == Status.CURRENT && issues.isEmpty(); }
}
