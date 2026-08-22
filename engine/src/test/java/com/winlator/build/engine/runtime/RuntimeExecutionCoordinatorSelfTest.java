package com.winlator.build.engine.runtime;

import com.winlator.build.engine.components.ComponentRegistry;
import com.winlator.build.engine.hardware.HardwareCapabilities;

import java.util.Arrays;

public final class RuntimeExecutionCoordinatorSelfTest {
    private RuntimeExecutionCoordinatorSelfTest() {}

    public static void runAll() {
        testSuccessfulPreparationCommitsOnce();
        testReadinessFailureDoesNotApply();
        testInvalidPlanDoesNotApply();
        testCommitFailureRollsBack();
        testRollbackFailureIsSurfaced();
        testInvalidManagerStateDoesNotApply();
    }

    private static void testSuccessfulPreparationCommitsOnce() {
        RuntimeManager manager = new RuntimeManager();
        RuntimeExecutionCoordinator coordinator = new RuntimeExecutionCoordinator(manager);
        RecordingApplier applier = new RecordingApplier(false, false);

        RuntimeExecutionCoordinator.Result result = coordinator.prepare(
                maliVulkan11(), LaunchRequirements.defaults(),
                RuntimeComponentCatalog.createPinnedRegistry(),
                new CompleteInventory(), applier);

        assertEquals(RuntimeExecutionCoordinator.Status.READY, result.getStatus(), "successful coordinator status");
        assertEquals(RuntimeManager.State.READY, result.getRuntimeSnapshot().getState(), "runtime manager ready state");
        assertEquals(1, applier.applyCount, "apply count");
        assertEquals(1, applier.commitCount, "commit count");
        assertEquals(0, applier.rollbackCount, "rollback count");
        assertFalse(result.wasRolledBack(), "successful preparation must not rollback");
    }

    private static void testReadinessFailureDoesNotApply() {
        RuntimeManager manager = new RuntimeManager();
        RuntimeExecutionCoordinator coordinator = new RuntimeExecutionCoordinator(manager);
        RecordingApplier applier = new RecordingApplier(false, false);

        RuntimeExecutionCoordinator.Result result = coordinator.prepare(
                maliVulkan11(), LaunchRequirements.defaults(),
                RuntimeComponentCatalog.createPinnedRegistry(),
                new RuntimeInventory() {
                    public boolean isRuntimeBaseReady() { return false; }
                    public boolean isComponentAvailable(ComponentRegistry.Component component) { return true; }
                    public String explainUnavailable(ComponentRegistry.Component component) { return ""; }
                }, applier);

        assertEquals(RuntimeExecutionCoordinator.Status.NOT_READY, result.getStatus(), "readiness failure status");
        assertEquals(0, applier.applyCount, "readiness failure must not apply");
        assertEquals(RuntimeManager.State.IDLE, manager.snapshot().getState(), "readiness failure leaves manager idle");
    }

    private static void testInvalidPlanDoesNotApply() {
        RuntimeManager manager = new RuntimeManager();
        RuntimeExecutionCoordinator coordinator = new RuntimeExecutionCoordinator(manager);
        RecordingApplier applier = new RecordingApplier(false, false);

        RuntimeExecutionCoordinator.Result result = coordinator.prepare(
                noVulkan(), LaunchRequirements.forGraphicsApi(LaunchRequirements.GraphicsApi.DIRECTX_12),
                RuntimeComponentCatalog.createPinnedRegistry(), new CompleteInventory(), applier);

        assertEquals(RuntimeExecutionCoordinator.Status.PLAN_INVALID, result.getStatus(), "invalid plan status");
        assertEquals(0, applier.applyCount, "invalid plan must not apply");
        assertEquals(RuntimeManager.State.IDLE, manager.snapshot().getState(), "invalid plan leaves manager idle");
    }

