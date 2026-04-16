package com.artha.core.saga;

/**
 * A single step in a saga.
 * <p>
 * Sagas model long-running business transactions as a sequence of local
 * transactions, each with a compensating action that semantically "undoes"
 * it. When any step fails, already-executed steps are compensated in reverse
 * order. This gives us eventual consistency across services without XA/2PC.
 *
 * @param <C> saga context type (threaded through all steps)
 */
public interface SagaStep<C> {

    /** Human-readable name, used for logs and state persistence. */
    String name();

    /**
     * Forward action: perform the real work. May throw to trigger compensation.
     * Must be idempotent (same input → same effect when replayed).
     */
    void execute(C context);

    /**
     * Undo the effect of {@link #execute(Object)} if later steps fail.
     * Must also be idempotent and should NOT throw under normal conditions —
     * a failing compensation puts the saga into a stuck state that needs
     * manual reconciliation.
     */
    void compensate(C context);
}
