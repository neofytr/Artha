package com.artha.core.saga;

import java.util.Optional;
import java.util.UUID;

/**
 * Durable storage for saga instances. An orchestrator uses this to persist
 * state transitions, so it can resume from the last known point after a crash.
 */
public interface SagaStateStore<C> {
    void save(SagaInstance<C> instance);
    Optional<SagaInstance<C>> findById(UUID sagaId);
}
