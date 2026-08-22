package com.winlator.build.engine.runtime;

import com.winlator.build.engine.components.ComponentRegistry;
import com.winlator.build.engine.hardware.HardwareCapabilities;

public final class RuntimeExecutionCoordinator {
    public enum Status {
        READY,
        INVALID_STATE,
        PLAN_INVALID,
        NOT_READY,
        APPLY_FAILED,
        PREPARE_FAILED,
        ROLLBACK_FAILED
    }

    public static final class Result {
        private final Status status;
        private final RuntimePlan plan;
        private final RuntimeReadiness readiness;
        private final RuntimeManager.Snapshot runtimeSnapshot;
        private final String error;
        private final boolean rolledBack;

        private Result(Status status, RuntimePlan plan, RuntimeReadiness readiness,
                RuntimeManager.Snapshot runtimeSnapshot, String error, boolean rolledBack) {
            this.status = status;
            this.plan = plan;
            this.readiness = readiness;
            this.runtimeSnapshot = runtimeSnapshot;
            this.error = error == null ? "" : error;
            this.rolledBack = rolledBack;
        }

        public Status getStatus() { return status; }
        public RuntimePlan getPlan() { return plan; }
        public RuntimeReadiness getReadiness() { return readiness; }
        public RuntimeManager.Snapshot getRuntimeSnapshot() { return runtimeSnapshot; }
        public String getError() { return error; }
        public boolean wasRolledBack() { return rolledBack; }
        public boolean isReady() { return status == Status.READY; }
    }

    private final RuntimePlanner planner;
    private final RuntimeReadinessChecker readinessChecker;
    private final RuntimeManager runtimeManager;

    public RuntimeExecutionCoordinator(RuntimeManager runtimeManager) {
        this(new RuntimePlanner(), new RuntimeReadinessChecker(), runtimeManager);
    }

    public RuntimeExecutionCoordinator(RuntimePlanner planner,
            RuntimeReadinessChecker readinessChecker, RuntimeManager runtimeManager) {
        if (planner == null || readinessChecker == null || runtimeManager == null) {
            throw new IllegalArgumentException("planner, readinessChecker and runtimeManager are required");
        }
        this.planner = planner;
        this.readinessChecker = readinessChecker;
        this.runtimeManager = runtimeManager;
    }

    public synchronized Result prepare(HardwareCapabilities hardware,
            LaunchRequirements requirements, ComponentRegistry registry,
            RuntimeInventory inventory, RuntimePlanApplier applier) {
        if (hardware == null || registry == null || inventory == null || applier == null) {
            throw new IllegalArgumentException("hardware, registry, inventory and applier are required");
        }

        RuntimeManager.Snapshot initial = runtimeManager.snapshot();
        if (initial.getState() != RuntimeManager.State.IDLE
                && initial.getState() != RuntimeManager.State.STOPPED) {
            return result(Status.INVALID_STATE, null, null, initial,
                    "runtime manager must be IDLE or STOPPED before preparation", false);
        }

        RuntimePlan plan = planner.plan(hardware,
                requirements == null ? LaunchRequirements.defaults() : requirements, registry);
        if (!plan.isValid()) {
            return result(Status.PLAN_INVALID, plan, null, runtimeManager.snapshot(),
                    join(plan.getErrors()), false);
        }

        RuntimeReadiness readiness = readinessChecker.check(plan, inventory);
        if (!readiness.isReady()) {
            return result(Status.NOT_READY, plan, readiness, runtimeManager.snapshot(),
                    join(readiness.getIssues()), false);
        }

        RuntimePlanApplier.Transaction transaction;
        try {
            transaction = applier.apply(plan);
        } catch (RuntimeException e) {
            return result(Status.APPLY_FAILED, plan, readiness, runtimeManager.snapshot(),
                    messageOf(e), false);
        }

        if (transaction == null || !transaction.isApplied()) {
            String error = transaction == null ? "runtime plan applier returned no transaction"
                    : transaction.getError();
            return result(Status.APPLY_FAILED, plan, readiness, runtimeManager.snapshot(),
                    emptyToDefault(error, "runtime plan could not be applied"), false);
        }

        try {
            RuntimeManager.Snapshot prepared = runtimeManager.prepare(plan);
            if (prepared.getState() != RuntimeManager.State.READY) {
                boolean rolledBack = safeRollback(transaction);
                return result(rolledBack ? Status.PREPARE_FAILED : Status.ROLLBACK_FAILED,
                        plan, readiness, prepared,
                        appendRollbackFailure(emptyToDefault(prepared.getError(),
                                "runtime manager did not reach READY"), rolledBack), rolledBack);
            }

            try {
                transaction.commit();
            } catch (RuntimeException e) {
                boolean rolledBack = safeRollback(transaction);
                try {
                    runtimeManager.fail("runtime plan commit failed: " + messageOf(e));
                } catch (RuntimeException ignored) {}
                return result(rolledBack ? Status.APPLY_FAILED : Status.ROLLBACK_FAILED,
                        plan, readiness, runtimeManager.snapshot(),
                        appendRollbackFailure("runtime plan commit failed: " + messageOf(e), rolledBack),
                        rolledBack);
            }

            return result(Status.READY, plan, readiness, runtimeManager.snapshot(), "", false);
        } catch (RuntimeException e) {
            boolean rolledBack = safeRollback(transaction);
            return result(rolledBack ? Status.PREPARE_FAILED : Status.ROLLBACK_FAILED,
                    plan, readiness, runtimeManager.snapshot(),
                    appendRollbackFailure(messageOf(e), rolledBack), rolledBack);
        }
    }

    private static boolean safeRollback(RuntimePlanApplier.Transaction transaction) {
        try {
            transaction.rollback();
            return true;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private static String appendRollbackFailure(String error, boolean rolledBack) {
        return rolledBack ? error : emptyToDefault(error, "runtime operation failed") + "; rollback failed";
    }

    private static Result result(Status status, RuntimePlan plan, RuntimeReadiness readiness,
            RuntimeManager.Snapshot snapshot, String error, boolean rolledBack) {
        return new Result(status, plan, readiness, snapshot, error, rolledBack);
    }

    private static String join(Iterable<String> values) {
        StringBuilder builder = new StringBuilder();
        if (values != null) {
            for (String value : values) {
                if (value == null || value.isEmpty()) continue;
                if (builder.length() > 0) builder.append("; ");
                builder.append(value);
            }
        }
        return builder.toString();
    }

    private static String messageOf(RuntimeException e) {
        String message = e.getMessage();
        return message == null || message.isEmpty() ? e.getClass().getSimpleName() : message;
    }

    private static String emptyToDefault(String value, String fallback) {
        return value == null || value.isEmpty() ? fallback : value;
    }
}
