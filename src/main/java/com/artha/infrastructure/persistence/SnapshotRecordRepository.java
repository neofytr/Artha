package com.artha.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface SnapshotRecordRepository extends JpaRepository<SnapshotRecord, SnapshotRecord.SnapshotId> {

    @Query("SELECT s FROM SnapshotRecord s WHERE s.aggregateId = :aggregateId ORDER BY s.version DESC LIMIT 1")
    Optional<SnapshotRecord> findLatest(@Param("aggregateId") UUID aggregateId);
}
