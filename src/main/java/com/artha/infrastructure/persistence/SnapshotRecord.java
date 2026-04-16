package com.artha.infrastructure.persistence;

import jakarta.persistence.*;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "aggregate_snapshots")
@IdClass(SnapshotRecord.SnapshotId.class)
public class SnapshotRecord {

    @Id
    @Column(name = "aggregate_id")
    private UUID aggregateId;

    @Id
    @Column(name = "version")
    private long version;

    @Column(name = "aggregate_type", nullable = false)
    private String aggregateType;

    @Column(name = "data", nullable = false)
    private byte[] data;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected SnapshotRecord() {}

    public SnapshotRecord(UUID aggregateId, String aggregateType, long version, byte[] data) {
        this.aggregateId = aggregateId;
        this.aggregateType = aggregateType;
        this.version = version;
        this.data = data;
        this.createdAt = Instant.now();
    }

    public UUID getAggregateId() { return aggregateId; }
    public String getAggregateType() { return aggregateType; }
    public long getVersion() { return version; }
    public byte[] getData() { return data; }
    public Instant getCreatedAt() { return createdAt; }

    public static class SnapshotId implements Serializable {
        private UUID aggregateId;
        private long version;

        public SnapshotId() {}
        public SnapshotId(UUID aggregateId, long version) {
            this.aggregateId = aggregateId;
            this.version = version;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof SnapshotId that)) return false;
            return version == that.version && Objects.equals(aggregateId, that.aggregateId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(aggregateId, version);
        }
    }
}
