package com.artha.core.event;

import java.util.UUID;

public class ConcurrencyException extends RuntimeException {
    private final UUID aggregateId;
    private final long expectedVersion;
    private final long actualVersion;

    public ConcurrencyException(UUID aggregateId, long expectedVersion, long actualVersion) {
        super("Optimistic concurrency conflict on aggregate %s: expected version %d but found %d"
                .formatted(aggregateId, expectedVersion, actualVersion));
        this.aggregateId = aggregateId;
        this.expectedVersion = expectedVersion;
        this.actualVersion = actualVersion;
    }

    public UUID getAggregateId() { return aggregateId; }
    public long getExpectedVersion() { return expectedVersion; }
    public long getActualVersion() { return actualVersion; }
}
