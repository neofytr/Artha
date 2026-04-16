package com.artha.infrastructure.messaging;

import com.artha.core.event.DomainEvent;

/**
 * Abstracts how domain events leave the write model.
 * <p>
 * The production implementation uses the <b>transactional outbox</b> pattern:
 * events are written to an outbox table in the same database transaction as
 * the events themselves, then a separate relay process (polling or CDC) ships
 * them to Kafka. This gives us at-least-once delivery without a distributed
 * transaction between DB and Kafka.
 */
public interface EventPublisher {
    void publish(DomainEvent event, String aggregateType);
}