    private static void testCommitFailureRollsBack() {
        RuntimeManager manager = new RuntimeManager();
        RuntimeExecutionCoordinator coordinator = new RuntimeExecutionCoordinator(manager);
        RecordingApplier applier = new RecordingApplier(true, false);

        RuntimeExecutionCoordinator.Result result = coordinator.prepare(
                maliVulkan11(), LaunchRequirements.defaults(),
                RuntimeComponentCatalog.createPinnedRegistry(), new CompleteInventory(), applier);

        assertEquals(RuntimeExecutionCoordinator.Status.APPLY_FAILED, result.getStatus(), "commit failure status");
        assertEquals(1, applier.applyCount, "commit failure apply count");
        assertEquals(1, applier.commitCount, "commit failure commit count");
        assertEquals(1, applier.rollbackCount, "commit failure rollback count");
        assertTrue(result.wasRolledBack(), "commit failure must rollback");
        assertEquals(RuntimeManager.State.FAILED, manager.snapshot().getState(), "commit failure marks runtime failed");
    }

    private static void testRollbackFailureIsSurfaced() {
        RuntimeManager manager = new RuntimeManager();
        RuntimeExecutionCoordinator coordinator = new RuntimeExecutionCoordinator(manager);
        RecordingApplier applier = new RecordingApplier(true, true);

        RuntimeExecutionCoordinator.Result result = coordinator.prepare(
                maliVulkan11(), LaunchRequirements.defaults(),
                RuntimeComponentCatalog.createPinnedRegistry(), new CompleteInventory(), applier);

        assertEquals(RuntimeExecutionCoordinator.Status.ROLLBACK_FAILED, result.getStatus(), "rollback failure status");
        assertFalse(result.wasRolledBack(), "rollback failure must not report successful rollback");
        assertTrue(result.getError().contains("rollback failed"), "rollback failure diagnostic");
    }

    private static void testInvalidManagerStateDoesNotApply() {
        RuntimeManager manager = new RuntimeManager();
        RuntimePlan plan = new RuntimePlanner().plan(
                maliVulkan11(), LaunchRequirements.defaults(), RuntimeComponentCatalog.createPinnedRegistry());
        manager.prepare(plan);

        RuntimeExecutionCoordinator coordinator = new RuntimeExecutionCoordinator(manager);
        RecordingApplier applier = new RecordingApplier(false, false);
        RuntimeExecutionCoordinator.Result result = coordinator.prepare(
                maliVulkan11(), LaunchRequirements.defaults(),
                RuntimeComponentCatalog.createPinnedRegistry(), new CompleteInventory(), applier);

        assertEquals(RuntimeExecutionCoordinator.Status.INVALID_STATE, result.getStatus(), "invalid manager state status");
        assertEquals(0, applier.applyCount, "invalid manager state must not apply");
    }

    private static HardwareCapabilities maliVulkan11() {
        return new HardwareCapabilities(36, Arrays.asList("arm64-v8a", "armeabi-v7a"),
                4L * 1024 * 1024 * 1024, "Mali-G52 MC2", "ARM", "OpenGL ES 3.2", 1, 1, 0);
    }

    private static HardwareCapabilities noVulkan() {
        return new HardwareCapabilities(36, Arrays.asList("arm64-v8a"),
                4L * 1024 * 1024 * 1024, "PowerVR GE8320", "Imagination", "OpenGL ES 3.2", 0, 0, 0);
    }

    private static final class CompleteInventory implements RuntimeInventory {
        public boolean isRuntimeBaseReady() { return true; }
        public boolean isComponentAvailable(ComponentRegistry.Component component) { return true; }
        public String explainUnavailable(ComponentRegistry.Component component) { return ""; }
    }

    private static final class RecordingApplier implements RuntimePlanApplier {
        private final boolean failCommit;
        private final boolean failRollback;
        int applyCount;
        int commitCount;
        int rollbackCount;

        RecordingApplier(boolean failCommit, boolean failRollback) {
            this.failCommit = failCommit;
            this.failRollback = failRollback;
        }

        public Transaction apply(RuntimePlan plan) {
            applyCount++;
            return new Transaction() {
                public boolean isApplied() { return true; }
                public String getError() { return ""; }
                public void commit() {
                    commitCount++;
                    if (failCommit) throw new IllegalStateException("simulated commit failure");
                }
                public void rollback() {
                    rollbackCount++;
                    if (failRollback) throw new IllegalStateException("simulated rollback failure");
                }
            };
        }
    }

    private static void assertTrue(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static void assertFalse(boolean condition, String message) {
        assertTrue(!condition, message);
    }

    private static void assertEquals(Object expected, Object actual, String message) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError(message + ": expected=" + expected + " actual=" + actual);
        }
    }
}
