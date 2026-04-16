package com.artha.infrastructure.messaging;

import com.artha.application.query.AccountProjection;
import com.artha.core.event.DomainEvent;
import com.artha.infrastructure.persistence.EventSerializer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consumes account events from Kafka and feeds them to the read-model projection.
 * <p>
 * Running this through Kafka (rather than calling the projection inline during
 * command handling) gives us:
 * <ul>
 *     <li>Failure isolation: projection bugs don't roll back the write path.</li>
 *     <li>Replayability: we can rebuild the read model by resetting consumer offsets.</li>
 *     <li>Multiple consumers: other services can subscribe to the same topic.</li>
 * </ul>
 */
@Component
public class AccountEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(AccountEventConsumer.class);
    private static final int MAX_RETRIES = 3;

    private final AccountProjection projection;
    private final EventSerializer serializer;
    private final DeadLetterPublisher dlqPublisher;

    public AccountEventConsumer(AccountProjection projection, EventSerializer serializer,
                                DeadLetterPublisher dlqPublisher) {
        this.projection = projection;
        this.serializer = serializer;
        this.dlqPublisher = dlqPublisher;
    }

    @KafkaListener(topics = "artha.account.events", groupId = "artha.projection.account")
    public void consume(ConsumerRecord<String, String> record) {
        String eventType = header(record, "eventType");
        if (eventType == null) {
            // Without a type we can't do anything; dead-letter so it's auditable.
            dlqPublisher.sendToDlq(record, new IllegalStateException("missing eventType header"));
            return;
        }

        Exception lastError = null;
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                DomainEvent event = serializer.deserialize(eventType, record.value());
                projection.apply(event);
                return;  // success
            } catch (Exception e) {
                lastError = e;
                log.warn("Projection attempt {}/{} failed for offset {}: {}",
                        attempt, MAX_RETRIES, record.offset(), e.getMessage());
                if (attempt < MAX_RETRIES) {
                    try { Thread.sleep(100L * attempt); }
                    catch (InterruptedException ie) { Thread.currentThread().interrupt(); return; }
                }
            }
        }
        // Exhausted retries — ship to DLQ so the consumer group keeps moving.
        dlqPublisher.sendToDlq(record, lastError);
    }

    private static String header(ConsumerRecord<?, ?> record, String name) {
        var header = record.headers().lastHeader(name);
        return header == null ? null : new String(header.value());
    }
}
