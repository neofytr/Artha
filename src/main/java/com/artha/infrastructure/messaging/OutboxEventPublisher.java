package com.artha.infrastructure.messaging;

import com.artha.core.event.DomainEvent;
import com.artha.infrastructure.persistence.EventSerializer;
import com.artha.infrastructure.persistence.OutboxRecord;
import com.artha.infrastructure.persistence.OutboxRecordRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Transactional outbox publisher.
 * <p>
 * Writes events into the {@code event_outbox} table in the <b>same database
 * transaction</b> as the event store append. A separate scheduled relay
 * ({@link OutboxRelay}) ships those rows to Kafka and marks them published.
 * This avoids the dual-write problem (DB + Kafka failing independently)
 * without needing XA.
 */
@Component
public class OutboxEventPublisher implements EventPublisher {

    private final OutboxRecordRepository outboxRepository;
    private final EventSerializer serializer;
    private final TopicResolver topicResolver;

    public OutboxEventPublisher(OutboxRecordRepository outboxRepository,
                                EventSerializer serializer,
                                TopicResolver topicResolver) {
        this.outboxRepository = outboxRepository;
        this.serializer = serializer;
        this.topicResolver = topicResolver;
    }

    @Override
    @Transactional  // joins the event-store's transaction — outbox write is atomic with the event append
    public void publish(DomainEvent event, String aggregateType) {
        String topic = topicResolver.topicFor(aggregateType);
        String partitionKey = event.getAggregateId().toString(); // same aggregate → same partition → in-order delivery
        OutboxRecord record = new OutboxRecord(
                event.getEventId(), event.getAggregateId(), aggregateType, event.getEventType(),
                serializer.serialize(event), event.getOccurredAt(), topic, partitionKey
        );
        outboxRepository.save(record);
    }
}
