package com.artha.infrastructure.persistence;

import com.artha.core.event.ConcurrencyException;
import com.artha.core.event.DomainEvent;
import com.artha.core.event.EventStore;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * PostgreSQL-backed event store.
 * <p>
 * Optimistic concurrency is enforced by a UNIQUE constraint on
 * (aggregate_id, sequence_number). Two writers racing to write version N+1
 * will both try the same key; the second INSERT fails and we translate it
 * into a {@link ConcurrencyException} that callers typically retry.
 */
@Component
public class JpaEventStore implements EventStore {

    private final EventRecordRepository eventRepository;
    private final SnapshotRecordRepository snapshotRepository;
    private final EventSerializer serializer;

    public JpaEventStore(EventRecordRepository eventRepository,
                         SnapshotRecordRepository snapshotRepository,
                         EventSerializer serializer) {
        this.eventRepository = eventRepository;
        this.snapshotRepository = snapshotRepository;
        this.serializer = serializer;
    }

    @Override
    @Transactional // creates a tx if none, joins existing — outbox publisher also joins
    public void appendEvents(UUID aggregateId, String aggregateType,
                             List<DomainEvent> events, long expectedVersion) {
        if (events.isEmpty()) return;

        long currentVersion = eventRepository.findMaxSequenceNumber(aggregateId);
        if (currentVersion != expectedVersion) {
            throw new ConcurrencyException(aggregateId, expectedVersion, currentVersion);
        }

        try {
            for (DomainEvent e : events) {
                EventRecord record = new EventRecord(
                        e.getEventId(), e.getAggregateId(), aggregateType, e.getSequenceNumber(),
                        e.getEventType(), serializer.serialize(e), e.getOccurredAt()
                );
                eventRepository.save(record);
            }
            eventRepository.flush(); // surface unique-constraint violations now, not on tx commit
        } catch (DataIntegrityViolationException dive) {
            long actual = eventRepository.findMaxSequenceNumber(aggregateId);
            throw new ConcurrencyException(aggregateId, expectedVersion, actual);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<DomainEvent> loadEvents(UUID aggregateId, long afterSequenceNumber) {
        return eventRepository.findForAggregateAfter(aggregateId, afterSequenceNumber).stream()
                .map(r -> serializer.deserialize(r.getEventType(), r.getPayload()))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<DomainEvent> loadEvents(UUID aggregateId, long fromSequence, long toSequence) {
        return eventRepository.findForAggregateBetween(aggregateId, fromSequence, toSequence).stream()
                .map(r -> serializer.deserialize(r.getEventType(), r.getPayload()))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public long getCurrentVersion(UUID aggregateId) {
        return eventRepository.findMaxSequenceNumber(aggregateId);
    }

    @Override
    @Transactional
    public void saveSnapshot(UUID aggregateId, String aggregateType, long version, byte[] snapshotData) {
        snapshotRepository.save(new SnapshotRecord(aggregateId, aggregateType, version, snapshotData));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Snapshot> loadLatestSnapshot(UUID aggregateId) {
        return snapshotRepository.findLatest(aggregateId)
                .map(r -> new Snapshot(r.getAggregateId(), r.getAggregateType(), r.getVersion(), r.getData()));
    }
}
