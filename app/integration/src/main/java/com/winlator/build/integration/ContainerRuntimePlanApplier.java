package com.winlator.build.integration;

import com.winlator.build.engine.runtime.RuntimePlan;
import com.winlator.build.engine.runtime.RuntimePlanApplier;
import com.winlator.container.Container;

public final class ContainerRuntimePlanApplier implements RuntimePlanApplier {
    private final Container container;
    private final boolean resetRuntimeConfigs;

    public ContainerRuntimePlanApplier(Container container, boolean resetRuntimeConfigs) {
        if (container == null) throw new IllegalArgumentException("container is required");
        this.container = container;
        this.resetRuntimeConfigs = resetRuntimeConfigs;
    }

    @Override
    public Transaction apply(RuntimePlan plan) {
        final ContainerRuntimeAdapter.ApplyResult result =
                ContainerRuntimeAdapter.applyPlan(container, plan, resetRuntimeConfigs, false);

        if (!result.isSuccess()) {
            return new Transaction() {
                public boolean isApplied() { return false; }
                public String getError() { return result.getError(); }
                public void commit() {}
                public void rollback() {}
            };
        }

        return new Transaction() {
            private boolean commitAttempted;
            private boolean committed;
            private boolean rolledBack;

            public boolean isApplied() { return !rolledBack; }
            public String getError() { return ""; }

            public void commit() {
                if (rolledBack) throw new IllegalStateException("cannot commit a rolled back runtime plan");
                if (committed) return;
                commitAttempted = true;
                container.saveData();
                committed = true;
            }

            public void rollback() {
                if (rolledBack) return;
                ContainerRuntimeAdapter.rollback(container, result.getPreviousState(), commitAttempted);
                rolledBack = true;
            }
        };
    }
}
