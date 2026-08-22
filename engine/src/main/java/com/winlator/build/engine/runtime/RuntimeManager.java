package com.winlator.build.engine.runtime;

import com.winlator.build.engine.components.ComponentRegistry;
import com.winlator.build.engine.graphics.RendererPolicy;
import com.winlator.build.engine.hardware.HardwareCapabilities;

public final class RuntimeManager {
    public enum State { IDLE, PREPARING, READY, RUNNING, STOPPED, FAILED }

    public static final class Snapshot {
        private final State state;
        private final RendererPolicy.Decision rendererDecision;
        private final int compatibleComponentCount;
        private final String error;

        private Snapshot(State state, RendererPolicy.Decision rendererDecision,
                int compatibleComponentCount, String error) {
            this.state = state;
            this.rendererDecision = rendererDecision;
            this.compatibleComponentCount = compatibleComponentCount;
            this.error = error;
        }

        public State getState() { return state; }
        public RendererPolicy.Decision getRendererDecision() { return rendererDecision; }
        public int getCompatibleComponentCount() { return compatibleComponentCount; }
        public String getError() { return error; }
    }

    private State state = State.IDLE;
    private RendererPolicy.Decision rendererDecision;
    private int compatibleComponentCount;
    private String error = "";

    public synchronized Snapshot prepare(HardwareCapabilities hardware,
            RendererPolicy rendererPolicy, ComponentRegistry componentRegistry) {
        requireState(State.IDLE, State.STOPPED);
        if (hardware == null || rendererPolicy == null || componentRegistry == null) {
            throw new IllegalArgumentException("hardware, rendererPolicy and componentRegistry are required");
        }

        state = State.PREPARING;
        error = "";
        try {
            rendererDecision = rendererPolicy.select(hardware);
            compatibleComponentCount = componentRegistry.compatible(hardware).size();
            state = State.READY;
        } catch (RuntimeException e) {
            state = State.FAILED;
            error = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            throw e;
        }
        return snapshot();
    }

    public synchronized Snapshot start() { requireState(State.READY); state = State.RUNNING; return snapshot(); }
    public synchronized Snapshot stop() { requireState(State.READY, State.RUNNING, State.FAILED); state = State.STOPPED; return snapshot(); }

    public synchronized Snapshot fail(String message) {
        if (state == State.IDLE || state == State.STOPPED) throw new IllegalStateException("cannot fail a runtime that has not been prepared");
        state = State.FAILED;
        error = message == null ? "unknown runtime failure" : message;
        return snapshot();
    }

    public synchronized Snapshot reset() {
        if (state == State.RUNNING || state == State.PREPARING) throw new IllegalStateException("stop the runtime before reset");
        state = State.IDLE;
        rendererDecision = null;
        compatibleComponentCount = 0;
        error = "";
        return snapshot();
    }

    public synchronized Snapshot snapshot() { return new Snapshot(state, rendererDecision, compatibleComponentCount, error); }

    private void requireState(State... allowed) {
        for (State allowedState : allowed) if (state == allowedState) return;
        throw new IllegalStateException("invalid runtime transition from " + state);
    }
}
