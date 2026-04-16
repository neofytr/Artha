package com.artha.core.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Base class for all domain events in the event-sourced system.
 * Each event is immutable and uniquely identified.
 */
public abstract class DomainEvent {

    private final UUID eventId;
    private final UUID aggregateId;
    private final long sequenceNumber;
    private final Instant occurredAt;
    private final String eventType;

    protected DomainEvent(UUID aggregateId, long sequenceNumber) {
        this.eventId = UUID.randomUUID();
        this.aggregateId = aggregateId;
        this.sequenceNumber = sequenceNumber;
        this.occurredAt = Instant.now();
        this.eventType = this.getClass().getSimpleName();
    }

    protected DomainEvent(UUID eventId, UUID aggregateId, long sequenceNumber, Instant occurredAt) {
        this.eventId = eventId;
        this.aggregateId = aggregateId;
        this.sequenceNumber = sequenceNumber;
        this.occurredAt = occurredAt;
        this.eventType = this.getClass().getSimpleName();
    }

    public UUID getEventId() { return eventId; }
    public UUID getAggregateId() { return aggregateId; }
    public long getSequenceNumber() { return sequenceNumber; }
    public Instant getOccurredAt() { return occurredAt; }
    public String getEventType() { return eventType; }
}
