package com.artha.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface EventRecordRepository extends JpaRepository<EventRecord, UUID> {

    @Query("SELECT e FROM EventRecord e WHERE e.aggregateId = :aggregateId AND e.sequenceNumber > :after ORDER BY e.sequenceNumber ASC")
    List<EventRecord> findForAggregateAfter(@Param("aggregateId") UUID aggregateId, @Param("after") long after);

    @Query("SELECT e FROM EventRecord e WHERE e.aggregateId = :aggregateId AND e.sequenceNumber BETWEEN :from AND :to ORDER BY e.sequenceNumber ASC")
    List<EventRecord> findForAggregateBetween(@Param("aggregateId") UUID aggregateId,
                                              @Param("from") long from, @Param("to") long to);

    @Query("SELECT COALESCE(MAX(e.sequenceNumber), 0) FROM EventRecord e WHERE e.aggregateId = :aggregateId")
    long findMaxSequenceNumber(@Param("aggregateId") UUID aggregateId);
}
