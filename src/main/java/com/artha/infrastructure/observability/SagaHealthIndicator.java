package com.artha.infrastructure.observability;

import com.artha.core.saga.SagaState;
import com.artha.infrastructure.persistence.SagaInstanceRecordRepository;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * DOWN if any saga is STUCK — these are cases where compensation failed and
 * the money ledger is potentially inconsistent. Humans need to know.
 */
@Component
public class SagaHealthIndicator implements HealthIndicator {

    private final SagaInstanceRecordRepository repository;

    public SagaHealthIndicator(SagaInstanceRecordRepository repository) {
        this.repository = repository;
    }

    @Override
    public Health health() {
        long stuck = repository.countByState(SagaState.STUCK.name());
        long compensating = repository.countByState(SagaState.COMPENSATING.name());
        long running = repository.countByState(SagaState.RUNNING.name());

        Health.Builder builder = stuck > 0 ? Health.down() : Health.up();
        return builder
                .withDetail("stuck", stuck)
                .withDetail("compensating", compensating)
                .withDetail("running", running)
                .build();
    }
}
