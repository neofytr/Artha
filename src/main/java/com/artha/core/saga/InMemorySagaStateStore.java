package com.artha.core.saga;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory saga state store. Suitable for tests and for the write path of
 * a Postgres-backed store. For production we'd write through to Postgres in
 * the same transaction as the domain event store.
 */
public class InMemorySagaStateStore<C> implements SagaStateStore<C> {

    private final ConcurrentHashMap<UUID, SagaInstance<C>> store = new ConcurrentHashMap<>();

    @Override
    public void save(SagaInstance<C> instance) {
        store.put(instance.id(), instance);
    }

    @Override
    public Optional<SagaInstance<C>> findById(UUID sagaId) {
        return Optional.ofNullable(store.get(sagaId));
    }
}
