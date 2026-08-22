package com.winlator.build.engine.runtime;

public interface RuntimePlanApplier {
    interface Transaction {
        boolean isApplied();
        String getError();
        void commit();
        void rollback();
    }

    Transaction apply(RuntimePlan plan);
}
