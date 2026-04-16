package com.artha.infrastructure.observability;

import com.artha.infrastructure.persistence.OutboxRecord;
import com.artha.infrastructure.persistence.OutboxRecordRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * DOWN when the outbox has more pending messages than a threshold — usually
 * a symptom of Kafka being unreachable or the relay being wedged. This is the
 * signal oncall needs to know the write path is still fine but events aren't
 * flowing, which is a different kind of problem than "service is down."
 */
@Component
public class OutboxBacklogHealthIndicator implements HealthIndicator {

    private final OutboxRecordRepository repository;
    private final long threshold;

    public OutboxBacklogHealthIndicator(OutboxRecordRepository repository,
                                        @Value("${artha.health.outbox-backlog-threshold:1000}") long threshold) {
        this.repository = repository;
        this.threshold = threshold;
    }

    @Override
    public Health health() {
        long pending = repository.findAll().stream()
                .filter(r -> r.getStatus() == OutboxRecord.Status.PENDING)
                .count();
        long failed = repository.findAll().stream()
                .filter(r -> r.getStatus() == OutboxRecord.Status.FAILED)
                .count();

        Health.Builder builder = (pending > threshold) ? Health.down() : Health.up();
        return builder
                .withDetail("pending", pending)
                .withDetail("failed", failed)
                .withDetail("threshold", threshold)
                .build();
    }
}
