package com.artha.core.saga;

/**
 * Terminal and non-terminal states of a saga instance.
 * <p>
 * Persisted to a state store so orchestrators can resume sagas after crashes —
 * this is what makes sagas durable and the system recoverable.
 */
public enum SagaState {
    /** Saga has been started; next step has not yet executed. */
    STARTED,
    /** A step is currently executing. */
    RUNNING,
    /** A step failed; compensating previously-executed steps in reverse order. */
    COMPENSATING,
    /** All steps succeeded. Terminal. */
    COMPLETED,
    /** A step failed and all compensations succeeded. Terminal. */
    FAILED,
    /**
     * A compensation itself failed. Requires manual intervention.
     * Terminal only in the sense that the orchestrator won't retry automatically.
     */
    STUCK
}
