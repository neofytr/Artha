package com.artha.core.aggregate;

import com.artha.core.event.DomainEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Base class for all event-sourced aggregate roots.
 * <p>
 * Aggregates raise domain events in response to commands. The events are
 * both the source of truth for rebuilding state (via {@link #replay}) and the
 * facts published to the rest of the system when the aggregate is persisted.
 * <p>
 * Subclasses must implement {@link #apply(DomainEvent)} to mutate internal
 * state from an event. New events should be produced via {@link #raise}.
 */
public abstract class AggregateRoot {

    private UUID id;
    private long version = 0;
    private final List<DomainEvent> uncommittedEvents = new ArrayList<>();

    protected AggregateRoot() {}

    protected AggregateRoot(UUID id) {
        this.id = id;
    }

    public UUID getId() { return id; }
    public long getVersion() { return version; }

    protected void setId(UUID id) { this.id = id; }

    /**
     * Raise a new event: adds it to the uncommitted list and applies it to state.
     * The next sequence number is determined by current version + uncommitted count.
     */
    protected void raise(DomainEvent event) {
        apply(event);
        uncommittedEvents.add(event);
    }

    /**
     * Replay historical events to rebuild state. Used when loading an aggregate.
     * This does NOT add to uncommittedEvents — these events are already persisted.
     */
    public void replay(List<DomainEvent> history) {
        for (DomainEvent event : history) {
            apply(event);
            this.version = event.getSequenceNumber();
        }
    }

    /**
     * Hook for subclasses: mutate state from an event.
     * MUST be deterministic and side-effect free.
     */
    protected abstract void apply(DomainEvent event);

    public List<DomainEvent> getUncommittedEvents() {
        return Collections.unmodifiableList(uncommittedEvents);
    }

    public void markEventsCommitted() {
        this.version += uncommittedEvents.size();
        uncommittedEvents.clear();
    }

    /**
     * Compute the next sequence number for a new event being raised.
     */
    protected long nextSequence() {
        return version + uncommittedEvents.size() + 1;
    }
}
