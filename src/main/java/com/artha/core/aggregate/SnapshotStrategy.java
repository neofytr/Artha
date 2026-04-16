package com.artha.core.aggregate;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Decides when to snapshot an aggregate and provides serialization.
 * <p>
 * Trade-off: snapshots cost write I/O but save read I/O later. For high-traffic
 * aggregates (hot accounts), snapshot often. For rarely-read aggregates, never.
 */
public class SnapshotStrategy {

    private final int everyNVersions;
    private final ObjectMapper mapper;

    public SnapshotStrategy(int everyNVersions, ObjectMapper mapper) {
        if (everyNVersions <= 0) throw new IllegalArgumentException("everyNVersions must be positive");
        this.everyNVersions = everyNVersions;
        this.mapper = mapper;
    }

    public boolean shouldSnapshot(long version) {
        return version > 0 && version % everyNVersions == 0;
    }

    public byte[] serialize(Object snapshot) {
        try {
            return mapper.writeValueAsBytes(snapshot);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Snapshot serialization failed", e);
        }
    }

    public <T> T deserialize(byte[] data, Class<T> type) {
        try {
            return mapper.readValue(data, type);
        } catch (Exception e) {
            throw new IllegalStateException("Snapshot deserialization failed", e);
        }
    }
}
