package com.artha.infrastructure.messaging;

import com.artha.infrastructure.persistence.OutboxRecord;
import com.artha.infrastructure.persistence.OutboxRecordRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Polls the outbox table and publishes pending events to Kafka.
 * <p>
 * Uses a pessimistic {@code SELECT ... FOR UPDATE SKIP LOCKED} batch, so
 * multiple instances can run concurrently without stepping on each other.
 * Publishing is synchronous on the send() future: we only mark the row as
 * PUBLISHED after Kafka acks, so a crash mid-publish simply leaves the row
 * as PENDING and a later poll will retry.
 * <p>
 * This is at-least-once: duplicates can happen if the ack is lost. Downstream
 * consumers must be idempotent (keyed by {@code event_id}).
 */
@Component
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

    private final OutboxRecordRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final Counter publishedCounter;
    private final Counter failedCounter;
    private final int batchSize;

    public OutboxRelay(OutboxRecordRepository outboxRepository,
                       KafkaTemplate<String, String> kafkaTemplate,
                       MeterRegistry meterRegistry,
                       @Value("${artha.outbox.batch-size:100}") int batchSize) {
        this.outboxRepository = outboxRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.batchSize = batchSize;
        this.publishedCounter = Counter.builder("artha.outbox.published").register(meterRegistry);
        this.failedCounter = Counter.builder("artha.outbox.failed").register(meterRegistry);
    }

    @Scheduled(fixedDelayString = "${artha.outbox.poll-ms:500}")
    @Transactional
    public void poll() {
        List<OutboxRecord> batch = outboxRepository.lockPendingBatch(batchSize);
        if (batch.isEmpty()) return;

        for (OutboxRecord record : batch) {
            try {
                SendResult<String, String> result = kafkaTemplate
                        .send(record.getTopic(), record.getPartitionKey(), record.getPayload())
                        .get(5, TimeUnit.SECONDS);
                record.markPublished();
                publishedCounter.increment();
                log.debug("Published {} to {}@{}", record.getEventId(), record.getTopic(),
                        result.getRecordMetadata().offset());
            } catch (Exception e) {
                failedCounter.increment();
                record.incrementAttempt(e.getMessage());
                if (record.getAttempts() >= 10) {
                    record.markFailed(e.getMessage());
                    log.error("Outbox {} permanently failed after {} attempts", record.getEventId(), record.getAttempts());
                } else {
                    log.warn("Outbox {} publish failed (attempt {}): {}", record.getEventId(), record.getAttempts(), e.getMessage());
                }
            }
        }
    }
}
