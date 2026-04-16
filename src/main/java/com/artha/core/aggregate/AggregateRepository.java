package com.artha.core.aggregate;

import com.artha.core.event.ConcurrencyException;
import com.artha.core.event.DomainEvent;
import com.artha.core.event.EventStore;
import com.artha.infrastructure.messaging.EventPublisher;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Generic repository for event-sourced aggregates.
 * Handles loading via event replay (with optional snapshot optimization) and
 * saving via appending new events to the event store, then publishing those
 * events to the outbound message bus.
 */
public class AggregateRepository<T extends AggregateRoot> {

    private final EventStore eventStore;
    private final EventPublisher eventPublisher;
    private final Supplier<T> factory;
    private final String aggregateType;
    private final SnapshotStrategy snapshotStrategy;

    public AggregateRepository(EventStore eventStore,
                               EventPublisher eventPublisher,
                               Supplier<T> factory,
                               Class<T> aggregateClass,
                               SnapshotStrategy snapshotStrategy) {
        this.eventStore = eventStore;
        this.eventPublisher = eventPublisher;
        this.factory = factory;
        this.aggregateType = aggregateClass.getSimpleName();
        this.snapshotStrategy = snapshotStrategy;
    }

    /**
     * Load an aggregate by replaying its events (optionally bootstrapped from a snapshot).
     */
    public Optional<T> load(UUID aggregateId) {
        T aggregate = factory.get();
        long fromSequence = 0;

        // Try to bootstrap from snapshot first
        Optional<EventStore.Snapshot> snapshot = eventStore.loadLatestSnapshot(aggregateId);
        if (snapshot.isPresent() && aggregate instanceof Snapshotable<?> snapshotable) {
            @SuppressWarnings("unchecked")
            Snapshotable<Object> typedSnap = (Snapshotable<Object>) snapshotable;
            Object state = snapshotStrategy.deserialize(snapshot.get().data(), typedSnap.snapshotType());
            typedSnap.restoreFromSnapshot(state);
            aggregate.replay(List.of()); // sets version to snapshot version
            // we need to set version manually since replay from empty list won't
            fromSequence = snapshot.get().version();
            setVersion(aggregate, fromSequence);
        }

        List<DomainEvent> events = eventStore.loadEvents(aggregateId, fromSequence);
        if (events.isEmpty() && fromSequence == 0) {
            return Optional.empty();
        }
        aggregate.setId(aggregateId);
        aggregate.replay(events);
        return Optional.of(aggregate);
    }

    /**
     * Save an aggregate: append uncommitted events with optimistic locking,
     * then publish them to the message bus.
     * @throws ConcurrencyException if another writer beat us to it
     */
    public void save(T aggregate) {
        List<DomainEvent> newEvents = aggregate.getUncommittedEvents();
        if (newEvents.isEmpty()) return;

        long expectedVersion = aggregate.getVersion();
        eventStore.appendEvents(aggregate.getId(), aggregateType, newEvents, expectedVersion);

        // Publish events to the outbound bus AFTER they are persisted.
        // The publisher writes to an outbox table in the same transaction as the event store,
        // and a separate process relays them to Kafka — this gives us exactly-once semantics.
        for (DomainEvent event : newEvents) {
            eventPublisher.publish(event, aggregateType);
        }

        aggregate.markEventsCommitted();

        // Snapshot if strategy says so
        if (aggregate instanceof Snapshotable<?> snapshotable
                && snapshotStrategy.shouldSnapshot(aggregate.getVersion())) {
            byte[] data = snapshotStrategy.serialize(snapshotable.captureSnapshot());
            eventStore.saveSnapshot(aggregate.getId(), aggregateType, aggregate.getVersion(), data);
        }
    }

    // Reflection-free way to set version after snapshot hydration.
    // We deliberately keep AggregateRoot's version setter internal; this helper
    // lives in the same "core" module so it's the only external escape hatch.
    private void setVersion(T aggregate, long version) {
        try {
            var field = AggregateRoot.class.getDeclaredField("version");
            field.setAccessible(true);
            field.setLong(aggregate, version);
        } catch (Exception e) {
            throw new IllegalStateException("Could not set version on aggregate", e);
        }
    }
}
