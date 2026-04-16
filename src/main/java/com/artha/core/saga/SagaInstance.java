package com.artha.core.saga;

import java.time.Instant;
import java.util.UUID;

/**
 * Runtime state of a single saga execution. Persisted so the orchestrator
 * can survive process crashes.
 */
public class SagaInstance<C> {

    private final UUID id;
    private final String definitionName;
    private final C context;
    private SagaState state;
    private int completedStepIndex;   // -1 means nothing done; index of last successfully executed step otherwise
    private String lastError;
    private Instant updatedAt;

    public SagaInstance(UUID id, String definitionName, C context) {
        this.id = id;
        this.definitionName = definitionName;
        this.context = context;
        this.state = SagaState.STARTED;
        this.completedStepIndex = -1;
        this.updatedAt = Instant.now();
    }

    public UUID id() { return id; }
    public String definitionName() { return definitionName; }
    public C context() { return context; }
    public SagaState state() { return state; }
    public int completedStepIndex() { return completedStepIndex; }
    public String lastError() { return lastError; }
    public Instant updatedAt() { return updatedAt; }

    public void transitionTo(SagaState newState) {
        this.state = newState;
        this.updatedAt = Instant.now();
    }

    public void markStepCompleted(int stepIndex) {
        this.completedStepIndex = stepIndex;
        this.updatedAt = Instant.now();
    }

    public void markStepRolledBack(int stepIndex) {
        this.completedStepIndex = stepIndex - 1;
        this.updatedAt = Instant.now();
    }

    public void recordError(String error) {
        this.lastError = error;
        this.updatedAt = Instant.now();
    }
}
