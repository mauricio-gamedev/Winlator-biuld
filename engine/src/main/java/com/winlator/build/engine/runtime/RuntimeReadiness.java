package com.winlator.build.engine.runtime;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class RuntimeReadiness {
    private final boolean runtimeBaseReady;
    private final List<String> missingComponentIds;
    private final List<String> issues;

    RuntimeReadiness(boolean runtimeBaseReady, List<String> missingComponentIds,
            List<String> issues) {
        this.runtimeBaseReady = runtimeBaseReady;
        this.missingComponentIds = Collections.unmodifiableList(new ArrayList<>(missingComponentIds));
        this.issues = Collections.unmodifiableList(new ArrayList<>(issues));
    }

    public boolean isRuntimeBaseReady() { return runtimeBaseReady; }
    public List<String> getMissingComponentIds() { return missingComponentIds; }
    public List<String> getIssues() { return issues; }
    public boolean isReady() {
        return runtimeBaseReady && missingComponentIds.isEmpty() && issues.isEmpty();
    }
}
