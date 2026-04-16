package com.artha.infrastructure.persistence;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "saga_instances")
public class SagaInstanceRecord {

    @Id
    @Column(name = "saga_id")
    private UUID sagaId;

    @Column(name = "definition_name", nullable = false)
    private String definitionName;

    @Column(name = "state", nullable = false)
    private String state;

    @Column(name = "completed_step_index", nullable = false)
    private int completedStepIndex;

    @Column(name = "context", nullable = false, columnDefinition = "TEXT")
    private String contextJson;

    @Column(name = "last_error")
    private String lastError;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected SagaInstanceRecord() {}

    public SagaInstanceRecord(UUID sagaId, String definitionName, String state, int completedStepIndex,
                              String contextJson, String lastError, Instant createdAt, Instant updatedAt) {
        this.sagaId = sagaId;
        this.definitionName = definitionName;
        this.state = state;
        this.completedStepIndex = completedStepIndex;
        this.contextJson = contextJson;
        this.lastError = lastError;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public UUID getSagaId() { return sagaId; }
    public String getDefinitionName() { return definitionName; }
    public String getState() { return state; }
    public int getCompletedStepIndex() { return completedStepIndex; }
    public String getContextJson() { return contextJson; }
    public String getLastError() { return lastError; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void setState(String state) { this.state = state; }
    public void setCompletedStepIndex(int i) { this.completedStepIndex = i; }
    public void setContextJson(String c) { this.contextJson = c; }
    public void setLastError(String e) { this.lastError = e; }
    public void setUpdatedAt(Instant t) { this.updatedAt = t; }
}
