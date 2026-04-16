package com.artha.infrastructure.persistence;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;

import jakarta.persistence.QueryHint;

import java.util.List;

public interface OutboxRecordRepository extends JpaRepository<OutboxRecord, Long> {

    /**
     * Pull a batch of pending outbox rows for publishing. We use SKIP LOCKED so
     * multiple relay workers can consume in parallel without blocking each other.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints({@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2")}) // SKIP LOCKED on PG
    @Query(value = """
            SELECT * FROM event_outbox
            WHERE status = 'PENDING'
            ORDER BY outbox_id
            LIMIT :limit
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<OutboxRecord> lockPendingBatch(int limit);
}
