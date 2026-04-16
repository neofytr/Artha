package com.artha.core.event;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The event store is the single source of truth in an event-sourced system.
 * It persists domain events and supports replaying them to reconstruct aggregate state.
 */
public interface EventStore {

    /**
     * Appends events atomically. Uses optimistic concurrency control:
     * if expectedVersion doesn't match the current version, throws ConcurrencyException.
     */
    void appendEvents(UUID aggregateId, String aggregateType, List<DomainEvent> events, long expectedVersion);

    /**
     * Loads all events for an aggregate, starting after the given sequence number.
     * Pass 0 to load from the beginning.
     */
    List<DomainEvent> loadEvents(UUID aggregateId, long afterSequenceNumber);

    /**
     * Loads events between two sequence numbers (inclusive).
     * Used for partial replay from a snapshot.
     */
    List<DomainEvent> loadEvents(UUID aggregateId, long fromSequence, long toSequence);

    /**
     * Returns the latest version (highest sequence number) for an aggregate.
     */
    long getCurrentVersion(UUID aggregateId);

    /**
     * Stores a serialized snapshot of aggregate state at a given version.
     */
    void saveSnapshot(UUID aggregateId, String aggregateType, long version, byte[] snapshotData);

    /**
     * Loads the most recent snapshot for an aggregate.
     */
    Optional<Snapshot> loadLatestSnapshot(UUID aggregateId);

    record Snapshot(UUID aggregateId, String aggregateType, long version, byte[] data) {}
}
