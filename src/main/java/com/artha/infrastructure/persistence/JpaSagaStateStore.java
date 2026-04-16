package com.artha.infrastructure.persistence;

import com.artha.core.saga.SagaInstance;
import com.artha.core.saga.SagaState;
import com.artha.core.saga.SagaStateStore;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Postgres-backed saga state store.
 * <p>
 * Every state transition is persisted so a crashed orchestrator can be resumed
 * from the last known checkpoint. The context is JSON-serialized so we aren't
 * locked into the concrete type at compile time — different sagas can share the
 * same store.
 */
public class JpaSagaStateStore<C> implements SagaStateStore<C> {

    private final SagaInstanceRecordRepository repository;
    private final ObjectMapper mapper;
    private final Class<C> contextType;

    public JpaSagaStateStore(SagaInstanceRecordRepository repository,
                             ObjectMapper mapper,
                             Class<C> contextType) {
        this.repository = repository;
        this.mapper = mapper;
        this.contextType = contextType;
    }

    @Override
    public void save(SagaInstance<C> instance) {
        String contextJson;
        try {
            contextJson = mapper.writeValueAsString(instance.context());
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize saga context", e);
        }

        SagaInstanceRecord existing = repository.findById(instance.id()).orElse(null);
        if (existing == null) {
            SagaInstanceRecord record = new SagaInstanceRecord(
                    instance.id(), instance.definitionName(), instance.state().name(),
                    instance.completedStepIndex(), contextJson, instance.lastError(),
                    instance.updatedAt(), instance.updatedAt()
            );
            repository.save(record);
        } else {
            existing.setState(instance.state().name());
            existing.setCompletedStepIndex(instance.completedStepIndex());
            existing.setContextJson(contextJson);
            existing.setLastError(instance.lastError());
            existing.setUpdatedAt(Instant.now());
            repository.save(existing);
        }
    }

    @Override
    public Optional<SagaInstance<C>> findById(UUID sagaId) {
        return repository.findById(sagaId).map(this::toDomain);
    }

    private SagaInstance<C> toDomain(SagaInstanceRecord record) {
        try {
            C context = mapper.readValue(record.getContextJson(), contextType);
            SagaInstance<C> instance = new SagaInstance<>(record.getSagaId(), record.getDefinitionName(), context);
            instance.transitionTo(SagaState.valueOf(record.getState()));
            instance.markStepCompleted(record.getCompletedStepIndex());
            if (record.getLastError() != null) instance.recordError(record.getLastError());
            return instance;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to deserialize saga context", e);
        }
    }
}
