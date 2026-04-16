package com.artha.integration;

import com.artha.application.command.AccountCommandHandlers;
import com.artha.application.command.AccountCommands.*;
import com.artha.core.aggregate.AggregateRepository;
import com.artha.domain.account.Account;
import com.artha.domain.account.DomainException;
import com.artha.domain.account.Money;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * End-to-end integration test against the full Spring context.
 * <p>
 * Uses H2 (PostgreSQL-compatible mode) + EmbeddedKafka so the test is self-contained
 * and doesn't require Docker. Exercises the full write path: command dispatch →
 * aggregate load/save via JPA event store → outbox write → relay to embedded Kafka.
 * <p>
 * A Testcontainers variant with real Postgres + real Kafka is drafted but gated
 * behind a Docker availability check; see the README.
 */
@SpringBootTest(properties = {
        "artha.kafka.enabled=true",
        "artha.outbox.poll-ms=200",
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}"
})
@ActiveProfiles("test")
@EmbeddedKafka(partitions = 1,
        topics = {"artha.account.events", "artha.account.events.dlq"})
@DirtiesContext
class PaymentEngineIntegrationTest {

    @Autowired AccountCommandHandlers commands;
    @Autowired AggregateRepository<Account> accounts;

    @Test
    void openAccount_persistsEventsAndRebuildsOnLoad() {
        UUID id = UUID.randomUUID();
        commands.handle(new OpenAccount(id, "alice", "INR"));

        Account loaded = accounts.load(id).orElseThrow();
        assertThat(loaded.getOwnerId()).isEqualTo("alice");
        assertThat(loaded.getBalance()).isEqualTo(Money.zero(Currency.getInstance("INR")));
        assertThat(loaded.getVersion()).isEqualTo(1);
    }

    @Test
    void depositAndWithdraw_balanceMatches() {
        UUID id = UUID.randomUUID();
        commands.handle(new OpenAccount(id, "bob", "INR"));
        commands.handle(new Deposit(id, new BigDecimal("1000"), "INR", "seed"));
        commands.handle(new Withdraw(id, new BigDecimal("300"), "INR", "coffee"));

        Account loaded = accounts.load(id).orElseThrow();
        assertThat(loaded.getBalance()).isEqualTo(Money.of("700.00", "INR"));
    }

    @Test
    void overdraftIsRejected_stateUnchanged() {
        UUID id = UUID.randomUUID();
        commands.handle(new OpenAccount(id, "carol", "INR"));
        commands.handle(new Deposit(id, new BigDecimal("50"), "INR", "seed"));

        assertThatThrownBy(() -> commands.handle(new Withdraw(id, new BigDecimal("100"), "INR", "nope")))
                .isInstanceOf(DomainException.class);

        Account loaded = accounts.load(id).orElseThrow();
        assertThat(loaded.getBalance()).isEqualTo(Money.of("50.00", "INR"));
    }

    @Test
    void transferSaga_happyPath_movesFundsAtomically() {
        UUID src = UUID.randomUUID();
        UUID dst = UUID.randomUUID();
        commands.handle(new OpenAccount(src, "alice", "INR"));
        commands.handle(new OpenAccount(dst, "bob", "INR"));
        commands.handle(new Deposit(src, new BigDecimal("1000"), "INR", "seed"));

        commands.handle(new TransferMoney(src, dst, new BigDecimal("200"), "INR", "gift"));

        assertThat(accounts.load(src).orElseThrow().getBalance()).isEqualTo(Money.of("800.00", "INR"));
        assertThat(accounts.load(dst).orElseThrow().getBalance()).isEqualTo(Money.of("200.00", "INR"));
    }

    @Test
    void transferSaga_insufficientFunds_conservesMoney() {
        UUID src = UUID.randomUUID();
        UUID dst = UUID.randomUUID();
        commands.handle(new OpenAccount(src, "alice", "INR"));
        commands.handle(new OpenAccount(dst, "bob", "INR"));
        commands.handle(new Deposit(src, new BigDecimal("50"), "INR", "seed"));

        assertThatThrownBy(() ->
                commands.handle(new TransferMoney(src, dst, new BigDecimal("100"), "INR", "too-much")))
                .isInstanceOf(DomainException.class);

        BigDecimal total = accounts.load(src).orElseThrow().getBalance().amount()
                .add(accounts.load(dst).orElseThrow().getBalance().amount());
        assertThat(total).isEqualByComparingTo(new BigDecimal("50.00"));
    }

    @Test
    void transferSaga_frozenDestination_rollsBackSourceReservation() {
        UUID src = UUID.randomUUID();
        UUID dst = UUID.randomUUID();
        commands.handle(new OpenAccount(src, "alice", "INR"));
        commands.handle(new OpenAccount(dst, "bob", "INR"));
        commands.handle(new Deposit(src, new BigDecimal("1000"), "INR", "seed"));
        commands.handle(new FreezeAccount(dst, "KYC"));

        assertThatThrownBy(() ->
                commands.handle(new TransferMoney(src, dst, new BigDecimal("100"), "INR", "rejected")))
                .isInstanceOf(DomainException.class);

        // Source compensation must have released the reservation.
        Account source = accounts.load(src).orElseThrow();
        assertThat(source.getBalance()).isEqualTo(Money.of("1000.00", "INR"));
        assertThat(source.availableBalance()).isEqualTo(Money.of("1000.00", "INR"));
        assertThat(source.getReservations()).isEmpty();
    }
}
