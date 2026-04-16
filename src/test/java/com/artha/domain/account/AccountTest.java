package com.artha.domain.account;

import com.artha.core.event.DomainEvent;
import com.artha.domain.account.AccountEvents.*;
import org.junit.jupiter.api.Test;

import java.util.Currency;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AccountTest {

    private static final Currency INR = Currency.getInstance("INR");

    @Test
    void opensAccountWithZeroBalance() {
        Account account = Account.open(UUID.randomUUID(), "user-1", INR);
        assertThat(account.getBalance()).isEqualTo(Money.zero(INR));
        assertThat(account.getStatus()).isEqualTo(Account.Status.ACTIVE);
        assertThat(account.getUncommittedEvents()).hasSize(1);
    }

    @Test
    void depositIncreasesBalance() {
        Account account = Account.open(UUID.randomUUID(), "user-1", INR);
        account.deposit(Money.of("100.00", "INR"), "seed");
        assertThat(account.getBalance()).isEqualTo(Money.of("100.00", "INR"));
    }

    @Test
    void withdrawDecreasesBalance() {
        Account account = Account.open(UUID.randomUUID(), "user-1", INR);
        account.deposit(Money.of("100.00", "INR"), "seed");
        account.withdraw(Money.of("30.00", "INR"), "coffee");
        assertThat(account.getBalance()).isEqualTo(Money.of("70.00", "INR"));
    }

    @Test
    void cannotOverdraw() {
        Account account = Account.open(UUID.randomUUID(), "user-1", INR);
        account.deposit(Money.of("10.00", "INR"), "seed");
        assertThatThrownBy(() -> account.withdraw(Money.of("50.00", "INR"), "nope"))
                .isInstanceOf(InsufficientFundsException.class);
    }

    @Test
    void reservationBlocksAvailableButNotBalance() {
        Account account = Account.open(UUID.randomUUID(), "user-1", INR);
        account.deposit(Money.of("100.00", "INR"), "seed");
        UUID res = UUID.randomUUID();
        account.reserve(res, Money.of("40.00", "INR"));
        assertThat(account.getBalance()).isEqualTo(Money.of("100.00", "INR"));
        assertThat(account.availableBalance()).isEqualTo(Money.of("60.00", "INR"));
    }

    @Test
    void cannotWithdrawReservedFunds() {
        Account account = Account.open(UUID.randomUUID(), "user-1", INR);
        account.deposit(Money.of("100.00", "INR"), "seed");
        account.reserve(UUID.randomUUID(), Money.of("80.00", "INR"));
        assertThatThrownBy(() -> account.withdraw(Money.of("30.00", "INR"), "x"))
                .isInstanceOf(InsufficientFundsException.class);
    }

    @Test
    void commitReservationSettlesFunds() {
        Account account = Account.open(UUID.randomUUID(), "user-1", INR);
        account.deposit(Money.of("100.00", "INR"), "seed");
        UUID res = UUID.randomUUID();
        account.reserve(res, Money.of("40.00", "INR"));
        account.commitReservation(res);
        assertThat(account.getBalance()).isEqualTo(Money.of("60.00", "INR"));
        assertThat(account.availableBalance()).isEqualTo(Money.of("60.00", "INR"));
        assertThat(account.getReservations()).isEmpty();
    }

    @Test
    void releaseReservationFreesAvailable() {
        Account account = Account.open(UUID.randomUUID(), "user-1", INR);
        account.deposit(Money.of("100.00", "INR"), "seed");
        UUID res = UUID.randomUUID();
        account.reserve(res, Money.of("40.00", "INR"));
        account.releaseReservation(res);
        assertThat(account.availableBalance()).isEqualTo(Money.of("100.00", "INR"));
    }

    @Test
    void frozenAccountRejectsOperations() {
        Account account = Account.open(UUID.randomUUID(), "user-1", INR);
        account.freeze("KYC review");
        assertThatThrownBy(() -> account.deposit(Money.of("10.00", "INR"), "x"))
                .isInstanceOf(DomainException.class);
    }

    @Test
    void currencyMismatchIsRejected() {
        Account account = Account.open(UUID.randomUUID(), "user-1", INR);
        assertThatThrownBy(() -> account.deposit(Money.of("10.00", "USD"), "x"))
                .isInstanceOf(Money.CurrencyMismatchException.class);
    }

    @Test
    void replayRebuildsStateFromEvents() {
        UUID id = UUID.randomUUID();
        Account source = Account.open(id, "user-1", INR);
        source.deposit(Money.of("100.00", "INR"), "seed");
        source.withdraw(Money.of("30.00", "INR"), "coffee");

        List<DomainEvent> events = List.copyOf(source.getUncommittedEvents());
        Account replayed = new Account();
        replayed.replay(events);

        assertThat(replayed.getBalance()).isEqualTo(Money.of("70.00", "INR"));
        assertThat(replayed.getVersion()).isEqualTo(3);
    }

    @Test
    void snapshotRoundTripsState() {
        Account source = Account.open(UUID.randomUUID(), "user-1", INR);
        source.deposit(Money.of("100.00", "INR"), "seed");
        UUID res = UUID.randomUUID();
        source.reserve(res, Money.of("25.00", "INR"));

        var snapshot = source.captureSnapshot();
        Account restored = new Account();
        restored.restoreFromSnapshot(snapshot);

        assertThat(restored.getBalance()).isEqualTo(source.getBalance());
        assertThat(restored.availableBalance()).isEqualTo(source.availableBalance());
        assertThat(restored.getStatus()).isEqualTo(source.getStatus());
    }
}
