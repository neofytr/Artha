package com.artha.api.config;

import com.artha.application.saga.TransferMoneySaga;
import com.artha.core.aggregate.AggregateRepository;
import com.artha.core.aggregate.SnapshotStrategy;
import com.artha.core.event.ConcurrencyException;
import com.artha.core.event.EventStore;
import com.artha.core.resilience.CircuitBreaker;
import com.artha.core.resilience.RetryPolicy;
import com.artha.core.resilience.TokenBucketRateLimiter;
import com.artha.core.saga.SagaOrchestrator;
import com.artha.core.saga.SagaStateStore;
import com.artha.domain.account.Account;
import com.artha.domain.account.AccountEvents.*;
import com.artha.domain.transfer.TransferContext;
import com.artha.infrastructure.messaging.EventPublisher;
import com.artha.infrastructure.persistence.EventSerializer;
import com.artha.infrastructure.persistence.JpaSagaStateStore;
import com.artha.infrastructure.persistence.SagaInstanceRecordRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.Set;

@Configuration
public class ApplicationConfig {

    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper().registerModule(new JavaTimeModule());
    }

    @Bean
    public EventSerializer eventSerializer() {
        EventSerializer s = new EventSerializer();
        // Register every persisted event type. Forgetting to register an event here
        // = silent data loss on replay. A more aggressive design would scan a
        // marker interface; we keep it explicit to make the contract visible.
        s.register(AccountOpened.class);
        s.register(MoneyDeposited.class);
        s.register(MoneyWithdrawn.class);
        s.register(MoneyReserved.class);
        s.register(ReservationReleased.class);
        s.register(ReservationCommitted.class);
        s.register(AccountFrozen.class);
        s.register(AccountUnfrozen.class);
        s.register(AccountClosed.class);
        return s;
    }

    @Bean
    public SnapshotStrategy snapshotStrategy(ObjectMapper mapper,
                                             @Value("${artha.snapshot.every-n:50}") int everyN) {
        return new SnapshotStrategy(everyN, mapper);
    }

    @Bean
    public AggregateRepository<Account> accountRepository(EventStore eventStore,
                                                          EventPublisher eventPublisher,
                                                          SnapshotStrategy snapshotStrategy) {
        return new AggregateRepository<>(eventStore, eventPublisher, Account::new, Account.class, snapshotStrategy);
    }

    @Bean
    public RetryPolicy concurrencyRetryPolicy() {
        return new RetryPolicy(
                5,
                Duration.ofMillis(10),
                Duration.ofMillis(500),
                2.0,
                Set.of(ConcurrencyException.class)
        );
    }

    @Bean
    public SagaStateStore<TransferContext> transferSagaStore(SagaInstanceRecordRepository repo,
                                                             ObjectMapper mapper) {
        return new JpaSagaStateStore<>(repo, mapper, TransferContext.class);
    }

    @Bean
    public SagaOrchestrator<TransferContext> transferSagaOrchestrator(
            AggregateRepository<Account> accounts,
            SagaStateStore<TransferContext> store,
            RetryPolicy concurrencyRetryPolicy) {
        return new SagaOrchestrator<>(TransferMoneySaga.define(accounts), store, concurrencyRetryPolicy);
    }

    @Bean
    public TokenBucketRateLimiter apiRateLimiter(
            @Value("${artha.rate-limit.capacity:100}") int capacity,
            @Value("${artha.rate-limit.refill-per-second:50}") int refill) {
        return new TokenBucketRateLimiter(capacity, refill);
    }

    @Bean
    public CircuitBreaker downstreamCircuitBreaker(
            @Value("${artha.circuit.failures:5}") int failures,
            @Value("${artha.circuit.open-seconds:30}") int openSeconds,
            @Value("${artha.circuit.half-open-probes:3}") int probes) {
        return new CircuitBreaker("downstream", failures, Duration.ofSeconds(openSeconds), probes);
    }
}
