package com.artha.core.saga;

import com.artha.core.event.ConcurrencyException;
import com.artha.core.resilience.RetryPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.UUID;

/**
 * Executes a saga definition against a context.
 * <p>
 * Algorithm:
 * <pre>
 * for each step i in steps:
 *     try: step.execute(ctx); markStepCompleted(i)
 *     catch: transition -> COMPENSATING
 *            for each completed step j in reverse: step.compensate(ctx)
 *            transition -> FAILED (or STUCK if compensate threw)
 *            return
 * transition -> COMPLETED
 * </pre>
 * <p>
 * Steps that raise a retryable exception (e.g. {@link ConcurrencyException}) are
 * retried with exponential backoff before being considered failed. This helps
 * with transient optimistic-lock losers in event-sourced aggregates.
 */
public class SagaOrchestrator<C> {

    private static final Logger log = LoggerFactory.getLogger(SagaOrchestrator.class);

    private final SagaDefinition<C> definition;
    private final SagaStateStore<C> stateStore;
    private final RetryPolicy stepRetryPolicy;

    public SagaOrchestrator(SagaDefinition<C> definition,
                            SagaStateStore<C> stateStore,
                            RetryPolicy stepRetryPolicy) {
        this.definition = definition;
        this.stateStore = stateStore;
        this.stepRetryPolicy = stepRetryPolicy;
    }

    public SagaResult run(C context) {
        return run(UUID.randomUUID(), context);
    }

    public SagaResult run(UUID sagaId, C context) {
        SagaInstance<C> instance = new SagaInstance<>(sagaId, definition.name(), context);
        stateStore.save(instance);

        List<SagaStep<C>> steps = definition.steps();
        log.info("Starting saga {} ({}) with {} steps", definition.name(), sagaId, steps.size());

        for (int i = 0; i < steps.size(); i++) {
            SagaStep<C> step = steps.get(i);
            instance.transitionTo(SagaState.RUNNING);
            stateStore.save(instance);

            try {
                stepRetryPolicy.execute(() -> {
                    step.execute(context);
                    return null;
                });
                instance.markStepCompleted(i);
                stateStore.save(instance);
                log.info("Saga {} step {} ({}) OK", sagaId, i, step.name());
            } catch (RuntimeException e) {
                log.warn("Saga {} step {} ({}) failed: {}", sagaId, i, step.name(), e.getMessage());
                instance.recordError(e.getMessage());
                return rollback(instance, steps);
            }
        }

        instance.transitionTo(SagaState.COMPLETED);
        stateStore.save(instance);
        log.info("Saga {} COMPLETED", sagaId);
        return new SagaResult(sagaId, SagaState.COMPLETED, null);
    }

    private SagaResult rollback(SagaInstance<C> instance, List<SagaStep<C>> steps) {
        instance.transitionTo(SagaState.COMPENSATING);
        stateStore.save(instance);

        for (int j = instance.completedStepIndex(); j >= 0; j--) {
            SagaStep<C> step = steps.get(j);
            try {
                step.compensate(instance.context());
                instance.markStepRolledBack(j);
                stateStore.save(instance);
                log.info("Saga {} step {} ({}) compensated", instance.id(), j, step.name());
            } catch (RuntimeException e) {
                log.error("Saga {} compensation of step {} ({}) FAILED: {}", instance.id(), j, step.name(), e.getMessage());
                instance.recordError("Compensation failed at step " + j + ": " + e.getMessage());
                instance.transitionTo(SagaState.STUCK);
                stateStore.save(instance);
                return new SagaResult(instance.id(), SagaState.STUCK, instance.lastError());
            }
        }

        instance.transitionTo(SagaState.FAILED);
        stateStore.save(instance);
        return new SagaResult(instance.id(), SagaState.FAILED, instance.lastError());
    }

    public record SagaResult(UUID sagaId, SagaState state, String error) {}
}
